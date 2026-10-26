package com.ckbdevs.tdc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * The loading screen: full black, the TDC logo's two neon hexagons trace
 * themselves in (a bright spark at each tip), then the lettering fades up and
 * the glow pulses once. "CKB Devs / www.ckbdevs.com" sits at the bottom.
 * Same animation as the iPhone app and the desktop app.
 */
class SplashView(context: Context, private val onFinished: () -> Unit) : View(context) {

    // Centre lines of the logo's two hexagons, in the logo image's 494 x 494 space.
    private val outerPts = floatArrayOf(247f, 24f, 437f, 133.7f, 437f, 353.1f, 247f, 462.8f, 57f, 353.1f, 57f, 133.7f)
    // Starts at the bottom and runs the other way, so the two lines chase each other.
    private val innerPts = floatArrayOf(246f, 420.1f, 93f, 331.8f, 93f, 155f, 246f, 66.7f, 399f, 155f, 399f, 331.8f)

    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.splash_lines)
    private val density = resources.displayMetrics.density

    private var outer = 0f
    private var inner = 0f
    private var lettering = 0f
    private var pulse = 0f
    private var breathing: ValueAnimator? = null

    private val logoRect = RectF()
    private var scale = 1f
    private val outerPath = Path()
    private val innerPath = Path()
    private val outerMeasure = PathMeasure()
    private val innerMeasure = PathMeasure()
    private val seg = Path()

    private val revealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }
    private val maskedLogoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.rgb(245, 235, 255)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 13f * density; letterSpacing = 0.08f; color = Color.WHITE
    }
    private val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * density; color = Color.rgb(168, 115, 255)
    }

    /** Bottom inset (gesture bar) so the footer clears it. */
    var bottomInset = 0
        set(v) { field = v; invalidate() }

    init {
        setBackgroundColor(Color.BLACK)
        contentDescription = "The Dance Collective"
    }

    private fun hexPath(pts: FloatArray, out: Path) {
        out.reset()
        for (i in pts.indices step 2) {
            val x = logoRect.left + pts[i] * scale
            val y = logoRect.top + pts[i + 1] * scale
            if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
        }
        out.close()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w * 0.92f, h * 0.7f)
        logoRect.set((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f)
        scale = size / 494f
        hexPath(outerPts, outerPath); hexPath(innerPts, innerPath)
        outerMeasure.setPath(outerPath, false)
        innerMeasure.setPath(innerPath, false)
        revealPaint.strokeWidth = 42f * scale
        sparkPaint.strokeWidth = 9f * scale
        sparkPaint.maskFilter = BlurMaskFilter(max(1f, size / 220f), BlurMaskFilter.Blur.NORMAL)
    }

    private fun drawSegment(canvas: Canvas, m: PathMeasure, from: Float, to: Float, paint: Paint) {
        if (to <= from) return
        seg.reset()
        m.getSegment(from * m.length, to * m.length, seg, true)
        canvas.drawPath(seg, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. The neon lines of the real logo, revealed along each hexagon.
        val layer = canvas.saveLayer(logoRect.left - 40f, logoRect.top - 40f, logoRect.right + 40f, logoRect.bottom + 40f, null)
        drawSegment(canvas, outerMeasure, 0f, outer, revealPaint)
        drawSegment(canvas, innerMeasure, 0f, inner, revealPaint)
        canvas.drawBitmap(logo, null, logoRect, maskedLogoPaint)
        canvas.restoreToCount(layer)
        // 2. Then the whole logo (with the lettering) fades up over it.
        if (lettering > 0f) {
            logoPaint.alpha = (lettering * 255).toInt()
            canvas.drawBitmap(logo, null, logoRect, logoPaint)
        }
        // 3. Glow pulse: the logo added on top of itself, briefly.
        if (pulse > 0f) {
            glowPaint.alpha = (pulse * 150).toInt()
            canvas.drawBitmap(logo, null, logoRect, glowPaint)
        }
        // 4. Sparks leading each line.
        if (outer in 0.001f..0.999f) drawSegment(canvas, outerMeasure, max(0f, outer - 0.025f), outer, sparkPaint)
        if (inner in 0.001f..0.999f) drawSegment(canvas, innerMeasure, max(0f, inner - 0.025f), inner, sparkPaint)
        // 5. Footer.
        if (lettering > 0f) {
            val a = (lettering * 255).toInt()
            footerPaint.alpha = (a * 0.72f).toInt(); urlPaint.alpha = (a * 0.85f).toInt()
            val base = height - bottomInset - 16f * density
            canvas.drawText("CKB Devs", width / 2f, base - 17f * density, footerPaint)
            canvas.drawText("www.ckbdevs.com", width / 2f, base, urlPaint)
        }
    }

    private fun tween(from: Float, to: Float, duration: Long, delay: Long, interp: android.animation.TimeInterpolator? = null, set: (Float) -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration; startDelay = delay
            if (interp != null) interpolator = interp
            addUpdateListener { set(it.animatedValue as Float); invalidate() }
            start()
        }

    /** Plays the animation; onFinished runs after about 3 seconds. */
    fun play() {
        val draw = PathInterpolator(0.55f, 0.1f, 0.35f, 1f)
        tween(0f, 1f, 1600, 250, draw) { outer = it }
        tween(0f, 1f, 1600, 400, draw) { inner = it }
        tween(0f, 1f, 700, 1750) { lettering = it }
        tween(0f, 1f, 550, 2200) { pulse = it }.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { tween(1f, 0f, 550, 0) { pulse = it } }
        })
        postDelayed({ onFinished() }, 3000)
    }

    /** The animation is done but the portal is still loading: keep the glow breathing. */
    fun startBreathing() {
        if (breathing != null) return
        breathing = ValueAnimator.ofFloat(0f, 0.8f).apply {
            duration = 1100; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathing?.cancel()
    }
}
