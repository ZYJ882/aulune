package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RemoteModel(val id: String, val label: String = id)

internal object LlmProtocolSupport {
    fun endpointUrl(base: String, suffix: String): String {
        val trimmed = base.trimEnd('/')
        return if (trimmed.endsWith("/$suffix")) trimmed else "$trimmed/$suffix"
    }

    fun modelsUrl(base: String): String {
        val trimmed = base.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> "${trimmed.removeSuffix("/chat/completions")}/models"
            trimmed.endsWith("/messages") -> "${trimmed.removeSuffix("/messages")}/models"
            else -> "$trimmed/models"
        }
    }

    fun dataModelIds(payload: String): List<RemoteModel> {
        val data = Json.parseToJsonElement(payload).jsonObject["data"]?.jsonArray.orEmpty()
        return data.mapNotNull { item ->
            item.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }.distinct().sorted().map(::RemoteModel)
    }

    fun geminiModelIds(payload: String): List<RemoteModel> {
        val data = Json.parseToJsonElement(payload).jsonObject["models"]?.jsonArray.orEmpty()
        return data.mapNotNull { item ->
            item.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")?.trim()?.takeIf { it.isNotBlank() }
        }.distinct().sorted().map(::RemoteModel)
    }
}

class LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        provider: AiProvider,
        settings: ProviderSettings,
        messages: List<ConversationMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先在“模型”页填写 ${provider.displayName} API Key。" }
            require(settings.effectiveBaseUrl(provider).startsWith("https://")) { "接口地址必须使用 HTTPS。" }
            val model = settings.effectiveModel(provider)
            require(model.isNotBlank()) { "请填写模型名称或从模型列表中选择。" }
            when (settings.effectiveProtocol(provider)) {
                ProviderProtocol.OpenAiCompatible -> callOpenAiCompatible(provider, settings, model, messages)
                ProviderProtocol.AnthropicMessages -> callAnthropic(provider, settings, model, messages)
                ProviderProtocol.GeminiGenerateContent -> callGemini(provider, settings, model, messages)
            }
        }
    }

    suspend fun listModels(provider: AiProvider, settings: ProviderSettings): Result<List<RemoteModel>> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先填写 API Key，再获取模型列表。" }
            require(settings.effectiveBaseUrl(provider).startsWith("https://")) { "接口地址必须使用 HTTPS。" }
            when (settings.effectiveProtocol(provider)) {
                ProviderProtocol.OpenAiCompatible -> {
                    val request = Request.Builder()
                        .url(modelsUrl(settings.effectiveBaseUrl(provider)))
                        .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                        .get().build()
                    LlmProtocolSupport.dataModelIds(execute(request))
                }
                ProviderProtocol.AnthropicMessages -> {
                    val request = Request.Builder()
                        .url(modelsUrl(settings.effectiveBaseUrl(provider)))
                        .header("x-api-key", settings.apiKey.trim())
                        .header("anthropic-version", "2023-06-01")
                        .get().build()
                    val data = JSONObject(execute(request)).optJSONArray("data") ?: JSONArray()
                    (0 until data.length()).mapNotNull { index -> data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() } }
                        .distinct().map(::RemoteModel)
                }
                ProviderProtocol.GeminiGenerateContent -> {
                    val base = settings.effectiveBaseUrl(provider).trimEnd('/')
                    val url = "$base/models?key=${URLEncoder.encode(settings.apiKey.trim(), Charsets.UTF_8.name())}"
                    LlmProtocolSupport.geminiModelIds(execute(Request.Builder().url(url).get().build()))
                }
            }
        }
    }

    private fun callOpenAiCompatible(provider: AiProvider, settings: ProviderSettings, model: String, messages: List<ConversationMessage>): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(messages))
            put("temperature", 0.7)
            put("stream", false)
        }
        val request = baseRequest(chatUrl(settings.effectiveBaseUrl(provider)), payload)
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .build()
        return JSONObject(execute(request)).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun callAnthropic(provider: AiProvider, settings: ProviderSettings, model: String, messages: List<ConversationMessage>): String {
        val history = JSONArray()
        conversationWindow(messages).forEach { message ->
            history.put(JSONObject().apply {
                put("role", if (message.fromUser) "user" else "assistant")
                put("content", message.text)
            })
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", history)
        }
        val request = baseRequest(messagesUrl(settings.effectiveBaseUrl(provider)), payload)
            .header("x-api-key", settings.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .build()
        val blocks = JSONObject(execute(request)).getJSONArray("content")
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.getJSONObject(index)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun callGemini(provider: AiProvider, settings: ProviderSettings, model: String, messages: List<ConversationMessage>): String {
        val contents = JSONArray()
        conversationWindow(messages).forEach { message ->
            contents.put(JSONObject().apply {
                put("role", if (message.fromUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", message.text)))
            })
        }
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", contents)
            put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 1024))
        }
        val normalizedModel = model.removePrefix("models/")
        val endpoint = "${settings.effectiveBaseUrl(provider).trimEnd('/')}/models/" +
            "${URLEncoder.encode(normalizedModel, Charsets.UTF_8.name())}:generateContent?key=" +
            URLEncoder.encode(settings.apiKey.trim(), Charsets.UTF_8.name())
        val candidates = JSONObject(execute(baseRequest(endpoint, payload).build())).optJSONArray("candidates")
            ?: throw IOException("Gemini 未返回候选结果。")
        val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return buildString { for (index in 0 until parts.length()) append(parts.getJSONObject(index).optString("text")) }
            .ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun chatUrl(base: String): String = LlmProtocolSupport.endpointUrl(base, "chat/completions")
    private fun messagesUrl(base: String): String = LlmProtocolSupport.endpointUrl(base, "messages")
    private fun modelsUrl(base: String): String = LlmProtocolSupport.modelsUrl(base)

    private fun openAiMessages(messages: List<ConversationMessage>): JSONArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        conversationWindow(messages).forEach { message ->
            put(JSONObject().put("role", if (message.fromUser) "user" else "assistant").put("content", message.text))
        }
    }

    private fun conversationWindow(messages: List<ConversationMessage>): List<ConversationMessage> = messages.takeLast(12).dropWhile { !it.fromUser }

    private fun baseRequest(url: String, payload: JSONObject): Request.Builder = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("${response.code}：${body.take(360).ifBlank { response.message }}")
        body
    }

    private companion object {
        const val systemPrompt = "你是Aulune的专注思考助手。请使用用户输入的语言，给出清晰、可靠、可执行的回答；当信息不足时先说明假设，不要编造事实。"
    }
}
