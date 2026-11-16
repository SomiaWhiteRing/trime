// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    private val scope: ThemeScope,
) : ConstraintLayout(context) {
    private val theme: Theme
        get() = scope.theme

    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    init {
        id = R.id.unrolled_candidate_view
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )

        add(
            recyclerView,
            lParams {
                centerInParent()
            },
        )
    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }

    fun installCherryAlternatives(): List<TextView> {
        val sidebar = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28f / 3).roundToInt(), dp(17f / 3).roundToInt(), dp(4), dp(13))
            background = scope.drawable("candidate-sidebar.png")
        }
        addView(sidebar, LayoutParams(dp(65), 0).apply {
            startToStart = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
            bottomToTop = (recyclerView.layoutParams as LayoutParams).bottomToTop
        })
        return List(5) {
            TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(0xFFB3B3B3.toInt())
                sidebar.addView(this, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
            }
        }
    }

    fun installCherryFooter(grid: GridLayoutManager, onReturn: () -> Unit) {
        setBackgroundColor(0xFF0D0D0D.toInt())
        val footer = LinearLayout(context).apply { id = generateViewId() }
        addView(footer, LayoutParams(0, dp(53)).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        })
        recyclerView.updateLayoutParams<LayoutParams> {
            bottomToBottom = LayoutParams.UNSET
            bottomToTop = footer.id
        }
        recyclerView.setPadding(dp(69), dp(8f / 3).roundToInt(), dp(2), 0)
        recyclerView.clipToPadding = false
        recyclerView.scrollBarSize = dp(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recyclerView.verticalScrollbarThumbDrawable = GradientDrawable().apply {
                setColor(0xFF2E2E2E.toInt())
                cornerRadius = dp(1).toFloat()
            }
        }
        val buttons = listOf("上一页", "下一页", "返回").map { label ->
            TextView(context).apply {
                contentDescription = label
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(0xFFB3B3B3.toInt())
                footer.addView(this, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        }
        val up = buttons[0]
        val down = buttons[1]
        val back = buttons[2]
        up.setOnClickListener {
            val rows = (recyclerView.height / dp(43)).coerceAtLeast(1)
            val target = (grid.findFirstVisibleItemPosition() - rows * grid.spanCount).coerceAtLeast(0)
            grid.scrollToPositionWithOffset(target, 0)
        }
        down.setOnClickListener {
            val rows = (recyclerView.height / dp(43)).coerceAtLeast(1)
            val target = (grid.findFirstVisibleItemPosition() + rows * grid.spanCount).coerceAtMost(grid.itemCount - 1)
            grid.scrollToPositionWithOffset(target, 0)
        }
        back.setOnClickListener { onReturn() }
        val updateFooter = {
            val hasPrevious = recyclerView.canScrollVertically(-1)
            val hasNext = recyclerView.canScrollVertically(1)
            if (up.isEnabled != hasPrevious || down.isEnabled != hasNext || footer.background == null) {
                up.isEnabled = hasPrevious
                down.isEnabled = hasNext
                val state = "${if (hasPrevious) 1 else 0}${if (hasNext) 1 else 0}"
                val artwork = scope.drawable("candidate-footer-$state.png")
                footer.background = artwork
                up.text = if (artwork == null) "▲" else ""
                down.text = if (artwork == null) "▼" else ""
                back.text = if (artwork == null) "返回" else ""
                up.alpha = if (!hasPrevious && artwork == null) 0.35f else 1f
                down.alpha = if (!hasNext && artwork == null) 0.35f else 1f
            }
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateFooter()
            }
        })
        recyclerView.doOnLayout { updateFooter() }
    }

    /** Restyles the container background after a scheme switch. */
    fun refreshColors() {
        if ("cherry_edit" in theme.presetKeyboards) return
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )
    }
}
