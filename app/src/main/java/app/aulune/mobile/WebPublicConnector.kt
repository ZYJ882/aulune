package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v2.0.0 通用 Web 内容连接器。
 *
 * 对齐 OpenBiliClaw 的 web_adapter：适配任意网页，用 Jsoup 抽取正文 + og: 元数据。
 * 默认订阅若干高活跃度英文/中文资讯站点（HN / Lobsters / TechCrunch 中文版）。
 *
 * 与 OpenBiliClaw 不同：Android 端不集成 Playwright，纯 Jsoup 解析静态 HTML；
 * 不调用 LLM 二次抽取（节省 token）；如用户需要深度分析，可手动在内容卡点击"用云端 AI 分析"。
 */
class WebPublicConnector(
    private val client: OkHttpClient = defaultWebClient,
) : PlatformPublicConnector {

    override val platform: ContentPlatform
        get() = ContentPlatform.BILIBILI // 占位；Web connector 用一个独立 ContentPlatform 更合适，但暂用 BILIBILI
    // 注意：当前版本未在 PlatformConnectorFactory 注册，因为需要一个独立 ContentPlatform 入口
    // 见 [ContentPlatform.WEB] 暂未加入枚举；后续可在 MultiPlatformContract.kt 加 WEB 项

    /** 信息源种子：完全公开 + 无需登录 + 支持 og:image */
    private val seeds = listOf(
        WebSeed("https://news.ycombinator.com", "Hacker News", SourceChannel.Insight),
        WebSeed("https://lobste.rs", "Lobsters", SourceChannel.Insight),
        WebSeed("https://www.techmeme.com", "Techmeme", SourceChannel.Brief),
    )

    override suspend fun fetchPublic(page: Int, pageSize: Int): List<LocalContentEntity> =
        withContext(Dispatchers.IO) {
            seeds.flatMap { seed ->
                try { fetchFromSeed(seed, pageSize / seeds.size + 1) } catch (_: Exception) { emptyList() }
            }.take(pageSize)
        }

    private fun fetchFromSeed(seed: WebSeed, limit: Int): List<LocalContentEntity> {
        val request = Request.Builder()
            .url(seed.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            return parseSiteHome(html, seed, limit)
        }
    }

    private fun parseSiteHome(html: String, seed: WebSeed, limit: Int): List<LocalContentEntity> {
        val doc = Jsoup.parse(html, seed.url)
        val items = mutableListOf<LocalContentEntity>()
        // Hacker News / Lobsters 都是 .titleline > a
        val titleLinks = doc.select(".titleline > a, .story > a, h2 > a, .entry-title > a")
        titleLinks.forEach { link ->
            if (items.size >= limit) return@forEach
            val title = link.text().trim()
            if (title.length < 8) return@forEach
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (!href.startsWith("http")) return@forEach
            // 抓 og:description 作为摘要
            val metaDesc = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
            val now = nowMillis()
            items += LocalContentEntity(
                contentKey = "web:${href.hashCode()}",
                source = "${seed.label} · Web",
                channel = seed.channel.name,
                title = title,
                readTime = "网页",
                summary = metaDesc.ifBlank { "来自 ${seed.label} 的资讯。" },
                theme = classifyTheme(title, metaDesc),
                url = href,
                gradientStart = platformGradientStart(title, ContentPlatform.BILIBILI),
                gradientEnd = platformGradientEnd(title, ContentPlatform.BILIBILI),
                createdAt = now,
                updatedAt = now,
                sourceKey = "web",
                authorKey = seed.label,
            )
        }
        return items
    }

    private data class WebSeed(
        val url: String,
        val label: String,
        val channel: SourceChannel,
    )

    companion object {
        private val defaultWebClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
