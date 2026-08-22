package app.aulune.mobile

import android.webkit.CookieManager

/**
 * 只在用户明确点击“允许同步”后，把官方 WebView 会话暂存于当前 App 进程内存。
 * 不写入磁盘、不导出、不显示原始 Cookie；进程结束或用户清除授权后即失效。
 */
object BilibiliSession {
    @Volatile
    private var inMemoryCookie: String? = null

    fun captureFromOfficialWebView(): Boolean {
        val cookies = listOf(
            CookieManager.getInstance().getCookie("https://www.bilibili.com"),
            CookieManager.getInstance().getCookie("https://api.bilibili.com")
        ).filterNotNull()
            .flatMap { it.split(';').map(String::trim) }
            .filter { it.substringBefore('=').isNotBlank() }
            .distinctBy { it.substringBefore('=') }
            .joinToString("; ")
        if (!cookies.containsCookie("SESSDATA")) return false
        inMemoryCookie = cookies
        return true
    }

    fun currentCookie(): String? = inMemoryCookie

    fun clear() {
        inMemoryCookie = null
    }

    fun isAuthorized(): Boolean = inMemoryCookie?.containsCookie("SESSDATA") == true

    private fun String.containsCookie(name: String): Boolean = split(';')
        .asSequence()
        .map { it.trim().substringBefore('=') }
        .any { it == name }
}
