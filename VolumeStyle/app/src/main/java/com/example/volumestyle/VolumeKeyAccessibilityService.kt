package com.example.volumestyle

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/**
 * خدمة Accessibility تعترض ضغط أزرار الصوت الفعلية،
 * تغيّر مستوى الصوت الحقيقي عبر AudioManager،
 * وتعرض شريطًا خاصًا بدل شريط النظام الافتراضي (نفس الطريقة التي
 * تستخدمها تطبيقات مثل Volume Styles).
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var fillView: View? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // تأكيد أن الخدمة تطلب فلترة أحداث المفاتيح (أزرار الصوت)
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER

                // غيّر الصوت الحقيقي لنظام الهاتف (ستريم الميديا)
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    0 // 0 = بدون عرض واجهة النظام الافتراضية
                )
                showOrUpdateOverlay()
            }
            // true = نمنع ظهور شريط النظام الافتراضي، ونتحكم نحن بالعرض
            return true
        }
        return false
    }

    private fun showOrUpdateOverlay() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ratio = current.toFloat() / max.toFloat()

        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_volume, null)
            fillView = overlayView?.findViewById(R.id.fillView)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            params.x = 32

            windowManager.addView(overlayView, params)
        }

        animateFillTo(ratio)
        scheduleHide()
    }

    private fun animateFillTo(ratio: Float) {
        val fill = fillView ?: return
        val trackHeight = (overlayView?.findViewById<View>(R.id.trackView))?.height ?: return
        val targetHeight = (trackHeight * ratio).toInt()

        val currentHeight = fill.layoutParams.height.takeIf { it > 0 } ?: 0
        ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            duration = 180
            addUpdateListener { anim ->
                val params = fill.layoutParams
                params.height = anim.animatedValue as Int
                fill.layoutParams = params
            }
            start()
        }
    }

    private fun scheduleHide() {
        hideRunnable?.let { hideHandler.removeCallbacks(it) }
        hideRunnable = Runnable {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                fillView = null
            }
        }
        hideHandler.postDelayed(hideRunnable!!, 1500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // غير مستخدم - نعتمد فقط على onKeyEvent
    }

    override fun onInterrupt() {}
}
