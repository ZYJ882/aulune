package app.aulune.mobile.openbiliclaw

import kotlinx.serialization.json.JsonObject

/**
 * OpenBiliClaw 内容拉取 API。
 *
 * 提供推荐内容、收藏/稍后再看、观看历史、活动流、愉悦度探针等内容获取能力。
 * 所有数据来自后端聚合的多平台内容。
 */
class OpenBiliClawContentApi(private val client: OpenBiliClawClient) {

    // ═══════════════════════════════════════════════════════════
    //  推荐内容
    // ═══════════════════════════════════════════════════════════

    /** 获取推荐列表 */
    suspend fun getRecommendations(timeoutSeconds: Int = 12): List<Recommendation> {
        val json = client.get("/recommendations", timeoutSeconds)
        return Recommendation.listFromJson(json)
    }

    /** 刷新推荐（重新生成，可能较慢） */
    suspend fun refreshRecommendations(): JsonObject =
        client.post("/recommendations/refresh", timeoutSeconds = 60)

    /** 重新洗牌推荐（排除指定内容） */
    suspend fun reshuffleRecommendations(excludedBvids: List<String>): List<Recommendation> {
        val json = client.post(
            "/recommendations/reshuffle",
            mapOf("excluded_bvids" to excludedBvids),
            timeoutSeconds = 30,
        )
        return Recommendation.listFromJson(json)
    }

    /** 追加推荐（在现有基础上添加更多） */
    suspend fun appendRecommendations(excludedBvids: List<String>): List<Recommendation> {
        val json = client.post(
            "/recommendations/append",
            mapOf("excluded_bvids" to excludedBvids),
            timeoutSeconds = 30,
        )
        return Recommendation.listFromJson(json)
    }

    /** 上报推荐点击 */
    suspend fun reportClick(payload: Map<String, Any?>): Boolean {
        return try {
            val requestId = "click-${System.currentTimeMillis()}-${(0..Int.MAX_VALUE).random()}"
            client.post("/recommendation-click", payload + ("request_id" to requestId))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 提交推荐反馈 */
    suspend fun submitFeedback(
        recommendationId: Int,
        feedbackType: String,
        note: String = "",
    ): JsonObject {
        val requestId = "fb-${System.currentTimeMillis()}-${(0..Int.MAX_VALUE).random()}"
        return client.post(
            "/feedback",
            mapOf(
                "recommendation_id" to recommendationId,
                "feedback_type" to feedbackType,
                "note" to note,
                "request_id" to requestId,
            ),
            timeoutSeconds = 35,
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  运行时状态 & 活动流
    // ═══════════════════════════════════════════════════════════

    /** 获取运行时状态（推荐池、当前活动等） */
    suspend fun getRuntimeStatus(): RuntimeStatus {
        val json = client.get("/runtime-status", timeoutSeconds = 8)
        return RuntimeStatus.fromJson(json)
    }

    /** 获取活动流（最近的发现/同步活动） */
    suspend fun getActivityFeed(limit: Int = 5, before: String = ""): ActivityFeed {
        val suffix = if (before.isEmpty()) {
            "?limit=$limit"
        } else {
            "?limit=$limit&before=${java.net.URLEncoder.encode(before, "UTF-8")}"
        }
        val json = client.get("/activity-feed$suffix", timeoutSeconds = 8)
        return ActivityFeed.fromJson(json)
    }

    // ═══════════════════════════════════════════════════════════
    //  收藏 / 稍后再看
    // ═══════════════════════════════════════════════════════════

    /** 保存内容到指定列表 */
    suspend fun save(kind: SavedListKind, item: SavedItem): JsonObject =
        client.post("/saved/${kind.apiValue}", item.toSavePayload())

    /** 从指定列表移除内容 */
    suspend fun remove(kind: SavedListKind, itemKey: String): JsonObject =
        client.post("/saved/${kind.apiValue}/remove", mapOf("item_key" to itemKey))

    /** 获取指定列表内容 */
    suspend fun fetchSaved(
        kind: SavedListKind,
        limit: Int = 50,
        offset: Int = 0,
    ): List<SavedItem> {
        val json = client.get("/saved/${kind.apiValue}?limit=$limit&offset=$offset")
        return SavedItem.listFromJson(json)
    }

    /** 获取内容在指定列表中的状态 */
    suspend fun savedStatus(kind: SavedListKind, itemKey: String): JsonObject =
        client.get("/saved/${kind.apiValue}/status?item_key=${java.net.URLEncoder.encode(itemKey, "UTF-8")}")

    /** 同步指定列表到平台 */
    suspend fun syncSaved(kind: SavedListKind, itemKeys: List<String>): JsonObject =
        client.post(
            "/saved/${kind.apiValue}/sync",
            mapOf("item_keys" to itemKeys.distinct()),
            timeoutSeconds = 30,
        )

    /** 轮询同步任务状态 */
    suspend fun pollSyncTask(taskId: String): JsonObject =
        client.get("/saved-sync/tasks/${java.net.URLEncoder.encode(taskId, "UTF-8")}", timeoutSeconds = 15)

    // ═══════════════════════════════════════════════════════════
    //  Bilibili 收藏 / 稍后再看（专用接口）
    // ═══════════════════════════════════════════════════════════

    /** 添加到 Bilibili 收藏 */
    suspend fun addToFavorites(bvid: String): JsonObject =
        client.post("/favorites", mapOf("bvid" to bvid))

    /** 从 Bilibili 收藏移除 */
    suspend fun removeFromFavorites(bvid: String): JsonObject =
        client.delete("/favorites/$bvid")

    /** 获取 Bilibili 收藏状态 */
    suspend fun getFavoriteStatus(bvid: String): JsonObject =
        client.get("/favorites/$bvid")

    /** 获取 Bilibili 收藏列表 */
    suspend fun getFavorites(): List<SavedItem> {
        val json = client.get("/favorites")
        return SavedItem.listFromJson(json)
    }

    /** 添加到 Bilibili 稍后再看 */
    suspend fun addToWatchLater(bvid: String): JsonObject =
        client.post("/watch-later", mapOf("bvid" to bvid))

    /** 从 Bilibili 稍后再看移除 */
    suspend fun removeFromWatchLater(bvid: String): JsonObject =
        client.delete("/watch-later/$bvid")

    /** 获取 Bilibili 稍后再看状态 */
    suspend fun getWatchLaterStatus(bvid: String): JsonObject =
        client.get("/watch-later/$bvid")

    /** 获取 Bilibili 稍后再看列表 */
    suspend fun getWatchLater(): List<SavedItem> {
        val json = client.get("/watch-later")
        return SavedItem.listFromJson(json)
    }

    // ═══════════════════════════════════════════════════════════
    //  观看历史
    // ═══════════════════════════════════════════════════════════

    /** 获取内容历史 */
    suspend fun getContentHistory(
        limit: Int = 50,
        offset: Int = 0,
        sourcePlatform: String? = null,
    ): List<ContentHistoryItem> {
        val params = buildString {
            append("?limit=$limit&offset=$offset")
            if (sourcePlatform != null) append("&source_platform=$sourcePlatform")
        }
        val json = client.get("/content-history$params")
        return ContentHistoryItem.listFromJson(json)
    }

    // ═══════════════════════════════════════════════════════════
    //  愉悦度探针
    // ═══════════════════════════════════════════════════════════

    /** 获取待处理的愉悦度探针 */
    suspend fun getPendingDelights(limit: Int? = null): List<Delight> {
        val qs = if (limit != null) "?limit=$limit" else ""
        val json = client.get("/delight/pending-batch$qs")
        return Delight.listFromJson(json)
    }

    /** 响应愉悦度探针 */
    suspend fun respondToDelight(
        bvid: String,
        response: String,
        title: String = "",
        message: String = "",
    ): JsonObject {
        val requestId = "delight-${System.currentTimeMillis()}-${(0..Int.MAX_VALUE).random()}"
        return client.post(
            "/delight/respond",
            mapOf(
                "bvid" to bvid,
                "response" to response,
                "title" to title,
                "message" to message,
                "request_id" to requestId,
            ),
            timeoutSeconds = 35,
        )
    }

    /** 标记愉悦度探针已发送 */
    suspend fun markDelightSent(bvid: String): JsonObject =
        client.post("/delight/sent", mapOf("bvid" to bvid))

    /** 触发愉悦度探针 */
    suspend fun triggerDelight(): JsonObject =
        client.post("/delight/trigger")

    // ═══════════════════════════════════════════════════════════
    //  兴趣/回避探针
    // ═══════════════════════════════════════════════════════════

    /** 获取待处理的兴趣探针 */
    suspend fun getPendingInterestProbes(): JsonObject =
        client.get("/interest-probes/pending")

    /** 响应兴趣探针 */
    suspend fun respondToInterestProbe(response: Map<String, Any?>): JsonObject =
        client.post("/interest-probes/respond", response)

    /** 触发兴趣探针 */
    suspend fun triggerInterestProbe(): JsonObject =
        client.post("/interest-probes/trigger")

    /** 获取待处理的回避探针 */
    suspend fun getPendingAvoidanceProbes(): JsonObject =
        client.get("/avoidance-probes/pending")

    /** 响应回避探针 */
    suspend fun respondToAvoidanceProbe(response: Map<String, Any?>): JsonObject =
        client.post("/avoidance-probes/respond", response)

    /** 触发回避探针 */
    suspend fun triggerAvoidanceProbe(): JsonObject =
        client.post("/avoidance-probes/trigger")

    // ═══════════════════════════════════════════════════════════
    //  通知
    // ═══════════════════════════════════════════════════════════

    /** 获取待处理通知 */
    suspend fun getPendingNotifications(): JsonObject =
        client.get("/notifications/pending")

    /** 标记通知已发送 */
    suspend fun markNotificationSent(notificationId: String): JsonObject =
        client.post("/notifications/sent", mapOf("notification_id" to notificationId))
}
