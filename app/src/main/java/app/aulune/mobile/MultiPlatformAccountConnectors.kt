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
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════
//  共享工具
// ═══════════════════════════════════════════════════════════════

private val accountClient = OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private val accountJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.boolean(key: String): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun nowMillis() = System.currentTimeMillis()

private fun accountEntity(
    platform: ContentPlatform,
    remoteId: String,
    title: String,
    summary: String,
    url: String,
    author: String,
    readTime: String,
    theme: String,
    channel: SourceChannel = SourceChannel.Video,
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
        gradientStart = platform.accent,
        gradientEnd = (platform.accent and 0x00FFFFFF) or 0xFF888888.toLong(),
        createdAt = now,
        updatedAt = now,
        sourceKey = remoteId,
        authorKey = author,
    )
}

private fun classifyTheme(title: String, summary: String = "", category: String = ""): String {
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
//  抖音账号连接器
// ═══════════════════════════════════════════════════════════════

class DouyinAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.DOUYIN

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.douyin.com/aweme/v1/web/user/profile/self/"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://www.douyin.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                val uid = data?.text("uid") ?: data?.text("sec_uid") ?: ""
                val nickname = data?.text("nickname") ?: ""
                val avatar = data?.get("avatar_thumb")?.jsonObject?.get("url_list")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: ""
                val signature = data?.text("signature") ?: ""
                val followerCount = data?.get("follower_count")?.jsonPrimitive?.intOrNull ?: 0
                val followingCount = data?.get("following_count")?.jsonPrimitive?.intOrNull ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = data,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        // 抖音收藏/喜欢列表需要额外 API，这里返回基本信息
        return PlatformAccountReadResult(info = info, content = emptyList())
    }
}

// ═══════════════════════════════════════════════════════════════
//  小红书账号连接器
// ═══════════════════════════════════════════════════════════════

class XiaohongshuAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.XIAOHONGSHU

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://edith.xiaohongshu.com/api/sns/web/v1/user/me"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                val uid = data?.text("user_id") ?: data?.text("red_id") ?: ""
                val nickname = data?.text("nickname") ?: ""
                val avatar = data?.text("imageb") ?: data?.text("avatar") ?: ""
                val signature = data?.text("desc") ?: ""
                val followerCount = data?.int("follows") ?: data?.int("follower_count") ?: 0
                val followingCount = data?.int("fans") ?: data?.int("following_count") ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = data,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://edith.xiaohongshu.com/api/sns/web/v1/user/favourites?cursor=&num=20"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["data"]?.jsonObject?.get("items")?.jsonArray ?: JsonArray(emptyList())
                items.mapNotNull { it as? JsonObject }.mapNotNull { note ->
                    val noteCard = note["note_card"]?.jsonObject ?: note
                    val noteId = noteCard.text("note_id").ifBlank { note.text("id") }
                    val title = noteCard.text("display_title").ifBlank { noteCard.text("title") }
                    if (noteId.isBlank() || title.isBlank()) return@mapNotNull null
                    val user = noteCard["user"]?.jsonObject
                    accountEntity(
                        platform = platform,
                        remoteId = noteId,
                        title = title,
                        summary = noteCard.text("desc"),
                        url = "https://www.xiaohongshu.com/explore/$noteId",
                        author = user?.text("nickname")?.ifBlank { "小红书" } ?: "小红书",
                        readTime = "收藏",
                        theme = classifyTheme(title, noteCard.text("desc")),
                        channel = SourceChannel.Notes,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  知乎账号连接器
// ═══════════════════════════════════════════════════════════════

class ZhihuAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.ZHIHU

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.zhihu.com/api/v4/me"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://www.zhihu.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val uid = root.text("id") ?: ""
                val nickname = root.text("name") ?: ""
                val avatar = root.text("avatar_url") ?: ""
                val signature = root.text("headline") ?: ""
                val followerCount = root.int("follower_count") ?: 0
                val followingCount = root.int("following_count") ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = root,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.zhihu.com/api/v4/me/collections?limit=20&offset=${(page - 1) * 20}"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://www.zhihu.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["data"]?.jsonArray ?: JsonArray(emptyList())
                items.mapNotNull { it as? JsonObject }.mapNotNull { col ->
                    val id = col.text("id")
                    val title = col.text("title")
                    if (id.isBlank() || title.isBlank()) return@mapNotNull null
                    accountEntity(
                        platform = platform,
                        remoteId = "collection_$id",
                        title = title,
                        summary = col.text("description"),
                        url = "https://www.zhihu.com/collection/$id",
                        author = col["creator"]?.jsonObject?.text("name")?.ifBlank { "知乎" } ?: "知乎",
                        readTime = "${col.int("item_count")} 条",
                        theme = classifyTheme(title, col.text("description")),
                        channel = SourceChannel.Insight,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  微博账号连接器
// ═══════════════════════════════════════════════════════════════

class WeiboAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.WEIBO

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://weibo.com/ajax/profile/info"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://weibo.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject?.get("user")?.jsonObject ?: root["data"]?.jsonObject
                val uid = data?.text("idstr") ?: data?.text("id") ?: ""
                val nickname = data?.text("screen_name") ?: data?.text("name") ?: ""
                val avatar = data?.text("avatar_hd") ?: data?.text("avatar_large") ?: ""
                val signature = data?.text("description") ?: ""
                val followerCount = data?.int("followers_count") ?: 0
                val followingCount = data?.int("friends_count") ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = data,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://weibo.com/ajax/favorites/all_fav?page=$page"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", "https://weibo.com/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["data"]?.jsonArray ?: JsonArray(emptyList())
                items.mapNotNull { it as? JsonObject }.mapNotNull { fav ->
                    val id = fav.text("id")
                    val text = fav.text("text_raw").ifBlank { fav.text("text") }
                    if (id.isBlank() || text.isBlank()) return@mapNotNull null
                    val cleanText = text.replace(Regex("<[^>]+>"), "").take(100)
                    accountEntity(
                        platform = platform,
                        remoteId = id,
                        title = cleanText.ifBlank { "微博收藏" },
                        summary = cleanText,
                        url = "https://weibo.com/${fav["user"]?.jsonObject?.text("idstr") ?: ""}/$id",
                        author = fav["user"]?.jsonObject?.text("screen_name")?.ifBlank { "微博" } ?: "微博",
                        readTime = "收藏",
                        theme = classifyTheme(cleanText, ""),
                        channel = SourceChannel.Brief,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Reddit 账号连接器
// ═══════════════════════════════════════════════════════════════

class RedditAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.REDDIT

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://oauth.reddit.com/api/v1/me"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val uid = root.text("id") ?: ""
                val nickname = root.text("name") ?: ""
                val avatar = root.text("icon_img")?.substringBefore("?") ?: ""
                val followerCount = root.int("num_friends") ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = root["subreddit"]?.jsonObject?.text("public_description") ?: "",
                    followerCount = followerCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = root,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.reddit.com/user/me/saved.json?limit=25&after=${if (page > 1) "t3_${(page - 1) * 25}" else ""}"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val children = root["data"]?.jsonObject?.get("children")?.jsonArray ?: JsonArray(emptyList())
                children.mapNotNull { it as? JsonObject }.mapNotNull { child ->
                    val data = child["data"] as? JsonObject ?: return@mapNotNull null
                    val id = data.text("id")
                    val title = data.text("title")
                    if (id.isBlank() || title.isBlank()) return@mapNotNull null
                    accountEntity(
                        platform = platform,
                        remoteId = id,
                        title = title,
                        summary = data.text("selftext").take(200),
                        url = data.text("url_overridden_by_dest").ifBlank { "https://www.reddit.com${data.text("permalink")}" },
                        author = "u/${data.text("author")}",
                        readTime = "↑${data.int("ups")}",
                        theme = classifyTheme(title, data.text("selftext"), data.text("subreddit")),
                        channel = SourceChannel.Brief,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  YouTube 账号连接器
// ═══════════════════════════════════════════════════════════════

class YoutubeAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.YOUTUBE

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            // YouTube 没有简单的用户信息 API，通过访问主页判断登录状态
            val url = "https://www.youtube.com/feed/you"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                // 检查页面中是否包含登录用户标识
                val hasLogin = body.contains("\"loggedIn\":true") || body.contains("ytcfg.set({\"LOGGED_IN\":true")
                val channelNameMatch = Regex("\"channelName\":\"([^\"]+)\"").find(body)
                val channelIdMatch = Regex("\"channelId\":\"([^\"]+)\"").find(body)
                val nickname = channelNameMatch?.groupValues?.get(1) ?: ""
                val uid = channelIdMatch?.groupValues?.get(1) ?: ""
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    isLoggedIn = hasLogin && (nickname.isNotBlank() || uid.isNotBlank()),
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }
}

// ═══════════════════════════════════════════════════════════════
//  X/Twitter 账号连接器
// ═══════════════════════════════════════════════════════════════

class TwitterAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.TWITTER

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.twitter.com/1.1/account/verify_credentials.json"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "TwitterAndroid/9.95.0-release.0")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val uid = root.text("id_str") ?: root.text("id") ?: ""
                val nickname = root.text("screen_name") ?: root.text("name") ?: ""
                val avatar = root.text("profile_image_url_https") ?: ""
                val signature = root.text("description") ?: ""
                val followerCount = root.int("followers_count") ?: 0
                val followingCount = root.int("friends_count") ?: 0
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = root,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.twitter.com/1.1/favorites/list.json?count=20&page=$page"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "TwitterAndroid/9.95.0-release.0")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val array = json.parseToJsonElement(body).jsonArray
                array.mapNotNull { it as? JsonObject }.mapNotNull { tweet ->
                    val id = tweet.text("id_str") ?: tweet.text("id") ?: ""
                    val text = tweet.text("text") ?: tweet.text("full_text") ?: ""
                    if (id.isBlank() || text.isBlank()) return@mapNotNull null
                    val cleanText = text.replace(Regex("https?://\\S+"), "").trim().take(100)
                    accountEntity(
                        platform = platform,
                        remoteId = id,
                        title = cleanText.ifBlank { "X 收藏" },
                        summary = cleanText,
                        url = "https://twitter.com/${tweet["user"]?.jsonObject?.text("screen_name") ?: ""}/status/$id",
                        author = "@${tweet["user"]?.jsonObject?.text("screen_name") ?: "x"}",
                        readTime = "♥${tweet.int("favorite_count")}",
                        theme = classifyTheme(cleanText, ""),
                        channel = SourceChannel.Brief,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  V2EX 账号连接器
// ═══════════════════════════════════════════════════════════════

class V2exAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.V2EX

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.v2ex.com/api/v2/member/self"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val result = root["result"] as? JsonObject
                val uid = result?.text("id") ?: root.text("id") ?: ""
                val nickname = result?.text("username") ?: root.text("username") ?: ""
                val avatar = result?.text("avatar_large") ?: ""
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = if (avatar.startsWith("http")) avatar else "https:$avatar",
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = result,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }
}

// ═══════════════════════════════════════════════════════════════
//  Bangumi 账号连接器
// ═══════════════════════════════════════════════════════════════

class BangumiAccountConnector(
    private val client: OkHttpClient = accountClient,
    private val json: Json = accountJson,
) : PlatformAccountConnector {
    override val platform = ContentPlatform.BANGUMI

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bgm.tv/v0/me"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val uid = root.text("username") ?: root.text("id")?.toString() ?: ""
                val nickname = root.text("nickname") ?: root.text("username") ?: ""
                val avatar = root["avatar"]?.jsonObject?.text("large") ?: ""
                val signature = root.text("sign") ?: ""
                PlatformAccountInfo(
                    platform = platform,
                    uid = uid,
                    nickname = nickname,
                    avatarUrl = avatar,
                    signature = signature,
                    isLoggedIn = uid.isNotBlank() && nickname.isNotBlank(),
                    rawData = root,
                )
            }
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        val info = verifyLogin(cookie)
        if (!info.isLoggedIn) return PlatformAccountReadResult(info = info, error = "未登录")
        return PlatformAccountReadResult(info = info, content = emptyList())
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bgm.tv/v0/users/-/collections?limit=20&offset=${(page - 1) * 20}"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", "Aulune/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val array = json.parseToJsonElement(body).jsonArray
                array.mapNotNull { it as? JsonObject }.mapNotNull { col ->
                    val subject = col["subject"] as? JsonObject ?: return@mapNotNull null
                    val id = subject.text("id") ?: ""
                    val name = subject.text("name_cn").ifBlank { subject.text("name") }
                    if (id.isBlank() || name.isBlank()) return@mapNotNull null
                    accountEntity(
                        platform = platform,
                        remoteId = id,
                        title = name,
                        summary = subject.text("summary").take(200),
                        url = "https://bgm.tv/subject/$id",
                        author = "Bangumi",
                        readTime = "收藏",
                        theme = classifyTheme(name, subject.text("summary"), "动漫"),
                        channel = SourceChannel.Notes,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  账号连接器工厂
// ═══════════════════════════════════════════════════════════════

object PlatformAccountConnectorFactory {
    private val connectors = mutableMapOf<ContentPlatform, PlatformAccountConnector>()

    fun get(platform: ContentPlatform): PlatformAccountConnector = synchronized(this) {
        connectors.getOrPut(platform) {
            when (platform) {
                ContentPlatform.BILIBILI -> BilibiliAccountConnectorAdapter()
                ContentPlatform.DOUYIN -> DouyinAccountConnector()
                ContentPlatform.XIAOHONGSHU -> XiaohongshuAccountConnector()
                ContentPlatform.ZHIHU -> ZhihuAccountConnector()
                ContentPlatform.WEIBO -> WeiboAccountConnector()
                ContentPlatform.YOUTUBE -> YoutubeAccountConnector()
                ContentPlatform.TWITTER -> TwitterAccountConnector()
                ContentPlatform.REDDIT -> RedditAccountConnector()
                ContentPlatform.V2EX -> V2exAccountConnector()
                ContentPlatform.BANGUMI -> BangumiAccountConnector()
            }
        }
    }
}

/**
 * Bilibili 账号连接器适配器。
 * 将已有的 BilibiliAccountConnector 适配到 PlatformAccountConnector 接口。
 */
class BilibiliAccountConnectorAdapter : PlatformAccountConnector {
    override val platform = ContentPlatform.BILIBILI
    private val delegate = BilibiliAccountConnector()

    override suspend fun verifyLogin(cookie: String): PlatformAccountInfo {
        return try {
            val result = delegate.readFirstPage(cookie)
            PlatformAccountInfo(
                platform = platform,
                uid = result.profile.mid.toString(),
                nickname = result.profile.name,
                avatarUrl = result.profile.faceUrl,
                level = result.profile.level.toString(),
                isLoggedIn = result.profile.mid > 0L && result.profile.name.isNotBlank(),
            )
        } catch (_: Exception) {
            PlatformAccountInfo(platform = platform, isLoggedIn = false)
        }
    }

    override suspend fun readAccount(cookie: String): PlatformAccountReadResult {
        return try {
            val result = delegate.readFirstPage(cookie)
            val info = PlatformAccountInfo(
                platform = platform,
                uid = result.profile.mid.toString(),
                nickname = result.profile.name,
                avatarUrl = result.profile.faceUrl,
                level = result.profile.level.toString(),
                isLoggedIn = result.profile.mid > 0L,
            )
            PlatformAccountReadResult(info = info, content = result.contents)
        } catch (e: Exception) {
            PlatformAccountReadResult(
                info = PlatformAccountInfo(platform = platform, isLoggedIn = false),
                error = e.message,
            )
        }
    }

    override suspend fun fetchFavorites(cookie: String, page: Int): List<LocalContentEntity> {
        // Bilibili 收藏通过 BilibiliAccountConnector 的 readFirstPage 获取
        return try {
            delegate.readFirstPage(cookie).contents.filter { it.channel == SourceChannel.Notes.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchHistory(cookie: String, page: Int): List<LocalContentEntity> {
        return try {
            delegate.readFirstPage(cookie).contents.filter { it.source.contains("历史") }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
