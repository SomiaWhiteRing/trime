/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.unrolled.CandidatesPagingSource
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.max

abstract class BaseUnrolledCandidateWindow(di: DI) :
    BoardWindow.NoBarBoardWindow(di),
    InputBroadcastReceiver {
    protected val service: TrimeInputMethodService by instance()
    protected val rime: RimeSession by instance()
    protected val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val bar: InputBarDelegate by instance()
    private val windowManager: BoardWindowManager by instance()
    private val compactCandidate: CompactCandidateDelegate by instance()

    protected val theme: Theme
        get() = scope.theme

    internal val usesCherryLayout: Boolean
        get() = "cherry_edit" in theme.presetKeyboards

    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    override fun refreshColors() {
        if (!::candidateLayout.isInitialized) return
        // the decorations share this drawable, so re-coloring its paint repaints the dividers
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        candidateLayout.refreshColors()
        // visible rows re-apply their colors on rebind
        adapter.notifyDataSetChanged()
    }

    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    final override fun onCreateView(): View {
        candidateLayout =
            onCreateCandidateLayout().apply {
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                }
            }
        return candidateLayout
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null

    private val candidatesPager by lazy {
        Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                CandidatesPagingSource(
                    rime,
                    total = compactCandidate.adapter.total,
                    offset = adapter.offset,
                    includeRawInputFallback = usesCherryLayout,
                )
            },
        )
    }

    private var candidatesSubmitJob: Job? = null
    private var rawInput = ""
    private var alternativeIndex = 0
    private var alternatives = emptyList<TextView>()

    internal fun open() {
        if (!usesCherryLayout) {
            windowManager.attachWindow(this)
            return
        }
        val compactItems = compactCandidate.adapter.items
        val keyboard by instance<KeyboardWindow>()
        service.lifecycleScope.launch {
            rawInput = rime.runOnReady { getRawInput() }
            startCandidatePaging(service.lifecycleScope)
            adapter.loadStateFlow.first { it.refresh !is LoadState.Loading }
            if (adapter.itemCount > 0 && windowManager.isAttached(keyboard) && compactCandidate.adapter.items === compactItems) {
                windowManager.attachWindow(this@BaseUnrolledCandidateWindow)
            } else {
                candidatesSubmitJob?.cancel()
            }
        }
    }

    private fun startCandidatePaging(scope: LifecycleCoroutineScope) {
        candidatesSubmitJob = scope.launch {
            candidatesPager.flow.collectLatest {
                adapter.submitData(it)
            }
        }
    }

    override fun onAttached() {
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        if (usesCherryLayout) {
            inputView.showCherryCandidateBoard(true)
            alternatives = candidateLayout.installCherryAlternatives()
            updateAlternatives()
        }
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.drop(if (usesCherryLayout) 1 else 0).collect {
                    if (it <= 0 && (!usesCherryLayout || rime.runOnReady { getRawInput().isEmpty() })) {
                        returnToKeyboard()
                    } else if (!usesCherryLayout) {
                        candidateLayout.resetPosition()
                        adapter.refreshWith(
                            offset = it,
                            highlightedIndex = compactCandidate.adapter.highlightedIdx,
                        )
                    }
                }
            }
        if (!usesCherryLayout) startCandidatePaging(lifecycleCoroutineScope)
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { _ ->
                val index = holder.idx
                val text = holder.text
                rime.launchOnReady {
                    if (!it.selectCandidate(index, global = true) && usesCherryLayout && index == 0 && it.getRawInput() == text) {
                        it.clearComposition()
                        service.commitText(text)
                    }
                }
            }
            setOnLongClickListener { view ->
                inputView.showCandidateActionMenu(holder.idx, holder.text, view, global = true)
                true
            }
        }
    }

    protected fun returnToKeyboard() {
        windowManager.attachWindow(KeyboardWindow)
    }

    private fun updateAlternatives() {
        val letter = rawInput.getOrNull(alternativeIndex)?.lowercaseChar()?.takeIf { it in 'a'..'z' }
        val number = letter?.let { "qwertyuiop".indexOf(it) }?.takeIf { it >= 0 }?.let { "1234567890"[it] }
        val values = listOf(letter, number, letter?.uppercaseChar())
        alternatives.forEachIndexed { index, view ->
            val value = values.getOrNull(index)
            view.text = value?.toString().orEmpty()
            view.setOnClickListener(if (value == null) null else View.OnClickListener {
                val position = alternativeIndex
                val replacement = rawInput.replaceRange(position, position + 1, value.toString())
                lifecycleCoroutineScope.launch {
                    val caret = replacement.substring(0, position + 1).encodeToByteArray().size
                    if (!rime.runOnReady { setRawInput(replacement, caret) }) return@launch
                    rawInput = replacement
                    alternativeIndex = position + 1
                    updateAlternatives()
                }
            })
        }
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        if (!usesCherryLayout || !::candidateLayout.isInitialized) return
        candidateLayout.resetPosition()
        adapter.refreshWith(offset = 0, highlightedIndex = -1)
    }

    override fun onDetached() {
        if (usesCherryLayout) inputView.showCherryCandidateBoard(false)
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.total == adapter.offset),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
    }
}
