package app.aulune.mobile

import kotlin.math.absoluteValue

enum class InterestLifecycle(val label: String) {
    Trial("观察中"),
    Active("活跃"),
    Decaying("降温中"),
    Archived("已归档")
}

object LocalAdaptiveCore {
    private const val TrialEvidenceThreshold = 5
    private const val TrialDurationMillis = 7L * 24 * 60 * 60 * 1000
    private const val DecayAfterMillis = 30L * 24 * 60 * 60 * 1000
    private const val ArchiveAfterMillis = 60L * 24 * 60 * 60 * 1000

    fun normalize(content: LocalContentEntity): LocalContentEntity {
        val sourceKey = content.sourceKey.ifBlank {
            if (content.source.startsWith("B 站")) "bilibili" else content.source.substringBefore("·").trim().lowercase()
        }
        val author = content.authorKey.ifBlank {
            if (content.source.startsWith("B 站")) content.source.substringAfterLast("·", "").trim().lowercase() else ""
        }
        val base = content.copy(sourceKey = sourceKey, authorKey = author)
        return RuleTopicClassifier.canonicalize(base)
    }

    fun updateInterest(
        current: InterestEntity?,
        theme: String,
        delta: Double,
        now: Long,
        explicitNegative: Boolean = false
    ): InterestEntity {
        if (current == null) {
            return InterestEntity(
                theme = theme,
                weight = delta.coerceAtLeast(0.0),
                evidenceCount = 1,
                lifecycle = if (explicitNegative) InterestLifecycle.Archived.name else InterestLifecycle.Trial.name,
                firstSeenAt = now,
                lastEvidenceAt = now,
                updatedAt = now
            )
        }
        val oldState = current.lifecycle.toLifecycle()
        val count = current.evidenceCount + 1
        val nextState = when {
            explicitNegative -> InterestLifecycle.Archived
            oldState == InterestLifecycle.Archived || oldState == InterestLifecycle.Decaying -> InterestLifecycle.Active
            oldState == InterestLifecycle.Trial && (
                count >= TrialEvidenceThreshold || now - current.firstSeenAt >= TrialDurationMillis
            ) -> InterestLifecycle.Active
            else -> oldState
        }
        return current.copy(
            weight = (current.weight + delta).coerceAtLeast(0.0),
            evidenceCount = count,
            lifecycle = nextState.name,
            lastEvidenceAt = now,
            updatedAt = now
        )
    }

    fun applyTimeLifecycle(interests: List<InterestEntity>, now: Long): List<InterestEntity> = interests.map { item ->
        val silence = if (item.lastEvidenceAt > 0L) now - item.lastEvidenceAt else 0L
        when (item.lifecycle.toLifecycle()) {
            InterestLifecycle.Active -> if (silence >= DecayAfterMillis) item.copy(
                lifecycle = InterestLifecycle.Decaying.name,
                weight = (item.weight * 0.5).coerceAtLeast(0.0),
                updatedAt = now
            ) else item
            InterestLifecycle.Decaying -> if (silence >= ArchiveAfterMillis) item.copy(
                lifecycle = InterestLifecycle.Archived.name,
                updatedAt = now
            ) else item
            InterestLifecycle.Trial -> if (item.firstSeenAt > 0L && now - item.firstSeenAt >= TrialDurationMillis) item.copy(
                lifecycle = InterestLifecycle.Active.name,
                updatedAt = now
            ) else item
            else -> item
        }
    }

    fun shouldExclude(item: LocalContentEntity, feedback: List<LocalFeedbackEntity>): Boolean {
        val negatives = feedback.filter { it.feedbackType == FeedbackType.Negative.name }
        return negatives.any { signal ->
            when (signal.targetType) {
                FeedbackTarget.Content.name -> signal.targetKey == item.contentKey
                FeedbackTarget.Author.name -> item.authorKey.isNotBlank() && signal.targetKey == item.authorKey
                FeedbackTarget.Series.name -> item.seriesKey.isNotBlank() && signal.targetKey == item.seriesKey
                else -> false
            }
        }
    }

    fun score(
        item: LocalContentEntity,
        interests: List<InterestEntity>,
        recentEvents: List<BehaviorEventEntity>,
        feedback: List<LocalFeedbackEntity>,
        rotationIndex: Int,
        intent: SessionIntent = SessionIntent.Balanced
    ): Double {
        val matchingInterest = interests.firstOrNull { it.theme == item.theme }
        val lifecycleMultiplier = when (matchingInterest?.lifecycle.toLifecycle()) {
            InterestLifecycle.Active -> 1.0
            InterestLifecycle.Trial -> 0.55
            InterestLifecycle.Decaying -> 0.25
            InterestLifecycle.Archived -> 0.0
        }
        val interest = (matchingInterest?.weight ?: 0.0) * lifecycleMultiplier
        val sourceEvents = recentEvents.filter { it.sourceKey == item.sourceKey }
        val themeEvents = recentEvents.filter { it.theme == item.theme || it.topicGroup == item.topicGroup }
        val themeFatigue = fatigue(themeEvents.size, recentEvents.size) * 1.15
        val sourceMonotony = fatigue(sourceEvents.size, recentEvents.size) * 0.45
        val exploration = if (sourceEvents.isEmpty()) 0.18 else 0.0
        val positive = feedback.filter { it.feedbackType == FeedbackType.Positive.name }
        val negativeThemes = feedback.filter { it.feedbackType == FeedbackType.Negative.name && it.targetType == FeedbackTarget.Theme.name }
        val positiveTheme = if (positive.any { it.targetType == FeedbackTarget.Theme.name && it.targetKey == item.theme }) 1.25 else 0.0
        val negativeTheme = if (negativeThemes.any { it.targetKey == item.theme }) 2.3 else 0.0
        val negativeGroup = if (negativeThemes.any { it.targetKey == item.topicGroup }) 1.1 else 0.0
        val localSignals = (if (item.marked) 1.4 else 0.0) + (if (item.saved) 0.8 else 0.0)
        val intentAdjustment = when (intent) {
            SessionIntent.Balanced -> 0.0
            SessionIntent.Focus -> if (matchingInterest?.lifecycle.toLifecycle() == InterestLifecycle.Active) 0.9 else -0.12
            SessionIntent.Explore -> if (sourceEvents.isEmpty() || matchingInterest?.lifecycle.toLifecycle() == InterestLifecycle.Trial) 0.72 else -0.08
            SessionIntent.Calm -> if (themeFatigue < 0.25 && item.readTime.length <= 8) 0.38 else -themeFatigue * 0.9
        }
        val deterministicTiebreaker = ((item.contentKey.hashCode() xor rotationIndex).absoluteValue % 100) / 1000.0
        return interest + localSignals + exploration + positiveTheme + intentAdjustment - negativeTheme - negativeGroup - themeFatigue - sourceMonotony + deterministicTiebreaker
    }

    fun insightFor(item: LocalContentEntity, interest: InterestEntity?, feedback: List<LocalFeedbackEntity>, recentEvents: List<BehaviorEventEntity>): String {
        val lifecycle = interest?.lifecycle.toLifecycle()
        val evidence = interest?.evidenceCount ?: 0
        val fatigue = fatigue(recentEvents.count { it.theme == item.theme || it.topicGroup == item.topicGroup }, recentEvents.size)
        return when {
            feedback.any { it.feedbackType == FeedbackType.Positive.name && it.targetType == FeedbackTarget.Theme.name && it.targetKey == item.theme } -> "你明确喜欢「${item.theme}」，本机推荐给予主题加权。"
            lifecycle == InterestLifecycle.Trial -> "「${item.theme}」还在观察期，当前已有 $evidence 条本机证据。"
            lifecycle == InterestLifecycle.Decaying -> "「${item.theme}」近期证据较少，已自动降温但仍可被新行为重新激活。"
            lifecycle == InterestLifecycle.Active && fatigue > 0.45 -> "它符合你的活跃兴趣，但同类内容近期出现较多，已施加疲劳降权。"
            lifecycle == InterestLifecycle.Active -> "它与活跃兴趣「${item.theme}」匹配，来自本机画像的持续证据。"
            else -> "这是一条探索性候选；打开、喜欢或不感兴趣都会改变之后的本机排序。"
        }
    }

    private fun fatigue(count: Int, window: Int): Double {
        if (count <= 0 || window <= 0) return 0.0
        return ((count.toDouble() * count.toDouble()) / window.toDouble() * 0.9).coerceIn(0.0, 1.0)
    }
}

enum class FeedbackType { Positive, Negative }
enum class FeedbackTarget { Content, Theme, TopicGroup, Author, Series }

fun String?.toLifecycle(): InterestLifecycle = runCatching {
    InterestLifecycle.valueOf(this.orEmpty())
}.getOrDefault(InterestLifecycle.Trial)
