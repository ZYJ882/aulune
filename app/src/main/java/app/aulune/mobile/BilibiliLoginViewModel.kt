package app.aulune.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * B 站登录 ViewModel。
 *
 * 完整复刻自 PiliPlus lib/pages/login/controller.dart：
 *  - 二维码扫码登录 (TV/HD 端，轮询 auth_code)
 *  - 密码登录 (RSA 加密 + 签名 + 极验 + 风控手机验证)
 *  - 短信验证码登录 (发送验证码 + 极验 + 登录)
 *  - Cookie 登录 (验证 + 保存)
 */
class BilibiliLoginViewModel(application: Application) : AndroidViewModel(application) {

    private val api = BilibiliLoginApi()
    private val accountManager = BilibiliAccountManager.get(application)

    // ═══════════════════════════════════════════════════════════
    //  UI 状态
    // ═══════════════════════════════════════════════════════════

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // 登录成功事件
    private val _loginSuccess = MutableSharedFlow<BilibiliAccount>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<BilibiliAccount> = _loginSuccess.asSharedFlow()

    // 需要极验验证事件
    data class GeetestRequest(val gt: String, val challenge: String, val token: String?)
    private val _geetestRequest = MutableSharedFlow<GeetestRequest>(extraBufferCapacity = 1)
    val geetestRequest: SharedFlow<GeetestRequest> = _geetestRequest.asSharedFlow()

    // ═══════════════════════════════════════════════════════════
    //  二维码登录状态
    // ═══════════════════════════════════════════════════════════

    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    private val _qrCodeStatus = MutableStateFlow("等待扫码…")
    val qrCodeStatus: StateFlow<String> = _qrCodeStatus.asStateFlow()

    private val _qrCodeLeftTime = MutableStateFlow(BilibiliLoginConstants.QRCODE_TTL_SECONDS)
    val qrCodeLeftTime: StateFlow<Int> = _qrCodeLeftTime.asStateFlow()

    private var qrPollJob: Job? = null
    private var currentAuthCode: String? = null

    // ═══════════════════════════════════════════════════════════
    //  短信登录状态
    // ═══════════════════════════════════════════════════════════

    private val _smsCooldown = MutableStateFlow(0)
    val smsCooldown: StateFlow<Int> = _smsCooldown.asStateFlow()

    private var captchaKey = ""
    private var smsSendTimestamp = 0L
    private val captchaData = CaptchaData()

    // 待重试的操作 (极验成功后执行)
    private var pendingAction: (suspend () -> Unit)? = null

    // ═══════════════════════════════════════════════════════════
    //  二维码登录
    // ═══════════════════════════════════════════════════════════

    fun refreshQrCode() {
        qrPollJob?.cancel()
        _qrCodeStatus.value = "正在生成二维码…"
        _qrCodeLeftTime.value = BilibiliLoginConstants.QRCODE_TTL_SECONDS

        viewModelScope.launch {
            val result = api.getQrCode()
            result.onSuccess { info ->
                currentAuthCode = info.authCode
                _qrCodeUrl.value = info.url
                _qrCodeStatus.value = "请使用 B 站客户端扫码"
                startQrPolling(info.authCode)
            }.onFailure {
                _qrCodeStatus.value = "二维码生成失败：${it.message}"
            }
        }
    }

    private fun startQrPolling(authCode: String) {
        qrPollJob?.cancel()
        qrPollJob = viewModelScope.launch {
            var tick = 0
            while (tick < BilibiliLoginConstants.QRCODE_TTL_SECONDS) {
                delay(BilibiliLoginConstants.QRCODE_POLL_INTERVAL_MS)
                tick++
                _qrCodeLeftTime.value = BilibiliLoginConstants.QRCODE_TTL_SECONDS - tick

                val result = api.pollQrCode(authCode)
                when (result) {
                    is QrPollResult.Success -> {
                        _qrCodeStatus.value = "扫码成功"
                        val account = BilibiliAccount.fromLoginResponse(result.tokenInfo, result.cookies)
                        accountManager.setAccount(account)
                        _loginSuccess.emit(account)
                        return@launch
                    }
                    is QrPollResult.Scanned -> {
                        _qrCodeStatus.value = result.message
                    }
                    is QrPollResult.Expired -> {
                        _qrCodeStatus.value = "二维码已过期，请刷新"
                        _qrCodeLeftTime.value = 0
                        return@launch
                    }
                    is QrPollResult.Waiting -> {
                        // 继续等待
                    }
                    is QrPollResult.Error -> {
                        if (result.code != 0) {
                            _qrCodeStatus.value = result.message.ifBlank { "等待扫码…" }
                        }
                    }
                }
            }
            _qrCodeStatus.value = "二维码已过期，请刷新"
            _qrCodeLeftTime.value = 0
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  密码登录
    // ═══════════════════════════════════════════════════════════

    fun loginByPassword(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            showToast("用户名或密码不能为空")
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val webKeyResult = api.getWebKey()
            webKeyResult.onFailure {
                _isLoading.value = false
                _errorMessage.value = it.message
                return@launch
            }
            val webKey = webKeyResult.getOrThrow()
            val result = api.loginByPassword(username, password, webKey, captchaData)
            handleLoginResult(result)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  短信登录
    // ═══════════════════════════════════════════════════════════

    fun sendSmsCode(phone: String, cid: String = "86") {
        if (phone.isBlank()) {
            showToast("手机号不能为空")
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = api.sendSmsCode(cid, phone, captchaData)
            _isLoading.value = false
            when (result) {
                is SmsSendResult.Success -> {
                    captchaKey = result.captchaKey
                    smsSendTimestamp = System.currentTimeMillis()
                    startSmsCooldown()
                    showToast("验证码已发送")
                }
                is SmsSendResult.NeedCaptcha -> {
                    pendingAction = { sendSmsCode(phone, cid) }
                    requestGeetest(result.geeGt, result.geeChallenge, result.recaptchaToken)
                }
                is SmsSendResult.Failure -> {
                    _errorMessage.value = result.message
                }
            }
        }
    }

    fun loginBySms(phone: String, code: String, cid: String = "86") {
        if (phone.isBlank()) {
            showToast("手机号不能为空")
            return
        }
        if (captchaKey.isBlank()) {
            showToast("请先获取验证码")
            return
        }
        if (code.isBlank()) {
            showToast("验证码不能为空")
            return
        }
        if (System.currentTimeMillis() - smsSendTimestamp > BilibiliLoginConstants.SMS_CODE_TTL_MS) {
            showToast("验证码已过期，请重新获取")
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val webKeyResult = api.getWebKey()
            webKeyResult.onFailure {
                _isLoading.value = false
                _errorMessage.value = it.message
                return@launch
            }
            val webKey = webKeyResult.getOrThrow()
            val result = api.loginBySms(captchaKey, phone, code, cid, webKey)
            handleLoginResult(result)
        }
    }

    private fun startSmsCooldown() {
        _smsCooldown.value = BilibiliLoginConstants.SMS_SEND_COOLDOWN_SECONDS
        viewModelScope.launch {
            while (_smsCooldown.value > 0) {
                delay(1000)
                _smsCooldown.value--
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cookie 登录
    // ═══════════════════════════════════════════════════════════

    fun loginByCookie(cookie: String) {
        if (cookie.isBlank()) {
            showToast("Cookie 不能为空")
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = api.verifyCookie(cookie)
            _isLoading.value = false
            result.onSuccess { profile ->
                val account = BilibiliAccount.fromCookieString(cookie)
                accountManager.setAccount(account)
                showToast("登录成功：${profile.name}")
                _loginSuccess.emit(account)
            }.onFailure {
                _errorMessage.value = "Cookie 无效或已过期：${it.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  登录结果处理 (密码/短信通用)
    // ═══════════════════════════════════════════════════════════

    private suspend fun handleLoginResult(result: LoginResult) {
        _isLoading.value = false
        when (result) {
            is LoginResult.Success -> {
                accountManager.setAccount(result.account)
                showToast("登录成功")
                _loginSuccess.emit(result.account)
            }
            is LoginResult.NeedCaptcha -> {
                // pendingAction 已由调用方设置 (密码登录通过 setPasswordPendingAction)
                requestGeetest(result.geeGt, result.geeChallenge, result.recaptchaToken)
            }
            is LoginResult.NeedRiskVerify -> {
                showToast("本次登录需要验证手机号")
                handleRiskVerify(result.url, result.tmpToken, result.requestId, result.source)
            }
            is LoginResult.Failure -> {
                _errorMessage.value = result.message.ifBlank { "登录失败" }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  风控手机验证流程
    // ═══════════════════════════════════════════════════════════

    data class RiskVerifyState(
        val url: String,
        val tmpToken: String,
        val requestId: String,
        val source: String,
        val hideTel: String,
        val captchaKey: String,
    )

    private val _riskVerifyState = MutableStateFlow<RiskVerifyState?>(null)
    val riskVerifyState: StateFlow<RiskVerifyState?> = _riskVerifyState.asStateFlow()

    private suspend fun handleRiskVerify(url: String, tmpToken: String, requestId: String, source: String) {
        // 1. 获取安全中心账号信息
        val infoResult = api.safeCenterGetInfo(tmpToken)
        infoResult.onFailure {
            _errorMessage.value = "获取安全验证信息失败：${it.message}"
            return
        }
        val info = infoResult.getOrThrow()
        val accountInfo = info["account_info"] as? Map<*, *>
        val hideTel = accountInfo?.get("hide_tel")?.toString() ?: "未能获取手机号"
        val telVerify = when (val v = accountInfo?.get("tel_verify")) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            else -> false
        }
        if (!telVerify) {
            _errorMessage.value = "当前账号未支持手机号验证"
            return
        }

        // 2. 初始化风控状态，等待用户操作
        _riskVerifyState.value = RiskVerifyState(url, tmpToken, requestId, source, hideTel, "")

        // 3. preCapture 获取极验参数 (在用户点击"发送验证码"时执行)
        // 这里不自动发送，等待 UI 触发
    }

    /** 用户在风控对话框中点击"发送验证码" */
    fun riskSendSms() {
        val state = _riskVerifyState.value ?: return
        _isLoading.value = true

        viewModelScope.launch {
            // preCapture
            val preResult = api.preCapture()
            preResult.onFailure {
                _isLoading.value = false
                _errorMessage.value = "获取验证码参数失败"
                return@launch
            }
            val (geeGt, geeChallenge, token) = preResult.getOrThrow()
            captchaData.token = token

            // 极验验证
            pendingAction = {
                // 极验成功后发送风控短信
                val smsResult = api.safeCenterSendSms(state.tmpToken, captchaData, state.url)
                _isLoading.value = false
                smsResult.onSuccess { key ->
                    _riskVerifyState.value = state.copy(captchaKey = key)
                    showToast("短信验证码已发送")
                }.onFailure {
                    _errorMessage.value = "发送短信失败：${it.message}"
                }
            }
            requestGeetest(geeGt, geeChallenge, token)
        }
    }

    /** 用户在风控对话框中提交短信验证码 */
    fun riskVerifySms(code: String) {
        val state = _riskVerifyState.value ?: return
        if (code.isBlank()) {
            showToast("请输入短信验证码")
            return
        }
        if (state.captchaKey.isBlank()) {
            showToast("请先获取验证码")
            return
        }
        _isLoading.value = true

        viewModelScope.launch {
            // 验证短信
            val verifyResult = api.safeCenterVerifySms(
                code = code,
                tmpCode = state.tmpToken,
                requestId = state.requestId,
                source = state.source,
                captchaKey = state.captchaKey,
                refererUrl = state.url,
            )
            verifyResult.onFailure {
                _isLoading.value = false
                _errorMessage.value = "验证失败：${it.message}"
                return@launch
            }
            val oauthCode = verifyResult.getOrThrow()

            // 用 code 换 access_token
            val tokenResult = api.oauth2AccessToken(oauthCode)
            _isLoading.value = false
            tokenResult.onSuccess { account ->
                accountManager.setAccount(account)
                _riskVerifyState.value = null
                showToast("登录成功")
                _loginSuccess.emit(account)
            }.onFailure {
                _errorMessage.value = "登录失败：${it.message}"
            }
        }
    }

    fun dismissRiskVerify() {
        _riskVerifyState.value = null
    }

    // ═══════════════════════════════════════════════════════════
    //  极验验证
    // ═══════════════════════════════════════════════════════════

    private fun requestGeetest(gt: String, challenge: String, token: String?) {
        captchaData.token = token
        captchaData.geetest = GeetestData(challenge = challenge, gt = gt)
        viewModelScope.launch {
            _geetestRequest.emit(GeetestRequest(gt, challenge, token))
        }
    }

    /**
     * 极验验证结果回调。
     * UI 层在 GeetestActivity 返回结果后调用此方法。
     */
    fun onGeetestResult(validate: String?, seccode: String?, challenge: String?) {
        if (validate != null && seccode != null && challenge != null) {
            captchaData.validate = validate
            captchaData.seccode = seccode
            captchaData.geetest = GeetestData(challenge = challenge, gt = captchaData.geetest?.gt ?: "")
            showToast("验证成功")
            // 执行待重试的操作
            val action = pendingAction
            pendingAction = null
            if (action != null) {
                viewModelScope.launch { action() }
            }
        } else {
            showToast("验证已取消")
            captchaData.reset()
        }
    }

    /**
     * 设置密码登录的待重试操作。
     * UI 层在极验成功后需要重新调用 loginByPassword，
     * 所以在触发 NeedCaptcha 时设置 pendingAction。
     */
    fun setPasswordPendingAction(username: String, password: String) {
        pendingAction = { loginByPassword(username, password) }
    }

    // ═══════════════════════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════════════════════

    private fun showToast(msg: String) {
        _toastMessage.tryEmit(msg)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        qrPollJob?.cancel()
        super.onCleared()
    }
}
