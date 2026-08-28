package app.aulune.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode = ThemeManager.getMode(this).let { remember { mutableStateOf(it) } }
            val dynamicColor = ThemeManager.isDynamicColorEnabled(this).let { remember { mutableStateOf(it) } }
            AuluneTheme(
                themeMode = themeMode.value,
                dynamicColor = dynamicColor.value,
            ) {
                AuluneApp(
                    onThemeModeChange = { mode ->
                        themeMode.value = mode
                        ThemeManager.setMode(this@MainActivity, mode)
                    },
                    onDynamicColorChange = { enabled ->
                        dynamicColor.value = enabled
                        ThemeManager.setDynamicColorEnabled(this@MainActivity, enabled)
                    },
                )
            }
        }
    }


    companion object {
        fun createIntent(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java)
    }
}

// ═══════════════════════════════════════════════════════════════
//  底部导航
// ═══════════════════════════════════════════════════════════════

private enum class AppTab(val label: String, val icon: ImageVector) {
    Focus("灵感", Icons.Outlined.Lightbulb),
    Library("内容库", Icons.Outlined.Bookmarks),
    Compass("画像", Icons.Outlined.Person),
    Talk("对话", Icons.AutoMirrored.Outlined.Chat),
    Models("设置", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuluneApp(
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AuluneStore(context) }
    val llmClient = remember { LlmClient() }
    val localFeedViewModel: LocalFeedViewModel = viewModel()
    val libraryViewModel: LocalLibraryViewModel = viewModel()
    val conversationViewModel: LocalConversationViewModel = viewModel()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val navigateTo: (AppTab) -> Unit = { destination ->
        tabIndex = AppTab.entries.indexOf(destination)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 0.dp,
            ) {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { navigateTo(tab) },
                        icon = {
                            AnimatedContent(
                                targetState = tabIndex == index,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.85f, animationSpec = tween(150)) + fadeIn())
                                        .togetherWith(scaleOut(targetScale = 0.85f, animationSpec = tween(150)) + fadeOut())
                                },
                                label = "nav_icon",
                            ) { selected ->
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(220)) { -it / 4 } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = tween(220)) { -it / 4 } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(220)) { it / 4 } + fadeOut()
                    }
                },
                label = "tab_switch",
            ) { index ->
                when (AppTab.entries[index]) {
                    AppTab.Focus -> FocusScreen(localFeedViewModel)
                    AppTab.Library -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onNavigateToFocus = { navigateTo(AppTab.Focus) },
                    )
                    AppTab.Compass -> CompassScreen(
                        viewModel = localFeedViewModel,
                        onNavigateToSettings = { navigateTo(AppTab.Models) },
                    )
                    AppTab.Talk -> TalkScreen(
                        store = store,
                        client = llmClient,
                        conversationViewModel = conversationViewModel,
                        localFeedViewModel = localFeedViewModel,
                        onNavigateToSettings = { navigateTo(AppTab.Models) },
                    )
                    AppTab.Models -> SettingsScreen(
                        store = store,
                        localFeedViewModel = localFeedViewModel,
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  首页（灵感）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun FocusScreen(viewModel: LocalFeedViewModel) {
    val context = LocalContext.current
    val localState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptic = remember { HapticFeedback(context) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Aulune", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text("留给思考的输入", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        item {
            Text(
                "少一点噪声，多一些能帮助你判断、创造和行动的内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill("视角 · ${localState.activeLens}")
                StatusPill("已保存 ${localState.savedCount}")
                StatusPill("反馈 ${localState.feedbackCount}")
                StatusPill("模式 · ${localState.intent.label}")
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("内容来源", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SessionIntent.entries.forEach { intent ->
                            val selected = localState.intent == intent
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setSessionIntent(intent); haptic.click() },
                                label = { Text(intent.label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    Text(
                        "点击“获取内容”会手动导入 B 站公开热门与其他公开来源；不读取账号 Cookie。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { viewModel.importAllPlatformsPublic(); haptic.confirm() },
                        enabled = !localState.isPlatformSyncing,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (localState.isPlatformSyncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在获取公开内容…", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(Icons.Outlined.Explore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("获取内容", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(context, MultiPlatformLoginActivity::class.java)); haptic.click() },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("管理与同步我的账号", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "账号同步由你手动发起，可将收藏、观看历史和稍后再看作为本机推荐证据。“获取内容”只读取公开内容，不读取 Cookie。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    if (localState.bilibiliStatus.isNotBlank()) {
                        Text(localState.bilibiliStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (localState.platformSyncStatus.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            localState.platformSyncStatus.forEach { (platform, status) ->
                                Text("${platform.shortLabel}: $status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            ProfileGuidedExploreCard(
                plan = localState.profileExplorePlan,
                isRunning = localState.backgroundDiscovery.isRunning,
                onRun = viewModel::runProfileGuidedDiscovery,
            )
        }
        item { FocusPromptCard() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("为你推荐", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        localState.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("${localState.items.size} 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (localState.items.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "还没有内容",
                    description = "点击上方“获取内容”手动导入 B 站公开热门与其他公开来源，信息流会根据你的本机画像自动排序。",
                    actionLabel = "获取内容",
                    onAction = { viewModel.importAllPlatformsPublic() },
                )
            }
        } else {
            items(localState.items, key = { it.id }) { item ->
                CuratedItemCard(
                    item = item,
                    onOpen = {
                        viewModel.recordOpen(item)
                        haptic.click()
                        ExternalUrlPolicy.viewIntent(item.url)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    onMark = { viewModel.toggleMarked(item); haptic.confirm() },
                    onSave = { viewModel.toggleSaved(item); haptic.confirm() },
                    onPositive = { viewModel.setPositiveFeedback(item); haptic.click() },
                    onNegative = { viewModel.setNegativeFeedback(item); haptic.click() },
                    onAnalyzeCloud = { viewModel.analyzeItemWithCloudAi(item) },
                )
            }
        }
        item {
            Text(
                "内容、收藏、推荐理由与行为事件仅保存在这台手机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FocusPromptCard() {
    val prompts = remember {
        listOf(
            "如果今天只能推进一件事，哪件事会让后续工作变得更轻？",
            "最近有什么想法一直在脑海里反复出现？它在提醒你什么？",
            "你最近收藏但没看完的内容里，哪一条最值得现在花 10 分钟？",
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { index = (index + 1) % prompts.size },
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("给自己一个问题", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn() togetherWith
                        slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut()
                },
                label = "prompt_switch",
            ) { i ->
                Text(
                    prompts[i],
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("点击卡片切换问题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
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
    onAnalyzeCloud: () -> Unit,
) {
    val start = Color(item.gradientStart)
    val end = Color(item.gradientEnd)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
    ) {
        Row(Modifier.padding(14.dp)) {
            if (item.thumbnailUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(start, end))),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${item.title} 的缩略图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    ) {
                        Text(
                            item.channel.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                SourceThumbnailFallback(channel = item.channel, start = start, end = end)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(item.channel)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.theme,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.source} · ${item.readTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMark, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (item.marked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "标记",
                            tint = if (item.marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (item.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "保存",
                            tint = if (item.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    TextButton(onClick = onPositive, modifier = Modifier.height(32.dp)) { Text("喜欢", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = onNegative, modifier = Modifier.height(32.dp)) { Text("不感兴趣", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "打开", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceThumbnailFallback(channel: SourceChannel, start: Color, end: Color) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Icon(Icons.Outlined.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "暂无封面",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                channel.label,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SourceBadge(channel: SourceChannel) {
    Surface(
        color = Color(channel.accent).copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            channel.label,
            color = Color(channel.accent),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun ProfileGuidedExploreCard(
    plan: ProfileGuidedExplorePlan,
    isRunning: Boolean,
    onRun: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("按我的画像探索", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text("先展示本机计划，再由你点击联网", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Surface(color = colors.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text("本机计划", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 18.sp)
            if (plan.focusThemes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    plan.focusThemes.forEach { theme -> AssistChip(onClick = {}, label = { Text(theme, style = MaterialTheme.typography.labelSmall) }) }
                }
            }
            Button(
                onClick = onRun,
                enabled = plan.isReady && !isRunning,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "正在按画像探索…" else "确认并按画像探索", style = MaterialTheme.typography.labelLarge)
            }
            Text("只会导入计划列出的公开来源；不会后台执行，不读取账号 Cookie。", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun InterestHypothesisCard(
    hypotheses: List<InterestHypothesisUi>,
    onDecision: (String, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("兴趣候选", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            val pending = hypotheses.filter { it.status == InterestHypothesisStatus.Pending }
            if (pending.isEmpty()) {
                Text("尚无待确认候选。持续使用信息流后，或在对话页主动提取主题，系统会生成可审阅的探索方向。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 18.sp)
            } else {
                pending.take(3).forEach { hypothesis ->
                    Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(hypothesis.candidateTheme, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                            Text("${hypothesis.originLabel} · 基于 ${hypothesis.evidenceCount} 条证据", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            Text(hypothesis.reason, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 16.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { onDecision(hypothesis.id, true) }) { Text("确认兴趣", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { onDecision(hypothesis.id, false) }) { Text("不感兴趣", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            Text("候选不会自动写入画像，也不会自动发起内容探索。", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BackgroundDiscoveryCard(
    state: BackgroundDiscoveryUiState,
    onRunNow: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Explore, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("其他公开来源", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text("仅在你点击后联网 · B 站热门由上方单独导入", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "本机账本",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(state.notice, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 18.sp)
            Button(
                onClick = onRunNow,
                enabled = !state.isRunning,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在探索其他公开来源…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("探索其他公开来源", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (state.sources.isNotEmpty() || state.recentTasks.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
            }
            state.sources.take(3).forEach { source ->
                Text(
                    "${source.platform.shortLabel} · ${source.state.label} · ${source.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (source.state) {
                        SourceAvailabilityState.Available -> colors.tertiary
                        SourceAvailabilityState.Degraded -> colors.secondary
                        SourceAvailabilityState.Unavailable -> colors.error
                    },
                    lineHeight = 16.sp,
                )
            }
            state.recentTasks.firstOrNull()?.let { task ->
                Text(
                    "最近任务：${task.kind.label} · ${task.status.label} · ${task.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  内容库
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    viewModel: LocalLibraryViewModel,
    onNavigateToFocus: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("内容库", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text("保存、标记、最近打开和隐藏内容都只保留在这台手机。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LibrarySection.entries.forEachIndexed { index, section ->
                    SegmentedButton(
                        selected = section == state.section,
                        onClick = { viewModel.select(section) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LibrarySection.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(section.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("稍后 ${state.totalSaved}")
                StatusPill("标记 ${state.totalMarked}")
                StatusPill("隐藏 ${state.totalHidden}")
            }
        }
        if (state.items.isEmpty()) {
            item {
                EmptyStateCard(
                    title = state.emptyMessage.ifBlank { "这里还没有内容" },
                    description = "去首页浏览内容，点击书签图标保存到内容库。也可以导入 B 站观看历史。",
                    actionLabel = "去首页",
                    onAction = onNavigateToFocus,
                )
            }
        } else {
            items(state.items, key = { it.contentKey }) { item ->
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.clickable {
                        ExternalUrlPolicy.viewIntent(item.url)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item.summary.ifBlank { "暂无摘要" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, lineHeight = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.toggleSaved(item) }) { Text(if (item.saved) "移出稍后" else "保存", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { viewModel.toggleMarked(item) }) { Text(if (item.marked) "取消标记" else "标记", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { if (item.hidden) viewModel.restore(item) else viewModel.hide(item) }) {
                                Text(if (item.hidden) "恢复" else "隐藏", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  本机画像
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CompassScreen(
    viewModel: LocalFeedViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val localState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本机画像", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, lineHeight = 34.sp)
                Text("由你在这台手机上的打开、标记和收藏行为生成；你可以随时用新的行为改变它。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
            }
        }
        item {
            AgentCognitiveCard(
                snapshot = localState.agentSnapshot,
                run = localState.agentRun,
                cloudEnabled = localState.cloudAi.enabled && localState.cloudAi.hasKey,
                onRun = viewModel::runAgentCognition,
                onCloud = viewModel::buildCloudProfileCandidate,
            )
        }
        // 核心边界
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("核心边界", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    val core = localState.profiles.firstOrNull { it.layer == ProfileLayer.Core }
                    val values = localState.profiles.firstOrNull { it.layer == ProfileLayer.Values }
                    Text(
                        core?.summary?.ifBlank { values?.summary ?: "正在从本机事件生成核心边界…" } ?: "正在从本机事件生成核心边界…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                    if (core?.candidate?.isNotBlank() == true || values?.candidate?.isNotBlank() == true) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Text(
                                (core?.candidate ?: values?.candidate) ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { viewModel.confirmProfileLayer(core?.layer ?: ProfileLayer.Core) }) {
                                Text("确认写入", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { viewModel.resetProfileLayer(core?.layer ?: ProfileLayer.Core) }) {
                                Text("重新观察", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        // 兴趣层
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("兴趣层", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text("${localState.interests.size} 个主题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (localState.interests.isEmpty()) {
                        Text("先在首页打开、标记或保存内容。兴趣画像只基于本机行为生成。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        localState.interests.take(6).forEach { interest ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(10.dp))
                                Text(interest.theme, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text("${interest.lifecycle.label} · ${interest.evidenceCount}条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            InterestHypothesisCard(
                hypotheses = localState.hypotheses,
                onDecision = viewModel::decideInterestHypothesis,
            )
        }
        // 行为层
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("行为层", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BehaviorStat("已保存", localState.savedCount.toString())
                        BehaviorStat("反馈", localState.feedbackCount.toString())
                        BehaviorStat("内容", localState.items.size.toString())
                    }
                    val canUseCloudAi = localState.cloudAi.enabled && localState.cloudAi.hasKey
                    Button(
                        onClick = {
                            if (canUseCloudAi) viewModel.buildCloudProfileCandidate() else onNavigateToSettings()
                        },
                        enabled = !localState.cloudAi.isWorking,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (localState.cloudAi.isWorking) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (canUseCloudAi) "用 ${localState.cloudAi.provider.displayName} 更新画像候选" else "配置云端 AI 后更新画像",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        if (canUseCloudAi) localState.cloudAi.status else "需先在“设置”中保存 API Key；点击上方按钮可直接前往配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text("主题归并、系列识别、重排和分层画像均由手机内的确定性规则完成。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AgentCognitiveCard(
    snapshot: AgentCognitiveSnapshot,
    run: AgentRunUiState,
    cloudEnabled: Boolean,
    onRun: () -> Unit,
    onCloud: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("本地 Agent 认知闭环", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text("${run.phase.label} · 点击前不联网", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Text(snapshot.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, lineHeight = 22.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.layers.forEach { layer ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(layer.name, style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.width(112.dp), lineHeight = 20.sp)
                        Text(layer.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), lineHeight = 20.sp)
                    }
                }
            }
            if (snapshot.pendingConfirmations > 0) {
                Text("有 ${snapshot.pendingConfirmations} 个候选需要在下方确认或拒绝；候选不会自动变成你的长期画像。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 20.sp)
            }
            Button(
                onClick = onRun,
                enabled = run.phase != AgentRunPhase.Synthesizing,

                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (run.phase == AgentRunPhase.Synthesizing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (run.phase == AgentRunPhase.Synthesizing) "正在运行本机认知…" else "运行一次本机 Agent 认知", style = MaterialTheme.typography.labelLarge)
            }
            if (cloudEnabled) {
                OutlinedButton(
                    onClick = onCloud,
                    enabled = run.phase != AgentRunPhase.Synthesizing,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("用已启用模型增强画像候选", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(run.notice, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun BehaviorStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ═══════════════════════════════════════════════════════════════
//  对话页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TalkScreen(
    store: AuluneStore,
    client: LlmClient,
    conversationViewModel: LocalConversationViewModel,
    localFeedViewModel: LocalFeedViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val messages by conversationViewModel.messages.collectAsState()
    val isGenerating by conversationViewModel.isGenerating.collectAsState()
    val status by conversationViewModel.status.collectAsState()
    val activeSettings = store.settingsFor(store.selectedProvider)
    val context = LocalContext.current
    val haptic = HapticFeedback(context)

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        if (draft.isBlank() || isGenerating) return
        conversationViewModel.send(draft, store.selectedProvider, activeSettings, client)
        draft = ""
        haptic.confirm()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("和想法一起工作", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text("对话保存在本机；模型只在你发送后调用。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { conversationViewModel.clear() }) { Text("清空", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        ProviderBar(
            provider = store.selectedProvider,
            configured = activeSettings.apiKey.isNotBlank(),
            status = status,
            onConfigure = onNavigateToSettings,
        )
        if (messages.any { it.fromUser }) {
            TextButton(
                onClick = {
                    localFeedViewModel.extractDialogueInterestHypotheses(
                        messages.filter { it.fromUser }.map { it.text }
                    ) { message -> conversationViewModel.showStatus(message) }
                    haptic.confirm()
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("从我的对话提取兴趣候选", style = MaterialTheme.typography.labelMedium)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Aulune 对话", text))
                    conversationViewModel.showStatus("已复制消息内容")
                    haptic.confirm()
                }
            }
            if (isGenerating) item { ThinkingBubble(store.selectedProvider.displayName) }
        }
        // Pixel Messages 风格悬浮胶囊输入框
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("输入你的问题…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    minLines = 1,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (draft.isBlank() || isGenerating) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        .clickable(enabled = draft.isNotBlank() && !isGenerating) { submit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (draft.isBlank() || isGenerating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderBar(
    provider: AiProvider,
    configured: Boolean,
    status: String,
    onConfigure: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clickable { onConfigure() },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (configured) status else "尚未配置模型 · 点击此处前往设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage, onCopy: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(0.85f)) {
            Surface(
                color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (message.fromUser) 20.dp else 6.dp,
                    bottomEnd = if (message.fromUser) 6.dp else 20.dp,
                ),
                tonalElevation = if (message.fromUser) 0.dp else 1.dp,
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 20.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onCopy(message.text) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制消息", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble(provider: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("$provider 正在思考…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  设置页（模型工作台 + 外观）
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    store: AuluneStore,
    localFeedViewModel: LocalFeedViewModel,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val localState by localFeedViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentThemeMode = ThemeManager.getMode(context)
    val dynamicEnabled = ThemeManager.isDynamicColorEnabled(context)
    var expandedProvider by rememberSaveable { mutableStateOf(store.selectedProvider) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        // 外观设置
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("主题模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("动态颜色", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text("跟随壁纸主题色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = dynamicEnabled,
                                onCheckedChange = onDynamicColorChange,
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
        // 模型工作台
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("模型工作台", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    // API 类型 Chips
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AiProvider.entries.forEach { provider ->
                            AssistChip(
                                onClick = { expandedProvider = provider; store.selectProvider(provider) },
                                label = { Text(provider.displayName, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (expandedProvider == provider) {
                                    { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (expandedProvider == provider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                    // Accordion 展开的模型配置
                    AnimatedVisibility(visible = true) {
                        ModelConfigCard(
                            provider = expandedProvider,
                            store = store,
                            localFeedViewModel = localFeedViewModel,
                            localState = localState,
                        )
                    }
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                Text(
                    "隐私说明：云端 AI 只在你主动发送对话或更新画像时调用。不会发送登录 Cookie、账号令牌或原始观看记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp),
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
internal fun ModelPickerDialog(
    models: List<String>,
    selectedModel: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(models, query) { filterRemoteModels(models, query) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索模型") },
                    placeholder = { Text("例如 gpt、claude、qwen") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
                Text(
                    if (query.isBlank()) "共 ${filtered.size} 个可选模型" else "找到 ${filtered.size} 个匹配模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it }) { item ->
                        Surface(
                            color = if (item == selectedModel) colors.primaryContainer else colors.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
                        ) {
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item == selectedModel) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text("没有匹配模型；可清空搜索或继续手动填写。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ModelConfigCard(
    provider: AiProvider,
    store: AuluneStore,
    localFeedViewModel: LocalFeedViewModel,
    localState: LocalFeedUiState,
) {
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable(provider) { mutableStateOf(store.settingsFor(provider).apiKey) }
    var isApiKeyVisible by rememberSaveable(provider) { mutableStateOf(false) }
    var model by rememberSaveable(provider) { mutableStateOf(store.settingsFor(provider).effectiveModel(provider)) }
    var baseUrl by rememberSaveable(provider) { mutableStateOf(store.settingsFor(provider).effectiveBaseUrl(provider)) }
    var modelStatus by rememberSaveable(provider) { mutableStateOf("可手动填写模型名，或用 API Key 获取列表。") }
    var isLoadingModels by rememberSaveable(provider) { mutableStateOf(false) }
    var remoteModels by rememberSaveable(provider) { mutableStateOf(emptyList<String>()) }
    var showModelPicker by rememberSaveable(provider) { mutableStateOf(false) }
    var showClearConfigConfirm by rememberSaveable(provider) { mutableStateOf(false) }
    val configured = store.providerSettings.filterValues { it.apiKey.isNotBlank() }.keys
    val canEnable = apiKey.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(if (provider in configured) "已保存，可用于对话" else "尚未保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = apiKey, onValueChange = {
                apiKey = it
                store.saveProviderDraft(
                    provider,
                    ProviderSettings(apiKey = it.trim(), model = model.trim(), baseUrl = baseUrl.trim(), protocol = provider.protocol),
                )
            },
            label = { Text("API Key") },
            placeholder = { Text(provider.keyHint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            visualTransformation = if (isApiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                    Icon(
                        imageVector = if (isApiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isApiKeyVisible) "隐藏 API Key" else "显示 API Key",
                    )
                }
            },
        )
        OutlinedTextField(
            value = baseUrl, onValueChange = {
                baseUrl = it
                store.saveProviderDraft(
                    provider,
                    ProviderSettings(apiKey = apiKey.trim(), model = model.trim(), baseUrl = it.trim(), protocol = provider.protocol),
                )
            },
            label = { Text("接口基础地址") },
            placeholder = { Text(provider.defaultBaseUrl) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        OutlinedTextField(
            value = model, onValueChange = {
                model = it
                store.saveProviderDraft(
                    provider,
                    ProviderSettings(apiKey = apiKey.trim(), model = it.trim(), baseUrl = baseUrl.trim(), protocol = provider.protocol),
                )
            },
            label = { Text("模型名称") },
            placeholder = { Text(provider.defaultModel.ifBlank { "例如 provider/model-name" }) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Text(
            if (canEnable) {
                "输入内容会自动加密保存在本机；点击“保存并启用”后才会启用云端 API。"
            } else {
                "请先填写 API Key。当前草稿会加密保存在本机，但不会启用云端 API。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                isLoadingModels = true
                modelStatus = "正在获取模型列表…"
                val requestSettings = ProviderSettings(apiKey = apiKey.trim(), model = model.trim(), baseUrl = baseUrl.trim(), protocol = provider.protocol)
                store.saveProviderDraft(provider, requestSettings)
                modelStatus = "已保存本机配置草稿，正在获取模型列表…"
                scope.launch {
                    LlmClient().listModels(provider, requestSettings)
                        .onSuccess { models ->
                            remoteModels = filterRemoteModels(models.map { it.id }, query = "")
                            showModelPicker = remoteModels.isNotEmpty()
                            modelStatus = if (remoteModels.isEmpty()) "服务商未返回可用模型；仍可手动填写。" else "已获取 ${remoteModels.size} 个模型；已打开可搜索列表。"
                        }
                        .onFailure { error ->
                            remoteModels = emptyList()
                            modelStatus = "获取失败：${error.message ?: "请检查 Key 或接口地址。"}"
                        }
                    isLoadingModels = false
                }
            },
            enabled = !isLoadingModels,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            if (isLoadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("获取模型列表", style = MaterialTheme.typography.labelLarge)
        }
        Text(modelStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (remoteModels.isNotEmpty()) {
            OutlinedButton(
                onClick = { showModelPicker = true },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("浏览并搜索 ${remoteModels.size} 个模型", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (showModelPicker) {
            ModelPickerDialog(
                models = remoteModels,
                selectedModel = model,
                onDismiss = { showModelPicker = false },
                onSelect = { selected ->
                    model = selected
                    store.saveProviderDraft(
                        provider,
                        ProviderSettings(apiKey = apiKey.trim(), model = selected, baseUrl = baseUrl.trim(), protocol = provider.protocol),
                    )
                    modelStatus = "已选择并保存模型：$selected"
                    showModelPicker = false
                },
            )
        }
        Button(
            onClick = {
                val saved = ProviderSettings(apiKey = apiKey.trim(), model = model.trim(), baseUrl = baseUrl.trim(), protocol = provider.protocol)
                store.setProviderSettings(provider, saved)
                localFeedViewModel.saveCloudAiConfig(provider = provider, apiKey = saved.apiKey, model = saved.effectiveModel(provider), baseUrl = saved.effectiveBaseUrl(provider), protocol = saved.effectiveProtocol(provider), enable = true)
                modelStatus = "已保存 ${provider.displayName} 配置。"
            },
            enabled = canEnable,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("保存并启用 ${provider.displayName}", style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { localFeedViewModel.disableCloudAi() }) { Text("仅使用本机规则", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { showClearConfigConfirm = true }) {
                Text("清除当前配置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showClearConfigConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfigConfirm = false },
                title = { Text("清除 ${provider.displayName} 配置？") },
                text = { Text("将从本机加密存储中删除当前服务商的 API Key、模型和接口地址。若它正在用于云端增强，也会同时停止该增强。") },
                dismissButton = {
                    TextButton(onClick = { showClearConfigConfirm = false }) { Text("取消") }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            store.clearProviderSettings(provider)
                            localFeedViewModel.clearCloudAiConfig(provider)
                            apiKey = ""
                            model = provider.defaultModel
                            baseUrl = provider.defaultBaseUrl
                            remoteModels = emptyList()
                            modelStatus = "已清除 ${provider.displayName} 的本机配置。"
                            showClearConfigConfirm = false
                        },
                    ) { Text("清除") }
                },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  触觉反馈
// ═══════════════════════════════════════════════════════════════

private class HapticFeedback(private val context: android.content.Context) {
    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun click() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        }
    }

    fun confirm() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            }
        }
    }
}
