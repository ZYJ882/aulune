package app.aulune.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** A user-facing local collection. All operations stay on this device. */
enum class LibrarySection(val label: String) {
    Saved("稍后"),
    Marked("标记"),
    Recent("最近打开"),
    Hidden("已隐藏")
}

data class LocalLibraryUiState(
    val section: LibrarySection = LibrarySection.Saved,
    val items: List<LocalContentEntity> = emptyList(),
    val totalSaved: Int = 0,
    val totalMarked: Int = 0,
    val totalHidden: Int = 0,
    val emptyMessage: String = "这里还没有内容。"
)

internal fun filterLibraryContent(
    section: LibrarySection,
    content: List<LocalContentEntity>,
    events: List<BehaviorEventEntity>
): List<LocalContentEntity> {
    val byKey = content.associateBy { it.contentKey }
    return when (section) {
        LibrarySection.Saved -> content.filter { it.saved && !it.hidden }.sortedByDescending { it.updatedAt }
        LibrarySection.Marked -> content.filter { it.marked && !it.hidden }.sortedByDescending { it.updatedAt }
        LibrarySection.Hidden -> content.filter { it.hidden }.sortedByDescending { it.updatedAt }
        LibrarySection.Recent -> events
            .filter { it.eventType == "open" }
            .mapNotNull { event -> byKey[event.contentKey] }
            .distinctBy { item -> item.contentKey }
    }
}

class LocalLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AuluneLocalDatabase.create(application).localCoreDao()
    private val selection = MutableStateFlow(LibrarySection.Saved)

    val uiState: StateFlow<LocalLibraryUiState> = combine(
        dao.observeAllContent(),
        dao.observeRecentEvents(100),
        selection
    ) { content, events, section ->
        val items = filterLibraryContent(section, content, events)
        LocalLibraryUiState(
            section = section,
            items = items,
            totalSaved = content.count { it.saved && !it.hidden },
            totalMarked = content.count { it.marked && !it.hidden },
            totalHidden = content.count { it.hidden },
            emptyMessage = when (section) {
                LibrarySection.Saved -> "还没有稍后内容；在灵感页点“保存”即可加入。"
                LibrarySection.Marked -> "还没有标记内容；用标记保留值得反复看的线索。"
                LibrarySection.Recent -> "打开一条内容后，它会出现在这里。"
                LibrarySection.Hidden -> "没有隐藏内容。"
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalLibraryUiState())

    fun select(section: LibrarySection) {
        selection.value = section
    }

    fun toggleSaved(item: LocalContentEntity) {
        viewModelScope.launch {
            dao.setSaved(item.contentKey, !item.saved, System.currentTimeMillis())
        }
    }

    fun toggleMarked(item: LocalContentEntity) {
        viewModelScope.launch {
            dao.setMarked(item.contentKey, !item.marked, System.currentTimeMillis())
        }
    }

    fun hide(item: LocalContentEntity) {
        viewModelScope.launch {
            dao.setHidden(item.contentKey, true, System.currentTimeMillis())
        }
    }

    fun restore(item: LocalContentEntity) {
        viewModelScope.launch {
            dao.setHidden(item.contentKey, false, System.currentTimeMillis())
        }
    }
}

class LocalConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AuluneLocalDatabase.create(application).localCoreDao()
    private val diagnostics = AuluneDiagnostics(application)
    private val _isGenerating = MutableStateFlow(false)
    private val _status = MutableStateFlow("本地持久化对话")

    val isGenerating: StateFlow<Boolean> = _isGenerating
    val status: StateFlow<String> = _status
    val messages: StateFlow<List<ConversationMessage>> = dao.observeChatMessages()
        .map { entries -> entries.map(LocalChatMessageEntity::toConversationMessage) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            if (dao.chatMessageCount() == 0) {
                insert(false, "我是 Aulune。你的对话会保存在这台设备上，可用来梳理想法并逐步校准本地兴趣画像。")
            }
        }
    }

    fun send(
        draft: String,
        provider: AiProvider,
        settings: ProviderSettings,
        client: LlmClient
    ) {
        val input = draft.trim()
        if (input.isBlank() || _isGenerating.value) return
        viewModelScope.launch {
            if (settings.apiKey.isBlank()) {
                diagnostics.record("WARN", "对话未发送：${provider.displayName} 尚未配置 API Key")
                insert(false, "请先打开“模型”页，选择 ${provider.displayName} 并填写 API Key。对话记录仍会保留在本机。")
                _status.value = "等待模型配置"
                return@launch
            }
            val history = messages.value + ConversationMessage(
                id = 0L,
                fromUser = true,
                text = input,
                time = nowLabel()
            )
            insert(true, input)
            _isGenerating.value = true
            diagnostics.record("INFO", "用户主动开始对话：provider=${provider.displayName}，model=${settings.effectiveModel(provider)}")
            _status.value = "${provider.displayName} 正在思考"
            client.generate(provider, settings, history)
                .onSuccess { answer ->
                    diagnostics.record("INFO", "对话调用成功：provider=${provider.displayName}")
                    insert(false, answer)
                    _status.value = "${provider.displayName} 已完成"
                }
                .onFailure { error ->
                    diagnostics.record("ERROR", "对话调用失败：${error.message ?: "未知错误"}")
                    insert(false, "调用失败：${error.message ?: "请检查网络、Key 和模型名称。"}")
                    _status.value = "调用未成功"
                }
            _isGenerating.value = false
        }
    }

    fun showStatus(message: String) {
        _status.value = message
    }

    fun clear() {
        viewModelScope.launch {
            dao.clearChatMessages()
            insert(false, "新的本地对话已开始。你可以告诉我想探索的方向，或让我协助整理下一步。")
            _status.value = "已清除本地对话"
        }
    }

    private suspend fun insert(fromUser: Boolean, text: String) {
        dao.insertChatMessage(
            LocalChatMessageEntity(
                fromUser = fromUser,
                text = text.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun nowLabel(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
}

private fun LocalChatMessageEntity.toConversationMessage(): ConversationMessage = ConversationMessage(
    id = id,
    fromUser = fromUser,
    text = text,
    time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAt))
)
