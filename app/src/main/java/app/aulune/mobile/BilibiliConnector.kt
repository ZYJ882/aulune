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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 仅导入不需要账号凭据的 B 站公开热门内容。
 *
 * 登录 Cookie 继续只由官方 WebView 管理；此连接器不读取、导出、复制或发送 Cookie。
 * 后续若平台提供正式移动端授权 API，可另行实现显式授权的数据源。
 */
class BilibiliPublicConnector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PlatformPublicConnector {
    override val platform = ContentPlatform.BILIBILI

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> = fetchPopular(page, pageSize)

    suspend fun fetchPopular(page: Int = 1, pageSize: Int = 12): List<LocalContentEntity> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/web-interface/popular?pn=${page.coerceAtLeast(1)}&ps=${pageSize.coerceIn(1, 20)}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("B 站公开内容请求失败（HTTP ${response.code}）")
            val body = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IOException("B 站返回的数据格式无法识别") }
            val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
            if (code != 0) throw IOException(root["message"]?.jsonPrimitive?.contentOrNull ?: "B 站暂时拒绝了此次公开内容请求")
            val list = root["data"]?.jsonObject?.get("list")?.jsonArray ?: JsonArray(emptyList())
            list.mapNotNull { element -> element as? JsonObject }
                .mapNotNull { it.toLocalContent() }
        }
    }
}

private fun JsonObject.toLocalContent(): LocalContentEntity? {
    val bvid = text("bvid")
    val aid = text("aid")
    if (bvid.isBlank() && aid.isBlank()) return null

    val title = text("title").ifBlank { return null }
    val description = text("desc").replace(Regex("\\s+"), " ").trim()
    val owner = this["owner"]?.jsonObject?.text("name").orEmpty().ifBlank { "B 站创作者" }
    val duration = formatDuration(this["duration"]?.jsonPrimitive?.intOrNull ?: 0)
    val theme = LocalContentUnderstanding.classify(title, description, text("tname"))
    val now = System.currentTimeMillis()
    val remoteId = bvid.ifBlank { "av$aid" }
    return LocalContentEntity(
        contentKey = "bilibili:$remoteId",
        source = "B 站 · $owner",
        channel = SourceChannel.Video.name,
        title = title,
        readTime = duration,
        summary = description.ifBlank { "来自 B 站公开热门内容。" },
        theme = theme,
        url = if (bvid.isNotBlank()) "https://www.bilibili.com/video/$bvid" else "https://www.bilibili.com/video/av$aid",
        gradientStart = gradientStartFor(theme),
        gradientEnd = gradientEndFor(theme),
        createdAt = now,
        updatedAt = now
    )
}

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "视频"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun gradientStartFor(theme: String): Long = when {
    theme.contains("技术") || theme.contains("AI") -> 0xFF283B77
    theme.contains("商业") -> 0xFF1E5E59
    theme.contains("创作") -> 0xFF7A304E
    theme.contains("生活") -> 0xFF78552A
    else -> 0xFF333144
}

private fun gradientEndFor(theme: String): Long = when {
    theme.contains("技术") || theme.contains("AI") -> 0xFF5C8FE8
    theme.contains("商业") -> 0xFF55B8A9
    theme.contains("创作") -> 0xFFE07093
    theme.contains("生活") -> 0xFFE3AD5E
    else -> 0xFF8B79D6
}

/**
 * 端侧内容理解接口。当前实现为规则分类，后续可注入用户主动下载的本地模型，
 * 不向云端发送标题、简介或账号数据。
 */
interface LocalContentClassifier {
    fun classify(title: String, summary: String, category: String = ""): String
}

object LocalContentUnderstanding : LocalContentClassifier {
    override fun classify(title: String, summary: String, category: String): String {
        val text = "$title $summary $category".lowercase()
        return when {
            listOf("ai", "人工智能", "大模型", "机器学习", "模型", "算法", "编程", "代码", "开发", "数码", "科技").any(text::contains) -> "技术 · AI"
            listOf("创业", "商业", "公司", "产品", "运营", "财经", "投资", "经济").any(text::contains) -> "商业 · 决策"
            listOf("设计", "创作", "摄影", "剪辑", "音乐", "绘画", "写作", "动画").any(text::contains) -> "创作 · 表达"
            listOf("学习", "知识", "历史", "科普", "数学", "科学", "课程", "教程").any(text::contains) -> "学习 · 探索"
            listOf("生活", "旅行", "美食", "运动", "健康", "日常", "游戏").any(text::contains) -> "生活 · 兴趣"
            else -> "B站 · 热门"
        }
    }
}
