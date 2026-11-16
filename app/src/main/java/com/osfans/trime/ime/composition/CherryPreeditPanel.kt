/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp

@SuppressLint("ViewConstructor")
internal class CherryPreeditPanel(
    context: Context,
    scope: ThemeScope,
    private val preeditUi: PreeditUi,
    private val onResize: () -> Unit,
    moveCursor: (Boolean) -> Unit,
) : FrameLayout(context) {
    private var expanded = false
    private val barColor = 0xFF4D4D4D.toInt()
    private val glyphColor = 0xFFB3B3B3.toInt()
    private val text = preeditUi.preedit
    private val bar = View(context).apply { setBackgroundColor(barColor) }
    private val pencil = icon(scope, "cherry-preedit-pencil.png", R.drawable.ic_baseline_edit_24, "编辑拼音") {
        open()
    }
    private val left = icon(scope, "cherry-preedit-left.png", R.drawable.ic_baseline_arrow_left_24, "光标左移") {
        moveCursor(false)
    }
    private val right = icon(scope, "cherry-preedit-right.png", R.drawable.ic_baseline_arrow_right_24, "光标右移") {
        moveCursor(true)
    }
    private val collapse = object : View(context) {
        private val shape = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            shape.reset()
            shape.moveTo(0f, height.toFloat())
            shape.cubicTo(context.dp(5f), height.toFloat(), context.dp(1f), 0f, context.dp(17f), 0f)
            shape.lineTo(width.toFloat(), 0f)
            shape.lineTo(width.toFloat(), height.toFloat())
            shape.close()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = 0xFF717171.toInt()
            canvas.drawPath(shape, paint)
            val x = width - context.dp(10.5f)
            val y = height / 2f
            paint.color = glyphColor
            canvas.drawCircle(x, y, context.dp(4.6f), paint)
            paint.color = 0xFF717171.toInt()
            paint.strokeWidth = context.dp(1.4f)
            canvas.drawLine(x - context.dp(2.6f), y, x + context.dp(2.6f), y, paint)
        }
    }.apply {
        contentDescription = "收起"
        setOnClickListener { close() }
    }

    init {
        clipChildren = false
        addView(bar, LayoutParams(MATCH_PARENT, context.dp(32), Gravity.BOTTOM))
        addView(preeditUi.root, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            topMargin = context.dp(3)
            marginEnd = context.dp(4)
        })
        addView(pencil, LayoutParams(context.dp(9), context.dp(9), Gravity.END or Gravity.TOP))
        addView(left, LayoutParams(context.dp(34), context.dp(32), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = context.dp(36)
        })
        addView(right, LayoutParams(context.dp(34), context.dp(32), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = context.dp(2)
        })
        addView(collapse, LayoutParams(context.dp(27), context.dp(18.67f).toInt(), Gravity.END or Gravity.TOP))
        showControls()
    }

    fun open() {
        if (!expanded) setExpanded(true)
    }

    fun close() {
        if (expanded) setExpanded(false)
    }

    fun updateCursor() {
        left.isEnabled = text.preeditCaret > 0
        right.isEnabled = text.preeditCaret < text.length()
        left.alpha = if (left.isEnabled) 1f else 0.35f
        right.alpha = if (right.isEnabled) 1f else 0.35f
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        layoutParams = layoutParams.apply {
            width = if (expanded) MATCH_PARENT else WRAP_CONTENT
        }
        preeditUi.root.layoutParams = (preeditUi.root.layoutParams as LayoutParams).apply {
            width = if (expanded) MATCH_PARENT else WRAP_CONTENT
            topMargin = if (expanded) context.dp(18.67f).toInt() else context.dp(3)
            marginEnd = context.dp(if (expanded) 70 else 4)
        }
        text.layoutParams = text.layoutParams.apply { width = if (expanded) MATCH_PARENT else WRAP_CONTENT }
        text.textSize = if (expanded) 20f else 16f
        text.setHeight(context.dp(if (expanded) 32 else 21))
        text.setPadding(context.dp(if (expanded) 3 else 6), 0, context.dp(if (expanded) 3 else 10), 0)
        text.showPreeditCaret = expanded
        showControls()
        updateCursor()
        onResize()
    }

    private fun showControls() {
        pencil.visibility = if (expanded) GONE else VISIBLE
        left.visibility = if (expanded) VISIBLE else GONE
        right.visibility = left.visibility
        collapse.visibility = left.visibility
        bar.visibility = left.visibility
    }

    private fun icon(scope: ThemeScope, name: String, fallback: Int, label: String, click: () -> Unit) =
        ImageButton(context).apply {
            val original = scope.drawable(name)
            if (original == null) setImageResource(fallback) else setImageDrawable(original)
            imageTintList = ColorStateList.valueOf(glyphColor)
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = label
            setOnClickListener { click() }
        }

    private companion object {
        const val MATCH_PARENT = LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = LayoutParams.WRAP_CONTENT
    }
}
