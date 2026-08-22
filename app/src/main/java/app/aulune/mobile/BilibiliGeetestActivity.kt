package app.aulune.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 极验验证码 Activity。
 *
 * 复刻自 PiliPlus lib/pages/login/geetest/geetest_webview_dialog.dart。
 *
 * 修复要点：
 *  - onPageFinished 后检测 typeof Geetest，确保 JS 加载完成再初始化
 *  - 初始化失败时通过 JS Bridge 通知原生层，避免静默失败导致无限循环
 *  - verify() 触发后等待用户完成滑动，onSuccess 回调返回 validate/seccode/challenge
 */
class BilibiliGeetestActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var gt: String = ""
    private var challenge: String = ""
    private var geetestReady = false
    private var initAttempts = 0
    private val handler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gt = intent.getStringExtra(EXTRA_GT).orEmpty()
        challenge = intent.getStringExtra(EXTRA_CHALLENGE).orEmpty()

        if (gt.isBlank() || challenge.isBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        statusText = TextView(this).apply {
            text = "正在加载验证码…"
            textSize = 14f
            setTextColor(Color.GRAY)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP; topMargin = 40 }
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = android.view.Gravity.CENTER }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 页面加载完成后，检测 Geetest JS 是否可用
                    checkGeetestReady()
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(GeetestJsInterface(), "AndroidBridge")
        }

        root.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(statusText)
        root.addView(progressBar)
        setContentView(root)

        webView.loadDataWithBaseURL(
            "https://api.geetest.com/",
            BASE_HTML,
            "text/html",
            "UTF-8",
            null,
        )
    }

    /**
     * 检测 Geetest JS 是否已加载。
     * 如果未加载，延迟 300ms 重试，最多重试 10 次。
     */
    private fun checkGeetestReady() {
        if (geetestReady) return
        initAttempts++
        if (initAttempts > 10) {
            finishWithError("验证码加载超时，请检查网络后重试")
            return
        }
        webView.evaluateJavascript("typeof Geetest") { result ->
            val type = result?.trim()?.removeSurrounding("\"") ?: ""
            if (type == "function") {
                geetestReady = true
                loadGeetestConfig()
            } else {
                // JS 还没加载完，延迟重试
                handler.postDelayed({ checkGeetestReady() }, 300)
            }
        }
    }

    /**
     * 获取极验配置并初始化。
     */
    private fun loadGeetestConfig() {
        statusText.text = "正在初始化验证码…"
        CoroutineScope(Dispatchers.Main).launch {
            val configJson = fetchConfig()
            if (configJson == null) {
                finishWithError("获取验证码配置失败")
                return@launch
            }
            progressBar.visibility = android.view.View.GONE
            statusText.visibility = android.view.View.GONE

            // 初始化极验并触发验证
            val script = buildString {
                append("(function(){")
                append("try{")
                append("var R=function(n,o){AndroidBridge.postMessage(n,JSON.stringify(o));};")
                append("var config=$configJson;")
                append("var t=Geetest(config);")
                append("t.onSuccess(function(){R('success',t.getValidate());});")
                append("t.onError(function(o){R('error',o);});")
                append("t.onClose(function(o){R('close',o);});")
                append("t.onReady(function(){try{t.verify();}catch(e){R('error',{message:'verify failed:'+e.message});}});")
                append("}catch(e){AndroidBridge.postMessage('error',JSON.stringify({message:'init failed:'+e.message}));}")
                append("})();")
            }
            webView.evaluateJavascript(script, null)
        }
    }

    private suspend fun fetchConfig(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.geetest.com/gettype.php?gt=$gt"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                // 返回格式: ({...})
                val jsonStr = if (body.startsWith("(") && body.endsWith(")")) {
                    body.substring(1, body.length - 1)
                } else {
                    body
                }
                val json = JSONObject(jsonStr)
                if (json.optString("status") != "success") return@runCatching null
                val data = json.getJSONObject("data")
                // 合并参数（与 PiliPlus 一致）
                data.put("gt", gt)
                data.put("challenge", challenge)
                data.put("offline", false)
                data.put("new_captcha", true)
                data.put("product", "bind")
                data.put("width", "100%")
                data.put("https", true)
                data.put("protocol", "https://")
                data.toString()
            }
        }.getOrNull()
    }

    private fun finishWithSuccess(validate: String, seccode: String, challengeResult: String) {
        val data = Intent().apply {
            putExtra(RESULT_VALIDATE, validate)
            putExtra(RESULT_SECCODE, seccode)
            putExtra(RESULT_CHALLENGE, challengeResult)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishWithError(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra(RESULT_ERROR, message))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    inner class GeetestJsInterface {
        @JavascriptInterface
        fun postMessage(type: String, data: String) {
            runOnUiThread {
                when (type) {
                    "success" -> {
                        runCatching {
                            val json = JSONObject(data)
                            val validate = json.optString("geetest_validate")
                            val seccode = json.optString("geetest_seccode")
                            // 极验验证后 challenge 可能变化，优先用返回的新值
                            val challengeResult = json.optString("geetest_challenge", challenge)
                            if (validate.isNotBlank() && seccode.isNotBlank()) {
                                finishWithSuccess(validate, seccode, challengeResult)
                            } else {
                                finishWithError("验证结果无效")
                            }
                        }.getOrElse { finishWithError("解析验证结果失败: ${it.message}") }
                    }
                    "error" -> {
                        val msg = runCatching { JSONObject(data).optString("message", "未知错误") }.getOrDefault("极验错误")
                        finishWithError(msg)
                    }
                    "close" -> {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_GT = "gt"
        private const val EXTRA_CHALLENGE = "challenge"
        const val RESULT_VALIDATE = "validate"
        const val RESULT_SECCODE = "seccode"
        const val RESULT_CHALLENGE = "challenge_result"
        const val RESULT_ERROR = "error"

        private const val GEETEST_JS = "https://static.geetest.com/static/js/fullpage.0.0.0.js"

        private val BASE_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body { margin:0; padding:0; display:flex; justify-content:center; align-items:center; min-height:100vh; background:#fff; }
                    #captcha { width:100%; max-width:320px; }
                </style>
            </head>
            <body>
                <div id="captcha"></div>
                <script src="$GEETEST_JS"></script>
            </body>
            </html>
        """.trimIndent()

        fun createIntent(context: Context, gt: String, challenge: String): Intent =
            Intent(context, BilibiliGeetestActivity::class.java).apply {
                putExtra(EXTRA_GT, gt)
                putExtra(EXTRA_CHALLENGE, challenge)
            }
    }
}
