package app.aulune.mobile

import kotlinx.serialization.json.JsonObject

// ═══════════════════════════════════════════════════════════════
//  平台枚举
// ═══════════════════════════════════════════════════════════════

/**
 * 支持的内容平台。
 * 对齐 OpenBiliClaw 的 11 个数据源。
 */
enum class ContentPlatform(
    val id: String,
    val label: String,
    val shortLabel: String,
    val accent: Long,
    val loginUrl: String,
    val homeUrl: String,
    val contentKeyPrefix: String,
) {
    BILIBILI(
        id = "bilibili",
        label = "哔哩哔哩",
        shortLabel = "B站",
        accent = 0xFFFB7299,
        loginUrl = "https://passport.bilibili.com/login",
        homeUrl = "https://www.bilibili.com/",
        contentKeyPrefix = "bilibili:",
    ),
    DOUYIN(
        id = "douyin",
        label = "抖音",
        shortLabel = "抖音",
        accent = 0xFF000000,
        loginUrl = "https://www.douyin.com/login",
        homeUrl = "https://www.douyin.com/",
        contentKeyPrefix = "douyin:",
    ),
    XIAOHONGSHU(
        id = "xiaohongshu",
        label = "小红书",
        shortLabel = "小红书",
        accent = 0xFFFF2442,
        loginUrl = "https://www.xiaohongshu.com/login",
        homeUrl = "https://www.xiaohongshu.com/",
        contentKeyPrefix = "xhs:",
    ),
    ZHIHU(
        id = "zhihu",
        label = "知乎",
        shortLabel = "知乎",
        accent = 0xFF0066FF,
        loginUrl = "https://www.zhihu.com/signin",
        homeUrl = "https://www.zhihu.com/",
        contentKeyPrefix = "zhihu:",
    ),
    WEIBO(
        id = "weibo",
        label = "微博",
        shortLabel = "微博",
        accent = 0xFFFF8200,
        loginUrl = "https://passport.weibo.cn/signin/login",
        homeUrl = "https://m.weibo.cn/",
        contentKeyPrefix = "weibo:",
    ),
    YOUTUBE(
        id = "youtube",
        label = "YouTube",
        shortLabel = "YT",
        accent = 0xFFFF0000,
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube",
        homeUrl = "https://www.youtube.com/",
        contentKeyPrefix = "youtube:",
    ),
    TWITTER(
        id = "twitter",
        label = "X (Twitter)",
        shortLabel = "X",
        accent = 0xFF1DA1F2,
        loginUrl = "https://twitter.com/i/flow/login",
        homeUrl = "https://twitter.com/home",
        contentKeyPrefix = "twitter:",
    ),
    REDDIT(
        id = "reddit",
        label = "Reddit",
        shortLabel = "Reddit",
        accent = 0xFFFF4500,
        loginUrl = "https://www.reddit.com/login",
        homeUrl = "https://www.reddit.com/",
        contentKeyPrefix = "reddit:",
    ),
    V2EX(
        id = "v2ex",
        label = "V2EX",
        shortLabel = "V2EX",
        accent = 0xFF333333,
        loginUrl = "https://www.v2ex.com/signin",
        homeUrl = "https://www.v2ex.com/",
        contentKeyPrefix = "v2ex:",
    ),
    BANGUMI(
        id = "bangumi",
        label = "Bangumi",
        shortLabel = "BGM",
        accent = 0xFFF09199,
        loginUrl = "https://bgm.tv/login",
        homeUrl = "https://bgm.tv/",
        contentKeyPrefix = "bangumi:",
    );

    companion object {
        fun fromId(id: String): ContentPlatform? = entries.firstOrNull { it.id == id }
        fun fromContentKey(contentKey: String): ContentPlatform? {
            return entries.firstOrNull { contentKey.startsWith(it.contentKeyPrefix) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  账号信息模型
// ═══════════════════════════════════════════════════════════════

/** 平台账号信息（登录后爬取） */
data class PlatformAccountInfo(
    val platform: ContentPlatform,
    val uid: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val signature: String = "",
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val level: String = "",
    val isLoggedIn: Boolean = false,
    val rawData: JsonObject? = null,
)

/** 平台账号读取结果 */
data class PlatformAccountReadResult(
    val info: PlatformAccountInfo,
    val content: List<LocalContentEntity> = emptyList(),
    val error: String? = null,
)

// ═══════════════════════════════════════════════════════════════
//  连接器接口
// ═══════════════════════════════════════════════════════════════

/** 公开内容连接器（无需登录） */
interface PlatformPublicConnector {
    val platform: ContentPlatform
    suspend fun fetchPublic(page: Int = 1, pageSize: Int = 20): List<LocalContentEntity>
}

/** 账号数据连接器（需要登录 Cookie） */
interface PlatformAccountConnector {
    val platform: ContentPlatform

    /** 读取账号首页数据（用户信息 + 内容列表） */
    suspend fun readAccount(cookie: String): PlatformAccountReadResult

    /** 仅验证登录状态并获取用户信息 */
    suspend fun verifyLogin(cookie: String): PlatformAccountInfo

    /** 读取收藏夹 */
    suspend fun fetchFavorites(cookie: String, page: Int = 1): List<LocalContentEntity> = emptyList()

    /** 读取观看历史 */
    suspend fun fetchHistory(cookie: String, page: Int = 1): List<LocalContentEntity> = emptyList()
}

// ═══════════════════════════════════════════════════════════════
//  平台 Cookie 管理
// ═══════════════════════════════════════════════════════════════

/** 多平台 Cookie 管理器（EncryptedSharedPreferences） */
object PlatformCookieManager {
    private const val PREFS_NAME = "platform_cookies"
    private const val KEY_SUFFIX = "_cookie"

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    fun getCookie(context: android.content.Context, platform: ContentPlatform): String =
        prefs(context).getString("${platform.id}$KEY_SUFFIX", "") ?: ""

    fun setCookie(context: android.content.Context, platform: ContentPlatform, cookie: String) {
        prefs(context).edit().putString("${platform.id}$KEY_SUFFIX", cookie).apply()
    }

    fun clearCookie(context: android.content.Context, platform: ContentPlatform) {
        prefs(context).edit().remove("${platform.id}$KEY_SUFFIX").apply()
    }

    fun isLoggedIn(context: android.content.Context, platform: ContentPlatform): Boolean =
        getCookie(context, platform).isNotBlank()

    fun loggedInPlatforms(context: android.content.Context): List<ContentPlatform> =
        ContentPlatform.entries.filter { isLoggedIn(context, it) }
}
