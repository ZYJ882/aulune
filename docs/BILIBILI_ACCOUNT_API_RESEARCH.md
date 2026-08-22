# B 站账户读取接口调研记录

本文件记录本次实现所依据的外部接口资料，仅作为数据适配参考，不复制任何参考项目代码。

## 主要接口与授权边界

| 数据 | 接口 | 认证/参数要点 |
|---|---|---|
| 登录用户信息 | `GET https://api.bilibili.com/x/web-interface/nav` | 参考文档标明仅 Cookie（SESSDATA）认证；成功响应含 `isLogin`、`mid`、`uname`、`face`、等级和会员状态 |
| 收藏夹列表 | `GET https://api.bilibili.com/x/v3/fav/folder/created/list` | 需要 `up_mid`、`pn`、`ps`；读取用户收藏夹时需要登录权限 |
| 收藏夹内容 | `GET https://api.bilibili.com/x/v3/fav/resource/list` | 需要 `media_id`、`pn`、`ps`；返回 `medias`，其中包含 `bvid`、标题、简介、作者、时长和收藏时间 |
| 观看历史 | `GET https://api.bilibili.com/x/web-interface/history/cursor` | Cookie（SESSDATA）认证；使用 `ps`、`max`、`view_at`、`business` 分页 |
| 稍后再看 | `GET https://api.bilibili.com/x/v2/history/toview` | 参考项目的历史/稍后再看接口文档中列出；本实现仅读取首屏 |

## 授权与安全结论

B 站开放平台页面说明其账号授权、用户管理和用户授权数据属于正式开放能力；若后续取得正式 Android OAuth/SDK 资格，应该优先迁移到正式授权链路。当前 Aulune 仍使用官方 WebView 登录，因此本实现将 Cookie 读取设为**用户主动点击授权同步后的内存临时桥接**：不在磁盘保存、不显示原始 Cookie、不导出、不上传、不执行写操作。

## 参考来源

1. B 站开放平台文档中心：账号授权、用户管理、数据开放和 Android SDK。https://openhome.bilibili.com/doc
2. Bilibili API Collect：登录基本信息（`/x/web-interface/nav`）。https://github.com/renovate-bot/catlair-_-bilibili-API-collect/blob/master/login/login_info.md
3. Bilibili API Collect：收藏夹内容（`/x/v3/fav/resource/list`）。https://github.com/renovate-bot/catlair-_-bilibili-API-collect/blob/master/fav/list.md
4. Bilibili API Collect：观看历史（`/x/web-interface/history/cursor`）。https://github.com/renovate-bot/catlair-_-bilibili-API-collect/blob/master/history%26toview/history.md
5. bilibili-api 开发文档：说明 Credential 需要 SESSDATA、bili_jct、buvid3，并提示接口可能变化。https://nemo2011.github.io/bilibili-api/

## 重要限制

这些接口资料属于第三方整理或开放平台资料，接口可能变更、需要额外签名或受风控限制。Aulune 当前实现将账户读取限定为首屏只读同步，并在请求失败时提示用户重新授权或稍后重试；不会绕过登录、验证码或风控。
