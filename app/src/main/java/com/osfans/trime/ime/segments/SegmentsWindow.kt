/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.NativeTokenizer
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.systemservices.clipboardManager

class SegmentsWindow(di: DI, private val source: String) : BoardWindow.BarBoardWindow(di) {
    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme
    private val windowManager: BoardWindowManager by instance()
    private var clipboardPopup: PopupWindow? = null
    private var updateClipboardToolbar: (() -> Unit)? = null

    override val title: String by lazy {
        context.getString(R.string.word_segment)
    }

    private val adapter by lazy {
        SegmentsAdapter(scope, { onSelectionChanged() }, source, cherryPopup = clipboardPopup != null)
    }

    private val touchListener by lazy {
        DragSelectTouchListener(context, adapter) {
            onSelectionChanged()
        }
    }

    private val ui by lazy {
        SegmentsUi(context, scope).apply {
            recyclerView.apply {
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                    alignItems = AlignItems.FLEX_START
                }
                adapter = this@SegmentsWindow.adapter
            }
            selectButton.setOnClickListener {
                if (adapter.isAllSelected) {
                    adapter.deselect()
                } else {
                    adapter.selectAll()
                }
                onSelectionChanged()
            }
            shareButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    val target = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    val chooser = Intent.createChooser(target, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(chooser)
                }
            }
            searchButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                }
            }
            starButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    service.lifecycleScope.launch {
                        CollectionHelper.addNewBean(text)
                    }
                    ctx.toast(R.string.star_success)
                }
            }
            copyButton.apply {
                isEnabled = false
                setOnClickListener {
                    copySelected()
                }
            }
        }
    }

    private val toolbarButtons by lazy {
        val buttonSpacing = theme.toolBar.buttonSpacing
        FlexboxLayout(context).apply {
            flexDirection = FlexDirection.ROW_REVERSE
            alignItems = AlignItems.CENTER
            val buttons = listOf(ui.selectButton, ui.copyButton, ui.starButton, ui.searchButton, ui.shareButton)
            val size = theme.style.run { candidateViewHeight + commentHeight }
            buttons.forEachIndexed { index, button ->
                val lParams = FlexboxLayout.LayoutParams(dp(size), dp(size))
                if (index > 0) {
                    lParams.marginEnd = dp(buttonSpacing)
                }
                addView(button, lParams)
            }
        }
    }

    override fun onCreateBarView(): View = toolbarButtons

    private fun onSelectionChanged() {
        updateClipboardToolbar?.let {
            it()
            return
        }
        val joined = adapter.joinedSegments
        if (clipboardPopup == null) service.updateComposingText(joined)

        if (adapter.isAllSelected) {
            ui.setSelectButtonToDeselect()
        } else {
            ui.setSelectButtonToSelectAll()
        }

        val hasSelection = adapter.selectionSnapshot.isNotEmpty()
        ui.updateButtons(hasSelection)
    }

    private var viewCreated = false

    override fun onCreateView(): View {
        viewCreated = true
        return ui.root
    }

    private fun copySelected() {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", adapter.joinedSegments))
        clipboardPopup?.dismiss() ?: windowManager.attachWindow(KeyboardWindow)
    }

    internal fun openFromClipboard(anchor: View, translate: (String) -> Unit, search: (String) -> Unit): PopupWindow {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF353535.toInt())
        }
        val popup = PopupWindow(content, anchor.width, anchor.height, true).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
        }
        clipboardPopup = popup
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF434343.toInt())
            addView(TextView(context).apply {
                text = "‹"
                textSize = 28f
                setTextColor(0xFFB3B3B3.toInt())
                gravity = Gravity.CENTER
                contentDescription = context.getString(R.string.cherry_translation_close)
                setOnClickListener { popup.dismiss() }
            }, LinearLayout.LayoutParams(dp(37), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(0xFFB3B3B3.toInt())
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(37)))
        content.addView(onCreateView().apply {
            setBackgroundColor(0xFF353535.toInt())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), 0, dp(15), 0)
            setBackgroundColor(0xFF424242.toInt())
        }
        val selectAll = CheckBox(context).apply {
            buttonTintList = null
            setButtonDrawable(R.drawable.cherry_clipboard_checkbox)
            setText(R.string.cherry_clipboard_select_all)
            setTextColor(0xFFB3B3B3.toInt())
            setOnClickListener {
                if (isChecked) adapter.selectAll() else adapter.deselect()
                onSelectionChanged()
            }
        }
        footer.addView(selectAll)
        footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        val buttons = listOf(
            Triple("copy", android.R.string.copy) { copySelected() },
            Triple("translate", R.string.cherry_clipboard_translate) { translate(adapter.joinedSegments) },
            Triple("search", R.string.cherry_clipboard_search) { search(adapter.joinedSegments) },
        ).map { (key, label, action) ->
            ImageView(context).apply {
                contentDescription = context.getString(label)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageDrawable(scope.drawable("clipboard-segments-$key.png"))
                if (drawable == null) setImageResource(when (key) {
                    "copy" -> R.drawable.ic_baseline_content_copy_24
                    "search" -> R.drawable.ic_baseline_search_24
                    else -> android.R.drawable.ic_menu_edit
                })
                setColorFilter(0xFFB3B3B3.toInt())
                setOnClickListener { action() }
                footer.addView(this, LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    if (key != "search") marginEnd = dp(20)
                })
            }
        }
        updateClipboardToolbar = {
            selectAll.isChecked = adapter.itemCount > 0 && adapter.isAllSelected
            val hasSelection = adapter.selectionSnapshot.isNotEmpty()
            buttons.forEach {
                it.isEnabled = hasSelection
                it.alpha = if (hasSelection) 1f else 0.4f
            }
        }
        content.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(37)))
        adapter.submitList(NativeTokenizer.tokenize(source))
        onSelectionChanged()
        ui.recyclerView.addOnItemTouchListener(touchListener)
        popup.setOnDismissListener {
            ui.recyclerView.removeOnItemTouchListener(touchListener)
            updateClipboardToolbar = null
            clipboardPopup = null
        }
        popup.showAsDropDown(anchor, 0, -anchor.height)
        return popup
    }

    override fun refreshColors() {
        if (!viewCreated) return
        ui.refreshColors()
        (ui.recyclerView.adapter as? SegmentsAdapter)?.notifyDataSetChanged()
    }

    override fun onAttached() {
        val segments = NativeTokenizer.tokenize(source)
        service.lifecycleScope.launch {
            adapter.submitList(segments)
        }
        ui.recyclerView.addOnItemTouchListener(touchListener)
    }

    override fun onDetached() {
        ui.recyclerView.removeOnItemTouchListener(touchListener)
        service.currentInputConnection?.finishComposingText()
    }
}
