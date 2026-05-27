package com.lightmeter.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ln

class MainActivity : Activity() {
    private val cameraPermissionRequest = 1001
    private val sceneEv100 = 16.0

    private val apertureLabels = listOf(
        "f/1", "f/1.4", "f/2", "f/2.8", "f/4",
        "f/5.6", "f/8", "f/11", "f/16", "f/22", "f/32"
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

    private lateinit var liveEvValueText: TextView
    private lateinit var liveOffsetText: TextView
    private lateinit var targetMetricValue: TextView
    private lateinit var currentMetricValue: TextView
    private lateinit var offsetMetricValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
        }
        setContentView(createContentView())
        refreshExposureReadouts()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("#111318"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(appHeader())
        root.addView(space(10))
        root.addView(liveViewPanel())
        root.addView(space(10))
        root.addView(controlPanel())

        return root
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
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(color("#1C2028"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.25f
            )

            addView(sectionHeader("Live View", "Rear camera preview"))
            addView(space(10))
            addView(liveViewSurface())
            addView(space(10))
            addView(evStrip())
        }
    }

    private fun liveViewSurface(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(color("#252B35"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )

            addView(label("Camera Preview", 22f, "#F5F1E8", bold = true).centered())
            addView(space(6))
            addView(label("CameraX preview placeholder", 14f, "#AEB6C3").centered())
            addView(space(16))
            liveEvValueText = label("EV 15.0", 44f, "#D6A84F", bold = true).centered()
            addView(liveEvValueText)
            addView(space(8))
            liveOffsetText = label("On target", 13f, "#AEB6C3").centered()
            addView(liveOffsetText)
        }
    }

    private fun evStrip(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#252B35"))

            targetMetricValue = metric("Target", "15.0 EV")
            currentMetricValue = metric("Result", "15.0 EV")
            offsetMetricValue = metric("Offset", "0.0 EV")

            addView(targetMetricValue)
            addView(currentMetricValue)
            addView(offsetMetricValue)
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

            addView(controlCard(
                title = "Aperture",
                labels = apertureLabels,
                initialIndex = apertureIndex,
                onIndexChanged = {
                    apertureIndex = it
                    refreshExposureReadouts()
                }
            ))
            addView(spaceHorizontal(8))
            addView(controlCard(
                title = "Shutter",
                labels = shutterLabels,
                initialIndex = shutterIndex,
                onIndexChanged = {
                    shutterIndex = it
                    refreshExposureReadouts()
                }
            ))
            addView(spaceHorizontal(8))
            addView(controlCard(
                title = "ISO",
                labels = isoLabels,
                initialIndex = isoIndex,
                onIndexChanged = {
                    isoIndex = it
                    refreshExposureReadouts()
                }
            ))
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

    private fun refreshExposureReadouts() {
        val settingEv = calculateSettingEv100(
            apertureValues[apertureIndex],
            shutterValues[shutterIndex]
        )
        val requiredEv = sceneEv100 + isoAdjustmentEv100(isoValues[isoIndex])
        val offset = settingEv - requiredEv

        liveEvValueText.text = "Scene EV100 ${formatEv(sceneEv100)}"
        liveOffsetText.text = exposureStateText(offset)

        targetMetricValue.text = metricValue("Scene\n${formatEv(sceneEv100)}")
        currentMetricValue.text = metricValue("Setting\n${formatEv(settingEv)}")
        offsetMetricValue.text = metricValue("Delta\n${formatSignedEv(offset)}")
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
        val rounded = kotlin.math.abs(offset)
        return when {
            rounded < 0.05 -> "On target"
            offset > 0 -> "Under by ${formatEv(rounded)} EV"
            else -> "Over by ${formatEv(rounded)} EV"
        }
    }

    private fun metricValue(value: String): String = value

    private fun footerHint(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(color("#252B35"))

            addView(label("Fixed preset controls", 13f, "#AEB6C3", bold = true))
            addView(space(4))
            addView(label("The three cards are fixed in the frame and only snap between preset values. EV is treated as scene EV100, where bright daylight is higher and darker scenes move toward zero. Aperture follows the standard f-stop series, where each one-stop change moves exposure by 2x and EV by 1. ISO shifts the required setting exposure by log2(ISO/100).", 12f, "#7F8997"))
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
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
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

            val hintColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(0, dp(12), 0, dp(12))
                addView(label("▲", 14f, "#D6A84F", bold = true).centered())
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f)
                    setBackgroundColor(color("#3A414E"))
                })
                addView(label("▼", 14f, "#D6A84F", bold = true).centered())
            }
            dialFrame.addView(hintColumn)

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
                            val direction = if (dy < 0) 1 else -1
                            updateIndex(selectedIndex + direction)
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
                0f, 0f, width, height,
                intArrayOf(color("#111318"), color("#252B35"), color("#3A414E")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            frameRect.set(dp(8).toFloat(), dp(8).toFloat(), width - dp(8).toFloat(), height - dp(8).toFloat())
            canvas.drawRoundRect(frameRect, radius, radius, backgroundPaint)

            highlightPaint.color = color("#4E5665")
            canvas.drawRoundRect(
                RectF(frameRect.left + dp(10), frameRect.top + dp(12), frameRect.right - dp(10), frameRect.top + dp(34).toFloat()),
                radius,
                radius,
                highlightPaint
            )

            shadowPaint.color = color("#0B0D10")
            canvas.drawRoundRect(
                RectF(frameRect.left + dp(10), frameRect.bottom - dp(34).toFloat(), frameRect.right - dp(10), frameRect.bottom - dp(12).toFloat()),
                radius,
                radius,
                shadowPaint
            )

            val centerY = height / 2f
            val centerBand = RectF(frameRect.left + dp(10), centerY - dp(34), frameRect.right - dp(10), centerY + dp(34))
            backgroundPaint.shader = LinearGradient(
                0f, centerBand.top, 0f, centerBand.bottom,
                intArrayOf(color("#1A1E25"), color("#2F3540"), color("#1A1E25")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(centerBand, dp(16).toFloat(), dp(16).toFloat(), backgroundPaint)

            val grooveTop = centerBand.top + dp(8)
            val grooveBottom = centerBand.bottom - dp(8)
            var y = grooveTop
            while (y < grooveBottom) {
                canvas.drawLine(frameRect.left + dp(16).toFloat(), y, frameRect.right - dp(16).toFloat(), y, groovePaint)
                y += dp(8).toFloat()
            }

            toothPaint.color = color("#D6A84F")
            val toothWidth = dp(6).toFloat()
            val toothHeight = dp(3).toFloat()
            val toothGap = dp(7).toFloat()
            var toothY = frameRect.top + dp(18).toFloat()
            while (toothY < frameRect.bottom - dp(18)) {
                canvas.drawRoundRect(
                    RectF(frameRect.left + dp(4).toFloat(), toothY, frameRect.left + dp(4).toFloat() + toothWidth, toothY + toothHeight),
                    2f,
                    2f,
                    toothPaint
                )
                canvas.drawRoundRect(
                    RectF(frameRect.right - dp(4).toFloat() - toothWidth, toothY, frameRect.right - dp(4).toFloat(), toothY + toothHeight),
                    2f,
                    2f,
                    toothPaint
                )
                toothY += toothGap
            }
        }
    }
}
