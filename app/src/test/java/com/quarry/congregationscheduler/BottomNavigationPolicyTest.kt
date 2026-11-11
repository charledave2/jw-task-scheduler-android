package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class BottomNavigationPolicyTest {
    @Test fun primaryActionPrefersManageWhenPermitted() { assertEquals("Manage Schedule",primaryNavigationPage{true}) }
    @Test fun hiddenManageFallsBackToPermittedCalendar() { assertEquals("Schedule Calendar",previewPrimaryPage{it!="Manage Schedule"}) }
    @Test fun homeIsFallbackWhenOtherPagesAreRestricted() { assertEquals("Home",previewPrimaryPage{it=="Home"}) }
}
