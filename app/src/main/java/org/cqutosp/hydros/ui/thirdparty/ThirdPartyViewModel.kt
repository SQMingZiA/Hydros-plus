package org.cqutosp.hydros.ui.thirdparty

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cqutosp.hydros.data.HydrosApplication
import org.cqutosp.hydros.data.network.CampusTransport
import org.cqutosp.hydros.data.network.Credentials
import org.cqutosp.hydros.thirdparty.AlipayResult
import org.cqutosp.hydros.thirdparty.BoundDorm
import org.cqutosp.hydros.thirdparty.DormBindingStore
import org.cqutosp.hydros.thirdparty.RechargeController
import org.cqutosp.hydros.thirdparty.RechargeRepository
import org.cqutosp.hydros.thirdparty.RechargeUi
import org.cqutosp.hydros.thirdparty.RechargeOrder
import org.cqutosp.hydros.thirdparty.RechargeChannel
import org.cqutosp.hydros.thirdparty.UseCodeController
import org.cqutosp.hydros.thirdparty.UseCodeRepository
import org.cqutosp.hydros.thirdparty.UseCodeUi
import org.cqutosp.hydros.thirdparty.normalizeDormMac
import org.cqutosp.hydros.thirdparty.normalizeDormRoom

/** 绑定寝室页面的本地状态。 */
data class DormUi(
    val loading: Boolean = true,
    val bound: BoundDorm? = null,
    val editing: Boolean = false,
    val error: String? = null,
)

/**
 * 承载 thirdparty 包的三个拓展功能（使用码 / 绑定寝室 / 充值）。
 * 单独一个 ViewModel，避免把移植代码的状态混进 HydrosUiState。
 */
class ThirdPartyViewModel(
    transport: CampusTransport?,
    private val credentials: () -> Credentials?,
    private val dormStore: DormBindingStore,
) : ViewModel() {
    private val useCodeController: UseCodeController? = transport?.let { UseCodeController(viewModelScope, UseCodeRepository(it), credentials) }
    val useCode: StateFlow<UseCodeUi>? get() = useCodeController?.state

    private val rechargeController: RechargeController? = transport?.let { RechargeController(viewModelScope, RechargeRepository(it), credentials) }
    val recharge: StateFlow<RechargeUi>? get() = rechargeController?.ui
    val pendingRechargeOrder: StateFlow<RechargeOrder?>? get() = rechargeController?.pendingOrder

    private val _dorm = MutableStateFlow(DormUi())
    val dorm: StateFlow<DormUi> = _dorm.asStateFlow()

    init { loadDorm() }

    fun refreshUseCode() { useCodeController?.refresh() }
    fun setUseCodeEnabled(enabled: Boolean) { useCodeController?.setEnabled(enabled) }
    fun generateUseCode() { useCodeController?.generate() }
    fun claimUseCode(code: String) { useCodeController?.claim(code) }

    /** 离开使用码页面时清掉未领取的候选码与一次性提示。 */
    fun clearUseCode() { useCodeController?.clear() }

    fun openRecharge() { rechargeController?.open() }
    fun refreshRecharge() { rechargeController?.load() }
    fun selectRechargePackage(id: Long) { rechargeController?.selectPackage(id) }
    fun selectRechargeChannel(channel: RechargeChannel) { rechargeController?.selectChannel(channel) }
    fun submitRecharge() { rechargeController?.submit() }
    fun consumeRechargeOrder(): RechargeOrder? = rechargeController?.consumeOrder()
    fun refreshRechargeBalance() { rechargeController?.refreshBalance() }

    /** 支付宝客户端返回结果后回传给充值控制器，由它决定是否刷新余额。 */
    fun completeRechargePayment(result: AlipayResult) { rechargeController?.completePayment(result) }

    fun loadDorm() {
        viewModelScope.launch {
            val bound = runCatching { dormStore.load() }.getOrNull()
            _dorm.update { it.copy(loading = false, bound = bound) }
        }
    }

    fun startEditDorm() { _dorm.update { it.copy(editing = true, error = null) } }
    fun cancelEditDorm() { _dorm.update { it.copy(editing = false, error = null) } }

    fun saveDorm(room: String, mac: String, label: String) {
        val normalizedRoom = normalizeDormRoom(room)
        val normalizedMac = normalizeDormMac(mac)
        if (normalizedRoom.isEmpty()) { _dorm.update { it.copy(error = "请输入寝室号") }; return }
        if (normalizedMac == null) { _dorm.update { it.copy(error = "请输入 12 位十六进制 MAC，如 AA:BB:CC:DD:EE:FF") }; return }
        viewModelScope.launch {
            _dorm.update { it.copy(error = null) }
            try {
                // 保留原绑定时间，便于展示“上次绑定”。
                val previous = _dorm.value.bound
                val bound = BoundDorm(room = normalizedRoom, deviceMac = normalizedMac, label = label.trim(),
                    boundAt = if (previous?.deviceMac == normalizedMac) previous.boundAt else System.currentTimeMillis())
                dormStore.save(bound)
                _dorm.update { it.copy(bound = bound, editing = false, error = null) }
            } catch (error: Exception) {
                _dorm.update { it.copy(error = error.message ?: "寝室绑定保存失败") }
            }
        }
    }

    fun clearDorm() {
        viewModelScope.launch {
            try {
                dormStore.clear()
                _dorm.update { it.copy(bound = null, editing = false, error = null) }
            } catch (error: Exception) {
                _dorm.update { it.copy(error = error.message ?: "解除绑定失败") }
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as HydrosApplication
                val dependencies = app.dependencies
                return ThirdPartyViewModel(dependencies.transport, { dependencies.auth.credentials.value },
                    DormBindingStore(application)) as T
            }
        }
    }
}

/** 拓展功能的 ViewModel 入口，由 HydrosApp 在 NavHost 外层创建。 */
@Composable
fun thirdPartyViewModel(): ThirdPartyViewModel = viewModel(
    factory = ThirdPartyViewModel.factory(
        LocalContext.current.applicationContext as Application,
    )
)
