package app.aulune.mobile.openbiliclaw

import kotlinx.serialization.json.JsonObject

/**
 * OpenBiliClaw 系统管理 API。
 *
 * 提供健康检查、初始化、配置、认证、更新、数据迁移等系统级能力。
 */
class OpenBiliClawSystemApi(private val client: OpenBiliClawClient) {

    // ═══════════════════════════════════════════════════════════
    //  健康检查 & 基础信息
    // ═══════════════════════════════════════════════════════════

    /** 健康检查 */
    suspend fun health(): JsonObject = client.get("/health")

    /** Ping */
    suspend fun ping(): JsonObject = client.get("/ping")

    /** 获取二维码信息（用于浏览器扩展配对） */
    suspend fun getQrInfo(): JsonObject = client.get("/qr-info")

    // ═══════════════════════════════════════════════════════════
    //  认证（后端密码门）
    // ═══════════════════════════════════════════════════════════

    /** 获取认证状态 */
    suspend fun authStatus(): JsonObject = client.get("/auth/status")

    /** 登录（输入后端密码） */
    suspend fun login(password: String): JsonObject =
        client.post("/auth/login", mapOf("password" to password))

    /** 登出 */
    suspend fun logout(): JsonObject = client.post("/auth/logout")

    /** 获取扩展令牌 */
    suspend fun getExtensionToken(): JsonObject = client.post("/auth/extension-token")

    // ═══════════════════════════════════════════════════════════
    //  初始化
    // ═══════════════════════════════════════════════════════════

    /** 获取初始化状态 */
    suspend fun getInitStatus(): JsonObject = client.get("/init-status")

    /** 开始初始化 */
    suspend fun init(config: Map<String, Any?>): JsonObject =
        client.post("/init", config, timeoutSeconds = 120)

    /** 取消初始化 */
    suspend fun cancelInit(): JsonObject = client.post("/init/cancel")

    /** 标记初始化完成 */
    suspend fun initCompleted(): JsonObject = client.post("/init-completed")

    /** 修复嵌入向量 */
    suspend fun repairEmbeddings(): JsonObject =
        client.post("/embedding/repair", timeoutSeconds = 120)

    /** 获取嵌入修复状态 */
    suspend fun getEmbeddingRepairStatus(): JsonObject = client.get("/embedding/repair")

    // ═══════════════════════════════════════════════════════════
    //  配置
    // ═══════════════════════════════════════════════════════════

    /** 获取配置 */
    suspend fun getConfig(): JsonObject = client.get("/config")

    /** 应用配置 */
    suspend fun applyConfig(config: Map<String, Any?>): JsonObject =
        client.put("/config", config)

    /** 获取配置应用状态 */
    suspend fun getConfigApplyStatus(): JsonObject = client.get("/config/apply-status")

    /** 发现可用的 LLM 模型 */
    suspend fun discoverModels(provider: String = "", baseUrl: String = "", apiKey: String = ""): JsonObject =
        client.post(
            "/config/discover-models",
            mapOf(
                "provider" to provider,
                "base_url" to baseUrl,
                "api_key" to apiKey,
            ),
        )

    /** 探测服务可用性 */
    suspend fun probeService(service: String, config: Map<String, Any?>): JsonObject =
        client.post("/config/probe-service", mapOf("service" to service, "config" to config))

    // ═══════════════════════════════════════════════════════════
    //  更新
    // ═══════════════════════════════════════════════════════════

    /** 检查更新 */
    suspend fun checkUpdate(): JsonObject = client.get("/update/check")

    /** 应用更新 */
    suspend fun applyUpdate(): JsonObject = client.post("/update/apply", timeoutSeconds = 120)

    /** 获取更新状态 */
    suspend fun getUpdateStatus(): JsonObject = client.get("/update-status")

    // ═══════════════════════════════════════════════════════════
    //  自动启动
    // ═══════════════════════════════════════════════════════════

    /** 获取自动启动状态 */
    suspend fun getAutostartStatus(): JsonObject = client.get("/autostart-status")

    /** 应用自动启动配置 */
    suspend fun applyAutostart(config: Map<String, Any?>): JsonObject =
        client.post("/autostart/apply", config)

    // ═══════════════════════════════════════════════════════════
    //  数据迁移
    // ═══════════════════════════════════════════════════════════

    /** 导出数据 */
    suspend fun exportData(): JsonObject = client.get("/migration/export")

    /** 导入数据 */
    suspend fun importData(data: Map<String, Any?>): JsonObject =
        client.post("/migration/import", data, timeoutSeconds = 120)

    /** 获取待迁移数据 */
    suspend fun getPendingMigration(): JsonObject = client.get("/migration/pending")

    /** 获取迁移状态 */
    suspend fun getMigrationStatus(): JsonObject = client.get("/migration/status")

    // ═══════════════════════════════════════════════════════════
    //  事件上报
    // ═══════════════════════════════════════════════════════════

    /** 上报事件（观看、点击等） */
    suspend fun ingestEvent(event: Map<String, Any?>): JsonObject =
        client.post("/events", event)

    // ═══════════════════════════════════════════════════════════
    //  图片代理
    // ═══════════════════════════════════════════════════════════

    /** 获取图片代理 URL */
    fun imageProxyUrl(originalUrl: String): String =
        "${client.getConfig().originUrl}/api/image-proxy?url=${java.net.URLEncoder.encode(originalUrl, "UTF-8")}"

    // ═══════════════════════════════════════════════════════════
    //  浏览器扩展
    // ═══════════════════════════════════════════════════════════

    /** 重新加载扩展 */
    suspend fun reloadExtension(): JsonObject = client.post("/extension/reload")

    /** 运行扩展 E2E 测试 */
    suspend fun runExtensionE2E(test: Map<String, Any?>): JsonObject =
        client.post("/extension/e2e/run", test, timeoutSeconds = 120)

    /** 获取扩展 E2E 测试结果 */
    suspend fun getExtensionE2EResult(testId: String): JsonObject =
        client.get("/extension/e2e/result?test_id=$testId")
}

/**
 * OpenBiliClaw 个人资料 API。
 *
 * 管理用户兴趣画像、回避主题、偏好平台等。
 */
class OpenBiliClawProfileApi(private val client: OpenBiliClawClient) {

    /** 获取个人资料摘要 */
    suspend fun getProfileSummary(
        refresh: Boolean = false,
        timeoutSeconds: Int = 30,
    ): ProfileSummary {
        val query = if (refresh) "?refresh=true" else ""
        val json = client.get("/profile-summary$query", timeoutSeconds = timeoutSeconds)
        return ProfileSummary.fromJson(json)
    }

    /** 获取个人资料编辑状态 */
    suspend fun getProfileEditState(): ProfileEditState {
        val json = client.get("/profile/edit-state")
        return ProfileEditState.fromJson(json)
    }

    /** 编辑个人资料 */
    suspend fun editProfile(state: ProfileEditState): JsonObject =
        client.post("/profile/edit", state.toPayload())

    /** 获取待处理的认知更新 */
    suspend fun getPendingCognitionUpdates(): JsonObject =
        client.get("/cognition-updates/pending")

    /** 标记认知更新已查看 */
    suspend fun markCognitionUpdateSeen(updateId: String): JsonObject =
        client.post("/cognition-updates/seen", mapOf("update_id" to updateId))

    /** 提交洞察反馈 */
    suspend fun submitInsightFeedback(insightId: String, feedback: String, useful: Boolean): JsonObject =
        client.post(
            "/insights/feedback",
            mapOf(
                "insight_id" to insightId,
                "feedback" to feedback,
                "useful" to useful,
            ),
        )
}
