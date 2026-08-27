package app.aulune.mobile

import java.util.Locale

enum class SessionIntent(val label: String, val description: String) {
    Balanced("平衡", "在熟悉主题与新来源之间保持平衡"),
    Focus("专注", "优先显示已活跃的兴趣主题"),
    Explore("探索", "提高新来源和观察中主题的出现机会"),
    Calm("低噪", "降低近期重复主题，优先短内容和未读来源")
}

enum class ProfileLayer(val label: String) {
    Surface("当前状态"),
    Interests("兴趣层"),
    Values("长期方向"),
    Core("核心边界")
}

object RuleTopicClassifier {
    private data class Rule(
        val canonicalTheme: String,
        val group: String,
        val words: Set<String>
    )

    private val rules = listOf(
        Rule("技术 · AI", "技术", setOf("ai", "人工智能", "大模型", "llm", "模型", "机器学习", "deepseek", "chatgpt", "gemini")),
        Rule("技术 · 编程", "技术", setOf("编程", "代码", "开发", "android", "kotlin", "python", "开源", "github", "算法")),
        Rule("商业 · 产品", "商业", setOf("产品", "创业", "商业", "营销", "增长", "品牌", "公司", "运营")),
        Rule("创造 · 设计", "创造", setOf("设计", "摄影", "绘画", "创作", "写作", "音乐", "剪辑", "艺术")),
        Rule("学习 · 知识", "学习", setOf("学习", "阅读", "知识", "课程", "历史", "科学", "数学", "语言")),
        Rule("生活 · 注意力", "生活", setOf("生活", "习惯", "效率", "注意力", "健康", "心理", "时间", "整理")),
        Rule("娱乐 · 影视游戏", "娱乐", setOf("游戏", "动画", "电影", "影视", "番剧", "音乐现场", "电竞", "漫画"))
    )

    fun classifyText(text: String): List<String> {
        val normalized = text.lowercase(Locale.ROOT)
        return rules
            .map { rule -> rule to rule.words.count { word -> normalized.contains(word) } }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (rule, _) -> rule.canonicalTheme }
            .distinct()
    }

    fun canonicalize(content: LocalContentEntity): LocalContentEntity {
        if (content.analysisSource == "cloud" && content.theme.isNotBlank() && content.topicGroup.isNotBlank()) {
            return content.copy(
                seriesKey = content.seriesKey.ifBlank { inferSeriesKey(content.title) },
                authorKey = content.authorKey.ifBlank { inferAuthorKey(content.source) }
            )
        }
        val text = listOf(content.title, content.summary, content.theme, content.source)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val winning = rules.maxByOrNull { rule -> rule.words.count { word -> text.contains(word) } }
        val score = winning?.words?.count { text.contains(it) } ?: 0
        val retainedTheme = content.theme.trim()
        val fallbackTheme = if (retainedTheme.isNotBlank()) retainedTheme else "探索 · 未分类"
        val theme = if (score > 0) winning!!.canonicalTheme else fallbackTheme
        val group = if (score > 0) winning!!.group else fallbackTheme.substringBefore("·").trim().ifBlank { "探索" }
        val series = content.seriesKey.ifBlank { inferSeriesKey(content.title) }
        val author = content.authorKey.ifBlank { inferAuthorKey(content.source) }
        return content.copy(theme = theme, topicGroup = group, seriesKey = series, authorKey = author)
    }

    fun inferSeriesKey(title: String): String {
        val clean = title.lowercase(Locale.ROOT)
            .replace(Regex("【[^】]{1,40}】"), " ")
            .replace(Regex("\\[[^]]{1,40}]"), " ")
            .replace(Regex("第\\s*\\d+\\s*[期集篇]"), " ")
            .replace(Regex("ep\\.?\\s*\\d+"), " ")
            .replace(Regex("part\\s*\\d+"), " ")
            .replace(Regex("\\d+"), " ")
            .replace(Regex("[：:|｜—-].*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean.take(48)
    }

    private fun inferAuthorKey(source: String): String {
        val tokens = source.split("·", "•", "|", "｜").map { it.trim() }.filter { it.isNotBlank() }
        return tokens.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
    }
}

object LocalProfileBuilder {
    fun build(
        interests: List<InterestEntity>,
        eventCount: Int,
        intent: SessionIntent,
        existing: Map<String, LocalProfileEntity>,
        now: Long
    ): List<LocalProfileEntity> {
        val active = interests.filter { it.lifecycle.toLifecycle() == InterestLifecycle.Active }
            .sortedByDescending { it.weight }
        val visible = interests.filter { it.lifecycle.toLifecycle() != InterestLifecycle.Archived }
            .sortedByDescending { it.weight }
        val topThemes = active.take(3).joinToString("、") { it.theme }
            .ifBlank { "尚在探索" }
        val surface = "本次使用偏向「${intent.label}」：${intent.description}。"
        val interestSummary = if (visible.isEmpty()) {
            "尚未形成稳定兴趣；继续打开、保存、喜欢或不感兴趣来提供本机证据。"
        } else {
            "当前重点：$topThemes。活跃主题会优先出现，降温主题保留重新激活的机会。"
        }
        val longTermCandidate = if (active.size >= 2 && eventCount >= 12) {
            "你对${active.take(2).joinToString("和") { it.theme }}持续提供了本机正向证据；可作为长期方向候选。"
        } else {
            "证据仍不足以形成长期方向；Aulune不会自动定义你的价值观。"
        }
        val coreCandidate = "核心边界默认保持空白。只有你主动确认后，长期方向才会被写入本机核心画像。"
        return listOf(
            profile(ProfileLayer.Surface, surface, eventCount, now, existing),
            profile(ProfileLayer.Interests, interestSummary, visible.sumOf { it.evidenceCount }, now, existing),
            profile(ProfileLayer.Values, longTermCandidate, active.sumOf { it.evidenceCount }, now, existing, requiresConfirmation = true),
            profile(ProfileLayer.Core, coreCandidate, 0, now, existing, requiresConfirmation = true)
        )
    }

    fun confirm(profile: LocalProfileEntity, now: Long): LocalProfileEntity = profile.copy(
        summary = profile.candidate.ifBlank { profile.summary },
        candidate = "",
        confirmationState = "confirmed",
        updatedAt = now,
        revision = profile.revision + 1
    )

    private fun profile(
        layer: ProfileLayer,
        candidate: String,
        evidence: Int,
        now: Long,
        existing: Map<String, LocalProfileEntity>,
        requiresConfirmation: Boolean = false
    ): LocalProfileEntity {
        val prior = existing[layer.name]
        val preserveConfirmed = requiresConfirmation && prior?.confirmationState == "confirmed"
        return LocalProfileEntity(
            layer = layer.name,
            summary = if (preserveConfirmed) prior!!.summary else if (requiresConfirmation) "等待你的确认" else candidate,
            candidate = if (requiresConfirmation) candidate else "",
            evidenceCount = evidence,
            confirmationState = if (preserveConfirmed) "confirmed" else if (requiresConfirmation) "pending" else "automatic",
            updatedAt = now,
            revision = (prior?.revision ?: 0) + 1
        )
    }
}
