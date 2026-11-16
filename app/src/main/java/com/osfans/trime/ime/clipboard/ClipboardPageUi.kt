/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
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

class ClipboardPageUi(override val ctx: Context, cherry: Boolean = false) : Ui {
    private val spacing = SpacesItemDecoration(if (cherry) ctx.dp(8f / 3f).toInt() else ctx.dp(4), if (cherry) 0 else ctx.dp(4))
    private val divider by lazy {
        DividerItemDecoration(ctx, DividerItemDecoration.VERTICAL).apply {
            setDrawable(GradientDrawable().apply {
                setColor(0xFF595959.toInt())
                setSize(1, ctx.dp(1f / 3).toInt().coerceAtLeast(1))
            })
        }
    }
    val recyclerView = recyclerView {
        if (cherry) setPadding(dp(8), dp(2), dp(8), dp(2))
        addItemDecoration(spacing)
    }

    fun setCompact(compact: Boolean) = recyclerView.apply {
        removeItemDecorationAt(0)
        addItemDecoration(if (compact) divider else spacing)
    }

    override val root = view(::FrameLayout) {
        add(recyclerView, lParams(matchParent, matchParent))
    }
}
