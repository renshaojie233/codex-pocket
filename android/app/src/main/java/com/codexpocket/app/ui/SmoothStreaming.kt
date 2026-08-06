package com.codexpocket.app.ui

internal fun smoothStreamChunkCodePoints(pendingCodePoints: Int): Int = when {
    pendingCodePoints <= 0 -> 0
    pendingCodePoints <= 24 -> 1
    pendingCodePoints <= 80 -> 2
    pendingCodePoints <= 200 -> 3
    pendingCodePoints <= 500 -> 5
    else -> 8
}

internal fun takeCodePointPrefix(value: String, codePointCount: Int): Pair<String, String> {
    if (value.isEmpty() || codePointCount <= 0) return "" to value
    val available = value.codePointCount(0, value.length)
    val end = value.offsetByCodePoints(0, codePointCount.coerceAtMost(available))
    return value.substring(0, end) to value.substring(end)
}
