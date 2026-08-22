package app.aulune.mobile

/**
 * B 站登录相关数据模型。
 *
 * 复刻自 PiliPlus:
 *  - lib/models/login/model.dart   (CaptchaDataModel)
 *  - lib/utils/accounts/account.dart (LoginAccount)
 */

// ═══════════════════════════════════════════════════════════════
//  账号模型
// ═══════════════════════════════════════════════════════════════

/**
 * 已登录的 B 站账号。
 *
 * @property cookies      Cookie 键值对 (SESSDATA, bili_jct, DedeUserID, buvid3 等)
 * @property accessToken  APP 访问令牌 (oauth2 返回)
 * @property refreshToken 刷新令牌
 */
data class BilibiliAccount(
    val cookies: Map<String, String>,
    val accessToken: String?,
    val refreshToken: String?,
) {
    /** 用户 mid，来自 DedeUserID Cookie */
    val mid: Long
        get() = cookies["DedeUserID"]?.toLongOrNull() ?: 0L

    /** CSRF 令牌，来自 bili_jct Cookie */
    val csrf: String
        get() = cookies["bili_jct"].orEmpty()

    /** 是否已登录 (有 SESSDATA) */
    val isLogin: Boolean
        get() = cookies["SESSDATA"]?.isNotBlank() == true

    /** 拼接为 Cookie 请求头字符串 */
    val cookieHeader: String
        get() = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    companion object {
        /** 从扫码/登录接口返回的 token_info + cookie_info 构造 */
        fun fromLoginResponse(
            tokenInfo: Map<String, Any?>,
            cookieInfo: List<Map<String, Any?>>,
        ): BilibiliAccount {
            val cookies = LinkedHashMap<String, String>()
            for (item in cookieInfo) {
                val name = item["name"]?.toString() ?: continue
                val value = item["value"]?.toString() ?: continue
                cookies[name] = value
            }
            // 确保 buvid3 存在
            if (!cookies.containsKey("buvid3")) {
                cookies["buvid3"] = BilibiliLoginUtils.generateBuvid()
            }
            return BilibiliAccount(
                cookies = cookies,
                accessToken = tokenInfo["access_token"]?.toString(),
                refreshToken = tokenInfo["refresh_token"]?.toString(),
            )
        }

        /** 从纯 Cookie 字符串构造 (Cookie 登录方式) */
        fun fromCookieString(cookie: String): BilibiliAccount {
            val cookies = BilibiliLoginUtils.parseCookieString(cookie)
            return BilibiliAccount(cookies = cookies, accessToken = null, refreshToken = null)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  验证码数据
// ═══════════════════════════════════════════════════════════════

/** 极验验证码参数 */
data class GeetestData(
    val challenge: String,
    val gt: String,
)

/** 验证码数据模型，对应 PiliPlus CaptchaDataModel */
data class CaptchaData(
    var validate: String? = null,
    var seccode: String? = null,
    var geetest: GeetestData? = null,
    var token: String? = null,
) {
    fun isReady(): Boolean =
        !validate.isNullOrBlank() && !seccode.isNullOrBlank() && geetest != null

    fun reset() {
        validate = null
        seccode = null
        geetest = null
        token = null
    }
}

// ═══════════════════════════════════════════════════════════════
//  二维码信息
// ═══════════════════════════════════════════════════════════════

/** 二维码申请结果 */
data class QrCodeInfo(
    val authCode: String,
    val url: String,
)

/** 扫码轮询结果 */
sealed class QrPollResult {
    /** 等待扫码 */
    data object Waiting : QrPollResult()

    /** 已扫码，等待确认 */
    data class Scanned(val message: String) : QrPollResult()

    /** 扫码成功，返回登录数据 */
    data class Success(
        val tokenInfo: Map<String, Any?>,
        val cookies: List<Map<String, Any?>>,
    ) : QrPollResult()

    /** 二维码已过期 */
    data object Expired : QrPollResult()

    /** 其他错误 */
    data class Error(val code: Int, val message: String) : QrPollResult()
}

// ═══════════════════════════════════════════════════════════════
//  通用 API 响应
// ═══════════════════════════════════════════════════════════════

/** B 站 API 通用响应包装 */
data class BilibiliApiResponse(
    val code: Int,
    val message: String,
    val data: Map<String, Any?>?,
) {
    val isSuccess: Boolean get() = code == 0
}

// ═══════════════════════════════════════════════════════════════
//  登录结果
// ═══════════════════════════════════════════════════════════════

/** 登录操作结果 */
sealed class LoginResult {
    data class Success(val account: BilibiliAccount) : LoginResult()
    data class NeedCaptcha(val geeGt: String, val geeChallenge: String, val recaptchaToken: String?) : LoginResult()
    data class NeedRiskVerify(val url: String, val tmpToken: String, val requestId: String, val source: String) : LoginResult()
    data class Failure(val code: Int, val message: String) : LoginResult()
}

/** 短信发送结果 */
sealed class SmsSendResult {
    data class Success(val captchaKey: String) : SmsSendResult()
    data class NeedCaptcha(val geeGt: String, val geeChallenge: String, val recaptchaToken: String?) : SmsSendResult()
    data class Failure(val code: Int, val message: String) : SmsSendResult()
}
