# Aulune v1.8.1 发布说明

## 修复内容

### 1. 首页信息流点击闪退
**问题**：点击信息流卡片时 App 闪退。
**根因**：`startActivity(Intent.ACTION_VIEW)` 未做异常捕获，当 URL 格式无效或设备上无对应应用处理时，抛出 `ActivityNotFoundException` 导致崩溃。
**修复**：
- 集中 URL 校验策略：仅允许同时具有 `http`/`https` 协议和主机名的链接
- 用 `runCatching` 包裹 `startActivity` 调用
- 信息流与内容库页面的卡片点击同步接入同一策略

### 2. 导入公共信息流闪退
**问题**：点击"导入 B 站公开内容"或"全平台"按钮时 App 闪退。
**根因**：`HapticFeedback` 触觉反馈类未做异常保护，部分设备上 `Vibrator` 服务调用或 `VibrationEffect.createPredefined` 会抛出异常。
**修复**：
- `HapticFeedback` 的 `vibrator` lazy 初始化添加 `runCatching` 保护
- `click()` 和 `confirm()` 方法全部添加 `runCatching` 包裹
- 即使触觉反馈失败也不影响主流程

### 3. 其他稳定性与交互加固
- Material You 主题更新系统栏时安全解析 `Activity` 上下文，避免非 `Activity` 上下文的强制转换崩溃
- 恢复“手动来源探测”正式入口；网络探测只会在用户点击“立即探测公开来源”后执行，不包含后台或定时联网
- 登录后自动获取 B 站数据逻辑已有 try-catch 保护，确认无崩溃风险

## 版本信息
- 版本号：1.8.1
- versionCode：100000035
- minSdk：26（Android 8.0）
- targetSdk：35（Android 15）
- compileSdk：35

## 构建方式
```bash
./gradlew assembleDebug
```
或推送到 GitHub 仓库后由 GitHub Actions 自动构建。

## 已知功能
- 首页灵感信息流（B 站公开内容导入 + 全平台公开内容导入）
- 内容库（保存/标记/隐藏/恢复）
- 本机画像（核心边界/兴趣层/行为层三层画像）
- 对话页（多模型 AI 对话）
- 设置页（模型配置 + 外观主题切换 + 动态颜色）
- B 站登录（二维码/密码/短信/Cookie 四种方式，登录后自动获取账号数据）
- 多平台账号登录（WebView Cookie 登录）
- Material Design 3 / Material You 统一设计语言
- Dynamic Color（Android 12+ 跟随壁纸主题色）
- Edge-to-Edge 沉浸式布局
