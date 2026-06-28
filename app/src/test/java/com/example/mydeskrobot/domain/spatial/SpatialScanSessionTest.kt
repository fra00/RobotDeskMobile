package com.example.mydeskrobot.domain.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpatialScanSessionTest {

    @Before
    fun setUp() {
        SpatialScanSession.reset()
    }

    @Test
    fun newPlaceRequiresThreeScansWithBody() {
        SpatialScanSession.configure(bodyAvailable = true)
        assertEquals(3, SpatialScanSession.requiredScans())
        SpatialScanSession.recordScan(listOf("scrivania"))
        assertFalse(SpatialScanSession.isReadyForNewPlaceSave())
        SpatialScanSession.recordScan(listOf("monitor"))
        assertFalse(SpatialScanSession.isReadyForNewPlaceSave())
        SpatialScanSession.recordScan(listOf("libreria"))
        assertTrue(SpatialScanSession.isReadyForNewPlaceSave())
        assertEquals(listOf("scrivania", "monitor", "libreria"), SpatialScanSession.mergedLandmarks())
    }

    @Test
    fun newPlaceRequiresOneScanWithoutBody() {
        SpatialScanSession.configure(bodyAvailable = false)
        assertFalse(SpatialScanSession.isReadyForNewPlaceSave())
        SpatialScanSession.recordScan(listOf("scrivania"))
        assertTrue(SpatialScanSession.isReadyForNewPlaceSave())
    }
}
