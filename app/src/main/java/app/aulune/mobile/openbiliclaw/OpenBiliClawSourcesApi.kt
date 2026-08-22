package app.aulune.mobile.openbiliclaw

import kotlinx.serialization.json.JsonObject

/**
 * OpenBiliClaw 多平台来源管理 API。
 *
 * 管理 11 个平台的数据源：Bilibili、抖音、小红书、知乎、微博、X/Twitter、
 * Reddit、YouTube、V2EX、Bangumi、LinuxDo。
 *
 * 每个平台支持：
 *  - 凭证设置（Cookie/Token/Identity）
 *  - 登录状态验证
 *  - 任务拉取（next-task）和结果上报（task-result）
 *  - 手动触发（kick）
 */
class OpenBiliClawSourcesApi(private val client: OpenBiliClawClient) {

    // ═══════════════════════════════════════════════════════════
    //  通用来源管理
    // ═══════════════════════════════════════════════════════════

    /** 获取所有来源状态 */
    suspend fun getSources(): JsonObject = client.get("/sources")

    /** 获取来源运行状态 */
    suspend fun getStatus(): JsonObject = client.get("/sources/status")

    /** 获取指定来源详情 */
    suspend fun getSource(recipeId: String): JsonObject = client.get("/sources/$recipeId")

    /** 获取来源凭证 */
    suspend fun getCredential(slug: String): JsonObject = client.get("/sources/$slug/credential")

    /** 设置来源凭证 */
    suspend fun setCredential(slug: String, credential: Map<String, Any?>): JsonObject =
        client.put("/sources/$slug/credential", credential)

    /** 验证来源登录状态 */
    suspend fun verify(slug: String): JsonObject = client.post("/sources/$slug/verify")

    /** 获取所有凭证 */
    suspend fun getCredentials(): JsonObject = client.get("/sources/credentials")

    // ═══════════════════════════════════════════════════════════
    //  Bilibili
    // ═══════════════════════════════════════════════════════════

    /** 设置 Bilibili Cookie（旧版接口，已弃用但仍可用） */
    suspend fun setBilibiliCookie(cookie: String, csrf: String = ""): JsonObject =
        client.post("/bilibili/cookie", mapOf("cookie" to cookie, "csrf" to csrf))

    /** 触发 Bilibili 任务 */
    suspend fun kickBilibili(): JsonObject = client.post("/sources/bili/kick")

    /** 获取 Bilibili 下一个任务 */
    suspend fun nextBilibiliTask(): JsonObject = client.get("/sources/bili/next-task")

    /** 上报 Bilibili 任务结果 */
    suspend fun submitBilibiliTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/bili/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  抖音 (Douyin)
    // ═══════════════════════════════════════════════════════════

    /** 设置抖音 Cookie（旧版接口） */
    suspend fun setDouyinCookie(cookie: String): JsonObject =
        client.post("/sources/dy/cookie", mapOf("cookie" to cookie))

    /** 触发抖音任务 */
    suspend fun kickDouyin(): JsonObject = client.post("/sources/dy/kick")

    /** 获取抖音下一个任务 */
    suspend fun nextDouyinTask(): JsonObject = client.get("/sources/dy/next-task")

    /** 上报抖音任务结果 */
    suspend fun submitDouyinTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/dy/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  小红书 (Xiaohongshu)
    // ═══════════════════════════════════════════════════════════

    /** 触发小红书任务 */
    suspend fun kickXiaohongshu(): JsonObject = client.post("/sources/xhs/kick")

    /** 获取小红书下一个任务 */
    suspend fun nextXiaohongshuTask(): JsonObject = client.get("/sources/xhs/next-task")

    /** 上报小红书任务结果 */
    suspend fun submitXiaohongshuTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/xhs/task-result", result)

    /** 获取小红书创作者列表 */
    suspend fun getXiaohongshuCreators(): JsonObject = client.get("/sources/xhs/creators")

    /** 获取小红书指定创作者 */
    suspend fun getXiaohongshuCreator(subId: String): JsonObject =
        client.get("/sources/xhs/creators/$subId")

    /** 获取小红书观察 URL 列表 */
    suspend fun getXiaohongshuObservedUrls(): JsonObject =
        client.get("/sources/xhs/observed-urls")

    /** 获取小红书 Token */
    suspend fun getXiaohongshuTokens(): JsonObject = client.get("/sources/xhs/tokens")

    // ═══════════════════════════════════════════════════════════
    //  知乎 (Zhihu)
    // ═══════════════════════════════════════════════════════════

    /** 触发知乎任务 */
    suspend fun kickZhihu(): JsonObject = client.post("/sources/zhihu/kick")

    /** 获取知乎下一个任务 */
    suspend fun nextZhihuTask(): JsonObject = client.get("/sources/zhihu/next-task")

    /** 上报知乎任务结果 */
    suspend fun submitZhihuTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/zhihu/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  微博 (Weibo)
    // ═══════════════════════════════════════════════════════════

    /** 触发微博任务 */
    suspend fun kickWeibo(): JsonObject = client.post("/sources/weibo/kick")

    /** 获取微博下一个任务 */
    suspend fun nextWeiboTask(): JsonObject = client.get("/sources/weibo/next-task")

    /** 上报微博任务结果 */
    suspend fun submitWeiboTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/weibo/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  X / Twitter
    // ═══════════════════════════════════════════════════════════

    /** 设置 X Cookie（旧版接口） */
    suspend fun setXCookie(cookie: String, authToken: String = "", ct0: String = ""): JsonObject =
        client.post("/sources/x/cookie", mapOf(
            "cookie" to cookie,
            "auth_token" to authToken,
            "ct0" to ct0,
        ))

    /** 触发 X 任务 */
    suspend fun kickX(): JsonObject = client.post("/sources/x/kick")

    /** 获取 X 下一个任务 */
    suspend fun nextXTask(): JsonObject = client.get("/sources/x/next-task")

    /** 上报 X 任务结果 */
    suspend fun submitXTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/x/task-result", result)

    /** 获取 X 状态 */
    suspend fun getXStatus(): JsonObject = client.get("/sources/x/status")

    /** 获取 X 创作者列表 */
    suspend fun getXCreators(): JsonObject = client.get("/sources/x/creators")

    /** 获取 X 指定创作者 */
    suspend fun getXCreator(subId: String): JsonObject =
        client.get("/sources/x/creators/$subId")

    // ═══════════════════════════════════════════════════════════
    //  Reddit
    // ═══════════════════════════════════════════════════════════

    /** 设置 Reddit Cookie（旧版接口） */
    suspend fun setRedditCookie(cookie: String): JsonObject =
        client.post("/sources/reddit/cookie", mapOf("cookie" to cookie))

    /** 触发 Reddit 任务 */
    suspend fun kickReddit(): JsonObject = client.post("/sources/reddit/kick")

    /** 获取 Reddit 下一个任务 */
    suspend fun nextRedditTask(): JsonObject = client.get("/sources/reddit/next-task")

    /** 上报 Reddit 任务结果 */
    suspend fun submitRedditTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/reddit/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  YouTube
    // ═══════════════════════════════════════════════════════════

    /** 触发 YouTube 任务 */
    suspend fun kickYoutube(): JsonObject = client.post("/sources/yt/kick")

    /** 获取 YouTube 下一个任务 */
    suspend fun nextYoutubeTask(): JsonObject = client.get("/sources/yt/next-task")

    /** 上报 YouTube 任务结果 */
    suspend fun submitYoutubeTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/yt/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  V2EX
    // ═══════════════════════════════════════════════════════════

    /** 触发 V2EX 任务 */
    suspend fun kickV2ex(): JsonObject = client.post("/sources/v2ex/kick")

    /** 获取 V2EX 下一个任务 */
    suspend fun nextV2exTask(): JsonObject = client.get("/sources/v2ex/next-task")

    /** 上报 V2EX 任务结果 */
    suspend fun submitV2exTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/v2ex/task-result", result)

    /** 设置 V2EX 身份信息 */
    suspend fun setV2exIdentity(identity: Map<String, Any?>): JsonObject =
        client.post("/sources/v2ex/identity", identity)

    /** 获取 V2EX 登录状态 */
    suspend fun getV2exLoginState(): JsonObject =
        client.get("/sources/v2ex/login-state")

    // ═══════════════════════════════════════════════════════════
    //  Bangumi
    // ═══════════════════════════════════════════════════════════

    /** 设置 Bangumi 身份信息 */
    suspend fun setBangumiIdentity(identity: Map<String, Any?>): JsonObject =
        client.post("/sources/bangumi/identity", identity)

    // ═══════════════════════════════════════════════════════════
    //  LinuxDo
    // ═══════════════════════════════════════════════════════════

    /** 触发 LinuxDo 任务 */
    suspend fun kickLinuxdo(): JsonObject = client.post("/sources/linuxdo/kick")

    /** 获取 LinuxDo 下一个任务 */
    suspend fun nextLinuxdoTask(): JsonObject = client.get("/sources/linuxdo/next-task")

    /** 上报 LinuxDo 任务结果 */
    suspend fun submitLinuxdoTaskResult(result: Map<String, Any?>): JsonObject =
        client.post("/sources/linuxdo/task-result", result)

    // ═══════════════════════════════════════════════════════════
    //  平台枚举到 API 的便捷映射
    // ═══════════════════════════════════════════════════════════

    /** 按平台触发任务 */
    suspend fun kick(platform: SourcePlatform): JsonObject = when (platform) {
        SourcePlatform.BILIBILI -> kickBilibili()
        SourcePlatform.DOUYIN -> kickDouyin()
        SourcePlatform.XIAOHONGSHU -> kickXiaohongshu()
        SourcePlatform.ZHIHU -> kickZhihu()
        SourcePlatform.WEIBO -> kickWeibo()
        SourcePlatform.TWITTER -> kickX()
        SourcePlatform.REDDIT -> kickReddit()
        SourcePlatform.YOUTUBE -> kickYoutube()
        SourcePlatform.V2EX -> kickV2ex()
        SourcePlatform.LINUXDO -> kickLinuxdo()
        else -> throw IllegalArgumentException("平台 ${platform.label} 不支持 kick")
    }

    /** 按平台获取下一个任务 */
    suspend fun nextTask(platform: SourcePlatform): JsonObject = when (platform) {
        SourcePlatform.BILIBILI -> nextBilibiliTask()
        SourcePlatform.DOUYIN -> nextDouyinTask()
        SourcePlatform.XIAOHONGSHU -> nextXiaohongshuTask()
        SourcePlatform.ZHIHU -> nextZhihuTask()
        SourcePlatform.WEIBO -> nextWeiboTask()
        SourcePlatform.TWITTER -> nextXTask()
        SourcePlatform.REDDIT -> nextRedditTask()
        SourcePlatform.YOUTUBE -> nextYoutubeTask()
        SourcePlatform.V2EX -> nextV2exTask()
        SourcePlatform.LINUXDO -> nextLinuxdoTask()
        else -> throw IllegalArgumentException("平台 ${platform.label} 不支持 next-task")
    }

    /** 按平台上报任务结果 */
    suspend fun submitTaskResult(platform: SourcePlatform, result: Map<String, Any?>): JsonObject =
        when (platform) {
            SourcePlatform.BILIBILI -> submitBilibiliTaskResult(result)
            SourcePlatform.DOUYIN -> submitDouyinTaskResult(result)
            SourcePlatform.XIAOHONGSHU -> submitXiaohongshuTaskResult(result)
            SourcePlatform.ZHIHU -> submitZhihuTaskResult(result)
            SourcePlatform.WEIBO -> submitWeiboTaskResult(result)
            SourcePlatform.TWITTER -> submitXTaskResult(result)
            SourcePlatform.REDDIT -> submitRedditTaskResult(result)
            SourcePlatform.YOUTUBE -> submitYoutubeTaskResult(result)
            SourcePlatform.V2EX -> submitV2exTaskResult(result)
            SourcePlatform.LINUXDO -> submitLinuxdoTaskResult(result)
            else -> throw IllegalArgumentException("平台 ${platform.label} 不支持 task-result")
        }
}
