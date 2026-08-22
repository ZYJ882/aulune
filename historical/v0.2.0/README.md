# Aulune for Android

**Aulune** 是一个以灵感整理与多模型对话为中心的 Android 原生工作台。工程采用 **Kotlin + Jetpack Compose + Material 3**，提供本地优先的内容卡片、思考地图、对话工作区及可切换的多模型 API 配置。

> 包名：`app.aulune.mobile`。应用图标与名称均为独立设计；工程中不包含任何第三方服务的密钥。

## 功能概览

| 模块 | 内容 | 主要交互 |
|---|---|---|
| 灵感 | 主题化内容卡片、重点标记、保存与外部链接 | 换一组灵感、标记、保存、打开 |
| 画像 | 长期偏好、当前关注与思考方式的本地展示 | 在对话中补充或修正理解 |
| 对话 | 多轮消息、模型状态、输入与生成状态 | 通过已配置模型直接生成回复 |
| 模型工作台 | OpenAI、Claude、Gemini、DeepSeek 的 API Key 与模型名配置 | 选择提供商、填写 Key、启用模型 |

## 支持的模型服务

Aulune 使用设备上的 HTTPS 直连请求，不使用中转服务器。OpenAI 和 DeepSeek 使用兼容的 Chat Completions 形态；Claude 使用 Messages API；Gemini 使用 Generate Content API。[1] [2] [3] [4]

| 提供商 | 内置协议 | 初始模型名 | 可配置项 |
|---|---|---|---|
| OpenAI | Chat Completions | `gpt-4o-mini` | API Key、模型名称 |
| Claude | Messages API | `claude-3-5-haiku-latest` | API Key、模型名称 |
| Gemini | Generate Content | `gemini-2.5-flash` | API Key、模型名称 |
| DeepSeek | Chat Completions | `deepseek-v4-flash` | API Key、模型名称 |

模型名取决于账户权限、区域和服务商当前可用模型；可直接在“模型”页改为你的账户支持的模型名称。对于每次模型调用，Aulune 仅发送最近的有限对话上下文和必要的系统提示，不会自动上传灵感卡片或画像内容。

## API Key 安全边界

本版本将 API Key **仅保留在应用内存中**。它不会写入源码、APK、日志或第三方转发服务；退出应用进程后会被清除。由于请求从你的设备直连所选服务商，请只使用自己拥有且有权限使用的 Key，并注意相应服务商的用量、数据处理和计费规则。

## 使用步骤

首先安装 APK 后打开底部的“模型”页，选择 OpenAI、Claude、Gemini 或 DeepSeek，填写对应 API Key 和可用模型名，然后点击“启用”。随后进入“对话”页并选择模型，即可发起生成请求。没有配置 Key 时，应用仍可浏览本地工作台和体验离线内容，但不会调用外部模型。

## 构建与安装

环境要求为 Android SDK 35、Java 17 和 Android 8.0（API 26）或更高版本的设备。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

调试 APK 输出路径如下：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 工程结构

```text
aulune/
├── app/
│   └── src/main/
│       ├── java/app/aulune/mobile/
│       │   ├── MainActivity.kt  # Compose 界面、导航与交互
│       │   ├── AppData.kt       # 本地会话状态与内容数据
│       │   └── LlmClient.kt     # 四家服务的 HTTPS 调用与响应解析
│       ├── res/mipmap-*/        # Aulune 启动器图标
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 已知边界

本版本只覆盖文本对话，暂不支持流式输出、文件/图片上传、工具调用、账号登录或跨设备同步。API Key 采用内存会话模式，以避免将明文凭据持久化；如需长期保存，应在下一版本接入 Android Keystore 支持的加密存储，并在真实设备上完成安全评审。

## References

[1] [OpenAI Chat Completions API Reference](https://developers.openai.com/api/reference/chat-completions/overview/)

[2] [Anthropic Messages API Reference](https://docs.anthropic.com/en/api/messages)

[3] [Gemini API: Generate Content](https://ai.google.dev/api/generate-content)

[4] [DeepSeek Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion)
