package app.aulune.mobile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 手机本机优先的数据底座。内容、标准行为事件、反馈、兴趣生命周期和推荐状态
 * 全部保存在应用私有 SQLite 数据库，不依赖服务器或云端模型。
 */
@Entity(tableName = "local_content")
data class LocalContentEntity(
    @androidx.room.PrimaryKey val contentKey: String,
    val source: String,
    val channel: String,
    val title: String,
    val readTime: String,
    val summary: String,
    val theme: String,
    val url: String,
    val gradientStart: Long,
    val gradientEnd: Long,
    val marked: Boolean = false,
    val saved: Boolean = false,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceKey: String = "",
    val authorKey: String = "",
    val seriesKey: String = "",
    val topicGroup: String = "",
    val aiInsight: String = "",
    val analysisSource: String = "rule"
)

@Entity(tableName = "behavior_event")
data class BehaviorEventEntity(
    @androidx.room.PrimaryKey val id: String,
    val contentKey: String,
    val eventType: String,
    val theme: String,
    val occurredAt: Long,
    val sourceKey: String = "",
    val topicGroup: String = "",
    val targetType: String = ""
)

@Entity(tableName = "interest")
data class InterestEntity(
    @androidx.room.PrimaryKey val theme: String,
    val weight: Double,
    val evidenceCount: Int,
    val lifecycle: String = InterestLifecycle.Trial.name,
    val firstSeenAt: Long = 0L,
    val lastEvidenceAt: Long = 0L,
    val updatedAt: Long
)

@Entity(tableName = "local_feedback")
data class LocalFeedbackEntity(
    @androidx.room.PrimaryKey val id: String,
    val contentKey: String,
    val feedbackType: String,
    val targetType: String,
    val targetKey: String,
    val occurredAt: Long
)

/** P3 分层画像；Values/Core 只能由用户确认写入，不会被行为自动覆盖。 */
@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @androidx.room.PrimaryKey val layer: String,
    val summary: String,
    val candidate: String = "",
    val evidenceCount: Int = 0,
    val confirmationState: String = "automatic",
    val updatedAt: Long,
    val revision: Int = 1
)

@Entity(tableName = "local_preference")
data class LocalPreferenceEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

/** 对话历史只保存于本机，用于让用户在重启后继续同一段思考。 */
@Entity(tableName = "local_chat_message")
data class LocalChatMessageEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long
)

@Dao
interface LocalCoreDao {
    @Query("SELECT * FROM local_content WHERE hidden = 0")
    fun observeVisibleContent(): Flow<List<LocalContentEntity>>

    @Query("SELECT * FROM local_content ORDER BY updatedAt DESC")
    fun observeAllContent(): Flow<List<LocalContentEntity>>

    @Query("SELECT COUNT(*) FROM local_content WHERE saved = 1")
    fun observeSavedCount(): Flow<Int>

    @Query("SELECT * FROM interest ORDER BY weight DESC, updatedAt DESC")
    fun observeInterests(): Flow<List<InterestEntity>>

    @Query("SELECT * FROM behavior_event ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 30): Flow<List<BehaviorEventEntity>>

    @Query("SELECT * FROM behavior_event ORDER BY occurredAt ASC")
    suspend fun allEvents(): List<BehaviorEventEntity>

    @Query("SELECT * FROM local_feedback ORDER BY occurredAt DESC LIMIT :limit")
    fun observeFeedback(limit: Int = 100): Flow<List<LocalFeedbackEntity>>

    @Query("SELECT * FROM local_profile ORDER BY layer ASC")
    fun observeProfiles(): Flow<List<LocalProfileEntity>>

    @Query("SELECT * FROM local_profile ORDER BY layer ASC")
    suspend fun profilesNow(): List<LocalProfileEntity>

    @Query("SELECT * FROM local_chat_message ORDER BY createdAt ASC, id ASC")
    fun observeChatMessages(): Flow<List<LocalChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM local_chat_message")
    suspend fun chatMessageCount(): Int

    @Insert
    suspend fun insertChatMessage(message: LocalChatMessageEntity)

    @Query("DELETE FROM local_chat_message")
    suspend fun clearChatMessages()

    @Query("SELECT COUNT(*) FROM behavior_event")
    suspend fun eventCount(): Int

    @Query("SELECT * FROM local_content")
    suspend fun allContent(): List<LocalContentEntity>

    @Query("SELECT value FROM local_preference WHERE `key` = :key LIMIT 1")
    suspend fun preferenceValue(key: String): String?

    @Query("SELECT value FROM local_preference WHERE `key` = :key LIMIT 1")
    fun observePreferenceValue(key: String): Flow<String?>

    @Query("SELECT COUNT(*) FROM local_content")
    suspend fun contentCount(): Int

    @Query("SELECT * FROM local_content WHERE contentKey IN (:keys)")
    suspend fun contentByKeys(keys: List<String>): List<LocalContentEntity>

    @Query("SELECT * FROM local_content WHERE contentKey = :contentKey LIMIT 1")
    suspend fun contentByKey(contentKey: String): LocalContentEntity?

    @Query("SELECT * FROM interest ORDER BY weight DESC, updatedAt DESC")
    suspend fun interestsNow(): List<InterestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(items: List<LocalContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInterest(item: InterestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInterests(items: List<InterestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BehaviorEventEntity)

    @Query("SELECT COUNT(*) FROM behavior_event WHERE id = :eventId")
    suspend fun eventExists(eventId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: LocalFeedbackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(items: List<LocalProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(item: LocalPreferenceEntity)

    @Query("SELECT * FROM local_profile WHERE layer = :layer LIMIT 1")
    suspend fun profile(layer: String): LocalProfileEntity?

    @Query("DELETE FROM local_profile WHERE layer = :layer")
    suspend fun deleteProfile(layer: String)

    @Query("DELETE FROM local_feedback WHERE contentKey = :contentKey AND targetType = :targetType AND targetKey = :targetKey")
    suspend fun clearFeedbackTarget(contentKey: String, targetType: String, targetKey: String)

    @Query("UPDATE local_content SET marked = :value, updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun setMarked(contentKey: String, value: Boolean, now: Long)

    @Query("UPDATE local_content SET saved = :value, updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun setSaved(contentKey: String, value: Boolean, now: Long)

    @Query("UPDATE local_content SET hidden = :value, updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun setHidden(contentKey: String, value: Boolean, now: Long)

    @Query("UPDATE local_content SET theme = :theme, topicGroup = :topicGroup, seriesKey = :seriesKey, aiInsight = :aiInsight, analysisSource = 'cloud', updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun applyCloudAnalysis(contentKey: String, theme: String, topicGroup: String, seriesKey: String, aiInsight: String, now: Long)

    @Query("UPDATE behavior_event SET theme = :theme, topicGroup = :topicGroup WHERE contentKey = :contentKey")
    suspend fun reclassifyContentEvents(contentKey: String, theme: String, topicGroup: String)

    @Query("UPDATE local_profile SET candidate = :candidate, confirmationState = 'pending', updatedAt = :now, revision = revision + 1 WHERE layer = :layer")
    suspend fun applyCloudProfileCandidate(layer: String, candidate: String, now: Long)

    @Query("SELECT * FROM interest WHERE theme = :theme LIMIT 1")
    suspend fun interest(theme: String): InterestEntity?

    @Query("DELETE FROM interest")
    suspend fun clearInterests()

    @Query("DELETE FROM local_content WHERE contentKey LIKE 'bilibili:%'")
    suspend fun deleteBilibiliContent()

    @Query("DELETE FROM behavior_event WHERE contentKey LIKE 'bilibili:%'")
    suspend fun deleteBilibiliEvents()

    @Query("DELETE FROM local_feedback WHERE contentKey LIKE 'bilibili:%'")
    suspend fun deleteBilibiliFeedback()

    @Query("DELETE FROM local_content WHERE contentKey LIKE :prefix")
    suspend fun deleteContentByPrefix(prefix: String)

    @Query("DELETE FROM behavior_event WHERE contentKey LIKE :prefix")
    suspend fun deleteEventsByPrefix(prefix: String)

    @Query("DELETE FROM local_feedback WHERE contentKey LIKE :prefix")
    suspend fun deleteFeedbackByPrefix(prefix: String)
}

@Database(
    entities = [
        LocalContentEntity::class,
        BehaviorEventEntity::class,
        InterestEntity::class,
        LocalFeedbackEntity::class,
        LocalProfileEntity::class,
        LocalPreferenceEntity::class,
        LocalChatMessageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AuluneLocalDatabase : RoomDatabase() {
    abstract fun localCoreDao(): LocalCoreDao

    companion object {
        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_content ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_content ADD COLUMN authorKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_content ADD COLUMN seriesKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_content ADD COLUMN topicGroup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE behavior_event ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE behavior_event ADD COLUMN topicGroup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE behavior_event ADD COLUMN targetType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE interest ADD COLUMN lifecycle TEXT NOT NULL DEFAULT 'Active'")
                db.execSQL("ALTER TABLE interest ADD COLUMN firstSeenAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE interest ADD COLUMN lastEvidenceAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE interest SET firstSeenAt = updatedAt, lastEvidenceAt = updatedAt WHERE firstSeenAt = 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_feedback (id TEXT NOT NULL, contentKey TEXT NOT NULL, feedbackType TEXT NOT NULL, targetType TEXT NOT NULL, targetKey TEXT NOT NULL, occurredAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS local_profile (layer TEXT NOT NULL, summary TEXT NOT NULL, candidate TEXT NOT NULL DEFAULT '', evidenceCount INTEGER NOT NULL DEFAULT 0, confirmationState TEXT NOT NULL DEFAULT 'automatic', updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(layer))")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_preference (`key` TEXT NOT NULL, value TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_content ADD COLUMN aiInsight TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_content ADD COLUMN analysisSource TEXT NOT NULL DEFAULT 'rule'")
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS local_chat_message (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromUser INTEGER NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }

        fun create(context: Context): AuluneLocalDatabase = Room.databaseBuilder(
            context.applicationContext,
            AuluneLocalDatabase::class.java,
            "aulune-local.db"
        ).addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5).build()
    }
}

data class LocalInterestUi(
    val theme: String,
    val weight: Double,
    val evidenceCount: Int,
    val lifecycle: InterestLifecycle
)

data class LocalProfileUi(
    val layer: ProfileLayer,
    val summary: String,
    val candidate: String,
    val evidenceCount: Int,
    val confirmationState: String,
    val updatedAt: Long
)

data class CloudAiUiState(
    val provider: AiProvider = AiProvider.OpenAI,
    val model: String = AiProvider.OpenAI.defaultModel,
    val enabled: Boolean = false,
    val hasKey: Boolean = false,
    val isWorking: Boolean = false,
    val status: String = "未启用；信息流将使用本机规则。"
)

data class BilibiliSyncUiState(
    val isImporting: Boolean = false,
    val message: String = "B 站公开内容尚未导入。登录 Cookie 不会被读取。",
    val profile: BilibiliAccountProfile? = null
)

data class LocalFeedUiState(
    val items: List<CuratedItem> = emptyList(),
    val savedCount: Int = 0,
    val activeLens: String = "正在建立本机画像",
    val interests: List<LocalInterestUi> = emptyList(),
    val isRefreshing: Boolean = true,
    val isBilibiliImporting: Boolean = false,
    val bilibiliStatus: String = "B 站公开内容尚未导入。登录 Cookie 不会被读取。",
    val bilibiliProfile: BilibiliAccountProfile? = null,
    val feedbackCount: Int = 0,
    val intent: SessionIntent = SessionIntent.Balanced,
    val profiles: List<LocalProfileUi> = emptyList(),
    val cloudAi: CloudAiUiState = CloudAiUiState(),
    val explanation: String = "推荐、收藏、反馈和行为事件仅保存在这台手机。",
    val isPlatformSyncing: Boolean = false,
    val platformSyncStatus: Map<ContentPlatform, String> = emptyMap(),
    val platformLoginStatus: Map<ContentPlatform, String> = emptyMap(),
)

private data class CloudProfileInput(
    val interests: List<InterestEntity>,
    val intent: SessionIntent,
    val eventCount: Int
)

private data class PlatformIntegrationState(
    val cloud: CloudAiUiState,
    val syncing: Boolean,
    val syncStatus: Map<ContentPlatform, String>,
    val loginStatus: Map<ContentPlatform, String>
)

private data class RankingSnapshot(
    val content: List<LocalContentEntity>,
    val savedCount: Int,
    val interests: List<InterestEntity>,
    val events: List<BehaviorEventEntity>,
    val feedback: List<LocalFeedbackEntity>,
    val profiles: List<LocalProfileEntity> = emptyList()
)

private class LocalCoreRepository(private val dao: LocalCoreDao) {
    fun observeRankingSnapshot(): Flow<RankingSnapshot> {
        val core = combine(
            dao.observeVisibleContent(),
            dao.observeSavedCount(),
            dao.observeInterests(),
            dao.observeRecentEvents(),
            dao.observeFeedback()
        ) { content, savedCount, interests, events, feedback ->
            RankingSnapshot(content, savedCount, interests, events, feedback)
        }
        return combine(core, dao.observeProfiles()) { snapshot, profiles ->
            snapshot.copy(profiles = profiles)
        }
    }

    suspend fun ensureSeedContent() {
        val now = System.currentTimeMillis()
        if (dao.contentCount() == 0) {
            dao.upsertContent(localSeedContent(now).map(LocalAdaptiveCore::normalize))
        } else {
            dao.upsertContent(dao.allContent().map(LocalAdaptiveCore::normalize))
        }
        rebuildProfiles(force = true)
    }

    suspend fun applyInterestLifecycle() {
        val updated = LocalAdaptiveCore.applyTimeLifecycle(dao.interestsNow(), System.currentTimeMillis())
        if (updated.isNotEmpty()) dao.upsertInterests(updated)
        rebuildProfiles(force = true)
    }

    suspend fun setIntent(intent: SessionIntent) {
        dao.upsertPreference(LocalPreferenceEntity("session_intent", intent.name, System.currentTimeMillis()))
        rebuildProfiles(force = true)
    }

    suspend fun loadIntent(): SessionIntent = runCatching {
        SessionIntent.valueOf(dao.preferenceValue("session_intent").orEmpty())
    }.getOrDefault(SessionIntent.Balanced)

    suspend fun cloudProfileInput(): CloudProfileInput = CloudProfileInput(
        interests = dao.interestsNow(),
        intent = loadIntent(),
        eventCount = dao.eventCount()
    )

    suspend fun confirmProfileLayer(layer: ProfileLayer) {
        if (layer !in setOf(ProfileLayer.Values, ProfileLayer.Core)) return
        val current = dao.profile(layer.name) ?: return
        dao.upsertProfiles(listOf(LocalProfileBuilder.confirm(current, System.currentTimeMillis())))
    }

    suspend fun resetProfileLayer(layer: ProfileLayer) {
        if (layer !in setOf(ProfileLayer.Values, ProfileLayer.Core)) return
        dao.deleteProfile(layer.name)
        rebuildProfiles(force = true)
    }

    suspend fun importBilibiliPopular(): Int = importContent(BilibiliPublicConnector().fetchPopular())

    suspend fun cloudContentInput(contentKey: String): LocalContentEntity? = dao.contentByKey(contentKey)

    suspend fun applyCloudContentAnalysis(item: CuratedItem, analysis: CloudContentAnalysis) {
        val now = System.currentTimeMillis()
        val theme = analysis.theme.ifBlank { item.theme }
        val group = analysis.topicGroup.ifBlank { item.topicGroup }
        dao.applyCloudAnalysis(
            contentKey = item.id,
            theme = theme,
            topicGroup = group,
            seriesKey = analysis.seriesKey.ifBlank { item.seriesKey },
            aiInsight = analysis.insight,
            now = now
        )
        dao.reclassifyContentEvents(item.id, theme, group)
        rebuildInterestsFromRemainingEvents()
        rebuildProfiles(force = true)
    }

    suspend fun applyCloudProfileAnalysis(analysis: CloudProfileAnalysis) {
        rebuildProfiles(force = true)
        val now = System.currentTimeMillis()
        if (analysis.valuesCandidate.isNotBlank()) {
            dao.applyCloudProfileCandidate(ProfileLayer.Values.name, analysis.valuesCandidate, now)
        }
        if (analysis.coreCandidate.isNotBlank()) {
            dao.applyCloudProfileCandidate(ProfileLayer.Core.name, analysis.coreCandidate, now)
        }
    }

    suspend fun importBilibiliAccount(): BilibiliAccountReadResult {
        val cookie = BilibiliSession.currentCookie() ?: throw IllegalStateException("请先在官方 B 站页面点击“授权同步”")
        val result = BilibiliAccountConnector().readFirstPage(cookie)
        val normalized = result.contents.map(LocalAdaptiveCore::normalize)
        importContent(normalized)
        recordImportedBilibiliEvidence(normalized)
        rebuildProfiles(force = true)
        return result
    }

    suspend fun clearBilibiliLocalData() {
        dao.deleteBilibiliFeedback()
        dao.deleteBilibiliEvents()
        dao.deleteBilibiliContent()
        rebuildInterestsFromRemainingEvents()
        rebuildProfiles(force = true)
    }

    // ═══════════════════════════════════════════════════════════
    //  多平台内容导入
    // ═══════════════════════════════════════════════════════════

    /** 导入指定平台的公开内容 */
    suspend fun importPlatformPublic(platform: ContentPlatform): Int {
        val connector = PlatformConnectorFactory.getPublic(platform)
        val items = connector.fetchPublic(pageSize = 20)
        val normalized = items.map(LocalAdaptiveCore::normalize)
        return importContent(normalized)
    }

    /** 导入所有平台的公开内容 */
    suspend fun importAllPlatformsPublic(): Map<ContentPlatform, Int> {
        val results = mutableMapOf<ContentPlatform, Int>()
        ContentPlatform.entries.forEach { platform ->
            runCatching {
                results[platform] = importPlatformPublic(platform)
            }.onFailure {
                results[platform] = 0
            }
        }
        rebuildProfiles(force = true)
        return results
    }

    /** 同步指定平台的账号数据（收藏/历史等） */
    suspend fun syncPlatformAccount(platform: ContentPlatform): PlatformAccountReadResult {
        val context = app.aulune.mobile.PlatformCookieManager::class.java
        val cookie = getPlatformCookie(platform)
        if (cookie.isBlank()) throw IllegalStateException("请先登录 ${platform.label}")
        val connector = PlatformAccountConnectorFactory.get(platform)
        val result = connector.readAccount(cookie)
        if (!result.error.isNullOrBlank()) {
            throw PlatformConnectorException(platform, IllegalStateException(result.error))
        }
        val normalized = result.content.map(LocalAdaptiveCore::normalize)
        importContent(normalized)
        rebuildProfiles(force = true)
        return result
    }

    /** 同步所有已登录平台的账号数据 */
    suspend fun syncAllPlatformAccounts(): Map<ContentPlatform, PlatformAccountReadResult> {
        val results = mutableMapOf<ContentPlatform, PlatformAccountReadResult>()
        ContentPlatform.entries.forEach { platform ->
            runCatching {
                val cookie = getPlatformCookie(platform)
                if (cookie.isNotBlank()) {
                    results[platform] = syncPlatformAccount(platform)
                }
            }.onFailure {
                // 忽略单个平台失败
            }
        }
        return results
    }

    /** 获取指定平台的 Cookie */
    private fun getPlatformCookie(platform: ContentPlatform): String {
        val ctx = appContext ?: return ""
        return PlatformCookieManager.getCookie(ctx, platform)
    }

    /** 清除指定平台的本地内容 */
    suspend fun clearPlatformContent(platform: ContentPlatform) {
        val prefix = platform.contentKeyPrefix
        dao.deleteContentByPrefix(prefix)
        dao.deleteEventsByPrefix(prefix)
        dao.deleteFeedbackByPrefix(prefix)
        rebuildInterestsFromRemainingEvents()
        rebuildProfiles(force = true)
    }

    companion object {
        /** 全局 Application Context 引用，由 LocalFeedViewModel 初始化时设置 */
        @Volatile
        var appContext: android.content.Context? = null
    }

    suspend fun toggleMarked(item: CuratedItem) {
        val next = !item.marked
        val now = System.currentTimeMillis()
        dao.setMarked(item.id, next, now)
        record(item, if (next) "mark" else "unmark", if (next) 1.8 else -1.8, now)
    }

    suspend fun toggleSaved(item: CuratedItem) {
        val next = !item.saved
        val now = System.currentTimeMillis()
        dao.setSaved(item.id, next, now)
        record(item, if (next) "save" else "unsave", if (next) 1.2 else -1.2, now)
    }

    suspend fun recordOpen(item: CuratedItem) {
        record(item, "open", 0.35, System.currentTimeMillis())
    }

    suspend fun applyExplicitFeedback(item: CuratedItem, positive: Boolean) {
        val now = System.currentTimeMillis()
        val type = if (positive) FeedbackType.Positive else FeedbackType.Negative
        val targets = buildList {
            add(FeedbackTarget.Content to item.id)
            add(FeedbackTarget.Theme to item.theme)
            if (item.topicGroup.isNotBlank()) add(FeedbackTarget.TopicGroup to item.topicGroup)
            if (item.authorKey.isNotBlank()) add(FeedbackTarget.Author to item.authorKey)
            if (item.seriesKey.isNotBlank()) add(FeedbackTarget.Series to item.seriesKey)
        }
        targets.forEach { (target, key) ->
            dao.clearFeedbackTarget(item.id, target.name, key)
            dao.insertFeedback(
                LocalFeedbackEntity(
                    id = "${item.id}:${target.name}:$key",
                    contentKey = item.id,
                    feedbackType = type.name,
                    targetType = target.name,
                    targetKey = key,
                    occurredAt = now
                )
            )
        }
        if (!positive) dao.setHidden(item.id, true, now)
        record(
            item = item,
            type = if (positive) "positive_feedback" else "negative_feedback",
            delta = if (positive) 2.6 else -2.0,
            now = now,
            explicitNegative = !positive,
            targetType = FeedbackTarget.Theme.name
        )
    }

    private suspend fun rebuildInterestsFromRemainingEvents() {
        val events = dao.allEvents()
        dao.clearInterests()
        events.forEach { event ->
            val rule = eventRule(event.eventType) ?: return@forEach
            dao.upsertInterest(
                LocalAdaptiveCore.updateInterest(
                    current = dao.interest(event.theme),
                    theme = event.theme,
                    delta = rule.delta,
                    now = event.occurredAt,
                    explicitNegative = rule.explicitNegative
                )
            )
        }
    }

    private data class EventRule(val delta: Double, val explicitNegative: Boolean = false)

    private fun eventRule(type: String): EventRule? = when (type) {
        "open" -> EventRule(0.35)
        "mark" -> EventRule(1.8)
        "unmark" -> EventRule(-1.8)
        "save" -> EventRule(1.2)
        "unsave" -> EventRule(-1.2)
        "positive_feedback" -> EventRule(2.6)
        "negative_feedback" -> EventRule(-2.0, explicitNegative = true)
        "import_favorite" -> EventRule(1.4)
        "import_history" -> EventRule(0.5)
        "import_watch_later" -> EventRule(0.9)
        else -> null
    }

    private suspend fun recordImportedBilibiliEvidence(items: List<LocalContentEntity>) {
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val evidence = when {
                item.source.contains("收藏夹") -> "import_favorite" to 1.4
                item.source.contains("观看历史") -> "import_history" to 0.5
                item.source.contains("稍后再看") -> "import_watch_later" to 0.9
                else -> null
            } ?: return@forEach
            val eventId = "import:${item.contentKey}:${evidence.first}"
            if (dao.eventExists(eventId) > 0) return@forEach
            dao.insertEvent(
                BehaviorEventEntity(
                    id = eventId,
                    contentKey = item.contentKey,
                    eventType = evidence.first,
                    theme = item.theme,
                    occurredAt = now,
                    sourceKey = item.sourceKey,
                    topicGroup = item.topicGroup,
                    targetType = "import"
                )
            )
            dao.upsertInterest(
                LocalAdaptiveCore.updateInterest(
                    current = dao.interest(item.theme),
                    theme = item.theme,
                    delta = evidence.second,
                    now = now
                )
            )
        }
    }

    private suspend fun importContent(incoming: List<LocalContentEntity>): Int {
        if (incoming.isEmpty()) return 0
        val normalized = incoming.map(LocalAdaptiveCore::normalize)
        val existing = dao.contentByKeys(normalized.map { it.contentKey }).associateBy { it.contentKey }
        val preserved = normalized.map { fresh ->
            existing[fresh.contentKey]?.let { current ->
                fresh.copy(
                    marked = current.marked,
                    saved = current.saved || fresh.saved,
                    hidden = current.hidden,
                    createdAt = current.createdAt,
                    sourceKey = fresh.sourceKey.ifBlank { current.sourceKey },
                    authorKey = fresh.authorKey.ifBlank { current.authorKey },
                    seriesKey = fresh.seriesKey.ifBlank { current.seriesKey },
                    topicGroup = fresh.topicGroup.ifBlank { current.topicGroup },
                    aiInsight = fresh.aiInsight.ifBlank { current.aiInsight },
                    analysisSource = if (fresh.analysisSource == "cloud") "cloud" else current.analysisSource
                )
            } ?: fresh
        }
        dao.upsertContent(preserved)
        return preserved.size
    }

    private suspend fun record(
        item: CuratedItem,
        type: String,
        delta: Double,
        now: Long,
        explicitNegative: Boolean = false,
        targetType: String = ""
    ) {
        dao.insertEvent(
            BehaviorEventEntity(
                id = UUID.randomUUID().toString(),
                contentKey = item.id,
                eventType = type,
                theme = item.theme,
                occurredAt = now,
                sourceKey = item.sourceKey,
                topicGroup = item.topicGroup,
                targetType = targetType
            )
        )
        dao.upsertInterest(
            LocalAdaptiveCore.updateInterest(
                current = dao.interest(item.theme),
                theme = item.theme,
                delta = delta,
                now = now,
                explicitNegative = explicitNegative
            )
        )
        rebuildProfiles(force = dao.eventCount() % 5 == 0)
    }

    private suspend fun rebuildProfiles(force: Boolean) {
        if (!force && dao.eventCount() % 5 != 0) return
        val intent = loadIntent()
        val existing = dao.profilesNow().associateBy { it.layer }
        val profiles = LocalProfileBuilder.build(
            interests = dao.interestsNow(),
            eventCount = dao.eventCount(),
            intent = intent,
            existing = existing,
            now = System.currentTimeMillis()
        )
        dao.upsertProfiles(profiles)
    }
}

class LocalFeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalCoreRepository(AuluneLocalDatabase.create(application).localCoreDao())
    private val rotation = MutableStateFlow(0)
    private val bilibiliSync = MutableStateFlow(BilibiliSyncUiState())
    private val sessionIntent = MutableStateFlow(SessionIntent.Balanced)
    private val secureCloudSettings = SecureCloudAiSettings(application)
    private val cloudService = CloudAiSemanticService()
    private val cloudConfig = MutableStateFlow(secureCloudSettings.load())
    private val cloudUi = MutableStateFlow(cloudUiState(secureCloudSettings.load()))
    private val _platformSyncStatus = MutableStateFlow<Map<ContentPlatform, String>>(emptyMap())
    val platformSyncStatus: StateFlow<Map<ContentPlatform, String>> = _platformSyncStatus
    private val _isPlatformSyncing = MutableStateFlow(false)
    val isPlatformSyncing: StateFlow<Boolean> = _isPlatformSyncing
    private val _platformLoginStatus = MutableStateFlow<Map<ContentPlatform, String>>(emptyMap())
    private val integrationState = combine(cloudUi, _isPlatformSyncing, _platformSyncStatus, _platformLoginStatus) { cloud, syncing, syncStatus, loginStatus ->
        PlatformIntegrationState(cloud, syncing, syncStatus, loginStatus)
    }

    val uiState: StateFlow<LocalFeedUiState> = combine(
        repository.observeRankingSnapshot(),
        rotation,
        bilibiliSync,
        sessionIntent,
        integrationState
    ) { snapshot, rotationIndex, sync, intent, integration ->
        val cloud = integration.cloud
        val platformSyncing = integration.syncing
        val platformStatus = integration.syncStatus
        val platformLoginStatus = integration.loginStatus
        val ordered = snapshot.content
            .map(LocalAdaptiveCore::normalize)
            .filterNot { LocalAdaptiveCore.shouldExclude(it, snapshot.feedback) }
            .map { item -> item to LocalAdaptiveCore.score(item, snapshot.interests, snapshot.events, snapshot.feedback, rotationIndex, intent) }
            .sortedByDescending { it.second }
            .map { (item, _) -> item.toCuratedItem(snapshot.interests, snapshot.feedback, snapshot.events) }
        val top = snapshot.interests.maxByOrNull { it.weight }
        LocalFeedUiState(
            items = ordered,
            savedCount = snapshot.savedCount,
            activeLens = top?.theme ?: "清晰思考",
            interests = snapshot.interests.filter { it.lifecycle.toLifecycle() != InterestLifecycle.Archived }
                .map { LocalInterestUi(it.theme, it.weight, it.evidenceCount, it.lifecycle.toLifecycle()) },
            isRefreshing = sync.isImporting,
            isBilibiliImporting = sync.isImporting,
            bilibiliStatus = sync.message,
            bilibiliProfile = sync.profile,
            feedbackCount = snapshot.feedback.size,
            intent = intent,
            profiles = snapshot.profiles.map { it.toLocalProfileUi() },
            cloudAi = cloud,
            explanation = when {
                top == null -> "从打开、喜欢、保存或不感兴趣开始，本机画像会在这台手机上逐步形成。"
                top.lifecycle.toLifecycle() == InterestLifecycle.Trial -> "「${top.theme}」正在观察期，已有 ${top.evidenceCount} 条本机证据；重复行为后才会进入活跃推荐。"
                top.lifecycle.toLifecycle() == InterestLifecycle.Decaying -> "「${top.theme}」近期证据较少，已自动降温；新的正向行为会重新激活它。"
                else -> "本机画像目前更关注「${top.theme}」，已积累 ${top.evidenceCount} 条行为证据。"
            },
            isPlatformSyncing = platformSyncing,
            platformSyncStatus = platformStatus,
            platformLoginStatus = platformLoginStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalFeedUiState())

    init {
        LocalCoreRepository.appContext = getApplication()
        viewModelScope.launch {
            sessionIntent.value = repository.loadIntent()
            repository.ensureSeedContent()
            repository.applyInterestLifecycle()
            refreshPlatformStatuses()
        }
    }

    fun refreshPlatformStatuses() {
        val context = getApplication<Application>()
        _platformLoginStatus.value = ContentPlatform.entries.associateWith { platform ->
            val authorization = if (PlatformCookieManager.isLoggedIn(context, platform)) "已授权，可读取账户数据" else "未登录；可尝试公开导入"
            "$authorization · ${PlatformCapabilityMatrix.summary(platform)}"
        }
    }

    fun rotateFeed() { rotation.value += 1 }

    fun saveCloudAiConfig(
        provider: AiProvider,
        apiKey: String,
        model: String,
        baseUrl: String = "",
        protocol: ProviderProtocol? = null,
        enable: Boolean
    ) {
        val previous = cloudConfig.value
        val updated = CloudAiConfig(
            provider = provider,
            apiKey = apiKey.ifBlank { previous.apiKey },
            model = model.ifBlank { provider.defaultModel },
            baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl },
            protocol = protocol ?: provider.protocol,
            enabled = enable
        )
        secureCloudSettings.save(updated)
        cloudConfig.value = updated
        cloudUi.value = cloudUiState(updated)
    }

    fun disableCloudAi() {
        val updated = cloudConfig.value.copy(enabled = false)
        secureCloudSettings.save(updated)
        cloudConfig.value = updated
        cloudUi.value = cloudUiState(updated)
    }

    fun clearCloudAiKey() {
        secureCloudSettings.clear()
        cloudConfig.value = CloudAiConfig()
        cloudUi.value = cloudUiState(CloudAiConfig())
    }

    fun analyzeItemWithCloudAi(item: CuratedItem) {
        val config = cloudConfig.value
        if (!config.isUsable || cloudUi.value.isWorking) {
            cloudUi.value = cloudUiState(config, status = "未启用云端 AI；当前内容继续使用本机规则。")
            return
        }
        viewModelScope.launch {
            val content = repository.cloudContentInput(item.id)
                ?: run {
                    cloudUi.value = cloudUiState(config, status = "内容已不存在，本机规则保持不变。")
                    return@launch
                }
            cloudUi.value = cloudUiState(config, working = true, status = "${config.provider.displayName} 正在分析内容元数据…")
            cloudService.analyzeContent(config, content)
                .onSuccess { analysis ->
                    repository.applyCloudContentAnalysis(item, analysis)
                    cloudUi.value = cloudUiState(config, status = "云端内容分析已保存到本机；后续排序仍由本机执行。")
                }
                .onFailure { error ->
                    cloudUi.value = cloudUiState(config, status = "云端分析失败，已保留本机规则：${error.message ?: "请检查 Key、模型和网络。"}")
                }
        }
    }

    fun buildCloudProfileCandidate() {
        val config = cloudConfig.value
        if (!config.isUsable || cloudUi.value.isWorking) {
            cloudUi.value = cloudUiState(config, status = "未启用云端 AI；长期画像仍使用本机候选。")
            return
        }
        viewModelScope.launch {
            val input = repository.cloudProfileInput()
            cloudUi.value = cloudUiState(config, working = true, status = "${config.provider.displayName} 正在根据聚合兴趣生成候选…")
            cloudService.buildProfileCandidate(config, input.interests, input.intent, input.eventCount)
                .onSuccess { analysis ->
                    repository.applyCloudProfileAnalysis(analysis)
                    cloudUi.value = cloudUiState(config, status = "云端候选已写入待确认区域；不会自动成为长期画像。")
                }
                .onFailure { error ->
                    cloudUi.value = cloudUiState(config, status = "云端候选生成失败，已保留本机候选：${error.message ?: "请检查 Key、模型和网络。"}")
                }
        }
    }

    fun setSessionIntent(intent: SessionIntent) {
        sessionIntent.value = intent
        viewModelScope.launch { repository.setIntent(intent) }
    }

    fun confirmProfileLayer(layer: ProfileLayer) {
        viewModelScope.launch { repository.confirmProfileLayer(layer) }
    }

    fun resetProfileLayer(layer: ProfileLayer) {
        viewModelScope.launch { repository.resetProfileLayer(layer) }
    }

    fun importBilibiliPublicContent() = runBilibiliSync("正在导入 B 站公开热门内容…") {
        val count = repository.importBilibiliPopular()
        "已将 $count 条 B 站公开内容保存到本机信息流。"
    }

    fun syncBilibiliAccount() = runBilibiliSync("正在读取已授权的 B 站账户数据…") {
        val result = repository.importBilibiliAccount()
        bilibiliSync.value = BilibiliSyncUiState(
            message = "已读取 ${result.profile.name} 的账户数据，并将 ${result.contents.size} 条收藏/历史内容保存到本机。",
            profile = result.profile
        )
        return@runBilibiliSync null
    }

    fun clearBilibiliAuthorization() {
        BilibiliSession.clear()
        bilibiliSync.value = BilibiliSyncUiState(message = "本机账户读取授权已清除；已同步内容仍保留在本地。")
    }

    fun clearBilibiliLocalData() {
        viewModelScope.launch {
            repository.clearBilibiliLocalData()
            BilibiliSession.clear()
            bilibiliSync.value = BilibiliSyncUiState(message = "已删除本机 B 站收藏、历史、反馈与行为事件，并清除读取授权。")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  多平台同步
    // ═══════════════════════════════════════════════════════════

    /** 导入指定平台的公开内容 */
    fun importPlatformPublic(platform: ContentPlatform) {
        if (_isPlatformSyncing.value) return
        viewModelScope.launch {
            _isPlatformSyncing.value = true
            _platformSyncStatus.value = _platformSyncStatus.value + (platform to "正在导入 ${platform.label} 公开内容…")
            val attempt = PlatformRetryPolicy.run { repository.importPlatformPublic(platform) }
            _platformSyncStatus.value = _platformSyncStatus.value + (platform to when {
                attempt.value == null -> "公开导入失败（${attempt.attempts} 次）：${attempt.failure?.display() ?: "未知错误"}"
                attempt.value > 0 -> "已导入 ${attempt.value} 条 ${platform.label} 内容${if (attempt.attempts > 1) "（重试 ${attempt.attempts - 1} 次）" else ""}"
                else -> "未取得公开内容；可能受限流、接口变化或网络影响"
            })
            _isPlatformSyncing.value = false
            refreshPlatformStatuses()
        }
    }

    /** 导入所有平台的公开内容 */
    fun importAllPlatformsPublic() {
        if (_isPlatformSyncing.value) return
        viewModelScope.launch {
            _isPlatformSyncing.value = true
            ContentPlatform.entries.forEach { platform ->
                _platformSyncStatus.value = _platformSyncStatus.value + (platform to "正在导入 ${platform.label}…")
                val attempt = PlatformRetryPolicy.run { repository.importPlatformPublic(platform) }
                _platformSyncStatus.value = _platformSyncStatus.value + (platform to when {
                    attempt.value == null -> "失败（${attempt.attempts} 次）：${attempt.failure?.display() ?: "未知错误"}"
                    attempt.value > 0 -> "已导入 ${attempt.value} 条${if (attempt.attempts > 1) "（重试 ${attempt.attempts - 1} 次）" else ""}"
                    else -> "未取得公开内容"
                })
            }
            _isPlatformSyncing.value = false
            refreshPlatformStatuses()
        }
    }

    /** 同步指定平台的账号数据 */
    fun syncPlatformAccount(platform: ContentPlatform) {
        if (_isPlatformSyncing.value) return
        viewModelScope.launch {
            _isPlatformSyncing.value = true
            _platformSyncStatus.value = _platformSyncStatus.value + (platform to "正在同步 ${platform.label} 账号数据…")
            val attempt = PlatformRetryPolicy.run { repository.syncPlatformAccount(platform) }
            _platformSyncStatus.value = _platformSyncStatus.value + (platform to when (val result = attempt.value) {
                null -> "账户读取失败（${attempt.attempts} 次）：${attempt.failure?.display() ?: "未知错误"}"
                else -> {
                    val name = result.info.nickname.ifBlank { platform.label }
                    if (result.error.isNullOrBlank()) "已同步 $name 的 ${result.content.size} 条内容${if (attempt.attempts > 1) "（重试 ${attempt.attempts - 1} 次）" else ""}"
                    else "账户读取受限：${PlatformFailureClassifier.classify(IllegalStateException(result.error)).display()}"
                }
            })
            _isPlatformSyncing.value = false
            refreshPlatformStatuses()
        }
    }

    /** 同步所有已登录平台的账号数据 */
    fun syncAllPlatformAccounts() {
        if (_isPlatformSyncing.value) return
        viewModelScope.launch {
            _isPlatformSyncing.value = true
            ContentPlatform.entries.forEach { platform ->
                val cookie = PlatformCookieManager.getCookie(getApplication(), platform)
                if (cookie.isBlank()) {
                    _platformSyncStatus.value = _platformSyncStatus.value + (platform to "未登录，跳过")
                    return@forEach
                }
                _platformSyncStatus.value = _platformSyncStatus.value + (platform to "正在同步…")
                val attempt = PlatformRetryPolicy.run { repository.syncPlatformAccount(platform) }
                _platformSyncStatus.value = _platformSyncStatus.value + (platform to when (val result = attempt.value) {
                    null -> "失败（${attempt.attempts} 次）：${attempt.failure?.display() ?: "未知错误"}"
                    else -> {
                        val name = result.info.nickname.ifBlank { platform.label }
                        if (result.error.isNullOrBlank()) "已同步 $name 的 ${result.content.size} 条"
                        else "账户读取受限：${PlatformFailureClassifier.classify(IllegalStateException(result.error)).display()}"
                    }
                })
            }
            _isPlatformSyncing.value = false
            refreshPlatformStatuses()
        }
    }

    /** 清除指定平台的本地内容 */
    fun clearPlatformLocalData(platform: ContentPlatform) {
        viewModelScope.launch {
            repository.clearPlatformContent(platform)
            _platformSyncStatus.value = _platformSyncStatus.value + (platform to "已清除本地内容")
        }
    }

    fun toggleMarked(item: CuratedItem) { viewModelScope.launch { repository.toggleMarked(item) } }
    fun toggleSaved(item: CuratedItem) { viewModelScope.launch { repository.toggleSaved(item) } }
    fun recordOpen(item: CuratedItem) { viewModelScope.launch { repository.recordOpen(item) } }
    fun setPositiveFeedback(item: CuratedItem) { viewModelScope.launch { repository.applyExplicitFeedback(item, positive = true) } }
    fun setNegativeFeedback(item: CuratedItem) { viewModelScope.launch { repository.applyExplicitFeedback(item, positive = false) } }

    private fun cloudUiState(config: CloudAiConfig, working: Boolean = false, status: String? = null) = CloudAiUiState(
        provider = config.provider,
        model = config.effectiveModel,
        enabled = config.enabled,
        hasKey = config.apiKey.isNotBlank(),
        isWorking = working,
        status = status ?: when {
            config.isUsable -> "已启用 ${config.provider.displayName}；内容分析与长期画像候选需由你手动触发。"
            config.enabled -> "已选择云端 AI，但尚未保存可用 API Key；信息流将使用本机规则。"
            else -> "未启用；信息流将使用本机规则。"
        }
    )

    private fun runBilibiliSync(startMessage: String, task: suspend () -> String?) {
        if (bilibiliSync.value.isImporting) return
        viewModelScope.launch {
            bilibiliSync.value = BilibiliSyncUiState(isImporting = true, message = startMessage, profile = bilibiliSync.value.profile)
            runCatching { task() }
                .onSuccess { message ->
                    if (message != null) bilibiliSync.value = BilibiliSyncUiState(message = message, profile = bilibiliSync.value.profile)
                }
                .onFailure { error ->
                    bilibiliSync.value = BilibiliSyncUiState(message = "B 站同步失败：${error.message ?: "请稍后重试"}", profile = bilibiliSync.value.profile)
                }
        }
    }
}

private fun LocalContentEntity.toCuratedItem(
    interests: List<InterestEntity>,
    feedback: List<LocalFeedbackEntity>,
    recentEvents: List<BehaviorEventEntity>
): CuratedItem {
    val interest = interests.firstOrNull { it.theme == theme }
    return CuratedItem(
        id = contentKey,
        channel = channel.toSourceChannel(),
        title = title,
        source = source,
        readTime = readTime,
        insight = aiInsight.ifBlank { LocalAdaptiveCore.insightFor(this, interest, feedback, recentEvents) },
        theme = theme,
        url = url,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
        marked = marked,
        saved = saved,
        lifecycle = interest?.lifecycle.toLifecycle(),
        sourceKey = sourceKey,
        authorKey = authorKey,
        seriesKey = seriesKey,
        topicGroup = topicGroup
    )
}

private fun LocalProfileEntity.toLocalProfileUi(): LocalProfileUi = LocalProfileUi(
    layer = runCatching { ProfileLayer.valueOf(layer) }.getOrDefault(ProfileLayer.Surface),
    summary = summary,
    candidate = candidate,
    evidenceCount = evidenceCount,
    confirmationState = confirmationState,
    updatedAt = updatedAt
)

private fun String.toSourceChannel(): SourceChannel = when (this) {
    SourceChannel.Insight.name -> SourceChannel.Insight
    SourceChannel.Brief.name -> SourceChannel.Brief
    SourceChannel.Video.name -> SourceChannel.Video
    SourceChannel.Notes.name -> SourceChannel.Notes
    else -> SourceChannel.Signal
}

private fun localSeedContent(now: Long): List<LocalContentEntity> = listOf(
    LocalContentEntity("local:insight-1", "思维备忘", SourceChannel.Insight.name, "把复杂问题缩小：先找到真正需要被解释的那个变量", "阅读 9 分钟", "它不提供更多方法，而是帮你重建判断问题的重要顺序。", "思考 · 结构", "https://example.com", 0xFF28224D, 0xFF8067E7, createdAt = now, updatedAt = now),
    LocalContentEntity("local:notes-1", "简约实践", SourceChannel.Notes.name, "真正可持续的个人系统，总有一个足够轻的最小入口", "阅读 6 分钟", "它关注的是降低维护成本，而不是堆叠更多工具。", "创造 · 系统", "https://example.com", 0xFF0E5A57, 0xFF4CC6B7, createdAt = now, updatedAt = now),
    LocalContentEntity("local:video-1", "慢变量", SourceChannel.Video.name, "为什么小而稳定的迭代，比宏大的计划更接近长期成果", "12:05", "这条内容提供了可验证的节奏，而不是激励话术。", "长期 · 迭代", "https://example.com", 0xFF5A263D, 0xFFE56884, createdAt = now, updatedAt = now),
    LocalContentEntity("local:brief-1", "周末观察", SourceChannel.Brief.name, "从观点到行动：如何把一次阅读变成可以复用的判断", "阅读 7 分钟", "这篇给出的复盘方式足够轻，值得试一次。", "阅读 · 决策", "https://example.com", 0xFF173D5A, 0xFF5EAED8, createdAt = now, updatedAt = now),
    LocalContentEntity("local:signal-1", "夜航者", SourceChannel.Signal.name, "给注意力留白，并不是放弃效率，而是在保护判断质量", "阅读 5 分钟", "当输入开始变得嘈杂时，它能帮助你重新确认哪些信息不必马上处理。", "生活 · 注意力", "https://example.com", 0xFF5B1F55, 0xFFA64D96, createdAt = now, updatedAt = now)
)
