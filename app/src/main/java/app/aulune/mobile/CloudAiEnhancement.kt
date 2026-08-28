package app.aulune.mobile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 仅保存用户主动启用的云端增强配置；Key 由 Android Keystore 保护。 */
data class CloudAiConfig(
    val provider: AiProvider = AiProvider.OpenAI,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val protocol: ProviderProtocol? = null,
    val enabled: Boolean = false
) {
    val effectiveModel: String get() = model.ifBlank { provider.defaultModel }
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.defaultBaseUrl }
    val effectiveProtocol: ProviderProtocol get() = ProviderSettings(protocol = protocol).effectiveProtocol(provider)
    val isUsable: Boolean get() = enabled && apiKey.isNotBlank()
    fun providerSettings(): ProviderSettings = ProviderSettings(apiKey, effectiveModel, effectiveBaseUrl, effectiveProtocol)
}

/**
 * 将用户当前输入归一化为一份独立的云端增强配置。
 * 空 Key 不会隐式继承上一服务商的 Key，也不能启用云端调用。
 */
internal fun normalizedCloudAiConfig(
    provider: AiProvider,
    apiKey: String,
    model: String,
    baseUrl: String = "",
    protocol: ProviderProtocol? = null,
    enable: Boolean,
): CloudAiConfig {
    val normalizedKey = apiKey.trim()
    return CloudAiConfig(
        provider = provider,
        apiKey = normalizedKey,
        model = model.trim().ifBlank { provider.defaultModel },
        baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
        protocol = protocol ?: provider.protocol,
        enabled = enable && normalizedKey.isNotBlank(),
    )
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
            baseUrl = preferences.getString("base_url", "").orEmpty(),
            protocol = preferences.getString("protocol", "").orEmpty().takeIf { it.isNotBlank() }?.let(ProviderProtocol::valueOf),
            enabled = preferences.getBoolean("enabled", false)
        )
    }.getOrDefault(CloudAiConfig())

    fun save(config: CloudAiConfig) {
        val normalizedProtocol = if (config.provider == AiProvider.Custom) config.effectiveProtocol else config.provider.protocol
        preferences.edit()
            .putString("provider", config.provider.name)
            .putString("api_key", config.apiKey.trim())
            .putString("model", config.model.trim())
            .putString("base_url", config.baseUrl.trim())
            .putString("protocol", normalizedProtocol.name)
            .putBoolean("enabled", config.enabled)
            .commit()
    }

    fun clear() { preferences.edit().clear().commit() }
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

internal object CloudJsonResponseParser {
    fun extractObject(text: String): JsonObject {
        val candidate = balancedObject(text)
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
        val variants = listOf(
            candidate,
            candidate.replace(Regex(",\\s*([}])"), "$1"),
            candidate.replace(Regex("'([^']*)'\\s*:"), "\\\"$1\\\":").replace(Regex(":\\s*'([^']*)'"), ":\\\"$1\\\"")
                .replace(Regex(",\\s*([}])"), "$1"),
            candidate.replace(Regex("([,{]\\s*)([A-Za-z][A-Za-z0-9_]*)\\s*:"), "$1\\\"$2\\\":")
                .replace(Regex(",\\s*([}])"), "$1")
        )
        variants.firstNotNullOfOrNull { value ->
            runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull()
        }?.let { return it }
        throw IllegalArgumentException("云端模型返回的 JSON 格式无效；请重试或更换支持结构化输出的模型。")
    }

    private fun balancedObject(text: String): String {
        val start = text.indexOf('{')
        require(start >= 0) { "云端模型未返回可解析 JSON。" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        throw IllegalArgumentException("云端模型未返回完整 JSON 对象。")
    }

    fun stringValue(payload: JsonObject, key: String): String =
        payload[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

/** 只在用户明确点击后挑选近期、可见且尚未经过云端整理的内容。 */
internal object CloudManualOrganizePolicy {
    const val DefaultBatchLimit = 5

    fun selectCandidates(items: List<LocalContentEntity>, limit: Int = DefaultBatchLimit): List<LocalContentEntity> =
        items.asSequence()
            .filter { !it.hidden && it.title.isNotBlank() && it.analysisSource != "cloud" }
            .sortedByDescending { it.updatedAt }
            .take(limit.coerceAtLeast(0))
            .toList()

    fun completionMessage(requested: Int, succeeded: Int, failed: Int): String = when {
        requested <= 0 -> "没有需要云端整理的内容；当前信息流继续使用本机规则。"
        succeeded == requested -> "已整理 $succeeded 条内容；分类和候选理由已保存到本机，排序仍由本机规则执行。"
        succeeded > 0 -> "已整理 $succeeded/$requested 条内容，另有 $failed 条失败；失败内容保持本机规则。"
        else -> "本轮 $requested 条内容均未完成云端整理；已保留本机规则，请检查 Key、模型和网络。"
    }
}

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
        return client.generate(config.provider, config.providerSettings(), messages)
            .mapCatching { text ->
                val payload = CloudJsonResponseParser.extractObject(text)
                CloudContentAnalysis(
                    theme = CloudJsonResponseParser.stringValue(payload, "theme").trim().take(48).ifBlank { item.theme },
                    topicGroup = CloudJsonResponseParser.stringValue(payload, "topicGroup").trim().take(16).ifBlank { item.topicGroup },
                    seriesKey = CloudJsonResponseParser.stringValue(payload, "seriesKey").trim().take(64),
                    insight = CloudJsonResponseParser.stringValue(payload, "insight").trim().take(80)
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
        return client.generate(config.provider, config.providerSettings(), messages)
            .mapCatching { text ->
                val payload = CloudJsonResponseParser.extractObject(text)
                CloudProfileAnalysis(
                    valuesCandidate = CloudJsonResponseParser.stringValue(payload, "valuesCandidate").trim().take(120),
                    coreCandidate = CloudJsonResponseParser.stringValue(payload, "coreCandidate").trim().take(120)
                )
            }
    }

}
