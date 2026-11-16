// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add

class CandidateUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val compatView: View,
) : Ui {
    private val theme: Theme get() = scope.theme

    private val usesCherryLayout = "cherry_edit" in theme.presetKeyboards
    private val cherryArrow = usesCherryLayout && scope.drawable("candidate-expand.png") != null
    private var unrolled = false

    val unrollButton =
        (if (cherryArrow) {
            ToolButton(ctx, ToolBar.Button(
                foreground = ToolBar.Button.Foreground(style = "candidate-expand.png", padding = 0),
            ), scope)
        } else {
            ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, scope)
        }).apply { visibility = View.INVISIBLE }

    override val root =
        ctx.constraintLayout {
            add(
                unrollButton,
                lParams(dp(if (cherryArrow) 60 else 40)) {
                    centerVertically()
                    endOfParent()
                },
            )
            add(
                compatView,
                lParams {
                    centerVertically()
                    startOfParent(dp(if (usesCherryLayout) 0 else theme.style.candidatePadding / 2))
                    before(unrollButton)
                },
            )
        }

    fun setUnrolled(unrolled: Boolean) {
        this.unrolled = unrolled
        refreshColors()
    }

    /** Restyles the candidate bar after a scheme switch. */
    fun refreshColors() {
        unrollButton.refreshColors()
        if (unrolled) {
            unrollButton.setIcon(R.drawable.ic_baseline_expand_less_24)
        } else if (!cherryArrow) {
            unrollButton.setIcon(R.drawable.ic_baseline_expand_more_24)
        }
    }
}
