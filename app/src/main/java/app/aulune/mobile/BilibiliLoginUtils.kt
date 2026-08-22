package app.aulune.mobile

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher

/**
 * B 站登录工具集。
 *
 * 复刻自 PiliPlus:
 *  - lib/utils/app_sign.dart        (AppSign.appSign)
 *  - lib/utils/login_utils.dart     (generateBuvid, genDeviceId)
 *  - lib/http/login.dart            (RSA 加密、dt 参数)
 */
object BilibiliLoginUtils {

    private val secureRandom = SecureRandom()

    // ═══════════════════════════════════════════════════════════
    //  APP 签名 (AppSign)
    // ═══════════════════════════════════════════════════════════

    /**
     * 对参数进行 B 站 APP 签名。
     *
     * 会直接在 [params] 中追加 `appkey`、`ts`、`sign` 三个字段。
     * 算法：按 key 字典序排序 → 拼接 query string → 追加 appsec → MD5。
     */
    fun appSign(
        params: MutableMap<String, String>,
        appkey: String = BilibiliLoginConstants.APP_KEY,
        appsec: String = BilibiliLoginConstants.APP_SEC,
    ) {
        params["appkey"] = appkey
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        val sorted = params.entries.sortedBy { it.key }
        val query = buildQueryString(sorted)
        params["sign"] = md5(query + appsec)
    }

    private fun buildQueryString(entries: List<Map.Entry<String, String>>): String {
        val sb = StringBuilder()
        var first = true
        for ((key, value) in entries) {
            if (!first) sb.append('&')
            first = false
            sb.append(urlEncode(key))
            if (value.isNotEmpty()) {
                sb.append('=').append(urlEncode(value))
            }
        }
        return sb.toString()
    }

    fun toFormBody(params: Map<String, String>): String = buildQueryString(params.entries.toList())

    // ═══════════════════════════════════════════════════════════
    //  buvid 生成
    // ═══════════════════════════════════════════════════════════

    /**
     * 生成 buvid (XY 前缀格式)。
     *
     * 16 随机字节 → MD5 → 取第 3/13/23 位字符前置 + 完整 MD5。
     */
    fun generateBuvid(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        val md5 = md5Bytes(bytes)
        return "XY${md5[2]}${md5[12]}${md5[22]}$md5"
    }

    // ═══════════════════════════════════════════════════════════
    //  deviceId 生成
    // ═══════════════════════════════════════════════════════════

    /**
     * 生成设备 ID (34 位十六进制)。
     *
     * 结构：16 随机字节 + 7 BCD 时间字节(世纪/年/月/日/时/分/秒) + 8 随机字节
     *       → MD5(32hex) + 1 字节校验和(2hex)。
     */
    fun genDeviceId(): String {
        val cal = Calendar.getInstance()
        val bytes = ByteArray(31)
        // 先填充 31 个随机字节，再以 7 个 BCD 时间字节覆盖中段。
        secureRandom.nextBytes(bytes)
        // 7 BCD 时间
        bytes[16] = dec2bcd(cal.get(Calendar.YEAR) / 100)
        bytes[17] = dec2bcd(cal.get(Calendar.YEAR) % 100)
        bytes[18] = dec2bcd(cal.get(Calendar.MONTH) + 1)
        bytes[19] = dec2bcd(cal.get(Calendar.DAY_OF_MONTH))
        bytes[20] = dec2bcd(cal.get(Calendar.HOUR_OF_DAY))
        bytes[21] = dec2bcd(cal.get(Calendar.MINUTE))
        bytes[22] = dec2bcd(cal.get(Calendar.SECOND))
        // 末尾 8 字节保留此前生成的随机值。
        // 校验和 (低 8 位)
        var sum = 0
        for (b in bytes) sum += b.toInt() and 0xFF
        val check = (sum and 0xFF).toString(16).padStart(2, '0')
        return md5Bytes(bytes) + check
    }

    private fun dec2bcd(dec: Int): Byte {
        require(dec in 0..99) { "dec must be 0..99, got $dec" }
        return (((dec / 10) shl 4) or (dec % 10)).toByte()
    }

    // ═══════════════════════════════════════════════════════════
    //  RSA 加密
    // ═══════════════════════════════════════════════════════════

    /**
     * 使用 B 站返回的 RSA 公钥 (PKCS#1 PEM 格式) 加密 [plain]，
     * 返回 Base64 字符串。与 PiliPlus 的 encrypt 包 RSA 默认行为一致 (PKCS1Padding)。
     */
    fun rsaEncryptBase64(publicKeyPem: String, plain: String): String {
        val keyBytes = android.util.Base64.decode(
            publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), ""),
            android.util.Base64.DEFAULT,
        )
        val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
        val publicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    /**
     * 生成 `dt` 参数：16 位随机字符串 → RSA 加密 → Base64 → URL encode。
     */
    fun generateDt(publicKeyPem: String): String {
        val random = generateRandomString(16)
        val encrypted = rsaEncryptBase64(publicKeyPem, random)
        return urlEncode(encrypted)
    }

    // ═══════════════════════════════════════════════════════════
    //  随机字符串
    // ═══════════════════════════════════════════════════════════

    private const val RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun generateRandomString(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(RANDOM_CHARS[secureRandom.nextInt(RANDOM_CHARS.length)])
        }
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════
    //  通用工具
    // ═══════════════════════════════════════════════════════════

    fun md5(input: String): String = md5Bytes(input.toByteArray(Charsets.UTF_8))

    fun md5Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * 从 "name1=value1; name2=value2" 格式的 Cookie 字符串中解析为 Map。
     */
    fun parseCookieString(cookie: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (part in cookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val name = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (name.isNotEmpty()) result[name] = value
        }
        return result
    }

    fun Map<String, String>.toCookieString(): String =
        entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun String.containsCookie(name: String): Boolean =
        split(';').any { part ->
            part.trim().substringBefore('=').trim() == name
        }

    fun String.cookieValue(name: String): String? {
        for (part in split(';')) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$name=")) {
                return trimmed.substring(name.length + 1).trim()
            }
        }
        return null
    }
}
