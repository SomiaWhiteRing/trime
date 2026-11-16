/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.window

import android.view.Gravity
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxHorizontalDecoration
import com.osfans.trime.ime.window.BoardWindow
import org.kodein.di.DI
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

class FlexboxUnrolledCandidateWindow(di: DI) : BaseUnrolledCandidateWindow(di) {
    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply {
        slideEdge = Gravity.TOP
    }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(scope, cherryExpanded = usesCherryLayout) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                itemView.apply {
                    minimumWidth = dp(40)
                    layoutParams =
                        if (usesCherryLayout) {
                            RecyclerView.LayoutParams(matchParent, dp(43))
                        } else {
                            FlexboxLayoutManager
                                .LayoutParams(wrapContent, dp(theme.style.run { candidateViewHeight + commentHeight }))
                                .apply { flexGrow = 1f }
                        }
                }
            }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager: RecyclerView.LayoutManager by lazy {
        if (usesCherryLayout) {
            GridLayoutManager(context, 2)
        } else {
            FlexboxLayoutManager(context).apply {
                justifyContent = JustifyContent.SPACE_AROUND
                alignItems = AlignItems.FLEX_START
            }
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, scope).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
            if (usesCherryLayout) {
                isVerticalScrollBarEnabled = true
            } else {
                addItemDecoration(FlexboxHorizontalDecoration(separatorDrawable))
            }
        }
        if (usesCherryLayout) {
            installCherryFooter(layoutManager as GridLayoutManager, ::returnToKeyboard)
        }
    }
}
