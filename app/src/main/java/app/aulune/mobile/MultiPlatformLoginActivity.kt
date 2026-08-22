package app.aulune.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多平台登录 Activity。
 *
 * 功能：
 *  1. 显示 11 个平台列表，标注登录状态
 *  2. 点击平台打开 WebView 进行登录
 *  3. 登录完成后自动提取 Cookie 并保存
 *  4. 调用账号连接器验证登录并爬取用户信息（昵称/头像/粉丝数等）
 */
class MultiPlatformLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    onBack = { finish() },
                    onPlatformClick = { platform ->
                        startActivity(PlatformWebLoginActivity.createIntent(this, platform))
                    },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, MultiPlatformLoginActivity::class.java)
    }
}

// ═══════════════════════════════════════════════════════════════
//  平台列表屏幕
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    onBack: () -> Unit,
    onPlatformClick: (ContentPlatform) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loggedInMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var accountInfoMap by remember { mutableStateOf<Map<String, PlatformAccountInfo>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        refreshStatus(context) { map, infoMap ->
            loggedInMap = map
            accountInfoMap = infoMap
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("多平台账号管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Text("←", modifier = Modifier
                        .padding(16.dp)
                        .clickable { onBack() }, fontSize = 20.sp)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                "已登录 ${loggedInMap.count { it.value }} / ${ContentPlatform.entries.size} 个平台",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = Color.Gray,
            )
            LazyColumn {
                items(ContentPlatform.entries) { platform ->
                    val isLoggedIn = loggedInMap[platform.id] ?: false
                    val info = accountInfoMap[platform.id]
                    PlatformRow(
                        platform = platform,
                        isLoggedIn = isLoggedIn,
                        accountInfo = info,
                        onClick = { onPlatformClick(platform) },
                        onLogout = {
                            PlatformCookieManager.clearCookie(context, platform)
                            scope.launch {
                                refreshStatus(context) { map, infoMap ->
                                    loggedInMap = map
                                    accountInfoMap = infoMap
                                }
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlatformRow(
    platform: ContentPlatform,
    isLoggedIn: Boolean,
    accountInfo: PlatformAccountInfo?,
    onClick: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 平台图标（圆形色块 + 首字母）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(platform.accent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                platform.shortLabel.take(2),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(platform.label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (isLoggedIn && accountInfo != null && accountInfo.nickname.isNotBlank()) {
                Text(
                    "${accountInfo.nickname} · 粉丝${formatCount(accountInfo.followerCount)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            } else if (isLoggedIn) {
                Text("已登录", fontSize = 12.sp, color = Color(0xFF4CAF50))
            } else {
                Text("未登录", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (isLoggedIn) {
            Text(
                "退出",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onLogout() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = Color(0xFFF44336),
            )
        } else {
            Text("登录 →", fontSize = 13.sp, color = Color(0xFF2196F3))
        }
    }
}

private suspend fun refreshStatus(
    context: Context,
    onResult: (Map<String, Boolean>, Map<String, PlatformAccountInfo>) -> Unit,
) {
    withContext(Dispatchers.IO) {
        val loggedInMap = mutableMapOf<String, Boolean>()
        val infoMap = mutableMapOf<String, PlatformAccountInfo>()
        ContentPlatform.entries.forEach { platform ->
            val cookie = PlatformCookieManager.getCookie(context, platform)
            val isLoggedIn = cookie.isNotBlank()
            loggedInMap[platform.id] = isLoggedIn
            if (isLoggedIn) {
                try {
                    val connector = PlatformAccountConnectorFactory.get(platform)
                    val info = connector.verifyLogin(cookie)
                    infoMap[platform.id] = info
                } catch (_: Exception) {
                    // 验证失败，保留默认
                }
            }
        }
        onResult(loggedInMap, infoMap)
    }
}

private fun formatCount(value: Int): String = when {
    value >= 10000 -> "${"%.1f".format(value / 10000.0)}万"
    value >= 1000 -> "${"%.1f".format(value / 1000.0)}k"
    else -> value.toString()
}

// ═══════════════════════════════════════════════════════════════
//  平台 WebView 登录 Activity
// ═══════════════════════════════════════════════════════════════

/**
 * 单个平台的 WebView 登录界面。
 * 登录完成后自动提取 Cookie、保存、验证并爬取用户信息。
 */
@OptIn(ExperimentalMaterial3Api::class)
class PlatformWebLoginActivity : ComponentActivity() {

    private lateinit var platform: ContentPlatform
    private var isLoggingIn = mutableStateOf(false)
    private var loginStatus = mutableStateOf("正在加载登录页…")
    private var accountInfo = mutableStateOf<PlatformAccountInfo?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = intent.getSerializableExtra(EXTRA_PLATFORM) as? ContentPlatform
            ?: ContentPlatform.BILIBILI

        // 清除该平台旧 Cookie，确保全新登录
        CookieManager.getInstance().removeAllCookies(null)

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("登录 ${platform.label}") },
                            navigationIcon = {
                                Text("←", modifier = Modifier
                                    .padding(16.dp)
                                    .clickable { finish() }, fontSize = 20.sp)
                            },
                        )
                    },
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            loginStatus.value = "请在页面中完成登录…"
                                            // 检查是否已登录（URL 跳转到首页）
                                            if (url != null && isLoggedInUrl(url, platform)) {
                                                extractCookieAndVerify()
                                            }
                                        }
                                    }
                                    webChromeClient = WebChromeClient()
                                    loadUrl(platform.loginUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 登录状态覆盖层
                        if (isLoggingIn.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x88000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(loginStatus.value, color = Color.White)
                                    accountInfo.value?.let { info ->
                                        if (info.nickname.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "${info.nickname} · 粉丝${formatCount(info.followerCount)}",
                                                color = Color(0xFF8BC34A),
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isLoggedInUrl(url: String, platform: ContentPlatform): Boolean {
        return when (platform) {
            ContentPlatform.BILIBILI -> url.contains("bilibili.com") && !url.contains("passport")
            ContentPlatform.DOUYIN -> url.contains("douyin.com") && !url.contains("login")
            ContentPlatform.XIAOHONGSHU -> url.contains("xiaohongshu.com") && !url.contains("login")
            ContentPlatform.ZHIHU -> url.contains("zhihu.com") && !url.contains("signin")
            ContentPlatform.WEIBO -> url.contains("weibo.com") && !url.contains("signin") && !url.contains("passport")
            ContentPlatform.YOUTUBE -> url.contains("youtube.com") && !url.contains("accounts.google.com")
            ContentPlatform.TWITTER -> url.contains("twitter.com") && !url.contains("login") && !url.contains("flow/login")
            ContentPlatform.REDDIT -> url.contains("reddit.com") && !url.contains("login")
            ContentPlatform.V2EX -> url.contains("v2ex.com") && !url.contains("signin")
            ContentPlatform.BANGUMI -> url.contains("bgm.tv") && !url.contains("login")
        }
    }

    private fun extractCookieAndVerify() {
        if (isLoggingIn.value) return
        isLoggingIn.value = true
        loginStatus.value = "正在提取登录信息…"

        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie(platform.homeUrl) ?: ""

        if (cookie.isBlank()) {
            loginStatus.value = "未检测到登录信息，请完成登录后返回"
            isLoggingIn.value = false
            return
        }

        // 保存 Cookie
        PlatformCookieManager.setCookie(this, platform, cookie)
        loginStatus.value = "Cookie 已保存，正在验证登录状态…"

        // 验证登录并爬取用户信息
        val scope = kotlinx.coroutines.GlobalScope
        scope.launch(Dispatchers.IO) {
            try {
                val connector = PlatformAccountConnectorFactory.get(platform)
                val info = connector.verifyLogin(cookie)
                accountInfo.value = info
                if (info.isLoggedIn) {
                    loginStatus.value = "登录成功！${info.nickname}"
                    // 延迟关闭
                    kotlinx.coroutines.delay(1500)
                    finish()
                } else {
                    loginStatus.value = "登录验证失败，请重试"
                    isLoggingIn.value = false
                }
            } catch (e: Exception) {
                loginStatus.value = "验证出错：${e.message}"
                isLoggingIn.value = false
            }
        }
    }

    companion object {
        private const val EXTRA_PLATFORM = "extra_platform"

        fun createIntent(context: Context, platform: ContentPlatform): Intent =
            Intent(context, PlatformWebLoginActivity::class.java).apply {
                putExtra(EXTRA_PLATFORM, platform)
            }
    }
}
