/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ime.clipboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.osfans.trime.R
import splitties.dimensions.dp

internal fun showCherryDeleteConfirmation(anchor: ClipboardLayout, confirm: () -> Unit): PopupWindow {
    val context = anchor.context
    val shade = FrameLayout(context).apply { setBackgroundColor(0x99000000.toInt()) }
    val popup = PopupWindow(shade, anchor.width, anchor.height + anchor.titleUi.root.height, true).apply {
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        animationStyle = 0
    }
    shade.setOnClickListener { popup.dismiss() }
    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        setPadding(context.dp(24), context.dp(28), context.dp(24), context.dp(18))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(16f)
            setColor(0xFF454547.toInt())
        }
        addView(TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setText(R.string.cherry_clipboard_confirm_delete)
            setTextColor(0xFFB3B3B3.toInt())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    val buttons = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
    for (positive in listOf(false, true)) {
        val button = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setText(if (positive) R.string.ok else R.string.cancel)
            setTextColor(if (positive) 0xFFE5E5E5.toInt() else 0xFFB3B3B3.toInt())
            background = GradientDrawable().apply {
                cornerRadius = context.dp(19f)
                setColor(if (positive) 0xFF9D1E42.toInt() else 0x334F4F4F)
            }
            setOnClickListener {
                popup.dismiss()
                if (positive) confirm()
            }
        }
        buttons.addView(button, LinearLayout.LayoutParams(context.dp(77), context.dp(44)).apply {
            if (positive) marginStart = context.dp(12)
        })
    }
    card.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(44)).apply {
        topMargin = context.dp(24)
    })
    shade.addView(card, FrameLayout.LayoutParams(
        minOf(context.dp(300), anchor.width - context.dp(40)),
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER,
    ))
    popup.showAtLocation(anchor, Gravity.BOTTOM, 0, 0)
    return popup
}
