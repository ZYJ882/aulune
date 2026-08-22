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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B 站搜索 API。
 *
 * 复刻自 OpenBiliClaw src/openbiliclaw/bilibili/api.py 的 search 方法。
 * 使用 WBI 签名调用 /x/web-interface/wbi/search/type 接口。
 *
 * 特性：
 *  - WBI 签名（自动获取/缓存 img_key/sub_key）
 *  - v_voucher 检测与自动重试（刷新 WBI key 后重试）
 *  - 412 IP 封禁检测
 *  - 搜索结果解析为视频列表
 */
class BilibiliSearchApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    /** 搜索结果视频项 */
    data class SearchVideoItem(
        val bvid: String,
        val aid: Long,
        val title: String,
        val description: String,
        val coverUrl: String,
        val upName: String,
        val upMid: Long,
        val duration: String,
        val viewCount: Long,
        val danmakuCount: Long,
        val pubDate: Long,
        val tag: String,
    )

    /** 搜索结果 */
    data class SearchResult(
        val items: List<SearchVideoItem>,
        val total: Int,
        val page: Int,
        val pageSize: Int,
        val numResults: Int,
        val numPages: Int,
    )

    /**
     * 搜索视频。
     *
     * @param keyword 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（默认 20）
     * @param order 排序方式：totalrank(综合) / click(最多点击) / pubdate(最新发布) / dm(最多弹幕) / stow(最多收藏)
     * @param cookie 可选的登录 Cookie（未登录也可搜索，但结果可能受限）
     * @param maxAttempts 最大重试次数（v_voucher 时自动刷新 WBI key 重试）
     */
    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "totalrank",
        cookie: String? = null,
        maxAttempts: Int = 3,
    ): SearchResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                val signedParams = BilibiliWbiSign.signAndGetParams(
                    params = mapOf(
                        "keyword" to keyword,
                        "search_type" to "video",
                        "page" to page.toString(),
                        "page_size" to pageSize.toString(),
                        "order" to order,
                        "web_location" to "1550101",
                    ),
                    cookie = cookie,
                )
                val urlBuilder = "https://api.bilibili.com/x/web-interface/wbi/search/type".toHttpUrlOrNull()!!
                    .newBuilder()
                signedParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                val requestBuilder = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Accept", "application/json")
                    .header("Referer", "https://search.bilibili.com/all?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")
                    .header("Origin", "https://search.bilibili.com")
                    .header("User-Agent", BilibiliLoginConstants.USER_AGENT)
                    .get()
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookie)
                }
                val resp = client.newCall(requestBuilder.build()).execute()
                val searchResult: SearchResult? = resp.use { response ->
                    if (response.code == 412) {
                        throw SearchBlockedException("搜索被 IP 限流 (412)，请稍后重试")
                    }
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
                    val message = root["message"]?.jsonPrimitive?.contentOrNull ?: "未知错误"
                    if (code != 0) {
                        throw SearchException(code, message)
                    }
                    val data = root["data"]?.jsonObject
                        ?: return@use SearchResult(emptyList(), 0, page, pageSize, 0, 0)
                    // v_voucher 检测：WBI key 过期或限流。返回 null 以便在 Lambda 外重试。
                    if (data.containsKey("v_voucher") && data["result"] == null) {
                        if (attempt < maxAttempts) {
                            BilibiliWbiSign.invalidateCache()
                            return@use null
                        }
                        throw SearchException(-1, "搜索触发 v_voucher 验证，WBI key 刷新后仍失败")
                    }
                    val resultArray = data["result"]?.jsonArray ?: JsonArray(emptyList())
                    val items = resultArray.mapNotNull { it as? JsonObject }
                        .mapNotNull { parseSearchItem(it) }
                    val total = data["total"]?.jsonPrimitive?.intOrNull ?: 0
                    val numResults = data["numResults"]?.jsonPrimitive?.intOrNull ?: items.size
                    val numPages = data["numPages"]?.jsonPrimitive?.intOrNull ?: 1
                    SearchResult(items, total, page, pageSize, numResults, numPages)
                }
                if (searchResult != null) return@withContext searchResult
                // 已失效的 WBI key 已在上方清理；以新的 key 进入下一轮请求。
                continue
            } catch (e: SearchBlockedException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt >= maxAttempts) break
            }
        }
        throw lastError ?: SearchException(-1, "搜索失败")
    }

    private fun parseSearchItem(obj: JsonObject): SearchVideoItem? {
        val bvid = obj.str("bvid")
        val aid = obj.long("aid")
        if (bvid.isBlank() && aid == 0L) return null
        // 标题可能包含 <em class="keyword"> 高亮标签，需要去除
        val title = obj.str("title").replace(Regex("<[^>]+>"), "")
        return SearchVideoItem(
            bvid = bvid,
            aid = aid,
            title = title,
            description = obj.str("description"),
            coverUrl = "https:" + obj.str("pic").removePrefix("http:").removePrefix("https:"),
            upName = obj.str("author"),
            upMid = obj.long("mid"),
            duration = obj.str("duration"),
            viewCount = obj.long("play"),
            danmakuCount = obj.long("danmaku"),
            pubDate = obj.long("pubdate"),
            tag = obj.str("tag"),
        )
    }

    /** 搜索异常 */
    class SearchException(val code: Int, message: String) : Exception("[$code] $message")

    /** 搜索被 IP 限流 */
    class SearchBlockedException(message: String) : Exception(message)
}

// ═══════════════════════════════════════════════════════════════
//  JsonObject 扩展（与 BilibiliLoginApi 中的一致，避免重复依赖）
// ═══════════════════════════════════════════════════════════════

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L
