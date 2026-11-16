/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.filter
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.InputTabLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.recyclerview.verticalLayoutManager
import java.util.Calendar

class ClipboardWindow(di: DI, private val initialTab: Int = 0) : BoardWindow.BarBoardWindow(di) {

    private val service: TrimeInputMethodService by instance()
    private val windowManager: BoardWindowManager by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme

    override val showTitle: Boolean
        get() = !usesCherryLayout

    private lateinit var clipboardLayout: ClipboardLayout
    private lateinit var clipboardPagesAdapter: ClipboardPagesAdapter
    private var wasAttached = false
    internal val usesCherryLayout: Boolean
        get() = "cherry_edit" in theme.presetKeyboards

    private enum class Category(val label: Int) {
        ALL(R.string.cherry_clipboard_all),
        RECENT(R.string.cherry_clipboard_recent),
        TEXT(R.string.cherry_clipboard_text),
        NUMBER(R.string.cherry_clipboard_number),
        LINK(R.string.cherry_clipboard_link),
    }

    private val category = MutableStateFlow(Category.ALL)
    private val numericClip = Regex("""[+\-]?[\p{Nd}]+(?:[.,]\p{Nd}+)?""")
    private lateinit var categoryLabels: List<TextView>
    private lateinit var historyCount: TextView

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste

    private val clipboardBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allBeans() }
    }
    private val collectionBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { CollectionHelper.allBeans() }
    }
    private var clipboardBeansSubmitJob: Job? = null
    private var clipboardCountJob: Job? = null
    private var collectionBeansSubmitJob: Job? = null

    private val clipboardBeansAdapter by lazy {
        object : ClipboardAdapter(scope) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onPin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.unpin(id) }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_CLIPBOARD)
            }

            override fun onShare(bean: DatabaseBean) {
                val text = bean.text ?: return
                launchTextSharing(text)
            }

            override fun onSegment(bean: DatabaseBean) {
                val text = bean.text ?: return
                windowManager.attachWindow(SegmentsWindow(di, text))
            }

            override fun onCollect(bean: DatabaseBean) {
                service.lifecycleScope.launch {
                    CollectionHelper.addNewBean(bean.text ?: "")
                }
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.delete(id) }
            }

            override val enableCollection: Boolean = true
        }
    }

    private val collectionBeansAdapter by lazy {
        object : ClipboardAdapter(scope) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_COLLECTION)
            }

            override fun onShare(bean: DatabaseBean) {
                val text = bean.text ?: return
                launchTextSharing(text)
            }

            override fun onSegment(bean: DatabaseBean) {
                val text = bean.text ?: return
                windowManager.attachWindow(SegmentsWindow(di, text))
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { CollectionHelper.delete(id) }
            }

            override val enableCollection: Boolean = false
        }
    }

    private val clipboardPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = clipboardBeansAdapter
            }
            if (usesCherryLayout) {
                val categories = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(8), 0, context.dp(8), 0)
                }
                categoryLabels = Category.entries.map { filter ->
                    TextView(context).apply {
                        setText(filter.label)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(context.dp(8), 0, context.dp(8), 0)
                        setOnClickListener {
                            category.value = filter
                            refreshCategoryLabels()
                        }
                        categories.addView(
                            this,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                }
                historyCount = TextView(context).apply {
                    textSize = 13f
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setTextColor(scope.colors.keySymbolColor)
                }
                categories.addView(
                    historyCount,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
                )
                root.removeView(recyclerView)
                root.addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            categories,
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(38)),
                        )
                        addView(
                            recyclerView,
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                        )
                    },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
                refreshCategoryLabels()
            }
        }
    }

    private fun refreshCategoryLabels() {
        Category.entries.zip(categoryLabels).forEach { (filter, label) ->
            val selected = filter == category.value
            label.isSelected = selected
            label.setTextColor(if (selected) 0xFFC51640.toInt() else scope.colors.keyTextColor)
            label.paint.isUnderlineText = selected
        }
    }

    private val collectionPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = collectionBeansAdapter
            }
        }
    }

    override fun onCreateView() = ClipboardLayout(context, scope).apply {
        clipboardLayout = this
        clipboardPagesAdapter = object : ClipboardPagesAdapter() {
            override fun getItemCount(): Int = 2
            override fun onCreatePage(position: Int): ClipboardPageUi = when (position) {
                0 -> clipboardPage
                else -> collectionPage
            }
        }
        viewPager.apply {
            adapter = clipboardPagesAdapter
        }
        titleUi.apply {
            tabLayout.onConfigureTab(viewPager) { tabUi, position ->
                configureTab(tabUi, position)
            }
            backButton?.setOnClickListener { windowManager.attachWindow(KeyboardWindow) }
            deleteAllButton.setOnClickListener { anchor ->
                if (usesCherryLayout) {
                    PopupMenu(context, anchor).apply {
                        menu.add(R.string.delete_all).setOnMenuItemClickListener {
                            deleteCurrentPage(viewPager.currentItem)
                            true
                        }
                        show()
                    }
                } else {
                    deleteCurrentPage(viewPager.currentItem)
                }
            }
        }
    }

    private fun deleteCurrentPage(currentItem: Int) {
        when (currentItem) {
            0 -> promptDeleteAll { ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned()) }
            else -> promptDeleteAll { CollectionHelper.deleteAll(CollectionHelper.haveUnpinned()) }
        }
    }

    private fun launchTextSharing(text: String) {
        val target = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(target, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(chooser)
    }

    private fun promptDeleteAll(action: suspend () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.ask_to_delete_all)
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    action()
                }
            }.setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    private fun configureTab(
        tabUi: InputTabLayout.TabUi,
        position: Int,
    ) {
        val label =
            when (position) {
                0 -> R.string.clipboard
                else -> if (usesCherryLayout) R.string.cherry_clipboard_phrases else R.string.collection
            }
        tabUi.label.apply {
            setText(label)
            textSize = if (usesCherryLayout) 14f else theme.style.candidateTextSize
            setTypeface(theme.fonts.candidate, if (usesCherryLayout) Typeface.NORMAL else Typeface.BOLD)
            setTextColor(scope.colors.keyTextColor)
        }
        if (usesCherryLayout) {
            tabUi.root.minimumWidth = context.dp(56)
            tabUi.root.minimumHeight = context.dp(35)
            tabUi.activeStyle = { active ->
                tabUi.label.paint.isFakeBoldText = active
                tabUi.label.setTextColor(if (active) 0xFF9D1E42.toInt() else 0xFFB3B3B3.toInt())
                tabUi.root.background = if (active) {
                    CherryClipboardTabBackground(context.dp(15f), context.dp(0.5f), context.dp(8f))
                } else {
                    null
                }
            }
        }
    }

    override fun onAttached() {
        wasAttached = true
        clipboardLayout.viewPager.setCurrentItem(initialTab, false)
        clipboardBeansSubmitJob = service.lifecycleScope.launch {
            clipboardPages().collectLatest { clipboardBeansAdapter.submitData(it) }
        }
        if (usesCherryLayout) {
            clipboardCountJob = service.lifecycleScope.launch {
                ClipboardHelper.observeItemCount().collect { count ->
                    historyCount.text = context.getString(
                        R.string.cherry_clipboard_count,
                        count,
                        prefs.clipboardLimit.getValue(),
                    )
                }
            }
        }
        collectionBeansSubmitJob = service.lifecycleScope.launch {
            collectionBeansPager.flow.collect {
                collectionBeansAdapter.submitData(it)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun clipboardPages() = category.flatMapLatest { filter ->
        if (filter == Category.ALL) return@flatMapLatest clipboardBeansPager.flow
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        clipboardBeansPager.flow.map { page ->
            page.filter { bean ->
                val text = bean.text.orEmpty().trim()
                when (filter) {
                    Category.ALL -> true
                    Category.RECENT -> bean.time >= today
                    Category.TEXT -> text.isNotEmpty() &&
                        !numericClip.matches(text) && !Patterns.WEB_URL.matcher(text).find()
                    Category.NUMBER -> numericClip.matches(text)
                    Category.LINK -> Patterns.WEB_URL.matcher(text).find()
                }
            }
        }
    }

    override fun onDetached() {
        clipboardBeansAdapter.dismissPopupMenu()
        collectionBeansAdapter.dismissPopupMenu()
        clipboardBeansSubmitJob?.cancel()
        clipboardCountJob?.cancel()
        collectionBeansSubmitJob?.cancel()
    }

    override fun refreshColors() {
        if (!wasAttached) return
        clipboardLayout.titleUi.deleteAllButton.refreshColors()
        if (::categoryLabels.isInitialized) {
            refreshCategoryLabels()
            historyCount.setTextColor(scope.colors.keySymbolColor)
        }
        clipboardLayout.titleUi.tabLayout.reconfigureTabs { tabUi, position ->
            configureTab(tabUi, position)
        }
        clipboardBeansAdapter.refreshColors()
        collectionBeansAdapter.refreshColors()
    }

    override fun onCreateBarView(): View = clipboardLayout.titleUi.root
}
