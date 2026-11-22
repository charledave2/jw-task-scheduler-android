package com.quarry.congregationscheduler

/** Compare numeric release versions independently of build suffixes and metadata. */
internal fun isNewerAppVersion(remote: String, current: String): Boolean {
    fun parts(version: String) = version.removePrefix("v").substringBefore('-').substringBefore('+')
        .split('.').map { it.toIntOrNull() ?: 0 }
    val left = parts(remote)
    val right = parts(current)
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}
