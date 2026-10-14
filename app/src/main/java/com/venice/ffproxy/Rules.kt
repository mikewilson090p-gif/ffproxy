package com.venice.ffproxy

enum class RuleType { JSON_PATCH, HEADER_SET, BODY_APPEND }

data class Rule(
    val id: String,
    var name: String,
    var enabled: Boolean,
    var type: RuleType,
    var urlRegex: String = ".*",
    var contentTypeRegex: String = "",
    var keyRegex: String = "",
    var value: String = "",
    var headerName: String = "",
    var headerValue: String = "",
    var snippet: String = ""
) {
    fun detail(): String = when (type) {
        RuleType.JSON_PATCH -> "JSON  " + keyRegex + "  ->  " + value
        RuleType.HEADER_SET -> "HEADER  " + headerName + ": " + headerValue
        RuleType.BODY_APPEND -> "APPEND  " + snippet.trim().take(40)
    }
}