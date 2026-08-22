# Aulune 云端模型服务商接入说明

> 本功能仅调用用户主动配置的**云端 API**。Aulune 不包含本地模型或向量嵌入功能，以控制移动设备内存占用。

Aulune 1.6.1 将服务商的 API Key、模型名称、接口基础地址和必要的协议配置保存在当前设备的 Android Keystore 加密偏好中。用户可以为每个服务商分别保存配置，并可随时修改预设基础地址。预设服务商会按官方默认接口格式自动调用；**只有“自定义”服务商显示协议选择**。普通对话、内容 AI 解析和画像候选会使用保存时选定的同一服务商配置。

| 服务商 | 默认基础地址 | 默认协议 | 模型目录行为 |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | OpenAI 兼容 | `GET /models` |
| Claude | `https://api.anthropic.com/v1` | Anthropic Messages | `GET /models` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta` | Gemini GenerateContent | `GET /models?key=…` |
| DeepSeek | `https://api.deepseek.com` | OpenAI 兼容 | `GET /models` |
| 智谱 GLM（Z.AI） | `https://api.z.ai/api/paas/v4` | OpenAI 兼容 | 尝试 `GET /models`；失败时可手动填写 |
| Kimi（Moonshot） | `https://api.moonshot.ai/v1` | OpenAI 兼容 | `GET /models` |
| OpenRouter | `https://openrouter.ai/api/v1` | OpenAI 兼容 | `GET /models` |
| 自定义 | 用户填写 | 用户选择 | 按所选协议尝试标准目录路径；失败时可手动填写 |

## 使用方式

在“模型”页选择服务商后，填写自己的 API Key。接口基础地址为普通可编辑字段；若服务商使用兼容网关，可将其修改为网关提供的 HTTPS 基础地址。对于 OpenAI、Claude、Gemini、DeepSeek、智谱 GLM、Kimi 与 OpenRouter，应用会自动采用表中的官方默认格式；仅选择“自定义”时才需要选择调用协议。随后可填写模型名，或点击“获取模型列表”后点选返回的模型。

模型目录只是辅助选择功能。目录请求失败、服务商未提供目录，或账户无目录权限时，**不会阻止保存和使用手动填写的模型名称**。Aulune 不会将 API Key 写入安装包、源码、Git 历史或自身服务器。模型请求由设备通过 HTTPS 直接发送至用户填写的服务商或网关；服务商的数据处理、使用条件与费用由用户自行确认。

## 协议适配

| 协议 | 对话请求 | 鉴权 | 响应文本 |
|---|---|---|---|
| OpenAI 兼容 | `POST /chat/completions` | `Authorization: Bearer …` | `choices[0].message.content` |
| Anthropic Messages | `POST /messages` | `x-api-key` 与 `anthropic-version: 2023-06-01` | `content[]` 中的文本块 |
| Gemini GenerateContent | `POST /models/{model}:generateContent?key=…` | 查询参数 `key` | `candidates[0].content.parts[]` |

端点输入应使用 HTTPS。对于 OpenAI 兼容、Anthropic 与 Gemini 自定义网关，使用者须根据其网关文档在“自定义”服务商中选择适当协议，并填写正确的基础地址和模型名。预设服务商不会显示协议切换控件。

## 官方资料

以下链接用于确认默认服务的公开接口形状；模型可用性、账号权限和具体模型名称以服务商账户及其最新文档为准。

1. [OpenAI Chat Completions](https://developers.openai.com/api/reference/chat-completions/overview)：对话消息列表的 Chat Completions 接口。
2. [Z.AI Quick Start](https://docs.z.ai/guides/overview/quick-start)：OpenAI 兼容的 Chat Completions 地址与 Bearer 鉴权。
3. [Kimi API Overview](https://platform.kimi.ai/docs/api/overview) 与 [Chat API](https://platform.kimi.ai/docs/api/chat)：OpenAI 兼容的 `/v1/chat/completions`、`/v1/models` 与响应结构。
4. [OpenRouter Quickstart](https://openrouter.ai/docs/quickstart)：OpenAI 兼容的统一 API 与模型目录。
5. [DeepSeek Quick Start](https://api-docs.deepseek.com/)：OpenAI 基础地址及 `/chat/completions` 默认调用示例。
6. [Claude Messages API](https://platform.claude.com/docs/en/api/messages)：Claude Messages 接口。
7. [Gemini GenerateContent Quick Start](https://ai.google.dev/gemini-api/docs/generate-content/get-started)：Gemini GenerateContent 方法与 API Key 鉴权。

本文描述的是客户端接口适配，不构成对任一云服务的可用性、费用、数据处理或兼容性的保证。
