package app.aulune.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

enum class BilibiliDestination(val label: String, val url: String) {
    Login("官方登录", "https://passport.bilibili.com/login"),
    Account("账号中心", "https://account.bilibili.com/account/home"),
    Creator("创作中心", "https://member.bilibili.com/v2#/home"),
    Messages("消息", "https://message.bilibili.com/")
}

class BilibiliWebActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = intent.getStringExtra(EXTRA_DESTINATION)
            ?.let { name -> BilibiliDestination.entries.firstOrNull { it.name == name } }
            ?: BilibiliDestination.Account
        setContent { BilibiliManagerPage(initialDestination = destination, onClose = ::finish) }
    }

    companion object {
        private const val EXTRA_DESTINATION = "bilibili_destination"

        fun createIntent(context: Context, destination: BilibiliDestination): Intent =
            Intent(context, BilibiliWebActivity::class.java)
                .putExtra(EXTRA_DESTINATION, destination.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BilibiliManagerPage(initialDestination: BilibiliDestination, onClose: () -> Unit) {
    var destination by remember { mutableStateOf(initialDestination) }
    var pageLoading by remember { mutableStateOf(true) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var consentMessage by remember { mutableStateOf("账户数据读取默认关闭") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    LaunchedEffect(destination) {
        webView?.loadUrl(destination.url)
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF7857FF),
            background = Color(0xFFF8F7FF),
            surface = Color.White,
            onSurface = Color(0xFF171427)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFFF8F7FF),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("B 站账号管理")
                            Text("官方网页会话", style = MaterialTheme.typography.labelSmall, color = Color(0xFF716C84))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
                        IconButton(onClick = { showSignOutDialog = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "清除网页会话") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BilibiliDestination.entries.forEach { item ->
                        val selected = item == destination
                        Surface(
                            color = if (selected) Color(0xFFEDE9FF) else Color(0xFFF3F2F7),
                            shape = RoundedCornerShape(10.dp),
                            onClick = { destination = item }
                        ) {
                            Text(item.label, color = if (selected) Color(0xFF49329A) else Color(0xFF514D60), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    Surface(
                        color = Color(0xFFF3F2F7),
                        shape = RoundedCornerShape(10.dp),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination.url)))
                        }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("外部浏览器", color = Color(0xFF514D60))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF514D60))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFBF2)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(consentMessage, color = Color(0xFF6A4B00), style = MaterialTheme.typography.labelSmall)
                        Text("只读取账号信息、收藏夹、历史和稍后再看，不执行写操作。", color = Color(0xFF806A3D), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = {
                        consentMessage = if (BilibiliSession.captureFromOfficialWebView()) {
                            "已授权本次账户读取（仅保存在内存）"
                        } else {
                            "未检测到登录，请先在官方页面完成登录"
                        }
                    }) { Text("授权同步") }
                    TextButton(onClick = {
                        BilibiliSession.clear()
                        consentMessage = "本机账户读取授权已清除"
                    }) { Text("撤销") }
                }
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { viewContext ->
                            createBilibiliWebView(
                                context = viewContext,
                                onLoadingChanged = { pageLoading = it },
                                onOpenExternal = { uri ->
                                    viewContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            ).also {
                                webView = it
                                it.loadUrl(destination.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (pageLoading) {
                        Box(Modifier.fillMaxSize().background(Color(0xBFF8F7FF)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF7857FF))
                                Spacer(Modifier.size(12.dp))
                                Text("正在打开官方页面…", color = Color(0xFF716C84))
                            }
                        }
                    }
                }
            }
        }

        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                title = { Text("清除网页登录状态？") },
                text = { Text("这会清除Aulune内嵌账号管理页保存的网页 Cookie，并返回官方登录页；不会影响设备上的 B 站官方客户端。") },
                confirmButton = {
                    TextButton(onClick = {
                        clearWebSession(webView)
                        BilibiliSession.clear()
                        consentMessage = "网页登录状态与本机账户读取授权已清除"
                        destination = BilibiliDestination.Account
                        webView?.loadUrl("https://passport.bilibili.com/login")
                        showSignOutDialog = false
                    }) { Text("清除并退出", color = Color(0xFFB3261E)) }
                },
                dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("取消") } }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createBilibiliWebView(
    context: Context,
    onLoadingChanged: (Boolean) -> Unit,
    onOpenExternal: (Uri) -> Unit
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return if (isTrustedBilibiliUrl(uri)) false else {
                onOpenExternal(uri)
                true
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onLoadingChanged(true)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onLoadingChanged(false)
        }
    }
}

private fun isTrustedBilibiliUrl(uri: Uri): Boolean {
    val host = uri.host?.lowercase().orEmpty()
    return uri.scheme == "https" && (
        host == "bilibili.com" || host.endsWith(".bilibili.com") || host.endsWith(".hdslb.com")
    )
}

private fun clearWebSession(webView: WebView?) {
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        webView?.clearHistory()
        webView?.clearCache(true)
    }
}
