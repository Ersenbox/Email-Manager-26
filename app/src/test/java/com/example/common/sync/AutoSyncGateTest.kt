package com.example.common.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncGateTest {

    private var now = 1_000_000L
    private val gate = AutoSyncGate(minForegroundIntervalMs = 60_000L, clock = { now })

    @Test
    fun `periodic sync is only allowed on screen`() {
        assertFalse(gate.isForeground)
        gate.onForeground(isSyncing = false)
        assertTrue(gate.isForeground)
        gate.onBackground()
        assertFalse(gate.isForeground)
    }

    @Test
    fun `first return to the app asks for a sync`() {
        assertTrue(gate.onForeground(isSyncing = false))
    }

    @Test
    fun `quick return after a good sync does not sync again`() {
        gate.onSyncFinished(success = true)
        now += 59_999L
        assertFalse(gate.onForeground(isSyncing = false))
    }

    @Test
    fun `return after the interval syncs again`() {
        gate.onSyncFinished(success = true)
        now += 60_000L
        assertTrue(gate.onForeground(isSyncing = false))
    }

    @Test
    fun `return after a failed sync always syncs so the old error goes away`() {
        gate.onSyncFinished(success = true)
        gate.onSyncFinished(success = false)
        assertTrue(gate.onForeground(isSyncing = false))
    }

    @Test
    fun `sync running on return is retried once if it fails`() {
        assertFalse(gate.onForeground(isSyncing = true))
        assertTrue(gate.onSyncFinished(success = false))
        assertFalse(gate.onSyncFinished(success = false))
    }

    @Test
    fun `sync running on return is not retried if it succeeds`() {
        gate.onForeground(isSyncing = true)
        assertFalse(gate.onSyncFinished(success = true))
    }

    @Test
    fun `no retry when the app left the screen again`() {
        gate.onForeground(isSyncing = true)
        gate.onBackground()
        assertFalse(gate.onSyncFinished(success = false))
    }
}
