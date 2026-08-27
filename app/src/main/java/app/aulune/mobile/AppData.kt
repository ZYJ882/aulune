package app.aulune.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class SourceChannel(val label: String, val accent: Long) {
    Insight("深读", 0xFF7C5CFC),
    Brief("观察", 0xFF0EA5E9),
    Video("视频", 0xFFF97316),
    Notes("笔记", 0xFF14B8A6),
    Signal("灵感", 0xFFEC4899)
}

enum class ProviderProtocol(val label: String) {
    OpenAiCompatible("OpenAI 兼容"),
    AnthropicMessages("Anthropic Messages"),
    GeminiGenerateContent("Gemini GenerateContent")
}

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val protocol: ProviderProtocol,
    val keyHint: String
) {
    OpenAI("OpenAI", "gpt-4o-mini", "https://api.openai.com/v1", ProviderProtocol.OpenAiCompatible, "sk-…"),
    Claude("Claude", "claude-3-5-haiku-latest", "https://api.anthropic.com/v1", ProviderProtocol.AnthropicMessages, "sk-ant-…"),
    Gemini("Gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com/v1beta", ProviderProtocol.GeminiGenerateContent, "AIza…"),
    DeepSeek("DeepSeek", "deepseek-v4-flash", "https://api.deepseek.com", ProviderProtocol.OpenAiCompatible, "sk-…"),
    Zhipu("智谱 GLM", "glm-5.3", "https://api.z.ai/api/paas/v4", ProviderProtocol.OpenAiCompatible, "…"),
    Kimi("Kimi", "kimi-k2.6", "https://api.moonshot.ai/v1", ProviderProtocol.OpenAiCompatible, "sk-…"),
    OpenRouter("OpenRouter", "openai/gpt-4o-mini", "https://openrouter.ai/api/v1", ProviderProtocol.OpenAiCompatible, "sk-or-…"),
    Custom("自定义", "", "https://", ProviderProtocol.OpenAiCompatible, "按服务商要求填写")
}

data class ProviderSettings(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val protocol: ProviderProtocol? = null
) {
    fun effectiveModel(provider: AiProvider): String = model.trim().ifBlank { provider.defaultModel }
    fun effectiveBaseUrl(provider: AiProvider): String = baseUrl.trim().ifBlank { provider.defaultBaseUrl }
    /** 预设服务商使用官方默认协议；只有自定义服务商允许用户选择协议。 */
    fun effectiveProtocol(provider: AiProvider): ProviderProtocol = when (provider) {
        AiProvider.Custom -> protocol ?: provider.protocol
        else -> provider.protocol
    }
}

data class ProviderProfilesSnapshot(
    val selectedProvider: AiProvider = AiProvider.OpenAI,
    val profiles: Map<AiProvider, ProviderSettings> = emptyMap()
)

internal object ProviderProfilesCodec {
    fun decode(encodedProfiles: String, selected: String?): ProviderProfilesSnapshot {
        val root = Json.parseToJsonElement(encodedProfiles).jsonObject
        val profiles = buildMap {
            AiProvider.entries.forEach { provider ->
                val item = root[provider.name]?.jsonObject ?: return@forEach
                val protocol = item["protocol"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { ProviderProtocol.valueOf(it) }.getOrNull() }
                put(provider, ProviderSettings(
                    apiKey = item["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    model = item["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    baseUrl = item["baseUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    protocol = protocol
                ))
            }
        }
        return ProviderProfilesSnapshot(
            selectedProvider = selected?.let { runCatching { AiProvider.valueOf(it) }.getOrDefault(AiProvider.OpenAI) } ?: AiProvider.OpenAI,
            profiles = profiles
        )
    }

    fun encode(snapshot: ProviderProfilesSnapshot): String = buildJsonObject {
        snapshot.profiles.forEach { (provider, value) ->
            put(provider.name, buildJsonObject {
                put("apiKey", value.apiKey.trim())
                put("model", value.model.trim())
                put("baseUrl", value.baseUrl.trim())
                put("protocol", value.protocol?.name.orEmpty())
            })
        }
    }.toString()
}

/** 服务商 Key、模型与自定义端点只保存在当前设备的 Android Keystore 加密偏好中。 */
class SecureProviderProfiles(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "aulune-provider-profiles",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): ProviderProfilesSnapshot = runCatching {
        ProviderProfilesCodec.decode(
            encodedProfiles = preferences.getString("profiles", "{}") ?: "{}",
            selected = preferences.getString("selected", AiProvider.OpenAI.name)
        )
    }.getOrDefault(ProviderProfilesSnapshot())

    fun save(snapshot: ProviderProfilesSnapshot): Boolean =
        preferences.edit()
            .putString("profiles", ProviderProfilesCodec.encode(snapshot))
            .putString("selected", snapshot.selectedProvider.name)
            .commit()
}

/** A UI projection of a persisted local content item. */
data class CuratedItem(
    val id: String,
    val channel: SourceChannel,
    val title: String,
    val source: String,
    val readTime: String,
    val insight: String,
    val theme: String,
    val url: String,
    val gradientStart: Long,
    val gradientEnd: Long,
    val marked: Boolean = false,
    val saved: Boolean = false,
    val lifecycle: InterestLifecycle = InterestLifecycle.Trial,
    val sourceKey: String = "",
    val authorKey: String = "",
    val seriesKey: String = "",
    val topicGroup: String = "",
    /** 仅保存来源响应提供的 HTTP(S) 缩略图地址；空值时 UI 使用本机渐变回退。 */
    val thumbnailUrl: String = ""
)

data class ConversationMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: String
)

internal fun ProviderProfilesSnapshot.withCloudConfigFallback(cloud: CloudAiConfig): ProviderProfilesSnapshot {
    if (cloud.apiKey.isBlank()) return this
    val existing = profiles[cloud.provider]
    if (existing?.apiKey?.isNotBlank() == true) return this
    return copy(
        selectedProvider = if (profiles.isEmpty()) cloud.provider else selectedProvider,
        profiles = profiles + (cloud.provider to cloud.providerSettings()),
    )
}

class AuluneStore(context: Context) {
    private val secureProfiles = SecureProviderProfiles(context)
    private val secureCloudSettings = SecureCloudAiSettings(context)
    val messages = mutableStateListOf<ConversationMessage>()
    val providerSettings = mutableStateMapOf<AiProvider, ProviderSettings>()

    var selectedProvider by mutableStateOf(AiProvider.OpenAI)
        private set
    var isGenerating by mutableStateOf(false)
    var aiStatus by mutableStateOf("本地对话模式")
        private set

    init {
        val storedSnapshot = secureProfiles.load()
        val snapshot = storedSnapshot.withCloudConfigFallback(secureCloudSettings.load())
        providerSettings.putAll(snapshot.profiles)
        selectedProvider = snapshot.selectedProvider
        if (snapshot != storedSnapshot) secureProfiles.save(snapshot)
        val active = providerSettings[selectedProvider]
        aiStatus = if (active?.apiKey.isNullOrBlank()) "本地对话模式" else "${selectedProvider.displayName} 已就绪"
    }

    fun settingsFor(provider: AiProvider): ProviderSettings = providerSettings[provider] ?: ProviderSettings(
        model = provider.defaultModel,
        baseUrl = provider.defaultBaseUrl,
        protocol = provider.protocol
    )

    fun selectProvider(provider: AiProvider) {
        selectedProvider = provider
        persist()
    }

    fun setProviderSettings(provider: AiProvider, settings: ProviderSettings) {
        updateProviderSettings(provider, settings, updateStatus = true)
    }

    /** 清除指定服务商的本机配置，不影响其他已保存的服务商。 */
    fun clearProviderSettings(provider: AiProvider) {
        providerSettings.remove(provider)
        if (selectedProvider == provider) {
            selectedProvider = providerSettings.keys.firstOrNull() ?: AiProvider.OpenAI
        }
        aiStatus = "已清除 ${provider.displayName} 的本机配置"
        if (!persist()) {
            aiStatus = "本机配置清除失败，请检查设备存储后重试。"
        }
    }

    /**
     * 保存用户在模型工作台输入的草稿，不会启用云端调用。
     * 这让“获取模型列表”后的关闭或进程重建不再丢失 Key、地址和模型名称。
     */
    fun saveProviderDraft(provider: AiProvider, settings: ProviderSettings) {
        updateProviderSettings(provider, settings, updateStatus = false)
    }

    private fun updateProviderSettings(provider: AiProvider, settings: ProviderSettings, updateStatus: Boolean) {
        val normalized = settings.copy(
            protocol = if (provider == AiProvider.Custom) settings.effectiveProtocol(provider) else provider.protocol
        )
        providerSettings[provider] = normalized
        selectedProvider = provider
        if (updateStatus) {
            aiStatus = if (normalized.apiKey.isBlank()) "本地对话模式" else "${provider.displayName} 已就绪"
        }
        if (!persist()) {
            aiStatus = "本机配置保存失败，请检查设备存储后重试。"
        }
    }

    fun updateGenerating(value: Boolean) {
        isGenerating = value
        if (value) aiStatus = "${selectedProvider.displayName} 正在思考"
    }

    fun updateAiStatus(value: String) { aiStatus = value }

    private fun persist(): Boolean = secureProfiles.save(ProviderProfilesSnapshot(selectedProvider, providerSettings.toMap()))
}
