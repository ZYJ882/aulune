package app.aulune.mobile

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 当前版本实际暴露给用户的平台能力，不把试验性直连表述为稳定同步。 */
enum class PlatformCapabilityLevel(val label: String) {
    Supported("可用"),
    Experimental("试验"),
    Limited("受限"),
    Unavailable("未提供")
}

data class PlatformCapabilities(
    val publicImport: PlatformCapabilityLevel,
    val accountProfile: PlatformCapabilityLevel,
    val accountContent: PlatformCapabilityLevel,
    val note: String
)

object PlatformCapabilityMatrix {
    fun forPlatform(platform: ContentPlatform): PlatformCapabilities = when (platform) {
        ContentPlatform.BILIBILI -> PlatformCapabilities(
            publicImport = PlatformCapabilityLevel.Supported,
            accountProfile = PlatformCapabilityLevel.Supported,
            accountContent = PlatformCapabilityLevel.Supported,
            note = "可读取公开热门及用户主动授权后的收藏、历史和稍后内容。"
        )
        ContentPlatform.BANGUMI -> PlatformCapabilities(
            publicImport = PlatformCapabilityLevel.Experimental,
            accountProfile = PlatformCapabilityLevel.Experimental,
            accountContent = PlatformCapabilityLevel.Limited,
            note = "公开条目与账户读取依赖公开接口或页面结构，内容导入范围有限。"
        )
        ContentPlatform.DOUYIN,
        ContentPlatform.XIAOHONGSHU,
        ContentPlatform.ZHIHU,
        ContentPlatform.WEIBO,
        ContentPlatform.YOUTUBE,
        ContentPlatform.TWITTER,
        ContentPlatform.REDDIT,
        ContentPlatform.V2EX -> PlatformCapabilities(
            publicImport = PlatformCapabilityLevel.Experimental,
            accountProfile = PlatformCapabilityLevel.Experimental,
            accountContent = PlatformCapabilityLevel.Limited,
            note = "使用 Android 端直连连接器；平台接口、登录态、限流和网络变化可能导致无结果。"
        )
    }

    fun summary(platform: ContentPlatform): String {
        val capability = forPlatform(platform)
        return "公开${capability.publicImport.label} · 账户${capability.accountProfile.label} · 内容${capability.accountContent.label}"
    }
}

class PlatformConnectorException(
    val platform: ContentPlatform,
    cause: Throwable
) : IllegalStateException("${platform.label} 连接失败：${cause.message ?: cause.javaClass.simpleName}", cause)

enum class PlatformFailureKind(val userMessage: String, val retryable: Boolean) {
    RateLimited("请求过于频繁，已短暂退避后重试。", true),
    Authentication("登录状态无效或已过期，请重新登录。", false),
    Network("网络不可用或连接超时，请检查网络后重试。", true),
    Server("平台服务暂不可用，请稍后重试。", true),
    DataFormat("平台返回格式已变化，本版本暂不能解析。", false),
    Unknown("导入失败，请稍后再试或查看来源状态。", false)
}

data class PlatformFailure(
    val kind: PlatformFailureKind,
    val detail: String = ""
) {
    fun display(): String = if (detail.isBlank()) kind.userMessage else "${kind.userMessage}（$detail）"
}

object PlatformFailureClassifier {
    fun classify(error: Throwable): PlatformFailure {
        val root = generateSequence(error) { it.cause }.last()
        val message = sequenceOf(error.message.orEmpty(), root.message.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        val normalized = message.lowercase()
        val kind = when {
            normalized.contains("429") || normalized.contains("412") ||
                normalized.contains("rate") || normalized.contains("限流") || normalized.contains("too many") -> PlatformFailureKind.RateLimited
            normalized.contains("401") || normalized.contains("403") || normalized.contains("cookie") ||
                normalized.contains("登录") || normalized.contains("授权") || normalized.contains("auth") -> PlatformFailureKind.Authentication
            root is SocketTimeoutException || root is UnknownHostException || root is IOException ||
                normalized.contains("timeout") || normalized.contains("network") || normalized.contains("连接") -> PlatformFailureKind.Network
            normalized.contains("500") || normalized.contains("502") || normalized.contains("503") ||
                normalized.contains("server") || normalized.contains("服务") -> PlatformFailureKind.Server
            normalized.contains("json") || normalized.contains("parse") || normalized.contains("格式") -> PlatformFailureKind.DataFormat
            else -> PlatformFailureKind.Unknown
        }
        return PlatformFailure(kind, message.take(80))
    }
}

data class PlatformAttempt<T>(
    val value: T? = null,
    val attempts: Int = 0,
    val failure: PlatformFailure? = null
)

object PlatformRetryPolicy {
    const val maxAttempts: Int = 3

    fun delayMillis(attempt: Int): Long = when (attempt.coerceAtLeast(1)) {
        1 -> 450L
        2 -> 1_000L
        else -> 2_000L
    }

    suspend fun <T> run(operation: suspend () -> T): PlatformAttempt<T> {
        var lastFailure: PlatformFailure? = null
        repeat(maxAttempts) { index ->
            try {
                return PlatformAttempt(value = operation(), attempts = index + 1)
            } catch (error: Throwable) {
                val failure = PlatformFailureClassifier.classify(error)
                lastFailure = failure
                if (!failure.kind.retryable || index == maxAttempts - 1) {
                    return PlatformAttempt(attempts = index + 1, failure = failure)
                }
                delay(delayMillis(index + 1))
            }
        }
        return PlatformAttempt(attempts = maxAttempts, failure = lastFailure ?: PlatformFailure(PlatformFailureKind.Unknown))
    }
}
