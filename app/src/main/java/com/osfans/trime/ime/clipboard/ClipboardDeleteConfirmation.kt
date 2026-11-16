/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ime.clipboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeScope
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

internal fun showCherryPhraseDeleteConfirmation(
    anchor: ClipboardLayout,
    scope: ThemeScope,
    confirm: () -> Unit,
): PopupWindow {
    val context = anchor.context
    val shade = FrameLayout(context).apply { setBackgroundColor(0x99000000.toInt()) }
    val popup = PopupWindow(shade, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, false).apply {
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isClippingEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setIsLaidOutInScreen(true)
        animationStyle = 0
    }
    shade.setOnClickListener { popup.dismiss() }
    val artwork = scope.drawable("clipboard-phrase-delete-dialog.png")
    val card = FrameLayout(context).apply {
        isClickable = true
        background = artwork ?: GradientDrawable().apply {
            cornerRadius = context.dp(16f)
            setColor(0xFF363636.toInt())
        }
        addView(TextView(context).apply {
            setText(R.string.cherry_clipboard_confirm_delete)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (artwork == null) 0xFFB3B3B3.toInt() else Color.TRANSPARENT)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(60)).apply {
            topMargin = context.dp(10)
        })
        for ((index, label) in listOf(R.string.cancel, R.string.cherry_phrase_confirm).withIndex()) {
            addView(TextView(context).apply {
                setText(label)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(when {
                    artwork != null -> Color.TRANSPARENT
                    index == 0 -> 0xFFB3B3B3.toInt()
                    else -> Color.WHITE
                })
                if (artwork == null) background = GradientDrawable().apply {
                    cornerRadius = context.dp(22f)
                    if (index == 0) setStroke(context.dp(1), 0xFF656565.toInt()) else setColor(0xFFA21E42.toInt())
                }
                setOnClickListener {
                    popup.dismiss()
                    if (index == 1) confirm()
                }
            }, FrameLayout.LayoutParams(context.dp(120), context.dp(44), Gravity.BOTTOM or if (index == 0) Gravity.LEFT else Gravity.RIGHT).apply {
                leftMargin = context.dp(24)
                rightMargin = context.dp(24)
                bottomMargin = context.dp(20)
            })
        }
    }
    shade.addView(card, FrameLayout.LayoutParams(
        minOf(context.dp(300), context.resources.displayMetrics.widthPixels - context.dp(32)),
        context.dp(138),
        Gravity.CENTER,
    ))
    popup.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, 0, 0)
    return popup
}
