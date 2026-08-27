package app.aulune.mobile

/** 模型选择弹窗的本机筛选规则；不发起网络请求，也不处理 API Key。 */
internal fun filterRemoteModels(models: List<String>, query: String): List<String> {
    val needle = query.trim()
    return models.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .filter { needle.isBlank() || it.contains(needle, ignoreCase = true) }
        .sortedBy { it.lowercase() }
        .toList()
}
