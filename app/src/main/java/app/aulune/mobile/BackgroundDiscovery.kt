package app.aulune.mobile

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DiscoveryTaskKind(val label: String) {
    Manual("手动探测")
}

enum class DiscoveryTaskStatus(val label: String) {
    Queued("等待执行"),
    Running("正在执行"),
    Completed("已完成"),
    Partial("部分完成"),
    Failed("执行失败"),
    Cancelled("已取消")
}

enum class SourceAvailabilityState(val label: String) {
    Available("可用"),
    Degraded("不稳定"),
    Unavailable("不可用")
}

@Entity(
    tableName = "local_discovery_task",
    indices = [Index(value = ["createdAt"]), Index(value = ["status"])]
)
data class DiscoveryTaskEntity(
    @PrimaryKey val taskId: String,
    val kind: String,
    val status: String,
    val createdAt: Long,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val discoveredCount: Int = 0,
    val checkedSources: Int = 0,
    val availableSources: Int = 0,
    val detail: String = ""
)

@Entity(tableName = "source_availability")
data class SourceAvailabilityEntity(
    @PrimaryKey val platform: String,
    val state: String,
    val detail: String,
    val checkedAt: Long,
    val discoveredCount: Int = 0,
    val attempts: Int = 0
)

data class SourceProbeOutcome(
    val platform: ContentPlatform,
    val discoveredCount: Int,
    val attempts: Int,
    val failure: PlatformFailure? = null
) {
    val state: SourceAvailabilityState
        get() = when {
            failure == null -> SourceAvailabilityState.Available
            failure.kind.retryable -> SourceAvailabilityState.Degraded
            else -> SourceAvailabilityState.Unavailable
        }

    fun detail(): String = when {
        failure != null -> failure.display()
        discoveredCount > 0 -> "已发现并保存 $discoveredCount 条公开内容。"
        else -> "连接正常，本轮没有可保存的新公开内容。"
    }

    fun toEntity(now: Long): SourceAvailabilityEntity = SourceAvailabilityEntity(
        platform = platform.name,
        state = state.name,
        detail = detail(),
        checkedAt = now,
        discoveredCount = discoveredCount,
        attempts = attempts
    )
}

data class DiscoveryRunResult(val outcomes: List<SourceProbeOutcome>) {
    val discoveredCount: Int get() = outcomes.sumOf { it.discoveredCount }
    val availableSources: Int get() = outcomes.count { it.state == SourceAvailabilityState.Available }
}

object DiscoveryTaskClassifier {
    fun status(result: DiscoveryRunResult): DiscoveryTaskStatus = when {
        result.outcomes.isEmpty() -> DiscoveryTaskStatus.Failed
        result.outcomes.all { it.failure != null } -> DiscoveryTaskStatus.Failed
        result.outcomes.any { it.failure != null } -> DiscoveryTaskStatus.Partial
        else -> DiscoveryTaskStatus.Completed
    }

    fun detail(result: DiscoveryRunResult): String = when (status(result)) {
        DiscoveryTaskStatus.Completed -> "已探测 ${result.outcomes.size} 个来源，发现 ${result.discoveredCount} 条公开内容。"
        DiscoveryTaskStatus.Partial -> "已探测 ${result.outcomes.size} 个来源，发现 ${result.discoveredCount} 条内容；部分来源暂不可用。"
        DiscoveryTaskStatus.Failed -> "本轮来源探测未成功；请查看各来源状态后稍后重试。"
        else -> ""
    }
}

data class SourceAvailabilityUi(
    val platform: ContentPlatform,
    val state: SourceAvailabilityState,
    val detail: String,
    val checkedAt: Long,
    val discoveredCount: Int
)

data class DiscoveryTaskUi(
    val kind: DiscoveryTaskKind,
    val status: DiscoveryTaskStatus,
    val createdAt: Long,
    val finishedAt: Long,
    val discoveredCount: Int,
    val detail: String
)

data class BackgroundDiscoveryUiState(
    val isRunning: Boolean = false,
    val notice: String = "仅在你点击“立即探测公开来源”后执行；不会在后台自动联网。",
    val sources: List<SourceAvailabilityUi> = emptyList(),
    val recentTasks: List<DiscoveryTaskUi> = emptyList()
)

internal fun SourceAvailabilityEntity.toUi(): SourceAvailabilityUi = SourceAvailabilityUi(
    platform = runCatching { ContentPlatform.valueOf(platform) }.getOrDefault(ContentPlatform.BILIBILI),
    state = runCatching { SourceAvailabilityState.valueOf(state) }.getOrDefault(SourceAvailabilityState.Unavailable),
    detail = detail,
    checkedAt = checkedAt,
    discoveredCount = discoveredCount
)

internal fun DiscoveryTaskEntity.toUi(): DiscoveryTaskUi = DiscoveryTaskUi(
    kind = runCatching { DiscoveryTaskKind.valueOf(kind) }.getOrDefault(DiscoveryTaskKind.Manual),
    status = runCatching { DiscoveryTaskStatus.valueOf(status) }.getOrDefault(DiscoveryTaskStatus.Failed),
    createdAt = createdAt,
    finishedAt = finishedAt,
    discoveredCount = discoveredCount,
    detail = detail
)
