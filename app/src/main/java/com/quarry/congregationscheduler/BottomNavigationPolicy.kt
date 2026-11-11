package com.quarry.congregationscheduler

internal fun primaryNavigationPage(canOpen: (String)->Boolean): String = when {
    canOpen("Manage Schedule") -> "Manage Schedule"
    canOpen("Schedule Calendar") -> "Schedule Calendar"
    else -> "Home"
}
