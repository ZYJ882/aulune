package app.aulune.mobile.openbiliclaw

import kotlinx.serialization.json.JsonObject

/**
 * OpenBiliClaw AI 聊天 API。
 *
 * 提供与后端 AI Agent 的对话能力，支持多轮对话、卡片操作、待确认事项等。
 */
class OpenBiliClawChatApi(private val client: OpenBiliClawClient) {

    /**
     * 开始一个对话轮次。
     *
     * @param message 用户消息
     * @param session 会话标识（默认 popup）
     * @param scope 作用域（默认 chat）
     * @param turnId 可选的轮次 ID（用于重试）
     * @param subjectId 主题 ID
     * @param subjectTitle 主题标题
     * @param replyToTurnId 回复的轮次 ID
     */
    suspend fun startTurn(
        message: String,
        session: String = "popup",
        scope: String = "chat",
        turnId: String = "",
        subjectId: String = "",
        subjectTitle: String = "",
        replyToTurnId: String = "",
    ): ChatTurn {
        val json = client.post(
            "/chat/turns",
            mapOf(
                "turn_id" to turnId,
                "session" to session,
                "scope" to scope,
                "subject_id" to subjectId,
                "subject_title" to subjectTitle,
                "reply_to_turn_id" to replyToTurnId,
                "message" to message,
            ),
            timeoutSeconds = 35,
        )
        return ChatTurn.fromJson(json)
    }

    /** 获取指定轮次详情 */
    suspend fun fetchTurn(turnId: String): ChatTurn {
        val json = client.get("/chat/turns/${java.net.URLEncoder.encode(turnId, "UTF-8")}", timeoutSeconds = 10)
        return ChatTurn.fromJson(json)
    }

    /** 获取轮次列表 */
    suspend fun fetchTurns(
        session: String = "popup",
        scope: String = "",
        limit: Int = 100,
    ): List<ChatTurn> {
        val query = buildString {
            append("?session=$session&limit=$limit")
            if (scope.isNotEmpty()) append("&scope=$scope")
        }
        val json = client.get("/chat/turns$query", timeoutSeconds = 12)
        return ChatTurn.listFromJson(json)
    }

    /** 获取待确认事项列表 */
    suspend fun fetchPendingConfirmations(session: String = "popup"): List<PendingConfirmation> {
        val json = client.get("/chat/pending-confirmations?session=$session", timeoutSeconds = 10)
        return PendingConfirmation.listFromJson(json)
    }

    /** 打开待确认事项（触发 AI 处理） */
    suspend fun openPendingConfirmation(ref: String, session: String = "popup"): ChatTurn {
        val json = client.post(
            "/chat/pending-confirmations/${java.net.URLEncoder.encode(ref, "UTF-8")}/open",
            mapOf("session" to session),
            timeoutSeconds = 60,
        )
        return ChatTurn.fromJson(json)
    }

    /** 对卡片执行操作 */
    suspend fun actOnCard(turnId: String, action: String): JsonObject =
        client.post(
            "/chat/cards/${java.net.URLEncoder.encode(turnId, "UTF-8")}/action",
            mapOf("action" to action),
            timeoutSeconds = 65,
        )

    /** 获取对话上下文 */
    suspend fun fetchContext(turnId: String): JsonObject =
        client.get("/chat/contexts/${java.net.URLEncoder.encode(turnId, "UTF-8")}", timeoutSeconds = 10)

    /** 发送消息（便捷方法，等同于 startTurn） */
    suspend fun sendMessage(message: String, session: String = "popup"): ChatTurn =
        startTurn(message = message, session = session)
}
