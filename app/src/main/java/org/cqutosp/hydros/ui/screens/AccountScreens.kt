package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.*

enum class AccountForm(val title: String) { PHONE("更换手机号"), PASSWORD("修改密码") }

/** 平铺分组：白底、无卡片容器、无额外左右 padding（页面留白由 PageColumn 统一提供）。 */
@Composable
private fun AccountSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
fun ManageAccountScreen(
    state: HydrosUiState,
    onPhone: () -> Unit,
    onPassword: () -> Unit,
    onLogout: () -> Unit,
    onSession: () -> Unit,
) {
    val snackbar = LocalHydrosSnackbar.current
    var logoutSheet by remember { mutableStateOf(false) }
    PageColumn {
        // 身份卡：圆角图标块 + 姓名 + 手机号 / 学校
        AccountSurface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconTile(
                    res = R.drawable.ic_person,
                    modifier = Modifier,
                    tone = IconTone.PRIMARY,
                    size = 44.dp,
                    iconSize = 22.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = state.account.name,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.account.phone.isNotBlank()) {
                        Text(
                            text = state.account.phone,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.account.school.isNotBlank()) {
                        Text(
                            text = state.account.school,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Section("账户安全") {
            MineRow("更换手机号", icon = R.drawable.ic_phone, tone = IconTone.PRIMARY, onClick = onPhone)
            SubtleDivider(horizontalPadding = 0.dp)
            MineRow("修改密码", icon = R.drawable.ic_lock, tone = IconTone.SECONDARY, onClick = onPassword)
        }
        DangerAction(
            label = "退出账户",
            icon = R.drawable.ic_logout,
            onClick = {
                if (state.session != null) snackbar.show("请先结束当前会话，再退出账户。", SnackbarAction("进入使用页面", onSession), error = true)
                else logoutSheet = true
            }
        )
    }

    if (logoutSheet) {
        ConfirmBottomSheet(
            title = "退出账户？",
            description = "退出后需要重新登录。",
            confirm = "退出",
            destructive = true,
            onDismiss = { logoutSheet = false }
        ) {
            logoutSheet = false
            onLogout()
        }
    }
}

@Composable
fun LoginScreen(onSend: suspend (String) -> Boolean = { false }, onLogin: suspend (String, String, Boolean) -> Boolean = { _, _, _ -> false },
    loginRetryAfterMillis: () -> Long = { 0 },
    initialPhone: String = "",
    footer: (@Composable () -> Unit)? = null) {
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sms by rememberSaveable { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalHydrosSnackbar.current
    var retrySeconds by remember { mutableIntStateOf(0) }
    val retryAfter by rememberUpdatedState(loginRetryAfterMillis)
    fun refreshCooldown() {
        retrySeconds = ((retryAfter() + 999) / 1_000).toInt()
    }
    LaunchedEffect(Unit) {
        while (true) {
            refreshCooldown()
            kotlinx.coroutines.delay(250)
        }
    }
    PageColumn {
        // 品牌区：图标 + 名称 + 一句话说明，登录页唯一的“装饰”，其余全部是表单本身。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 登录页直接用应用图标，避免和桌面图标不是同一个标识。
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Hydros+",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Hydros+",
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "校园热水 · 扫码即用",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SegmentedControl(
            options = listOf("密码登录", "验证码登录"),
            selectedIndex = if (sms) 1 else 0,
            onSelect = { index ->
                sms = index == 1
                if (sms) password = "" else code = ""
                submitted = false
            }
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FormField(phone, { phone=it }, "手机号", numeric=true, error=if(submitted) phoneError(phone) else null)
            if(sms) VerificationCodeField(phone, code, { code=it }, if(submitted) codeError(code) else null, validatePhone = ::phoneError, maxLength = null, onSend = onSend)
            else FormField(password, { password=it }, "密码", secret=true, error=if(submitted) passwordError(password) else null)
        }
        Spacer(Modifier.height(6.dp))
        PrimaryAction(if(busy) "正在登录" else if(retrySeconds > 0) "${retrySeconds} 秒后重试" else "登录", enabled=!busy && retrySeconds == 0, loading=busy) {
            submitted=true
            val error = phoneError(phone) ?: if (sms) codeError(code) else passwordError(password)
            if (error != null) snackbar.show(error, error = true)
            else scope.launch {
                busy=true
                try { if(onLogin(phone, if(sms) code else password, sms)) { password=""; code="" } }
                finally { refreshCooldown(); busy=false }
            }
        }
        footer?.invoke()
    }
}

@Composable
fun AccountFormScreen(form: AccountForm, account: AccountUi, onSend: suspend (String, Int) -> Boolean,
    onSave: suspend (String, String, String, String) -> Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sms by rememberSaveable { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val snackbar = LocalHydrosSnackbar.current
    val targetPhone=if(form==AccountForm.PHONE) phone else account.phone
    PageColumn {
        if(form == AccountForm.PASSWORD) {
            SegmentedControl(
                options = listOf("通过当前密码修改", "通过短信验证码修改"),
                selectedIndex = if (sms) 1 else 0,
                onSelect = { index ->
                    sms = index == 1
                    if (sms) oldPassword = "" else code = ""
                    submitted = false
                }
            )
        }
        AccountSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if(form==AccountForm.PHONE) {
                    FormField(phone, { phone=it }, "新手机号", numeric=true, error=if(submitted) phoneError(phone) ?: if(phone==account.phone) "请输入不同的新号码" else null else null)
                    VerificationCodeField(phone, code, { code=it }, if(submitted) codeError(code) else null, validatePhone = ::phoneError, maxLength = null) { onSend(it, 5) }
                } else {
                    if (sms) {
                        Text(
                            text = "当前手机号：${account.phone}",
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        VerificationCodeField(targetPhone, code, { code=it }, if(submitted) codeError(code) else null, validatePhone = ::phoneError, maxLength = null) { onSend(it, 2) }
                    } else {
                        FormField(oldPassword, { oldPassword=it }, "当前密码", secret=true, error=if(submitted) passwordError(oldPassword) else null)
                    }
                    FormField(password, { password=it }, "新密码", secret=true, error=if(submitted) passwordError(password) else null)
                    FormField(repeated, { repeated=it }, "确认新密码", secret=true, error=if(submitted && repeated!=password) "两次输入的密码不一致" else null)
                }
            }
        }
        PrimaryAction(if(busy) "正在保存" else "保存修改", enabled=!busy, loading=busy) {
            submitted=true
            val error=when(form) {
                AccountForm.PHONE -> phoneError(phone) ?: if (phone == account.phone) "请输入不同的新号码" else codeError(code)
                AccountForm.PASSWORD -> if (sms) {
                    codeError(code) ?: passwordError(password) ?: if (repeated != password) "两次输入的密码不一致" else null
                } else {
                    passwordError(oldPassword) ?: passwordError(password) ?: if (repeated != password) "两次输入的密码不一致" else null
                }
            }
            if (error != null) snackbar.show(error, error = true)
            else scope.launch {
                busy=true
                try {
                    val saveOld = if (form == AccountForm.PASSWORD && !sms) oldPassword else ""
                    val saveCode = if (form == AccountForm.PHONE || sms) code else ""
                    if(onSave(targetPhone, saveOld, password, saveCode)) {
                        oldPassword=""; password=""; repeated=""; code=""; snackbar.show("修改完成", severity = org.cqutosp.hydros.data.feedback.FeedbackSeverity.SUCCESS)
                    }
                }
                finally { busy=false }
            }
        }
    }
}