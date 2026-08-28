package app.aulune.mobile

/** Agent 的本地运行阶段；网络探索不属于自动阶段，必须由用户另一次点击触发。 */
enum class AgentRunPhase(val label: String) {
    Idle("待运行"),
    Synthesizing("正在理解本机证据"),
    AwaitingConfirmation("等待用户确认"),
    Ready("已完成本轮认知"),
    Failed("本轮未完成")
}

data class AgentRunUiState(
    val phase: AgentRunPhase = AgentRunPhase.Idle,
    val notice: String = "点击后才会整理本机证据；不会在后台联网。",
    val completedAt: Long = 0L,
)

enum class AuluneAgentTool(val label: String, val network: Boolean) {
    ObserveLocalEvidence("读取本机聚合证据", false),
    RebuildPreferences("重建兴趣偏好", false),
    ProposeHypotheses("生成待确认兴趣候选", false),
    EvaluateCandidates("评估内容候选", false),
    SynthesizeMemory("合成五层认知快照", false),
    CloudProfileCandidate("生成云端画像候选", true),
}

object AuluneAgentToolPolicy {
    val localPipeline: List<AuluneAgentTool> = listOf(
        AuluneAgentTool.ObserveLocalEvidence,
        AuluneAgentTool.RebuildPreferences,
        AuluneAgentTool.ProposeHypotheses,
        AuluneAgentTool.EvaluateCandidates,
        AuluneAgentTool.SynthesizeMemory,
    )

    fun description(): String = localPipeline.joinToString(" → ") { it.label }
}

data class AgentMemoryLayer(
    val name: String,
    val description: String,
    val evidenceCount: Int,
    val requiresConfirmation: Boolean,
)

data class AgentCognitiveSnapshot(
    val layers: List<AgentMemoryLayer>,
    val recommendationFocus: List<String>,
    val pendingConfirmations: Int,
    val generatedAt: Long,
) {
    val summary: String
        get() = if (recommendationFocus.isEmpty()) {
            "本机证据仍在积累，先浏览并反馈内容。"
        } else {
            "当前认知重点：${recommendationFocus.joinToString("、")}；推荐仍由本机规则执行。"
        }
}

data class AgentCandidateEvaluation(
    val contentKey: String,
    val score: Double,
    val reasons: List<String>,
) {
    val explanation: String get() = reasons.joinToString("；")
}

/**
 * Aulune 的可审计 Agent 核心：不调用网络、不保存原始对话、不把推测直接写成用户事实。
 * Event/Preference 使用现有事件和兴趣表；Awareness 使用待确认候选；Insight/Soul 使用确认式画像层。
 */
object AuluneAgentPolicy {
    private const val EventLayer = "Event · 行为事件"
    private const val PreferenceLayer = "Preference · 偏好"
    private const val AwarenessLayer = "Awareness · 候选意识"
    private const val InsightLayer = "Insight · 认知洞察"
    private const val SoulLayer = "Soul · 长期画像"

    internal fun synthesize(snapshot: RankingSnapshot, now: Long = System.currentTimeMillis()): AgentCognitiveSnapshot {
        val focus = snapshot.interests
            .filter { it.lifecycle.toLifecycle() != InterestLifecycle.Archived }
            .sortedByDescending { it.weight }
            .take(3)
            .map { it.theme }
        val pending = snapshot.hypotheses.count { it.status == InterestHypothesisStatus.Pending.name }
        val values = snapshot.profiles.firstOrNull { it.layer == ProfileLayer.Values.name }
        val core = snapshot.profiles.firstOrNull { it.layer == ProfileLayer.Core.name }
        val awareness = if (pending == 0) "暂无待确认候选；新的跨主题推测必须经过你确认。" else "有 $pending 个兴趣候选等待确认或拒绝。"
        val insight = values?.summary?.takeIf { it.isNotBlank() } ?: "洞察层尚未形成确认结论。"
        val soul = core?.summary?.takeIf { it.isNotBlank() && core.confirmationState == "confirmed" }
            ?: "长期画像只会由你确认后的长期方向逐步形成。"
        return AgentCognitiveSnapshot(
            layers = listOf(
                AgentMemoryLayer(EventLayer, "打开、保存、反馈和导入等事件只保存在本机。", snapshot.events.size, false),
                AgentMemoryLayer(PreferenceLayer, focus.ifEmpty { listOf("尚在探索") }.joinToString("、"), snapshot.interests.sumOf { it.evidenceCount }, false),
                AgentMemoryLayer(AwarenessLayer, awareness, pending, true),
                AgentMemoryLayer(InsightLayer, insight, values?.evidenceCount ?: 0, true),
                AgentMemoryLayer(SoulLayer, soul, core?.evidenceCount ?: 0, true),
            ),
            recommendationFocus = focus,
            pendingConfirmations = pending,
            generatedAt = now,
        )
    }

    internal fun evaluate(
        item: LocalContentEntity,
        snapshot: RankingSnapshot,
        rotationIndex: Int = 0,
        intent: SessionIntent = SessionIntent.Balanced,
    ): AgentCandidateEvaluation {
        val base = LocalAdaptiveCore.score(item, snapshot.interests, snapshot.events, snapshot.feedback, rotationIndex, intent)
        val matching = snapshot.interests.firstOrNull { it.theme == item.theme }
        val reasons = buildList {
            if (matching != null) add("匹配本机主题 ${item.theme}") else add("保留新主题探索机会")
            if (snapshot.events.none { it.sourceKey == item.sourceKey }) add("来源新颖")
            if (item.summary.isNotBlank()) add("内容摘要完整")
            if (item.thumbnailUrl.isNotBlank()) add("有来源缩略图")
            if (snapshot.feedback.none { it.targetType == FeedbackTarget.Content.name && it.targetKey == item.contentKey }) add("未被当前内容反馈否决")
        }
        return AgentCandidateEvaluation(item.contentKey, base, reasons)
    }

    internal fun sortCandidates(items: List<LocalContentEntity>, snapshot: RankingSnapshot, intent: SessionIntent = SessionIntent.Balanced): List<AgentCandidateEvaluation> =
        items
            .filterNot { LocalAdaptiveCore.shouldExclude(it, snapshot.feedback) }
            .map { evaluate(it, snapshot, intent = intent) }
            .sortedByDescending { it.score }
}
