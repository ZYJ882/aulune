package app.aulune.mobile

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v2.0.0 跨机器迁移 · 备份导出/导入。
 *
 * 对齐 OpenBiliClaw 的 .obcbackup 设计：
 * - 单一 JSON 文件包含所有 13 张表的数据
 * - 明文（含兴趣、画像、对话历史、行为事件；不含 API Key、Cookie、令牌）
 * - 导入前先校验 schema 版本，失败时取消并保留原数据
 * - 用 Room withTransaction 保证原子性：要么全成功，要么全回滚
 *
 * 与 OpenBiliClaw 不同：
 * - Android 端不打包 SQLite 文件本身（跨架构 ABI 风险），用 JSON 重新构造
 * - 不导出源机 API 登录密码 / 会话签名密钥 / 扩展设备 key（敏感信息本就不在本机 Room）
 *
 * 文件名格式：aulune-backup-YYYYMMDD-HHmmss.obcbackup
 */
object BackupManager {

    private const val BackupVersion = 1
    private const val BackupMagic = "aulune-backup"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false  // 紧凑，减小体积
    }

    @Serializable
    private data class BackupEnvelope(
        val magic: String = BackupMagic,
        val version: Int = BackupVersion,
        val createdAt: Long,
        val appVersionName: String,
        val appVersionCode: Long,
        val deviceLabel: String,  // 仅用于用户识别，不含 IMEI/Android ID
        val schema: BackupSchema,
    )

    @Serializable
    private data class BackupSchema(
        val databaseVersion: Int,
        val entitiesCount: Int,
        val tables: Map<String, Int>,  // tableName -> rowCount
    )

    @Serializable
    private data class BackupPayload(
        val envelope: BackupEnvelope,
        /** 所有表数据以 JSON 数组形式存储，键为表名 */
        val content: List<LocalContentEntityJson>,
        val events: List<BehaviorEventEntityJson>,
        val interests: List<InterestEntityJson>,
        val feedback: List<LocalFeedbackEntityJson>,
        val profiles: List<LocalProfileEntityJson>,
        val preferences: List<LocalPreferenceEntityJson>,
        val chatMessages: List<LocalChatMessageEntityJson>,
        val interestHypotheses: List<InterestHypothesisEntityJson>,
        val avoidanceHypotheses: List<AvoidanceHypothesisEntityJson>,
        val psychologicalProfiles: List<PsychologicalProfileEntityJson>,
        val viewedLedger: List<ViewedLedgerEntityJson>,
        val discoveryTasks: List<DiscoveryTaskEntityJson>,
        val sourceAvailability: List<SourceAvailabilityEntityJson>,
    )

    // ═══════════════════════════════════════════════════════════
    //  JSON 序列化 DTO（与 Entity 一一对应，但用 @Serializable 标注）
    // ═══════════════════════════════════════════════════════════

    @Serializable
    private data class LocalContentEntityJson(
        val contentKey: String, val source: String, val channel: String, val title: String,
        val readTime: String, val summary: String, val theme: String, val url: String,
        val gradientStart: Long, val gradientEnd: Long, val marked: Boolean, val saved: Boolean,
        val hidden: Boolean, val createdAt: Long, val updatedAt: Long,
        val sourceKey: String = "", val authorKey: String = "", val seriesKey: String = "",
        val topicGroup: String = "", val aiInsight: String = "", val analysisSource: String = "rule",
        val thumbnailUrl: String = "",
    )

    @Serializable
    private data class BehaviorEventEntityJson(
        val id: String, val contentKey: String, val eventType: String, val theme: String,
        val occurredAt: Long, val sourceKey: String = "", val topicGroup: String = "", val targetType: String = "",
    )

    @Serializable
    private data class InterestEntityJson(
        val theme: String, val weight: Double, val evidenceCount: Int,
        val lifecycle: String, val firstSeenAt: Long, val lastEvidenceAt: Long, val updatedAt: Long,
    )

    @Serializable
    private data class LocalFeedbackEntityJson(
        val id: String, val contentKey: String, val feedbackType: String,
        val targetType: String, val targetKey: String, val occurredAt: Long,
    )

    @Serializable
    private data class LocalProfileEntityJson(
        val layer: String, val summary: String, val candidate: String = "",
        val evidenceCount: Int = 0, val confirmationState: String = "automatic",
        val updatedAt: Long, val revision: Int = 1,
    )

    @Serializable
    private data class LocalPreferenceEntityJson(
        val key: String, val value: String, val updatedAt: Long,
    )

    @Serializable
    private data class LocalChatMessageEntityJson(
        val id: Long, val fromUser: Boolean, val text: String, val createdAt: Long, val turnId: String = "",
    )

    @Serializable
    private data class InterestHypothesisEntityJson(
        val id: String, val candidateTheme: String, val sourceTheme: String, val origin: String,
        val reason: String, val evidenceCount: Int, val status: String,
        val createdAt: Long, val expiresAt: Long, val decidedAt: Long = 0L,
    )

    @Serializable
    private data class AvoidanceHypothesisEntityJson(
        val id: String, val candidatePattern: String, val sourceTheme: String, val origin: String,
        val reason: String, val evidenceCount: Int, val status: String,
        val createdAt: Long, val expiresAt: Long, val decidedAt: Long = 0L,
    )

    @Serializable
    private data class PsychologicalProfileEntityJson(
        val dimension: String, val summary: String, val detail: String = "",
        val candidate: String = "", val candidateDetail: String = "",
        val evidenceCount: Int = 0, val confirmationState: String = "automatic",
        val updatedAt: Long, val revision: Int = 1,
    )

    @Serializable
    private data class ViewedLedgerEntityJson(
        val contentKey: String, val viewedAt: Long,
    )

    @Serializable
    private data class DiscoveryTaskEntityJson(
        val taskId: String, val kind: String, val status: String,
        val createdAt: Long, val startedAt: Long = 0L, val finishedAt: Long = 0L,
        val discoveredCount: Int = 0, val checkedSources: Int = 0, val availableSources: Int = 0,
        val detail: String = "",
    )

    @Serializable
    private data class SourceAvailabilityEntityJson(
        val platform: String, val state: String, val detail: String,
        val checkedAt: Long, val discoveredCount: Int = 0, val attempts: Int = 0,
    )

    // ═══════════════════════════════════════════════════════════
    //  导出
    // ═══════════════════════════════════════════════════════════

    data class ExportResult(
        val success: Boolean,
        val targetUri: Uri? = null,
        val bytesWritten: Long = 0L,
        val errorMessage: String? = null,
        val tableCounts: Map<String, Int> = emptyMap(),
    )

    suspend fun export(context: Context, targetUri: Uri, deviceLabel: String = "本机"): ExportResult = withContext(Dispatchers.IO) {
        try {
            val db = AuluneLocalDatabase.create(context)
            val dao = db.localCoreDao()

            val content = dao.dumpAllContent()
            val events = dao.dumpAllEvents()
            val interests = dao.dumpAllInterests()
            val feedback = dao.dumpAllFeedback()
            val profiles = dao.dumpAllProfiles()
            val preferences = dao.dumpAllPreferences()
            val chatMessages = dao.dumpAllChatMessages()
            val interestHypotheses = dao.dumpAllInterestHypotheses()
            val avoidanceHypotheses = dao.dumpAllAvoidanceHypotheses()
            val psychologicalProfiles = dao.dumpAllPsychologicalProfiles()
            val viewedLedger = dao.dumpAllViewedLedger()
            val discoveryTasks = dao.dumpAllDiscoveryTasks()
            val sourceAvailability = dao.dumpAllSourceAvailability()

            val tableCounts = mapOf(
                "local_content" to content.size,
                "behavior_event" to events.size,
                "interest" to interests.size,
                "local_feedback" to feedback.size,
                "local_profile" to profiles.size,
                "local_preference" to preferences.size,
                "local_chat_message" to chatMessages.size,
                "local_interest_hypothesis" to interestHypotheses.size,
                "local_avoidance_hypothesis" to avoidanceHypotheses.size,
                "local_psychological_profile" to psychologicalProfiles.size,
                "local_viewed_ledger" to viewedLedger.size,
                "local_discovery_task" to discoveryTasks.size,
                "source_availability" to sourceAvailability.size,
            )

            val envelope = BackupEnvelope(
                createdAt = System.currentTimeMillis(),
                appVersionName = "2.0.0-dev",
                appVersionCode = 100000050L,
                deviceLabel = deviceLabel,
                schema = BackupSchema(
                    databaseVersion = 10,
                    entitiesCount = 13,
                    tables = tableCounts,
                ),
            )

            val payload = BackupPayload(
                envelope = envelope,
                content = content.map { it.toJson() },
                events = events.map { it.toJson() },
                interests = interests.map { it.toJson() },
                feedback = feedback.map { it.toJson() },
                profiles = profiles.map { it.toJson() },
                preferences = preferences.map { it.toJson() },
                chatMessages = chatMessages.map { it.toJson() },
                interestHypotheses = interestHypotheses.map { it.toJson() },
                avoidanceHypotheses = avoidanceHypotheses.map { it.toJson() },
                psychologicalProfiles = psychologicalProfiles.map { it.toJson() },
                viewedLedger = viewedLedger.map { it.toJson() },
                discoveryTasks = discoveryTasks.map { it.toJson() },
                sourceAvailability = sourceAvailability.map { it.toJson() },
            )

            val jsonStr = json.encodeToString(BackupPayload.serializer(), payload)
            val bytes = jsonStr.toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return@withContext ExportResult(false, errorMessage = "无法写入目标文件")

            ExportResult(
                success = true,
                targetUri = targetUri,
                bytesWritten = bytes.size.toLong(),
                tableCounts = tableCounts,
            )
        } catch (error: Throwable) {
            ExportResult(false, errorMessage = error.message ?: "导出失败")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  导入
    // ═══════════════════════════════════════════════════════════

    data class ImportResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val tableCounts: Map<String, Int> = emptyMap(),
        val sourceDevice: String = "",
        val sourceCreatedAt: Long = 0L,
        val sourceAppVersion: String = "",
    )

    /** 仅校验文件，不清数据；用于"导入前预览" */
    suspend fun validate(context: Context, sourceUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val payload = readPayload(context, sourceUri)
            if (payload.envelope.magic != BackupMagic) {
                return@withContext ImportResult(false, errorMessage = "文件不是有效的 Aulune 备份（magic 不匹配）")
            }
            if (payload.envelope.version > BackupVersion) {
                return@withContext ImportResult(false, errorMessage = "备份版本 v${payload.envelope.version} 高于当前应用支持版本 v$BackupVersion")
            }
            ImportResult(
                success = true,
                tableCounts = payload.envelope.schema.tables,
                sourceDevice = payload.envelope.deviceLabel,
                sourceCreatedAt = payload.envelope.createdAt,
                sourceAppVersion = payload.envelope.appVersionName,
            )
        } catch (error: Throwable) {
            ImportResult(false, errorMessage = error.message ?: "无法解析备份文件")
        }
    }

    /** 真正写入；导入前会清空所有用户表（保留 LLM provider 配置，因其在加密 Keystore 不在 Room） */
    suspend fun import(context: Context, sourceUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val payload = readPayload(context, sourceUri)
            if (payload.envelope.magic != BackupMagic) {
                return@withContext ImportResult(false, errorMessage = "文件不是有效的 Aulune 备份")
            }
            if (payload.envelope.version > BackupVersion) {
                return@withContext ImportResult(false, errorMessage = "备份版本过高，请升级 Aulune 后再导入")
            }

            val db = AuluneLocalDatabase.create(context)
            val dao = db.localCoreDao()

            // 用 Room 事务保证原子性：清空 + 写入要么全部成功，要么全部回滚
            db.withTransaction {
                // 清空所有用户数据表
                dao.clearContent()
                dao.clearEvents()
                dao.clearInterests()
                dao.clearFeedback()
                dao.clearProfiles()
                dao.clearPreferences()
                dao.clearChatMessages()
                dao.clearInterestHypotheses()
                dao.clearAvoidanceHypotheses()
                dao.clearPsychologicalProfiles()
                dao.clearViewedLedger()
                dao.clearDiscoveryTasks()
                dao.clearSourceAvailability()

                // 批量写入
                if (payload.content.isNotEmpty()) dao.bulkInsertContent(payload.content.map { it.toEntity() })
                if (payload.events.isNotEmpty()) dao.bulkInsertEvents(payload.events.map { it.toEntity() })
                if (payload.interests.isNotEmpty()) dao.bulkInsertInterests(payload.interests.map { it.toEntity() })
                if (payload.feedback.isNotEmpty()) dao.bulkInsertFeedback(payload.feedback.map { it.toEntity() })
                if (payload.profiles.isNotEmpty()) dao.bulkInsertProfiles(payload.profiles.map { it.toEntity() })
                if (payload.preferences.isNotEmpty()) dao.bulkInsertPreferences(payload.preferences.map { it.toEntity() })
                if (payload.chatMessages.isNotEmpty()) dao.bulkInsertChatMessages(payload.chatMessages.map { it.toEntity() })
                if (payload.interestHypotheses.isNotEmpty()) dao.bulkInsertInterestHypotheses(payload.interestHypotheses.map { it.toEntity() })
                if (payload.avoidanceHypotheses.isNotEmpty()) dao.bulkInsertAvoidanceHypotheses(payload.avoidanceHypotheses.map { it.toEntity() })
                if (payload.psychologicalProfiles.isNotEmpty()) dao.bulkInsertPsychologicalProfiles(payload.psychologicalProfiles.map { it.toEntity() })
                if (payload.viewedLedger.isNotEmpty()) dao.bulkInsertViewedLedger(payload.viewedLedger.map { it.toEntity() })
                if (payload.discoveryTasks.isNotEmpty()) dao.bulkInsertDiscoveryTasks(payload.discoveryTasks.map { it.toEntity() })
                if (payload.sourceAvailability.isNotEmpty()) dao.bulkInsertSourceAvailability(payload.sourceAvailability.map { it.toEntity() })
            }

            ImportResult(
                success = true,
                tableCounts = payload.envelope.schema.tables,
                sourceDevice = payload.envelope.deviceLabel,
                sourceCreatedAt = payload.envelope.createdAt,
                sourceAppVersion = payload.envelope.appVersionName,
            )
        } catch (error: Throwable) {
            ImportResult(false, errorMessage = error.message ?: "导入失败")
        }
    }

    private fun readPayload(context: Context, sourceUri: Uri): BackupPayload {
        val jsonStr = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IOException("无法读取备份文件")
        return json.decodeFromString(BackupPayload.serializer(), jsonStr)
    }

    // ═══════════════════════════════════════════════════════════
    //  Entity ↔ Json 转换器
    // ═══════════════════════════════════════════════════════════

    private fun LocalContentEntity.toJson() = LocalContentEntityJson(
        contentKey, source, channel, title, readTime, summary, theme, url,
        gradientStart, gradientEnd, marked, saved, hidden, createdAt, updatedAt,
        sourceKey, authorKey, seriesKey, topicGroup, aiInsight, analysisSource, thumbnailUrl,
    )
    private fun LocalContentEntityJson.toEntity() = LocalContentEntity(
        contentKey, source, channel, title, readTime, summary, theme, url,
        gradientStart, gradientEnd, marked, saved, hidden, createdAt, updatedAt,
        sourceKey, authorKey, seriesKey, topicGroup, aiInsight, analysisSource, thumbnailUrl,
    )

    private fun BehaviorEventEntity.toJson() = BehaviorEventEntityJson(id, contentKey, eventType, theme, occurredAt, sourceKey, topicGroup, targetType)
    private fun BehaviorEventEntityJson.toEntity() = BehaviorEventEntity(id, contentKey, eventType, theme, occurredAt, sourceKey, topicGroup, targetType)

    private fun InterestEntity.toJson() = InterestEntityJson(theme, weight, evidenceCount, lifecycle, firstSeenAt, lastEvidenceAt, updatedAt)
    private fun InterestEntityJson.toEntity() = InterestEntity(theme, weight, evidenceCount, lifecycle, firstSeenAt, lastEvidenceAt, updatedAt)

    private fun LocalFeedbackEntity.toJson() = LocalFeedbackEntityJson(id, contentKey, feedbackType, targetType, targetKey, occurredAt)
    private fun LocalFeedbackEntityJson.toEntity() = LocalFeedbackEntity(id, contentKey, feedbackType, targetType, targetKey, occurredAt)

    private fun LocalProfileEntity.toJson() = LocalProfileEntityJson(layer, summary, candidate, evidenceCount, confirmationState, updatedAt, revision)
    private fun LocalProfileEntityJson.toEntity() = LocalProfileEntity(layer, summary, candidate, evidenceCount, confirmationState, updatedAt, revision)

    private fun LocalPreferenceEntity.toJson() = LocalPreferenceEntityJson(key, value, updatedAt)
    private fun LocalPreferenceEntityJson.toEntity() = LocalPreferenceEntity(key, value, updatedAt)

    private fun LocalChatMessageEntity.toJson() = LocalChatMessageEntityJson(id, fromUser, text, createdAt, turnId)
    private fun LocalChatMessageEntityJson.toEntity() = LocalChatMessageEntity(id, fromUser, text, createdAt, turnId)

    private fun InterestHypothesisEntity.toJson() = InterestHypothesisEntityJson(id, candidateTheme, sourceTheme, origin, reason, evidenceCount, status, createdAt, expiresAt, decidedAt)
    private fun InterestHypothesisEntityJson.toEntity() = InterestHypothesisEntity(id, candidateTheme, sourceTheme, origin, reason, evidenceCount, status, createdAt, expiresAt, decidedAt)

    private fun AvoidanceHypothesisEntity.toJson() = AvoidanceHypothesisEntityJson(id, candidatePattern, sourceTheme, origin, reason, evidenceCount, status, createdAt, expiresAt, decidedAt)
    private fun AvoidanceHypothesisEntityJson.toEntity() = AvoidanceHypothesisEntity(id, candidatePattern, sourceTheme, origin, reason, evidenceCount, status, createdAt, expiresAt, decidedAt)

    private fun PsychologicalProfileEntity.toJson() = PsychologicalProfileEntityJson(dimension, summary, detail, candidate, candidateDetail, evidenceCount, confirmationState, updatedAt, revision)
    private fun PsychologicalProfileEntityJson.toEntity() = PsychologicalProfileEntity(dimension, summary, detail, candidate, candidateDetail, evidenceCount, confirmationState, updatedAt, revision)

    private fun ViewedLedgerEntity.toJson() = ViewedLedgerEntityJson(contentKey, viewedAt)
    private fun ViewedLedgerEntityJson.toEntity() = ViewedLedgerEntity(contentKey, viewedAt)

    private fun DiscoveryTaskEntity.toJson(): DiscoveryTaskEntityJson {
        // DiscoveryTaskEntity 是现成的，需要单独读取其字段
        return DiscoveryTaskEntityJson(
            taskId = this.taskId,
            kind = this.kind,
            status = this.status,
            createdAt = this.createdAt,
            startedAt = this.startedAt,
            finishedAt = this.finishedAt,
            discoveredCount = this.discoveredCount,
            checkedSources = this.checkedSources,
            availableSources = this.availableSources,
            detail = this.detail,
        )
    }
    private fun DiscoveryTaskEntityJson.toEntity() = DiscoveryTaskEntity(
        taskId = taskId,
        kind = kind,
        status = status,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        discoveredCount = discoveredCount,
        checkedSources = checkedSources,
        availableSources = availableSources,
        detail = detail,
    )

    private fun SourceAvailabilityEntity.toJson(): SourceAvailabilityEntityJson {
        return SourceAvailabilityEntityJson(
            platform = platform,
            state = state,
            detail = detail,
            checkedAt = checkedAt,
            discoveredCount = discoveredCount,
            attempts = attempts,
        )
    }
    private fun SourceAvailabilityEntityJson.toEntity() = SourceAvailabilityEntity(
        platform = platform,
        state = state,
        detail = detail,
        checkedAt = checkedAt,
        discoveredCount = discoveredCount,
        attempts = attempts,
    )

    // ═══════════════════════════════════════════════════════════
    //  文件名生成
    // ═══════════════════════════════════════════════════════════

    fun generateFileName(now: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        return "aulune-backup-${sdf.format(Date(now))}.obcbackup"
    }

    /** 人类可读的文件大小 */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }

    /** 人类可读的时间戳 */
    fun formatTimestamp(epoch: Long): String {
        if (epoch <= 0L) return "未知时间"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epoch))
    }
}
