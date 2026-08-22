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

/** 账户数据只读结果；原始 Cookie 不会出现在结果对象中。 */
data class BilibiliAccountProfile(
    val mid: Long,
    val name: String,
    val faceUrl: String,
    val level: Int,
    val isVip: Boolean
)

data class BilibiliAccountReadResult(
    val profile: BilibiliAccountProfile,
    val contents: List<LocalContentEntity>
)

/**
 * B 站账户数据适配器。它只在用户明确授权同步后运行，使用当前进程内存中的 Cookie，
 * 只读取数据，不执行点赞、收藏、删除历史或其他写操作。
 */
class BilibiliAccountConnector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun readFirstPage(cookie: String): BilibiliAccountReadResult = withContext(Dispatchers.IO) {
        require(cookie.containsCookie("SESSDATA")) { "请先在官方 B 站页面完成登录并授权同步" }
        val nav = getJson("https://api.bilibili.com/x/web-interface/nav", cookie)
        val navData = requireSuccess(nav, "读取 B 站账号信息")
        val profile = BilibiliAccountProfile(
            mid = navData.long("mid"),
            name = navData.text("uname").ifBlank { "B 站用户" },
            faceUrl = navData.text("face"),
            level = navData["level_info"]?.jsonObject?.int("current_level") ?: 0,
            isVip = navData.int("vipStatus") == 1
        )

        val contents = mutableListOf<LocalContentEntity>()
        val foldersResponse = getJson(
            "https://api.bilibili.com/x/v3/fav/folder/created/list?up_mid=${profile.mid}&pn=1&ps=20",
            cookie
        )
        val foldersData = requireSuccess(foldersResponse, "读取收藏夹列表")
        val folders = foldersData["list"]?.jsonArray ?: JsonArray(emptyList())
        folders.take(10).forEach { folderElement ->
            val folder = folderElement as? JsonObject ?: return@forEach
            val mediaId = folder.long("id")
            if (mediaId <= 0) return@forEach
            val itemsResponse = getJson(
                "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$mediaId&pn=1&ps=20&platform=web",
                cookie
            )
            val itemsData = runCatching { requireSuccess(itemsResponse, "读取收藏夹内容") }.getOrNull() ?: return@forEach
            val medias = itemsData["medias"]?.jsonArray ?: JsonArray(emptyList())
            contents += medias.mapNotNull { it as? JsonObject }
                .mapNotNull { it.toLocalContent("收藏夹 · ${folder.text("title")}") }
        }

        val historyResponse = getJson("https://api.bilibili.com/x/web-interface/history/cursor?ps=30&max=0&view_at=0&business=", cookie)
        val historyData = runCatching { requireSuccess(historyResponse, "读取观看历史") }.getOrNull()
        val historyList = historyData?.get("list")?.jsonArray ?: JsonArray(emptyList())
        contents += historyList.mapNotNull { it as? JsonObject }
            .mapNotNull { it.toHistoryContent() }

        val toViewResponse = getJson("https://api.bilibili.com/x/v2/history/toview?pn=1&ps=20", cookie)
        val toViewData = runCatching { requireSuccess(toViewResponse, "读取稍后再看") }.getOrNull()
        val toViewList = toViewData?.get("list")?.jsonArray ?: JsonArray(emptyList())
        contents += toViewList.mapNotNull { it as? JsonObject }
            .mapNotNull { it.toToViewContent() }

        BilibiliAccountReadResult(profile, contents.distinctBy { it.contentKey })
    }

    private fun getJson(url: String, cookie: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cookie", cookie)
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("B 站账户接口失败（HTTP ${response.code}）")
            val body = response.body?.string().orEmpty()
            return runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IOException("B 站账户接口返回格式无法识别") }
        }
    }

    private fun requireSuccess(root: JsonObject, action: String): JsonObject {
        val code = root.int("code")
        if (code != 0) {
            val message = root.text("message").ifBlank { "返回码 $code" }
            throw IOException("${action}失败：$message")
        }
        return root["data"]?.jsonObject ?: throw IOException("${action}失败：缺少 data")
    }
}

private fun JsonObject.toLocalContent(sourceLabel: String): LocalContentEntity? {
    val bvid = text("bvid").ifBlank { text("bv_id") }
    if (bvid.isBlank()) return null
    val title = text("title").ifBlank { return null }
    val summary = text("intro").replace(Regex("\\s+"), " ").trim()
    val owner = this["upper"]?.jsonObject?.text("name").orEmpty().ifBlank { "B 站创作者" }
    val theme = LocalContentUnderstanding.classify(title, summary)
    val now = System.currentTimeMillis()
    return LocalContentEntity(
        contentKey = "bilibili:$bvid",
        source = "B 站 · $sourceLabel · $owner",
        channel = SourceChannel.Video.name,
        title = title,
        readTime = formatAccountDuration(int("duration")),
        summary = summary.ifBlank { "来自你的 B 站 $sourceLabel。" },
        theme = theme,
        url = "https://www.bilibili.com/video/$bvid",
        gradientStart = 0xFF263B78,
        gradientEnd = 0xFF5C8FE8,
        createdAt = now,
        updatedAt = now,
        saved = sourceLabel.startsWith("收藏夹")
    )
}

private fun JsonObject.toHistoryContent(): LocalContentEntity? {
    val history = this["history"]?.jsonObject ?: return null
    val bvid = history.text("bvid")
    if (bvid.isBlank()) return null
    val title = text("title").ifBlank { return null }
    val summary = text("long_title").ifBlank { "来自你的 B 站观看历史。" }
    val theme = LocalContentUnderstanding.classify(title, summary, text("tag_name"))
    val now = System.currentTimeMillis()
    return LocalContentEntity(
        contentKey = "bilibili:$bvid",
        source = "B 站 · 观看历史 · ${text("author_name").ifBlank { "创作者" }}",
        channel = SourceChannel.Video.name,
        title = title,
        readTime = formatAccountDuration(int("duration")),
        summary = summary,
        theme = theme,
        url = "https://www.bilibili.com/video/$bvid",
        gradientStart = 0xFF5A263D,
        gradientEnd = 0xFFE56884,
        createdAt = now,
        updatedAt = now
    )
}

private fun JsonObject.toToViewContent(): LocalContentEntity? {
    val bvid = text("bvid").ifBlank { this["history"]?.jsonObject?.text("bvid").orEmpty() }
    if (bvid.isBlank()) return null
    val title = text("title").ifBlank { return null }
    val summary = text("intro").ifBlank { "来自你的 B 站稍后再看。" }
    val theme = LocalContentUnderstanding.classify(title, summary)
    val now = System.currentTimeMillis()
    return LocalContentEntity(
        contentKey = "bilibili:$bvid",
        source = "B 站 · 稍后再看",
        channel = SourceChannel.Video.name,
        title = title,
        readTime = formatAccountDuration(int("duration")),
        summary = summary,
        theme = theme,
        url = "https://www.bilibili.com/video/$bvid",
        gradientStart = 0xFF5B1F55,
        gradientEnd = 0xFFA64D96,
        createdAt = now,
        updatedAt = now,
        saved = true
    )
}

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
private fun String.containsCookie(name: String): Boolean = split(';')
    .asSequence()
    .map { it.trim().substringBefore('=') }
    .any { it == name }
private fun formatAccountDuration(seconds: Int): String = if (seconds > 0) "%d:%02d".format(seconds / 60, seconds % 60) else "视频"
