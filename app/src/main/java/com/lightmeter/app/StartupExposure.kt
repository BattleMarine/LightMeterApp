package com.lightmeter.app

import kotlin.math.abs
import kotlin.math.ln

internal data class ExposureReading(val aperture: Double, val seconds: Double, val iso: Int) {
    companion object {
        fun from(aperture: Double?, nanos: Long?, iso: Int?): ExposureReading? {
            if (aperture == null || !aperture.isFinite() || aperture <= 0.0 ||
                nanos == null || nanos <= 0L || iso == null || iso <= 0) return null
            return ExposureReading(aperture, nanos / 1_000_000_000.0, iso)
        }
    }
}

internal enum class PresetExposureRange { WITHIN, TOO_BRIGHT, TOO_DARK }

internal enum class ExposurePriority(val code: String) {
    APERTURE("A"), SHUTTER("S");

    companion object {
        fun restore(saved: String?): ExposurePriority = entries.firstOrNull { it.code == saved } ?: APERTURE
    }
}

internal data class ExposureSelection(
    val index: Int, val range: PresetExposureRange,
    val priority: ExposurePriority = ExposurePriority.APERTURE
)

internal data class ExpectedExposureRequest(
    val aeMode: Int,
    val nanos: Long? = null,
    val iso: Int? = null,
    val aperture: Float? = null,
    val compensation: Int? = null
) {
    // Only identify the submitted request. Actual sensor values may be clamped by hardware.
    fun matches(
        requestAeMode: Int?, resultAeMode: Int?, requestNanos: Long?, requestIso: Int?,
        requestAperture: Float?, requestCompensation: Int?
    ): Boolean = requestAeMode == aeMode && resultAeMode == aeMode &&
        (nanos == null || requestNanos == nanos) && (iso == null || requestIso == iso) &&
        (aperture == null || requestAperture == aperture) &&
        (compensation == null || requestCompensation == compensation)
}

internal class StartupAeProbe {
    var lastReading: ExposureReading? = null
        private set

    fun reset() { lastReading = null }

    fun observe(reading: ExposureReading?, converged: Boolean?): ExposureReading? {
        if (reading == null) return null
        lastReading = reading
        return reading.takeIf { converged == true }
    }
}

internal data class MeteringCards(val aperture: Double, val seconds: Double, val filmIso: Int)

/** One press is a transaction; cards are committed only after preview application succeeds. */
internal class OneShotMetering {
    enum class Stage { IDLE, MEASURING, APPLYING, RESTORING }
    var stage = Stage.IDLE
        private set
    var cards: MeteringCards? = null
        private set
    var priority = ExposurePriority.APERTURE
        private set
    private var selection: ExposureSelection? = null
    val busy: Boolean get() = stage != Stage.IDLE

    fun begin(settings: MeteringCards, ready: Boolean, selectedPriority: ExposurePriority = ExposurePriority.APERTURE): Boolean {
        if (!ready || busy) return false
        cards = settings
        priority = selectedPriority
        selection = null
        stage = Stage.MEASURING
        return true
    }

    fun observe(reading: ExposureReading?, converged: Boolean?, candidates: List<Double>): ExposureSelection? {
        if (stage != Stage.MEASURING || reading == null || converged != true) return null
        val settings = cards ?: return null
        return StartupExposure.choose(priority, reading, settings, candidates).also {
            selection = it
            stage = Stage.APPLYING
        }
    }

    fun applied(): ExposureSelection? {
        if (stage != Stage.APPLYING) return null
        return selection.also { cancel() }
    }

    fun restore(): Boolean {
        if (!busy) return false
        selection = null
        stage = Stage.RESTORING
        return true
    }

    fun restored(): Boolean {
        if (stage != Stage.RESTORING) return false
        cancel()
        return true
    }

    fun cancel() {
        stage = Stage.IDLE
        cards = null
        selection = null
    }
}

internal object StartupExposure {
    fun choose(priority: ExposurePriority, reading: ExposureReading, cards: MeteringCards,
               candidates: List<Double>): ExposureSelection = when (priority) {
        ExposurePriority.APERTURE -> chooseShutter(reading, cards.filmIso, cards.aperture, candidates)
        ExposurePriority.SHUTTER -> chooseAperture(reading, cards.filmIso, cards.seconds, candidates)
    }

    fun chooseAperture(reading: ExposureReading, filmIso: Int, seconds: Double,
                       candidates: List<Double>): ExposureSelection {
        require(filmIso > 0 && seconds.isFinite() && seconds > 0)
        require(reading.aperture.isFinite() && reading.aperture > 0 &&
            reading.seconds.isFinite() && reading.seconds > 0 && reading.iso > 0)
        require(candidates.isNotEmpty() && candidates.all { it.isFinite() && it > 0 })
        fun log2(value: Double) = ln(value) / ln(2.0)
        val target = log2(reading.aperture) + 0.5 * (log2(seconds) - log2(reading.seconds) +
            log2(filmIso.toDouble()) - log2(reading.iso.toDouble()))
        val index = candidates.indices.minByOrNull { abs(2 * (log2(candidates[it]) - target)) }!!
        // A larger aperture number darkens the image: range signs differ from shutter time.
        val range = when {
            target < log2(candidates.min()) - 1e-6 -> PresetExposureRange.TOO_DARK
            target > log2(candidates.max()) + 1e-6 -> PresetExposureRange.TOO_BRIGHT
            else -> PresetExposureRange.WITHIN
        }
        return ExposureSelection(index, range, ExposurePriority.SHUTTER)
    }

    fun chooseShutter(
        reading: ExposureReading,
        filmIso: Int,
        aperture: Double,
        candidates: List<Double>
    ): ExposureSelection {
        require(filmIso > 0 && aperture.isFinite() && aperture > 0)
        require(reading.aperture.isFinite() && reading.aperture > 0 &&
            reading.seconds.isFinite() && reading.seconds > 0 && reading.iso > 0)
        require(candidates.isNotEmpty() && candidates.all { it.isFinite() && it > 0 })
        fun log2(value: Double) = ln(value) / ln(2.0)
        val target = log2(reading.seconds) + 2 * (log2(aperture) - log2(reading.aperture)) +
            log2(reading.iso.toDouble()) - log2(filmIso.toDouble())
        val index = candidates.indices.minByOrNull { abs(log2(candidates[it]) - target) }!!
        val range = when {
            target < log2(candidates.min()) - 1e-6 -> PresetExposureRange.TOO_BRIGHT
            target > log2(candidates.max()) + 1e-6 -> PresetExposureRange.TOO_DARK
            else -> PresetExposureRange.WITHIN
        }
        return ExposureSelection(index, range)
    }

    // Restore the exact film ISO, including values absent from the 1-stop preset.
    fun restoreFilmIso(saved: Int?, presets: List<List<Int>>): Pair<Int, Int> {
        val iso = saved?.takeIf { value -> presets.any { value in it } } ?: 100
        val preset = presets.indexOfFirst { iso in it }
        require(preset >= 0)
        return preset to presets[preset].indexOf(iso)
    }
}

/** Main-thread gate for superseded requests and frames arriving during a transition. */
internal class ExposureRequestGate {
    enum class Phase { IDLE, STARTUP_AE, REQUESTED_AE, MANUAL, AUTO_PREVIEW }
    var phase = Phase.IDLE
        private set
    var token = 0L
        private set
    private var applied = false
    private var frameFence = Long.MAX_VALUE

    fun keepsAppliedTint(next: Phase, plainAutoPreview: Boolean): Boolean =
        !plainAutoPreview && phase == next && (phase == Phase.MANUAL || phase == Phase.AUTO_PREVIEW)

    fun begin(next: Phase): Long {
        phase = next
        applied = false
        frameFence = Long.MAX_VALUE
        return ++token
    }

    fun applied(requestToken: Long, lastFrame: Long) {
        if (requestToken != token || phase == Phase.IDLE) return
        applied = true
        frameFence = lastFrame
    }

    fun accepts(requestToken: Long, frame: Long, requestMatches: Boolean): Boolean =
        requestToken == token && phase != Phase.IDLE && applied && frame > frameFence && requestMatches

    fun stop() { begin(Phase.IDLE) }
}
