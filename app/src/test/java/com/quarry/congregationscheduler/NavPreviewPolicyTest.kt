package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class NavPreviewPolicyTest {
    @Test fun primaryActionPrefersManageWhenPermitted() { assertEquals("Manage Schedule",previewPrimaryPage{true}) }
    @Test fun hiddenManageFallsBackToPermittedCalendar() { assertEquals("Schedule Calendar",previewPrimaryPage{it!="Manage Schedule"}) }
    @Test fun homeIsFallbackWhenOtherPagesAreRestricted() { assertEquals("Home",previewPrimaryPage{it=="Home"}) }
}
