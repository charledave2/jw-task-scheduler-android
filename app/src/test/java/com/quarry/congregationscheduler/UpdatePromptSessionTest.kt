package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class UpdatePromptSessionTest {
    @Test fun laterSuppressesInFlightResult() {
        val session=UpdatePromptSession(); session.begin()
        val request=session.generation
        session.dismiss()
        assertFalse(session.accepts(request))
    }
    @Test fun nextOpenShowsUpdateAgain() {
        val session=UpdatePromptSession(); session.begin(); session.dismiss(); session.begin()
        assertFalse(session.dismissed)
        assertTrue(session.accepts(session.generation))
    }
    @Test fun oldForegroundRequestCannotReplaceNewResult() {
        val session=UpdatePromptSession(); session.begin()
        val old=session.generation
        session.begin()
        assertFalse(session.accepts(old))
    }
    @Test fun newAppProcessDoesNotRememberLater() {
        val old=UpdatePromptSession(); old.dismiss()
        val restarted=UpdatePromptSession()
        assertFalse(restarted.dismissed)
        assertTrue(restarted.accepts(restarted.generation))
    }
}
