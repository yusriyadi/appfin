package com.tellolabs.appfin.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.tellolabs.appfin.R
import com.tellolabs.appfin.domain.OverlayPort
import com.tellolabs.appfin.domain.SessionState

/** All methods run synchronously on the service's main thread, including attach/detach. */
class ProgressOverlay(private val service: AccessibilityService) : OverlayPort {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var label: TextView? = null
    private var counter: TextView? = null
    private var progress: ProgressBar? = null

    override fun show(onCancel: () -> Unit) {
        check(view == null)
        val density = service.resources.displayMetrics.density
        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), 48, (24 * density).toInt(), 48)
            setBackgroundColor(Color.rgb(245, 248, 245))
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        fun text(size: Float) = TextView(service).apply {
            textSize = size
            setTextColor(Color.rgb(25, 40, 30))
            gravity = Gravity.CENTER
            layout.addView(this)
        }
        text(24f).setText(R.string.overlay_title)
        label = text(18f)
        counter = text(16f)
        progress = ProgressBar(service, null, android.R.attr.progressBarStyleHorizontal).also {
            layout.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48))
        }
        layout.addView(Button(service).apply {
            setText(R.string.cancel)
            setOnClickListener { onCancel() }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        params.title = "AppFin progress"
        wm.addView(layout, params) // Propagate attach failure; do not run an invisible session.
        view = layout
    }
    override fun render(state: SessionState) {
        label?.text = state.current?.label ?: service.getString(R.string.preparing)
        counter?.text = service.getString(R.string.progress_count, state.results.size, state.total)
        progress?.max = state.total.coerceAtLeast(1)
        progress?.progress = state.results.size
    }
    override fun hide() {
        val attached = view ?: return
        wm.removeViewImmediate(attached)
        view = null
        label = null
        counter = null
        progress = null
    }
}
