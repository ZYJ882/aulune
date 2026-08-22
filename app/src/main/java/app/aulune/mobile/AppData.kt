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

/** A UI projection of a persisted local content item. */
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
    val marked: Boolean = false,
    val saved: Boolean = false,
    val lifecycle: InterestLifecycle = InterestLifecycle.Trial,
    val sourceKey: String = "",
    val authorKey: String = "",
    val seriesKey: String = "",
    val topicGroup: String = ""
)

data class ConversationMessage(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val time: String
)

/**
 * 对话与模型配置仍是会话级功能。推荐、收藏、历史和兴趣画像已移交给 LocalFeedViewModel
 * 与 Room 数据库，不再保存在 Compose 内存列表中。
 */
class AuluneStore {
    val messages = mutableStateListOf<ConversationMessage>().apply { addAll(seedMessages()) }
    val providerSettings = mutableStateMapOf<AiProvider, ProviderSettings>()

    var selectedProvider by mutableStateOf(AiProvider.OpenAI)
    var isGenerating by mutableStateOf(false)
    var aiStatus by mutableStateOf("本地对话模式")
        private set

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

private fun seedMessages() = listOf(
    ConversationMessage(1, false, "我是Aulune。你可以让我梳理想法、解释问题，或一起制定下一步。", "09:20"),
    ConversationMessage(2, true, "我希望把近期的工作节奏调得更稳一点。", "09:22"),
    ConversationMessage(3, false, "可以。我们先不急着新增任务，先识别哪一部分最消耗你的注意力，再为它设计一个更轻的默认动作。", "09:22")
)
