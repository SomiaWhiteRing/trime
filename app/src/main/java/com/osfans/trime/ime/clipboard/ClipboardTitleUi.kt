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
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.core.InputTabLayout
import splitties.dimensions.dp
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

    val backButton = if (cherry) cherryButton("clipboard-title-back.png") else null
    val deleteAllButton = if (cherry) {
        cherryButton("clipboard-title-menu.png")
    } else {
        ToolButton(ctx, R.drawable.ic_baseline_delete_sweep_24, scope)
    }

    private fun cherryButton(image: String) = ToolButton(
        ctx,
        ToolBar.Button(foreground = ToolBar.Button.Foreground(style = image, padding = 0)),
        scope,
    )

    private val size = theme.style.run { candidateViewHeight + commentHeight }

    override val root = constraintLayout {
        if (cherry) {
            clipChildren = false
            setBackgroundColor(0xFF434343.toInt())
            add(
                View(ctx).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFF4F4F4F.toInt(), 0xFF353535.toInt()),
                    )
                },
                lParams(matchParent, dp(1)) {
                    topOfParent(dp(37) - 1)
                },
            )
        }
        add(
            tabLayout,
            lParams(wrapContent, dp(size)) {
                startOfParent()
                centerVertically()
            },
        )
        backButton?.let {
            add(it, lParams(dp(37), dp(37)) {
                startOfParent()
                centerVertically()
            })
        }
        add(
            deleteAllButton,
            lParams(dp(size), dp(size)) {
                centerVertically()
                endOfParent()
            },
        )
    }
}


internal class CherryClipboardTabBackground(
    private val curveWidth: Float,
    private val divider: Float,
    private val overhang: Float,
) : Drawable() {
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left - overhang
        val right = bounds.right + overhang
        val top = bounds.top + divider
        val bottom = bounds.bottom - divider
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
        val inset = curveWidth.coerceAtMost(bounds.width() / 2f)
        val top = bounds.top + divider
        paint.strokeWidth = divider * 1.5f
        canvas.drawLine(bounds.left - overhang + inset, top, bounds.right + overhang - inset, top, paint)

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
