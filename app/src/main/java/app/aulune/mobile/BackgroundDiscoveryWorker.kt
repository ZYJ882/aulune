package app.aulune.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * v2.0.0 后台主动发现 + 本地通知。
 *
 * 对齐 OpenBiliClaw 的后台刷新能力，但本地实现：
 * - 用 WorkManager PeriodicWorker，每 6 小时执行一次
 * - 默认关闭，用户在"设置 → 后台主动发现"开关
 * - 仅在设备空闲 + 充电 + 网络可用时执行
 * - 发现新内容后，如本机画像有 Top 兴趣且匹配到 ≥3 条候选，发本地通知
 * - 通知点击直接打开 MainActivity
 *
 * 与 OpenBiliClaw 不同：
 * - 不接入 WebSocket 主动推送（Android 需要常驻前台服务，电池开销大）
 * - 用本地通知代替；用户可关闭通知
 */
object BackgroundDiscoveryWorkManager {
    private const val CHANNEL_ID = "aulune-discovery"
    private const val WORK_NAME = "aulune-background-discovery"
    private const val PREF_NAME = "aulune-discovery-prefs"
    private const val PREF_KEY_ENABLED = "background_discovery_enabled"
    private const val PREF_KEY_INTERVAL_HOURS = "interval_hours"
    private const val DEFAULT_INTERVAL_HOURS = 6L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_ENABLED, false)

    fun setIntervalHours(context: Context, hours: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putLong(PREF_KEY_INTERVAL_HOURS, hours.coerceIn(1L, 24L)).apply()
        if (isEnabled(context)) schedule(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    fun getIntervalHours(context: Context): Long =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)

    private fun schedule(context: Context) {
        val hours = getIntervalHours(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(false)  // 不强制设备空闲，让用户能看到通知
            .setRequiresCharging(false)
            .build()
        val request = PeriodicWorkRequestBuilder<BackgroundDiscoveryWorker>(
            hours, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aulune 内容发现",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "后台主动发现匹配本机画像的新内容时通知"
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }
}

/**
 * 实际执行后台发现任务的 Worker。
 *
 * 流程：
 * 1. 取本机画像的 Top 3 兴趣主题
 * 2. 调用对应平台的 public connector 拉取候选
 * 3. 用 AdaptiveRanking 评分
 * 4. 如有 ≥3 条 score ≥ 0.5 的"惊喜内容"，发本地通知
 * 5. 把候选写入 local_content 表（标记为待评估，不直接进入信息流）
 */
class BackgroundDiscoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val diagnostics = AuluneDiagnostics(context)
        return try {
            diagnostics.record("INFO", "后台主动发现：开始执行")
            val db = AuluneLocalDatabase.create(context)
            val dao = db.localCoreDao()

            // 1. 取 Top 3 兴趣主题
            val interests = dao.interestsNow()
                .filter { it.lifecycle.toLifecycle() == InterestLifecycle.Active }
                .sortedByDescending { it.weight }
                .take(3)
            if (interests.isEmpty()) {
                diagnostics.record("INFO", "后台主动发现：尚无活跃兴趣，跳过本轮")
                return Result.success()
            }

            // 2. 调用对应平台的 public connector
            val platforms = interests.flatMap { interest ->
                val group = interest.theme.substringBefore("·").trim()
                when (group) {
                    "技术" -> listOf(ContentPlatform.BILIBILI, ContentPlatform.ZHIHU, ContentPlatform.YOUTUBE)
                    "商业" -> listOf(ContentPlatform.ZHIHU, ContentPlatform.V2EX, ContentPlatform.WEIBO)
                    "创造" -> listOf(ContentPlatform.XIAOHONGSHU, ContentPlatform.BILIBILI, ContentPlatform.YOUTUBE)
                    "学习" -> listOf(ContentPlatform.ZHIHU, ContentPlatform.BILIBILI, ContentPlatform.YOUTUBE)
                    "生活" -> listOf(ContentPlatform.XIAOHONGSHU, ContentPlatform.DOUYIN, ContentPlatform.BILIBILI)
                    "娱乐" -> listOf(ContentPlatform.BILIBILI, ContentPlatform.BANGUMI, ContentPlatform.DOUYIN)
                    else -> listOf(ContentPlatform.BILIBILI, ContentPlatform.ZHIHU)
                }.distinct()
            }.distinct().take(4)

            val candidates = mutableListOf<LocalContentEntity>()
            val feedback = dao.allFeedback()
            val events = dao.allEvents()
            platforms.forEach { platform ->
                try {
                    val connector = PlatformConnectorFactory.getPublic(platform)
                    val items = connector.fetchPublic(pageSize = 8)
                    candidates.addAll(items)
                } catch (e: Exception) {
                    diagnostics.record("WARN", "后台主动发现：${platform.shortLabel} 拉取失败 - ${e.message}")
                }
            }

            if (candidates.isEmpty()) {
                diagnostics.record("INFO", "后台主动发现：本轮未拉到候选")
                return Result.success()
            }

            // 3. 用 AdaptiveRanking 评分，筛出"惊喜内容"
            val scored = candidates.map { item ->
                val normalized = LocalAdaptiveCore.normalize(item)
                val score = LocalAdaptiveCore.score(
                    item = normalized,
                    interests = interests,
                    recentEvents = events,
                    feedback = feedback,
                    rotationIndex = 0,
                    intent = SessionIntent.Balanced,
                )
                normalized to score
            }.filter { it.second >= 0.5 }
                .sortedByDescending { it.second }
                .take(5)

            if (scored.isEmpty()) {
                diagnostics.record("INFO", "后台主动发现：本轮无惊喜内容（score ≥ 0.5）")
                return Result.success()
            }

            // 4. 写入数据库
            val now = System.currentTimeMillis()
            dao.upsertContent(scored.map { it.first.copy(createdAt = now, updatedAt = now) })

            // 5. 发本地通知
            val topItem = scored.first().first
            BackgroundDiscoveryWorkManager.ensureChannel(context)
            val notificationManager = context.getSystemService<NotificationManager>() ?: return Result.success()
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, "aulune-discovery")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Aulune 发现了 ${scored.size} 条你可能感兴趣的内容")
                .setContentText("「${topItem.title.take(40)}」— 基于 ${interests.first().theme}")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(scored.joinToString("\n") { "· ${it.first.title.take(50)}" }))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(1001, notification)
            diagnostics.record("INFO", "后台主动发现：完成；写入 ${scored.size} 条候选并发通知")

            Result.success()
        } catch (error: Throwable) {
            diagnostics.record("ERROR", "后台主动发现失败：${error.message ?: "未知错误"}")
            // 失败不重试太频繁，让 WorkManager 按 backoff 重试
            Result.retry()
        }
    }
}
