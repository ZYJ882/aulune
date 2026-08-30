# Aulune Android 更新记录

## v2.0.0-dev — 本机补全 OpenBiliClaw 核心能力（首轮 + 次轮）

v2.0.0-dev 在 v1.9.6 基础上本机补全 OpenBiliClaw 的"先理解你 → 主动跨平台发现"闭环。**纯 Android 原生，不依赖外部后端**。

### 第一轮 · 理解你 + 对话

**避雷探针**：和兴趣探针对称的负向候选。当用户对某些主题、作者或系列反复给负反馈（≥2 次）后，应用主动询问是否长期避开；确认后写入过滤偏好，影响排序权重但不直接屏蔽内容。避雷候选支持四态：Pending / Avoiding / Tolerable / Expired。

**心理学画像（灵魂引擎四维度）**：对齐 OpenBiliClaw 灵魂引擎。本机行为桥接生成 4 维度候选：
- MBTI 推断（INTP-A 等格式 + 置信度）
- 认知风格（结构化 / 直觉型 / 实验型 / 综合型）
- 深层需求（好奇心 / 系统理解 / 自我表达 / 成就感 / 归属感 …）
- 人格素描（自然语言描述）

候选不会自动写入画像，需用户确认；云端 AI 配置后可生成更精准的候选。

**兴趣探针心理学桥接扩展**：从 v1.9.6 的 7 条跨主题桥接扩展到 19 条，覆盖技术/商业/学习/创造/生活/娱乐/哲学/音乐 8 个维度，例如 "技术 · AI" → "学习 · 哲学"、"商业 · 产品" → "学习 · 心理学"。

**"换一批" 三层去重**：v1.9.6 的 `rotateFeed()` 只是空操作。v2.0.0 实现真正的三层去重：
- 第一层：当前批（VM 内存 items.contentKey）
- 第二层：本会话已看（sessionViewedKeys StateFlow）
- 第三层：30 天持久化账本（`local_viewed_ledger` 表，Room Migration v8→v9）

**30 天历史"已移除"分类**：内容库从 4 个 tab 扩到 5 个（Saved / Marked / Recent / Hidden / Removed）。"已移除"按近 30 天 cutoff 显示隐藏内容，超 30 天自动从账本清理但不从源头删除。

**流式 SSE 输出**：`LlmClient.generateStream()` 返回 `Flow<String>`，OpenAI 兼容协议走 SSE 逐 token 输出；Claude / Gemini 自动回退到 `generate()` 一次性返回。对话页等待体验从"30 秒空白"变为"打字机式渐进显示"。

**durable turn_id**：`LocalChatMessageEntity` 加 `turnId` 字段，每轮 user+assistant 共享同一个 UUID。即使重试或切换 provider，也能用 turnId 找回该轮的完整上下文。Room Migration v9→v10。

**LLM failover chain**：`LlmClient.generateWithFailover()` 按顺序尝试主 provider + 备选 providers，第一个成功即返回。`TalkScreen.submit()` 自动收集所有已配置 Key 的 providers 作为备选；主 provider 失败时自动切换。

**苏格拉底式追问 prompt**：升级 system prompt 为 7 条原则的苏格拉底式追问，不直接给结论，通过反问帮用户发现自己的偏好与思考路径。

### 第二轮 · 多平台真实 connector + WorkManager 后台主动发现 + 跨机器迁移

**YouTube connector 重写**：从 v1.9.6 的 12 个硬编码 video ID + oEmbed（30 天不会更新）改为 8 个高活跃频道 RSS feed（Kurzgesagt / Vsauce / Vox / Y Combinator / The Futur / Marques Brownlee / Pick Up Limes / 集合）。纯公开 RSS，无需 API Key、不读 Cookie。

**Twitter/X connector 重写**：从 `api.twitter.com/1.1/trends/place.json`（需 Bearer token，会失败）改为 Nitter 多实例 RSS fallback（privacydev.net → poast.org → nitter.cz）。全部失败时返回空 list，不影响其他平台。

**通用 Web connector**：新建 `WebPublicConnector.kt`，用 Jsoup 解析 Hacker News / Lobsters / Techmeme 首页，对齐 OpenBiliClaw 的 `web_adapter`。

**WorkManager 后台主动发现**：新建 `BackgroundDiscoveryWorker.kt`。PeriodicWorker 每 1-24h 执行（默认 6h，用户可选 1/3/6/12h）。流程：取 Top 3 兴趣 → 按主题映射平台 → 调 connector → AdaptiveRanking 评分 → 写入候选 → 发本地通知。通知 channel: `aulune-discovery` (IMPORTANCE_LOW)，点击直达 MainActivity。Constraints: 网络可用 + 不低电量。失败 LINEAR backoff 30min。

**本地通知权限**：AndroidManifest 加 `POST_NOTIFICATIONS`（Android 13+）+ `RECEIVE_BOOT_COMPLETED`。

**跨机器迁移（导出/导入 backup）**：新建 `BackupManager.kt`（479 行）。
- 备份格式：`aulune-backup-YYYYMMDD-HHmmss.obcbackup` JSON 文件
- 含 13 张表所有数据（内容 / 行为 / 兴趣 / 反馈 / 画像 / 偏好 / 对话 / 候选 / 避雷 / 心理 / 已看账本 / 发现任务 / 来源可用性）
- **不含 API Key、Cookie、令牌**
- 导入用 `Room.withTransaction` 保证原子性（清空+写入要么全成功要么全回滚）
- 导入前先 validate 显示预览 + 二次确认对话框
- 用 SAF `CreateDocument` / `OpenDocument` 走系统文件选择器

### 数据库迁移

Room 版本 v8 → v10（两次迁移）：
- v8→v9：新建 3 张表（`local_avoidance_hypothesis` / `local_psychological_profile` / `local_viewed_ledger`）
- v9→v10：`local_chat_message` 加 `turnId` 列

### 新增依赖

- `androidx.work:work-runtime-ktx:2.10.0` — WorkManager 后台任务
- `org.jsoup:jsoup:1.18.1` — 通用 Web HTML 解析

### 新增文件（6 个）

- `AvoidanceProbe.kt` — 避雷探针
- `PsychologicalProfile.kt` — MBTI / 认知风格 / 深层需求 / 人格素描
- `ViewedLedgerEntity.kt` — 30 天持久化已看账本
- `SocraticPromptPolicy.kt` — 苏格拉底式追问 prompt
- `WebPublicConnector.kt` — 通用 Web Jsoup 抓取
- `BackgroundDiscoveryWorker.kt` — WorkManager + 本地通知
- `BackupManager.kt` — 跨机器迁移

### 修改文件（7 个）

- `LocalCore.kt` — DAO 加 39 个方法（dump + clear + bulkInsert × 13 张表），Repository 加 8 个方法，VM 加 8 个方法，2 次 Migration
- `MainActivity.kt` — 避雷/心理画像/换一批/后台发现/备份 5 张新卡片，imports 加 9 个 icon
- `LlmClient.kt` — `generateStream` 流式 + `generateWithFailover` failover chain + 苏格拉底 prompt
- `LocalLibraryAndConversation.kt` — `send()` 流式 + failover + durable turnId
- `MultiPlatformConnectors.kt` — YouTube RSS + Twitter Nitter
- `ProfileGuidedExploration.kt` — bridges 7→19
- `MultiPlatformContract.kt` — LibrarySection.Removed

### 代码量

Kotlin 总行数 14,722 → **16,802** (+2,080 / +14%)；文件数 58 → **65** (+7)；新增依赖 2 个；新增权限 2 个。

## v1.9.6 — Gemini 画像候选与日志诊断

为 Gemini 云端画像候选请求启用原生 JSON 响应模式，普通对话继续使用文本响应模式；同时保留代码围栏、前后说明和常见非标准 JSON 的容错解析。不可恢复时仍只保留本机候选，不会把异常内容写入长期画像。

设置页新增“日志设置”，可查看、复制和清除最近的脱敏运行日志。日志记录模型调用、云端画像候选、本机 Agent 和对话的成功或失败状态，不记录 API Key、Cookie、令牌或完整对话内容。

## v1.9.5 — 画像页 Agent 卡片布局修复

修复本机画像页“本地 Agent 认知闭环”卡片出现内层浅色矩形错位、背景叠加和内容区域边界不一致的问题。现在使用单一连续卡片背景，并统一宽度、圆角、内边距和按钮宽度。

五层认知内容改为纵向分组展示，增加标题与说明之间的间距，统一多行文本行高，避免双栏内容在窄屏上挤压或发生视觉截断。Agent 认知、候选确认、云端调用和联网触发逻辑不变。

## v1.9.4 — 云端画像候选 JSON 兼容性

针对 OpenRouter 普通对话正常、但“用 OpenRouter 更新画像候选”失败的情况，增强画像候选专用响应解析。现在可处理模型常见的 Markdown 代码围栏、前后解释文字、JSON 尾逗号、单引号字符串和未加引号的简单字段名；无法恢复时会显示“请重试或更换支持结构化输出的模型”的明确提示。

本修复只作用于云端画像候选和内容分析，不改变普通对话的请求与显示逻辑，也不改变用户主动点击后才调用云端模型的边界。

## v1.9.3 — OpenRouter 错误处理与对话复制

修复 OpenRouter 和其他 OpenAI 兼容接口返回错误对象、空响应、缺失 `choices` 或空 `choices` 时直接抛出原始异常的问题。现在会区分 HTTP 错误、无法解析的响应、缺失候选、空内容和模型缺少 `message` 等情况，并尽可能显示服务商返回的错误码与说明，不再直接显示 `No value for choices`。

云端画像候选和内容分析改用严格 JSON 解析；模型返回 Markdown 代码围栏、前后说明文字时仍可提取对象，JSON 语法无效时会保留本机规则结果并显示可读失败信息。

对话消息新增“复制”按钮，用户可将用户消息或模型回复复制到系统剪贴板；复制操作不记录 API Key 或消息内容日志。

## v1.9.2 — API Key 显示与隐藏

模型配置页的 API Key 输入框新增右侧小眼睛按钮。默认状态仍为隐藏，点击后可在明文和隐藏状态之间切换；再次点击即可恢复隐藏。该按钮只改变当前页面的显示方式，不改变 API Key 的 Android Keystore 加密保存、自动草稿保存或敏感日志边界。

## v1.9.1 — 画像页排版可读性修复

本版本优化本机画像页的视觉层级：将“本机画像”从过大的展示字号调整为更紧凑的页面标题，并增加标题与说明文字之间的留白和行高，避免标题区拥挤。

“本地 Agent 认知闭环”卡片现在增加卡片内段落间距、五层记忆行之间的呼吸空间，并将多行说明统一为更舒适的行高；不改变 Agent 认知、候选确认或联网触发逻辑。

## v1.9.0 — 本机 Agent 认知闭环

v1.9.0 将 Aulune 从“本机规则推荐 + 可选云端分析”扩展为可运行、可审计的本机 Agent 闭环。画像页新增“本地 Agent 认知闭环”卡片；用户点击后，应用会整理本机行为事件、偏好、兴趣候选、已确认洞察和长期画像，并展示 Event、Preference、Awareness、Insight、Soul 五层认知状态。

信息流现在通过统一的 Agent 候选评估器排序。评估会综合主题匹配、兴趣生命周期、来源新颖性、内容摘要完整度、缩略图可用性、近期重复疲劳、用户意图以及正负反馈，并提供可解释理由；这仍是本机确定性规则，不会伪装成云端训练模型。

兴趣推测继续遵循确认式闭环：行为或用户主动选择的对话主题只能生成 Awareness 候选；用户确认后才进入兴趣层，拒绝或过期候选不会自动升级。画像页的云端 AI 入口仍只在用户主动点击时调用，并且只发送最小化的聚合兴趣和当前任务信息。跨平台公开发现先形成画像引导计划，再由用户再次点击执行；没有后台联网、周期抓取、OpenBiliClaw 后端、本地 embedding 或 TXT 明文备份。

新增 JVM 回归测试覆盖五层快照、待确认候选、候选评估理由和既有配置恢复逻辑。

## v1.8.7 — 关键操作流程与云端配置安全优化

v1.8.7 修复内容库空状态的“去首页”入口、对话页模型设置直达和未配置云端模型的明确提示。模型工作台要求先填写 API Key 才能启用云端调用；保存时不会在切换服务商后错误继承上一服务商的 Key。清除操作只删除当前服务商的本机加密配置，并在需要时停止对应增强。

本次 GitHub v1.8.7 使用临时证书，不能覆盖安装既有固定发布证书版本；v1.9.0 仍需按发布说明处理签名不一致情况。

## v1.8.6 — 模型配置持久化修复

v1.8.6 修复模型工作台在关闭或重启应用后可能显示为空的问题。服务商档案和已启用云端配置现在都会在用户保存、获取模型列表或从列表选中模型时同步写入 Android Keystore 加密偏好；启动时会从已保存的云端配置回填缺失的服务商档案，且不会覆盖已有的有效配置。

API Key、模型名称、接口地址和协议仍只保存在本机加密存储中，不会进入日志、源码归档或 GitHub Release。要关闭云端调用可继续使用“仅使用本机规则”；要删除 Key 需主动点击“清除 Key”。

## v1.8.5 — 模型搜索与统一公开内容获取

v1.8.5 将“获取模型列表”的结果改为自动打开的可搜索、可上下滚动模型选择弹窗。模型按名称去重并支持大小写无关的实时过滤；点选后会回填模型名称，同时继续保留手动填写模型名的方式。

首页将 B 站公开热门并入统一的“获取内容”动作。用户单次点击会按顺序手动请求 B 站和其他支持的公开来源，并在界面分别展示来源结果；该动作不读取账号 Cookie。账号同步仍保持独立、显式授权的入口，按画像探索仍保持先展示计划、再由用户点击联网。

## v1.8.4 — 数据库升级稳定性修复

v1.8.4 修复兴趣候选数据表在已有本机数据库升级时的 Room 索引架构不一致问题。此前从 v1.8.3 测试版升级后，Room 可能在启动时校验迁移失败并导致应用退出；本版本使实体声明与 v7→v8 迁移创建的索引完全一致。现有内容、行为、兴趣、画像和对话数据保持原地迁移，不需要清除应用数据。

本版本继续保持画像探索和对话偏好提取均由用户手动触发；没有增加后台联网、定时任务或自动化跨平台抓取。

## v1.8.3 — 画像引导探索与可确认兴趣候选

v1.8.3 在本机画像中加入可审阅的兴趣候选。候选只来自已积累的行为主题与用户主动选择提取的对话主题；每项候选都会显示来源、理由和证据数，必须由用户确认才会写入兴趣，拒绝或过期的候选不会自动升级。

首页新增“按我的画像探索”。应用会先展示当前关注主题与建议的公开来源；只有用户点击“确认并按画像探索”后，才从这些来源导入公开候选并在本机重排。该能力没有后台任务、定时刷新或自动联网，也不会读取账号 Cookie。对话页的“从我的对话提取兴趣候选”同样必须由用户点击，且仅保存提取后的候选主题而非将对话原文写入画像证据。

本次 GitHub 的 `v1.8.3-debug` 为调试签名测试发布，不能覆盖安装固定发布证书签名的正式 APK；固定签名材料恢复前不会将该测试包描述为可无缝升级的正式版。

## v1.8.2 — 信息流来源、真实缩略图与本机推荐优化

v1.8.2 重新划分首页入口：B 站热门仅导入匿名公开内容；“其他公开来源”只在用户点击后探索抖音、小红书、知乎、微博等平台，明确跳过 B 站以避免重复；账号入口用于用户主动同步账号可用的收藏、观看历史与稍后再看等证据。所有来源探测继续不含后台、定时或周期性联网。

当公开来源在响应中提供安全的 HTTP/HTTPS 封面地址时，信息流会将地址持久化并加载真实缩略图；未提供封面的热榜条目会显示明确的“暂无封面”回退。多平台来源键现按平台归一化，使来源疲劳和探索排序能够跨同一来源的候选项实际生效。正向作者、主题组、系列与单条内容反馈，及主题组负反馈，均已接入本机排序。

本次 GitHub 的 `v1.8.2-debug` 为调试签名测试发布，不能覆盖安装固定发布证书签名的正式 APK；固定签名材料恢复前不会将该测试包描述为可无缝升级的正式版。

## v1.8.1 — 稳定性、主题与显式联网边界

v1.8.1 将信息流和内容库的外部链接启动统一收敛到只允许带主机名的 HTTP/HTTPS 地址，并在启动外部处理程序时容错，避免异常链接或设备缺少处理程序导致应用退出。导入操作的触觉反馈同样不会再影响主流程。

本版本保留深色、浅色和跟随系统的 Material You 主题，并补齐夜间 Edge-to-Edge 系统栏资源及安全的 `Activity` 上下文解析。手动来源探测入口恢复在首页；它只会在用户点击后联网，不包含后台、定时或周期性探测。固定发布签名、包名 `app.aulune.mobile` 和干净的可见版本号 `1.8.1` 保持不变。

## v1.7.0 — 手动云端智能整理

v1.7.0 在信息流增加“云端智能整理”控制台。用户明确点击后，应用才会依次选取最多五条尚未经过云端分析的可见内容，并向用户已启用的服务商发送该条内容的标题、摘要、来源和当前规则主题。服务商返回的主题、主题组、系列线索与候选理由会保存到本机；整理失败的内容不会被删除或降级，仍按本机规则参与排序。

这一能力不启动后台、定时或周期性 AI 请求，不下载本地聊天模型、embedding 或视觉模型。云端调用不会发送登录 Cookie、账号令牌、原始观看记录或设备标识；用户未配置或未启用 API Key 时，所有内容继续使用本机规则。长期画像候选仍须由用户确认后才写入。

## v1.6.1 — 精简模型工作台与默认接口格式

v1.6.1 已从“模型”页移除 B 站账号连接区，使模型配置界面只保留服务商、Key、可编辑 HTTPS 基础地址、模型目录和手动模型名等 AI 配置内容。B 站账号能力仍仅在其原有的本机内容与账号入口中提供，不再占用模型工作台空间。

预设服务商不再向用户展示协议选择。OpenAI、DeepSeek、智谱 GLM、Kimi 与 OpenRouter 自动使用 OpenAI Chat Completions 兼容格式；Claude 自动使用 Anthropic Messages；Gemini 自动使用 Gemini GenerateContent。只有“自定义”服务商保留协议选择；同时对已保存的历史配置强制回归对应预设的默认格式。默认协议的官方资料链接已整理至 `docs/model-providers.md`。

## v1.6.0 — 手动来源探测与本机任务账本

v1.6.0 增加“手动来源探测”控制台。用户点击“立即探测公开来源”后，Aulune 才会按现有公开连接器执行探测，并将每个来源的可用性、发现数量、错误分类、重试结果与最近任务状态保存到应用私有数据库。网络、限流、服务端、登录和数据格式异常继续复用既有可靠性分类和最多三次退避规则。

本版本**不注册后台、定时或周期性来源探测**，不包含服务端、自动 Agent、本地模型或向量嵌入；所有联网探测均须由用户明确点击发起。Cookie 加密存储与敏感日志边界仍按既定要求保持暂缓，未作改动。

## v1.5.0 — 多服务商模型工作台

v1.5.0 新增**智谱 GLM（Z.AI）**、**Kimi（Moonshot）**、**OpenRouter**和**自定义服务商**。每个服务商均可独立保存 API Key、模型名称、HTTPS 接口基础地址与调用协议；模型工作台支持在线获取模型列表并点选，也始终保留手动填写模型名的方式。请求适配 OpenAI 兼容、Anthropic Messages 与 Gemini GenerateContent 三种协议，普通对话、内容 AI 解析和画像候选会复用已保存的服务商配置。

配置使用 Android Keystore 加密偏好保存于设备本机。此版本继续保持独立本地内容应用路线；不包含本地模型或向量嵌入，以避免增加设备内存占用。安装包由固定发布证书签名并通过 GitHub Release 发布。

## v0.6.0 — 画像、信息流与自进化图标

v0.6.0 将启动器图标更新为“汇入、理解、成长”的抽象标记：中心圆核代表用户画像，左下三条曲线代表被筛选后汇入的推荐信息流，右上开放弧线与三枚节点代表 AI 随使用反馈持续演进。图标采用深靛蓝底、薄荷绿与青蓝的独立配色，并已适配完整 Android 启动器密度资源。安装包：`releases/v0.6.0/Aulune-v0.6.0.apk`。

## v0.5.0 — 独立简约启动器图标

v0.5.0 替换了 Aulune 的启动器图标，采用深午夜蓝背景上的抽象极光交织符号：两条不对称圆润光带围绕中心菱形留白展开，并使用象牙白至薄荷青渐变。图标已生成 `mdpi` 至 `xxxhdpi` 全部启动器资源，不含文字，也不复制或关联任何第三方品牌标志。安装包：`releases/v0.5.0/Aulune-v0.5.0.apk`。

## v0.4.0 — 四方式官方认证入口


Cookie 入口仅展示会话凭据风险与官方登录引导，不支持粘贴、验证、导入、导出或保存 Cookie。Aulune 不读取、不记录账号密码、短信验证码、`SESSDATA`、`bili_jct`、CSRF 或访问令牌。安装包：`releases/v0.4.0/Aulune-v0.4.0.apk`。

## v0.3.0 — 官方网页账号管理

v0.3.0 在多模型工作台中加入了 B 站账号管理入口。用户可以在受控的官方网页会话中访问账号中心、创作中心和消息页，并使用刷新、外部浏览器打开和清除网页会话等操作。应用不读取、不导出 B 站 Cookie、CSRF 或访问令牌，也不提供密码、短信验证码或 Cookie 导入表单。

本版本同时保留 v0.2.0 的 OpenAI、Claude、Gemini 和 DeepSeek 文本对话能力。安装包：`releases/v0.3.0/Aulune-v0.3.0.apk`。

## v0.2.0 — 多模型工作台

v0.2.0 将 Aulune 从离线原型扩展为可配置的多模型 Android 工作台。用户可以为 OpenAI、Claude、Gemini 和 DeepSeek 分别填写 API Key 与模型名称，并在对话页面中选择服务商发起 HTTPS 直连请求。Key 只保留在应用内存中，退出进程后清除。

本版本尚未包含 B 站账号管理。安装包：`releases/v0.2.0/Aulune-v0.2.0.apk`。

## 版本边界

采用 Aulune 固定发布证书签名的正式 Release，以递增内部版本号支持同包名覆盖升级。调试签名测试包会在 Release 中单独标注，不能覆盖安装正式 APK；使用者仍应在真实设备上完成隐私、安全、网络兼容性和服务商条款评审。
