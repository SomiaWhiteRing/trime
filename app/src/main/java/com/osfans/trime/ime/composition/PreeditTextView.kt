/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.math.MathUtils
import splitties.dimensions.dp

@SuppressLint("AppCompatCustomView")
class PreeditTextView
@JvmOverloads
constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : TextView(context, attributeSet) {
    var onMoveCursor: ((Int) -> Unit)? = null
    var showPreeditCaret = false
        set(value) {
            field = value
            invalidate()
        }
    var preeditCaret = 0
        set(value) {
            field = value
            if (showPreeditCaret) invalidate()
        }
    private val caretPaint = Paint().apply {
        color = 0xFFB3B3B3.toInt()
        strokeWidth = context.dp(2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showPreeditCaret) {
            val x = layout.getPrimaryHorizontal(preeditCaret.coerceIn(0, text.length)) + totalPaddingLeft
            canvas.drawLine(x, context.dp(3f), x, height - context.dp(3f), caretPaint)
        }
    }
    private var lastTapOffset = -1
    private var newCursorPos = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val textString = text.toString()
                lastTapOffset = MathUtils.clamp(
                    getOffsetForPosition(x, y),
                    0,
                    textString.length,
                )
                val bytes = textString.substring(0, lastTapOffset).toByteArray()
                newCursorPos = bytes.size
                return true
            }

            MotionEvent.ACTION_UP -> {
                onMoveCursor?.invoke(newCursorPos)
                lastTapOffset = -1
                newCursorPos = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                lastTapOffset = -1
                newCursorPos = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
