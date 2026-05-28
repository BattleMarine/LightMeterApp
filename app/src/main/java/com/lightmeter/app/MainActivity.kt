package com.lightmeter.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.util.Range
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {
    private val sceneEv100 = 16.0

    private val apertureLabels = listOf(
        "f/1.0", "f/1.4", "f/2.0", "f/2.8", "f/4.0",
        "f/5.6", "f/8.0", "f/11.0", "f/16.0", "f/22.0", "f/32.0"
    )
    private val apertureValues = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0)

    private val shutterLabels = listOf(
        "1/2s", "1/4", "1/8", "1/15", "1/30",
        "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000"
    )
    private val shutterValues = listOf(
        0.5, 0.25, 0.125, 1.0 / 15.0, 1.0 / 30.0,
        1.0 / 60.0, 1.0 / 125.0, 1.0 / 250.0, 1.0 / 500.0, 1.0 / 1000.0, 1.0 / 2000.0
    )

    private val isoLabels = listOf("25", "50", "100", "200", "400", "800", "1600", "3200", "6400", "12800", "25600")
    private val isoValues = listOf(25, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600)

    private var apertureIndex = 4
    private var shutterIndex = 10
    private var isoIndex = 2

    private lateinit var previewView: PreviewView
    private lateinit var simulationTintView: View
    private lateinit var liveStatusText: TextView
    private lateinit var liveEvValueText: TextView
    private lateinit var liveOffsetText: TextView
    private lateinit var liveReadoutText: TextView
    private lateinit var targetMetricValue: TextView
    private lateinit var currentMetricValue: TextView
    private lateinit var offsetMetricValue: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var supportsManualSensor = false
    private var availableApertures: FloatArray = floatArrayOf()
    private var exposureTimeRange: Range<Long>? = null
    private var sensitivityRange: Range<Int>? = null
    private var minimumFocusDistance: Float? = null
    private var latestCameraEv100: Double? = null
    private var latestCaptureSummary = "Awaiting camera preview"

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraProvider()
        } else {
            latestCaptureSummary = "Camera permission denied"
            refreshExposureReadouts()
            updateCameraStatus("Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        refreshExposureReadouts()

        if (hasCameraPermission()) {
            startCameraProvider()
        } else {
            updateCameraStatus("Requesting camera permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission() && cameraProvider == null) {
            startCameraProvider()
        }
    }

    private fun createContentView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("#111318"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(appHeader())
            addView(space(10))
            addView(liveViewPanel())
            addView(space(10))
            addView(controlPanel())
        }
    }

    private fun appHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("LightMeter", 28f, "#F5F1E8", bold = true))
            addView(space(4))
            addView(label("Manual exposure assistant", 14f, "#AEB6C3"))
        }
    }

    private fun liveViewPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.2f
            )
            setBackgroundColor(color("#1C2028"))

            val previewFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            previewView = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            simulationTintView = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                alpha = 0f
            }

            previewFrame.addView(previewView)
            previewFrame.addView(simulationTintView)
            previewFrame.addView(liveOverlay())
            addView(previewFrame)
            addView(space(10))
            addView(evStrip())
        }
    }

    private fun evStrip(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#252B35"))
            layoutParams = LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )

            targetMetricValue = metric("Scene", "16.0 EV")
            currentMetricValue = metric("Setting", "15.0 EV")
            offsetMetricValue = metric("Delta", "0.0 EV")

            addView(targetMetricValue)
            addView(currentMetricValue)
            addView(offsetMetricValue)
        }
    }

    private fun liveOverlay(): View {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(14), dp(14), dp(14), dp(14))

            val topStatus = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Live View", 13f, "#D6A84F", bold = true))
                addView(space(4))
                liveStatusText = label("Waiting for camera", 12f, "#AEB6C3")
                addView(liveStatusText)
            }
            addView(
                topStatus,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
            )

            val centerStack = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                liveEvValueText = label("Applied EV100 16.0", 40f, "#D6A84F", bold = true).centered()
                liveOffsetText = label("On target", 13f, "#F5F1E8").centered()
                liveReadoutText = label("Preview only", 12f, "#AEB6C3").centered()
                addView(liveEvValueText)
                addView(space(6))
                addView(liveOffsetText)
                addView(space(6))
                addView(liveReadoutText)
            }
            addView(
                centerStack,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun controlPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#1C2028"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.95f
            )

            addView(sectionHeader("Controls", "Swipe each dial up or down"))
            addView(space(10))
            addView(controlCardsRow())
            addView(space(10))
            addView(footerHint())
        }
    }

    private fun controlCardsRow(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            addView(
                controlCard(
                    title = "Aperture",
                    labels = apertureLabels,
                    initialIndex = apertureIndex,
                    onIndexChanged = {
                        apertureIndex = it
                        onControlChanged()
                    }
                )
            )
            addView(spaceHorizontal(8))
            addView(
                controlCard(
                    title = "Shutter",
                    labels = shutterLabels,
                    initialIndex = shutterIndex,
                    onIndexChanged = {
                        shutterIndex = it
                        onControlChanged()
                    }
                )
            )
            addView(spaceHorizontal(8))
            addView(
                controlCard(
                    title = "ISO",
                    labels = isoLabels,
                    initialIndex = isoIndex,
                    onIndexChanged = {
                        isoIndex = it
                        onControlChanged()
                    }
                )
            )
        }
    }

    private fun controlCard(
        title: String,
        labels: List<String>,
        initialIndex: Int,
        onIndexChanged: (Int) -> Unit
    ): View {
        return DialValueCard(
            context = this,
            title = title,
            labels = labels,
            initialIndex = initialIndex,
            onIndexChanged = onIndexChanged
        )
    }

    private fun onControlChanged() {
        refreshExposureReadouts()
        applyRequestedCameraState()
    }

    private fun refreshExposureReadouts() {
        val aperture = apertureValues[apertureIndex]
        val shutterSeconds = shutterValues[shutterIndex]
        val iso = isoValues[isoIndex]

        val settingEv100 = calculateSettingEv100(aperture, shutterSeconds)
        val requiredEv100 = sceneEv100 + isoAdjustmentEv100(iso)
        val appliedCameraEv100 = latestCameraEv100 ?: settingEv100
        val delta = appliedCameraEv100 - requiredEv100

        liveEvValueText.text = "Applied EV100 ${formatEv(appliedCameraEv100)}"
        liveOffsetText.text = exposureStateText(delta)
        liveReadoutText.text = latestCaptureSummary

        targetMetricValue.text = metricValue("Scene\n${formatEv(sceneEv100)}")
        currentMetricValue.text = metricValue("Setting\n${formatEv(settingEv100)}")
        offsetMetricValue.text = metricValue("Delta\n${formatSignedEv(delta)}")

        updateSimulationTint(delta)
    }

    private fun startCameraProvider() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
            } catch (exception: Exception) {
                latestCaptureSummary = "Camera unavailable"
                updateCameraStatus("Camera unavailable")
                refreshExposureReadouts()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = selectCameraSelector(provider)

        if (selector == null) {
            latestCaptureSummary = "No usable camera found"
            updateCameraStatus("No usable camera found")
            refreshExposureReadouts()
            return
        }

        try {
            provider.unbindAll()
        } catch (_: Exception) {
        }

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder)
            .setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    handleCaptureResult(result)
                }
            })

        previewView.display?.rotation?.let { previewBuilder.setTargetRotation(it) }

        val preview = previewBuilder.build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        activeCamera = provider.bindToLifecycle(this, selector, preview)
        inspectCameraCapabilities(activeCamera)
        applyRequestedCameraState()
        updateCameraStatus(buildCameraStatus())
        latestCaptureSummary = buildCameraMetadataSummary()
        refreshExposureReadouts()
    }

    private fun selectCameraSelector(provider: ProcessCameraProvider): CameraSelector? {
        return when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> null
        }
    }

    private fun inspectCameraCapabilities(camera: Camera?) {
        if (camera == null) {
            supportsManualSensor = false
            availableApertures = floatArrayOf()
            exposureTimeRange = null
            sensitivityRange = null
            minimumFocusDistance = null
            return
        }

        val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
        val capabilities = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        ) ?: intArrayOf()

        supportsManualSensor = capabilities.any { it == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR }
        availableApertures = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES
        ) ?: floatArrayOf()
        exposureTimeRange = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        sensitivityRange = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        minimumFocusDistance = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        )
    }

    private fun applyRequestedCameraState() {
        val camera = activeCamera ?: return

        val requestedAperture = apertureValues[apertureIndex]
        val requestedShutterSeconds = shutterValues[shutterIndex]
        val requestedIso = isoValues[isoIndex]

        val exposureSeconds = requestedShutterSeconds
        val exposureNanos = (exposureSeconds * 1_000_000_000.0).roundToLong()

        val optionsBuilder = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)

        val chosenAperture = chooseApertureForDevice(requestedAperture)
        if (chosenAperture != null) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, chosenAperture)
        }

        if (supportsManualSensor) {
            val finalExposureNanos = exposureTimeRange?.let {
                exposureNanos.coerceIn(it.lower, it.upper)
            } ?: exposureNanos
            val finalIso = sensitivityRange?.let {
                requestedIso.coerceIn(it.lower, it.upper)
            } ?: requestedIso

            optionsBuilder
                .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, finalExposureNanos)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, finalIso)

            Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
                optionsBuilder.build()
            )
            updateCameraStatus(buildCameraStatus())
        } else {
            val desiredExposureDelta = (sceneEv100 + isoAdjustmentEv100(requestedIso)) - calculateSettingEv100(
                requestedAperture,
                requestedShutterSeconds
            )
            val exposureState = camera.cameraInfo.exposureState
            val step = exposureState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 1f
            val compensationIndex = (desiredExposureDelta / step).roundToInt().coerceIn(
                exposureState.exposureCompensationRange.lower,
                exposureState.exposureCompensationRange.upper
            )
            camera.cameraControl.setExposureCompensationIndex(compensationIndex)
            updateCameraStatus("Preview only - simulating exposure")
        }
    }

    private fun chooseApertureForDevice(requestedAperture: Double): Float? {
        if (availableApertures.isEmpty()) {
            return null
        }
        var chosen = availableApertures[0]
        var chosenDistance = abs(chosen.toDouble() - requestedAperture)
        for (aperture in availableApertures) {
            val distance = abs(aperture.toDouble() - requestedAperture)
            if (distance < chosenDistance) {
                chosen = aperture
                chosenDistance = distance
            }
        }
        return chosen
    }

    private fun handleCaptureResult(result: TotalCaptureResult) {
        val exposureTimeNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val aperture = result.get(CaptureResult.LENS_APERTURE)

        val exposureSeconds = exposureTimeNanos?.toDouble()?.div(1_000_000_000.0)
        if (exposureSeconds != null && aperture != null) {
            latestCameraEv100 = calculateSettingEv100(aperture.toDouble(), exposureSeconds)
        }

        latestCaptureSummary = buildCaptureSummary(result, exposureSeconds, sensitivity, aperture)
        runOnUiThread {
            refreshExposureReadouts()
        }
    }

    private fun buildCaptureSummary(
        result: TotalCaptureResult,
        exposureSeconds: Double?,
        sensitivity: Int?,
        aperture: Float?
    ): String {
        val parts = mutableListOf<String>()

        if (aperture != null) {
            parts += "f/${formatAperture(aperture.toDouble())}"
        }
        if (exposureSeconds != null) {
            parts += formatExposureTime(exposureSeconds)
        }
        if (sensitivity != null) {
            parts += "ISO $sensitivity"
        }

        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)

        val focusState = when (afState) {
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "AF scanning"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "AF passive"
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "AF locked"
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "AF unlocked"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "AF focused"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "AF hunting"
            else -> null
        }
        val exposureState = when (aeState) {
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> "AE converged"
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> "AE searching"
            CaptureResult.CONTROL_AE_STATE_LOCKED -> "AE locked"
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> "AE flash required"
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "AE precapture"
            CaptureResult.CONTROL_AE_STATE_INACTIVE -> "AE inactive"
            else -> null
        }

        if (focusState != null) {
            parts += focusState
        }
        if (exposureState != null) {
            parts += exposureState
        }

        if (parts.isEmpty()) {
            return "Camera metadata unavailable"
        }

        return parts.joinToString(" · ")
    }

    private fun buildCameraMetadataSummary(): String {
        return if (supportsManualSensor) {
            "Manual sensor control supported"
        } else {
            "Preview only - exposure simulation enabled"
        }
    }

    private fun buildCameraStatus(): String {
        val focusText = when {
            minimumFocusDistance == null || minimumFocusDistance == 0f -> "fixed focus"
            else -> "focus distance available"
        }
        return if (supportsManualSensor) {
            "CameraX preview active · manual sensor · $focusText"
        } else {
            "CameraX preview active · auto exposure · $focusText"
        }
    }

    private fun updateCameraStatus(message: String) {
        if (!::liveStatusText.isInitialized) {
            return
        }
        liveStatusText.text = message
    }

    private fun updateSimulationTint(deltaEv: Double) {
        if (!::simulationTintView.isInitialized) {
            return
        }

        if (supportsManualSensor) {
            simulationTintView.alpha = 0f
            return
        }

        val intensity = min(0.55f, (abs(deltaEv) / 4.0).toFloat() * 0.55f)
        if (intensity <= 0f) {
            simulationTintView.alpha = 0f
            return
        }

        simulationTintView.setBackgroundColor(if (deltaEv >= 0) Color.BLACK else Color.WHITE)
        simulationTintView.alpha = intensity
    }

    private fun calculateSettingEv100(aperture: Double, shutterSeconds: Double): Double {
        return log2((aperture * aperture) / shutterSeconds)
    }

    private fun isoAdjustmentEv100(iso: Int): Double {
        return log2(iso / 100.0)
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)

    private fun formatEv(value: Double): String = String.format("%.1f", value)

    private fun formatSignedEv(value: Double): String {
        return if (value >= 0) "+${formatEv(value)}" else formatEv(value)
    }

    private fun exposureStateText(offset: Double): String {
        val rounded = abs(offset)
        return when {
            rounded < 0.05 -> "On target"
            offset > 0 -> "Under by ${formatEv(rounded)} EV"
            else -> "Over by ${formatEv(rounded)} EV"
        }
    }

    private fun formatExposureTime(seconds: Double): String {
        return if (seconds >= 1.0) {
            "${String.format("%.0f", seconds)}s"
        } else {
            val denominator = (1.0 / seconds).roundToInt().coerceAtLeast(1)
            "1/${denominator}s"
        }
    }

    private fun formatAperture(value: Double): String = String.format("%.1f", value)

    private fun metricValue(value: String): String = value

    private fun footerHint(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#252B35"))

            addView(label("Fixed preset controls", 13f, "#AEB6C3", bold = true))
            addView(space(4))
            addView(
                label(
                    "The three cards stay fixed in the frame and snap between presets only. The live view uses CameraX preview. If the device supports manual sensor control, the app sends actual Camera2 requests for aperture, shutter, ISO, and infinity-focus best effort. If not, the preview remains live and a simple exposure tint simulates the chosen setting.",
                    12f,
                    "#7F8997"
                )
            )
        }
    }

    private fun sectionHeader(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, "#F5F1E8", bold = true))
            addView(space(4))
            addView(label(subtitle, 12f, "#AEB6C3"))
        }
    }

    private fun metric(title: String, value: String): TextView {
        return label("$title\n$value", 16f, "#F5F1E8", bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun label(text: String, size: Float, hexColor: String, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color(hexColor))
            if (bold) {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun TextView.centered(): TextView {
        gravity = Gravity.CENTER
        return this
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))
        }
    }

    private fun spaceHorizontal(width: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(width), LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(hex: String): Int = Color.parseColor(hex)

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    inner class DialValueCard(
        context: Context,
        private val title: String,
        private val labels: List<String>,
        initialIndex: Int,
        private val onIndexChanged: (Int) -> Unit
    ) : LinearLayout(context) {
        private val swipeThreshold = dp(28).toFloat()
        private var selectedIndex = initialIndex
        private var downX = 0f
        private var downY = 0f
        private var activeTouch = false
        private val valueText: TextView

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(color("#202530"))
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true

            addView(label(title, 14f, "#AEB6C3").centered())
            addView(space(8))

            val dialFrame = FrameLayout(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            dialFrame.addView(DialArtworkView(context))

            valueText = label(labels[selectedIndex], 28f, "#F5F1E8", bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            dialFrame.addView(valueText)

            addView(dialFrame)
            addView(space(6))
            addView(label("Swipe up/down", 12f, "#7F8997").centered())

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parentDisallowInterceptTouchEvent(true)
                        downX = event.x
                        downY = event.y
                        activeTouch = true
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        v.parentDisallowInterceptTouchEvent(false)
                        if (activeTouch && abs(dy) > swipeThreshold && abs(dy) > abs(dx)) {
                            val steps = (-dy / swipeThreshold).roundToInt()
                            if (steps != 0) {
                                updateIndex(selectedIndex + steps)
                            }
                        }
                        activeTouch = false
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        v.parentDisallowInterceptTouchEvent(false)
                        activeTouch = false
                        true
                    }

                    else -> true
                }
            }
        }

        private fun updateIndex(newIndex: Int) {
            val clamped = newIndex.coerceIn(0, labels.lastIndex)
            if (clamped == selectedIndex) return
            selectedIndex = clamped
            valueText.text = labels[selectedIndex]
            onIndexChanged(selectedIndex)
        }

        private fun View.parentDisallowInterceptTouchEvent(disallow: Boolean) {
            parent?.requestDisallowInterceptTouchEvent(disallow)
        }
    }

    inner class DialArtworkView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val frameRect = RectF()

        init {
            backgroundPaint.style = Paint.Style.FILL
            highlightPaint.style = Paint.Style.FILL
            shadowPaint.style = Paint.Style.FILL
            toothPaint.style = Paint.Style.FILL
            groovePaint.style = Paint.Style.STROKE
            groovePaint.strokeWidth = dp(1).toFloat()
            groovePaint.color = color("#7F8997")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            val radius = dp(18).toFloat()

            backgroundPaint.shader = LinearGradient(
                0f,
                0f,
                width,
                height,
                intArrayOf(color("#111318"), color("#252B35"), color("#3A414E")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            frameRect.set(
                dp(8).toFloat(),
                dp(8).toFloat(),
                width - dp(8).toFloat(),
                height - dp(8).toFloat()
            )
            canvas.drawRoundRect(frameRect, radius, radius, backgroundPaint)

            highlightPaint.color = color("#4E5665")
            canvas.drawRoundRect(
                RectF(
                    frameRect.left + dp(10),
                    frameRect.top + dp(12),
                    frameRect.right - dp(10),
                    frameRect.top + dp(34).toFloat()
                ),
                radius,
                radius,
                highlightPaint
            )

            shadowPaint.color = color("#0B0D10")
            canvas.drawRoundRect(
                RectF(
                    frameRect.left + dp(10),
                    frameRect.bottom - dp(34).toFloat(),
                    frameRect.right - dp(10),
                    frameRect.bottom - dp(12).toFloat()
                ),
                radius,
                radius,
                shadowPaint
            )

            val centerY = height / 2f
            val centerBand = RectF(
                frameRect.left + dp(10),
                centerY - dp(34),
                frameRect.right - dp(10),
                centerY + dp(34)
            )
            backgroundPaint.shader = LinearGradient(
                0f,
                centerBand.top,
                0f,
                centerBand.bottom,
                intArrayOf(color("#1A1E25"), color("#2F3540"), color("#1A1E25")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(centerBand, dp(16).toFloat(), dp(16).toFloat(), backgroundPaint)

            val grooveTop = centerBand.top + dp(8)
            val grooveBottom = centerBand.bottom - dp(8)
            var y = grooveTop
            while (y < grooveBottom) {
                canvas.drawLine(
                    frameRect.left + dp(16).toFloat(),
                    y,
                    frameRect.right - dp(16).toFloat(),
                    y,
                    groovePaint
                )
                y += dp(8).toFloat()
            }

            toothPaint.color = color("#D6A84F")
            val toothWidth = dp(6).toFloat()
            val toothHeight = dp(3).toFloat()
            val toothGap = dp(7).toFloat()
            var toothY = frameRect.top + dp(18).toFloat()
            while (toothY < frameRect.bottom - dp(18)) {
                canvas.drawRoundRect(
                    RectF(
                        frameRect.left + dp(4).toFloat(),
                        toothY,
                        frameRect.left + dp(4).toFloat() + toothWidth,
                        toothY + toothHeight
                    ),
                    2f,
                    2f,
                    toothPaint
                )
                canvas.drawRoundRect(
                    RectF(
                        frameRect.right - dp(4).toFloat() - toothWidth,
                        toothY,
                        frameRect.right - dp(4).toFloat(),
                        toothY + toothHeight
                    ),
                    2f,
                    2f,
                    toothPaint
                )
                toothY += toothGap
            }
        }
    }
}
