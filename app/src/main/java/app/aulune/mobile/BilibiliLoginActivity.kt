package app.aulune.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val LoginCanvas = ComposeColor(0xFFF9FCEF)
private val LoginCard = ComposeColor(0xFFEFF2EA)
private val LoginInk = ComposeColor(0xFF30342E)
private val LoginMuted = ComposeColor(0xFF62695E)
private val LoginAccent = ComposeColor(0xFF3E6A3B)
private val LoginBorder = ComposeColor(0xFF9EA69B)
private val LoginError = ComposeColor(0xFFB3261E)

private enum class SafeLoginMethod(val title: String, val icon: ImageVector) {
    Qr("扫码登录", Icons.Outlined.QrCode2),
    Password("密码登录", Icons.Outlined.Password),
    Sms("短信登录", Icons.Outlined.Phone),
    Cookie("Cookie", Icons.Outlined.ContentPaste)
}

class BilibiliLoginActivity : ComponentActivity() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BilibiliLoginPage(
                onClose = ::finish,
                onLoginSuccess = {
                    scope.launch {
                        try {
                            val accountManager = BilibiliAccountManager.get(this@BilibiliLoginActivity)
                            val cookie = accountManager.currentCookie
                            if (cookie.isNotBlank()) {
                                val connector = BilibiliAccountConnector()
                                val result = connector.readFirstPage(cookie)
                                val db = AuluneLocalDatabase.create(this@BilibiliLoginActivity)
                                val repo = LocalCoreRepository(db.localCoreDao())
                                result.contents.forEach { entity ->
                                    repo.importContent(listOf(entity))
                                }
                            }
                        } catch (_: Exception) { }
                        finish()
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, BilibiliLoginActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BilibiliLoginPage(
    onClose: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val viewModel: BilibiliLoginViewModel = viewModel()
    var selected by rememberSaveable { mutableStateOf(SafeLoginMethod.Qr) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val riskState by viewModel.riskVerifyState.collectAsState()

    // Toast
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            android.widget.Toast.makeText(
                context,
                msg,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 登录成功
    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collectLatest { onLoginSuccess() }
    }

    // 极验验证启动器
    val geetestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            viewModel.onGeetestResult(
                validate = data?.getStringExtra(BilibiliGeetestActivity.RESULT_VALIDATE),
                seccode = data?.getStringExtra(BilibiliGeetestActivity.RESULT_SECCODE),
                challenge = data?.getStringExtra(BilibiliGeetestActivity.RESULT_CHALLENGE),
            )
        } else {
            viewModel.onGeetestResult(null, null, null)
        }
    }

    // 极验请求
    LaunchedEffect(Unit) {
        viewModel.geetestRequest.collectLatest { req ->
            val intent = BilibiliGeetestActivity.createIntent(
                context,
                req.gt,
                req.challenge,
            )
            geetestLauncher.launch(intent)
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = LoginAccent,
            background = LoginCanvas,
            surface = LoginCard,
            onSurface = LoginInk,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(LoginCanvas)
                    .verticalScroll(rememberScrollState()),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "登录",
                            color = LoginInk,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    navigationIcon = { Spacer(Modifier.width(48.dp)) },
                    actions = {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭登录",
                                tint = LoginInk,
                                modifier = Modifier.size(31.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LoginCanvas),
                )
                Column(Modifier.padding(horizontal = 30.dp, vertical = 36.dp)) {
                    Text("连接 B 站", color = LoginInk, fontSize = 29.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "选择一种方式继续。支持扫码、密码、短信验证码和 Cookie 四种登录方式。",
                        color = LoginInk,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                    )
                    Spacer(Modifier.height(40.dp))
                    LoginMethodTabs(selected = selected, onSelect = { selected = it })
                    Divider(color = LoginBorder.copy(alpha = 0.6f), thickness = 1.dp)
                    Spacer(Modifier.height(28.dp))
                    when (selected) {
                        SafeLoginMethod.Qr -> QrLoginPanel(viewModel)
                        SafeLoginMethod.Password -> PasswordLoginPanel(viewModel)
                        SafeLoginMethod.Sms -> SmsLoginPanel(viewModel)
                        SafeLoginMethod.Cookie -> CookieLoginPanel(viewModel)
                    }
                    Spacer(Modifier.height(20.dp))
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            errorMessage!!,
                            color = LoginError,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        "继续即表示你同意 B 站官方页面展示的用户协议与隐私政策。",
                        color = LoginMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    )
                    Spacer(Modifier.height(25.dp))
                }
            }

            // 加载遮罩
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = LoginAccent)
                }
            }

            // 风控验证对话框
            if (riskState != null) {
                RiskVerifyDialog(viewModel, riskState!!)
            }
        }
    }
}

@Composable
private fun LoginMethodTabs(selected: SafeLoginMethod, onSelect: (SafeLoginMethod) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SafeLoginMethod.entries.forEach { item ->
            val isSelected = item == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(item) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = if (isSelected) LoginAccent else LoginMuted,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    item.title,
                    color = if (isSelected) LoginAccent else LoginMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            if (isSelected) LoginAccent else ComposeColor.Transparent,
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun LoginPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = LoginCard,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  二维码登录
// ═══════════════════════════════════════════════════════════════

@Composable
private fun QrLoginPanel(viewModel: BilibiliLoginViewModel) {
    val qrUrl by viewModel.qrCodeUrl.collectAsState()
    val qrStatus by viewModel.qrCodeStatus.collectAsState()
    val qrLeftTime by viewModel.qrCodeLeftTime.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshQrCode()
    }

    val qrBitmap = remember(qrUrl) {
        qrUrl?.let { generateQrBitmap(it) }
    }

    LoginPanel {
        Text(
            "扫码登录",
            color = LoginInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            "请使用 B 站客户端扫码确认登录",
            color = LoginMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            color = ComposeColor.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(200.dp),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LoginAccent)
                        Spacer(Modifier.height(12.dp))
                        Text("正在生成二维码…", color = LoginMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            qrStatus,
            color = LoginInk,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (qrLeftTime > 0) {
            Text(
                "剩余 ${qrLeftTime}s",
                color = LoginMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { viewModel.refreshQrCode() },
            colors = ButtonDefaults.buttonColors(
                containerColor = LoginAccent,
                contentColor = ComposeColor.White,
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text("刷新二维码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  密码登录
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PasswordLoginPanel(viewModel: BilibiliLoginViewModel) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LoginPanel {
        Text("密码登录", color = LoginInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LoginTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = "账号 / 手机号 / 邮箱",
            keyboardType = KeyboardType.Text,
        )
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "密码",
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )
        Button(
            onClick = {
                viewModel.clearError()
                viewModel.setPasswordPendingAction(username, password)
                viewModel.loginByPassword(username, password)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = LoginAccent,
                contentColor = ComposeColor.White,
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp),
        ) {
            Text("登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "登录过程中可能需要极验验证码或手机号风控验证，均由 B 站官方接口处理。",
            color = LoginMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  短信登录
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SmsLoginPanel(viewModel: BilibiliLoginViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val smsCooldown by viewModel.smsCooldown.collectAsState()

    LoginPanel {
        Text("短信验证码登录", color = LoginInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LoginTextField(
            value = phone,
            onValueChange = { phone = it },
            placeholder = "手机号",
            keyboardType = KeyboardType.Phone,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoginTextField(
                value = code,
                onValueChange = { code = it },
                placeholder = "短信验证码",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = {
                    viewModel.clearError()
                    viewModel.sendSmsCode(phone)
                },
                enabled = smsCooldown <= 0,
                colors = ButtonDefaults.textButtonColors(contentColor = LoginAccent),
            ) {
                Text(
                    if (smsCooldown > 0) "${smsCooldown}s" else "获取验证码",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Button(
            onClick = {
                viewModel.clearError()
                viewModel.loginBySms(phone, code)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = LoginAccent,
                contentColor = ComposeColor.White,
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp),
        ) {
            Text("登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "验证码 5 分钟内有效，发送前可能需要极验验证。",
            color = LoginMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Cookie 登录
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CookieLoginPanel(viewModel: BilibiliLoginViewModel) {
    var cookie by rememberSaveable { mutableStateOf("") }

    LoginPanel {
        Text("Cookie 会话登录", color = LoginInk, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "粘贴从 B 站获取的 Cookie字符串（需包含 SESSDATA）。",
            color = LoginMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        OutlinedTextField(
            value = cookie,
            onValueChange = { cookie = it },
            placeholder = { Text("SESSDATA=...; bili_jct=...; ...", color = LoginMuted, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LoginAccent,
                unfocusedBorderColor = LoginBorder,
                focusedTextColor = LoginInk,
                unfocusedTextColor = LoginInk,
                cursorColor = LoginAccent,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 4,
        )
        Button(
            onClick = {
                viewModel.clearError()
                viewModel.loginByCookie(cookie)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = LoginAccent,
                contentColor = ComposeColor.White,
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp),
        ) {
            Text("验证并登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Cookie 属于高敏感凭据，将通过加密存储保存在本地。",
            color = LoginMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  风控验证对话框
// ═══════════════════════════════════════════════════════════════

@Composable
private fun RiskVerifyDialog(
    viewModel: BilibiliLoginViewModel,
    state: BilibiliLoginViewModel.RiskVerifyState,
) {
    var code by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { viewModel.dismissRiskVerify() },
        title = {
            Text(
                "本次登录需要验证手机号",
                color = LoginInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    state.hideTel,
                    color = LoginInk,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("请输入短信验证码", color = LoginMuted, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LoginAccent,
                        unfocusedBorderColor = LoginBorder,
                        focusedTextColor = LoginInk,
                        unfocusedTextColor = LoginInk,
                        cursorColor = LoginAccent,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                TextButton(
                    onClick = { viewModel.riskSendSms() },
                    colors = ButtonDefaults.textButtonColors(contentColor = LoginAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("发送验证码", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.riskVerifySms(code) },
                colors = ButtonDefaults.textButtonColors(contentColor = LoginAccent),
            ) {
                Text("确认", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.dismissRiskVerify() },
                colors = ButtonDefaults.textButtonColors(contentColor = LoginMuted),
            ) {
                Text("取消", fontSize = 15.sp)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════
//  通用组件
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = LoginMuted, fontSize = 15.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginAccent,
            unfocusedBorderColor = LoginBorder,
            focusedTextColor = LoginInk,
            unfocusedTextColor = LoginInk,
            cursorColor = LoginAccent,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

// ═══════════════════════════════════════════════════════════════
//  二维码生成
// ═══════════════════════════════════════════════════════════════

private fun generateQrBitmap(content: String, size: Int = 512): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
