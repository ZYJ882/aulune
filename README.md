# Aulune Android

> **Aulune** 是一个以“理解你、整理信息、持续成长”为产品方向的 Android 本地 AI 洞察工作台。

Aulune 使用 **Kotlin、Jetpack Compose 与 Material 3** 独立实现，应用包名为 `app.aulune.mobile`。它将个人画像、推荐信息流、多模型文本对话与受控网页账号入口组合在一个本地原生应用中。当前仓库面向学习、原型验证和个人设备测试公开；不包含任何真实 API Key、账号凭据或服务端密钥。

## 项目名称与视觉含义

“Aulune”由 *Aura*（围绕个体的感知与氛围）和 *Analysis*（分析与理解）联想而来。v0.6.0 的图标以一个中心圆核、左下三条汇入曲线、右上开放生长弧线及渐进节点组成，分别代表**个人画像**、**推荐信息流**与**AI 随反馈持续演进的方向**。

## 参考来源与改造关系

本项目是独立实现，**不是**下列项目的 fork，也不包含其源码、图标或账号凭据。功能方向、界面信息架构和安全取舍参考了以下公开项目：

| 参考项目 | 在 Aulune 中的参考点 | Aulune 的改造方式 |
|---|---|---|
| [OpenBiliClaw](https://github.com/whiteguo233/OpenBiliClaw) | 本地优先、用户理解、跨来源内容发现的产品方向 | 改造为 Android 原生的本地画像、主题与信息流原型；未移植其 Python Agent、采集器或服务端 |

> Aulune 与上述项目及哔哩哔哩均无隶属、赞助或官方合作关系。若未来使用任一参考项目的源码、资源或商标，应自行核对并遵守其许可证、平台条款及适用法律。

## 已实现能力

| 模块 | 当前实现 |
|---|---|
| 本地洞察工作台 | 本地主题内容卡、规则排序、收藏/标记、偏好画像和关注主题展示 |
| 本地内容库 | 稍后、标记、最近打开与已隐藏内容可本机查看、恢复和重新整理 |
| 持久化对话 | 对话历史保存在应用私有 Room 数据库，重启后可继续；调用模型仅在用户配置 Key 并发送后发生 |
| 本地画像反馈 | 长期方向与核心边界可确认写入，也可“重新观察”以由后续本机行为重新生成候选 |
| 多平台导入 | B站提供公开与账户内容读取；其余来源标记为试验性直连，并显示公开/账户/内容能力矩阵 |
| 来源可靠性反馈 | 对网络、登录、限流、服务端和格式错误分类展示；可重试错误最多自动退避重试 3 次 |
| 手动来源探测与任务账本 | 仅在用户点击“立即探测公开来源”后联网，按来源保存可用性、结果、失败原因和最近任务记录；不会注册后台或周期性联网任务 |
| 多服务商云端模型 | 可配置 OpenAI、Claude、Gemini、DeepSeek、智谱 GLM、Kimi、OpenRouter 与自定义服务商；每项均可编辑 HTTPS 基础地址、选择协议、获取模型列表或手动填写模型名 |
| 自定义品牌资源 | 独立应用名称、包名、主题与 Android 多密度启动器图标 |
| 自动构建与发布 | 每次主分支更新或受控源码压缩包上传都会自动构建固定签名 APK、递增内部版本并创建 GitHub Release |

## 已复刻与未复刻的范围

| 范围 | 状态 | 说明 |
|---|---|---|
| B 站官方网页账号管理 | 已实现 | 使用受控 HTTPS WebView，支持官方账号中心、创作中心、消息页、刷新和清除网页会话 |
| OpenBiliClaw 的“本地优先 + 了解用户”产品方向 | 已实现为原型 | 通过本地画像、主题卡和多模型工作台表达该方向 |
| OpenBiliClaw 的跨平台内容采集与自进化 Agent | 未实现 | Aulune 不接入 Python Agent、浏览器扩展、后台自动发现、五层灵魂画像或服务端调度；公开来源探测仅由用户手动触发 |
| 多平台公开导入与账号读取 | 部分实现 | Android 端提供若干直连连接器与 WebView 授权入口；结果受平台接口、登录、限流和网络条件影响 |
| 多账号会话、自动投稿、自动评论 | 未实现 | 不提供自动化账号操作；账户读取仅面向用户主动发起的本机导入 |
| 服务端、云同步、账户体系 | 未实现 | 当前版本不含 Aulune 自有后端，也不提供跨设备同步 |
| 固定签名 Android 发布 | 已实现 | GitHub Actions 使用固定发布证书构建；同包名且更高内部版本号可覆盖升级 |

## 版本下载

每个版本的 APK 既作为独立 GitHub Release 资产发布，也在仓库 `releases/` 目录保留副本。请优先从 Release 页面下载安装。

| 版本 | 主要内容 | APK |
|---|---|---|
| `v0.6.0` | 更新为“画像核心、汇入信息流与 AI 生长轨迹”图标 | [Aulune-v0.6.0.apk](releases/v0.6.0/Aulune-v0.6.0.apk) |
| `v0.5.0` | 独立简约启动器图标 | [Aulune-v0.5.0.apk](releases/v0.5.0/Aulune-v0.5.0.apk) |
| `v0.4.0` | 四方式官方认证引导入口 | [Aulune-v0.4.0.apk](releases/v0.4.0/Aulune-v0.4.0.apk) |
| `v0.3.0` | 官方网页账号管理入口 | [Aulune-v0.3.0.apk](releases/v0.3.0/Aulune-v0.3.0.apk) |
| `v0.2.0` | OpenAI、Claude、Gemini、DeepSeek 多模型配置与文本对话 | [Aulune-v0.2.0.apk](releases/v0.2.0/Aulune-v0.2.0.apk) |


## 安全与数据边界

Aulune 的 B 站能力使用隔离的官方网页会话。登录、二维码、短信、密码和安全验证由 B 站官方页面完成；Aulune 不提供密码或 Cookie 导入表单，也不读取、不导出 `SESSDATA`、`bili_jct`、CSRF 或访问令牌。内嵌页面仅允许 HTTPS 下的 B 站可信域名导航；“清除网页会话”只清除 Aulune 容器中的 Cookie，不影响官方 B 站客户端。

多模型请求会从设备 HTTPS 直连选定服务商。API Key、模型名、基础地址与协议使用 Android Keystore 加密保存在本机；模型列表获取失败时仍可手动填写模型名。使用者只应填写自己拥有并获授权使用的 API Key，并自行承担相关服务商的数据处理、用量和计费责任。Aulune 不包含本地模型或向量嵌入功能，以避免额外的设备内存占用。详细接口适配和官方资料见 [多服务商接入说明](docs/model-providers.md)。

公开来源探测也只在用户明确点击“立即探测公开来源”时进行。结果、错误分类和最近任务记录保存于应用私有数据库；应用不安排后台、定时或周期性来源探测。

## 构建与安装

环境要求为 **Android SDK 35、Java 17、Android 8.0（API 26）或更高版本**。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Release 中的 APK 使用 Aulune 固定签名密钥构建。安装固定签名版本后，后续同包名且内部版本号更高的 APK 可直接覆盖升级。

## 工程结构

```text
aulune/
├── app/src/main/java/app/aulune/mobile/
│   ├── MainActivity.kt             # Compose 主界面、导航和交互
│   ├── AppData.kt                  # 本地状态、模型配置与离线内容
│   ├── LlmClient.kt                # 可编辑端点的多协议模型调用与目录解析
│   ├── BackgroundDiscovery.kt      # 仅手动触发的来源探测和任务账本模型
│   ├── BilibiliLoginActivity.kt    # 四方式认证入口的 Compose 引导页
│   └── BilibiliWebActivity.kt      # 受控官方网页账号管理容器
├── assets/                          # 独立品牌图标源图
├── docs/model-providers.md         # 多服务商协议、端点与官方资料
├── docs/releases/                  # 各版本发布说明
├── releases/                        # 各版本 APK 副本
├── PROJECT_SCOPE.md                 # 参考来源、功能映射和未实现边界
└── CHANGELOG.md                     # 版本变更记录
```

## 许可证

本仓库的 Aulune 自主代码以 [MIT License](LICENSE) 发布。参考项目及其各自资产仍受其原始许可证约束，不因本仓库公开而改变。
