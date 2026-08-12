package com.gipogo.rhctools.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun noUpdateProducesNoFlow() {
        assertNull(AppUpdatePolicy.selectMode(false, true, true, 5))
    }

    @Test fun normalUpdateUsesFlexibleFlow() {
        assertEquals(
            AppUpdateMode.FLEXIBLE,
            AppUpdatePolicy.selectMode(true, true, true, 2)
        )
    }

    @Test fun highPriorityUpdateUsesImmediateFlow() {
        assertEquals(
            AppUpdateMode.IMMEDIATE,
            AppUpdatePolicy.selectMode(true, true, true, 4)
        )
    }

    @Test fun oldNonCriticalUpdateRemainsFlexible() {
        assertEquals(
            AppUpdateMode.FLEXIBLE,
            AppUpdatePolicy.selectMode(true, true, true, 0)
        )
    }

    @Test fun fallsBackToAllowedFlowAndHandlesNoAllowedFlow() {
        assertEquals(
            AppUpdateMode.FLEXIBLE,
            AppUpdatePolicy.selectMode(true, true, false, 5)
        )
        assertEquals(
            AppUpdateMode.IMMEDIATE,
            AppUpdatePolicy.selectMode(true, false, true, 0)
        )
        assertNull(AppUpdatePolicy.selectMode(true, false, false, 5))
    }

    @Test fun dailyCheckRunsAtMostOncePerSuccessfulDay() {
        assertFalse(AppUpdatePolicy.isDailyCheckDue(100, 100))
        assertTrue(AppUpdatePolicy.isDailyCheckDue(99, 100))
        assertTrue(AppUpdatePolicy.isDailyCheckDue(null, 100))
    }
}
