/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewOutlineProvider
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class SegmentUi(override val ctx: Context, private val scope: ThemeScope, private val cherryPopup: Boolean) : Ui {
    private val theme: Theme get() = scope.theme

    private val spacing = ctx.dp(4)

    val textView =
        textView {
            textSize = 16f
            isSingleLine = true
            typeface = theme.fonts.key
            if (cherryPopup) {
                setPaddingDp(8, 5, 8, 5)
                setHeight(ctx.dp(32))
                gravity = gravityCenter
            } else {
                setPaddingDp(8, 4, 8, 4)
            }
            setTextColor(textColorStates())
        }

    private fun textColorStates(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_selected),
        ),
        intArrayOf(
            if (cherryPopup) 0xFFB3B3B3.toInt() else scope.colors.keyTextColor,
            if (cherryPopup) 0xFF353535.toInt() else scope.colors.hilitedKeyTextColor,
        ),
    )

    private fun rootBackground(): StateListDrawable = StateListDrawable().apply {
        if (cherryPopup) {
            addState(intArrayOf(android.R.attr.state_selected), GradientDrawable().apply {
                cornerRadius = ctx.dp(4f)
                setColor(0xFF9D1E42.toInt())
            })
            addState(intArrayOf(), GradientDrawable().apply {
                cornerRadius = ctx.dp(4f)
                setColor(0xFF434343.toInt())
                setStroke(ctx.dp(0.3f).toInt().coerceAtLeast(1), 0x4DB3B3B3)
            })
            return@apply
        }
        addState(
            intArrayOf(-android.R.attr.state_selected),
            scope.decorDrawable(
                "key_back_color",
                cornerRadius = ctx.dp(theme.style.roundCorner),
            ),
        )
        addState(
            intArrayOf(android.R.attr.state_selected),
            scope.decorDrawable(
                "hilited_key_back_color",
                cornerRadius = ctx.dp(theme.style.roundCorner),
            ),
        )
    }

    override val root = GestureFrame(ctx).apply {
        isClickable = true
        background = rootBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent).apply {
            setMargins(spacing, spacing, spacing, spacing)
        }
        add(
            textView,
            lParams(wrapContent, wrapContent) {
                gravity = gravityCenter
            },
        )
    }

    /** Restyles the row after a scheme switch. */
    fun refreshColors() {
        textView.setTextColor(textColorStates())
        root.background = rootBackground()
    }

    fun update(isSelected: Boolean) {
        root.isSelected = isSelected
        textView.isSelected = isSelected
    }
}
