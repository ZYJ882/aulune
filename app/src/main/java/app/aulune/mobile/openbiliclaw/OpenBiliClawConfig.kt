package app.aulune.mobile.openbiliclaw

import android.content.Context
import android.content.SharedPreferences

/**
 * OpenBiliClaw 后端连接配置。
 *
 * 复刻自 OpenBiliClaw-mobile lib/api/client.dart 的连接配置。
 * 后端默认运行在本地 127.0.0.1:8420，Android 模拟器访问宿主机用 10.0.2.2。
 */
data class OpenBiliClawConfig(
    val scheme: String = "http",
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
    val sessionToken: String = "",
) {
    companion object {
        const val DEFAULT_PORT = 8420
        private const val PREFS_NAME = "openbiliclaw_config"
        private const val KEY_SCHEME = "scheme"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_SESSION = "session_token"

        /** Android 模拟器访问宿主机的特殊地址 */
        const val EMULATOR_HOST = "10.0.2.2"

        fun load(context: Context): OpenBiliClawConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return OpenBiliClawConfig(
                scheme = prefs.getString(KEY_SCHEME, "http") ?: "http",
                host = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1",
                port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
                sessionToken = prefs.getString(KEY_SESSION, "") ?: "",
            )
        }
    }

    val baseUrl: String
        get() = "$scheme://$host:$port/api"

    val originUrl: String
        get() = "$scheme://$host:$port"

    val wsUrl: String
        get() = "${if (scheme == "https") "wss" else "ws"}://$host:$port/api/runtime-stream"

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SCHEME, scheme)
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putString(KEY_SESSION, sessionToken)
            .apply()
    }

    fun withSession(token: String): OpenBiliClawConfig = copy(sessionToken = token)

    fun clearSession(): OpenBiliClawConfig = copy(sessionToken = "")
}
