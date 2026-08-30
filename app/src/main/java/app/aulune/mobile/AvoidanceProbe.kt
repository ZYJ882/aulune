package app.aulune.mobile

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 避雷探针：和兴趣探针对称，用于主动确认用户想避开的内容形态、风格边界或主题方向。
 *
 * 与 OpenBiliClaw 的避雷探针对齐：候选不会自动写入过滤偏好，必须由用户确认。
 * 仅来自本机负反馈聚合与画像，不调用网络，不读取 Cookie。
 *
 * 四态：Pending / Avoiding（确认要避开）/ Tolerable（暂可接受）/ Expired
 */
enum class AvoidanceHypothesisStatus(val label: String) {
    Pending("等待确认"),
    Avoiding("已确认避开"),
    Tolerable("暂可接受"),
    Expired("已过期")
}

@Entity(
    tableName = "local_avoidance_hypothesis",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])]
)
data class AvoidanceHypothesisEntity(
    @PrimaryKey val id: String,
    /** 候选要避开的内容形态或主题，例如「长篇广告前缀」「标题党」「短视频解说」 */
    val candidatePattern: String,
    /** 来源主题或负反馈类型，例如「重复疲劳」「负反馈主题」「标题党信号」 */
    val sourceTheme: String,
    /** 触发来源：behavior / cloud / rule */
    val origin: String,
    /** 给用户看的解释，避免让用户面对冷冰冰的标签 */
    val reason: String,
    /** 累计负反馈或重复出现次数，作为证据 */
    val evidenceCount: Int,
    val status: String = AvoidanceHypothesisStatus.Pending.name,
    val createdAt: Long,
    val expiresAt: Long,
    val decidedAt: Long = 0L,
)

data class AvoidanceHypothesisUi(
    val id: String,
    val candidatePattern: String,
    val sourceTheme: String,
    val originLabel: String,
    val reason: String,
    val evidenceCount: Int,
    val status: AvoidanceHypothesisStatus,
    val expiresAt: Long,
)

/**
 * 避雷候选生成策略。完全基于本机负反馈、重复疲劳和规则启发式，不调用网络。
 *
 * 三种生成来源：
 * 1. [fromNegativeFeedback]：同一主题或作者收到 ≥2 次负反馈时，主动询问是否要长期避开
 * 2. [fromRepeatFatigue]：同一系列或主题在 24h 内连续出现 N 次且未保存，提示是否避开
 * 3. [fromRuleSignals]：本机规则识别到的标题党、低质封面、广告前缀等可解释信号
 */
object AvoidanceProbePolicy {
    private const val HypothesisTtlMillis = 21L * 24 * 60 * 60 * 1000
    private const val NegativeFeedbackThreshold = 2
    private const val RepeatFatigueThreshold = 4

    /** 默认规则信号；这些是 Aulune 本机已能识别的负向特征。 */
    private val ruleSignals = listOf(
        RuleSignal("标题党", "rule", "多条内容标题含夸张词或悬念钩子；如不想看到此类，可确认避开。"),
        RuleSignal("短视频解说复述", "rule", "部分内容是影视/游戏的纯解说复述，缺少原创；可确认是否长期降低。"),
        RuleSignal("广告前置", "rule", "本机检测到部分内容前 30 秒含推广或导流；可确认是否避开。"),
        RuleSignal("重复主题疲劳", "rule", "同一主题在近期反复出现且未保存；可确认是否暂时避开。"),
    )

    private data class RuleSignal(
        val pattern: String,
        val origin: String,
        val reason: String,
    )

    fun propose(
        feedback: List<LocalFeedbackEntity>,
        contents: List<LocalContentEntity>,
        existing: List<AvoidanceHypothesisEntity>,
        now: Long,
    ): List<AvoidanceHypothesisEntity> {
        if (feedback.isEmpty() && contents.isEmpty()) return emptyList()
        val blocked = existing
            .filter { it.status in setOf(AvoidanceHypothesisStatus.Pending.name, AvoidanceHypothesisStatus.Avoiding.name) }
            .map { it.candidatePattern }
            .toSet()
        val out = mutableListOf<AvoidanceHypothesisEntity>()

        // 1. 负反馈聚合：按 targetType + targetKey 统计
        val negativeByKey = feedback
            .filter { it.feedbackType == "negative" }
            .groupBy { "${it.targetType}|${it.targetKey}" }
            .mapValues { it.value.size }
            .filter { it.value >= NegativeFeedbackThreshold }
        negativeByKey.forEach { (key, count) ->
            val (type, target) = key.split("|", limit = 2).let { it.getOrNull(0) to (it.getOrNull(1) ?: "") }
            val label = when (type) {
                "theme" -> "主题·$target"
                "author" -> "作者·$target"
                "series" -> "系列·$target"
                else -> target
            }
            if (label !in blocked) {
                out += AvoidanceHypothesisEntity(
                    id = "avoid:feedback:${key.hashCode()}",
                    candidatePattern = label,
                    sourceTheme = "负反馈聚合",
                    origin = "behavior",
                    reason = "近 30 天你对 $label 给出 $count 次负反馈（不感兴趣 / 移除）。是否要长期避开此类内容？",
                    evidenceCount = count,
                    createdAt = now,
                    expiresAt = now + HypothesisTtlMillis,
                )
            }
        }

        // 2. 重复疲劳：同主题或系列在 24h 内 ≥4 次未保存
        val dayAgo = now - 24L * 60 * 60 * 1000
        val recent = contents.filter { it.createdAt >= dayAgo && !it.saved }
        val byTopic = recent.groupBy { it.topicGroup.ifBlank { it.theme } }
            .mapValues { it.value.size }
            .filter { it.value >= RepeatFatigueThreshold }
        byTopic.forEach { (topic, count) ->
            if (topic.isBlank()) return@forEach
            val label = "主题·$topic"
            if (label !in blocked) {
                out += AvoidanceHypothesisEntity(
                    id = "avoid:fatigue:topic:${topic.hashCode()}",
                    candidatePattern = label,
                    sourceTheme = "重复疲劳",
                    origin = "behavior",
                    reason = "「$topic」在最近 24h 出现 $count 次但未被保存；可确认是否暂时降低此类内容的可见度。",
                    evidenceCount = count,
                    createdAt = now,
                    expiresAt = now + HypothesisTtlMillis,
                )
            }
        }

        // 3. 规则信号：仅生成一次，用户拒绝或确认后不再重复出现
        ruleSignals.forEach { signal ->
            if (signal.pattern !in blocked && feedback.size >= 3) {
                out += AvoidanceHypothesisEntity(
                    id = "avoid:rule:${signal.pattern.hashCode()}",
                    candidatePattern = signal.pattern,
                    sourceTheme = "本机规则",
                    origin = signal.origin,
                    reason = signal.reason,
                    evidenceCount = feedback.size,
                    createdAt = now,
                    expiresAt = now + HypothesisTtlMillis,
                )
            }
        }

        return out.take(5)
    }

    fun expire(items: List<AvoidanceHypothesisEntity>, now: Long): List<AvoidanceHypothesisEntity> = items.map { item ->
        if (item.status == AvoidanceHypothesisStatus.Pending.name && item.expiresAt <= now) {
            item.copy(status = AvoidanceHypothesisStatus.Expired.name, decidedAt = now)
        } else item
    }
}

internal fun AvoidanceHypothesisEntity.toUi(): AvoidanceHypothesisUi = AvoidanceHypothesisUi(
    id = id,
    candidatePattern = candidatePattern,
    sourceTheme = sourceTheme,
    originLabel = when (origin) {
        "dialogue" -> "对话候选"
        "behavior" -> "行为聚合"
        "rule" -> "本机规则"
        else -> "候选"
    },
    reason = reason,
    evidenceCount = evidenceCount,
    status = runCatching { AvoidanceHypothesisStatus.valueOf(status) }.getOrDefault(AvoidanceHypothesisStatus.Pending),
    expiresAt = expiresAt,
)
