package app.aulune.mobile.openbiliclaw

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// ═══════════════════════════════════════════════════════════════
//  平台枚举与工具
// ═══════════════════════════════════════════════════════════════

enum class SourcePlatform(val apiValue: String, val label: String) {
    BILIBILI("bilibili", "B站"),
    XIAOHONGSHU("xiaohongshu", "小红书"),
    DOUYIN("douyin", "抖音"),
    WEIBO("weibo", "微博"),
    YOUTUBE("youtube", "YouTube"),
    TWITTER("twitter", "X"),
    ZHIHU("zhihu", "知乎"),
    REDDIT("reddit", "Reddit"),
    BANGUMI("bangumi", "Bangumi"),
    LINUXDO("linuxdo", "Linux.do"),
    V2EX("v2ex", "V2EX"),
    WEB("web", "网页"),
    UNKNOWN("unknown", "未知");

    companion object {
        private val ALIASES = mapOf(
            "bili" to BILIBILI, "bilibili" to BILIBILI,
            "xhs" to XIAOHONGSHU, "xiaohongshu" to XIAOHONGSHU, "rednote" to XIAOHONGSHU,
            "dy" to DOUYIN, "douyin" to DOUYIN, "tiktok" to DOUYIN,
            "wb" to WEIBO, "weibo" to WEIBO,
            "yt" to YOUTUBE, "youtube" to YOUTUBE,
            "x" to TWITTER, "twitter" to TWITTER,
            "zh" to ZHIHU, "zhihu" to ZHIHU,
            "rd" to REDDIT, "reddit" to REDDIT,
            "bgm" to BANGUMI, "bangumi" to BANGUMI,
            "linuxdo" to LINUXDO, "linux.do" to LINUXDO,
            "v2" to V2EX, "v2ex" to V2EX,
            "web" to WEB,
        )

        fun normalize(value: String, contentUrl: String = "", bvid: String = ""): SourcePlatform {
            val source = value.trim().lowercase()
            ALIASES[source]?.let { return it }
            if (source.isEmpty() && bvid.contains(":")) {
                val namespace = bvid.split(":").first().trim().lowercase()
                ALIASES[namespace]?.let { return it }
            }
            val host = contentUrl.trim().let { url ->
                if (url.contains("://")) url.substringAfter("://").substringBefore("/")
                else url.substringBefore("/")
            }.lowercase()
            return when {
                host.contains("bilibili.com") || host.contains("b23.tv") -> BILIBILI
                host.contains("xiaohongshu.com") || host.contains("xhslink.com") -> XIAOHONGSHU
                host.contains("douyin.com") -> DOUYIN
                host.contains("weibo.com") || host.contains("weibo.cn") -> WEIBO
                host.contains("youtube.com") || host.contains("youtu.be") -> YOUTUBE
                host.contains("x.com") || host.contains("twitter.com") -> TWITTER
                host.contains("zhihu.com") -> ZHIHU
                host.contains("reddit.com") || host.contains("redd.it") -> REDDIT
                host.contains("bgm.tv") || host.contains("bangumi.tv") -> BANGUMI
                host.contains("linux.do") -> LINUXDO
                host.contains("v2ex.com") -> V2EX
                bvid.isNotEmpty() && !bvid.contains(":") -> BILIBILI
                else -> if (source.isEmpty()) WEB else UNKNOWN
            }
        }
    }
}

enum class SavedListKind(val apiValue: String, val label: String) {
    WATCH_LATER("watch_later", "稍后再看"),
    FAVORITE("favorite", "收藏"),
}

// ═══════════════════════════════════════════════════════════════
//  推荐内容
// ═══════════════════════════════════════════════════════════════

data class Recommendation(
    val id: Int = 0,
    val bvid: String = "",
    val itemKey: String = "",
    val contentId: String = "",
    val title: String = "",
    val upName: String = "",
    val coverUrl: String = "",
    val expression: String = "",
    val topicLabel: String = "",
    val contentUrl: String = "",
    val sourcePlatform: SourcePlatform = SourcePlatform.BILIBILI,
    val contentType: String = "video",
    val bodyText: String = "",
    val publishedAt: String = "",
    val publishedLabel: String = "",
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val favoriteCount: Int = 0,
    val danmakuCount: Int = 0,
    val ratingScore: Double = 0.0,
    val ratingCount: Int = 0,
    val sourceRank: Int = 0,
    val feedbackType: String = "",
) {
    val displayTitle: String get() = title.ifEmpty { "这条标题还没对上号" }
    val displayUpName: String get() = upName.ifEmpty { "这位 UP 还没认出来" }
    val isTextCard: Boolean get() = coverUrl.isEmpty() || contentType.lowercase() in setOf(
        "tweet", "thread", "answer", "article", "question", "post", "comment",
    )
    val savedIdentity: String
        get() = when {
            itemKey.isNotEmpty() -> itemKey
            contentId.isNotEmpty() -> "${sourcePlatform.apiValue}:$contentId"
            bvid.isNotEmpty() -> "${sourcePlatform.apiValue}:$bvid"
            else -> contentUrl
        }
    val statsLabel: String
        get() = buildList {
            if (viewCount > 0) add("▶ ${formatCount(viewCount)}")
            if (likeCount > 0) add("👍 ${formatCount(likeCount)}")
            if (commentCount > 0) add("💬 ${formatCount(commentCount)}")
            if (favoriteCount > 0) add("⭐ ${formatCount(favoriteCount)}")
            if (danmakuCount > 0) add("弹幕 ${formatCount(danmakuCount)}")
            if (ratingScore > 0) add("评分 ${"%.1f".format(ratingScore)}")
            if (ratingCount > 0) add("${formatCount(ratingCount)} 人评分")
            if (sourceRank > 0) add("排名 #$sourceRank")
        }.joinToString(" · ")

    fun toSavedPayload(): Map<String, Any?> = mapOf(
        "source_platform" to sourcePlatform.apiValue,
        "content_id" to contentId,
        "content_url" to contentUrl,
        "content_type" to contentType,
        "title" to title,
        "author_name" to upName,
        "cover_url" to coverUrl,
        "note" to "",
    )

    companion object {
        fun fromJson(json: JsonObject): Recommendation {
            val bvid = json.str("bvid")
            val source = SourcePlatform.normalize(
                json.str("source_platform"),
                contentUrl = json.str("content_url"),
                bvid = bvid,
            )
            val explicitContentId = json.str("content_id")
            val contentId = explicitContentId.ifEmpty {
                if (bvid.contains(":")) bvid.substringAfter(":") else bvid
            }
            return Recommendation(
                id = json.int("id"),
                bvid = bvid,
                itemKey = json.str("item_key").ifEmpty {
                    if (contentId.isEmpty()) "" else "${source.apiValue}:$contentId"
                },
                contentId = contentId,
                title = decodeHtml(json.str("title")),
                upName = decodeHtml(json.str("up_name")),
                coverUrl = json.str("cover_url"),
                expression = decodeHtml(json.str("expression")),
                topicLabel = decodeHtml(json.str("topic_label")),
                contentUrl = json.str("content_url"),
                sourcePlatform = source,
                contentType = json.str("content_type").ifEmpty { "video" },
                bodyText = decodeHtml(json.str("body_text")),
                publishedAt = json.str("published_at"),
                publishedLabel = json.str("published_label"),
                viewCount = json.int("view_count"),
                likeCount = json.int("like_count"),
                commentCount = json.int("comment_count"),
                favoriteCount = json.int("favorite_count"),
                danmakuCount = json.int("danmaku_count"),
                ratingScore = json.double("rating_score"),
                ratingCount = json.int("rating_count"),
                sourceRank = json.int("source_rank"),
                feedbackType = json.str("feedback_type"),
            )
        }

        fun listFromJson(json: JsonObject, key: String = "items"): List<Recommendation> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  收藏/稍后再看
// ═══════════════════════════════════════════════════════════════

data class SavedItem(
    val itemKey: String,
    val sourcePlatform: SourcePlatform,
    val contentId: String,
    val contentUrl: String = "",
    val contentType: String = "video",
    val title: String = "",
    val coverUrl: String = "",
    val authorName: String = "",
    val note: String = "",
    val addedAt: String = "",
    val syncStatus: String = "pending",
    val syncTaskId: String = "",
    val resolvedTarget: String = "",
    val errorCode: String = "",
    val errorMessage: String = "",
) {
    val bvid: String get() = if (sourcePlatform == SourcePlatform.BILIBILI) contentId else "${sourcePlatform.apiValue}:$contentId"
    val upName: String get() = authorName
    val synced: Boolean get() = syncStatus == "synced" || syncStatus == "already_synced"
    val syncing: Boolean get() = syncStatus == "syncing" || (syncStatus == "pending" && syncTaskId.isNotEmpty())
    val localOnly: Boolean get() = syncStatus == "unsupported" && errorCode == "unsupported_content_type"
    val canSync: Boolean get() = !synced && !syncing && !localOnly

    val syncLabel: String
        get() = when (syncStatus) {
            "syncing" -> "同步中"
            "synced", "already_synced" -> "已同步"
            "login_required" -> "需要登录"
            "extension_required" -> "需要插件"
            "rate_limited", "failed" -> "同步失败"
            "unsupported" -> if (localOnly) "仅本地保存" else "同步暂不可用"
            else -> "待同步"
        }

    fun toSavePayload(): Map<String, Any?> = mapOf(
        "source_platform" to sourcePlatform.apiValue,
        "content_id" to contentId,
        "content_url" to contentUrl,
        "content_type" to contentType,
        "title" to title,
        "author_name" to authorName,
        "cover_url" to coverUrl,
        "note" to note,
    )

    companion object {
        fun fromJson(json: JsonObject): SavedItem {
            val legacyBvid = json.str("bvid")
            val source = SourcePlatform.normalize(
                json.str("source_platform"),
                contentUrl = json.str("content_url"),
                bvid = legacyBvid,
            )
            val contentId = json.str("content_id").ifEmpty {
                if (legacyBvid.contains(":")) legacyBvid.substringAfter(":") else legacyBvid
            }
            return SavedItem(
                itemKey = json.str("item_key").ifEmpty { "${source.apiValue}:$contentId" },
                sourcePlatform = source,
                contentId = contentId,
                contentUrl = json.str("content_url"),
                contentType = json.str("content_type").ifEmpty { "video" },
                title = decodeHtml(json.str("title")),
                coverUrl = json.str("cover_url"),
                authorName = decodeHtml(
                    json.str("author_name").ifEmpty { json.str("up_name") },
                ),
                note = decodeHtml(json.str("note")),
                addedAt = json.str("added_at"),
                syncStatus = json.str("sync_status").ifEmpty { "pending" },
                syncTaskId = json.str("sync_task_id"),
                resolvedTarget = decodeHtml(json.str("resolved_target")),
                errorCode = json.str("error_code"),
                errorMessage = decodeHtml(json.str("error_message")),
            )
        }

        fun listFromJson(json: JsonObject, key: String = "items"): List<SavedItem> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  运行时状态 & 活动流
// ═══════════════════════════════════════════════════════════════

data class RuntimeStatus(
    val poolAvailableCount: Int = 0,
    val lastReplenishedCount: Int = 0,
    val poolPendingCount: Int = 0,
    val recentPoolTopics: List<String> = emptyList(),
    val activity: String = "",
    val busy: Boolean = false,
) {
    val topicSummary: String get() = recentPoolTopics.joinToString(" / ")

    companion object {
        fun fromJson(json: JsonObject): RuntimeStatus {
            val topics = (json["recent_pool_topics"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.map { topicLabel(it) }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.take(3)
                ?: emptyList()
            return RuntimeStatus(
                poolAvailableCount = json.int("pool_available_count"),
                lastReplenishedCount = json.int("last_replenished_count"),
                poolPendingCount = json.int("pool_pending_count"),
                recentPoolTopics = topics,
                activity = decodeHtml(json.str("activity").ifEmpty { json.str("current_activity") }),
                busy = json.boolean("busy") || json.boolean("refreshing") || json.boolean("discovery_running"),
            )
        }
    }
}

data class ActivityFeed(
    val headline: String = "",
    val liveSummary: String = "",
    val items: List<ActivityItem> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): ActivityFeed {
            val items = (json["items"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.map { ActivityItem.fromJson(it) }
                ?: emptyList()
            return ActivityFeed(
                headline = decodeHtml(json.str("headline")),
                liveSummary = decodeHtml(json.str("live_summary")),
                items = items,
                hasMore = json.boolean("has_more"),
                nextCursor = json.str("next_cursor"),
            )
        }
    }
}

data class ActivityItem(
    val title: String = "",
    val summary: String = "",
    val createdAt: String = "",
    val type: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): ActivityItem = ActivityItem(
            title = decodeHtml(json.str("title").ifEmpty { json.str("headline") }.ifEmpty { json.str("type") }),
            summary = decodeHtml(json.str("summary").ifEmpty { json.str("message") }.ifEmpty { json.str("detail") }),
            createdAt = json.str("created_at").ifEmpty { json.str("timestamp") },
            type = json.str("type"),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  AI 聊天
// ═══════════════════════════════════════════════════════════════

data class ChatTurn(
    val turnId: String = "",
    val session: String = "",
    val scope: String = "",
    val subjectId: String = "",
    val subjectTitle: String = "",
    val replyToTurnId: String = "",
    val message: String = "",
    val response: String = "",
    val status: String = "",
    val createdAt: String = "",
    val cards: List<ChatCard> = emptyList(),
) {
    val isPending: Boolean get() = status == "pending" || status == "processing"
    val isComplete: Boolean get() = status == "complete" || status == "completed"

    companion object {
        fun fromJson(json: JsonObject): ChatTurn {
            val cards = (json["cards"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.map { ChatCard.fromJson(it) }
                ?: emptyList()
            return ChatTurn(
                turnId = json.str("turn_id"),
                session = json.str("session"),
                scope = json.str("scope"),
                subjectId = json.str("subject_id"),
                subjectTitle = json.str("subject_title"),
                replyToTurnId = json.str("reply_to_turn_id"),
                message = json.str("message"),
                response = json.str("response").ifEmpty { json.str("reply") },
                status = json.str("status"),
                createdAt = json.str("created_at"),
                cards = cards,
            )
        }

        fun listFromJson(json: JsonObject, key: String = "items"): List<ChatTurn> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

data class ChatCard(
    val cardId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val actions: List<ChatCardAction> = emptyList(),
) {
    companion object {
        fun fromJson(json: JsonObject): ChatCard {
            val actions = (json["actions"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.map { ChatCardAction.fromJson(it) }
                ?: emptyList()
            return ChatCard(
                cardId = json.str("card_id").ifEmpty { json.str("id") },
                type = json.str("type"),
                title = json.str("title"),
                body = json.str("body"),
                actions = actions,
            )
        }
    }
}

data class ChatCardAction(
    val action: String = "",
    val label: String = "",
    val style: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): ChatCardAction = ChatCardAction(
            action = json.str("action"),
            label = json.str("label"),
            style = json.str("style"),
        )
    }
}

data class PendingConfirmation(
    val ref: String = "",
    val title: String = "",
    val message: String = "",
    val turnId: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): PendingConfirmation = PendingConfirmation(
            ref = json.str("ref"),
            title = json.str("title"),
            message = json.str("message"),
            turnId = json.str("turn_id"),
        )

        fun listFromJson(json: JsonObject, key: String = "items"): List<PendingConfirmation> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  个人资料
// ═══════════════════════════════════════════════════════════════

data class ProfileSummary(
    val interests: List<String> = emptyList(),
    val avoidedTopics: List<String> = emptyList(),
    val preferredPlatforms: List<String> = emptyList(),
    val contentTypes: List<String> = emptyList(),
    val summary: String = "",
    val lastUpdated: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): ProfileSummary {
            val data = json["data"] as? JsonObject ?: json
            return ProfileSummary(
                interests = data.stringList("interests"),
                avoidedTopics = data.stringList("avoided_topics"),
                preferredPlatforms = data.stringList("preferred_platforms"),
                contentTypes = data.stringList("content_types"),
                summary = data.str("summary"),
                lastUpdated = data.str("last_updated"),
            )
        }
    }
}

data class ProfileEditState(
    val interests: String = "",
    val avoidedTopics: String = "",
    val preferredPlatforms: String = "",
    val contentTypes: String = "",
    val notes: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): ProfileEditState {
            val data = json["data"] as? JsonObject ?: json
            return ProfileEditState(
                interests = data.str("interests"),
                avoidedTopics = data.str("avoided_topics"),
                preferredPlatforms = data.str("preferred_platforms"),
                contentTypes = data.str("content_types"),
                notes = data.str("notes"),
            )
        }
    }

    fun toPayload(): Map<String, Any?> = mapOf(
        "interests" to interests,
        "avoided_topics" to avoidedTopics,
        "preferred_platforms" to preferredPlatforms,
        "content_types" to contentTypes,
        "notes" to notes,
    )
}

// ═══════════════════════════════════════════════════════════════
//  内容历史
// ═══════════════════════════════════════════════════════════════

data class ContentHistoryItem(
    val itemKey: String = "",
    val sourcePlatform: SourcePlatform = SourcePlatform.BILIBILI,
    val contentId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val authorName: String = "",
    val viewedAt: String = "",
    val progress: Int = 0,
    val duration: Int = 0,
) {
    companion object {
        fun fromJson(json: JsonObject): ContentHistoryItem {
            val source = SourcePlatform.normalize(json.str("source_platform"))
            return ContentHistoryItem(
                itemKey = json.str("item_key"),
                sourcePlatform = source,
                contentId = json.str("content_id"),
                title = decodeHtml(json.str("title")),
                coverUrl = json.str("cover_url"),
                authorName = decodeHtml(json.str("author_name").ifEmpty { json.str("up_name") }),
                viewedAt = json.str("viewed_at").ifEmpty { json.str("created_at") },
                progress = json.int("progress"),
                duration = json.int("duration"),
            )
        }

        fun listFromJson(json: JsonObject, key: String = "items"): List<ContentHistoryItem> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  来源/平台状态
// ═══════════════════════════════════════════════════════════════

data class SourceStatus(
    val slug: String = "",
    val name: String = "",
    val platform: SourcePlatform = SourcePlatform.UNKNOWN,
    val enabled: Boolean = false,
    val authenticated: Boolean = false,
    val loginState: String = "",
    val lastSync: String = "",
    val error: String = "",
    val credentialType: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): SourceStatus {
            val slug = json.str("slug")
            return SourceStatus(
                slug = slug,
                name = json.str("name").ifEmpty { slug },
                platform = SourcePlatform.normalize(slug),
                enabled = json.boolean("enabled"),
                authenticated = json.boolean("authenticated"),
                loginState = json.str("login_state"),
                lastSync = json.str("last_sync"),
                error = json.str("error"),
                credentialType = json.str("credential_type"),
            )
        }

        fun listFromJson(json: JsonObject, key: String = "sources"): List<SourceStatus> {
            val array = json[key] as? JsonArray ?: (json["items"] as? JsonArray) ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  愉悦度探针
// ═══════════════════════════════════════════════════════════════

data class Delight(
    val bvid: String = "",
    val title: String = "",
    val message: String = "",
    val itemKey: String = "",
) {
    companion object {
        fun fromJson(json: JsonObject): Delight = Delight(
            bvid = json.str("bvid"),
            title = json.str("title"),
            message = json.str("message"),
            itemKey = json.str("item_key"),
        )

        fun listFromJson(json: JsonObject, key: String = "items"): List<Delight> {
            val array = json[key] as? JsonArray ?: return emptyList()
            return array.mapNotNull { it as? JsonObject }.map { fromJson(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  工具函数
// ═══════════════════════════════════════════════════════════════

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

private fun JsonObject.double(key: String): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0

private fun JsonObject.boolean(key: String): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

private fun topicLabel(value: String): String {
    val lower = value.trim().lowercase()
    return when {
        lower.startsWith("xhs-extension-") -> "小红书"
        lower.startsWith("dy-plugin-") || lower.startsWith("douyin-") -> "抖音"
        lower.startsWith("yt-") || lower.startsWith("youtube-") -> "YouTube"
        lower.startsWith("reddit-") -> "Reddit"
        lower.startsWith("bangumi-") -> "Bangumi"
        else -> value.trim()
    }
}

fun formatCount(value: Int): String = when {
    value >= 100000000 -> compactNumber(value / 100000000.0, "亿")
    value >= 10000 -> compactNumber(value / 10000.0, "万")
    else -> value.toString()
}

private fun compactNumber(value: Double, suffix: String): String {
    val fixed = "%.1f".format(value)
    return (if (fixed.endsWith(".0")) fixed.substring(0, fixed.length - 2) else fixed) + suffix
}

fun decodeHtml(text: String): String {
    if (text.isEmpty()) return text
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&#x27;", "'")
        .replace("&#x2F;", "/")
}
