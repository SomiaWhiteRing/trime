/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.osfans.trime.R
import splitties.dimensions.dp
import kotlin.math.roundToInt

internal fun createCherrySendModePanel(
    context: Context,
    automatic: Boolean,
    close: () -> Unit,
    select: (Boolean) -> Unit,
): View = LinearLayout(context).apply {
    val surface = 0xFF353535.toInt()
    val labelColor = 0xFFB3B3B3.toInt()
    val accent = 0xFFA21E42.toInt()
    val radius = context.dp(20f)
    orientation = LinearLayout.VERTICAL
    isClickable = true
    background = GradientDrawable().apply {
        setColor(surface)
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    }

    addView(FrameLayout(context).apply {
        addView(TextView(context).apply {
            setText(R.string.cherry_send_title)
            textSize = 16f
            setTextColor(labelColor)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, context.dp(5))
            ViewCompat.setAccessibilityHeading(this, true)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = "×"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(labelColor)
            contentDescription = context.getString(R.string.cherry_send_close)
            setOnClickListener { close() }
        }, FrameLayout.LayoutParams(context.dp(38), context.dp(34), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = context.dp(10)
        })
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38f + 1f / 3).roundToInt()))

    listOf(
        Triple(false, R.string.cherry_send_manual_label, R.string.cherry_send_manual_detail),
        Triple(true, R.string.cherry_send_automatic_label, R.string.cherry_send_automatic_detail),
    ).forEach { (mode, title, detail) ->
        addView(FrameLayout(context).apply {
            isSelected = mode == automatic
            setOnClickListener { select(mode) }
            addView(TextView(context).apply {
                setText(title)
                textSize = 16f
                setTextColor(labelColor)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(20)
                topMargin = context.dp(14)
            })
            addView(TextView(context).apply {
                setText(detail)
                textSize = 12f
                setTextColor(0xFF808080.toInt())
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(20)
                marginEnd = context.dp(32)
                topMargin = context.dp(41)
            })
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(surface)
                    setStroke(context.dp(if (mode == automatic) 4 else 1), if (mode == automatic) accent else labelColor)
                }
            }, FrameLayout.LayoutParams(context.dp(18), context.dp(18), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = context.dp(20)
            })
            addView(View(context).apply {
                setBackgroundColor(0xFF434343.toInt())
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM).apply {
                marginStart = context.dp(20)
                marginEnd = context.dp(20)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(70f + 1f / 3).roundToInt()))
    }
}
