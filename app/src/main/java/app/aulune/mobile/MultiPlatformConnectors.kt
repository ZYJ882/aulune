package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════
//  共享工具
// ═══════════════════════════════════════════════════════════════

private val defaultClient = OkHttpClient.Builder()
    .callTimeout(15, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private val defaultJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun nowMillis() = System.currentTimeMillis()

private fun makeEntity(
    platform: ContentPlatform,
    remoteId: String,
    title: String,
    summary: String,
    url: String,
    author: String,
    readTime: String,
    theme: String,
    channel: SourceChannel = SourceChannel.Video,
    thumbnailUrl: String = "",
): LocalContentEntity {
    val now = nowMillis()
    return LocalContentEntity(
        contentKey = "${platform.contentKeyPrefix}$remoteId",
        source = "${platform.shortLabel} · $author",
        channel = channel.name,
        title = title,
        readTime = readTime,
        summary = summary.ifBlank { "来自${platform.label}。" },
        theme = theme,
        url = url,
        gradientStart = platformGradientStart(theme, platform),
        gradientEnd = platformGradientEnd(theme, platform),
        thumbnailUrl = ExternalUrlPolicy.normalizedHttpUrlOrEmpty(thumbnailUrl),
        createdAt = now,
        updatedAt = now,
        sourceKey = platform.name.lowercase(),
        authorKey = author,
    )
}

private fun platformGradientStart(theme: String, platform: ContentPlatform): Long = when {
    theme.contains("技术") || theme.contains("AI") -> 0xFF283B77
    theme.contains("商业") -> 0xFF1E5E59
    theme.contains("创作") -> 0xFF7A304E
    theme.contains("生活") -> 0xFF78552A
    else -> platform.accent
}

private fun platformGradientEnd(theme: String, platform: ContentPlatform): Long = when {
    theme.contains("技术") || theme.contains("AI") -> 0xFF5C8FE8
    theme.contains("商业") -> 0xFF55B8A9
    theme.contains("创作") -> 0xFFE07093
    theme.contains("生活") -> 0xFFE3AD5E
    else -> (platform.accent and 0x00FFFFFF) or 0xFFAAAAAA.toLong()
}

private fun classifyTheme(title: String, summary: String, category: String = ""): String {
    val text = "$title $summary $category".lowercase()
    return when {
        listOf("ai", "人工智能", "大模型", "机器学习", "算法", "编程", "代码", "开发", "数码", "科技", "技术").any(text::contains) -> "技术 · AI"
        listOf("创业", "商业", "公司", "产品", "运营", "财经", "投资", "经济", "职场").any(text::contains) -> "商业 · 决策"
        listOf("设计", "创作", "摄影", "剪辑", "音乐", "绘画", "写作", "动画", "艺术").any(text::contains) -> "创作 · 表达"
        listOf("学习", "知识", "历史", "科普", "数学", "科学", "课程", "教程", "读书").any(text::contains) -> "学习 · 探索"
        listOf("生活", "旅行", "美食", "运动", "健康", "日常", "游戏", "时尚", "美妆").any(text::contains) -> "生活 · 兴趣"
        else -> "综合 · 热门"
    }
}

// ═══════════════════════════════════════════════════════════════
//  抖音
// ═══════════════════════════════════════════════════════════════

/**
 * 抖音公开热门内容连接器。
 * 使用抖音 Web 端公开 API（无需登录）。
 */
class DouyinPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.DOUYIN

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            // 抖音 Web 端热门视频 API
            val url = "https://www.douyin.com/aweme/v1/web/hot/search/list/"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Referer", "https://www.douyin.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val list = root["data"]?.jsonObject?.get("word_list")?.jsonArray
                        ?: root["data"]?.jsonArray
                        ?: JsonArray(emptyList())
                    list.mapNotNull { it as? JsonObject }
                        .take(pageSize)
                        .mapNotNull { it.toDouyinContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toDouyinContent(): LocalContentEntity? {
        val word = text("word").ifBlank { text("sentence_id") }
        if (word.isBlank()) return null
        val hotValue = text("hot_value").ifBlank { int("hot_value").toString() }
        val position = int("position")
        return makeEntity(
            platform = ContentPlatform.DOUYIN,
            remoteId = "hot_${word.hashCode()}",
            title = word,
            summary = "抖音热榜 · 热度 $hotValue",
            url = "https://www.douyin.com/search/${java.net.URLEncoder.encode(word, "UTF-8")}",
            author = "抖音热榜",
            readTime = "热榜 #$position",
            theme = classifyTheme(word, ""),
            channel = SourceChannel.Brief,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  小红书
// ═══════════════════════════════════════════════════════════════

/**
 * 小红书公开内容连接器。
 * 使用小红书 Web 端探索页 API。
 */
class XiaohongshuPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.XIAOHONGSHU

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://edith.xiaohongshu.com/api/sns/web/v1/homefeed"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Referer", "https://www.xiaohongshu.com/explore")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val items = root["data"]?.jsonObject?.get("items")?.jsonArray
                        ?: JsonArray(emptyList())
                    items.mapNotNull { it as? JsonObject }
                        .take(pageSize)
                        .mapNotNull { it.toXhsContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toXhsContent(): LocalContentEntity? {
        val noteCard = this["note_card"]?.jsonObject ?: this
        val noteId = noteCard.text("note_id").ifBlank { text("id") }
        if (noteId.isBlank()) return null
        val title = noteCard.text("display_title").ifBlank { noteCard.text("title") }
        if (title.isBlank()) return null
        val user = noteCard["user"]?.jsonObject
        val nickname = user?.text("nickname").orEmpty().ifBlank { "小红书创作者" }
        val type = noteCard.text("type")
        val cover = noteCard["cover"]?.jsonObject?.get("url_default")?.jsonPrimitive?.contentOrNull.orEmpty()
        return makeEntity(
            platform = ContentPlatform.XIAOHONGSHU,
            remoteId = noteId,
            title = title,
            summary = noteCard.text("desc").ifBlank { "来自小红书的${if (type == "video") "视频" else "笔记"}。" },
            url = "https://www.xiaohongshu.com/explore/$noteId",
            author = nickname,
            readTime = if (type == "video") "视频" else "笔记",
            theme = classifyTheme(title, noteCard.text("desc")),
            channel = if (type == "video") SourceChannel.Video else SourceChannel.Notes,
            thumbnailUrl = cover,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  知乎
// ═══════════════════════════════════════════════════════════════

/**
 * 知乎公开内容连接器。
 * 使用知乎热榜 API（无需登录）。
 */
class ZhihuPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.ZHIHU

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=$pageSize"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Referer", "https://www.zhihu.com/hot")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val items = root["data"]?.jsonArray ?: JsonArray(emptyList())
                    items.mapNotNull { it as? JsonObject }
                        .take(pageSize)
                        .mapNotNull { it.toZhihuContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toZhihuContent(): LocalContentEntity? {
        val target = this["target"]?.jsonObject ?: this
        val id = target.text("id").ifBlank { target.text("url").substringAfterLast("/") }
        if (id.isBlank()) return null
        val title = target.text("title").ifBlank { text("title") }
        if (title.isBlank()) return null
        val type = target.text("type")
        val author = target["author"]?.jsonObject?.text("name").orEmpty().ifBlank { "知乎用户" }
        val excerpt = target.text("excerpt")
        val detailText = this["detail_text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return makeEntity(
            platform = ContentPlatform.ZHIHU,
            remoteId = id,
            title = title,
            summary = excerpt.ifBlank { "知乎热榜 · $detailText" },
            url = target.text("url").ifBlank { "https://www.zhihu.com/question/$id" },
            author = author,
            readTime = if (type == "answer") "回答" else "问题",
            theme = classifyTheme(title, excerpt),
            channel = SourceChannel.Insight,
            thumbnailUrl = target.text("thumbnail").ifBlank { target.text("image_url") },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  微博
// ═══════════════════════════════════════════════════════════════

/**
 * 微博公开内容连接器。
 * 使用微博移动端热搜 API（无需登录）。
 */
class WeiboPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.WEIBO

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://weibo.com/ajax/side/hotSearch"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Referer", "https://weibo.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val items = root["data"]?.jsonObject?.get("realtime")?.jsonArray
                        ?: JsonArray(emptyList())
                    items.mapNotNull { it as? JsonObject }
                        .take(pageSize)
                        .mapNotNull { it.toWeiboContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toWeiboContent(): LocalContentEntity? {
        val word = text("word").ifBlank { text("note") }
        if (word.isBlank()) return null
        val rank = int("rankpos").let { if (it > 0) it else int("num") }
        val category = text("category")
        val num = int("num")
        return makeEntity(
            platform = ContentPlatform.WEIBO,
            remoteId = "hot_${word.hashCode()}",
            title = word,
            summary = "微博热搜 · 热度 $num",
            url = "https://s.weibo.com/weibo?q=${java.net.URLEncoder.encode("#$word#", "UTF-8")}",
            author = "微博热搜",
            readTime = "热搜 #$rank",
            theme = classifyTheme(word, "", category),
            channel = SourceChannel.Brief,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  YouTube（v2.0.0 改为 RSS 真实公开 API）
// ═══════════════════════════════════════════════════════════════

/**
 * YouTube 公开内容连接器（v2.0.0 升级）。
 *
 * v1.x：用 12 个硬编码视频 ID + oEmbed（一次只能拿 12 条且更新慢）
 * v2.0.0：使用 YouTube 官方 RSS feed
 *   - https://www.youtube.com/feeds/videos.xml?channel_id=...
 *   - 完全公开无需 API Key
 *   - 默认订阅 8 个高活跃频道覆盖科技/科普/创作/学习/生活
 *
 * 对齐 OpenBiliClaw：不读账号 Cookie、不调需要 API Key 的 YouTube Data API v3
 */
class YoutubePublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.YOUTUBE

    /** 高活跃度公开频道，覆盖主要兴趣维度 */
    private val seedChannels = listOf(
        // 科技
        "UCsXVk37bltHxD1rDPwtNM8Q" to "Kurzgesagt",        // 科普动画
        "UC1_uNU4j7pD8i1yp-Lb65ZQ" to "集合",               // 科技
        // 创作与设计
        "UCYqMRVFeODJQlFnfNDBlJHg" to "The Futur",         // 设计教育
        // 学习与知识
        "UCsooa4yRKr_qVoMj1OyO0oQ" to " Vox",              // 科普
        "UC6nSFpj9HTCZ5t-N3Rm3-HA" to "Vsauce",            // 科普
        // 商业与产品
        "UC3XTzVza9Ed3g7uoVjS7uMw" to "Y Combinator",      // 创业
        // 生活
        "UCddiUEpeqJcYeBxX1IVBKvQ" to "Pick Up Limes",    // 生活方式
        // 娱乐
        "UCBi2mrWuNuyYy4gbM6fU18Q" to "Marques Brownlee",  // 科技数码评测
    )

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            seedChannels.flatMap { (channelId, channelLabel) ->
                try { fetchChannelFeed(channelId, channelLabel) } catch (_: Exception) { emptyList() }
            }.sortedByDescending { it.createdAt }.take(pageSize)
        }

    private fun fetchChannelFeed(channelId: String, channelLabel: String): List<LocalContentEntity> {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val xml = response.body?.string().orEmpty()
            return parseRssFeed(xml, channelLabel)
        }
    }

    /** 轻量级 RSS XML 解析；不引入完整 XML 库以保持 APK 体积 */
    private fun parseRssFeed(xml: String, channelLabel: String): List<LocalContentEntity> {
        val results = mutableListOf<LocalContentEntity>()
        val entryRegex = Regex("<entry>([\\s\\S]*?)</entry>")
        val fieldRegex = Regex("<(yt:videoId|title|published|link[^>]*|media:description|media:thumbnail[^>]*)>([^<]*)")
        entryRegex.findAll(xml).forEach { entryMatch ->
            val entry = entryMatch.groupValues[1]
            val fields = fieldRegex.findAll(entry).associate { it.groupValues[1] to it.groupValues[2] }
            val videoId = fields["yt:videoId"].orEmpty()
            if (videoId.isBlank()) return@forEach
            val title = fields["title"].orEmpty()
            if (title.isBlank()) return@forEach
            val description = fields["media:description"].orEmpty()
            val published = fields["published"].orEmpty()
            val thumbnail = Regex("url=['\"]([^'\"]+)['\"]")
                .find(fields["media:thumbnail"] ?: "")?.groupValues?.getOrNull(1).orEmpty()
            results += makeEntity(
                platform = ContentPlatform.YOUTUBE,
                remoteId = videoId,
                title = title,
                summary = description.take(200).ifBlank { "$channelLabel 的视频" },
                url = "https://www.youtube.com/watch?v=$videoId",
                author = channelLabel,
                readTime = published.take(10).ifBlank { "视频" },
                theme = classifyTheme(title, description),
                channel = SourceChannel.Video,
                thumbnailUrl = thumbnail,
            )
        }
        return results
    }
}

// ═══════════════════════════════════════════════════════════════
//  Reddit
// ═══════════════════════════════════════════════════════════════

/**
 * Reddit 公开内容连接器。
 * 使用 Reddit JSON API（无需登录）。
 */
class RedditPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.REDDIT

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://www.reddit.com/r/popular/hot.json?limit=$pageSize"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val children = root["data"]?.jsonObject?.get("children")?.jsonArray
                        ?: JsonArray(emptyList())
                    children.mapNotNull { it as? JsonObject }
                        .mapNotNull { it["data"] as? JsonObject }
                        .mapNotNull { it.toRedditContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toRedditContent(): LocalContentEntity? {
        val id = text("id")
        if (id.isBlank()) return null
        val title = text("title")
        if (title.isBlank()) return null
        val subreddit = text("subreddit")
        val author = text("author")
        val ups = int("ups")
        val numComments = int("num_comments")
        val permalink = text("permalink")
        val url = text("url_overridden_by_dest").ifBlank { text("url") }
        val selftext = text("selftext").take(200)
        val postHint = text("post_hint")
        return makeEntity(
            platform = ContentPlatform.REDDIT,
            remoteId = id,
            title = title,
            summary = selftext.ifBlank { "r/$subreddit · ↑$ups · 💬$numComments" },
            url = if (url.isNotBlank()) url else "https://www.reddit.com$permalink",
            author = "u/$author · r/$subreddit",
            readTime = "↑$ups",
            theme = classifyTheme(title, selftext, subreddit),
            channel = if (postHint == "image") SourceChannel.Notes else SourceChannel.Brief,
            thumbnailUrl = this["preview"]?.jsonObject
                ?.get("images")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("source")?.jsonObject?.text("url")?.replace("&amp;", "").orEmpty()
                .ifBlank { text("thumbnail") },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  V2EX
// ═══════════════════════════════════════════════════════════════

/**
 * V2EX 公开内容连接器。
 * 使用 V2EX API（无需登录）。
 */
class V2exPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.V2EX

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://www.v2ex.com/api/topics/hot.json"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val array = json.parseToJsonElement(body).jsonArray
                    array.mapNotNull { it as? JsonObject }
                        .take(pageSize)
                        .mapNotNull { it.toV2exContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toV2exContent(): LocalContentEntity? {
        val id = int("id").toString()
        if (id == "0") return null
        val title = text("title")
        if (title.isBlank()) return null
        val member = this["member"]?.jsonObject?.text("username").orEmpty()
        val node = this["node"]?.jsonObject?.text("title").orEmpty()
        val replies = int("replies")
        val content = text("content").take(200)
        return makeEntity(
            platform = ContentPlatform.V2EX,
            remoteId = id,
            title = title,
            summary = content.ifBlank { "节点: $node · 回复: $replies" },
            url = "https://www.v2ex.com/t/$id",
            author = member.ifBlank { "V2EX" },
            readTime = "$replies 回复",
            theme = classifyTheme(title, content, node),
            channel = SourceChannel.Insight,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Bangumi
// ═══════════════════════════════════════════════════════════════

/**
 * Bangumi 公开内容连接器。
 * 使用 Bangumi API（无需登录）。
 */
class BangumiPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.BANGUMI

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            val url = "https://api.bgm.tv/v0/calendar"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LocalContentEntity>()
                    val body = response.body?.string().orEmpty()
                    val array = json.parseToJsonElement(body).jsonArray
                    array.flatMap { day ->
                        (day as? JsonObject)?.get("items")?.jsonArray
                            ?.mapNotNull { it as? JsonObject }
                            ?: emptyList()
                    }.take(pageSize).mapNotNull { it.toBangumiContent() }
                }
            } catch (error: Exception) {
                throw PlatformConnectorException(platform, error)
            }
        }

    private fun JsonObject.toBangumiContent(): LocalContentEntity? {
        val id = int("id").toString()
        if (id == "0") return null
        val name = text("name_cn").ifBlank { text("name") }
        if (name.isBlank()) return null
        val summary = text("summary").take(200)
        val rating = this["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.contentOrNull ?: "0"
        val airDate = text("air_date")
        return makeEntity(
            platform = ContentPlatform.BANGUMI,
            remoteId = id,
            title = name,
            summary = summary.ifBlank { "评分: $rating · 开播: $airDate" },
            url = "https://bgm.tv/subject/$id",
            author = "Bangumi",
            readTime = "评分 $rating",
            theme = classifyTheme(name, summary, "动漫"),
            channel = SourceChannel.Notes,
            thumbnailUrl = this["images"]?.jsonObject?.text("large").orEmpty()
                .ifBlank { this["images"]?.jsonObject?.text("common").orEmpty() },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  连接器工厂
// ═══════════════════════════════════════════════════════════════

object PlatformConnectorFactory {
    private val publicConnectors = mutableMapOf<ContentPlatform, PlatformPublicConnector>()

    fun getPublic(platform: ContentPlatform): PlatformPublicConnector = synchronized(this) {
        publicConnectors.getOrPut(platform) {
            when (platform) {
                ContentPlatform.BILIBILI -> BilibiliPublicConnector()
                ContentPlatform.DOUYIN -> DouyinPublicConnector()
                ContentPlatform.XIAOHONGSHU -> XiaohongshuPublicConnector()
                ContentPlatform.ZHIHU -> ZhihuPublicConnector()
                ContentPlatform.WEIBO -> WeiboPublicConnector()
                ContentPlatform.YOUTUBE -> YoutubePublicConnector()
                ContentPlatform.TWITTER -> TwitterPublicConnector()
                ContentPlatform.REDDIT -> RedditPublicConnector()
                ContentPlatform.V2EX -> V2exPublicConnector()
                ContentPlatform.BANGUMI -> BangumiPublicConnector()
            }
        }
    }

    fun allPublic(): List<PlatformPublicConnector> =
        ContentPlatform.entries.map { getPublic(it) }
}

// ═══════════════════════════════════════════════════════════════
//  Twitter/X（v2.0.0 改为 Nitter 多实例 fallback）
// ═══════════════════════════════════════════════════════════════

/**
 * X (Twitter) 公开内容连接器（v2.0.0 升级）。
 *
 * v1.x：调 `api.twitter.com/1.1/trends/place.json`（需要 Bearer token，会失败）
 * v2.0.0：尝试多个公开 Nitter 实例的 trending 接口，依次回退
 *
 * 对齐 OpenBiliClaw：不依赖 X 官方 API；如全部 Nitter 实例不可用，
 * connector 会返回 emptyList（来源可用性记录为 Unavailable），不影响其他平台。
 */
class TwitterPublicConnector(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) : PlatformPublicConnector {
    override val platform = ContentPlatform.TWITTER

    /** 公共 Nitter 实例列表；按顺序尝试，第一个成功即返回 */
    private val nitterInstances = listOf(
        "https://nitter.privacydev.net",
        "https://nitter.poast.org",
        "https://nitter.cz",
    )

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            nitterInstances.forEach { base ->
                try {
                    val result = fetchFromNitter(base, pageSize)
                    if (result.isNotEmpty()) return@withContext result
                } catch (_: Exception) { /* try next */ }
            }
            // 全部 Nitter 不可用时返回空列表，UI 会显示"未取得公开内容"
            emptyList()
        }

    private fun fetchFromNitter(base: String, pageSize: Int): List<LocalContentEntity> {
        // Nitter 不提供 JSON API，但 RSS 是 XML，每个话题 trending 的 RSS 列表可拿
        val url = "$base/rss/trending"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val xml = response.body?.string().orEmpty()
            return parseNitterTrending(xml, base).take(pageSize)
        }
    }

    private fun parseNitterTrending(xml: String, base: String): List<LocalContentEntity> {
        val results = mutableListOf<LocalContentEntity>()
        val itemRegex = Regex("<item>([\\s\\S]*?)</item>")
        val fieldRegex = Regex("<(title|link|description|pubDate)>([^<]*)")
        itemRegex.findAll(xml).forEach { itemMatch ->
            val item = itemMatch.groupValues[1]
            val fields = fieldRegex.findAll(item).associate { it.groupValues[1] to it.groupValues[2] }
            val title = fields["title"]?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            val link = fields["link"]?.trim().orEmpty()
            // Nitter link 是 nitter.privacydev.net/search?q=...，转换回 x.com
            val xUrl = link.replace(base, "https://x.com")
                .replace("/search?q=", "/search?q=")
            val pubDate = fields["pubDate"]?.trim().orEmpty()
            results += makeEntity(
                platform = ContentPlatform.TWITTER,
                remoteId = "trend_${title.hashCode()}",
                title = title.removePrefix("#"),
                summary = "X 趋势 · 来自 Nitter",
                url = xUrl.ifBlank { "https://x.com/search?q=${java.net.URLEncoder.encode(title, "UTF-8")}" },
                author = "X 趋势",
                readTime = pubDate.take(16).ifBlank { "趋势" },
                theme = classifyTheme(title, ""),
                channel = SourceChannel.Brief,
            )
        }
        return results
    }
}
