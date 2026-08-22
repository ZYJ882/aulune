package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLibraryTest {
    private fun item(
        key: String,
        saved: Boolean = false,
        marked: Boolean = false,
        hidden: Boolean = false,
        updatedAt: Long = 1L
    ) = LocalContentEntity(
        contentKey = key,
        source = "本地测试",
        channel = SourceChannel.Insight.name,
        title = key,
        readTime = "1 分钟",
        summary = "摘要",
        theme = "测试主题",
        url = "",
        gradientStart = 0L,
        gradientEnd = 0L,
        saved = saved,
        marked = marked,
        hidden = hidden,
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    @Test
    fun savedLibraryExcludesHiddenAndSortsByLatestUpdate() {
        val older = item("older", saved = true, updatedAt = 10L)
        val newer = item("newer", saved = true, updatedAt = 20L)
        val hidden = item("hidden", saved = true, hidden = true, updatedAt = 30L)

        val result = filterLibraryContent(LibrarySection.Saved, listOf(older, newer, hidden), emptyList())

        assertEquals(listOf("newer", "older"), result.map { it.contentKey })
    }

    @Test
    fun recentLibraryUsesOpenEventsAndKeepsNewestFirst() {
        val first = item("first")
        val second = item("second")
        val events = listOf(
            BehaviorEventEntity("open-first", "first", "open", "测试主题", 10L),
            BehaviorEventEntity("save-second", "second", "save", "测试主题", 20L),
            BehaviorEventEntity("open-second", "second", "open", "测试主题", 30L),
            BehaviorEventEntity("open-first-again", "first", "open", "测试主题", 40L),
        )

        val result = filterLibraryContent(LibrarySection.Recent, listOf(first, second), events.reversed())

        assertEquals(listOf("first", "second"), result.map { it.contentKey })
    }
}
