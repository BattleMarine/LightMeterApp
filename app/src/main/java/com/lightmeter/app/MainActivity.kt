package com.lightmeter.app

import android.animation.AnimatorSet
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
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
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Range
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {
    private val sceneEv100 = 16.0

    private data class DialPresetSet(
        val apertureLabels: List<String>,
        val apertureValues: List<Double>,
        val shutterLabels: List<String>,
        val shutterValues: List<Double>,
        val isoLabels: List<String>,
        val isoValues: List<Int>
    )

    private val oneStopPreset = DialPresetSet(
        apertureLabels = listOf(
            "f/1.0", "f/1.4", "f/2.0", "f/2.8", "f/4.0",
            "f/5.6", "f/8.0", "f/11.0", "f/16.0", "f/22.0", "f/32.0"
        ),
        apertureValues = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0),
        shutterLabels = listOf(
            "1/2s", "1/4", "1/8", "1/15", "1/30",
            "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000"
        ),
        shutterValues = listOf(
            0.5, 0.25, 0.125, 1.0 / 15.0, 1.0 / 30.0,
            1.0 / 60.0, 1.0 / 125.0, 1.0 / 250.0, 1.0 / 500.0, 1.0 / 1000.0, 1.0 / 2000.0
        ),
        isoLabels = listOf("25", "50", "100", "200", "400", "800", "1600", "3200", "6400", "12800", "25600"),
        isoValues = listOf(25, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600)
    )

    private val halfStopPreset = DialPresetSet(
        apertureLabels = listOf(
            "f/1.0", "f/1.2", "f/1.4", "f/1.7", "f/2.0", "f/2.4", "f/2.8", "f/3.5", "f/4.0", "f/4.8",
            "f/5.6", "f/6.7", "f/8.0", "f/9.5", "f/11", "f/13", "f/16", "f/19", "f/22", "f/27", "f/32"
        ),
        apertureValues = listOf(
            1.0, 1.2, 1.4, 1.7, 2.0, 2.4, 2.8, 3.5, 4.0, 4.8,
            5.6, 6.7, 8.0, 9.5, 11.0, 13.0, 16.0, 19.0, 22.0, 27.0, 32.0
        ),
        shutterLabels = listOf(
            "1/2", "1/2.5", "1/3", "1/4", "1/5", "1/6", "1/8", "1/10", "1/13", "1/15",
            "1/20", "1/25", "1/30", "1/40", "1/50", "1/60", "1/80", "1/100", "1/125", "1/160",
            "1/200", "1/250", "1/320", "1/400", "1/500", "1/640", "1/800", "1/1000", "1/1250", "1/1600", "1/2000"
        ),
        shutterValues = listOf(
            0.5, 0.4, 1.0 / 3.0, 0.25, 0.2, 1.0 / 6.0, 0.125, 0.1, 1.0 / 13.0, 1.0 / 15.0,
            1.0 / 20.0, 1.0 / 25.0, 1.0 / 30.0, 1.0 / 40.0, 1.0 / 50.0, 1.0 / 60.0, 1.0 / 80.0, 1.0 / 100.0, 1.0 / 125.0, 1.0 / 160.0,
            1.0 / 200.0, 1.0 / 250.0, 1.0 / 320.0, 1.0 / 400.0, 1.0 / 500.0, 1.0 / 640.0, 1.0 / 800.0, 1.0 / 1000.0, 1.0 / 1250.0, 1.0 / 1600.0, 1.0 / 2000.0
        ),
        isoLabels = listOf("25", "35", "50", "70", "100", "140", "200", "280", "400", "560", "800", "1100", "1600", "2200", "3200", "4500", "6400", "9000", "12800", "18000", "25600"),
        isoValues = listOf(25, 35, 50, 70, 100, 140, 200, 280, 400, 560, 800, 1100, 1600, 2200, 3200, 4500, 6400, 9000, 12800, 18000, 25600)
    )

    private val thirdStopPreset = DialPresetSet(
        apertureLabels = listOf(
            "f/1.0", "f/1.1", "f/1.2", "f/1.4", "f/1.6", "f/1.8", "f/2.0", "f/2.2", "f/2.5", "f/2.8",
            "f/3.2", "f/3.5", "f/4.0", "f/4.5", "f/5.0", "f/5.6", "f/6.3", "f/7.1", "f/8.0", "f/9.0",
            "f/10", "f/11", "f/13", "f/14", "f/16", "f/18", "f/20", "f/22", "f/25", "f/29", "f/32"
        ),
        apertureValues = listOf(
            1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8,
            3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0,
            10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0
        ),
        shutterLabels = listOf(
            "1/2", "1/2.5", "1/3", "1/4", "1/5", "1/6", "1/8", "1/10", "1/13", "1/15",
            "1/20", "1/25", "1/30", "1/40", "1/50", "1/60", "1/80", "1/100", "1/125", "1/160",
            "1/200", "1/250", "1/320", "1/400", "1/500", "1/640", "1/800", "1/1000", "1/1250", "1/1600", "1/2000"
        ),
        shutterValues = listOf(
            0.5, 0.4, 1.0 / 3.0, 0.25, 0.2, 1.0 / 6.0, 0.125, 0.1, 1.0 / 13.0, 1.0 / 15.0,
            1.0 / 20.0, 1.0 / 25.0, 1.0 / 30.0, 1.0 / 40.0, 1.0 / 50.0, 1.0 / 60.0, 1.0 / 80.0, 1.0 / 100.0, 1.0 / 125.0, 1.0 / 160.0,
            1.0 / 200.0, 1.0 / 250.0, 1.0 / 320.0, 1.0 / 400.0, 1.0 / 500.0, 1.0 / 640.0, 1.0 / 800.0, 1.0 / 1000.0, 1.0 / 1250.0, 1.0 / 1600.0, 1.0 / 2000.0
        ),
        isoLabels = listOf("25", "32", "40", "50", "64", "80", "100", "125", "160", "200", "250", "320", "400", "500", "640", "800", "1000", "1250", "1600", "2000", "2500", "3200", "4000", "5000", "6400", "8000", "10000", "12800", "16000", "20000", "25600"),
        isoValues = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600)
    )

    private var activeDialPresetSet = thirdStopPreset

    private var apertureIndex = 6
    private var shutterIndex = 15
    private var isoIndex = 6
    private var adjustmentStepLabel = "1/3stop"

    private lateinit var previewView: PreviewView
    private lateinit var simulationTintView: View
    private lateinit var apertureDialCard: DialValueCard
    private lateinit var shutterDialCard: DialValueCard
    private lateinit var isoDialCard: DialValueCard

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var supportsManualSensor = false
    private var availableApertures: FloatArray = floatArrayOf()
    private var exposureTimeRange: Range<Long>? = null
    private var sensitivityRange: Range<Int>? = null
    private var minimumFocusDistance: Float? = null
    private var latestPhysicalAperture: Double? = null
    private var latestPhysicalShutterSeconds: Double? = null
    private var latestPhysicalIso: Int? = null
    private var previewResidualEv: Double = 0.0
    private val simulationTintHandler = Handler(Looper.getMainLooper())
    private var pendingSimulationTintEv: Double = 0.0
    private val applySimulationTintRunnable = Runnable {
        updateSimulationTint(pendingSimulationTintEv)
    }
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }
        enterFullscreenMode()
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
        enterFullscreenMode()
        if (hasCameraPermission() && cameraProvider == null) {
            startCameraProvider()
        }
    }

    private fun enterFullscreenMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            addView(previewFrame)
        }
    }

    private fun controlPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#1C2028"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                controlPanelHeightPx()
            )

            addView(controlHeader())
            addView(space(10))
            addView(controlCardsRow())
        }
    }

    private fun controlPanelHeightPx(): Int {
        val screenHeightDp = resources.configuration.screenHeightDp
        val targetDp = when {
            screenHeightDp >= 840 -> 356
            screenHeightDp >= 780 -> 344
            screenHeightDp >= 720 -> 332
            screenHeightDp >= 660 -> 316
            else -> 300
        }
        return dp(targetDp)
    }

    private fun controlHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(
                sectionHeader("Controls", "Swipe each dial up or down").apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(spaceHorizontal(10))
            addView(stepModeSpinner())
        }
    }

    private fun stepModeSpinner(): View {
        val modes = listOf("1stop", "1/2stop", "1/3stop")
        return Spinner(this).apply {
            val adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                modes
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(modes.indexOf(adjustmentStepLabel).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    adjustmentStepLabel = modes[position]
                    applyAdjustmentStepMode(adjustmentStepLabel)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
            }
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

            apertureDialCard = controlCard(
                title = "Aperture",
                labels = activeDialPresetSet.apertureLabels,
                initialIndex = apertureIndex,
                onIndexChanged = {
                    apertureIndex = it
                    onControlChanged()
                }
            )
            addView(apertureDialCard)
            addView(spaceHorizontal(8))
            shutterDialCard = controlCard(
                title = "Shutter",
                labels = activeDialPresetSet.shutterLabels,
                initialIndex = shutterIndex,
                onIndexChanged = {
                    shutterIndex = it
                    onControlChanged()
                }
            )
            addView(shutterDialCard)
            addView(spaceHorizontal(8))
            isoDialCard = controlCard(
                title = "ISO",
                labels = activeDialPresetSet.isoLabels,
                initialIndex = isoIndex,
                onIndexChanged = {
                    isoIndex = it
                    onControlChanged()
                }
            )
            addView(isoDialCard)
        }
    }

    private fun applyAdjustmentStepMode(mode: String) {
        val nextPreset = when (mode) {
            "1/2stop" -> halfStopPreset
            "1/3stop" -> thirdStopPreset
            else -> oneStopPreset
        }
        val currentAperture = activeDialPresetSet.apertureValues.getOrNull(apertureIndex)
            ?: activeDialPresetSet.apertureValues.first()
        val currentShutter = activeDialPresetSet.shutterValues.getOrNull(shutterIndex)
            ?: activeDialPresetSet.shutterValues.first()
        val currentIso = activeDialPresetSet.isoValues.getOrNull(isoIndex)
            ?: activeDialPresetSet.isoValues.first()

        activeDialPresetSet = nextPreset
        apertureIndex = nearestIndexDouble(nextPreset.apertureValues, currentAperture)
        shutterIndex = nearestIndexDouble(nextPreset.shutterValues, currentShutter)
        isoIndex = nearestIndexInt(nextPreset.isoValues, currentIso.toDouble())

        if (::apertureDialCard.isInitialized && ::shutterDialCard.isInitialized && ::isoDialCard.isInitialized) {
            apertureDialCard.updateItems(nextPreset.apertureLabels, apertureIndex)
            shutterDialCard.updateItems(nextPreset.shutterLabels, shutterIndex)
            isoDialCard.updateItems(nextPreset.isoLabels, isoIndex)
        }

        onControlChanged()
    }

    private fun nearestIndexDouble(values: List<Double>, target: Double): Int {
        return values.indices.minByOrNull { abs(values[it] - target) } ?: 0
    }

    private fun nearestIndexInt(values: List<Int>, target: Double): Int {
        return values.indices.minByOrNull { abs(values[it] - target) } ?: 0
    }

    private fun controlCard(
        title: String,
        labels: List<String>,
        initialIndex: Int,
        onIndexChanged: (Int) -> Unit,
        valueTextSizeSp: Float = 22f
    ): DialValueCard {
        return DialValueCard(
            context = this,
            title = title,
            labels = labels,
            initialIndex = initialIndex,
            onIndexChanged = onIndexChanged,
            valueTextSizeSp = valueTextSizeSp
        )
    }

    private fun onControlChanged() {
        applyRequestedCameraState()
        refreshExposureReadouts()
    }

    private fun refreshExposureReadouts() {
        buildExposureSnapshot()
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

        val targetSnapshot = buildExposureSnapshot()
        val currentPhysical = currentPhysicalState()
        var residualEv = targetSnapshot.virtualBrightnessEv100 - targetSnapshot.physicalBrightnessEv100

        if (supportsManualSensor) {
            val requestedAperture = activeDialPresetSet.apertureValues[apertureIndex]
            val requestedShutterSeconds = activeDialPresetSet.shutterValues[shutterIndex]
            val requestedIso = activeDialPresetSet.isoValues[isoIndex]

            val physicalAperture = chooseApertureForDevice(requestedAperture)?.toDouble()
                ?: currentPhysical.aperture
            val physicalTarget = resolvePhysicalExposureTarget(
                requestedAperture = requestedAperture,
                requestedShutterSeconds = requestedShutterSeconds,
                requestedIso = requestedIso,
                physicalAperture = physicalAperture
            )
            val physicalBrightnessAfterRequest = brightnessForPhysical(
                physicalTarget.aperture,
                physicalTarget.shutterSeconds,
                physicalTarget.iso
            )
            residualEv = targetSnapshot.virtualBrightnessEv100 - physicalBrightnessAfterRequest

            val exposureNanos = (physicalTarget.shutterSeconds * 1_000_000_000.0).roundToLong()
            val finalExposureNanos = exposureNanos
            val finalIso = physicalTarget.iso

            val optionsBuilder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
                .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, finalExposureNanos)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, finalIso)

            chooseApertureForDevice(requestedAperture)?.let { chosenAperture ->
                optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, chosenAperture)
            }

            Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
                optionsBuilder.build()
            )
            updateCameraStatus(buildCameraStatus())
        } else {
            val exposureState = camera.cameraInfo.exposureState
            val step = exposureState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 1f
            val compensationIndex = (residualEv / step).roundToInt().coerceIn(
                exposureState.exposureCompensationRange.lower,
                exposureState.exposureCompensationRange.upper
            )
            camera.cameraControl.setExposureCompensationIndex(compensationIndex)
            updateCameraStatus("Preview only - simulating exposure")
        }

    }

    private fun currentPhysicalState(): PhysicalState {
        val requestedAperture = activeDialPresetSet.apertureValues[apertureIndex]
        val currentAperture = latestPhysicalAperture
            ?: availableApertures.firstOrNull()?.toDouble()
            ?: requestedAperture
        val currentShutter = latestPhysicalShutterSeconds ?: activeDialPresetSet.shutterValues[shutterIndex]
        val currentIso = latestPhysicalIso ?: activeDialPresetSet.isoValues[isoIndex]
        return PhysicalState(
            aperture = currentAperture,
            shutterSeconds = currentShutter,
            iso = currentIso
        )
    }

    private fun brightnessForPhysical(aperture: Double, shutterSeconds: Double, iso: Int): Double {
        val settingEv100 = calculateSettingEv100(aperture, shutterSeconds)
        val requiredEv100 = sceneEv100 + isoAdjustmentEv100(iso)
        val offsetEv = settingEv100 - requiredEv100
        return sceneEv100 - offsetEv
    }

    private fun chooseIsoForResidual(currentIso: Int, residualEv: Double): Int {
        val targetIso = currentIso * 2.0.pow(residualEv)
        return pickNearestSupportedIso(targetIso)
    }

    private fun chooseShutterForResidual(currentShutterSeconds: Double, residualEv: Double): Double {
        val targetShutterSeconds = currentShutterSeconds * 2.0.pow(residualEv)
        return pickNearestSupportedShutter(targetShutterSeconds)
    }

    private fun calculateEquivalentIso(
        requestedAperture: Double,
        requestedShutterSeconds: Double,
        requestedIso: Int,
        physicalAperture: Double,
        physicalShutterSeconds: Double
    ): Double {
        val requestedSettingEv100 = calculateSettingEv100(requestedAperture, requestedShutterSeconds)
        val physicalSettingEv100 = calculateSettingEv100(physicalAperture, physicalShutterSeconds)
        return requestedIso * 2.0.pow(physicalSettingEv100 - requestedSettingEv100)
    }

    private fun resolvePhysicalExposureTarget(
        requestedAperture: Double,
        requestedShutterSeconds: Double,
        requestedIso: Int,
        physicalAperture: Double
    ): PhysicalExposureTarget {
        val targetExposureFactor = exposureFactorForSetting(
            requestedAperture,
            requestedShutterSeconds,
            requestedIso
        )
        val supportedShutters = activeDialPresetSet.shutterValues
        val supportedIsos = activeDialPresetSet.isoValues

        var shutterSeconds = pickNearestSupportedShutter(
            targetSeconds = requestedShutterSeconds,
            candidates = supportedShutters
        )
        var iso = pickNearestSupportedIso(
            requiredIsoForTargetExposure(
                physicalAperture = physicalAperture,
                shutterSeconds = shutterSeconds,
                targetExposureFactor = targetExposureFactor
            ),
            supportedIsos
        )

        repeat(5) {
            val requiredIso = requiredIsoForTargetExposure(
                physicalAperture = physicalAperture,
                shutterSeconds = shutterSeconds,
                targetExposureFactor = targetExposureFactor
            )
            if (requiredIso in supportedIsos.first().toDouble()..supportedIsos.last().toDouble()) {
                iso = pickNearestSupportedIso(requiredIso, supportedIsos)
                return PhysicalExposureTarget(
                    aperture = physicalAperture,
                    shutterSeconds = shutterSeconds,
                    iso = iso,
                    residualEv = targetExposureFactor - exposureFactorForSetting(
                        physicalAperture,
                        shutterSeconds,
                        iso
                    )
                )
            }

            val direction = if (requiredIso > supportedIsos.last()) {
                ShutterDirection.SLOWER
            } else {
                ShutterDirection.FASTER
            }
            iso = if (direction == ShutterDirection.SLOWER) {
                supportedIsos.last()
            } else {
                supportedIsos.first()
            }

            val residualEv = targetExposureFactor - exposureFactorForSetting(
                physicalAperture,
                shutterSeconds,
                iso
            )
            val movedShutter = chooseShutterForResidual(shutterSeconds, residualEv)
            shutterSeconds = pickShutterInDirection(
                candidate = movedShutter,
                reference = shutterSeconds,
                direction = direction,
                candidates = supportedShutters
            )
        }

        val finalRequiredIso = requiredIsoForTargetExposure(
            physicalAperture = physicalAperture,
            shutterSeconds = shutterSeconds,
            targetExposureFactor = targetExposureFactor
        )
        iso = when {
            finalRequiredIso > supportedIsos.last() -> supportedIsos.last()
            finalRequiredIso < supportedIsos.first() -> supportedIsos.first()
            else -> pickNearestSupportedIso(finalRequiredIso, supportedIsos)
        }

        return PhysicalExposureTarget(
            aperture = physicalAperture,
            shutterSeconds = shutterSeconds,
            iso = iso,
            residualEv = targetExposureFactor - exposureFactorForSetting(
                physicalAperture,
                shutterSeconds,
                iso
            )
        )
    }

    private fun exposureFactorForSetting(aperture: Double, shutterSeconds: Double, iso: Int): Double {
        return calculateSettingEv100(aperture, shutterSeconds) - isoAdjustmentEv100(iso)
    }

    private fun requiredIsoForTargetExposure(
        physicalAperture: Double,
        shutterSeconds: Double,
        targetExposureFactor: Double
    ): Double {
        val settingEv100 = calculateSettingEv100(physicalAperture, shutterSeconds)
        return 100.0 * 2.0.pow(settingEv100 - targetExposureFactor)
    }

    private fun pickNearestSupportedIso(targetIso: Double, candidates: List<Int>): Int {
        return candidates.minByOrNull { abs(it - targetIso) } ?: candidates.first()
    }

    private fun pickNearestSupportedShutter(targetSeconds: Double, candidates: List<Double>): Double {
        return candidates.minByOrNull { abs(it - targetSeconds) } ?: candidates.first()
    }

    private fun pickNearestSupportedIso(targetIso: Double): Int {
        val supportedIsoValues = activeDialPresetSet.isoValues.filter { iso ->
            sensitivityRange?.let { iso in it.lower..it.upper } ?: true
        }.ifEmpty { activeDialPresetSet.isoValues }

        return supportedIsoValues.minByOrNull { abs(it - targetIso) } ?: activeDialPresetSet.isoValues[0]
    }

    private fun pickNearestSupportedShutter(targetSeconds: Double): Double {
        val supportedShutters = activeDialPresetSet.shutterValues.filter { seconds ->
            exposureTimeRange?.let {
                val nanos = (seconds * 1_000_000_000.0).roundToLong()
                nanos in it.lower..it.upper
            } ?: true
        }.ifEmpty { activeDialPresetSet.shutterValues }

        return supportedShutters.minByOrNull { abs(it - targetSeconds) } ?: activeDialPresetSet.shutterValues[0]
    }

    private fun pickShutterInDirection(
        candidate: Double,
        reference: Double,
        direction: ShutterDirection,
        candidates: List<Double>
    ): Double {
        val filtered = when (direction) {
            ShutterDirection.SLOWER -> candidates.filter { it > reference }
            ShutterDirection.FASTER -> candidates.filter { it < reference }
        }

        if (filtered.isEmpty()) {
            return candidate
        }

        return filtered.minByOrNull { abs(it - candidate) } ?: candidate
    }

    private enum class ShutterDirection {
        FASTER,
        SLOWER
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
            latestPhysicalAperture = aperture.toDouble()
            latestPhysicalShutterSeconds = exposureSeconds
            latestPhysicalIso = sensitivity
        }

        latestCaptureSummary = buildCaptureSummary(result, exposureSeconds, sensitivity, aperture)
        val snapshot = buildExposureSnapshot()
        previewResidualEv = snapshot.virtualBrightnessEv100 - snapshot.physicalBrightnessEv100
        runOnUiThread {
            refreshExposureReadouts()
            scheduleSimulationTint(previewResidualEv)
        }
    }

    private fun buildExposureSnapshot(): ExposureSnapshot {
        val requestedAperture = activeDialPresetSet.apertureValues[apertureIndex]
        val requestedShutterSeconds = activeDialPresetSet.shutterValues[shutterIndex]
        val requestedIso = activeDialPresetSet.isoValues[isoIndex]
        val currentPhysical = currentPhysicalState()

        val physicalSettingEv100 = calculateSettingEv100(currentPhysical.aperture, currentPhysical.shutterSeconds)
        val physicalRequiredEv100 = sceneEv100 + isoAdjustmentEv100(currentPhysical.iso)
        val physicalOffsetEv = physicalSettingEv100 - physicalRequiredEv100
        val physicalBrightnessEv100 = sceneEv100 - physicalOffsetEv

        val virtualSettingEv100 = calculateSettingEv100(requestedAperture, requestedShutterSeconds)
        val virtualRequiredEv100 = sceneEv100 + isoAdjustmentEv100(requestedIso)
        val virtualOffsetEv = virtualSettingEv100 - virtualRequiredEv100
        val virtualBrightnessEv100 = sceneEv100 - virtualOffsetEv

        return ExposureSnapshot(
            physicalBrightnessEv100 = physicalBrightnessEv100,
            virtualBrightnessEv100 = virtualBrightnessEv100
        )
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

        return parts.joinToString(" | ")
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
            "CameraX preview active | manual sensor | $focusText"
        } else {
            "CameraX preview active | auto exposure | $focusText"
        }
    }

    private fun updateCameraStatus(message: String) {
        return
    }

    private fun updateSimulationTint(deltaEv: Double) {
        if (!::simulationTintView.isInitialized) {
            return
        }

        val intensity = min(0.65f, (abs(deltaEv) / 4.5).toFloat())
        if (intensity <= 0f) {
            simulationTintView.animate().alpha(0f).setDuration(140L).start()
            return
        }

        simulationTintView.setBackgroundColor(if (deltaEv >= 0) Color.WHITE else Color.BLACK)
        simulationTintView.animate().alpha(intensity).setDuration(140L).start()
    }

    private fun scheduleSimulationTint(deltaEv: Double) {
        pendingSimulationTintEv = deltaEv
        simulationTintHandler.removeCallbacks(applySimulationTintRunnable)
        simulationTintHandler.postDelayed(applySimulationTintRunnable, 90L)
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

    private data class ExposureSnapshot(
        val physicalBrightnessEv100: Double,
        val virtualBrightnessEv100: Double
    )

    private data class PhysicalState(
        val aperture: Double,
        val shutterSeconds: Double,
        val iso: Int
    )

    private data class PhysicalExposureTarget(
        val aperture: Double,
        val shutterSeconds: Double,
        val iso: Int,
        val residualEv: Double
    )

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
        private val onIndexChanged: (Int) -> Unit,
        private val valueTextSizeSp: Float
    ) : LinearLayout(context) {
        private val swipeThreshold = dp(28).toFloat()
        private var selectedIndex = initialIndex
        private var currentLabels = labels
        private var lastSwipeDirection = 1
        private var downX = 0f
        private var downY = 0f
        private var activeTouch = false
        private var transitionAnimator: AnimatorSet? = null
        private lateinit var dialArtworkView: DialArtworkView
        private lateinit var currentValueText: TextView
        private lateinit var incomingValueText: TextView

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
                clipChildren = true
                clipToPadding = true
            }
            dialArtworkView = DialArtworkView(context)
            dialFrame.addView(dialArtworkView)

            currentValueText = label(currentLabels[selectedIndex], valueTextSizeSp, "#F5F1E8", bold = true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setSingleLine(true)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            incomingValueText = label("", valueTextSizeSp, "#F5F1E8", bold = true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setSingleLine(true)
                alpha = 0f
                visibility = View.INVISIBLE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            dialFrame.addView(currentValueText)
            dialFrame.addView(incomingValueText)

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
                            lastSwipeDirection = if (dy < 0f) 1 else -1
                            updateIndex(selectedIndex + lastSwipeDirection, lastSwipeDirection)
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

        private fun updateIndex(newIndex: Int, direction: Int) {
            val clamped = newIndex.coerceIn(0, currentLabels.lastIndex)
            if (clamped == selectedIndex) return
            selectedIndex = clamped
            animateValueChange(currentLabels[selectedIndex], direction)
            onIndexChanged(selectedIndex)
        }

        fun updateItems(newLabels: List<String>, selectedIndex: Int) {
            currentLabels = newLabels
            this.selectedIndex = selectedIndex.coerceIn(0, newLabels.lastIndex)
            currentValueText.text = currentLabels[this.selectedIndex]
            currentValueText.translationY = 0f
            currentValueText.alpha = 1f
            incomingValueText.text = ""
            incomingValueText.translationY = 0f
            incomingValueText.alpha = 0f
            incomingValueText.visibility = View.INVISIBLE
        }

        private fun animateValueChange(nextLabel: String, direction: Int) {
            val travel = max(dp(28).toFloat(), height * 0.24f)
            transitionAnimator?.cancel()
            incomingValueText.animate().cancel()
            currentValueText.animate().cancel()

            currentValueText.visibility = View.VISIBLE
            incomingValueText.text = nextLabel
            incomingValueText.visibility = View.VISIBLE

            currentValueText.translationY = 0f
            currentValueText.alpha = 1f
            incomingValueText.translationY = if (direction > 0) travel else -travel
            incomingValueText.alpha = 0f

            val outgoingTarget = if (direction > 0) -travel else travel
            transitionAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(currentValueText, View.TRANSLATION_Y, 0f, outgoingTarget),
                    ObjectAnimator.ofFloat(currentValueText, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(incomingValueText, View.TRANSLATION_Y, incomingValueText.translationY, 0f),
                    ObjectAnimator.ofFloat(incomingValueText, View.ALPHA, 0f, 1f)
                )
                duration = 180L
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        currentValueText.text = nextLabel
                        currentValueText.translationY = 0f
                        currentValueText.alpha = 1f
                        incomingValueText.visibility = View.INVISIBLE
                        incomingValueText.translationY = 0f
                        incomingValueText.alpha = 0f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        currentValueText.text = nextLabel
                        currentValueText.translationY = 0f
                        currentValueText.alpha = 1f
                        incomingValueText.visibility = View.INVISIBLE
                        incomingValueText.translationY = 0f
                        incomingValueText.alpha = 0f
                    }
                })
                start()
            }
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
