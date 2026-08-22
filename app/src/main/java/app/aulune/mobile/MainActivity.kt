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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.AccountCircle
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    Library("内容库", Icons.Outlined.Bookmark),
    Compass("画像", Icons.Outlined.PersonOutline),
    Talk("对话", Icons.Outlined.ChatBubbleOutline),
    Models("模型", Icons.Outlined.Settings)
}

@Composable
private fun AuluneApp() {
    val appContext = LocalContext.current.applicationContext
    val store = remember { AuluneStore(appContext) }
    val llmClient = remember { LlmClient() }
    val localFeedViewModel: LocalFeedViewModel = viewModel()
    val localLibraryViewModel: LocalLibraryViewModel = viewModel()
    val localConversationViewModel: LocalConversationViewModel = viewModel()
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
                    AppTab.Focus -> FocusScreen(localFeedViewModel)
                    AppTab.Library -> LibraryScreen(localLibraryViewModel)
                    AppTab.Compass -> CompassScreen(localFeedViewModel)
                    AppTab.Talk -> TalkScreen(
                        store = store,
                        client = llmClient,
                        conversationViewModel = localConversationViewModel,
                        onOpenModels = { tabIndex = AppTab.Models.ordinal }
                    )
                    AppTab.Models -> ModelSettingsScreen(store, localFeedViewModel)
                }
            }
        }
    }
}

@Composable
private fun FocusScreen(viewModel: LocalFeedViewModel) {
    val context = LocalContext.current
    val localState by viewModel.uiState.collectAsState()
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
                StatusPill("当前视角 · ${localState.activeLens}", SoftViolet, Violet)
                StatusPill("已保存 ${localState.savedCount} 条", Color(0xFFE8F7FC), Cyan)
                StatusPill("反馈 ${localState.feedbackCount} 条", Color(0xFFFFF3E6), Color(0xFFD86A20))
                StatusPill("模式 · ${localState.intent.label}", Color(0xFFEFF8EF), Color(0xFF277A45))
                StatusPill("本地优先", Color(0xFFF1F0F5), Muted)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("本机信息流模式", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SessionIntent.entries.forEach { intent ->
                        val selected = localState.intent == intent
                        TextButton(
                            onClick = { viewModel.setSessionIntent(intent) },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Violet else Muted)
                        ) { Text(if (selected) "● ${intent.label}" else intent.label, fontSize = 12.sp) }
                    }
                }
                Text(localState.intent.description, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(
                    onClick = { viewModel.importBilibiliPublicContent() },
                    enabled = !localState.isBilibiliImporting,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (localState.isBilibiliImporting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Explore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入 B 站公开热门内容", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(localState.bilibiliStatus, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                // 多平台内容导入与账号管理
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.importAllPlatformsPublic() },
                        enabled = !localState.isPlatformSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        if (localState.isPlatformSyncing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("全平台导入", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = { context.startActivity(Intent(context, MultiPlatformLoginActivity::class.java)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047), contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("多平台账号", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("来源状态", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.refreshPlatformStatuses() }) { Text("刷新", fontSize = 12.sp) }
                }
                if (localState.platformLoginStatus.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7FB), RoundedCornerShape(8.dp)).padding(8.dp)) {
                        localState.platformLoginStatus.forEach { (platform, status) ->
                            Text("${platform.shortLabel}: $status", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
                // 多平台同步状态
                if (localState.platformSyncStatus.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(8.dp)) {
                        localState.platformSyncStatus.forEach { (platform, status) ->
                            Text("${platform.shortLabel}: $status", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
                BackgroundDiscoveryCard(
                    state = localState.backgroundDiscovery,
                    onRunNow = viewModel::runBackgroundDiscoveryNow
                )
                Text("云端 AI：${localState.cloudAi.status}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(
                    onClick = { viewModel.rotateFeed() },
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("按本机画像重排", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { FocusPromptCard() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("今日输入", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("${localState.items.size} 条", color = Muted, fontSize = 13.sp)
            }
        }
        item { Text(localState.explanation, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
        items(localState.items, key = { it.id }) { item ->
            CuratedItemCard(
                item = item,
                onOpen = {
                    viewModel.recordOpen(item)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                },
                onMark = { viewModel.toggleMarked(item) },
                onSave = { viewModel.toggleSaved(item) },
                onPositive = { viewModel.setPositiveFeedback(item) },
                onNegative = { viewModel.setNegativeFeedback(item) },
                onAnalyzeCloud = { viewModel.analyzeItemWithCloudAi(item) }
            )
        }
        item { Text("B 站公开内容、收藏、推荐理由与行为事件仅保存在这台手机。Aulune不读取、导出或同步官方登录 Cookie。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
internal fun BackgroundDiscoveryCard(
    state: BackgroundDiscoveryUiState,
    onRunNow: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("手动来源探测", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("仅在你点击后联网 · 仅探测公开来源", color = Color(0xFF277A45), fontSize = 11.sp)
                }
            }
            Text(state.notice, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Button(
                onClick = onRunNow,
                enabled = !state.isRunning,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F4A63)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRunning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("立即探测公开来源", fontSize = 13.sp)
            }
            if (state.sources.isNotEmpty()) {
                Text("最近来源可用性", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                state.sources.forEach { source ->
                    val tone = when (source.state) {
                        SourceAvailabilityState.Available -> Color(0xFF277A45)
                        SourceAvailabilityState.Degraded -> Color(0xFFD86A20)
                        SourceAvailabilityState.Unavailable -> Color(0xFFC62828)
                    }
                    Text("${source.platform.shortLabel} · ${source.state.label} · ${source.detail}", color = tone, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            state.recentTasks.firstOrNull()?.let { task ->
                Text("最近任务：${task.kind.label} · ${task.status.label} · ${task.detail}", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
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
private fun CuratedItemCard(
    item: CuratedItem,
    onOpen: () -> Unit,
    onMark: () -> Unit,
    onSave: () -> Unit,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    onAnalyzeCloud: () -> Unit
) {
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
                    Text("${item.theme} · ${item.lifecycle.label}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    TextButton(onClick = onPositive, modifier = Modifier.height(34.dp)) { Text("喜欢", fontSize = 11.sp) }
                    TextButton(onClick = onNegative, modifier = Modifier.height(34.dp)) { Text("不感兴趣", color = Muted, fontSize = 11.sp) }
                    TextButton(onClick = onAnalyzeCloud, modifier = Modifier.height(34.dp)) { Text("AI解析", color = Cyan, fontSize = 11.sp) }
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
private fun LibraryScreen(viewModel: LocalLibraryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("本地内容库", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("保存、标记、最近打开和隐藏内容都只保留在这台手机。", color = Muted, fontSize = 13.sp)
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibrarySection.entries.forEach { section ->
                    val selected = section == state.section
                    Surface(
                        color = if (selected) Violet else Color.White,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.clickable { viewModel.select(section) }
                    ) {
                        Text(
                            section.label,
                            color = if (selected) Color.White else Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("稍后 ${state.totalSaved}", Color(0xFFE8F7FC), Cyan)
                StatusPill("标记 ${state.totalMarked}", SoftViolet, Violet)
                StatusPill("隐藏 ${state.totalHidden}", Color(0xFFF1F0F5), Muted)
            }
        }
        if (state.items.isEmpty()) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                    Text(state.emptyMessage, color = Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(18.dp))
                }
            }
        } else {
            items(state.items, key = { it.contentKey }) { item ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.source, color = Violet, fontSize = 12.sp)
                        Text(item.summary.ifBlank { "暂无摘要" }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                if (item.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            }) { Text("打开") }
                            TextButton(onClick = { viewModel.toggleSaved(item) }) { Text(if (item.saved) "移出稍后" else "保存") }
                            TextButton(onClick = { viewModel.toggleMarked(item) }) { Text(if (item.marked) "取消标记" else "标记") }
                            TextButton(onClick = {
                                if (item.hidden) viewModel.restore(item) else viewModel.hide(item)
                            }) { Text(if (item.hidden) "恢复" else "隐藏", color = Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassScreen(viewModel: LocalFeedViewModel) {
    val localState by viewModel.uiState.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("你的本机画像", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("它由你在这台手机上的打开、标记和收藏行为生成；你可以随时用新的行为改变它。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        item { PortraitCard(localState) }
        item {
            Button(
                onClick = { viewModel.buildCloudProfileCandidate() },
                enabled = !localState.cloudAi.isWorking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) { Text("用 ${localState.cloudAi.provider.displayName} 更新长期画像候选") }
        }
        item { Text(localState.cloudAi.status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
        item { SectionTitle("分层本机画像") }
        if (localState.profiles.isEmpty()) {
            item { Text("正在从本机事件生成画像层…", color = Muted, fontSize = 13.sp) }
        } else {
            items(localState.profiles, key = { it.layer.name }) { profile ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.layer.label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                when (profile.confirmationState) {
                                    "confirmed" -> "已确认"
                                    "pending" -> "等待确认"
                                    else -> "自动更新"
                                },
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Text(profile.summary, color = Color(0xFF4E495F), fontSize = 13.sp, lineHeight = 19.sp)
                        if (profile.candidate.isBlank() && (profile.layer == ProfileLayer.Values || profile.layer == ProfileLayer.Core)) {
                            TextButton(onClick = { viewModel.resetProfileLayer(profile.layer) }) {
                                Text("重新观察", color = Muted, fontSize = 12.sp)
                            }
                        }
                        if (profile.candidate.isNotBlank()) {
                            Surface(color = SoftViolet, shape = RoundedCornerShape(12.dp)) {
                                Text(profile.candidate, color = Ink, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(11.dp))
                            }
                            if (profile.layer == ProfileLayer.Values || profile.layer == ProfileLayer.Core) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { viewModel.confirmProfileLayer(profile.layer) }) {
                                        Text("确认写入本机${profile.layer.label}", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { viewModel.resetProfileLayer(profile.layer) }) {
                                        Text("重新观察", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("已积累的兴趣证据") }
        if (localState.interests.isEmpty()) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Text("先在“灵感”页打开、标记或保存内容。Aulune不会为你预设人格标签，兴趣画像只基于本机行为生成。", color = Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(17.dp))
                }
            }
        } else {
            items(localState.interests, key = { it.theme }) { interest ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(Violet, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(interest.theme, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${interest.lifecycle.label} · ${interest.evidenceCount} 条证据", color = Muted, fontSize = 12.sp)
                        }
                        Text("本机兴趣强度 ${String.format(java.util.Locale.US, "%.1f", interest.weight)}。新主题先观察；持续正向行为会激活，长期无新证据会降温，明确不喜欢会归档并进入避雷。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        item { Text("P2/P3 不调用云端画像服务，也未加入端侧 Embedding 或本地 AI 模型。主题归并、系列识别、重排和分层画像均由手机内的确定性规则完成。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun PortraitCard(localState: LocalFeedUiState) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SoftViolet)) {
        Column(Modifier.padding(19.dp)) {
            Text("从行为中逐步形成，而不是被预先定义", color = Color(0xFF49329A), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(9.dp))
            Text(localState.explanation, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("本机存储", Color.White, Violet)
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
private fun TalkScreen(
    store: AuluneStore,
    client: LlmClient,
    conversationViewModel: LocalConversationViewModel,
    onOpenModels: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val messages by conversationViewModel.messages.collectAsState()
    val isGenerating by conversationViewModel.isGenerating.collectAsState()
    val status by conversationViewModel.status.collectAsState()
    val activeSettings = store.settingsFor(store.selectedProvider)
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        if (draft.isBlank() || isGenerating) return
        conversationViewModel.send(draft, store.selectedProvider, activeSettings, client)
        draft = ""
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("和想法一起工作", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("对话会保存在本机；模型只在你配置并发送后调用。", color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = { conversationViewModel.clear() }) { Text("清空", color = Muted) }
            }
        }
        ProviderBar(provider = store.selectedProvider, configured = activeSettings.apiKey.isNotBlank(), status = status, onClick = onOpenModels)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            if (isGenerating) item { ThinkingBubble(store.selectedProvider.displayName) }
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
            IconButton(onClick = { submit() }, enabled = !isGenerating, modifier = Modifier.size(46.dp).clip(CircleShape).background(if (draft.isBlank() || isGenerating) Color(0xFFE8E7EE) else Violet)) {
                Icon(Icons.Outlined.Send, contentDescription = "发送", tint = if (draft.isBlank() || isGenerating) Muted else Color.White)
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
private fun ModelSettingsScreen(store: AuluneStore, localFeedViewModel: LocalFeedViewModel) {
    val localState by localFeedViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var editingProvider by rememberSaveable { mutableStateOf(store.selectedProvider) }
    var apiKey by rememberSaveable(editingProvider) { mutableStateOf(store.settingsFor(editingProvider).apiKey) }
    var model by rememberSaveable(editingProvider) { mutableStateOf(store.settingsFor(editingProvider).effectiveModel(editingProvider)) }
    var baseUrl by rememberSaveable(editingProvider) { mutableStateOf(store.settingsFor(editingProvider).effectiveBaseUrl(editingProvider)) }
    var protocol by rememberSaveable(editingProvider) { mutableStateOf(store.settingsFor(editingProvider).effectiveProtocol(editingProvider)) }
    var remoteModels by rememberSaveable(editingProvider) { mutableStateOf(emptyList<String>()) }
    var modelStatus by rememberSaveable(editingProvider) { mutableStateOf("可手动填写模型名，或用 API Key 获取列表。") }
    var isLoadingModels by rememberSaveable(editingProvider) { mutableStateOf(false) }
    val configured = store.providerSettings.filterValues { it.apiKey.isNotBlank() }.keys

    fun switchProvider(provider: AiProvider) {
        editingProvider = provider
        val saved = store.settingsFor(provider)
        apiKey = saved.apiKey
        model = saved.effectiveModel(provider)
        baseUrl = saved.effectiveBaseUrl(provider)
        protocol = saved.effectiveProtocol(provider)
        remoteModels = emptyList()
        modelStatus = "可手动填写模型名，或用 API Key 获取列表。"
        store.selectProvider(provider)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("模型工作台", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("所有服务商都可编辑 HTTPS 接口地址、选择协议、在线获取模型并手动填写模型名。配置与 Key 使用 Android Keystore 加密保存在本机。", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        item { BilibiliAccountConnectorCard(localFeedViewModel) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    val selected = provider == editingProvider
                    Surface(
                        color = if (selected) Violet else Color.White,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.clickable { switchProvider(provider) }
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
                        Column(Modifier.weight(1f)) {
                            Text(editingProvider.displayName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(if (editingProvider in configured) "已保存，可用于对话" else "尚未保存", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, placeholder = { Text(editingProvider.keyHint) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("接口基础地址（可修改）") },
                        placeholder = { Text(editingProvider.defaultBaseUrl) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("调用协议", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ProviderProtocol.entries.forEach { candidate ->
                            val selected = protocol == candidate
                            Surface(
                                color = if (selected) SoftViolet else Color(0xFFF6F5FA),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { protocol = candidate }
                            ) { Text(candidate.label, color = if (selected) Violet else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = model, onValueChange = { model = it }, label = { Text("模型名称（可手动填写）") },
                        placeholder = { Text(editingProvider.defaultModel.ifBlank { "例如 provider/model-name" }) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(9.dp))
                    Button(
                        onClick = {
                            isLoadingModels = true
                            modelStatus = "正在获取模型列表…"
                            val requestSettings = ProviderSettings(apiKey.trim(), model.trim(), baseUrl.trim(), protocol)
                            scope.launch {
                                LlmClient().listModels(editingProvider, requestSettings)
                                    .onSuccess { models ->
                                        remoteModels = models.map { it.id }
                                        modelStatus = if (models.isEmpty()) "服务商未返回可用模型；仍可手动填写模型名。" else "已获取 ${models.size} 个模型，点选即可填入。"
                                    }
                                    .onFailure { error ->
                                        remoteModels = emptyList()
                                        modelStatus = "获取失败：${error.message ?: "请检查 Key、地址和协议。"}"
                                    }
                                isLoadingModels = false
                            }
                        },
                        enabled = !isLoadingModels,
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F4A63)), modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoadingModels) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("获取模型列表")
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(modelStatus, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (remoteModels.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            remoteModels.forEach { item ->
                                Surface(color = Color(0xFFF6F5FA), shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { model = item }) {
                                    Text(item, color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Button(
                        onClick = {
                            val saved = ProviderSettings(apiKey.trim(), model.trim(), baseUrl.trim(), protocol)
                            store.setProviderSettings(editingProvider, saved)
                            localFeedViewModel.saveCloudAiConfig(
                                provider = editingProvider,
                                apiKey = saved.apiKey,
                                model = saved.effectiveModel(editingProvider),
                                baseUrl = saved.effectiveBaseUrl(editingProvider),
                                protocol = saved.effectiveProtocol(editingProvider),
                                enable = true
                            )
                            modelStatus = "已保存 ${editingProvider.displayName} 配置。"
                        },
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()
                    ) { Text("保存并启用 ${editingProvider.displayName}") }
                    Spacer(Modifier.height(9.dp))
                    Text("当前云端增强：${localState.cloudAi.status}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { localFeedViewModel.disableCloudAi() }) { Text("仅使用本机规则") }
                        TextButton(onClick = { localFeedViewModel.clearCloudAiKey() }) { Text("清除云端增强 Key", color = Muted) }
                    }
                }
            }
        }
        item { SectionTitle("协议说明") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModelCapabilityRow("OpenAI 兼容", "OpenAI、DeepSeek、智谱、Kimi、OpenRouter 与多数自定义网关")
                    HorizontalDivider(color = Color(0xFFF0EFF5))
                    ModelCapabilityRow("Anthropic", "Claude Messages API")
                    HorizontalDivider(color = Color(0xFFF0EFF5))
                    ModelCapabilityRow("Gemini", "Google GenerateContent API")
                }
            }
        }
        item {
            Surface(color = Color(0xFFE8F7FC), shape = RoundedCornerShape(16.dp)) {
                Text("隐私说明：云端 AI 只在你主动发送对话、点击“AI解析”或更新长期画像候选时调用。内容分析发送标题、摘要、来源和当前主题；画像生成只发送聚合兴趣和事件数。不会发送 B 站 Cookie、账号令牌或原始观看记录。", color = Color(0xFF24536B), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun BilibiliAccountConnectorCard(localFeedViewModel: LocalFeedViewModel) {
    val context = LocalContext.current
    val localState by localFeedViewModel.uiState.collectAsState()
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF7FF))) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFD7F0FF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Explore, contentDescription = null, tint = Cyan, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("B 站账号连接", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("扫码、密码、短信与会话入口", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            Text("先在官方页面登录，再点击“授权同步”；Aulune只读账号信息、收藏夹、观看历史和稍后再看，不执行点赞、收藏或删除操作。", color = Color(0xFF24536B), fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { context.startActivity(Intent(context, BilibiliLoginActivity::class.java)) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                modifier = Modifier.fillMaxWidth()
            ) { Text("选择登录方式") }
            val profile = localState.bilibiliProfile
            if (profile != null) {
                Spacer(Modifier.height(8.dp))
                Text("已授权：${profile.name}（Lv.${profile.level}）", color = Color(0xFF24536B), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { localFeedViewModel.syncBilibiliAccount() },
                enabled = !localState.isBilibiliImporting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (localState.isBilibiliImporting) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("读取已授权账户数据")
            }
            Text(localState.bilibiliStatus, color = Color(0xFF24536B), fontSize = 12.sp, lineHeight = 18.sp)
            TextButton(
                onClick = { localFeedViewModel.clearBilibiliLocalData() },
                modifier = Modifier.align(Alignment.End)
            ) { Text("删除本机 B 站数据", color = Color(0xFFB3261E)) }
            TextButton(
                onClick = { context.startActivity(BilibiliWebActivity.createIntent(context, BilibiliDestination.Account)) },
                modifier = Modifier.align(Alignment.End)
            ) { Text("打开官方账号中心并授权") }
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
