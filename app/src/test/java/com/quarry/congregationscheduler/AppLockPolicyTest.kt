package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class AppLockPolicyTest {
    @Test fun enabledColdLaunchCannotOpenWorkspace() {
        val state=AppLockPolicy(true)
        assertTrue(state.locked);assertFalse(state.hasOpenedSession)
    }
    @Test fun cancellationNeverUnlocks() {
        val state=AppLockPolicy(true);val attempt=state.begin(LockAction.UNLOCK)!!
        assertFalse(state.finish(attempt.id,false));assertTrue(state.locked);assertFalse(state.hasOpenedSession)
    }
    @Test fun successUnlocksButNextBackgroundLocksAgain() {
        val state=AppLockPolicy(true);val attempt=state.begin(LockAction.UNLOCK)!!
        assertTrue(state.finish(attempt.id,true));assertFalse(state.locked);assertTrue(state.hasOpenedSession)
        state.background();assertTrue(state.locked);assertTrue(state.hasOpenedSession)
    }
    @Test fun enablingRequiresSuccessfulAuthentication() {
        val state=AppLockPolicy(false);val attempt=state.begin(LockAction.ENABLE)!!
        state.finish(attempt.id,false);assertFalse(state.enabled)
        val retry=state.begin(LockAction.ENABLE)!!;state.finish(retry.id,true);assertTrue(state.enabled)
    }
    @Test fun disablingRequiresSuccessfulAuthentication() {
        val state=AppLockPolicy(true);val attempt=state.begin(LockAction.DISABLE)!!
        state.finish(attempt.id,false);assertTrue(state.enabled);assertTrue(state.locked)
        val retry=state.begin(LockAction.DISABLE)!!;state.finish(retry.id,true);assertFalse(state.enabled);assertFalse(state.locked)
    }
    @Test fun staleCallbackCannotCompleteAnotherAttempt() {
        val state=AppLockPolicy(true);val first=state.begin(LockAction.UNLOCK)!!;state.abandon()
        val next=state.begin(LockAction.UNLOCK)!!
        assertFalse(state.finish(first.id,true));assertTrue(state.locked);assertEquals(next,state.pending)
    }
    @Test fun credentialsMayReturnAfterBackgroundWithoutDuplicatePrompts() {
        val state=AppLockPolicy(true);val request=state.begin(LockAction.UNLOCK)!!
        state.background();assertNull(state.begin(LockAction.UNLOCK));assertTrue(state.locked)
        assertTrue(state.finish(request.id,true));assertFalse(state.locked)
    }
    @Test fun processRestartDoesNotPersistUnlockedSession() {
        val first=AppLockPolicy(true);first.finish(first.begin(LockAction.UNLOCK)!!.id,true)
        val restarted=AppLockPolicy(first.enabled);assertTrue(restarted.locked);assertFalse(restarted.hasOpenedSession)
    }
    @Test fun lateSuccessWhileAppIsBackgroundedIsRejected() {
        val state=AppLockPolicy(true);val request=state.begin(LockAction.UNLOCK)!!
        state.background()
        assertFalse(state.finish(request.id,true,foreground=false))
        assertTrue(state.locked);assertFalse(state.hasOpenedSession)
    }
}
