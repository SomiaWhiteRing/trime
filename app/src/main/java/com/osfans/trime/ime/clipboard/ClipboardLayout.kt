/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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

@SuppressLint("ViewConstructor")
class ClipboardLayout(context: Context, scope: ThemeScope) : ConstraintLayout(context) {

    val viewPager = view(::ViewPager2) {}

    val titleUi = ClipboardTitleUi(context, scope)
    private var tabChooser: FrameLayout? = null
    private var settings: View? = null

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
            }
        })
    }

    private fun dismissTabChooser() {
        tabChooser?.let(::removeView)
        tabChooser = null
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
            setPadding(dp(13), dp(13), dp(13), dp(13))
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
                    setStroke(dp(1), if (selected) accent else 0xB8959697.toInt())
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
        tabChooser = overlay
        add(overlay, lParams { centerInParent() })
    }
}
