package app.aulune.mobile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** 仅保存用户主动启用的云端增强配置；Key 由 Android Keystore 保护。 */
data class CloudAiConfig(
    val provider: AiProvider = AiProvider.OpenAI,
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = false
) {
    val effectiveModel: String get() = model.ifBlank { provider.defaultModel }
    val isUsable: Boolean get() = enabled && apiKey.isNotBlank()
}

class SecureCloudAiSettings(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "aulune-cloud-ai",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): CloudAiConfig = runCatching {
        CloudAiConfig(
            provider = AiProvider.valueOf(preferences.getString("provider", AiProvider.OpenAI.name).orEmpty()),
            apiKey = preferences.getString("api_key", "").orEmpty(),
            model = preferences.getString("model", "").orEmpty(),
            enabled = preferences.getBoolean("enabled", false)
        )
    }.getOrDefault(CloudAiConfig())

    fun save(config: CloudAiConfig) {
        preferences.edit()
            .putString("provider", config.provider.name)
            .putString("api_key", config.apiKey.trim())
            .putString("model", config.model.trim())
            .putBoolean("enabled", config.enabled)
            .apply()
    }

    fun clear() { preferences.edit().clear().apply() }
}

data class CloudContentAnalysis(
    val theme: String,
    val topicGroup: String,
    val seriesKey: String,
    val insight: String
)

data class CloudProfileAnalysis(
    val valuesCandidate: String,
    val coreCandidate: String
)

/**
 * 云端增强仅发送用户主动请求分析的内容元数据和聚合兴趣，不发送 B 站 Cookie、账号令牌、
 * 原始观看历史或设备标识。LLM 输出解析失败时返回 failure，由本机规则保持结果不变。
 */
class CloudAiSemanticService(private val client: LlmClient = LlmClient()) {
    suspend fun analyzeContent(config: CloudAiConfig, item: LocalContentEntity): Result<CloudContentAnalysis> {
        if (!config.isUsable) return Result.failure(IllegalStateException("请先在“模型”页启用云端 AI 增强并保存 API Key。"))
        val messages = listOf(
            ConversationMessage(1, false, "你是内容分类器。只返回 JSON，不要 Markdown。字段：theme（格式为 大类 · 小类）、topicGroup（2-6 字大类）、seriesKey（稳定系列名；不确定为空字符串）、insight（不超过 42 个中文字符的推荐理由）。", ""),
            ConversationMessage(2, true, "标题：${item.title}\n摘要：${item.summary.take(900)}\n来源：${item.source}\n当前规则主题：${item.theme}", "")
        )
        return client.generate(config.provider, ProviderSettings(config.apiKey, config.effectiveModel), messages)
            .mapCatching { text ->
                val payload = JSONObject(extractJson(text))
                CloudContentAnalysis(
                    theme = payload.optString("theme").trim().take(48).ifBlank { item.theme },
                    topicGroup = payload.optString("topicGroup").trim().take(16).ifBlank { item.topicGroup },
                    seriesKey = payload.optString("seriesKey").trim().take(64),
                    insight = payload.optString("insight").trim().take(80)
                )
            }
    }

    suspend fun buildProfileCandidate(
        config: CloudAiConfig,
        interests: List<InterestEntity>,
        intent: SessionIntent,
        eventCount: Int
    ): Result<CloudProfileAnalysis> {
        if (!config.isUsable) return Result.failure(IllegalStateException("请先在“模型”页启用云端 AI 增强并保存 API Key。"))
        val evidence = interests.filter { it.lifecycle.toLifecycle() != InterestLifecycle.Archived }
            .sortedByDescending { it.weight }
            .take(8)
            .joinToString("；") { "${it.theme}(${String.format(java.util.Locale.US, "%.1f", it.weight)}分/${it.evidenceCount}条)" }
        val messages = listOf(
            ConversationMessage(1, false, "你是用户画像候选生成器。只返回 JSON，不要 Markdown。字段：valuesCandidate（不超过80字，必须表述为可供用户确认的长期方向，不是事实断言）、coreCandidate（不超过80字，必须明确为可选边界建议）。不可编造个人敏感信息。", ""),
            ConversationMessage(2, true, "会话意图：${intent.label}\n本机事件数：$eventCount\n聚合兴趣证据：$evidence", "")
        )
        return client.generate(config.provider, ProviderSettings(config.apiKey, config.effectiveModel), messages)
            .mapCatching { text ->
                val payload = JSONObject(extractJson(text))
                CloudProfileAnalysis(
                    valuesCandidate = payload.optString("valuesCandidate").trim().take(120),
                    coreCandidate = payload.optString("coreCandidate").trim().take(120)
                )
            }
    }

    private fun extractJson(text: String): String {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "云端模型未返回可解析 JSON。" }
        return trimmed.substring(start, end + 1)
    }
}
