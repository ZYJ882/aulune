package app.aulune.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * B 站账号管理器。
 *
 * 复刻自 PiliPlus lib/utils/accounts.dart 的核心能力：
 *  - 持久化登录账号 (Cookie + access_token + refresh_token)
 *  - 当前账号切换
 *  - 匿名账号 (未登录时使用 buvid)
 *  - 登录/登出回调
 *
 * 使用 EncryptedSharedPreferences 安全存储敏感凭据。
 */
class BilibiliAccountManager private constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bilibili_accounts_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var currentAccount: BilibiliAccount? = loadCurrent()

    @Volatile
    private var listeners = mutableListOf<(BilibiliAccount?) -> Unit>()

    /** 当前登录账号，未登录时为 null */
    val current: BilibiliAccount? get() = currentAccount

    /** 是否已登录 */
    val isLoggedIn: Boolean get() = currentAccount?.isLogin == true

    /** 当前账号的 Cookie，未登录时返回空字符串 */
    val currentCookie: String get() = currentAccount?.cookieHeader.orEmpty()

    /** 当前账号 mid，未登录时为 0 */
    val currentMid: Long get() = currentAccount?.mid ?: 0L

    // ═══════════════════════════════════════════════════════════
    //  账号保存 / 切换
    // ═══════════════════════════════════════════════════════════

    /**
     * 保存并切换到新登录的账号。
     * 复刻 PiliPlus controller.setAccount()。
     */
    fun setAccount(account: BilibiliAccount) {
        currentAccount = account
        persistAccount(account)
        notifyListeners()
    }

    /** 退出登录，清除当前账号 */
    fun logout() {
        currentAccount = null
        prefs.edit().remove(KEY_CURRENT_ACCOUNT).apply()
        notifyListeners()
    }

    /** 添加登录状态监听 */
    fun addListener(listener: (BilibiliAccount?) -> Unit) {
        listeners.add(listener)
    }

    /** 移除登录状态监听 */
    fun removeListener(listener: (BilibiliAccount?) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val snapshot = listeners.toList()
        val account = currentAccount
        snapshot.forEach { it(account) }
    }

    // ═══════════════════════════════════════════════════════════
    //  持久化
    // ═══════════════════════════════════════════════════════════

    private fun persistAccount(account: BilibiliAccount) {
        val json = JSONObject().apply {
            put("cookies", JSONObject(account.cookies))
            put("accessToken", account.accessToken ?: "")
            put("refreshToken", account.refreshToken ?: "")
        }
        prefs.edit().putString(KEY_CURRENT_ACCOUNT, json.toString()).apply()
    }

    private fun loadCurrent(): BilibiliAccount? {
        val raw = prefs.getString(KEY_CURRENT_ACCOUNT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val cookiesJson = json.getJSONObject("cookies")
            val cookies = LinkedHashMap<String, String>()
            for (key in cookiesJson.keys()) {
                cookies[key] = cookiesJson.getString(key)
            }
            BilibiliAccount(
                cookies = cookies,
                accessToken = json.optString("accessToken").ifBlank { null },
                refreshToken = json.optString("refreshToken").ifBlank { null },
            )
        }.getOrNull()
    }

    companion object {
        private const val KEY_CURRENT_ACCOUNT = "current_account"

        @Volatile
        private var instance: BilibiliAccountManager? = null

        fun get(context: Context): BilibiliAccountManager {
            return instance ?: synchronized(this) {
                instance ?: BilibiliAccountManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
