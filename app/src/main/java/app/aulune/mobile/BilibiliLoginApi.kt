package app.aulune.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * B 站登录 HTTP API 层。
 *
 * 完整复刻自 PiliPlus lib/http/login.dart，使用 android_hd 应用身份。
 * 包含：二维码登录、密码登录、短信登录、Cookie 登录、极验/风控全流程。
 */
class BilibiliLoginApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    /** 设备 ID (生成一次后复用) */
    val deviceId: String by lazy { BilibiliLoginUtils.genDeviceId() }

    /** buvid (生成一次后复用) */
    val buvid: String by lazy { BilibiliLoginUtils.generateBuvid() }

    // ═══════════════════════════════════════════════════════════
    //  通用请求头
    // ═══════════════════════════════════════════════════════════

    private fun baseHeaders(): Map<String, String> = mapOf(
        "buvid" to buvid,
        "env" to "prod",
        "app-key" to "android_hd",
        "User-Agent" to BilibiliLoginConstants.USER_AGENT,
        "x-bili-trace-id" to BilibiliLoginConstants.TRACE_ID,
        "x-bili-aurora-eid" to "",
        "x-bili-aurora-zone" to "",
        "bili-http-engine" to "cronet",
    )

    // ═══════════════════════════════════════════════════════════
    //  二维码登录 (TV/HD 端)
    // ═══════════════════════════════════════════════════════════

    /**
     * 申请二维码 (getHDcode)。
     * POST /x/passport-tv-login/qrcode/auth_code，参数在 URL query。
     */
    suspend fun getQrCode(): Result<QrCodeInfo> = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "local_id" to "0",
            "platform" to "android",
            "mobi_app" to "android_hd",
        )
        BilibiliLoginUtils.appSign(params)
        val url = BilibiliLoginConstants.API_GET_TV_CODE.toHttpUrlOrNull()!!.newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(null))
            .build()
        val resp = execute(request)
        if (resp.isSuccess) {
            val data = resp.data ?: return@withContext Result.failure(IOException("二维码接口未返回数据"))
            val authCode = data.str("auth_code")
            val qrUrl = data.str("url")
            if (authCode.isBlank() || qrUrl.isBlank()) {
                Result.failure(IOException("二维码接口返回字段缺失"))
            } else {
                Result.success(QrCodeInfo(authCode, qrUrl))
            }
        } else {
            Result.failure(IOException(resp.message))
        }
    }

    /**
     * 轮询扫码状态 (codePoll)。
     * POST /x/passport-tv-login/qrcode/poll，参数在 URL query。
     */
    suspend fun pollQrCode(authCode: String): QrPollResult = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "auth_code" to authCode,
            "local_id" to "0",
        )
        BilibiliLoginUtils.appSign(params)
        val url = BilibiliLoginConstants.API_QRCODE_POLL.toHttpUrlOrNull()!!.newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(null))
            .build()
        val resp = execute(request)
        when {
            resp.isSuccess -> {
                val data = resp.data ?: return@withContext QrPollResult.Error(-1, "无数据")
                val tokenInfo = data.obj("token_info")?.toMap()
                val cookieInfo = data.obj("cookie_info")?.array("cookies")?.toListOfMaps()
                    ?: data.array("cookie_info")?.toListOfMaps()
                    ?: emptyList()
                if (tokenInfo != null && cookieInfo.isNotEmpty()) {
                    QrPollResult.Success(tokenInfo, cookieInfo)
                } else {
                    QrPollResult.Waiting
                }
            }
            resp.code == BilibiliLoginConstants.CODE_QR_EXPIRED -> QrPollResult.Expired
            else -> QrPollResult.Error(resp.code, resp.message)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Web 端密钥 (RSA 公钥 + salt)
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取 RSA 公钥与 hash(salt)。
     * GET /x/passport-login/web/key
     */
    suspend fun getWebKey(): Result<WebKeyData> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BilibiliLoginConstants.API_GET_WEB_KEY)
            .get()
            .build()
        val resp = execute(request)
        if (resp.isSuccess) {
            val data = resp.data ?: return@withContext Result.failure(IOException("未返回密钥数据"))
            val key = data.str("key")
            val hash = data.str("hash")
            if (key.isBlank() || hash.isBlank()) {
                Result.failure(IOException("密钥字段缺失"))
            } else {
                Result.success(WebKeyData(key, hash))
            }
        } else {
            Result.failure(IOException(resp.message))
        }
    }

    data class WebKeyData(val key: String, val hash: String)

    // ═══════════════════════════════════════════════════════════
    //  短信验证码登录
    // ═══════════════════════════════════════════════════════════

    /**
     * 发送短信验证码 (sendSmsCode)。
     * POST /x/passport-login/sms/send，form body。
     */
    suspend fun sendSmsCode(
        cid: String,
        tel: String,
        captcha: CaptchaData,
    ): SmsSendResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val loginSessionId = BilibiliLoginUtils.md5(buvid + timestamp)
        val data = mutableMapOf(
            "build" to BilibiliLoginConstants.BUILD,
            "buvid" to buvid,
            "c_locale" to "zh_CN",
            "channel" to "master",
            "cid" to cid,
            "disable_rcmd" to "0",
            "local_id" to buvid,
            "login_session_id" to loginSessionId,
            "mobi_app" to "android_hd",
            "platform" to "android",
            "s_locale" to "zh_CN",
            "statistics" to BilibiliLoginConstants.STATISTICS,
            "tel" to tel,
            "ts" to (timestamp / 1000).toString(),
        )
        // 检测是否已传递极验参数（用于判断是否为验证后的重试）
        val hasCaptchaParams = captcha.validate?.isNotBlank() == true &&
            captcha.seccode?.isNotBlank() == true &&
            captcha.geetest?.challenge?.isNotBlank() == true
        captcha.geetest?.challenge?.let { data["gee_challenge"] = it }
        captcha.seccode?.let { data["gee_seccode"] = it }
        captcha.validate?.let { data["gee_validate"] = it }
        captcha.token?.let { data["recaptcha_token"] = it }
        BilibiliLoginUtils.appSign(data)
        val resp = postForm(BilibiliLoginConstants.API_APP_SMS_CODE, data, baseHeaders())
        when {
            resp.isSuccess && resp.data?.str("recaptcha_url").isNullOrBlank() -> {
                val captchaKey = resp.data?.str("captcha_key").orEmpty()
                SmsSendResult.Success(captchaKey)
            }
            resp.code == BilibiliLoginConstants.CODE_NEED_CAPTCHA || resp.code == 0 -> {
                // 关键修复：如果已经传递了极验参数但仍要求验证，说明验证失败，
                // 返回错误而非继续循环（对齐 PiliPlus 的 isGeeArgumentValid 语义）
                if (hasCaptchaParams) {
                    SmsSendResult.Failure(
                        resp.code,
                        resp.message.ifBlank { "人机验证未通过，请重新验证" },
                    )
                } else {
                    val recaptchaUrl = resp.data?.str("recaptcha_url")
                    val (geeGt, geeChallenge, token) = if (!recaptchaUrl.isNullOrBlank()) {
                        parseCaptchaUrl(recaptchaUrl)
                    } else {
                        // 回退到 preCapture 接口
                        val pre = preCaptureInternal()
                        Triple(pre?.first, pre?.second, pre?.third)
                    }
                    // 对齐 PiliPlus isGeeArgumentValid：gt/challenge 必须非空
                    if (!geeGt.isNullOrBlank() && !geeChallenge.isNullOrBlank()) {
                        SmsSendResult.NeedCaptcha(geeGt, geeChallenge, token)
                    } else {
                        SmsSendResult.Failure(resp.code, resp.message.ifBlank { "获取验证码参数失败" })
                    }
                }
            }
            else -> SmsSendResult.Failure(resp.code, resp.message)
        }
    }

    /**
     * 短信验证码登录 (loginBySms)。
     * POST /x/passport-login/login/sms，form body。
     */
    suspend fun loginBySms(
        captchaKey: String,
        tel: String,
        code: String,
        cid: String,
        webKey: WebKeyData,
    ): LoginResult = withContext(Dispatchers.IO) {
        val data = mutableMapOf<String, String>(
            "bili_local_id" to deviceId,
            "build" to BilibiliLoginConstants.BUILD,
            "buvid" to buvid,
            "c_locale" to "zh_CN",
            "captcha_key" to captchaKey,
            "channel" to "master",
            "cid" to cid,
            "code" to code,
            "device" to "phone",
            "device_id" to deviceId,
            "device_name" to "vivo",
            "device_platform" to "Android14vivo",
            "disable_rcmd" to "0",
            "dt" to BilibiliLoginUtils.generateDt(webKey.key),
            "from_pv" to "main.my-information.my-login.0.click",
            "from_url" to BilibiliLoginUtils.urlEncode("bilibili://user_center/mine"),
            "local_id" to buvid,
            "mobi_app" to "android_hd",
            "platform" to "android",
            "s_locale" to "zh_CN",
            "statistics" to BilibiliLoginConstants.STATISTICS,
            "tel" to tel,
        )
        BilibiliLoginUtils.appSign(data)
        val resp = postForm(BilibiliLoginConstants.API_LOGIN_BY_APP_SMS, data, baseHeaders())
        parseLoginResponse(resp)
    }

    // ═══════════════════════════════════════════════════════════
    //  密码登录
    // ═══════════════════════════════════════════════════════════

    /**
     * 密码登录 (loginByPwd)。
     * POST /x/passport-login/oauth2/login，form body。
     * 密码使用 RSA 公钥加密 (salt + password)。
     */
    suspend fun loginByPassword(
        username: String,
        password: String,
        webKey: WebKeyData,
        captcha: CaptchaData,
    ): LoginResult = withContext(Dispatchers.IO) {
        val passwordEncrypted = BilibiliLoginUtils.rsaEncryptBase64(webKey.key, webKey.hash + password)
        val data = mutableMapOf(
            "bili_local_id" to deviceId,
            "build" to BilibiliLoginConstants.BUILD,
            "buvid" to buvid,
            "c_locale" to "zh_CN",
            "channel" to "master",
            "device" to "phone",
            "device_id" to deviceId,
            "device_name" to "vivo",
            "device_platform" to "Android14vivo",
            "disable_rcmd" to "0",
            "dt" to BilibiliLoginUtils.generateDt(webKey.key),
            "from_pv" to "main.homepage.avatar-nologin.all.click",
            "from_url" to BilibiliLoginUtils.urlEncode("bilibili://pegasus/promo"),
            "local_id" to buvid,
            "mobi_app" to "android_hd",
            "password" to passwordEncrypted,
            "permission" to "ALL",
            "platform" to "android",
            "s_locale" to "zh_CN",
            "statistics" to BilibiliLoginConstants.STATISTICS,
            "username" to username,
        )
        // 检测是否已传递极验参数
        val hasCaptchaParams = captcha.validate?.isNotBlank() == true &&
            captcha.seccode?.isNotBlank() == true &&
            captcha.geetest?.challenge?.isNotBlank() == true
        captcha.geetest?.challenge?.let { data["gee_challenge"] = it }
        captcha.seccode?.let { data["gee_seccode"] = it }
        captcha.validate?.let { data["gee_validate"] = it }
        captcha.token?.let { data["recaptcha_token"] = it }
        BilibiliLoginUtils.appSign(data)
        val resp = postForm(BilibiliLoginConstants.API_LOGIN_BY_PWD, data, baseHeaders())
        val result = parseLoginResponse(resp)
        // 已传递极验参数但仍要求验证 → 验证失败，避免无限循环
        if (hasCaptchaParams && result is LoginResult.NeedCaptcha) {
            LoginResult.Failure(
                resp.code,
                resp.message.ifBlank { "人机验证未通过，请重新验证" },
            )
        } else {
            result
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  风控 / 安全中心
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取安全中心账号信息 (safeCenterGetInfo)。
     * GET /x/safecenter/user/info?tmp_code=...
     */
    suspend fun safeCenterGetInfo(tmpCode: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val url = BilibiliLoginConstants.API_SAFE_CENTER_INFO.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("tmp_code", tmpCode)
            .build()
        val request = Request.Builder().url(url).get().build()
        val resp = execute(request)
        if (resp.isSuccess) {
            Result.success(resp.data?.toMap() ?: emptyMap())
        } else {
            Result.failure(IOException("${resp.code}: ${resp.message}"))
        }
    }

    /**
     * 风控前置人机验证 (preCapture)。
     * POST /x/safecenter/captcha/pre
     */
    suspend fun preCapture(): Result<Triple<String, String, String?>> = withContext(Dispatchers.IO) {
        val result = preCaptureInternal()
        if (result != null) Result.success(result) else Result.failure(IOException("获取极验参数失败"))
    }

    private fun preCaptureInternal(): Triple<String, String, String?>? {
        val request = Request.Builder()
            .url(BilibiliLoginConstants.API_PRE_CAPTURE)
            .post("".toRequestBody(null))
            .build()
        val resp = runCatching { execute(request) }.getOrNull() ?: return null
        if (!resp.isSuccess) return null
        val data = resp.data ?: return null
        val geeGt = data.str("gee_gt")
        val geeChallenge = data.str("gee_challenge")
        val token = data.str("recaptcha_token")
        if (geeGt.isBlank() || geeChallenge.isBlank()) return null
        return Triple(geeGt, geeChallenge, token.ifBlank { null })
    }

    /**
     * 风控发送短信验证码 (safeCenterSmsCode)。
     * POST /x/safecenter/common/sms/send，带 Referer。
     */
    suspend fun safeCenterSendSms(
        tmpCode: String,
        captcha: CaptchaData,
        refererUrl: String,
        smsType: String = "loginTelCheck",
    ): Result<String> = withContext(Dispatchers.IO) {
        val data = mutableMapOf(
            "disable_rcmd" to "0",
            "sms_type" to smsType,
            "tmp_code" to tmpCode,
        )
        captcha.geetest?.challenge?.let { data["gee_challenge"] = it }
        captcha.seccode?.let { data["gee_seccode"] = it }
        captcha.validate?.let { data["gee_validate"] = it }
        captcha.token?.let { data["recaptcha_token"] = it }
        BilibiliLoginUtils.appSign(data)
        val headers = baseHeaders().toMutableMap()
        headers["Referer"] = refererUrl
        val resp = postForm(BilibiliLoginConstants.API_SAFE_CENTER_SMS_CODE, data, headers)
        if (resp.isSuccess) {
            val captchaKey = resp.data?.str("captcha_key").orEmpty()
            Result.success(captchaKey)
        } else {
            Result.failure(IOException("${resp.code}: ${resp.message}"))
        }
    }

    /**
     * 风控验证短信验证码 (safeCenterSmsVerify)。
     * POST /x/safecenter/login/tel/verify，带 Referer。
     */
    suspend fun safeCenterVerifySms(
        code: String,
        tmpCode: String,
        requestId: String,
        source: String,
        captchaKey: String,
        refererUrl: String,
        type: String = "loginTelCheck",
    ): Result<String> = withContext(Dispatchers.IO) {
        val data = mutableMapOf(
            "type" to type,
            "code" to code,
            "tmp_code" to tmpCode,
            "request_id" to requestId,
            "source" to source,
            "captcha_key" to captchaKey,
        )
        BilibiliLoginUtils.appSign(data)
        val headers = baseHeaders().toMutableMap()
        headers["Referer"] = refererUrl
        val resp = postForm(BilibiliLoginConstants.API_SAFE_CENTER_SMS_VERIFY, data, headers)
        if (resp.isSuccess) {
            val oauthCode = resp.data?.str("code").orEmpty()
            Result.success(oauthCode)
        } else {
            Result.failure(IOException("${resp.code}: ${resp.message}"))
        }
    }

    /**
     * 用 oauth code 换取 access_token (oauth2AccessToken)。
     * POST /x/passport-login/oauth2/access_token
     */
    suspend fun oauth2AccessToken(code: String): Result<BilibiliAccount> = withContext(Dispatchers.IO) {
        val data = mutableMapOf(
            "build" to BilibiliLoginConstants.BUILD,
            "buvid" to buvid,
            "code" to code,
            "disable_rcmd" to "0",
            "grant_type" to "authorization_code",
            "local_id" to buvid,
            "mobi_app" to "android_hd",
            "platform" to "android",
        )
        BilibiliLoginUtils.appSign(data)
        val resp = postForm(BilibiliLoginConstants.API_OAUTH2_ACCESS_TOKEN, data, baseHeaders())
        if (resp.isSuccess) {
            val dataObj = resp.data ?: return@withContext Result.failure(IOException("未返回 token 数据"))
            val tokenInfo = dataObj.obj("token_info")?.toMap()
            val cookieInfo = dataObj.obj("cookie_info")?.array("cookies")?.toListOfMaps()
                ?: dataObj.array("cookie_info")?.toListOfMaps()
                ?: emptyList()
            if (tokenInfo == null || cookieInfo.isEmpty()) {
                Result.failure(IOException("登录异常，接口未返回身份信息"))
            } else {
                Result.success(BilibiliAccount.fromLoginResponse(tokenInfo, cookieInfo))
            }
        } else {
            Result.failure(IOException("${resp.code}: ${resp.message}"))
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  退出登录
    // ═══════════════════════════════════════════════════════════

    /**
     * 退出登录 (logout)。
     * POST /login/exit/v2
     */
    suspend fun logout(account: BilibiliAccount): Result<Unit> = withContext(Dispatchers.IO) {
        val data = mapOf("biliCSRF" to account.csrf)
        val headers = baseHeaders().toMutableMap()
        headers["Cookie"] = account.cookieHeader
        val resp = postForm(BilibiliLoginConstants.API_LOGOUT, data, headers)
        if (resp.isSuccess) Result.success(Unit) else Result.failure(IOException(resp.message))
    }

    // ═══════════════════════════════════════════════════════════
    //  Cookie 登录校验
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验 Cookie 是否有效 (请求 nav 接口)。
     */
    suspend fun verifyCookie(cookie: String): Result<BilibiliAccountProfile> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BilibiliLoginConstants.API_NAV)
            .header("Cookie", cookie)
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BilibiliLoginConstants.USER_AGENT)
            .get()
            .build()
        val resp = execute(request)
        if (resp.isSuccess) {
            val data = resp.data ?: return@withContext Result.failure(IOException("未返回用户信息"))
            val isLogin = data.boolean("isLogin")
            if (!isLogin) {
                Result.failure(IOException("账号未登录"))
            } else {
                Result.success(
                    BilibiliAccountProfile(
                        mid = data.long("mid"),
                        name = data.str("uname").ifBlank { "B 站用户" },
                        faceUrl = data.str("face"),
                        level = data.obj("level_info")?.int("current_level") ?: 0,
                        isVip = data.int("vipStatus") == 1,
                    ),
                )
            }
        } else {
            Result.failure(IOException(resp.message))
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════════════════

    private fun parseLoginResponse(resp: ApiResponse): LoginResult {
        if (!resp.isSuccess) {
            return when (resp.code) {
                BilibiliLoginConstants.CODE_NEED_CAPTCHA -> {
                    val captureUrl = resp.data?.str("url")
                    if (!captureUrl.isNullOrBlank()) {
                        val (geeGt, geeChallenge, token) = parseCaptchaUrl(captureUrl)
                        if (!geeGt.isNullOrBlank() && !geeChallenge.isNullOrBlank()) {
                            LoginResult.NeedCaptcha(geeGt, geeChallenge, token)
                        } else {
                            LoginResult.Failure(resp.code, resp.message)
                        }
                    } else {
                        LoginResult.Failure(resp.code, resp.message)
                    }
                }
                else -> LoginResult.Failure(resp.code, resp.message)
            }
        }
        val data = resp.data ?: return LoginResult.Failure(-1, "未返回数据")
        val status = data.int("status")
        // status == 2: 风控，需要手机验证
        if (status == 2) {
            val url = data.str("url")
            if (url.isNotBlank()) {
                val uri = url.toHttpUrlOrNull()
                val tmpToken = uri?.queryParameter("tmp_token").orEmpty()
                val requestId = uri?.queryParameter("request_id").orEmpty()
                val source = uri?.queryParameter("source").orEmpty()
                if (tmpToken.isNotBlank()) {
                    return LoginResult.NeedRiskVerify(url, tmpToken, requestId, source)
                }
            }
            return LoginResult.Failure(-1, data.str("message").ifBlank { "登录环境存在风险" })
        }
        // 正常登录成功
        val tokenInfo = data.obj("token_info")?.toMap()
        val cookieInfo = data.obj("cookie_info")?.array("cookies")?.toListOfMaps()
            ?: data.array("cookie_info")?.toListOfMaps()
            ?: emptyList()
        if (tokenInfo == null || cookieInfo.isEmpty()) {
            return LoginResult.Failure(-1, "登录异常，接口未返回身份信息")
        }
        return LoginResult.Success(BilibiliAccount.fromLoginResponse(tokenInfo, cookieInfo))
    }

    private fun parseCaptchaUrl(url: String): Triple<String?, String?, String?> {
        val uri = url.toHttpUrlOrNull() ?: return Triple(null, null, null)
        return Triple(
            uri.queryParameter("gee_gt"),
            uri.queryParameter("gee_challenge"),
            uri.queryParameter("recaptcha_token"),
        )
    }

    private fun postForm(
        url: String,
        data: Map<String, String>,
        headers: Map<String, String>,
    ): ApiResponse {
        val body = FormBody.Builder()
            .apply { data.forEach { (k, v) -> add(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return execute(request)
    }

    private fun execute(request: Request): ApiResponse {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse {
                    return ApiResponse(-1, "响应格式无法识别: ${body.take(200)}", null)
                }
            val code = root.int("code")
            val message = root.str("message")
            val data = root["data"] as? JsonObject
            return ApiResponse(code, message, data)
        }
    }

    private data class ApiResponse(
        val code: Int,
        val message: String,
        val data: JsonObject?,
    ) {
        val isSuccess: Boolean get() = code == 0
    }
}

// ═══════════════════════════════════════════════════════════════
//  JsonObject / JsonElement 扩展
// ═══════════════════════════════════════════════════════════════

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.boolean(key: String): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: false

private fun JsonObject.obj(key: String): JsonObject? =
    this[key] as? JsonObject

private fun JsonObject.array(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonObject.toMap(): Map<String, Any?> {
    val result = LinkedHashMap<String, Any?>()
    for ((k, v) in this) {
        result[k] = v.toAny()
    }
    return result
}

private fun JsonArray.toListOfMaps(): List<Map<String, Any?>> =
    this.mapNotNull { (it as? JsonObject)?.toMap() }

private fun JsonElement.toAny(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        else -> booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
    is JsonObject -> toMap()
    is JsonArray -> map { it.toAny() }
}
