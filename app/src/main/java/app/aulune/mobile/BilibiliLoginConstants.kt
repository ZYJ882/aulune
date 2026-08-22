package app.aulune.mobile

/**
 * B 站登录相关常量与 API 端点。
 *
 * 复刻自 PiliPlus (Flutter) 的 lib/common/constants.dart 与 lib/http/api.dart，
 * 使用 android_hd (平板 HD 版) 应用身份，与 PiliPlus 保持一致。
 */
object BilibiliLoginConstants {

    // ── 应用身份 ──────────────────────────────────────────────
    /** android_hd 版 appkey */
    const val APP_KEY = "dfca71928277209b"

    /** android_hd 版 appsec */
    const val APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    /** 构建号 */
    const val BUILD = "2001100"

    /** 移动端 HD User-Agent */
    const val USER_AGENT =
        "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd " +
            "mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2"

    /** trace-id (占位固定值，与 PiliPlus 一致) */
    const val TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0"

    /** statistics 参数 */
    const val STATISTICS = """{"appId":5,"platform":3,"version":"2.0.1","abtest":""}"""

    // ── 基础 URL ──────────────────────────────────────────────
    private const val PASS_BASE = "https://passport.bilibili.com"

    // ── 二维码登录 (TV/HD 端) ─────────────────────────────────
    /** 申请二维码 auth_code */
    const val API_GET_TV_CODE = "$PASS_BASE/x/passport-tv-login/qrcode/auth_code"

    /** 轮询扫码状态 */
    const val API_QRCODE_POLL = "$PASS_BASE/x/passport-tv-login/qrcode/poll"

    // ── Web 端密钥 ────────────────────────────────────────────
    /** 获取 RSA 公钥与 hash(salt) */
    const val API_GET_WEB_KEY = "$PASS_BASE/x/passport-login/web/key"

    // ── 验证码 ─────────────────────────────────────────────────
    /** 获取图形验证码 (main_web) */
    const val API_GET_CAPTCHA = "$PASS_BASE/x/passport-login/captcha?source=main_web"

    // ── 短信登录 (App 端) ──────────────────────────────────────
    /** 发送短信验证码 */
    const val API_APP_SMS_CODE = "$PASS_BASE/x/passport-login/sms/send"

    /** 短信验证码登录 */
    const val API_LOGIN_BY_APP_SMS = "$PASS_BASE/x/passport-login/login/sms"

    // ── 密码登录 (App 端) ──────────────────────────────────────
    /** 密码登录 (oauth2/login) */
    const val API_LOGIN_BY_PWD = "$PASS_BASE/x/passport-login/oauth2/login"

    // ── 风控 / 安全中心 ─────────────────────────────────────────
    /** 获取安全中心账号信息 (tmp_code) */
    const val API_SAFE_CENTER_INFO = "$PASS_BASE/x/safecenter/user/info"

    /** 风控前置人机验证 (获取极验参数) */
    const val API_PRE_CAPTURE = "$PASS_BASE/x/safecenter/captcha/pre"

    /** 风控发送短信验证码 */
    const val API_SAFE_CENTER_SMS_CODE = "$PASS_BASE/x/safecenter/common/sms/send"

    /** 风控验证短信验证码 */
    const val API_SAFE_CENTER_SMS_VERIFY = "$PASS_BASE/x/safecenter/login/tel/verify"

    /** 用 oauth code 换取 access_token */
    const val API_OAUTH2_ACCESS_TOKEN = "$PASS_BASE/x/passport-login/oauth2/access_token"

    // ── 退出 ───────────────────────────────────────────────────
    const val API_LOGOUT = "$PASS_BASE/login/exit/v2"

    // ── 用户信息 (登录后校验) ──────────────────────────────────
    const val API_NAV = "https://api.bilibili.com/x/web-interface/nav"

    // ── 二维码有效期 ────────────────────────────────────────────
    /** 二维码有效秒数 */
    const val QRCODE_TTL_SECONDS = 180

    /** 轮询间隔毫秒 */
    const val QRCODE_POLL_INTERVAL_MS = 1000L

    /** 短信验证码有效期毫秒 (5 分钟) */
    const val SMS_CODE_TTL_MS = 5 * 60 * 1000L

    /** 短信发送冷却秒数 */
    const val SMS_SEND_COOLDOWN_SECONDS = 60

    // ── 轮询返回码 ─────────────────────────────────────────────
    /** 扫码成功 */
    const val CODE_QR_SUCCESS = 0

    /** 二维码已过期 */
    const val CODE_QR_EXPIRED = 86038

    /** 需要极验验证码 */
    const val CODE_NEED_CAPTCHA = -105
}
