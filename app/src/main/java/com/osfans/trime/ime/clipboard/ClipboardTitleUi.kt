/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
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
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent

class ClipboardTitleUi(override val ctx: Context, private val scope: ThemeScope) : Ui {
    private val theme: Theme get() = scope.theme

    private val cherry = "cherry_edit" in theme.presetKeyboards

    val tabLayout = InputTabLayout(ctx).apply {
        if (cherry) setPadding(dp(37), 0, 0, 0)
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
        if (cherry) setBackgroundColor(0xFF414141.toInt())
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
