package org.cqutosp.hydros.thirdparty

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.cqutosp.hydros.data.network.ApiFailure
import org.cqutosp.hydros.data.network.Credentials

/**
 * 充值页状态。余额单位是分，且只能来自服务端刷新，本地永不加钱。
 * 错误只用于整页加载失败；下单 / 支付结果都走 notice，避免把套餐列表冲掉。
 */
data class RechargeUi(
    val loading: Boolean = false,
    val placing: Boolean = false,
    val packages: List<RechargePackage> = emptyList(),
    val channels: List<RechargeChannel> = emptyList(),
    val selectedPackageId: Long? = null,
    val selectedChannel: RechargeChannel = RechargeChannel.ALIPAY,
    val error: String? = null,
    val notice: String? = null,
    val balanceCents: Long? = null,
    val refreshingBalance: Boolean = false,
) {
    val selectedPackage: RechargePackage?
        get() = packages.firstOrNull { it.id == selectedPackageId }

    val busy: Boolean get() = loading || placing

    val canSubmit: Boolean
        get() = !busy && selectedPackage != null && channels.contains(selectedChannel)
}

/**
 * 充值流程状态机：加载通道与套餐、下单、把待支付订单交给上层、支付回传后只刷新余额。
 *
 * 两个 generation：
 * - generation 保护加载 / 下单这整条链，账号切换或重新进入时旧请求的结果直接丢弃；
 * - balanceGeneration 保护余额刷新，连续刷新只认最后一次。
 * 任何路径都不在本地改余额，到账与否一律以服务端 account/wallet 为准。
 */
class RechargeController(
    private val scope: CoroutineScope,
    private val repository: RechargeRepository,
    private val credentials: () -> Credentials?,
) {

    private val mutableUi = MutableStateFlow(RechargeUi())
    val ui: StateFlow<RechargeUi> = mutableUi.asStateFlow()

    private val mutableOrder = MutableStateFlow<RechargeOrder?>(null)
    /** 非空表示有一张已经下单、等待上层拉起支付（或打开收银台）的订单。 */
    val pendingOrder: StateFlow<RechargeOrder?> = mutableOrder.asStateFlow()

    private var generation = 0L
    private var sessionKey: String? = null
    private var loadJob: Job? = null
    private var submitJob: Job? = null
    private var balanceJob: Job? = null
    private var balanceGeneration = 0L

    /** 进入页面时调用；同账号已经加载成功就不重复请求，账号变了则重来。 */
    fun open() {
        val state = mutableUi.value
        if (state.busy) return
        val auth = credentials()
        if (auth != null && sessionKey != null && sessionKey != auth.key) {
            clear()
            load()
            return
        }
        if (state.channels.isNotEmpty() && state.packages.isNotEmpty()) return
        load()
    }

    /** 重新读取支付方式与套餐；失败时整页给错误和重试。 */
    fun load() {
        if (mutableUi.value.busy) return
        loadJob?.cancel()
        submitJob?.cancel()
        balanceJob?.cancel()
        val previous = mutableUi.value
        val auth = credentials()
        if (auth == null) {
            sessionLost()
            return
        }
        ++generation
        ++balanceGeneration
        sessionKey = auth.key
        val expected = generation
        mutableUi.value = RechargeUi(loading = true)
        loadJob = scope.launch {
            try {
                val channels = repository.payChannels(auth)
                if (!isCurrent(expected)) return@launch
                val packages = repository.packages(auth)
                if (!isCurrent(expected)) return@launch
                val keepChannel = previous.selectedChannel.takeIf { it in channels }
                val keepPackage = previous.selectedPackageId?.takeIf { id -> packages.any { it.id == id } }
                mutableUi.value = mutableUi.value.copy(
                    loading = false,
                    packages = packages,
                    channels = channels,
                    selectedPackageId = keepPackage ?: packages.firstOrNull()?.id,
                    selectedChannel = keepChannel ?: channels.firstOrNull() ?: RechargeChannel.ALIPAY,
                    error = null,
                )
                refreshBalance()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrent(expected)) return@launch
                val message = (failure as? ApiFailure)?.message
                if (failure is ApiFailure && failure.expired) {
                    sessionLost()
                } else {
                    mutableUi.value = mutableUi.value.copy(
                        loading = false,
                        error = message?.takeIf(String::isNotBlank) ?: "读取充值信息失败，请检查网络后重试",
                    )
                }
            }
        }
    }

    fun selectPackage(id: Long) {
        val state = mutableUi.value
        if (state.busy) return
        if (state.packages.none { it.id == id }) return
        if (state.selectedPackageId == id) return
        mutableUi.value = state.copy(selectedPackageId = id, notice = null)
    }

    fun selectChannel(channel: RechargeChannel) {
        val state = mutableUi.value
        if (state.busy) return
        if (state.channels.none { it == channel }) return
        if (state.selectedChannel == channel) return
        mutableUi.value = state.copy(selectedChannel = channel, notice = null)
    }

    /** 下单。成功后把订单放进 pendingOrder，余额此时不动。 */
    fun submit() {
        if (!isCurrent()) {
            load()
            return
        }
        val state = mutableUi.value
        if (!state.canSubmit) return
        val auth = credentials() ?: return
        val amount = state.selectedPackage?.amount ?: return
        val channel = state.selectedChannel
        submitJob?.cancel()
        val expected = ++generation
        mutableUi.value = state.copy(placing = true, notice = null)
        submitJob = scope.launch {
            try {
                val order = repository.createOrder(auth, channel, amount)
                if (!isCurrent(expected)) return@launch
                mutableOrder.value = order
                mutableUi.value = mutableUi.value.copy(
                    placing = false,
                    notice = when (order) {
                        is RechargeOrder.Alipay -> "订单已生成，正在拉起支付宝…"
                        is RechargeOrder.Browser -> "支付页面已生成，完成支付后回来点「刷新余额」确认到账"
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!isCurrent(expected)) return@launch
                val message = (failure as? ApiFailure)?.message
                if (failure is ApiFailure && failure.expired) {
                    sessionLost()
                } else {
                    mutableUi.value = mutableUi.value.copy(
                        placing = false,
                        notice = message?.takeIf(String::isNotBlank) ?: "下单失败，请检查网络后重试",
                    )
                }
            }
        }
    }

    /** 支付结果回传：只提示，再问服务端要余额；即使 9000 也不在本地加钱。 */
    fun completePayment(result: AlipayResult) {
        if (!isCurrent()) {
            mutableOrder.value = null
            return
        }
        mutableOrder.value = null
        mutableUi.value = mutableUi.value.copy(placing = false, notice = describe(result))
        refreshBalance()
    }

    /** 交出待支付订单，由上层尝试拉起支付宝或打开浏览器；重复调用只会拿到一次订单。 */
    fun consumeOrder(): RechargeOrder? {
        val order = mutableOrder.value
        mutableOrder.value = null
        return order
    }

    /** 刷新余额（GET account/wallet），返回分；连续调用只保留最后一次。 */
    fun refreshBalance() {
        if (!isCurrent()) return
        val auth = credentials() ?: return
        balanceJob?.cancel()
        val request = ++balanceGeneration
        mutableUi.value = mutableUi.value.copy(refreshingBalance = true)
        balanceJob = scope.launch {
            try {
                val cents = repository.refreshBalance(auth)
                if (request != balanceGeneration || !isCurrent()) return@launch
                mutableUi.value = mutableUi.value.copy(
                    balanceCents = cents ?: mutableUi.value.balanceCents,
                    refreshingBalance = false,
                    notice = if (cents == null) "余额暂未更新，请稍后刷新确认到账" else mutableUi.value.notice,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request != balanceGeneration || !isCurrent()) return@launch
                if (failure is ApiFailure && failure.expired) {
                    sessionLost()
                } else {
                    mutableUi.value = mutableUi.value.copy(
                        refreshingBalance = false,
                        notice = "余额暂未更新，请稍后刷新确认到账",
                    )
                }
            }
        }
    }

    /** 离开页面：取消所有请求、丢掉待支付订单和本地状态。 */
    fun clear() {
        ++generation
        ++balanceGeneration
        loadJob?.cancel()
        loadJob = null
        submitJob?.cancel()
        submitJob = null
        balanceJob?.cancel()
        balanceJob = null
        sessionKey = null
        mutableOrder.value = null
        mutableUi.value = RechargeUi()
    }

    /**
     * 账号失效。这里不 cancel 自己所在的任务，只推进 generation：旧请求随后的写入
     * 会被 isCurrent 全部丢弃，页面回到需要重新登录的空态。
     */
    private fun sessionLost() {
        ++generation
        ++balanceGeneration
        sessionKey = null
        mutableOrder.value = null
        mutableUi.value = RechargeUi(error = "登录已失效，请重新登录")
    }

    private fun isCurrent(expected: Long = generation): Boolean {
        val auth = credentials() ?: return false
        return expected == generation && sessionKey == auth.key
    }

    private fun describe(result: AlipayResult): String = when (result) {
        AlipayResult.SUCCESS -> "支付成功，正在刷新余额…"
        AlipayResult.PROCESSING -> "支付处理中，稍后点「刷新余额」确认到账"
        AlipayResult.CANCELLED -> "已取消支付"
        AlipayResult.NETWORK_ERROR -> "网络异常，支付结果尚未确认，请点「刷新余额」核实"
        AlipayResult.FAILED -> "支付未完成，可查看支付宝账单后重试"
        AlipayResult.REPEAT -> "重复的支付请求，请先刷新余额确认是否到账"
        AlipayResult.UNKNOWN -> "支付结果尚未确认，请刷新余额或查看支付宝账单，避免重复充值"
    }
}
