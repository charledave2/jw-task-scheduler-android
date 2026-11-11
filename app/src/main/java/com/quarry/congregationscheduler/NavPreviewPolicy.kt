package com.quarry.congregationscheduler

internal fun previewPrimaryPage(canOpen: (String)->Boolean): String = when {
    canOpen("Manage Schedule") -> "Manage Schedule"
    canOpen("Schedule Calendar") -> "Schedule Calendar"
    else -> "Home"
}
internal fun navigationPreviewConfig() = LocalWorkspaceConfig(3,listOf(
    LocalDepartment("mic","Mic Men",listOf(LocalAssignmentRole("main_mic_1","Mic Man 1"),LocalAssignmentRole("main_mic_2","Mic Man 2"))),
    LocalDepartment("av","Audio / Video",listOf(LocalAssignmentRole("audio_operator","Audio Operator"),LocalAssignmentRole("video_operator","Video Operator"))),
    LocalDepartment("attendant","Platform",listOf(LocalAssignmentRole("attendant_1","Platform")))
),listOf(
    LocalMeetingRule("thursday","Thursday",4,"18:00",(1..3).toSet()),
    LocalMeetingRule("saturday","Saturday",6,"15:30",(1..3).toSet()),
    LocalMeetingRule("sunday","Sunday",7,"07:45",(1..3).toSet())
))
