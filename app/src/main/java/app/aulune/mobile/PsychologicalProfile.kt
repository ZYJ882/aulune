package app.aulune.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心理学画像：对齐 OpenBiliClaw 的灵魂引擎四维。
 *
 * - MBTI：四维度 + 置信度
 * - 认知风格：信息处理偏好（结构化 / 直觉型 / 实验型 / 综合型）
 * - 深层需求：心理层面的内容驱动力（成就感 / 归属感 / 好奇 / 控制感 / 自我表达 …）
 *
 * 与 [LocalProfileEntity] 不同：[LocalProfileEntity] 是行为归纳的渐进式画像（Surface / Interests / Values / Core），
 * 这里是云端 AI 一次性推断的心理学画像；用户可以确认或重新观察。
 */
enum class PsychologicalDimension(val label: String) {
    Mbti("MBTI 推断"),
    CognitiveStyle("认知风格"),
    DeepNeeds("深层需求"),
    PersonaSketch("人格素描")
}

@Entity(tableName = "local_psychological_profile")
data class PsychologicalProfileEntity(
    @PrimaryKey val dimension: String,
    /** 主结论，例如 "INTP-A" 或 "结构化 + 偏直觉" */
    val summary: String,
    /** 详细说明，例如各维度置信度或子特质 */
    val detail: String = "",
    /** 候选；用户确认前先放在 candidate，确认后才写入 summary */
    val candidate: String = "",
    val candidateDetail: String = "",
    val evidenceCount: Int = 0,
    val confirmationState: String = "automatic",
    val updatedAt: Long,
    val revision: Int = 1,
)

data class PsychologicalProfileUi(
    val dimension: PsychologicalDimension,
    val summary: String,
    val detail: String,
    val candidate: String,
    val candidateDetail: String,
    val evidenceCount: Int,
    val confirmationState: String,
    val updatedAt: Long,
) {
    val hasCandidate: Boolean get() = candidate.isNotBlank()
    val isConfirmed: Boolean get() = confirmationState == "confirmed"
}

/**
 * 心理学桥接：把"行为 → 心理偏好"的推断显式化、可解释。
 *
 * 例如：用户多次保存"机械结构 / 齿轮咬合"相关内容，
 * 桥接到 "深层需求·理解系统运作" 而非仅 "兴趣·机械"。
 */
object PsychologicalBridges {
    data class Bridge(
        val fromTheme: String,
        val toDimension: PsychologicalDimension,
        val toSummary: String,
        val toDetail: String,
        val reason: String,
    )

    /** 简化版的 Big Five → MBTI 映射，仅做启发式候选生成。 */
    val bridges = listOf(
        Bridge("技术 · AI", PsychologicalDimension.DeepNeeds, "好奇心 / 系统理解", "对算法与智能体如何运作的兴趣，反映深层好奇心和系统化思维倾向。", "持续关注 AI 与系统实现往往映射到「理解系统运作」的深层需求。"),
        Bridge("技术 · 编程", PsychologicalDimension.CognitiveStyle, "结构化 / 偏细节", "对实现细节与类型系统的关注，反映结构化信息处理偏好。", "编程实践偏好往往伴随结构化思维倾向。"),
        Bridge("创造 · 设计", PsychologicalDimension.DeepNeeds, "自我表达 / 美学", "对创作和视觉表达的关注，反映自我表达与美学驱动。", "设计实践往往映射到自我表达和美学驱动力。"),
        Bridge("商业 · 产品", PsychologicalDimension.DeepNeeds, "成就感 / 影响力", "对产品决策和市场反馈的关注，反映成就感与影响力驱动。", "商业产品关注往往伴随成就感和影响力需求。"),
        Bridge("学习 · 知识", PsychologicalDimension.CognitiveStyle, "直觉型 / 概念化", "持续吸收跨领域知识，反映概念化处理偏好。", "广度学习偏好通常映射到直觉型认知风格。"),
        Bridge("生活 · 注意力", PsychologicalDimension.CognitiveStyle, "实验型 / 反思", "对注意力与节奏的关注，反映反思性处理偏好。", "注意力管理实践反映实验型和反思型风格。"),
        Bridge("娱乐 · 影视游戏", PsychologicalDimension.DeepNeeds, "归属感 / 沉浸", "对叙事和沉浸式体验的偏好，反映归属与情感共鸣需求。", "影视游戏沉浸通常映射到归属感需求。"),
    )

    /** 兴趣主题命中桥接时生成候选；返回的候选不写入 summary，只进 candidate。 */
    fun generateCandidates(
        interests: List<InterestEntity>,
        existing: List<PsychologicalProfileEntity>,
        now: Long,
    ): List<PsychologicalProfileEntity> {
        if (interests.isEmpty()) return emptyList()
        val activeThemes = interests
            .filter { it.lifecycle.toLifecycle() == InterestLifecycle.Active && it.evidenceCount >= 3 }
            .sortedByDescending { it.weight }
            .take(6)
            .map { it.theme }
            .toSet()
        val out = mutableListOf<PsychologicalProfileEntity>()
        val existingByDim = existing.associateBy { it.dimension }
        bridges.filter { it.fromTheme in activeThemes }.forEach { bridge ->
            val cur = existingByDim[bridge.toDimension.name]
            // 不覆盖已确认的画像
            if (cur?.confirmationState == "confirmed") return@forEach
            // 已有候选则不重复生成
            if (cur?.candidate?.isNotBlank() == true) return@forEach
            val evidence = interests.firstOrNull { it.theme == bridge.fromTheme }?.evidenceCount ?: 0
            out += PsychologicalProfileEntity(
                dimension = bridge.toDimension.name,
                summary = cur?.summary ?: "",
                detail = cur?.detail ?: "",
                candidate = bridge.toSummary,
                candidateDetail = bridge.toDetail,
                evidenceCount = evidence,
                confirmationState = "pending",
                updatedAt = now,
            )
        }
        return out
    }
}

internal fun PsychologicalProfileEntity.toUi(): PsychologicalProfileUi = PsychologicalProfileUi(
    dimension = runCatching { PsychologicalDimension.valueOf(dimension) }.getOrDefault(PsychologicalDimension.PersonaSketch),
    summary = summary,
    detail = detail,
    candidate = candidate,
    candidateDetail = candidateDetail,
    evidenceCount = evidenceCount,
    confirmationState = confirmationState,
    updatedAt = updatedAt,
)
