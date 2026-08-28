package app.aulune.mobile

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 仅保存排查所需的脱敏事件；不写入 API Key、Cookie、令牌或完整对话内容。 */
class AuluneDiagnostics(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun record(level: String, message: String) {
        val safe = sanitize(message).trim().take(MAX_MESSAGE_LENGTH)
        if (safe.isBlank()) return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$timestamp [$level] $safe"
        val lines = preferences.getString(KEY_ENTRIES, "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()
        lines += line
        preferences.edit().putString(KEY_ENTRIES, lines.takeLast(MAX_ENTRIES).joinToString("\n")).apply()
    }

    @Synchronized
    fun read(): String = preferences.getString(KEY_ENTRIES, "").orEmpty()

    @Synchronized
    fun clear() { preferences.edit().remove(KEY_ENTRIES).apply() }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(api[-_ ]?key|authorization|bearer|token|cookie|secret)\\s*[:=]\\s*[^\\s,;]+"), "$1=[已隐藏]")
        .replace(Regex("(?i)sk-[A-Za-z0-9_-]+"), "[已隐藏]")
        .replace(Regex("(?i)AIza[A-Za-z0-9_-]+"), "[已隐藏]")

    private companion object {
        const val PREFS_NAME = "aulune-diagnostics"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 200
        const val MAX_MESSAGE_LENGTH = 600
    }
}
