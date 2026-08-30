package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RemoteModel(val id: String, val label: String = id)

internal object LlmResponseParser {
    fun openAiText(payload: String): String {
        val json = parseObject(payload, "OpenAI 兼容接口返回了无法解析的响应。")
        val choices = json["choices"]?.jsonArray
            ?: throw IOException("OpenAI 兼容接口未返回 choices：${errorDetail(json)}")
        if (choices.isEmpty()) throw IOException("OpenAI 兼容接口返回了空 choices：${errorDetail(json)}")
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw IOException("OpenAI 兼容接口的 choices 缺少 message。")
        val content = message["content"]
        val text = when (content) {
            is kotlinx.serialization.json.JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.joinToString("") { part ->
                val item = part.jsonObject
                if (item["type"]?.jsonPrimitive?.contentOrNull == "text") item["text"]?.jsonPrimitive?.contentOrNull.orEmpty() else ""
            }
            else -> ""
        }.trim()
        return text.ifBlank { throw IOException("模型返回了空内容。") }
    }

    fun errorDetail(payload: String): String = errorDetail(parseObject(payload, ""))

    private fun errorDetail(payload: JsonObject): String {
        val error = payload["error"]?.jsonObject
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { payload["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty() }
        val code = error?.get("code")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return listOf(code.takeIf { it.isNotBlank() }, message.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString("：")
            .ifBlank { "服务商未提供错误详情，请检查 API Key、模型、限额或接口地址。" }
            .take(360)
    }

    private fun parseObject(payload: String, message: String): JsonObject = runCatching {
        Json.parseToJsonElement(payload).jsonObject
    }.getOrElse { throw IOException(message.ifBlank { "接口返回了无法解析的错误响应。" }) }
}

internal object LlmErrorPolicy {
    fun isTransient(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return Regex("HTTP\\s+(429|5\\d{2})").containsMatchIn(message)
    }

    fun userMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            Regex("HTTP\\s+502", RegexOption.IGNORE_CASE).containsMatchIn(message) && message.contains("overload", ignoreCase = true) ->
                "OpenRouter 上游模型暂时过载（502），本次已自动重试 3 次仍未成功。请稍后重试，或更换一个模型。"
            Regex("HTTP\\s+429", RegexOption.IGNORE_CASE).containsMatchIn(message) ->
                "OpenRouter 当前触发限流（429），本次已自动重试 3 次仍未成功。请稍后重试或降低请求频率。"
            else -> message.ifBlank { "云端模型调用失败，请检查模型、API Key、额度或接口地址。" }
        }
    }
}

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
        messages: List<ConversationMessage>,
        structuredJson: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先在“模型”页填写 ${provider.displayName} API Key。" }
            require(settings.effectiveBaseUrl(provider).startsWith("https://")) { "接口地址必须使用 HTTPS。" }
            val model = settings.effectiveModel(provider)
            require(model.isNotBlank()) { "请填写模型名称或从模型列表中选择。" }
            retryTransient {
                when (settings.effectiveProtocol(provider)) {
                    ProviderProtocol.OpenAiCompatible -> callOpenAiCompatible(provider, settings, model, messages, structuredJson)
                    ProviderProtocol.AnthropicMessages -> callAnthropic(provider, settings, model, messages)
                    ProviderProtocol.GeminiGenerateContent -> callGemini(provider, settings, model, messages, structuredJson)
                }
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IOException(LlmErrorPolicy.userMessage(it), it)) }
        )
    }

    /**
     * v2.0.0 流式 SSE 输出：逐 token 返回，UI 可实现打字机效果。
     * - 仅支持 OpenAI 兼容协议（最广覆盖：OpenAI/OpenRouter/DeepSeek/Kimi/GLM）
     * - Claude / Gemini 仍走 [generate]；调用方在 collect 失败时回退到 [generate]
     * - 流以 `[DONE]` 结尾；遇到无法解析的行会忽略
     */
    fun generateStream(
        provider: AiProvider,
        settings: ProviderSettings,
        messages: List<ConversationMessage>,
        structuredJson: Boolean = false,
    ): Flow<String> = callbackFlow {
        require(settings.apiKey.isNotBlank()) { "请先填写 ${provider.displayName} API Key。" }
        require(settings.effectiveBaseUrl(provider).startsWith("https://")) { "接口地址必须使用 HTTPS。" }
        val model = settings.effectiveModel(provider)
        require(model.isNotBlank()) { "请填写模型名称。" }

        if (settings.effectiveProtocol(provider) != ProviderProtocol.OpenAiCompatible) {
            // 非 OpenAI 兼容协议不支持流式，回退到一次性返回
            val result = generate(provider, settings, messages, structuredJson)
            result.onSuccess { trySend(it) }
            result.onFailure { close(IOException(LlmErrorPolicy.userMessage(it), it)) }
            close()
            return@callbackFlow
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(messages))
            put("temperature", 0.7)
            put("stream", true)
            if (structuredJson) put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = baseRequest(chatUrl(settings.effectiveBaseUrl(provider)), payload)
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .build()

        val call = client.newCall(request)
        val response = try { call.execute() } catch (e: IOException) {
            close(IOException(LlmErrorPolicy.userMessage(e), e)); return@callbackFlow
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                close(IOException("HTTP ${resp.code}：${resp.message}")); return@callbackFlow
            }
            val body: ResponseBody = resp.body ?: run {
                close(IOException("流式响应无 body")); return@callbackFlow
            }
            val reader = BufferedReader(body.charStream())
            try {
                val buffer = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val parsed = parseStreamChunk(data)
                    if (parsed.isNotEmpty()) {
                        trySend(parsed)
                        buffer.append(parsed)
                    }
                }
            } catch (e: Exception) {
                close(IOException("流式读取中断：${e.message ?: "未知错误"}", e)); return@callbackFlow
            }
            close()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseStreamChunk(data: String): String {
        return runCatching {
            val json = JSONObject(data)
            val choices = json.optJSONArray("choices") ?: return@runCatching ""
            if (choices.length() == 0) return@runCatching ""
            val choice = choices.optJSONObject(0) ?: return@runCatching ""
            val delta = choice.optJSONObject("delta") ?: return@runCatching ""
            delta.optString("content", "")
        }.getOrDefault("")
    }

    /**
     * v2.0.0 LLM failover chain：按顺序尝试主 provider + 备选 providers，
     * 第一个成功即返回；全部失败时返回最后一个错误。
     *
     * 对齐 OpenBiliClaw 的"同类型 LLM 多渠道故障切换链"。
     * 调用方从 AuluneStore.providerSettings 里筛出所有已配置 Key 的 providers 传入。
     */
    suspend fun generateWithFailover(
        primary: Pair<AiProvider, ProviderSettings>,
        alternatives: List<Pair<AiProvider, ProviderSettings>>,
        messages: List<ConversationMessage>,
        structuredJson: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        val chain = listOf(primary) + alternatives.filter { it.first != primary.first }
        var lastError: Throwable? = null
        for ((provider, settings) in chain) {
            if (settings.apiKey.isBlank()) continue
            val result = generate(provider, settings, messages, structuredJson)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()
        }
        Result.failure(lastError ?: IOException("无可用的 LLM provider；请先在“模型”页配置 API Key。"))
    }

    private suspend fun retryTransient(block: () -> String): String {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (error: IOException) {
                lastError = error
                if (!LlmErrorPolicy.isTransient(error) || attempt == 2) throw error
                delay(700L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("云端模型调用失败。")
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

    private fun callOpenAiCompatible(provider: AiProvider, settings: ProviderSettings, model: String, messages: List<ConversationMessage>, structuredJson: Boolean): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(messages))
            put("temperature", 0.7)
            put("stream", false)
            if (structuredJson) put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = baseRequest(chatUrl(settings.effectiveBaseUrl(provider)), payload)
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .build()
        return LlmResponseParser.openAiText(execute(request))
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

    private fun callGemini(provider: AiProvider, settings: ProviderSettings, model: String, messages: List<ConversationMessage>, structuredJson: Boolean): String {
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
            put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 1024).apply {
                if (structuredJson) put("responseMimeType", "application/json")
            })
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
        if (!response.isSuccessful) {
            val detail = runCatching { LlmResponseParser.errorDetail(body) }.getOrDefault(body.take(180))
            throw IOException("HTTP ${response.code}：$detail")
        }
        if (body.isBlank()) throw IOException("接口返回了空响应。")
        body
    }

    private companion object {
        // v2.0.0：升级为苏格拉底式追问 system prompt
        const val systemPrompt = SocraticPromptPolicy.conversationalSystemPrompt.trim()
    }
}
