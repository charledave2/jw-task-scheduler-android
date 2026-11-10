package com.quarry.congregationscheduler

internal fun workspaceGroupNumbers(count: Int?): IntRange = 1..(count ?: 0).coerceAtLeast(0)
internal fun workspaceGroupSummary(count: Int?): String = when (val total = (count ?: 0).coerceAtLeast(0)) {
    0 -> "Groups not configured"
    1 -> "Group 1"
    else -> "Groups 1–$total"
}
