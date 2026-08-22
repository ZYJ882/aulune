package app.aulune.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStartupAppearance(AppearancePreferences(this).load())
        setContent { AuluneApp() }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    Focus("灵感", Icons.Outlined.Home),
    Library("内容库", Icons.Outlined.Bookmark),
    Compass("画像", Icons.Outlined.PersonOutline),
    Talk("对话", Icons.Outlined.ChatBubbleOutline),
    Settings("设置", Icons.Outlined.Settings)
}

private enum class SettingsDestination { Overview, Appearance, Models }

@Composable
private fun AuluneApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appearancePreferences = remember { AppearancePreferences(context) }
    var appearanceMode by remember { mutableStateOf(appearancePreferences.load()) }
    val store = remember { AuluneStore(appContext) }
    val llmClient = remember { LlmClient() }
    val localFeedViewModel: LocalFeedViewModel = viewModel()
    val localLibraryViewModel: LocalLibraryViewModel = viewModel()
    val localConversationViewModel: LocalConversationViewModel = viewModel()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var settingsDestinationName by rememberSaveable { mutableStateOf(SettingsDestination.Overview.name) }
    val settingsDestination = SettingsDestination.valueOf(settingsDestinationName)

    AuluneTheme(appearanceMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)))
                        .navigationBarsPadding()
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = {
                                tabIndex = index
                                if (tab != AppTab.Settings) settingsDestinationName = SettingsDestination.Overview.name
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                            label = { Text(tab.label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tabIndex to settingsDestination,
                transitionSpec = {
                    fadeIn(tween(AuluneLayout.MotionDuration)) togetherWith fadeOut(tween(AuluneLayout.MotionDuration))
                },
                label = "screen-transition"
            ) { (activeTab, destination) ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (AppTab.entries[activeTab]) {
                        AppTab.Focus -> FocusScreen(localFeedViewModel)
                        AppTab.Library -> LibraryScreen(localLibraryViewModel)
                        AppTab.Compass -> CompassScreen(localFeedViewModel)
                        AppTab.Talk -> TalkScreen(
                            store = store,
                            client = llmClient,
                            conversationViewModel = localConversationViewModel,
                            onOpenModels = {
                                tabIndex = AppTab.Settings.ordinal
                                settingsDestinationName = SettingsDestination.Models.name
                            }
                        )
                        AppTab.Settings -> SettingsScreen(
                            destination = destination,
                            store = store,
                            localFeedViewModel = localFeedViewModel,
                            appearanceMode = appearanceMode,
                            onAppearanceModeChange = { mode ->
                                appearanceMode = mode
                                appearancePreferences.save(mode)
                            },
                            onDestinationChange = { settingsDestinationName = it.name }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusScreen(viewModel: LocalFeedViewModel) {
    val context = LocalContext.current
    val localState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AppHeader(
                eyebrow = "AULUNE · 本地优先",
                title = "留给思考的输入",
                subtitle = "少一点噪声，多一些帮助判断、创造与行动的内容。",
                icon = Icons.Outlined.AutoAwesome,
                onAction = viewModel::rotateFeed
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuietBadge("当前视角 · ${localState.activeLens}", colors.primaryContainer, colors.onPrimaryContainer)
                QuietBadge("已保存 ${localState.savedCount} 条", colors.secondaryContainer, colors.onSecondaryContainer)
                QuietBadge("反馈 ${localState.feedbackCount} 条", colors.surfaceVariant, colors.onSurfaceVariant)
                QuietBadge("${localState.intent.label}模式", colors.tertiaryContainer, colors.onTertiaryContainer)
            }
        }
        item {
            AuluneCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("本机信息流", color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionIntent.entries.forEach { intent ->
                            val selected = localState.intent == intent
                            Surface(
                                color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.clickable { viewModel.setSessionIntent(intent) }
                            ) {
                                Text(
                                    intent.label,
                                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Text(localState.intent.description, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        item {
            AulunePrimaryButton(
                onClick = viewModel::importBilibiliPublicContent,
                enabled = !localState.isBilibiliImporting
            ) {
                if (localState.isBilibiliImporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入公开内容", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            AuluneCard(padding = 16.dp, containerColor = colors.surface.copy(alpha = 0.74f)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(localState.bilibiliStatus, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        QuietAction(
                            text = "全平台导入",
                            icon = Icons.Outlined.Public,
                            modifier = Modifier.weight(1f),
                            enabled = !localState.isPlatformSyncing,
                            onClick = viewModel::importAllPlatformsPublic
                        )
                        QuietAction(
                            text = "账号连接",
                            icon = Icons.Outlined.AccountCircle,
                            modifier = Modifier.weight(1f),
                            onClick = { context.startActivity(Intent(context, MultiPlatformLoginActivity::class.java)) }
                        )
                    }
                    if (localState.platformLoginStatus.isNotEmpty()) {
                        HorizontalDivider(color = colors.outlineVariant)
                        localState.platformLoginStatus.forEach { (platform, status) ->
                            Text("${platform.shortLabel} · $status", color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    if (localState.platformSyncStatus.isNotEmpty()) {
                        HorizontalDivider(color = colors.outlineVariant)
                        localState.platformSyncStatus.forEach { (platform, status) ->
                            Text("${platform.shortLabel} · $status", color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        item { BackgroundDiscoveryCard(state = localState.backgroundDiscovery, onRunNow = viewModel::runBackgroundDiscoveryNow) }
        item { CloudManualOrganizeCard(state = localState.cloudAi, onOrganize = viewModel::organizeRecentContentWithCloudAi) }
        item {
            QuietAction(
                text = "按本机画像重排",
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::rotateFeed
            )
        }
        item { FocusPromptCard() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("今日输入", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Text("${localState.items.size} 条", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        item { Text(localState.explanation, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp) }
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
        item {
            Text(
                "内容、收藏、推荐理由与行为事件均保存在这台设备。账号凭据不会在信息流中显示或导出。",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AppHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                AppCircleIconButton(Icons.Outlined.ArrowBack, "返回", onBack)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(eyebrow, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                Text(title, color = colors.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp)
            }
            Surface(
                shape = CircleShape,
                color = colors.primaryContainer,
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            if (onAction != null) {
                Spacer(Modifier.width(8.dp))
                AppCircleIconButton(Icons.Outlined.Refresh, "刷新", onAction)
            }
        }
        Text(subtitle, color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun AppCircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun QuietBadge(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = background) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = foreground, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun QuietAction(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (enabled) colors.surface else colors.surfaceVariant,
        contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        shape = AuluneControlShape,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FocusPromptCard() {
    val colors = MaterialTheme.colorScheme
    AuluneCard(containerColor = colors.surfaceVariant.copy(alpha = 0.72f)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("给自己一个问题", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("如果今天只能推进一件事，哪件事会让后续工作变得更轻？", color = colors.onSurface, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
            Text("打开“对话”，让已配置的云端模型陪你把答案拆开。", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
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
    val colors = MaterialTheme.colorScheme
    val start = Color(item.gradientStart)
    val end = Color(item.gradientEnd)
    AuluneCard(padding = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Row {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 136.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(start, end)))
                    .clickable { onOpen() },
                contentAlignment = Alignment.BottomStart
            ) {
                Surface(color = Color.Black.copy(alpha = 0.18f), shape = RoundedCornerShape(topEnd = 12.dp)) {
                    Text(item.channel.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(item.channel)
                    Spacer(Modifier.width(6.dp))
                    QuietBadge(item.lifecycle.label, colors.surfaceVariant, colors.onSurfaceVariant)
                }
                Text(item.title, color = colors.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${item.source} · ${item.readTime}", color = colors.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.insight, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    FeedAction(Icons.Outlined.Favorite, "喜欢", onPositive, colors.primary)
                    FeedAction(if (item.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "保存", onSave, colors.secondary)
                    FeedAction(Icons.Outlined.Explore, "略过", onNegative, colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onAnalyzeCloud, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI解析", tint = colors.primary, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "打开", tint = colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedAction(icon: ImageVector, text: String, onClick: () -> Unit, tint: Color) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = tint)
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourceBadge(channel: SourceChannel) {
    val color = Color(channel.accent)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
        Text(channel.label, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
internal fun BackgroundDiscoveryCard(
    state: BackgroundDiscoveryUiState,
    onRunNow: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AuluneCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("手动来源探测", color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("仅在你点击后联网 · 仅探测公开来源", color = colors.tertiary, fontSize = 11.sp)
                }
                QuietBadge("本机账本", colors.tertiaryContainer, colors.onTertiaryContainer)
            }
            Text(state.notice, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            QuietAction(
                text = if (state.isRunning) "正在探测公开来源…" else "立即探测公开来源",
                icon = Icons.Outlined.Explore,
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRunNow
            )
            if (state.sources.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                Text("最近来源可用性", color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                state.sources.forEach { source ->
                    val tone = when (source.state) {
                        SourceAvailabilityState.Available -> colors.tertiary
                        SourceAvailabilityState.Degraded -> colors.secondary
                        SourceAvailabilityState.Unavailable -> colors.error
                    }
                    Text("${source.platform.shortLabel} · ${source.state.label} · ${source.detail}", color = tone, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            state.recentTasks.firstOrNull()?.let { task ->
                Text("最近任务：${task.kind.label} · ${task.status.label} · ${task.detail}", color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
internal fun CloudManualOrganizeCard(
    state: CloudAiUiState,
    onOrganize: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AuluneCard(containerColor = colors.primaryContainer.copy(alpha = 0.52f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("云端智能整理", color = colors.onPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("仅在你点击后，才会依次向已配置服务商发送最多 5 条内容的标题、摘要、来源和当前规则主题。不会使用本地模型、Embedding 或后台联网。", color = colors.onSurface, fontSize = 12.sp, lineHeight = 18.sp)
            Text(
                if (state.enabled && state.hasKey) "当前使用 ${state.provider.displayName} · ${state.model}" else "请先在“设置 > 模型服务”保存并启用云端 API Key。",
                color = if (state.enabled && state.hasKey) colors.tertiary else colors.onSurfaceVariant,
                fontSize = 12.sp
            )
            AulunePrimaryButton(
                onClick = onOrganize,
                enabled = state.enabled && state.hasKey && !state.isWorking
            ) {
                if (state.isWorking) CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("智能整理最多 5 条内容", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(viewModel: LocalLibraryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader("LOCAL LIBRARY", "本地内容库", "保存、标记、最近打开和隐藏内容都只保留在这台手机。", Icons.Outlined.Bookmark) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySection.entries.forEach { section ->
                    val selected = section == state.section
                    Surface(
                        color = if (selected) colors.primaryContainer else colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.clickable { viewModel.select(section) }
                    ) {
                        Text(section.label, color = if (selected) colors.onPrimaryContainer else colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                QuietBadge("稍后 ${state.totalSaved}", colors.secondaryContainer, colors.onSecondaryContainer)
                QuietBadge("标记 ${state.totalMarked}", colors.primaryContainer, colors.onPrimaryContainer)
                QuietBadge("隐藏 ${state.totalHidden}", colors.surfaceVariant, colors.onSurfaceVariant)
            }
        }
        if (state.items.isEmpty()) {
            item { EmptyState(state.emptyMessage) }
        } else {
            items(state.items, key = { it.contentKey }) { item ->
                AuluneCard(padding = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(item.title, color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.source, color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(item.summary.ifBlank { "暂无摘要" }, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { if (item.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }) { Text("打开") }
                            TextButton(onClick = { viewModel.toggleSaved(item) }) { Text(if (item.saved) "移出稍后" else "保存") }
                            TextButton(onClick = { viewModel.toggleMarked(item) }) { Text(if (item.marked) "取消标记" else "标记") }
                            TextButton(onClick = { if (item.hidden) viewModel.restore(item) else viewModel.hide(item) }) { Text(if (item.hidden) "恢复" else "隐藏", color = colors.onSurfaceVariant) }
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
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader("YOUR SIGNALS", "你的本机画像", "从打开、标记和收藏中生成；每一条结论都可以被你修正。", Icons.Outlined.PersonOutline) }
        item { PortraitCard(localState) }
        item {
            AulunePrimaryButton(onClick = viewModel::buildCloudProfileCandidate, enabled = !localState.cloudAi.isWorking) {
                if (localState.cloudAi.isWorking) CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("生成长期画像候选", fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text(localState.cloudAi.status, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp) }
        item { SectionTitle("分层本机画像") }
        if (localState.profiles.isEmpty()) {
            item { EmptyState("正在从本机事件建立画像层…") }
        } else {
            items(localState.profiles, key = { it.layer.name }) { profile ->
                AuluneCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.layer.label, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            QuietBadge(
                                when (profile.confirmationState) {
                                    "confirmed" -> "已确认"
                                    "pending" -> "等待确认"
                                    else -> "自动更新"
                                },
                                colors.surfaceVariant,
                                colors.onSurfaceVariant
                            )
                        }
                        Text(profile.summary, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
                        if (profile.candidate.isNotBlank()) {
                            Surface(color = colors.primaryContainer.copy(alpha = 0.62f), shape = RoundedCornerShape(14.dp)) {
                                Text(profile.candidate, color = colors.onPrimaryContainer, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(13.dp))
                            }
                            if (profile.layer == ProfileLayer.Values || profile.layer == ProfileLayer.Core) {
                                TextButton(onClick = { viewModel.confirmProfileLayer(profile.layer) }) { Text("确认写入本机${profile.layer.label}") }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("已积累的兴趣证据") }
        if (localState.interests.isEmpty()) {
            item { EmptyState("先在“灵感”页打开、标记或保存内容。Aulune 不会为你预设人格标签。") }
        } else {
            items(localState.interests, key = { it.theme }) { interest ->
                AuluneCard(padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(colors.primary, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(interest.theme, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("本机兴趣强度 ${String.format(java.util.Locale.US, "%.1f", interest.weight)}", color = colors.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text("${interest.lifecycle.label}\n${interest.evidenceCount} 条证据", color = colors.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
        item { Text("画像、主题和重排由本机规则生成。云端候选只在你点击后调用，且必须由你确认。", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun PortraitCard(localState: LocalFeedUiState) {
    val colors = MaterialTheme.colorScheme
    AuluneCard(containerColor = colors.primaryContainer.copy(alpha = 0.68f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("从行为中形成，而不是被预先定义", color = colors.onPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(localState.explanation, color = colors.onSurface, fontSize = 14.sp, lineHeight = 21.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietBadge("本机存储", colors.surface, colors.onSurface)
                QuietBadge("可随时修正", colors.surface, colors.onSurface)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun EmptyState(text: String) {
    val colors = MaterialTheme.colorScheme
    AuluneCard(containerColor = colors.surface.copy(alpha = 0.7f)) {
        Text(text, color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun TalkScreen(
    store: AuluneStore,
    client: LlmClient,
    conversationViewModel: LocalConversationViewModel,
    onOpenModels: () -> Unit
) {
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val messages by conversationViewModel.messages.collectAsState()
    val isGenerating by conversationViewModel.isGenerating.collectAsState()
    val status by conversationViewModel.status.collectAsState()
    val activeSettings = store.settingsFor(store.selectedProvider)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun submit() {
        if (draft.isBlank() || isGenerating) return
        conversationViewModel.send(draft, store.selectedProvider, activeSettings, client)
        draft = ""
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("和想法一起工作", color = colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("对话保存在本机；模型只在你配置并发送后调用。", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
                TextButton(onClick = conversationViewModel::clear) { Text("清空", color = colors.onSurfaceVariant) }
            }
        }
        ProviderBar(provider = store.selectedProvider, configured = activeSettings.apiKey.isNotBlank(), status = status, onClick = onOpenModels)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            if (isGenerating) item { ThinkingBubble(store.selectedProvider.displayName) }
        }
        ChatComposer(draft = draft, onDraftChange = { draft = it }, enabled = !isGenerating, onSend = ::submit)
    }
}

@Composable
private fun ProviderBar(provider: AiProvider, configured: Boolean, status: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = AuluneControlShape,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (configured) colors.tertiary else colors.outline, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(status, color = colors.onSurfaceVariant, fontSize = 11.sp)
            }
            Text("切换", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(0.86f)) {
            Surface(
                color = if (message.fromUser) colors.primaryContainer else colors.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.fromUser) 20.dp else 6.dp, bottomEnd = if (message.fromUser) 6.dp else 20.dp),
                border = if (message.fromUser) null else BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Text(
                    message.text,
                    color = if (message.fromUser) colors.onPrimaryContainer else colors.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(message.time, color = colors.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ThinkingBubble(provider: String) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(17.dp), color = colors.primary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("$provider 正在思考…", color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChatComposer(draft: String, onDraftChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inputInteraction = remember { MutableInteractionSource() }
    val focused by inputInteraction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, tween(180), label = "chat-input-scale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.94f))
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("输入你的问题…", color = colors.onSurfaceVariant) },
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            enabled = enabled,
            interactionSource = inputInteraction,
            shape = RoundedCornerShape(26.dp),
            minLines = 1,
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.70f),
                unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.58f),
                disabledContainerColor = colors.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.primary,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface
            )
        )
        Spacer(Modifier.width(10.dp))
        SendButton(enabled = enabled && draft.isNotBlank(), onClick = onSend)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.90f), colors.secondary.copy(alpha = 0.84f)))
                else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant))
            )
    ) {
        Icon(Icons.Outlined.Send, contentDescription = "发送", tint = if (enabled) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsScreen(
    destination: SettingsDestination,
    store: AuluneStore,
    localFeedViewModel: LocalFeedViewModel,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onDestinationChange: (SettingsDestination) -> Unit
) {
    when (destination) {
        SettingsDestination.Overview -> SettingsOverview(appearanceMode, onDestinationChange)
        SettingsDestination.Appearance -> AppearanceSettingsScreen(appearanceMode, onAppearanceModeChange) { onDestinationChange(SettingsDestination.Overview) }
        SettingsDestination.Models -> ModelSettingsScreen(store, localFeedViewModel) { onDestinationChange(SettingsDestination.Overview) }
    }
}

@Composable
private fun SettingsOverview(appearanceMode: AppearanceMode, onDestinationChange: (SettingsDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader("PREFERENCES", "设置", "让界面、模型与数据边界保持符合你的习惯。", Icons.Outlined.Settings) }
        item {
            AuluneCard(padding = 0.dp) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = "外观",
                        description = appearanceMode.label,
                        tint = colors.primary,
                        onClick = { onDestinationChange(SettingsDestination.Appearance) }
                    )
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    SettingsRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "模型服务",
                        description = "API Key、模型与云端增强",
                        tint = colors.secondary,
                        onClick = { onDestinationChange(SettingsDestination.Models) }
                    )
                }
            }
        }
        item {
            AuluneCard(containerColor = colors.surfaceVariant.copy(alpha = 0.54f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本机优先", color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("内容、反馈与画像默认保留在这台设备。云端模型只会在你主动点击相应功能时调用。", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, description: String, tint: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceSettingsScreen(mode: AppearanceMode, onModeChange: (AppearanceMode) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader("APPEARANCE", "外观", "选择界面显示方式；每次选择都会立刻保存并应用。", Icons.Outlined.Palette, onBack = onBack) }
        item {
            AuluneCard(padding = 0.dp) {
                Column {
                    AppearanceMode.entries.forEachIndexed { index, option ->
                        AppearanceOption(option, selected = option == mode, onClick = { onModeChange(option) })
                        if (index < AppearanceMode.entries.lastIndex) HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
        item {
            AuluneCard(containerColor = colors.primaryContainer.copy(alpha = 0.55f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("动态颜色与平滑切换", color = colors.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Android 12 及以上设备会采用系统动态色彩。选择“跟随系统”时，系统在日落等场景切换深浅主题后，Aulune 会立即同步。", color = colors.onSurface, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceOption(option: AppearanceMode, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val icon = when (option) {
        AppearanceMode.System -> Icons.Outlined.Palette
        AppearanceMode.Light -> Icons.Outlined.LightMode
        AppearanceMode.Dark -> Icons.Outlined.DarkMode
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(10.dp))
        Surface(color = if (selected) colors.primaryContainer else colors.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(option.label, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(option.description, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ModelSettingsScreen(store: AuluneStore, localFeedViewModel: LocalFeedViewModel, onBack: () -> Unit) {
    val localState by localFeedViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader("MODEL SERVICES", "模型服务", "预设服务商自动使用官方默认格式；接口地址可编辑，只有自定义服务商需要选择协议。", Icons.Outlined.AutoAwesome, onBack = onBack) }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    val selected = provider == editingProvider
                    Surface(
                        color = if (selected) colors.primaryContainer else colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.clickable { switchProvider(provider) }
                    ) {
                        Text(provider.displayName, color = if (selected) colors.onPrimaryContainer else colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
        item {
            AuluneCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = colors.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(21.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(editingProvider.displayName, color = colors.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(if (editingProvider in configured) "已保存，可用于对话" else "尚未保存", color = colors.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    AppTextInput(apiKey, { apiKey = it }, "API Key", editingProvider.keyHint, password = true)
                    AppTextInput(baseUrl, { baseUrl = it }, "接口基础地址（可修改）", editingProvider.defaultBaseUrl)
                    if (editingProvider == AiProvider.Custom) {
                        Text("调用协议", color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProviderProtocol.entries.forEach { candidate ->
                                val selected = protocol == candidate
                                Surface(
                                    color = if (selected) colors.primaryContainer else colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable { protocol = candidate }
                                ) {
                                    Text(candidate.label, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                    AppTextInput(model, { model = it }, "模型名称（可手动填写）", editingProvider.defaultModel.ifBlank { "例如 provider/model-name" })
                    QuietAction(
                        text = if (isLoadingModels) "正在获取模型列表…" else "获取模型列表",
                        icon = Icons.Outlined.Refresh,
                        enabled = !isLoadingModels,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isLoadingModels = true
                            modelStatus = "正在获取模型列表…"
                            val requestSettings = ProviderSettings(apiKey = apiKey.trim(), model = model.trim(), baseUrl = baseUrl.trim(), protocol = if (editingProvider == AiProvider.Custom) protocol else editingProvider.protocol)
                            scope.launch {
                                LlmClient().listModels(editingProvider, requestSettings)
                                    .onSuccess { models ->
                                        remoteModels = models.map { it.id }
                                        modelStatus = if (models.isEmpty()) "服务商未返回可用模型；仍可手动填写模型名。" else "已获取 ${models.size} 个模型，点选即可填入。"
                                    }
                                    .onFailure { error ->
                                        remoteModels = emptyList()
                                        modelStatus = "获取失败：${error.message ?: "请检查 Key 或接口地址。"}"
                                    }
                                isLoadingModels = false
                            }
                        }
                    )
                    Text(modelStatus, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    if (remoteModels.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            remoteModels.forEach { item ->
                                Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { model = item }) {
                                    Text(item, color = colors.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                    Text("API Key 使用 Android Keystore 加密保存在本机；不会写入安装包。", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    AulunePrimaryButton(onClick = {
                        val saved = ProviderSettings(apiKey = apiKey.trim(), model = model.trim(), baseUrl = baseUrl.trim(), protocol = if (editingProvider == AiProvider.Custom) protocol else editingProvider.protocol)
                        store.setProviderSettings(editingProvider, saved)
                        localFeedViewModel.saveCloudAiConfig(editingProvider, saved.apiKey, saved.effectiveModel(editingProvider), saved.effectiveBaseUrl(editingProvider), saved.effectiveProtocol(editingProvider), enable = true)
                        modelStatus = "已保存 ${editingProvider.displayName} 配置。"
                    }) {
                        Text("保存并启用 ${editingProvider.displayName}", fontWeight = FontWeight.Bold)
                    }
                    Text("当前云端增强：${localState.cloudAi.status}", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = localFeedViewModel::disableCloudAi) { Text("仅使用本机规则") }
                        TextButton(onClick = localFeedViewModel::clearCloudAiKey) { Text("清除云端增强 Key", color = colors.error) }
                    }
                }
            }
        }
        item { SectionTitle("预设服务商默认接口格式") }
        item {
            AuluneCard {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    ModelCapabilityRow("OpenAI 兼容", "OpenAI、DeepSeek、智谱、Kimi、OpenRouter 与多数自定义网关")
                    HorizontalDivider(color = colors.outlineVariant)
                    ModelCapabilityRow("Anthropic", "Claude Messages API")
                    HorizontalDivider(color = colors.outlineVariant)
                    ModelCapabilityRow("Gemini", "Google GenerateContent API")
                }
            }
        }
        item {
            AuluneCard(containerColor = colors.secondaryContainer.copy(alpha = 0.64f)) {
                Text("隐私说明：云端 AI 只在你主动发送对话、点击“AI解析”、智能整理或更新长期画像候选时调用。不会发送登录 Cookie、账号令牌或原始观看记录。", color = colors.onSecondaryContainer, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun AppTextInput(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, password: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.72f),
            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.52f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.primary
        )
    )
}

@Composable
private fun ModelCapabilityRow(name: String, protocol: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(name, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(protocol, color = colors.onSurfaceVariant, fontSize = 12.sp)
    }
}
