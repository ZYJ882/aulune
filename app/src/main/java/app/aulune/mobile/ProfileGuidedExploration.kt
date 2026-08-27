package app.aulune.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一个可审计、可确认的探索方向。候选永远不会自动变成兴趣，也不会自行触发联网。
 */
enum class InterestHypothesisStatus(val label: String) {
    Pending("等待确认"),
    Confirmed("已确认"),
    Rejected("已拒绝"),
    Expired("已过期")
}

@Entity(tableName = "local_interest_hypothesis")
data class InterestHypothesisEntity(
    @PrimaryKey val id: String,
    val candidateTheme: String,
    val sourceTheme: String,
    val origin: String,
    val reason: String,
    val evidenceCount: Int,
    val status: String = InterestHypothesisStatus.Pending.name,
    val createdAt: Long,
    val expiresAt: Long,
    val decidedAt: Long = 0L,
)

data class InterestHypothesisUi(
    val id: String,
    val candidateTheme: String,
    val sourceTheme: String,
    val originLabel: String,
    val reason: String,
    val evidenceCount: Int,
    val status: InterestHypothesisStatus,
    val expiresAt: Long,
)

data class ProfileGuidedExplorePlan(
    val focusThemes: List<String> = emptyList(),
    val platforms: List<ContentPlatform> = emptyList(),
    val summary: String = "先积累一些本机兴趣证据，再生成探索计划。",
) {
    val isReady: Boolean get() = focusThemes.isNotEmpty() && platforms.isNotEmpty()
}

/**
 * 仅使用已在本机保存的聚合兴趣、反馈和对话主题。这里不读取 Cookie、不保存对话原文，
 * 不调用网络，也不把推测当作个人事实。
 */
object ProfileGuidedExplorationPolicy {
    private const val HypothesisTtlMillis = 14L * 24 * 60 * 60 * 1000

    private data class Bridge(
        val from: String,
        val to: String,
        val reason: String,
    )

    /** 有意保持少量、可解释的跨主题桥接；未知主题不会被伪造。 */
    private val bridges = listOf(
        Bridge("技术 · AI", "创造 · 设计", "你持续关注 AI 与技术，设计和创作实践可能提供新的应用视角。"),
        Bridge("技术 · 编程", "学习 · 知识", "你对编程和实现细节的关注，可能也会延伸到系统学习与知识方法。"),
        Bridge("商业 · 产品", "创造 · 设计", "你关注产品与商业决策，设计表达可能带来互补的观察角度。"),
        Bridge("学习 · 知识", "生活 · 注意力", "你持续吸收知识，注意力、习惯与学习方法可能值得小范围探索。"),
        Bridge("创造 · 设计", "商业 · 产品", "你对创作和设计的兴趣，可能会延伸到产品化与品牌实践。"),
        Bridge("生活 · 注意力", "学习 · 知识", "你在意生活节奏和注意力，学习方法与认知工具可能成为可尝试方向。"),
        Bridge("娱乐 · 影视游戏", "创造 · 设计", "你对影视或游戏的关注，可能延伸到叙事、视觉和创作方法。"),
    )

    private val platformByGroup = mapOf(
        "技术" to listOf(ContentPlatform.BILIBILI, ContentPlatform.ZHIHU, ContentPlatform.YOUTUBE, ContentPlatform.REDDIT),
        "商业" to listOf(ContentPlatform.ZHIHU, ContentPlatform.V2EX, ContentPlatform.WEIBO, ContentPlatform.TWITTER),
        "创造" to listOf(ContentPlatform.XIAOHONGSHU, ContentPlatform.BILIBILI, ContentPlatform.YOUTUBE),
        "学习" to listOf(ContentPlatform.ZHIHU, ContentPlatform.BILIBILI, ContentPlatform.REDDIT, ContentPlatform.YOUTUBE),
        "生活" to listOf(ContentPlatform.XIAOHONGSHU, ContentPlatform.DOUYIN, ContentPlatform.BILIBILI),
        "娱乐" to listOf(ContentPlatform.BILIBILI, ContentPlatform.BANGUMI, ContentPlatform.DOUYIN),
    )

    fun propose(
        interests: List<InterestEntity>,
        existing: List<InterestHypothesisEntity>,
        now: Long,
    ): List<InterestHypothesisEntity> {
        val activeThemes = interests
            .filter { it.lifecycle.toLifecycle() == InterestLifecycle.Active && it.evidenceCount >= 3 }
            .sortedByDescending { it.weight }
            .map { it.theme }
            .toSet()
        if (activeThemes.isEmpty()) return emptyList()
        val blocked = existing
            .filter { it.status in setOf(InterestHypothesisStatus.Pending.name, InterestHypothesisStatus.Confirmed.name) }
            .map { it.candidateTheme }
            .toSet() + activeThemes
        return bridges.asSequence()
            .filter { it.from in activeThemes && it.to !in blocked }
            .take(3)
            .map { bridge ->
                val evidence = interests.firstOrNull { it.theme == bridge.from }?.evidenceCount ?: 0
                InterestHypothesisEntity(
                    id = "hypothesis:${bridge.from.hashCode()}:${bridge.to.hashCode()}",
                    candidateTheme = bridge.to,
                    sourceTheme = bridge.from,
                    origin = "behavior",
                    reason = bridge.reason,
                    evidenceCount = evidence,
                    createdAt = now,
                    expiresAt = now + HypothesisTtlMillis,
                )
            }
            .toList()
    }

    /** 对话只保留抽取后的主题和计数，不保存关键词或聊天原文作为画像证据。 */
    fun proposeFromDialogue(messages: List<String>, existing: List<InterestHypothesisEntity>, now: Long): List<InterestHypothesisEntity> {
        val text = messages.joinToString(" ").lowercase()
        if (text.isBlank()) return emptyList()
        val detected = RuleTopicClassifier.classifyText(text)
        val blocked = existing
            .filter { it.status in setOf(InterestHypothesisStatus.Pending.name, InterestHypothesisStatus.Confirmed.name) }
            .map { it.candidateTheme }
            .toSet()
        return detected.asSequence()
            .filterNot { it in blocked }
            .take(3)
            .map { theme ->
                val mentions = messages.count { it.lowercase().contains(theme.substringAfter("·").trim().substringBefore(" ").lowercase()) }
                    .coerceAtLeast(1)
                InterestHypothesisEntity(
                    id = "dialogue:${theme.hashCode()}:$now",
                    candidateTheme = theme,
                    sourceTheme = "对话",
                    origin = "dialogue",
                    reason = "由你主动点击从本段对话提取的主题候选；未保存对话原文作为画像证据。",
                    evidenceCount = mentions,
                    createdAt = now,
                    expiresAt = now + HypothesisTtlMillis,
                )
            }
            .toList()
    }

    fun expire(items: List<InterestHypothesisEntity>, now: Long): List<InterestHypothesisEntity> = items.map { item ->
        if (item.status == InterestHypothesisStatus.Pending.name && item.expiresAt <= now) {
            item.copy(status = InterestHypothesisStatus.Expired.name, decidedAt = now)
        } else item
    }

    /**
     * 计划只选择公开候选的来源与排序重点。真正联网需要用户随后点击执行；
     * 当前来源连接器不伪造“全文搜索”能力，导入后仍由本机排序优先展示匹配内容。
     */
    fun plan(
        interests: List<InterestEntity>,
        hypotheses: List<InterestHypothesisEntity>,
        intent: SessionIntent,
    ): ProfileGuidedExplorePlan {
        val confirmed = hypotheses
            .filter { it.status == InterestHypothesisStatus.Confirmed.name }
            .sortedByDescending { it.decidedAt }
            .map { it.candidateTheme }
        val active = interests
            .filter { it.lifecycle.toLifecycle() == InterestLifecycle.Active }
            .sortedByDescending { it.weight }
            .map { it.theme }
        val focus = (confirmed + active).distinct().take(3)
        if (focus.isEmpty()) {
            return ProfileGuidedExplorePlan(summary = "先在信息流中打开、保存或反馈内容；形成活跃兴趣后，可在这里查看探索计划。")
        }
        val platforms = focus.flatMap { theme ->
            platformByGroup[theme.substringBefore("·").trim()].orEmpty()
        }.distinct().take(if (intent == SessionIntent.Explore) 4 else 3)
        val labels = focus.joinToString("、")
        val sourceLabels = platforms.joinToString("、") { it.shortLabel }
        val mode = when (intent) {
            SessionIntent.Explore -> "探索模式会保留更多跨主题候选"
            SessionIntent.Focus -> "专注模式会优先保留已有兴趣附近的候选"
            SessionIntent.Calm -> "低噪模式会在导入后继续降低近期重复主题"
            SessionIntent.Balanced -> "平衡模式会兼顾稳定兴趣与少量新来源"
        }
        return ProfileGuidedExplorePlan(
            focusThemes = focus,
            platforms = platforms,
            summary = "依据本机画像关注「$labels」，建议手动从 $sourceLabels 导入公开候选。$mode；点击执行前不会联网。"
        )
    }
}

internal fun InterestHypothesisEntity.toUi(): InterestHypothesisUi = InterestHypothesisUi(
    id = id,
    candidateTheme = candidateTheme,
    sourceTheme = sourceTheme,
    originLabel = if (origin == "dialogue") "对话候选" else "行为桥接",
    reason = reason,
    evidenceCount = evidenceCount,
    status = runCatching { InterestHypothesisStatus.valueOf(status) }.getOrDefault(InterestHypothesisStatus.Pending),
    expiresAt = expiresAt,
)
