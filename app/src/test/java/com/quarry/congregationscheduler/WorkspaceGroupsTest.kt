package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class WorkspaceGroupsTest {
    @Test fun missingOrEmptyWorkspaceDoesNotInventGroups() {
        for(count in listOf(null,0,-1)) {
            assertTrue(workspaceGroupNumbers(count).isEmpty())
            assertEquals("Groups not configured",workspaceGroupSummary(count))
        }
    }
    @Test fun oneGroupUsesSingularLabel() {
        assertEquals(listOf(1),workspaceGroupNumbers(1).toList())
        assertEquals("Group 1",workspaceGroupSummary(1))
    }
    @Test fun configuredCountsAboveAndBelowNineAreRespected() {
        for(count in listOf(4,9,12,30)) {
            val groups=workspaceGroupNumbers(count)
            assertEquals(count,groups.count())
            assertTrue(count in groups)
            assertFalse(count+1 in groups)
            assertEquals("Groups 1–$count",workspaceGroupSummary(count))
        }
    }
}
