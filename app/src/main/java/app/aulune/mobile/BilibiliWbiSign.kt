package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B 站 WBI (Web Bilibili Interface) 签名工具。
 *
 * 复刻自 OpenBiliClaw src/openbiliclaw/bilibili/api.py 的 WBI 签名实现。
 * 用于 B 站 Web 端搜索、空间等需要 w_rid 签名的接口。
 *
 * 算法流程：
 *  1. 从 /x/web-interface/nav 获取 wbi_img (img_url, sub_url)
 *  2. 从 URL 提取文件名（去扩展名）作为 img_key / sub_key
 *  3. img_key + sub_key 按 MIXIN_TAB 重排，取前 32 位 → mixin_key
 *  4. 参数加 wts（时间戳），按 key 排序，过滤特殊字符 [!'()*]
 *  5. query + mixin_key → MD5 → w_rid
 */
object BilibiliWbiSign {

    /** WBI mixin key 重排表（与 OpenBiliClaw 完全一致） */
    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22,
        25, 54, 21, 56, 59, 6, 57, 11, 36, 20, 34, 44, 52,
    )

    /** WBI key 缓存有效期（秒），与 OpenBiliClaw 一致为 300s */
    private const val KEY_TTL_SECONDS = 300L

    @Volatile
    private var cachedImgKey: String? = null

    @Volatile
    private var cachedSubKey: String? = null

    @Volatile
    private var keysFetchedAt = 0L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 获取 WBI img_key 和 sub_key（带缓存）。
     * 从 /x/web-interface/nav 的 wbi_img 字段提取。
     */
    suspend fun getWbiKeys(cookie: String? = null): Pair<String, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val cached = cachedImgKey
        val cachedSub = cachedSubKey
        if (cached != null && cachedSub != null && (now - keysFetchedAt) < KEY_TTL_SECONDS) {
            return@withContext cached to cachedSub
        }
        val requestBuilder = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/nav")
            .header("Accept", "application/json")
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BilibiliLoginConstants.USER_AGENT)
            .get()
        if (!cookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookie)
        }
        val resp = client.newCall(requestBuilder.build()).execute()
        resp.use { response ->
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: throw IllegalStateException("nav 接口未返回 data")
            val wbiImg = data["wbi_img"]?.jsonObject ?: throw IllegalStateException("nav 接口未返回 wbi_img")
            val imgUrl = wbiImg["img_url"]?.jsonPrimitive?.content.orEmpty()
            val subUrl = wbiImg["sub_url"]?.jsonPrimitive?.content.orEmpty()
            val imgKey = extractKeyComponent(imgUrl)
            val subKey = extractKeyComponent(subUrl)
            if (imgKey.isBlank() || subKey.isBlank()) {
                throw IllegalStateException("WBI key 提取失败")
            }
            cachedImgKey = imgKey
            cachedSubKey = subKey
            keysFetchedAt = now
            imgKey to subKey
        }
    }

    /**
     * 从 WBI 图片 URL 中提取 key 组件（文件名，去掉扩展名）。
     * 例如 https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png → 7cd084941338484aae1ad9425b84077c
     */
    fun extractKeyComponent(url: String): String {
        val path = url.substringAfterLast('/')
        return path.substringBeforeLast('.')
    }

    /**
     * 构建 mixin key。
     * img_key + sub_key 按 MIXIN_TAB 重排，取前 32 位。
     */
    fun buildMixinKey(imgKey: String, subKey: String): String {
        val merged = imgKey + subKey
        return buildString {
            for (index in MIXIN_TAB) {
                if (index < merged.length) {
                    append(merged[index])
                }
            }
        }.take(32)
    }

    /**
     * 对参数进行 WBI 签名。
     *
     * @param params 原始参数（不含 wts/w_rid）
     * @param imgKey WBI img_key
     * @param subKey WBI sub_key
     * @return 签名后的参数（包含 wts 和 w_rid）
     */
    fun signParams(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
    ): Map<String, String> {
        val mixinKey = buildMixinKey(imgKey, subKey)
        val wts = (System.currentTimeMillis() / 1000).toString()
        // 添加 wts，按 key 排序
        val withWts = params.toMutableMap()
        withWts["wts"] = wts
        val sorted = withWts.entries.sortedBy { it.key }
        // 过滤特殊字符 [!'()*]，拼接 query string
        val sanitized = sorted.associate { (k, v) ->
            k to v.replace(Regex("[!'()*]"), "")
        }
        val query = sanitized.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        // MD5(query + mixin_key) → w_rid
        val wRid = BilibiliLoginUtils.md5(query + mixinKey)
        return sanitized + ("w_rid" to wRid)
    }

    /**
     * 便捷方法：获取 key 并签名（自动处理缓存）。
     */
    suspend fun signAndGetParams(
        params: Map<String, String>,
        cookie: String? = null,
    ): Map<String, String> {
        val (imgKey, subKey) = getWbiKeys(cookie)
        return signParams(params, imgKey, subKey)
    }

    /** 强制刷新 WBI key 缓存（搜索返回 v_voucher 时调用） */
    fun invalidateCache() {
        cachedImgKey = null
        cachedSubKey = null
        keysFetchedAt = 0
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
