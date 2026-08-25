package app.aulune.mobile

import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * 集中处理应用内的外部链接边界。
 *
 * 只允许明确的 HTTP/HTTPS URL，避免界面层分别解析不受支持的 URI。
 * 实际启动 Activity 仍由调用方使用 runCatching 包裹，以覆盖设备不存在处理程序的情况。
 */
object ExternalUrlPolicy {
    fun isAllowed(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        return uri.host?.isNotBlank() == true && uri.scheme?.lowercase() in setOf("http", "https")
    }

    fun viewIntent(rawUrl: String): Intent? {
        val normalized = rawUrl.trim()
        if (!isAllowed(normalized)) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    }
}
