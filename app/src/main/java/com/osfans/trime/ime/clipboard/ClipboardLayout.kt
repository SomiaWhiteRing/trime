/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class ClipboardLayout(
    context: Context,
    private val scope: ThemeScope,
    private val attachTabChooser: (View) -> Unit,
) : ConstraintLayout(context) {

    val viewPager = view(::ViewPager2) {}

    val titleUi = ClipboardTitleUi(context, scope)
    private var tabChooser: View? = null
    internal val isShowingTabChooser get() = tabChooser != null
    private var settings: View? = null
    private var segments: View? = null
    internal val isShowingSegments get() = segments != null

    init {
        add(
            viewPager,
            lParams {
                centerInParent()
            },
        )
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dismissTabChooser()
                dismissSegments()
            }
        })
    }

    internal fun dismissTabChooser() {
        val chooser = tabChooser ?: return
        tabChooser = null
        (chooser.parent as? ViewGroup)?.removeView(chooser)
    }

    internal fun showSegments(content: View) {
        dismissTabChooser()
        dismissSegments()
        segments = content
        add(content, lParams { centerInParent() })
    }

    internal fun dismissSegments(): Boolean {
        val content = segments ?: return false
        removeView(content)
        segments = null
        return true
    }

    fun showSettings(content: View) {
        dismissTabChooser()
        settings = content
        titleUi.setSettingsOpen(true)
        add(content, lParams { centerInParent() })
    }

    fun dismissSettings(): Boolean {
        val content = settings ?: return false
        removeView(content)
        settings = null
        titleUi.setSettingsOpen(false)
        return true
    }

    private fun chooserBackground(): Drawable {
        val left = scope.drawable("clipboard-chooser-corner-left.png")
        val right = scope.drawable("clipboard-chooser-corner-right.png")
        val color = 0xFF434343.toInt()
        val radius = context.dp(20)
        if (left == null || right == null) {
            return GradientDrawable().apply {
                val r = radius.toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                setColor(color)
            }
        }
        return LayerDrawable(arrayOf(ColorDrawable(color), ColorDrawable(color), left, right)).apply {
            setLayerInset(0, 0, 0, 0, radius)
            setLayerInset(1, radius, 0, radius, 0)
            setLayerSize(2, radius, radius)
            setLayerSize(3, radius, radius)
            setLayerGravity(2, Gravity.LEFT or Gravity.BOTTOM)
            setLayerGravity(3, Gravity.RIGHT or Gravity.BOTTOM)
        }
    }

    fun toggleTabChooser(labels: List<Int>) {
        if (tabChooser != null) {
            dismissTabChooser()
            return
        }
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x30000000)
            setOnClickListener { dismissTabChooser() }
        }
        val tabs = FlexboxLayout(context).apply {
            flexWrap = FlexWrap.WRAP
            isClickable = true
            background = chooserBackground()
            setPadding(dp(13), dp(13f + 2f / 3).roundToInt(), dp(13), dp(13))
        }
        labels.forEachIndexed { index, label ->
            val selected = index == viewPager.currentItem
            val artworkName = when (context.getText(label).toString()) {
                "剪贴板" -> "history"
                "常用语" -> "phrases"
                "推荐" -> "recommend"
                else -> null
            }
            val state = if (selected) "active" else "idle"
            val artwork = artworkName?.let { scope.drawable("clipboard-chooser-$it-$state.png") }
            val tab = TextView(context).apply {
                setText(label)
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(scope.theme.fonts.candidate, if (selected) Typeface.BOLD else Typeface.NORMAL)
                val accent = 0xFF9D1E42.toInt()
                setTextColor(if (selected) accent else 0xFFB3B3B3.toInt())
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1f / 3).roundToInt().coerceAtLeast(1), if (selected) accent else 0xB8959697.toInt())
                }
                if (artwork != null) {
                    foreground = artwork
                    background = null
                    setTextColor(Color.TRANSPARENT)
                }
                setOnClickListener {
                    dismissTabChooser()
                    viewPager.setCurrentItem(index, false)
                }
            }
            val width = if (artwork == null) wrapContent else dp(if (artworkName == "recommend") 52 else 66)
            val height = if (artwork == null) wrapContent else dp(27)
            tabs.addView(tab, FlexboxLayout.LayoutParams(width, height).apply {
                setMargins(dp(3), 0, dp(3), dp(6))
            })
        }
        overlay.addView(tabs, FrameLayout.LayoutParams(matchParent, wrapContent))
        overlay.addView(View(context).apply {
            setBackgroundColor(0xFF3C3C3C.toInt())
        }, FrameLayout.LayoutParams(matchParent, dp(1f / 3).roundToInt().coerceAtLeast(1)))
        tabChooser = overlay
        attachTabChooser(overlay)
    }
}
