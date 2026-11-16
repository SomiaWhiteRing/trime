/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.core.InputTabLayout
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import kotlin.math.roundToInt

class ClipboardTitleUi(override val ctx: Context, private val scope: ThemeScope) : Ui {
    private val theme: Theme get() = scope.theme

    private val cherry = "cherry_edit" in theme.presetKeyboards

    val tabLayout = InputTabLayout(ctx).apply {
        if (cherry) {
            setPadding(dp(37), 0, 0, 0)
            horizontalTabMarginDp = 2
            clipChildren = false
            clipToPadding = false
        }
    }

    private val size = theme.style.run { candidateViewHeight + commentHeight }
    val backButton = if (cherry) cherryButton("clipboard-title-back.png") else null
    val actionButton = if (cherry) {
        cherryButton("clipboard-title-menu.png").apply {
            // The menu bitmap includes a narrow divider. Draw the shadow across the title itself.
            clipBounds = Rect(dp(3), 0, dp(size), dp(size))
        }
    } else {
        ToolButton(ctx, R.drawable.ic_baseline_delete_sweep_24, scope)
    }

    private fun cherryButton(image: String) = ToolButton(
        ctx,
        ToolBar.Button(foreground = ToolBar.Button.Foreground(style = image, padding = 0)),
        scope,
    ).apply {
        translationY = dp(1f / 3f)
    }

    private val menuDivider = View(ctx).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00000000, 0x11000000, 0x18000000, 0x1E000000, 0x24000000),
        ).apply {
            val radius = dp(1).toFloat()
            cornerRadii = floatArrayOf(0f, 0f, radius, radius, 0f, 0f, 0f, 0f)
        }
    }
    private val settingsTitle = TextView(ctx).apply {
        setText(R.string.cherry_clipboard_settings)
        textSize = 16f
        setTextColor(0xFFB3B3B3.toInt())
        visibility = View.GONE
    }

    fun setSettingsOpen(open: Boolean) {
        tabLayout.visibility = if (open) View.GONE else View.VISIBLE
        actionButton.visibility = if (open) View.GONE else View.VISIBLE
        menuDivider.visibility = actionButton.visibility
        settingsTitle.visibility = if (open) View.VISIBLE else View.GONE
    }

    override val root = constraintLayout {
        if (cherry) {
            clipChildren = false
            setBackgroundColor(0xFF353535.toInt())
            val stroke = dp(1f / 3).roundToInt().coerceAtLeast(1)
            val divider = dp(37) - stroke
            add(View(ctx).apply { setBackgroundColor(0xFF434343.toInt()) }, lParams(matchParent, divider) {
                topOfParent()
            })
            listOf(0xFF4F4F4F.toInt(), 0xFF3B3B3B.toInt()).forEachIndexed { index, color ->
                add(View(ctx).apply { setBackgroundColor(color) }, lParams(matchParent, stroke) {
                    topOfParent(divider + index * stroke)
                })
            }
        }
        add(
            tabLayout,
            lParams(wrapContent, dp(size)) {
                startOfParent()
                centerVertically()
            },
        )
        if (cherry) {
            add(menuDivider, lParams(dp(10f / 3).roundToInt(), dp(34)) {
                endOfParent(dp(size) - dp(8f / 3).roundToInt())
                topOfParent(dp(10f / 3).roundToInt())
            })
            add(settingsTitle, lParams(wrapContent, wrapContent) {
                centerHorizontally()
                centerVertically()
            })
        }
        backButton?.let {
            add(it, lParams(dp(37), dp(37)) {
                startOfParent()
                centerVertically()
            })
        }
        add(
            actionButton,
            lParams(dp(size), dp(size)) {
                centerVertically()
                endOfParent()
            },
        )
    }
}


internal class CherryClipboardTabBackground(context: Context) : Drawable() {
    private val curveWidth = context.dp(15f)
    private val divider = context.dp(0.5f)
    private val overhang = context.dp(8f)
    private val leftExtension = context.dp(1f / 3f)
    private val rightExtension = context.dp(4f / 3f)
    private val topInset = context.dp(0.4f)
    private val bottomInset = context.dp(1f / 6f)
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left - overhang - leftExtension
        val right = bounds.right + overhang + rightExtension
        val top = bounds.top + divider + topInset
        val bottom = bounds.bottom - divider - bottomInset
        val inset = curveWidth.coerceAtMost(bounds.width() / 2f)
        path.reset()
        path.moveTo(left, bottom)
        path.cubicTo(left + inset, bottom, left, top, left + inset, top)
        path.lineTo(right - inset, top)
        path.cubicTo(right, top, right - inset, bottom, right, bottom)
    }

    override fun draw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF353535.toInt()
        paint.alpha = opacity
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.color = 0xFF727272.toInt()
        paint.alpha = opacity
        paint.strokeWidth = divider
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
