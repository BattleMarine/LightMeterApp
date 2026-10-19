package com.lightmeter.app

import org.junit.Assert.*
import org.junit.Test

class StartupExposureTest {
    private val shutters = listOf(0.5, 0.25, 0.125, 1.0 / 60, 1.0 / 125, 1.0 / 2000)

    @Test fun fixedFilmIsoAndVirtualApertureDetermineEquivalentShutter() {
        val reading = ExposureReading(2.0, 1.0 / 100, 400)
        val result = StartupExposure.chooseShutter(reading, 100, 4.0, shutters)
        // Target 0.16 s; 1/8 is closer in stops than 1/4.
        assertEquals(2, result.index)
        assertEquals(PresetExposureRange.WITHIN, result.range)
        assertEquals(4, StartupExposure.chooseShutter(reading, 1600, 4.0, shutters).index)
    }

    @Test fun logarithmicDistanceWinsOverLinearSecondsDistance() {
        val result = StartupExposure.chooseShutter(ExposureReading(4.0, 0.18, 100),
            100, 4.0, listOf(0.125, 0.25))
        assertEquals(1, result.index)
    }

    @Test fun brightAndDarkScenesClampShutterWithoutChangingFilmIso() {
        assertEquals(ExposureSelection(5, PresetExposureRange.TOO_BRIGHT),
            StartupExposure.chooseShutter(ExposureReading(4.0, 0.00001, 100), 100, 4.0, shutters))
        assertEquals(ExposureSelection(0, PresetExposureRange.TOO_DARK),
            StartupExposure.chooseShutter(ExposureReading(4.0, 2.0, 100), 100, 4.0, shutters))
        for (seconds in listOf(shutters.min(), shutters.max())) {
            assertEquals(PresetExposureRange.WITHIN,
                StartupExposure.chooseShutter(ExposureReading(4.0, seconds, 100), 100, 4.0, shutters).range)
        }
    }

    @Test fun invalidOrIncompleteMetadataCannotSelectAStartupExposure() {
        assertNull(ExposureReading.from(null, 1L, 100))
        assertNull(ExposureReading.from(2.0, null, 100))
        assertNull(ExposureReading.from(2.0, 1L, null))
        assertNull(ExposureReading.from(Double.NaN, 1L, 100))
        assertNull(ExposureReading.from(Double.POSITIVE_INFINITY, 1L, 100))
        assertNull(ExposureReading.from(0.0, 1L, 100))
        assertNull(ExposureReading.from(2.0, 0L, 100))
        assertNull(ExposureReading.from(2.0, -1L, 100))
        assertNull(ExposureReading.from(2.0, 1L, 0))
        assertEquals(ExposureReading(2.0, 0.01, 100), ExposureReading.from(2.0, 10_000_000L, 100))
    }

    @Test fun firstInstallAndCorruptSavedIsoUse100WhileIntermediateValuesRestoreExactly() {
        val presets = listOf(listOf(25, 50, 100, 200), listOf(25, 35, 100, 140), listOf(25, 100, 125))
        assertEquals(0 to 2, StartupExposure.restoreFilmIso(null, presets))
        assertEquals(0 to 2, StartupExposure.restoreFilmIso(-999, presets))
        assertEquals(1 to 1, StartupExposure.restoreFilmIso(35, presets))
        assertEquals(2 to 2, StartupExposure.restoreFilmIso(125, presets))
    }

    @Test fun applicationSignalAndFreshMatchingFrameAreBothRequired() {
        val gate = ExposureRequestGate()
        val ae = gate.begin(ExposureRequestGate.Phase.STARTUP_AE)
        assertFalse(gate.accepts(ae, 100, true))
        gate.applied(ae, 100)
        assertFalse(gate.accepts(ae, 100, true))
        assertFalse(gate.accepts(ae, 101, false))
        assertTrue(gate.accepts(ae, 101, true))
    }

    @Test fun missingAeStateWaitsForDeadlineThenUsesOnlyACompleteReading() {
        val probe = StartupAeProbe()
        assertNull(probe.observe(null, true))
        assertNull(probe.lastReading) // Deadline with no reading must keep automatic preview.
        val reading = ExposureReading(2.0, 0.01, 200)
        assertNull(probe.observe(reading, null))
        assertEquals(reading, probe.lastReading) // Deadline can use this unconfirmed estimate.
        assertNull(probe.observe(null, true))
        assertEquals(reading, probe.lastReading)
        probe.reset()
        assertNull(probe.lastReading)
    }

    @Test fun searchingDoesNotCompleteButLaterConvergedReadingDoes() {
        val probe = StartupAeProbe()
        val searching = ExposureReading(2.0, 0.01, 200)
        val converged = ExposureReading(2.0, 0.02, 300)
        assertNull(probe.observe(searching, false))
        assertEquals(converged, probe.observe(converged, true))
        assertEquals(converged, probe.lastReading)
    }

    @Test fun userOverrideRejectsLateAeCompletionAndInflightAeFrames() {
        val gate = ExposureRequestGate()
        val ae = gate.begin(ExposureRequestGate.Phase.STARTUP_AE)
        val manual = gate.begin(ExposureRequestGate.Phase.MANUAL)
        gate.applied(ae, 10)
        assertFalse(gate.accepts(manual, 11, true))
        assertFalse(gate.accepts(ae, 11, true))
        gate.applied(manual, 11)
        assertFalse(gate.accepts(manual, 12, false))
        assertTrue(gate.accepts(manual, 12, true))
    }

    @Test fun pauseAndPermissionLossInvalidateRequestsAndResumeNeedsNewFrames() {
        val gate = ExposureRequestGate()
        val old = gate.begin(ExposureRequestGate.Phase.MANUAL)
        gate.applied(old, 8)
        gate.stop()
        gate.applied(old, 9)
        assertFalse(gate.accepts(old, 10, true))
        val resumed = gate.begin(ExposureRequestGate.Phase.STARTUP_AE)
        assertFalse(gate.accepts(resumed, 11, true))
        gate.applied(resumed, 11)
        assertTrue(gate.accepts(resumed, 12, true))
    }

    @Test fun timeoutFallbackCannotBeReplacedByLateManualCompletion() {
        val gate = ExposureRequestGate()
        val manual = gate.begin(ExposureRequestGate.Phase.MANUAL)
        val fallback = gate.begin(ExposureRequestGate.Phase.AUTO_PREVIEW)
        gate.applied(manual, 25)
        assertFalse(gate.accepts(fallback, 26, true))
        gate.applied(fallback, 26)
        assertTrue(gate.accepts(fallback, 27, true))
        assertFalse(gate.accepts(manual, 28, true))
    }

    @Test fun repeatedManualSwipesKeepLastTintButMeteringFallbackAndStopClearIt() {
        val gate = ExposureRequestGate()
        gate.begin(ExposureRequestGate.Phase.MANUAL)
        assertTrue(gate.keepsAppliedTint(ExposureRequestGate.Phase.MANUAL, false))
        assertFalse(gate.keepsAppliedTint(ExposureRequestGate.Phase.STARTUP_AE, false))
        assertFalse(gate.keepsAppliedTint(ExposureRequestGate.Phase.AUTO_PREVIEW, true))
        gate.stop()
        assertFalse(gate.keepsAppliedTint(ExposureRequestGate.Phase.MANUAL, false))
    }

    @Test fun nullAeModeAndStaleRequestOptionsCannotEnableCorrection() {
        val manual = ExpectedExposureRequest(0, 10_000_000L, 100, 2f)
        assertFalse(manual.matches(0, null, 10_000_000L, 100, 2f, null))
        assertFalse(manual.matches(null, 0, 10_000_000L, 100, 2f, null))
        assertFalse(manual.matches(1, 1, 10_000_000L, 100, 2f, null))
        assertFalse(manual.matches(0, 0, 10_000_000L, 200, 2f, null))
        assertFalse(manual.matches(0, 0, 20_000_000L, 100, 2f, null))
        assertFalse(manual.matches(0, 0, 10_000_000L, 100, 2.8f, null))
        assertTrue(manual.matches(0, 0, 10_000_000L, 100, 2f, null))
        val auto = ExpectedExposureRequest(1, compensation = 0)
        assertFalse(auto.matches(1, 1, null, null, null, -1))
        assertTrue(auto.matches(1, 1, null, null, null, 0))
    }

    @Test fun oneShotRejectsUnavailableAndDuplicatePressesWithoutReplacingSnapshot() {
        val meter = OneShotMetering()
        val cards = MeteringCards(8.0, 1.0 / 125, 400)
        assertFalse(meter.begin(cards, ready = false))
        assertFalse(meter.busy)
        assertTrue(meter.begin(cards, ready = true))
        assertFalse(meter.begin(MeteringCards(4.0, 0.5, 100), ready = true))
        assertEquals(cards, meter.cards)
    }

    @Test fun oneShotUsesCurrentApertureAndFilmIsoAndCommitsOnlyAfterApplication() {
        val meter = OneShotMetering()
        val cards = MeteringCards(8.0, 1.0 / 125, 400)
        val candidates = listOf(1.0 / 125, 1.0 / 60, 1.0 / 30, 1.0 / 15)
        meter.begin(cards, ready = true)
        val selection = meter.observe(ExposureReading(2.0, 0.01, 100), true, candidates)
        assertEquals(2, selection?.index) // f/8, ISO400 -> 0.04s; never reset to f/4.
        assertEquals(cards, meter.cards)
        assertEquals(OneShotMetering.Stage.APPLYING, meter.stage)
        assertTrue(meter.busy)
        assertEquals(selection, meter.applied())
        assertFalse(meter.busy)
        assertNull(meter.observe(ExposureReading(2.0, 0.5, 100), true, candidates))
        assertNull(meter.applied()) // No continued scene tracking or second commit.
    }

    @Test fun oneShotTimeoutDoesNotUseUnconvergedOrNullStateEstimates() {
        val meter = OneShotMetering()
        val cards = MeteringCards(5.6, 1.0 / 60, 200)
        meter.begin(cards, ready = true)
        assertNull(meter.observe(ExposureReading(2.0, 0.1, 100), false, shutters))
        assertNull(meter.observe(ExposureReading(2.0, 0.2, 100), null, shutters))
        assertNull(meter.observe(null, true, shutters))
        assertTrue(meter.restore())
        assertNull(meter.applied())
        assertEquals(cards, meter.cards)
        assertTrue(meter.restored())
        assertFalse(meter.busy)
    }

    @Test fun manualApplicationFailureDiscardsCandidateAndRestoresOldCards() {
        val meter = OneShotMetering()
        val cards = MeteringCards(2.8, 1.0 / 250, 800)
        meter.begin(cards, true)
        assertNotNull(meter.observe(ExposureReading(2.0, 0.01, 100), true, shutters))
        assertTrue(meter.restore())
        assertEquals(cards, meter.cards)
        assertNull(meter.observe(ExposureReading(2.0, 0.5, 100), true, shutters))
        assertNull(meter.applied())
        assertTrue(meter.busy)
        assertTrue(meter.restored())
        assertFalse(meter.restored())
    }

    @Test fun userCardOrStepChangeCancelsBothAeAndUncommittedApplication() {
        val meter = OneShotMetering()
        val cards = MeteringCards(11.0, 1.0 / 125, 125)
        meter.begin(cards, true)
        meter.cancel()
        assertNull(meter.observe(ExposureReading(2.0, 0.1, 100), true, shutters))
        meter.begin(cards, true)
        meter.observe(ExposureReading(2.0, 0.1, 100), true, shutters)
        meter.cancel()
        assertNull(meter.applied())
        assertFalse(meter.busy)
    }

    @Test fun interruptionKeepsRestorePendingAndDoesNotRestartOneShotAe() {
        val meter = OneShotMetering()
        val cards = MeteringCards(8.0, 0.125, 1600)
        meter.begin(cards, true)
        meter.restore() // Pause or permission loss.
        assertEquals(OneShotMetering.Stage.RESTORING, meter.stage)
        assertFalse(meter.begin(cards, true))
        assertNull(meter.observe(ExposureReading(2.0, 0.1, 100), true, shutters))
        assertEquals(cards, meter.cards)
        meter.restored()
        assertTrue(meter.begin(cards, true)) // Only a new user press starts another cycle.
    }

    @Test fun requestedAeFramesAndCompletionCannotLeakIntoRestoredManualPreview() {
        val gate = ExposureRequestGate()
        val ae = gate.begin(ExposureRequestGate.Phase.REQUESTED_AE)
        gate.applied(ae, 20)
        assertTrue(gate.accepts(ae, 21, true))
        val restore = gate.begin(ExposureRequestGate.Phase.MANUAL)
        gate.applied(ae, 21)
        assertFalse(gate.accepts(restore, 22, true))
        gate.applied(restore, 22)
        assertFalse(gate.accepts(ae, 23, true))
        assertFalse(gate.accepts(restore, 23, false))
        assertTrue(gate.accepts(restore, 23, true))
    }

    @Test fun priorityRestorationDefaultsToAAndPersistsSByStableCode() {
        assertEquals(ExposurePriority.APERTURE, ExposurePriority.restore(null))
        assertEquals(ExposurePriority.APERTURE, ExposurePriority.restore("invalid"))
        assertEquals(ExposurePriority.APERTURE, ExposurePriority.restore("A"))
        assertEquals(ExposurePriority.SHUTTER, ExposurePriority.restore("S"))
        for (priority in ExposurePriority.entries) {
            assertEquals(priority, ExposurePriority.restore(priority.code))
        }
    }

    @Test fun shutterPrioritySolvesApertureUsingTimeAndBothIsoValues() {
        val apertures = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 16.0, 32.0)
        val reading = ExposureReading(2.0, 0.01, 100)
        assertEquals(ExposureSelection(4, PresetExposureRange.WITHIN, ExposurePriority.SHUTTER),
            StartupExposure.chooseAperture(reading, 100, 0.04, apertures))
        assertEquals(6, StartupExposure.chooseAperture(reading, 400, 0.04, apertures).index)
        assertEquals(2, StartupExposure.chooseAperture(reading.copy(iso = 400), 100, 0.04, apertures).index)
    }

    @Test fun apertureChoiceMinimizesLogExposureErrorInsteadOfLinearFNumberError() {
        val selection = StartupExposure.chooseAperture(ExposureReading(1.45, 0.01, 100),
            100, 0.01, listOf(1.0, 2.0))
        assertEquals(1, selection.index)
    }

    @Test fun shutterPriorityRangeSignsReflectLightLossAtLargerFNumbers() {
        val apertures = listOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
        val dim = StartupExposure.chooseAperture(ExposureReading(0.5, 0.01, 100), 100, 0.01, apertures)
        assertEquals(ExposureSelection(0, PresetExposureRange.TOO_DARK, ExposurePriority.SHUTTER), dim)
        val bright = StartupExposure.chooseAperture(ExposureReading(64.0, 0.01, 100), 100, 0.01, apertures)
        assertEquals(ExposureSelection(5, PresetExposureRange.TOO_BRIGHT, ExposurePriority.SHUTTER), bright)
        for (boundary in listOf(1.0, 32.0)) {
            assertEquals(PresetExposureRange.WITHIN,
                StartupExposure.chooseAperture(ExposureReading(boundary, 0.01, 100), 100, 0.01, apertures).range)
        }
    }

    @Test fun bothPrioritiesKeepFilmIsoAndSelectOnlyTheirOwnAxis() {
        val reading = ExposureReading(2.0, 0.01, 100)
        val cards = MeteringCards(4.0, 0.04, 100)
        val a = StartupExposure.choose(ExposurePriority.APERTURE, reading, cards, listOf(0.01, 0.04, 0.16))
        val s = StartupExposure.choose(ExposurePriority.SHUTTER, reading, cards, listOf(2.0, 4.0, 8.0))
        assertEquals(ExposureSelection(1, PresetExposureRange.WITHIN, ExposurePriority.APERTURE), a)
        assertEquals(ExposureSelection(1, PresetExposureRange.WITHIN, ExposurePriority.SHUTTER), s)
        assertEquals(MeteringCards(4.0, 0.04, 100), cards)
    }

    @Test fun switchingPriorityDuringMeasurementOrApplicationDiscardsOldAxisSelection() {
        for (applyPending in listOf(false, true)) {
            val meter = OneShotMetering()
            val cards = MeteringCards(5.6, 1.0 / 60, 200)
            meter.begin(cards, true, ExposurePriority.SHUTTER)
            if (applyPending) assertNotNull(meter.observe(ExposureReading(2.0, 0.01, 100), true, listOf(2.0, 4.0, 8.0)))
            meter.restore() // Changing mode restores the previous preview, without remeasurement.
            assertNull(meter.applied())
            assertNull(meter.observe(ExposureReading(2.0, 0.01, 100), true, listOf(2.0, 4.0, 8.0)))
            assertEquals(cards, meter.cards)
            meter.restored()
            assertFalse(meter.busy)
            assertTrue(meter.begin(cards, true, ExposurePriority.APERTURE))
            assertEquals(ExposurePriority.APERTURE, meter.observe(ExposureReading(2.0, 0.01, 100), true, shutters)?.priority)
        }
    }

    @Test fun startupModeChangeRejectsLateAeAndNewModeDoesNotRestartMeasurement() {
        val gate = ExposureRequestGate()
        val initialAe = gate.begin(ExposureRequestGate.Phase.STARTUP_AE)
        gate.applied(initialAe, 30)
        val restoredAe = gate.begin(ExposureRequestGate.Phase.AUTO_PREVIEW)
        gate.applied(initialAe, 31)
        assertFalse(gate.accepts(initialAe, 32, true))
        assertFalse(gate.accepts(restoredAe, 32, true))
        gate.applied(restoredAe, 32)
        assertEquals(ExposureRequestGate.Phase.AUTO_PREVIEW, gate.phase)
        assertTrue(gate.accepts(restoredAe, 33, true))
    }

    @Test fun coldStartShutterPriorityUsesOneSixtiethAndRetainsSavedFilmIso() {
        val cards = MeteringCards(4.0, 1.0 / 60, 400)
        val mode = ExposurePriority.restore("S")
        val result = StartupExposure.choose(mode, ExposureReading(2.0, 1.0 / 60, 100), cards,
            listOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0))
        assertEquals(ExposureSelection(2, PresetExposureRange.WITHIN, ExposurePriority.SHUTTER), result)
        assertEquals(1.0 / 60, cards.seconds, 0.0)
        assertEquals(400, cards.filmIso)
    }
}
