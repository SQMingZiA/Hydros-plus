package org.cqutosp.hydros.ui.thirdparty

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.alipay.sdk.app.PayTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cqutosp.hydros.thirdparty.AlipayResult

/**
 * 从任意 Context 找到宿主 Activity。
 * 支付宝客户端 SDK 的 [PayTask] 需要一个真实的 Activity（内部用它的任务栈拉起收银台），
 * 传 Application 会直接支付失败，所以这里沿 ContextWrapper 往上找。
 */
internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 把服务端下发的 orderInfo 交给支付宝客户端完成支付。
 *
 * [PayTask.payV2] 是阻塞调用：它会切到支付宝 App 并一直等到用户返回结果，
 * 因此必须放到 IO 线程，绝不能在主线程（或 Compose 的组合阶段）直接调用。
 * 返回值是与 [AlipayResult] 一一对应的 resultStatus。
 */
suspend fun launchAlipayOrder(context: Context, orderInfo: String): AlipayResult {
    if (orderInfo.isBlank()) return AlipayResult.UNKNOWN
    val activity = context.findActivity() ?: return AlipayResult.UNKNOWN
    return withContext(Dispatchers.IO) {
        val raw = runCatching { PayTask(activity).payV2(orderInfo, true) }.getOrNull()
        AlipayResult.fromCode(raw?.get("resultStatus"))
    }
}
