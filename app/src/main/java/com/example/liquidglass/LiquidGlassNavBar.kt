package com.example.liquidglass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.*

/** A self-contained native bottom bar. Supply a separate view behind it for sampled glass blur. */
class LiquidGlassNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    enum class Destination(val label: String) {
        HOME("Home"), SEARCH("Search"), NOTIFICATIONS("Notifications"), PROFILE("Profile")
    }

    var onDestinationSelected: ((Destination) -> Unit)? = null
    var selectedDestination: Destination = Destination.HOME
        private set

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val blurLayer = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val artwork = GlassArtwork(context)
    private val buttons = mutableListOf<View>()
    private var backdrop: View? = null
    private var snapshot: Bitmap? = null
    private var animator: ValueAnimator? = null
    private var bubblePosition = 0f
    private var capturePending = false

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(40f)
            setColor(Color.rgb(36, 43, 62))
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = dp(18f)
        if (Build.VERSION.SDK_INT >= 28) {
            outlineAmbientShadowColor = Color.BLACK
            outlineSpotShadowColor = Color.BLACK
        }
        if (Build.VERSION.SDK_INT >= 31) {
            blurLayer.setRenderEffect(RenderEffect.createBlurEffect(dp(18f), dp(18f), Shader.TileMode.CLAMP))
        }
        addView(blurLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(artwork, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        Destination.entries.forEach { destination ->
            val button = object : View(context) {
                override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
                override fun drawableStateChanged() { super.drawableStateChanged(); artwork.invalidate() }
            }.apply {
                isClickable = true
                isFocusable = true
                contentDescription = destination.label
                if (Build.VERSION.SDK_INT >= 26) tooltipText = destination.label
                setOnClickListener { select(destination, true) }
            }
            buttons.add(button)
            row.addView(button, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateAccessibility()
    }

    /** The supplied view must be behind this bar and must not contain the bar itself. */
    fun setBackdropView(view: View?) {
        backdrop = view
        refreshBackdrop()
    }

    /** Call after the backdrop changes or scrolls; captures only the small region under the bar. */
    fun refreshBackdrop() {
        if (capturePending) return
        capturePending = true
        post {
            capturePending = false
            if (!isAttachedToWindow) return@post
            val source = backdrop
            if (source == null || width <= 0 || height <= 0 || source.width <= 0 || source.height <= 0) {
                blurLayer.setImageDrawable(null)
                snapshot = null
                return@post
            }
            // A quarter-resolution snapshot keeps allocation and redraw work bounded to the bar.
            val w = max(1, width / 4)
            val h = max(1, height / 4)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val barLocation = IntArray(2)
            val sourceLocation = IntArray(2)
            getLocationOnScreen(barLocation)
            source.getLocationOnScreen(sourceLocation)
            canvas.scale(w.toFloat() / width, h.toFloat() / height)
            canvas.translate((sourceLocation[0] - barLocation[0]).toFloat(), (sourceLocation[1] - barLocation[1]).toFloat())
            source.draw(canvas)
            // Older versions have no RenderEffect. Blur the tiny snapshot once on refresh instead.
            if (Build.VERSION.SDK_INT < 31) boxBlur(bitmap, 3)
            snapshot = bitmap
            blurLayer.setImageBitmap(bitmap)
        }
    }

    fun select(destination: Destination, animate: Boolean = true) {
        if (destination == selectedDestination && bubblePosition == destination.ordinal.toFloat()) return
        selectedDestination = destination
        updateAccessibility()
        animator?.cancel()
        val start = bubblePosition
        val end = destination.ordinal.toFloat()
        if (animate && isAttachedToWindow) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                // Damped spring response, settled at the end; no continuously running animation.
                interpolator = Interpolator { t ->
                    if (t >= 1f) 1f else (1.0 - exp(-7.0 * t) * (cos(10.0 * t) + 0.7 * sin(10.0 * t))).toFloat()
                }
                addUpdateListener {
                    bubblePosition = start + (end - start) * (it.animatedValue as Float)
                    artwork.invalidate()
                }
                start()
            }
        } else {
            bubblePosition = end
            artwork.invalidate()
        }
        onDestinationSelected?.invoke(destination)
    }

    private fun updateAccessibility() {
        buttons.forEachIndexed { index, view -> view.isSelected = index == selectedDestination.ordinal }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshBackdrop()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        bubblePosition = selectedDestination.ordinal.toFloat()
        blurLayer.setImageDrawable(null)
        snapshot = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshBackdrop()
    }

    private inner class GlassArtwork(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val bounds = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0x6EFFFFFF, 0x293E4B68, 0x59414D69, 0x74303A56), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            paint.shader = null
            // Delicate prismatic rim and a brighter upper edge.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f)
            paint.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(0xAAFFFFFF.toInt(), 0x518BD4FF, 0x30FFFFFF, 0x66E9B9FF, 0x88FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(dp(.7f), dp(.7f), w - dp(.7f), h - dp(.7f), r, r, paint)
            paint.shader = null
            paint.strokeWidth = dp(.7f); paint.color = 0x7AFFFFFF
            canvas.drawArc(dp(9f), dp(3f), w - dp(9f), h - dp(3f), 205f, 130f, false, paint)

            val cell = w / 4f
            val cx = cell * (bubblePosition.coerceIn(0f, 3f) + .5f)
            val bw = min(cell - dp(8f), dp(82f)); val bh = h - dp(12f)
            bounds.set(cx - bw / 2, (h - bh) / 2, cx + bw / 2, (h + bh) / 2)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(0x88FFFFFF.toInt(), 0x497DB9E1, 0x45617DAD, 0x72FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(bounds, bh / 2, bh / 2, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f)
            paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(0xCCFFFFFF.toInt(), 0x44FFFFFF, 0x669BE0FF, 0x80FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(bounds, bh / 2, bh / 2, paint)
            paint.shader = null

            Destination.entries.forEachIndexed { index, dest ->
                val active = (1f - abs(bubblePosition - index)).coerceIn(0f, 1f)
                val centerX = cell * (index + .5f)
                val pressed = buttons.getOrNull(index)?.isPressed == true
                if (pressed) {
                    paint.style = Paint.Style.FILL; paint.color = 0x22FFFFFF
                    canvas.drawCircle(centerX, h / 2, dp(28f), paint)
                }
                val iconY = h * .40f
                canvas.save()
                canvas.translate(centerX, iconY)
                val scale = 1f + .10f * active
                canvas.scale(scale, scale)
                drawIcon(canvas, dest, active)
                canvas.restore()
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                paint.textSize = dp(9.5f)
                paint.textAlign = Paint.Align.CENTER
                paint.color = blend(0xBBDDE4F1.toInt(), Color.WHITE, active)
                canvas.drawText(dest.label, centerX, h * .79f, paint)
            }
        }

        private fun drawIcon(c: Canvas, destination: Destination, active: Float) {
            paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.8f); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            paint.color = blend(0xD4E2E8F4.toInt(), Color.WHITE, active)
            val d = density
            c.save(); c.scale(d, d)
            // Stroke widths are in the scaled coordinate system here.
            paint.strokeWidth = 1.8f
            path.reset()
            when (destination) {
                Destination.HOME -> {
                    path.moveTo(-9f, -1f); path.lineTo(0f, -8f); path.lineTo(9f, -1f)
                    path.moveTo(-7f, -2f); path.lineTo(-7f, 8f); path.lineTo(-2.5f, 8f)
                    path.lineTo(-2.5f, 3f); path.lineTo(2.5f, 3f); path.lineTo(2.5f, 8f); path.lineTo(7f, 8f); path.lineTo(7f, -2f)
                    c.drawPath(path, paint)
                }
                Destination.SEARCH -> { c.drawCircle(-1.5f, -1.5f, 6.5f, paint); c.drawLine(3.5f, 3.5f, 8.5f, 8.5f, paint) }
                Destination.NOTIFICATIONS -> {
                    path.moveTo(-8f, 5f); path.cubicTo(-6f, 3f, -6f, 1f, -6f, -2f)
                    path.cubicTo(-6f, -10f, 6f, -10f, 6f, -2f); path.cubicTo(6f, 1f, 6f, 3f, 8f, 5f)
                    path.close(); c.drawPath(path, paint); c.drawArc(-2.5f, 5f, 2.5f, 10f, 0f, 180f, false, paint)
                }
                Destination.PROFILE -> { c.drawCircle(0f, -4f, 4f, paint); c.drawArc(-8f, 1f, 8f, 13f, 190f, 160f, false, paint) }
            }
            c.restore()
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    // Separable box blur over a small, downsampled bitmap; no allocations per pixel.
    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width; val h = bitmap.height
        var src = IntArray(w * h); var dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        repeat(2) {
            for (y in 0 until h) for (x in 0 until w) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (k in -radius..radius) {
                    val color = src[y * w + (x + k).coerceIn(0, w - 1)]
                    a += Color.alpha(color); r += Color.red(color); g += Color.green(color); b += Color.blue(color)
                }
                val n = radius * 2 + 1
                dst[y * w + x] = Color.argb(a / n, r / n, g / n, b / n)
            }
            val temp = src; src = dst; dst = temp
            for (y in 0 until h) for (x in 0 until w) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (k in -radius..radius) {
                    val color = src[(y + k).coerceIn(0, h - 1) * w + x]
                    a += Color.alpha(color); r += Color.red(color); g += Color.green(color); b += Color.blue(color)
                }
                val n = radius * 2 + 1
                dst[y * w + x] = Color.argb(a / n, r / n, g / n, b / n)
            }
            val temp2 = src; src = dst; dst = temp2
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }
}
