# Aulune 自动构建与发布

本仓库提供两条自动化路径，均会使用 Java 17 构建 Android Debug APK，并将构建结果上传为 GitHub Release 资产。

| 使用场景 | 操作方式 | 自动结果 |
|---|---|---|
| 上传外部源码压缩包 | 在 `source-upload` 分支的 `incoming-source/` 目录上传一个 `.zip`、`.tar.gz` 或 `.tgz` 源码包并提交 | 工作流安全解压，定位 Gradle 工程，运行构建和单元测试，发布带 APK 与校验信息的新 Release |
| 提交代码修改 | 从本仓库分支创建非草稿 PR | Android 构建成功后自动 Squash 合并；合并到 `main` 后自动构建并发布稳定版本 |

## 源码压缩包要求

压缩包必须只包含一个 Android Gradle 工程，且工程内存在 `gradlew` 和 `app/build.gradle.kts`。工作流拒绝超过 5,000 个文件、包含绝对路径或上级目录路径、或包含符号链接的压缩包。一次提交只允许 `incoming-source/` 中存在一个压缩包。

## 上传步骤

首先在 GitHub 中创建或切换到 `source-upload` 分支，然后把源码压缩包上传到 `incoming-source/`。提交后，GitHub Actions 将自动运行。构建完成后，从仓库的 Releases 页面下载 APK；该分支不会自动把压缩包或其源码合并到 `main`。

## PR 自动合并边界

自动合并仅处理由本仓库账户创建的、非草稿、状态为可合并的 PR。来自外部 Fork 的 PR 不会自动合并。PR 合并后触发主分支构建；若当前 `versionName` 对应的发布标签已经存在，则 APK 会作为工作流产物保留，而不会覆盖既有正式 Release。

## 固定签名与覆盖安装

自动工作流不会使用每次运行临时生成的调试证书，而是要求仓库配置固定签名密钥。管理员需要在 **Settings → Secrets and variables → Actions → New repository secret** 中配置以下三个 Repository secrets：

| Secret 名称 | 内容 |
|---|---|
| `AULUNE_SIGNING_KEYSTORE_BASE64` | 固定 `.jks` 文件经过 Base64 编码后的单行内容 |
| `AULUNE_SIGNING_STORE_PASSWORD` | Keystore 密码 |
| `AULUNE_SIGNING_KEY_PASSWORD` | `aulune-release` 别名对应的密钥密码 |

工作流会在 Runner 临时目录还原密钥，Gradle 使用 `aulune-release` 签名配置生成 APK，构建结束后 Runner 销毁。只有使用同一签名证书构建的 APK 才能覆盖安装在设备上的旧 APK；如果更换签名证书，必须先卸载旧应用，或继续保留原密钥用于后续更新。

## 自动版本与升级

每次主分支或源码压缩包构建都会自动计算唯一内部版本号：`versionCode = 100000000 + GitHub Actions run number`。对用户可见的 `versionName` 始终保持项目基础版本，例如 `1.8.1`，不会附加 `+build` 后缀。Release 标签同时包含基础版本和运行号，因此不会因重复标签跳过发布。固定签名保持不变，且新 APK 的 `versionCode` 高于旧版本时可以直接覆盖安装。第一次从旧临时调试签名切换到固定签名版本仍需卸载一次。
