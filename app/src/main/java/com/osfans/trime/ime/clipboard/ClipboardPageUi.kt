/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.widget.FrameLayout
import androidx.recyclerview.widget.DividerItemDecoration
import com.osfans.trime.ime.symbol.SpacesItemDecoration
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.roundToInt

class ClipboardPageUi(override val ctx: Context, private val cherry: Boolean = false) : Ui {
    private var compact = false
    private val spacing = SpacesItemDecoration(if (cherry) ctx.dp(8f / 3f).toInt() else ctx.dp(4), if (cherry) 0 else ctx.dp(4))
    private val divider by lazy {
        DividerItemDecoration(ctx, DividerItemDecoration.VERTICAL).apply {
            setDrawable(GradientDrawable().apply {
                setColor(0xFF232324.toInt())
                setSize(1, ctx.dp(0.26f).roundToInt().coerceAtLeast(1))
            })
        }
    }
    private val compactBackground by lazy {
        InsetDrawable(GradientDrawable().apply {
            setColor(0xFF454547.toInt())
            cornerRadius = ctx.dp(4f)
        }, ctx.dp(8), ctx.dp(2), ctx.dp(8), ctx.dp(2))
    }
    val recyclerView = recyclerView {
        if (cherry) setPadding(dp(8), dp(2), dp(8), dp(2))
        addItemDecoration(spacing)
    }

    fun setCompact(compact: Boolean) {
        if (this.compact == compact) return
        this.compact = compact
        recyclerView.removeItemDecorationAt(0)
        recyclerView.addItemDecoration(if (compact) divider else spacing)
        refreshCompactBackground()
    }

    fun refreshCompactBackground() {
        val background = if (compact && (recyclerView.adapter?.itemCount ?: 0) > 0) compactBackground else null
        if (recyclerView.background === background) return
        recyclerView.background = background
        if (cherry) recyclerView.setPadding(ctx.dp(8), ctx.dp(2), ctx.dp(8), ctx.dp(2))
    }

    override val root = view(::FrameLayout) {
        add(recyclerView, lParams(matchParent, matchParent))
    }
}
