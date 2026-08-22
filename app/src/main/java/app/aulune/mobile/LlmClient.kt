package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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
            val model = settings.model.ifBlank { provider.defaultModel }
            when (provider) {
                AiProvider.OpenAI -> callOpenAi(model, settings.apiKey, messages)
                AiProvider.Claude -> callClaude(model, settings.apiKey, messages)
                AiProvider.Gemini -> callGemini(model, settings.apiKey, messages)
                AiProvider.DeepSeek -> callDeepSeek(model, settings.apiKey, messages)
            }
        }
    }

    private fun callOpenAi(model: String, apiKey: String, messages: List<ConversationMessage>): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(messages))
            put("temperature", 0.7)
        }
        val request = baseRequest("https://api.openai.com/v1/chat/completions", payload)
            .header("Authorization", "Bearer $apiKey")
            .build()
        val response = execute(request)
        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun callDeepSeek(model: String, apiKey: String, messages: List<ConversationMessage>): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(messages))
            put("temperature", 0.7)
            put("stream", false)
        }
        val request = baseRequest("https://api.deepseek.com/chat/completions", payload)
            .header("Authorization", "Bearer $apiKey")
            .build()
        val response = execute(request)
        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun callClaude(model: String, apiKey: String, messages: List<ConversationMessage>): String {
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
        val request = baseRequest("https://api.anthropic.com/v1/messages", payload)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()
        val response = execute(request)
        val blocks = JSONObject(response).getJSONArray("content")
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.getJSONObject(index)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun callGemini(model: String, apiKey: String, messages: List<ConversationMessage>): String {
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
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${URLEncoder.encode(normalizedModel, Charsets.UTF_8.name())}:generateContent?key=" +
            URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val request = baseRequest(endpoint, payload).build()
        val response = execute(request)
        val candidates = JSONObject(response).optJSONArray("candidates")
            ?: throw IOException("Gemini 未返回候选结果。")
        val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return buildString {
            for (index in 0 until parts.length()) append(parts.getJSONObject(index).optString("text"))
        }.ifBlank { "模型没有返回可显示的文本。" }
    }

    private fun openAiMessages(messages: List<ConversationMessage>): JSONArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        conversationWindow(messages).forEach { message ->
            put(JSONObject().put("role", if (message.fromUser) "user" else "assistant").put("content", message.text))
        }
    }

    private fun conversationWindow(messages: List<ConversationMessage>): List<ConversationMessage> =
        messages.takeLast(12).dropWhile { !it.fromUser }

    private fun baseRequest(url: String, payload: JSONObject): Request.Builder = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = body.take(360).ifBlank { response.message }
            throw IOException("${response.code}：$detail")
        }
        body
    }

    private companion object {
        const val systemPrompt = "你是Aulune的专注思考助手。请使用用户输入的语言，给出清晰、可靠、可执行的回答；当信息不足时先说明假设，不要编造事实。"
    }
}
