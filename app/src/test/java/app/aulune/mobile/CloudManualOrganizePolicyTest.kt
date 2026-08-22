package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudManualOrganizePolicyTest {
    @Test
    fun selectsOnlyVisibleUnanalyzedRecentItemsWithinLimit() {
        val selected = CloudManualOrganizePolicy.selectCandidates(
            listOf(
                content("rule-old", updatedAt = 10L),
                content("cloud", updatedAt = 99L, analysisSource = "cloud"),
                content("hidden", updatedAt = 98L, hidden = true),
                content("rule-new", updatedAt = 30L),
                content("blank", updatedAt = 50L, title = "")
            ),
            limit = 2
        )

        assertEquals(listOf("rule-new", "rule-old"), selected.map { it.contentKey })
    }

    @Test
    fun completionMessagesKeepFailuresOnLocalRules() {
        assertEquals(
            "已整理 3 条内容；分类和候选理由已保存到本机，排序仍由本机规则执行。",
            CloudManualOrganizePolicy.completionMessage(requested = 3, succeeded = 3, failed = 0)
        )
        assertEquals(
            "已整理 2/3 条内容，另有 1 条失败；失败内容保持本机规则。",
            CloudManualOrganizePolicy.completionMessage(requested = 3, succeeded = 2, failed = 1)
        )
    }

    private fun content(
        key: String,
        updatedAt: Long,
        analysisSource: String = "rule",
        hidden: Boolean = false,
        title: String = key
    ) = LocalContentEntity(
        contentKey = key,
        source = "测试来源",
        channel = "测试频道",
        title = title,
        readTime = "",
        summary = "",
        theme = "测试主题",
        url = "https://example.test/$key",
        gradientStart = 0L,
        gradientEnd = 0L,
        hidden = hidden,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        analysisSource = analysisSource
    )
}
