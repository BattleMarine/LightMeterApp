package com.lightmeter.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val cameraPermissionRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
        }
        setContentView(createContentView())
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color("#111318"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        root.addView(label("LightMeter", 28f, "#F5F1E8", bold = true))
        root.addView(label("Manual exposure assistant", 14f, "#AEB6C3"))
        root.addView(space(20))

        root.addView(previewPanel())
        root.addView(space(18))
        root.addView(readingPanel())
        root.addView(space(18))
        root.addView(modeRow())
        root.addView(space(14))
        root.addView(settingsPanel())

        return root
    }

    private fun previewPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(color("#1C2028"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            addView(label("Camera Preview", 20f, "#F5F1E8", bold = true).centered())
            addView(space(8))
            addView(label("CameraX preview will be connected here.", 14f, "#AEB6C3").centered())
            addView(space(18))
            addView(label("EV 12.0", 44f, "#D6A84F", bold = true).centered())
        }
    }

    private fun readingPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(color("#252B35"))
            addView(metric("Target", "0.0 EV"))
            addView(metric("Current", "+0.0"))
            addView(metric("Meter", "12.0"))
        }
    }

    private fun modeRow(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(chip("P"))
            addView(chip("A"))
            addView(chip("S"))
            addView(chip("M", selected = true))
        }
    }

    private fun settingsPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(color("#1C2028"))
            addView(setting("Aperture", "f/4"))
            addView(setting("Shutter", "1/125"))
            addView(setting("ISO", "100"))
        }
    }

    private fun metric(title: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(label(title, 12f, "#AEB6C3").centered())
            addView(label(value, 20f, "#F5F1E8", bold = true).centered())
        }
    }

    private fun setting(title: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(label(title, 16f, "#AEB6C3"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(value, 24f, "#F5F1E8", bold = true))
        }
    }

    private fun chip(text: String, selected: Boolean = false): View {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(color(if (selected) "#111318" else "#F5F1E8"))
            setBackgroundColor(color(if (selected) "#D6A84F" else "#252B35"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(8)
            }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(hex: String): Int = Color.parseColor(hex)
}
