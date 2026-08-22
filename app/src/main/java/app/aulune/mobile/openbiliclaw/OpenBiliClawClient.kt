package app.aulune.mobile.openbiliclaw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenBiliClaw 后端 HTTP 客户端。
 *
 * 复刻自 OpenBiliClaw-mobile lib/api/client.dart。
 * 支持 session token 认证、自动捕获 Set-Cookie、WebSocket 实时事件流。
 */
class OpenBiliClawClient(
    private var config: OpenBiliClawConfig,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var onSessionChanged: ((String) -> Unit)? = null

    fun updateConfig(newConfig: OpenBiliClawConfig) {
        config = newConfig
    }

    fun getConfig(): OpenBiliClawConfig = config

    // ═══════════════════════════════════════════════════════════
    //  HTTP 方法
    // ═══════════════════════════════════════════════════════════

    suspend fun get(path: String, timeoutSeconds: Int = 10): JsonObject =
        request("GET", path, null, timeoutSeconds)

    suspend fun post(path: String, body: Map<String, Any?>? = null, timeoutSeconds: Int = 10): JsonObject =
        request("POST", path, body, timeoutSeconds)

    suspend fun put(path: String, body: Map<String, Any?>? = null, timeoutSeconds: Int = 60): JsonObject =
        request("PUT", path, body, timeoutSeconds)

    suspend fun delete(path: String, timeoutSeconds: Int = 10): JsonObject =
        request("DELETE", path, null, timeoutSeconds)

    private suspend fun request(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        timeoutSeconds: Int,
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val requestBuilder = Request.Builder().url(url).method(method, null)
        applyHeaders(requestBuilder)
        if (body != null && method != "GET" && method != "DELETE") {
            val jsonBody = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                body.toJsonElement(),
            )
            requestBuilder.method(method, jsonBody.toRequestBody(jsonMediaType))
        }
        val client = if (timeoutSeconds != 10) {
            httpClient.newBuilder()
                .callTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
        } else {
            httpClient
        }
        val response = client.newCall(requestBuilder.build()).await()
        handleResponse(response)
    }

    // ═══════════════════════════════════════════════════════════
    //  健康检查
    // ═══════════════════════════════════════════════════════════

    suspend fun checkHealth(
        overrideHost: String? = null,
        overridePort: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val host = overrideHost ?: config.host
        val port = overridePort ?: config.port
        val url = "${config.scheme}://$host:$port/api/health"
        return@withContext try {
            val request = Request.Builder().url(url).get()
                .header("X-OBC-Auth", "1")
                .build()
            val response = httpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
                .newCall(request).await()
            response.code == 200
        } catch (_: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  WebSocket 实时事件流
    // ═══════════════════════════════════════════════════════════

    fun connectRuntimeStream(listener: RuntimeStreamListener): WebSocket {
        val request = Request.Builder().url(config.wsUrl).build()
        val headers = mutableMapOf<String, String>()
        if (config.sessionToken.isNotEmpty()) {
            headers["Cookie"] = "obc_session=${config.sessionToken}"
            headers["Origin"] = config.originUrl
        }
        return httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val element = json.parseToJsonElement(text)
                    if (element is JsonObject) {
                        listener.onEvent(element)
                    }
                } catch (_: Exception) {
                    // 忽略非 JSON 消息
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosing(code, reason)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }
        })
    }

    // ═══════════════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════════════

    private fun buildUrl(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "${config.baseUrl}$normalized"
    }

    private fun applyHeaders(builder: Request.Builder) {
        builder.header("Content-Type", "application/json")
        builder.header("X-OBC-Auth", "1")
        if (config.sessionToken.isNotEmpty()) {
            builder.header("Cookie", "obc_session=${config.sessionToken}")
            builder.header("Origin", config.originUrl)
        }
    }

    private fun handleResponse(response: Response): JsonObject {
        response.use { resp ->
            // 捕获 Set-Cookie 中的 session token
            val setCookie = resp.header("Set-Cookie")
            if (setCookie != null) {
                val match = Regex("obc_session=([^;]*)").find(setCookie)
                if (match != null) {
                    val token = match.groupValues[1]
                    if (token.isNotEmpty() && token != config.sessionToken) {
                        config = config.withSession(token)
                        onSessionChanged?.invoke(token)
                    } else if (token.isEmpty()) {
                        config = config.clearSession()
                        onSessionChanged?.invoke("")
                    }
                }
            }
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                config = config.clearSession()
                onSessionChanged?.invoke("")
                throw OpenBiliClawApiException(resp.code, "未授权，请重新登录")
            }
            if (resp.code >= 400) {
                throw OpenBiliClawApiException(resp.code, body.ifBlank { "HTTP ${resp.code}" })
            }
            if (body.isBlank()) return JsonObject(emptyMap())
            return try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
        }
    }

    private fun Map<String, Any?>.toJsonElement(): JsonObject {
        val map = this.mapValues { (_, v) ->
            when (v) {
                is String -> kotlinx.serialization.json.JsonPrimitive(v)
                is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                is List<*> -> kotlinx.serialization.json.JsonArray(
                    v.map { item ->
                        when (item) {
                            is String -> kotlinx.serialization.json.JsonPrimitive(item)
                            is Number -> kotlinx.serialization.json.JsonPrimitive(item)
                            is Boolean -> kotlinx.serialization.json.JsonPrimitive(item)
                            else -> kotlinx.serialization.json.JsonNull
                        }
                    },
                )
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    (v as Map<String, Any?>).toJsonElement()
                }
                else -> kotlinx.serialization.json.JsonNull
            }
        }
        return JsonObject(map)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }
}

/** WebSocket 事件流监听器 */
interface RuntimeStreamListener {
    fun onOpen() {}
    fun onEvent(event: JsonObject) {}
    fun onClosing(code: Int, reason: String) {}
    fun onFailure(t: Throwable) {}
}

/** OpenBiliClaw API 异常 */
class OpenBiliClawApiException(
    val statusCode: Int,
    val rawBody: String,
) : Exception("[$statusCode] ${extractMessage(rawBody)}") {
    companion object {
        private fun extractMessage(body: String): String {
            return try {
                val json = Json.parseToJsonElement(body)
                if (json is JsonObject) {
                    val detail = json["detail"]
                    if (detail is kotlinx.serialization.json.JsonPrimitive) {
                        detail.content.ifBlank { body }
                    } else {
                        body
                    }
                } else {
                    body
                }
            } catch (_: Exception) {
                body
            }
        }
    }
}
