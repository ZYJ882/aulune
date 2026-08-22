package app.aulune.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Ink = Color(0xFF171427)
private val Muted = Color(0xFF716C84)
private val Violet = Color(0xFF7857FF)
private val Cyan = Color(0xFF1CA4D8)
private val Canvas = Color(0xFFF8F7FF)
private val SoftViolet = Color(0xFFEDE9FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuluneApp() }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    Focus("灵感", Icons.Outlined.Home),
    Compass("画像", Icons.Outlined.PersonOutline),
    Talk("对话", Icons.Outlined.ChatBubbleOutline),
    Models("模型", Icons.Outlined.Settings)
}

@Composable
private fun AuluneApp() {
    val store = remember { AuluneStore() }
    val llmClient = remember { LlmClient() }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Violet,
            secondary = Cyan,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Violet,
                                selectedTextColor = Ink,
                                indicatorColor = SoftViolet
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (AppTab.entries[tabIndex]) {
                    AppTab.Focus -> FocusScreen(store)
                    AppTab.Compass -> CompassScreen()
                    AppTab.Talk -> TalkScreen(store, llmClient, onOpenModels = { tabIndex = AppTab.Models.ordinal })
                    AppTab.Models -> ModelSettingsScreen(store)
                }
            }
        }
    }
}

@Composable
private fun FocusScreen(store: AuluneStore) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Aulune", color = Violet, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("留给思考的输入", color = Ink, fontWeight = FontWeight.Bold, fontSize = 27.sp)
                }
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Violet, Cyan))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Aulune", tint = Color.White) }
            }
        }
        item { Text("少一点噪声，多一些能帮助你判断、创造和行动的内容。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("当前视角 · ${store.activeLens}", SoftViolet, Violet)
                StatusPill("已保存 ${store.savedCount} 条", Color(0xFFE8F7FC), Cyan)
                StatusPill("本地优先", Color(0xFFF1F0F5), Muted)
            }
        }
        item {
            Button(
                onClick = { store.rotateFeed() },
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (store.isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("换一组灵感", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { FocusPromptCard() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("今日输入", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("${store.items.size} 条", color = Muted, fontSize = 13.sp)
            }
        }
        items(store.items, key = { it.id }) { item ->
            CuratedItemCard(
                item = item,
                onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) },
                onMark = { store.toggleMark(item) },
                onSave = { store.toggleSaved(item) }
            )
        }
        item { Text("内容卡片与收藏操作仅保留在当前本地会话。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun StatusPill(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
            Box(Modifier.size(6.dp).background(foreground, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 12.sp, color = Ink, maxLines = 1)
        }
    }
}

@Composable
private fun FocusPromptCard() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(19.dp)) {
            Text("给自己一个问题", color = Color(0xFFC8BCFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("如果今天只能推进一件事，哪件事会让后续工作变得更轻？", color = Color.White, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(11.dp))
            Text("打开“对话”，让任意已配置的模型陪你把答案拆开。", color = Color(0xFFD2D0DE), fontSize = 13.sp)
        }
    }
}

@Composable
private fun CuratedItemCard(item: CuratedItem, onOpen: () -> Unit, onMark: () -> Unit, onSave: () -> Unit) {
    val start = Color(item.gradientStart)
    val end = Color(item.gradientEnd)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(13.dp)) {
            Box(
                modifier = Modifier.size(width = 104.dp, height = 127.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(start, end))).clickable { onOpen() },
                contentAlignment = Alignment.BottomStart
            ) {
                Text(item.channel.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.fillMaxHeight().weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(item.channel)
                    Spacer(Modifier.width(7.dp))
                    Text(item.theme, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(7.dp))
                Text(item.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text("${item.source} · ${item.readTime}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(item.insight, color = Color(0xFF4E495F), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMark, modifier = Modifier.size(34.dp)) {
                        Icon(if (item.marked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "标记", tint = if (item.marked) Violet else Muted, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                        Icon(if (item.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "保存", tint = if (item.saved) Violet else Muted, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpen, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "打开", tint = Ink, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(channel: SourceChannel) {
    Surface(color = Color(channel.accent).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(channel.label, color = Color(channel.accent), fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun CompassScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("你的思考地图", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("它不是给你贴标签，而是帮助你看见稳定的偏好与正在发生的变化。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        item { PortraitCard() }
        item { SectionTitle("长期偏好") }
        item { InsightList(listOf("把复杂问题拆成可以验证的结构。", "看重节奏和可持续性，而不是短暂冲刺。", "希望工具减少摩擦，并真正服务于创造。")) }
        item { SectionTitle("当前关注") }
        item { InterestCard() }
        item { SectionTitle("思考方式") }
        item { ThinkingCard() }
        item { Text("当你愿意时，可以在“对话”中说“这不准确”或补充新的方向；这张地图会随你的输入持续更新。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun PortraitCard() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SoftViolet)) {
        Column(Modifier.padding(19.dp)) {
            Text("有选择地探索，也愿意停下来沉淀", color = Color(0xFF49329A), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(9.dp))
            Text("你会主动靠近新想法，但最终会留下能帮助理解世界、形成作品或建立稳定系统的内容。", color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("可信度 82%", Color.White, Violet)
                StatusPill("可随时修正", Color.White, Cyan)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 3.dp))

@Composable
private fun InsightList(items: List<String>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            items.forEach { text ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(7.dp).background(Violet, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun InterestCard() {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InterestRow("创造系统", "知识管理、写作、个人工具", Violet)
            HorizontalDivider(color = Color(0xFFF0EFF5))
            InterestRow("清晰表达", "结构化写作、产品叙事、深度解释", Cyan)
            HorizontalDivider(color = Color(0xFFF0EFF5))
            InterestRow("长期主义", "商业、技术史、深度访谈", Color(0xFFDB6E32))
        }
    }
}

@Composable
private fun InterestRow(title: String, body: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column { Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun ThinkingCard() {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("结构化探索", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("稳定", color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            DimensionBar("先理解", "先行动", 0.72f)
            DimensionBar("深度输入", "广度输入", 0.76f)
            DimensionBar("独立推演", "协作讨论", 0.63f)
        }
    }
}

@Composable
private fun DimensionBar(left: String, right: String, value: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(left, color = Muted, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(right, color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0xFFE9E7F2))) { Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(Violet)) }
    }
}

@Composable
private fun TalkScreen(store: AuluneStore, client: LlmClient, onOpenModels: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val activeSettings = store.providerSettings[store.selectedProvider] ?: ProviderSettings(model = store.selectedProvider.defaultModel)
    LaunchedEffect(store.messages.size, store.isGenerating) { if (store.messages.isNotEmpty()) listState.animateScrollToItem(store.messages.lastIndex) }

    fun submit() {
        val input = draft.trim()
        if (input.isBlank() || store.isGenerating) return
        if (activeSettings.apiKey.isBlank()) {
            store.addAssistantMessage("请先打开“模型”页，选择 ${store.selectedProvider.displayName} 并填写 API Key。密钥仅保留在当前应用会话中。")
            return
        }
        store.addUserMessage(input)
        draft = ""
        scope.launch {
            store.updateGenerating(true)
            client.generate(store.selectedProvider, activeSettings, store.messages.toList())
                .onSuccess { answer ->
                    store.addAssistantMessage(answer)
                    store.updateAiStatus("${store.selectedProvider.displayName} 已完成")
                }
                .onFailure { error ->
                    store.addAssistantMessage("调用失败：${error.message ?: "请检查网络、Key 和模型名称。"}")
                    store.updateAiStatus("调用未成功")
                }
            store.updateGenerating(false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 10.dp)) {
            Text("和想法一起工作", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("选择一个模型，把模糊的念头变成下一步。", color = Muted, fontSize = 13.sp)
        }
        ProviderBar(provider = store.selectedProvider, configured = activeSettings.apiKey.isNotBlank(), status = store.aiStatus, onClick = onOpenModels)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(store.messages, key = { it.id }) { message -> MessageBubble(message) }
            if (store.isGenerating) item { ThinkingBubble(store.selectedProvider.displayName) }
        }
        HorizontalDivider(color = Color(0xFFE9E8EF))
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).imePadding().padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("输入你的问题…", color = Muted) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                minLines = 1, maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { submit() }, enabled = !store.isGenerating, modifier = Modifier.size(46.dp).clip(CircleShape).background(if (draft.isBlank() || store.isGenerating) Color(0xFFE8E7EE) else Violet)) {
                Icon(Icons.Outlined.Send, contentDescription = "发送", tint = if (draft.isBlank() || store.isGenerating) Muted else Color.White)
            }
        }
    }
}

@Composable
private fun ProviderBar(provider: AiProvider, configured: Boolean, status: String, onClick: () -> Unit) {
    Surface(color = SoftViolet, shape = RoundedCornerShape(13.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (configured) Color(0xFF27AE60) else Color(0xFFB9B4C8), CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { Text(provider.displayName, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(status, color = Muted, fontSize = 11.sp) }
            Text("切换", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(0.84f)) {
            Surface(
                color = if (message.fromUser) Violet else Color.White,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (message.fromUser) 18.dp else 4.dp, bottomEnd = if (message.fromUser) 4.dp else 18.dp),
                shadowElevation = if (message.fromUser) 0.dp else 1.dp
            ) { Text(message.text, color = if (message.fromUser) Color.White else Ink, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) }
            Spacer(Modifier.height(4.dp)); Text(message.time, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ThinkingBubble(provider: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Violet, strokeWidth = 2.dp)
            Spacer(Modifier.width(9.dp)); Text("$provider 正在思考…", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ModelSettingsScreen(store: AuluneStore) {
    var editingProvider by rememberSaveable { mutableStateOf(store.selectedProvider) }
    var apiKey by rememberSaveable(editingProvider) { mutableStateOf(store.providerSettings[editingProvider]?.apiKey.orEmpty()) }
    var model by rememberSaveable(editingProvider) { mutableStateOf(store.providerSettings[editingProvider]?.model ?: editingProvider.defaultModel) }
    val configured = store.providerSettings.keys

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("模型工作台", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("接入你自己的 API Key。密钥不会写入安装包，也不会上传到任何中转服务。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    val selected = provider == editingProvider
                    Surface(
                        color = if (selected) Violet else Color.White,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.clickable {
                            editingProvider = provider
                            apiKey = store.providerSettings[provider]?.apiKey.orEmpty()
                            model = store.providerSettings[provider]?.model ?: provider.defaultModel
                        }
                    ) { Text(provider.displayName, color = if (selected) Color.White else Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(SoftViolet), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column { Text(editingProvider.displayName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(if (editingProvider in configured) "已配置，可用于对话" else "尚未配置", color = Muted, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, placeholder = { Text(editingProvider.keyHint) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(11.dp))
                    OutlinedTextField(
                        value = model, onValueChange = { model = it }, label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("可按你的账号权限填写模型名称；默认值只是可修改的起点。", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { store.setProviderSettings(editingProvider, ProviderSettings(apiKey = apiKey.trim(), model = model.trim())) },
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()
                    ) { Text("启用 ${editingProvider.displayName}") }
                }
            }
        }
        item { SectionTitle("支持的接口") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModelCapabilityRow("OpenAI", "Chat Completions")
                    HorizontalDivider(color = Color(0xFFF0EFF5))
                    ModelCapabilityRow("Claude", "Messages API")
                    HorizontalDivider(color = Color(0xFFF0EFF5))
                    ModelCapabilityRow("Gemini", "Generate Content")
                    HorizontalDivider(color = Color(0xFFF0EFF5))
                    ModelCapabilityRow("DeepSeek", "Chat Completions")
                }
            }
        }
        item {
            Surface(color = Color(0xFFE8F7FC), shape = RoundedCornerShape(16.dp)) {
                Text("安全说明：当前版本将 Key 保留在应用内存中；退出应用后会清除。请求会从你的设备通过 HTTPS 直连相应模型服务。", color = Color(0xFF24536B), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun ModelCapabilityRow(name: String, protocol: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f)); Text(protocol, color = Muted, fontSize = 12.sp)
    }
}
