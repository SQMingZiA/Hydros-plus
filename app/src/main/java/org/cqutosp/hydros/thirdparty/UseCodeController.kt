package org.cqutosp.hydros.thirdparty

import java.io.IOException
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
 * 使用码页面的状态。
 * [candidate]/[candidateAt] 是「换一个」得到的候选码及其生成时间（毫秒），用于 UI 的 3 分钟倒计时；
 * 候选码未领取时不会改变当前生效的使用码。
 */
data class UseCodeUi(
    val loading: Boolean = false,
    val state: UseCodeState? = null,
    val error: String? = null,
    val notice: String? = null,
    val busy: Boolean = false,
    val candidate: GeneratedUseCode? = null,
    val candidateAt: Long = 0L,
)

/** 使用码页面的主线程状态持有者；操作后总是重新拉取状态，避免本地猜测服务端结果。 */
class UseCodeController(
    private val scope: CoroutineScope,
    private val repository: UseCodeRepository,
    private val credentials: () -> Credentials?,
) {
    private val mutable = MutableStateFlow(UseCodeUi())
    val state: StateFlow<UseCodeUi> = mutable.asStateFlow()

    /** 只保留最后一次操作，旧请求的迟到响应用 [epoch] 丢弃。 */
    private var pending: Job? = null
    private var epoch = 0

    fun refresh() {
        start(loading = true, busy = false, fallback = "获取使用码失败，请重试") { auth, token ->
            val next = repository.status(auth)
            publish(token) { it.copy(state = next) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        start(loading = false, busy = true, fallback = "操作失败，请重试") { auth, token ->
            val next = repository.setEnabled(auth, enabled)
            publish(token) { it.copy(state = next, notice = if (enabled) "使用码已开启" else "使用码已关闭") }
        }
    }

    fun generate() {
        start(loading = false, busy = true, fallback = "换码失败，请重试") { auth, token ->
            val generated = repository.generate(auth)
            publish(token) {
                it.copy(
                    candidate = generated,
                    candidateAt = System.currentTimeMillis(),
                    error = null,
                    notice = "已换到新码，请在 3 分钟内领取",
                )
            }
        }
    }

    fun claim(code: String) {
        val target = code.trim()
        if (target.isEmpty()) {
            mutable.value = state.value.copy(error = "请先换一个使用码，再确定领取")
            return
        }
        start(loading = false, busy = true, fallback = "领取失败，请重试") { auth, token ->
            val next = repository.claim(auth, target)
            publish(token) {
                it.copy(
                    state = next,
                    candidate = null,
                    candidateAt = 0L,
                    error = null,
                    notice = "领取成功，使用码已更新",
                )
            }
        }
    }

    /** 离开页面或切号时清掉一次性提示与未领取的候选码。 */
    fun clear() {
        pending?.cancel()
        pending = null
        epoch++
        mutable.value = mutable.value.copy(
            loading = false,
            busy = false,
            error = null,
            notice = null,
            candidate = null,
            candidateAt = 0L,
        )
    }

    private fun start(
        loading: Boolean,
        busy: Boolean,
        fallback: String,
        block: suspend (Credentials, Int) -> Unit,
    ) {
        val auth = credentials()
        if (auth == null) {
            pending?.cancel()
            pending = null
            epoch++
            mutable.value = mutable.value.copy(loading = false, busy = false, error = "请先登录后再操作")
            return
        }
        pending?.cancel()
        val token = ++epoch
        mutable.value = mutable.value.copy(loading = loading, busy = busy, error = null)
        pending = scope.launch {
            try {
                block(auth, token)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Throwable) {
                if (token == epoch) mutable.value = mutable.value.copy(error = messageOf(failure, fallback))
            } finally {
                if (token == epoch) mutable.value = mutable.value.copy(loading = false, busy = false)
            }
        }
    }

    private fun publish(token: Int, transform: (UseCodeUi) -> UseCodeUi) {
        if (token == epoch) mutable.value = transform(mutable.value)
    }

    private fun messageOf(failure: Throwable, fallback: String): String = when {
        failure is ApiFailure && failure.expired -> "登录已失效，请重新登录"
        failure is ApiFailure -> failure.message.takeIf { it.isNotBlank() } ?: fallback
        failure is IOException -> "网络连接失败，请检查网络设置"
        else -> failure.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
