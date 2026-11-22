package com.quarry.congregationscheduler

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class UiPresentationTest {
    @Test fun limitedAccountsHaveOnlyRealDestinationsAndOneSelectedTab() {
        val viewer = navigationDestinations { it == "Home" }
        assertEquals(listOf("Home", "More"), viewer.map { it.page })
        val editor = navigationDestinations { true }
        assertEquals(5, editor.size)
        assertEquals("Manage Schedule", editor[2].page)
        assertEquals(listOf("Export Studio"), editor.filter { navigationSelected(it.page, "Export Studio", false) }.map { it.page })
        assertTrue(navigationSelected("More", "Top Performers", false))
        assertFalse(navigationSelected("Manage Schedule", "Export Studio", false))
        assertTrue(navigationSelected("Manage Schedule", "Schedule", false))
    }
    @Test fun panStopsAtImageEdgesAndFitClearsPan() {
        val viewport = IntSize(400, 300)
        val image = IntSize(400, 600)
        // Fitted image is 200 x 300; at 3x it is 600 x 900.
        assertEquals(Offset(100f, -300f), boundedPreviewPan(Offset(900f, -900f), 3f, viewport, image))
        assertEquals(Offset.Zero, boundedPreviewPan(Offset(20f, 30f), 1f, viewport, image))
        assertEquals(Offset.Zero, boundedPreviewPan(Offset(20f, 30f), 2f, IntSize.Zero, image))
        // No horizontal pan for a tall image until it exceeds the viewport width.
        assertEquals(Offset(0f, 50f), boundedPreviewPan(Offset(20f, 50f), 1.5f, viewport, image))
    }
    @Test fun destinationTitlesStayTheSameAcrossEntryPoints() {
        assertEquals("Top Performers", uiPageTitle("Top Performers"))
        assertEquals("Manage Schedule", uiPageTitle("Schedule"))
        assertEquals("Settings & Accounts", uiPageTitle("Settings & accounts"))
    }
}
