package app.aulune.mobile.openbiliclaw

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * OpenBiliClaw 后端统一入口。
 *
 * 整合所有 API 模块，提供对 OpenBiliClaw 后端（Python/FastAPI，默认端口 8420）的完整访问。
 *
 * 架构：
 *  - 后端运行在本地或局域网，负责多平台数据抓取、AI 推荐、内容发现
 *  - 本客户端通过 HTTP/REST + WebSocket 与后端通信
 *  - 支持 11 个平台：Bilibili、抖音、小红书、知乎、微博、X/Twitter、Reddit、YouTube、V2EX、Bangumi、LinuxDo
 *
 * 使用示例：
 * ```
 * val backend = OpenBiliClawBackend(context)
 * backend.connect("192.168.1.100", 8420)
 *
 * // 获取推荐
 * val recommendations = backend.content.getRecommendations()
 *
 * // AI 聊天
 * val turn = backend.chat.sendMessage("推荐一些科技视频")
 *
 * // 多平台登录状态
 * val sources = backend.sources.getSources()
 *
 * // 实时事件流
 * backend.client.connectRuntimeStream(object : RuntimeStreamListener {
 *     override fun onEvent(event: JsonObject) { ... }
 * })
 * ```
 */
class OpenBiliClawBackend(
    context: Context,
    initialConfig: OpenBiliClawConfig? = null,
) {
    private var config: OpenBiliClawConfig = initialConfig ?: OpenBiliClawConfig.load(context)
    private val appContext: Context = context.applicationContext

    /** 基础 HTTP + WebSocket 客户端 */
    val client: OpenBiliClawClient = OpenBiliClawClient(config)

    /** 多平台来源管理（11 平台凭证/验证/任务） */
    val sources: OpenBiliClawSourcesApi = OpenBiliClawSourcesApi(client)

    /** 内容拉取（推荐/收藏/历史/活动流/愉悦度） */
    val content: OpenBiliClawContentApi = OpenBiliClawContentApi(client)

    /** AI 聊天 */
    val chat: OpenBiliClawChatApi = OpenBiliClawChatApi(client)

    /** 系统管理（健康/初始化/配置/认证/更新/迁移） */
    val system: OpenBiliClawSystemApi = OpenBiliClawSystemApi(client)

    /** 个人资料（兴趣画像/回避主题/认知更新） */
    val profile: OpenBiliClawProfileApi = OpenBiliClawProfileApi(client)

    init {
        client.onSessionChanged = { token ->
            config = config.withSession(token)
            config.save(appContext)
        }
    }

    /**
     * 连接到指定后端。
     *
     * @param host 后端主机地址（Android 模拟器用 10.0.2.2 访问宿主机）
     * @param port 后端端口（默认 8420）
     * @param scheme 协议（http 或 https）
     */
    fun connect(host: String, port: Int = OpenBiliClawConfig.DEFAULT_PORT, scheme: String = "http") {
        config = OpenBiliClawConfig(scheme = scheme, host = host, port = port, sessionToken = config.sessionToken)
        client.updateConfig(config)
        config.save(appContext)
    }

    /** 获取当前配置 */
    fun getConfig(): OpenBiliClawConfig = config

    /** 检查后端是否可达 */
    suspend fun isReachable(): Boolean = client.checkHealth()

    /** 清除会话（登出） */
    fun clearSession() {
        config = config.clearSession()
        client.updateConfig(config)
        config.save(appContext)
    }

    companion object {
        /** Android 模拟器访问宿主机的特殊地址 */
        const val EMULATOR_HOST = OpenBiliClawConfig.EMULATOR_HOST

        /** 默认后端端口 */
        const val DEFAULT_PORT = OpenBiliClawConfig.DEFAULT_PORT

        /**
         * 支持的平台列表。
         */
        val SUPPORTED_PLATFORMS: List<SourcePlatform> = listOf(
            SourcePlatform.BILIBILI,
            SourcePlatform.DOUYIN,
            SourcePlatform.XIAOHONGSHU,
            SourcePlatform.ZHIHU,
            SourcePlatform.WEIBO,
            SourcePlatform.TWITTER,
            SourcePlatform.REDDIT,
            SourcePlatform.YOUTUBE,
            SourcePlatform.V2EX,
            SourcePlatform.BANGUMI,
            SourcePlatform.LINUXDO,
        )
    }
}
