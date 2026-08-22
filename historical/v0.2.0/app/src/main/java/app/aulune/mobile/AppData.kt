package app.aulune.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SourceChannel(val label: String, val accent: Long) {
    Insight("深读", 0xFF7C5CFC),
    Brief("观察", 0xFF0EA5E9),
    Video("视频", 0xFFF97316),
    Notes("笔记", 0xFF14B8A6),
    Signal("灵感", 0xFFEC4899)
}

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val keyHint: String
) {
    OpenAI("OpenAI", "gpt-4o-mini", "sk-…"),
    Claude("Claude", "claude-3-5-haiku-latest", "sk-ant-…"),
    Gemini("Gemini", "gemini-2.5-flash", "AIza…"),
    DeepSeek("DeepSeek", "deepseek-v4-flash", "sk-…")
}

data class ProviderSettings(
    val apiKey: String = "",
    val model: String = ""
)

data class CuratedItem(
    val id: String,
    val channel: SourceChannel,
    val title: String,
    val source: String,
    val readTime: String,
    val insight: String,
    val theme: String,
    val url: String,
    val gradientStart: Long,
    val gradientEnd: Long,
    var marked: Boolean = false,
    var saved: Boolean = false
)

data class ConversationMessage(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val time: String
)

class AuluneStore {
    val items = mutableStateListOf<CuratedItem>().apply { addAll(seedItems()) }
    val messages = mutableStateListOf<ConversationMessage>().apply { addAll(seedMessages()) }
    val providerSettings = mutableStateMapOf<AiProvider, ProviderSettings>()

    var activeLens by mutableStateOf("清晰思考")
        private set
    var savedCount by mutableStateOf(7)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var selectedProvider by mutableStateOf(AiProvider.OpenAI)
    var isGenerating by mutableStateOf(false)
    var aiStatus by mutableStateOf("本地对话模式")
        private set

    fun rotateFeed() {
        isRefreshing = true
        val first = items.removeFirstOrNull()
        if (first != null) items.add(first)
        activeLens = listOf("清晰思考", "创造系统", "长期视角", "注意力管理").random()
        savedCount = (4..13).random()
        isRefreshing = false
    }

    fun toggleMark(item: CuratedItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item.copy(marked = !item.marked)
    }

    fun toggleSaved(item: CuratedItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item.copy(saved = !item.saved)
    }

    fun addUserMessage(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            messages += ConversationMessage(messages.size + 1, true, clean, "刚刚")
        }
    }

    fun addAssistantMessage(text: String) {
        messages += ConversationMessage(messages.size + 1, false, text.trim(), "刚刚")
    }

    fun setProviderSettings(provider: AiProvider, settings: ProviderSettings) {
        providerSettings[provider] = settings
        selectedProvider = provider
        aiStatus = if (settings.apiKey.isBlank()) "本地对话模式" else "${provider.displayName} 已就绪"
    }

    fun updateGenerating(value: Boolean) {
        isGenerating = value
        if (value) aiStatus = "${selectedProvider.displayName} 正在思考"
    }

    fun updateAiStatus(value: String) {
        aiStatus = value
    }
}

private fun seedItems() = listOf(
    CuratedItem(
        id = "insight-1", channel = SourceChannel.Insight,
        title = "把复杂问题缩小：先找到真正需要被解释的那个变量",
        source = "思维备忘", readTime = "阅读 9 分钟", theme = "思考 · 结构",
        insight = "适合在信息过载时阅读：它不提供更多方法，而是帮你重建判断问题的重要顺序。",
        url = "https://example.com", gradientStart = 0xFF28224D, gradientEnd = 0xFF8067E7
    ),
    CuratedItem(
        id = "notes-1", channel = SourceChannel.Notes,
        title = "真正可持续的个人系统，总有一个足够轻的最小入口",
        source = "简约实践", readTime = "阅读 6 分钟", theme = "创造 · 系统",
        insight = "它关注的是降低维护成本，而不是堆叠更多工具，和你对“低摩擦创造”的偏好很接近。",
        url = "https://example.com", gradientStart = 0xFF0E5A57, gradientEnd = 0xFF4CC6B7
    ),
    CuratedItem(
        id = "video-1", channel = SourceChannel.Video,
        title = "为什么小而稳定的迭代，比宏大的计划更接近长期成果",
        source = "慢变量", readTime = "12:05", theme = "长期 · 迭代",
        insight = "这条内容提供了可验证的节奏，而不是激励话术；可以作为重新安排本周工作的参考。",
        url = "https://example.com", gradientStart = 0xFF5A263D, gradientEnd = 0xFFE56884
    ),
    CuratedItem(
        id = "brief-1", channel = SourceChannel.Brief,
        title = "从观点到行动：如何把一次阅读变成可以复用的判断",
        source = "周末观察", readTime = "阅读 7 分钟", theme = "阅读 · 决策",
        insight = "你更享受内容留下可复用框架的时刻；这篇给出的复盘方式足够轻，值得试一次。",
        url = "https://example.com", gradientStart = 0xFF173D5A, gradientEnd = 0xFF5EAED8
    ),
    CuratedItem(
        id = "signal-1", channel = SourceChannel.Signal,
        title = "给注意力留白，并不是放弃效率，而是在保护判断质量",
        source = "夜航者", readTime = "阅读 5 分钟", theme = "生活 · 注意力",
        insight = "当输入开始变得嘈杂时，它能帮助你重新确认哪些信息不必被马上处理。",
        url = "https://example.com", gradientStart = 0xFF5B1F55, gradientEnd = 0xFFA64D96
    )
)

private fun seedMessages() = listOf(
    ConversationMessage(1, false, "我是 Aulune。你可以让我梳理想法、解释问题，或一起制定下一步。", "09:20"),
    ConversationMessage(2, true, "我希望把近期的工作节奏调得更稳一点。", "09:22"),
    ConversationMessage(3, false, "可以。我们先不急着新增任务，先识别哪一部分最消耗你的注意力，再为它设计一个更轻的默认动作。", "09:22")
)
