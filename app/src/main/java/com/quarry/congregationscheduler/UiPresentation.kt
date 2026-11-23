package com.quarry.congregationscheduler

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

internal fun uiPageTitle(page: String): String = when (page) {
    "Schedule", "Manage Schedule" -> "Manage Schedule"
    "Settings & accounts", "Settings & Accounts" -> "Settings & Accounts"
    else -> page
}

internal data class NavigationDestination(val page: String, val label: String, val icon: String)
internal fun navigationDestinations(canOpen: (String) -> Boolean): List<NavigationDestination> {
    val primary = primaryNavigationPage(canOpen)
    val regular = listOf(
        NavigationDestination("Home", "Home", "house"),
        NavigationDestination("Schedule Calendar", "Calendar", "calendar-days"),
        NavigationDestination("Export Studio", "Export", "file-down"),
        NavigationDestination("More", "More", "more_horiz")
    ).filter { it.page != primary && (it.page == "More" || canOpen(it.page)) }
    val center = when (primary) {
        "Manage Schedule" -> NavigationDestination(primary, "Manage", "calendar-check")
        "Schedule Calendar" -> NavigationDestination(primary, "Calendar", "calendar-days")
        else -> NavigationDestination("Home", "Home", "house")
    }
    return regular.toMutableList().apply { add(size / 2, center) }
}
internal fun navigationSelected(destination: String, page: String, moreOpen: Boolean): Boolean =
    if (destination == "More") moreOpen || uiPageTitle(page) in setOf("People Directory", "Reports", "Task Review", "Top Performers", "Settings & Accounts")
    else !moreOpen && uiPageTitle(destination) == uiPageTitle(page)

/** Clamp pan to the scaled, fitted image rather than the bitmap's native pixels. */
internal fun boundedPreviewPan(pan: Offset, zoom: Float, viewport: IntSize, image: IntSize): Offset {
    if (zoom <= 1f || viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return Offset.Zero
    val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val x = max(0f, (image.width * fit * zoom - viewport.width) / 2f)
    val y = max(0f, (image.height * fit * zoom - viewport.height) / 2f)
    return Offset(pan.x.coerceIn(-x, x), pan.y.coerceIn(-y, y))
}

internal sealed interface NotificationPanelState {
    data object Loading : NotificationPanelState
    data class Ready(val personName: String?, val configured: Boolean, val devices: Int) : NotificationPanelState
    data class Failed(val message: String) : NotificationPanelState
}
