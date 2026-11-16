/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
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
class ClipboardLayout(context: Context, scope: ThemeScope) : ConstraintLayout(context) {

    val viewPager = view(::ViewPager2) {}

    val titleUi = ClipboardTitleUi(context, scope)
    private var tabChooser: PopupWindow? = null
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

    internal fun dismissTabChooser() = tabChooser?.dismiss()

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

    fun toggleTabChooser(labels: List<Int>) {
        if (tabChooser != null) {
            dismissTabChooser()
            return
        }
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { dismissTabChooser() }
        }
        val tabs = FlexboxLayout(context).apply {
            flexWrap = FlexWrap.WRAP
            setPadding(dp(13), dp(13f + 2f / 3).roundToInt(), dp(13), dp(12f + 2f / 3).roundToInt())
            isClickable = true
            background = GradientDrawable().apply {
                val radius = dp(20).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
                setColor(0xFF454547.toInt())
            }
        }
        labels.forEachIndexed { index, label ->
            val selected = index == viewPager.currentItem
            val tab = TextView(context).apply {
                setText(label)
                textSize = 14f
                gravity = Gravity.CENTER
                paint.isFakeBoldText = selected
                val accent = 0xFF9D1E42.toInt()
                setTextColor(if (selected) accent else 0xFFB3B3B3.toInt())
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1f / 3).roundToInt().coerceAtLeast(1), if (selected) accent else 0xB8959697.toInt())
                }
                setOnClickListener {
                    dismissTabChooser()
                    viewPager.setCurrentItem(index, false)
                }
            }
            tabs.addView(tab, FlexboxLayout.LayoutParams(wrapContent, wrapContent).apply {
                setMargins(dp(3), 0, dp(3), dp(6))
            })
        }
        overlay.addView(tabs, FrameLayout.LayoutParams(matchParent, wrapContent))
        val title = titleUi.root
        tabChooser = PopupWindow(overlay, width, height + title.height, true).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            animationStyle = 0
            setOnDismissListener { tabChooser = null }
            showAsDropDown(title, 0, -title.height, Gravity.START)
        }
    }
}
