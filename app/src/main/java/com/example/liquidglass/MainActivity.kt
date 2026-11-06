package com.example.liquidglass

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + .5f).toInt()
    private lateinit var bar: LiquidGlassNavBar
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var topInset = 0
    private var bottomInset = 0

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val root = FrameLayout(this)
        // The backdrop is a sibling of the bar, never its parent. This prevents recursive capture.
        val backdrop = FrameLayout(this)
        backdrop.addView(AtmosphereView(), FrameLayout.LayoutParams(-1, -1))
        scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        backdrop.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        bar = LiquidGlassNavBar(this)
        val barParams = FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM).apply {
            leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(20)
        }
        root.addView(bar, barParams)
        setContentView(root)

        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottomInset = insets.systemWindowInsetBottom
            scroll.setPadding(dp(26), topInset + dp(22), dp(26), bottomInset + dp(120))
            barParams.bottomMargin = bottomInset + dp(18)
            bar.layoutParams = barParams
            bar.refreshBackdrop()
            insets
        }
        bar.setBackdropView(backdrop)
        bar.onDestinationSelected = { destination -> showDestination(destination) }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> bar.refreshBackdrop() }
        val saved = savedInstanceState?.getString("destination")
        val destination = LiquidGlassNavBar.Destination.entries.firstOrNull { it.name == saved }
            ?: LiquidGlassNavBar.Destination.HOME
        if (destination == LiquidGlassNavBar.Destination.HOME) showDestination(destination)
        else bar.select(destination, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("destination", bar.selectedDestination.name)
        super.onSaveInstanceState(outState)
    }

    private fun showDestination(destination: LiquidGlassNavBar.Destination) {
        content.removeAllViews()
        val eyebrow = when (destination) {
            LiquidGlassNavBar.Destination.HOME -> "YOUR SPACE, REIMAGINED"
            LiquidGlassNavBar.Destination.SEARCH -> "FIND SOMETHING NEW"
            LiquidGlassNavBar.Destination.NOTIFICATIONS -> "YOU'RE ALL CAUGHT UP"
            LiquidGlassNavBar.Destination.PROFILE -> "A LITTLE MORE YOU"
        }
        val description = when (destination) {
            LiquidGlassNavBar.Destination.HOME -> "A quieter place for the things that matter."
            LiquidGlassNavBar.Destination.SEARCH -> "Discover ideas and inspiration, all in one place."
            LiquidGlassNavBar.Destination.NOTIFICATIONS -> "The latest updates will find their way here."
            LiquidGlassNavBar.Destination.PROFILE -> "Your personal space, just the way you like it."
        }
        val cardTitle = when (destination) {
            LiquidGlassNavBar.Destination.HOME -> "Make room\nfor wonder."
            LiquidGlassNavBar.Destination.SEARCH -> "A world of\npossibilities."
            LiquidGlassNavBar.Destination.NOTIFICATIONS -> "Enjoy the\nquiet moments."
            LiquidGlassNavBar.Destination.PROFILE -> "Made for\nyour moments."
        }
        val cardFoot = when (destination) {
            LiquidGlassNavBar.Destination.HOME -> "A fresh perspective for your day"
            LiquidGlassNavBar.Destination.SEARCH -> "Your next discovery starts here"
            LiquidGlassNavBar.Destination.NOTIFICATIONS -> "Nothing new right now"
            LiquidGlassNavBar.Destination.PROFILE -> "Your space is ready"
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("LUMEN", 12f, 0xFFF4F6FF.toInt(), true).apply { letterSpacing = .28f }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("✦", 20f, 0xFFDAE9FF.toInt(), false))
        content.addView(top)
        space(50)
        content.addView(label(eyebrow, 10f, 0xFFB6C6E5.toInt(), true).apply { letterSpacing = .15f })
        space(12)
        content.addView(label(destination.label, 42f, Color.WHITE, false).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -.035f
        })
        space(8)
        content.addView(label(description, 15f, 0xFFB6C1D8.toInt(), false).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        space(32)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x966F90BD.toInt(), 0x82505C90.toInt(), 0xA13D3C65.toInt())).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0x55FFFFFF)
            }
        }
        card.addView(label("✧     THE DAILY EDIT", 10f, 0xFFD8E6FF.toInt(), true).apply { letterSpacing = .12f })
        card.addView(View(this), LinearLayout.LayoutParams(1, dp(68)))
        card.addView(label(cardTitle, 32f, Color.WHITE, false).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -.025f
        })
        card.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
        card.addView(label(cardFoot, 13f, 0xFFD0D9EB.toInt(), false))
        content.addView(card, LinearLayout.LayoutParams(-1, -2))
        space(30)
        val section = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        section.addView(label("A moment for you", 18f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        section.addView(label("✳", 20f, 0xFFB6CFFF.toInt(), false))
        content.addView(section)
        space(10)
        content.addView(label("Slow down, take a breath, and explore at your own pace.", 14f, 0xFFADBBD3.toInt(), false).apply { setLineSpacing(dp(4).toFloat(), 1f) })
        scroll.scrollTo(0, 0)
        content.post { bar.refreshBackdrop() }
    }

    private fun label(text: String, size: Float, color: Int, medium: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun space(height: Int) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }

    private inner class AtmosphereView : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            paint.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(0xFF101A31.toInt(), 0xFF1B2342.toInt(), 0xFF171A35.toInt(), 0xFF080E1E.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = RadialGradient(w * .9f, h * .30f, w * .85f, 0x886A94C4.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = RadialGradient(w * .05f, h * .75f, w * .8f, 0x775E5597, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = RadialGradient(w * .88f, h * .98f, w * .65f, 0x885078A1.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
    }
}
