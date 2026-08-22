# Android 自动构建与安全发布

Aulune 使用 GitHub Actions 工作流 [`.github/workflows/android-build-release.yml`](../.github/workflows/android-build-release.yml) 自动构建 Debug APK，并通过 [`.github/workflows/pr-preview-release.yml`](../.github/workflows/pr-preview-release.yml) 将每次成功 PR 构建发布为公开预览版本。主分支仍只在版本号变化时创建稳定 Release。

## 触发规则

| 事件 | 触发条件 | 结果 | 是否创建 Release |
|---|---|---|---|
| Pull Request | 修改 Android 源码、Gradle 配置、自动化脚本或 `incoming-source/` 内压缩包 | 构建 Debug APK，保留 14 天 Actions 工件，并创建公开预览 Release | 是，标签为 `preview-pr-<编号>-run-<运行ID>`，标记为 Prerelease |
| 推送至 `main` | 修改 Android 源码、Gradle 配置、自动化脚本或 `incoming-source/` 内压缩包 | 构建 Debug APK，保留 14 天 Actions 工件 | 仅当 `versionName` 对应的 `v<versionName>` 标签不存在时创建 |
| 手动运行 | 在 GitHub Actions 页选择 “Run workflow” | 构建 Debug APK，保留 14 天 Actions 工件 | 否；用于验证而不会误发版本 |

> PR 构建仅具有读取仓库内容的最小权限。预览 Release 由构建成功后独立运行的发布任务创建；该任务只下载已生成的 APK 工件，不检出、不执行 PR 源码，也不读取仓库 Secrets。公开预览构建可能来自外部贡献，请仅在可信测试设备中安装。

## 常规源码更新与正式发布

对仓库中的 Android 源码进行修改后，应在 `app/build.gradle.kts` 同时递增 `versionCode` 与 `versionName`。例如，将 `versionCode = 6` 与 `versionName = "0.6.0"` 改为新的连续版本。

将变更合并或直接推送到 `main` 后，工作流会执行 `:app:assembleDebug`。如果 `v<versionName>` 尚不存在，工作流会自动创建对应 Git tag、GitHub Release，并上传构建得到的 APK。若版本号没有变化，构建仍会成功，但仅作为 Actions 工件保存，不会覆盖或重复发布已有 Release。

## 源码压缩包构建

可以将一个 Android 源码 ZIP 文件提交至仓库根目录下的 `incoming-source/`，例如：

```text
incoming-source/aulune-source-0.7.0.zip
```

工作流会优先使用该目录内的 ZIP，而非仓库当前源码。压缩包必须满足以下条件：

| 要求 | 说明 |
|---|---|
| 文件数量 | 1 到 5000 个文件 |
| 目录安全 | 不允许绝对路径、`..` 路径穿越、反斜杠路径或符号链接 |
| 项目结构 | 包内任意层级必须能找到 `gradlew` 与 `app/build.gradle.kts` |
| 版本来源 | 从压缩包的 `app/build.gradle.kts` 读取 `versionName` |
| 单包限制 | 每次构建只允许 `incoming-source/` 中有一个 `.zip` 文件 |

压缩包中源码的 Gradle 构建逻辑会在 GitHub Actions Runner 中执行。因此只应将**你信任并审阅过的源码**合并到 `main`。来自 PR 的压缩包会生成公开预览 Release；只有合并到 `main` 且版本号变化后，才会生成稳定 Release。

## 获取 PR 构建 APK

每次成功 PR 构建后，会自动生成标签为 `preview-pr-<编号>-run-<运行ID>` 的 **公开 Prerelease**，其中直接附带 Debug APK。打开仓库的 Releases 页面即可下载；同时也可在对应 Pull Request 的 **Checks** 中进入 “Android build and safe release” 工作流，从 **Artifacts** 区域下载保留 14 天的构建工件。

## 发布权限与安全说明

构建任务默认仅使用 `contents: read` 权限。`main` 分支的稳定 Release 任务与 PR 预览 Release 任务都依赖构建成功后才运行。PR 预览发布任务仅下载构建工件，不会检出或执行 PR 中的源码；预览标签与正式 `v<versionName>` 标签分离。

自动发布的 APK 为 Debug 构建，并非生产签名。正式渠道发布前仍应配置独立 release keystore、签名流程、版本策略和真实设备验证。

## 参考资料

[1] [GitHub Docs — Store and share data with workflow artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data)  
[2] [GitHub Docs — Use GITHUB_TOKEN for authentication in workflows](https://docs.github.com/en/actions/reference/authentication-in-a-workflow)  
[3] [GitHub Docs — About releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
