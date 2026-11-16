/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.InputTabLayout
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.translation.showClipboardTranslation
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.recyclerview.verticalLayoutManager
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

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

    private enum class Category(val label: Int, val referenceText: String) {
        ALL(R.string.cherry_clipboard_all, "全部"),
        RECENT(R.string.cherry_clipboard_recent, "最近"),
        TEXT(R.string.cherry_clipboard_text, "文本"),
        NUMBER(R.string.cherry_clipboard_number, "数字"),
        LINK(R.string.cherry_clipboard_link, "链接"),
    }

    private enum class PhraseFilter(val text: String, val width: Int) {
        FAVORITES("收藏", 45),
        RECENT("最近", 45),
        DEFAULT("默认分组", 73),
    }

    private val phraseFilter = MutableStateFlow(PhraseFilter.DEFAULT)
    private var displayedPhrases = emptyList<DatabaseBean>()
    private var phraseGroups = emptyMap<PhraseFilter, List<DatabaseBean>>()
    private var randomPhraseCounts = emptyMap<PhraseFilter, TextView>()
    private var phraseSequenceIndex = 0
    private var undoStagedPhrase: (() -> Boolean)? = null
    private lateinit var phraseSendButton: View
    private lateinit var phraseUndoButton: View
    private val recentPhrasePrefs by lazy { service.getSharedPreferences("cherry_phrase_recents", Context.MODE_PRIVATE) }
    private val recentPhraseIds by lazy {
        MutableStateFlow(recentPhrasePrefs.getString("ids", "").orEmpty().split(',').mapNotNull(String::toIntOrNull))
    }

    private val category = MutableStateFlow(Category.ALL)
    private val tabLabels by lazy {
        if (usesCherryLayout) {
            listOf(R.string.clipboard, R.string.cherry_clipboard_phrases, R.string.cherry_clipboard_recommend)
        } else {
            listOf(R.string.clipboard, R.string.collection)
        }
    }
    private val numericClip = Regex("""[+\-]?[\p{Nd}]+(?:[.,]\p{Nd}+)?""")
    private lateinit var categoryLabels: List<TextView>
    private val categoryArtwork by lazy {
        Category.entries.mapNotNull { filter ->
            if (context.getText(filter.label).toString() != filter.referenceText) return@mapNotNull null
            val name = filter.name.lowercase()
            val idle = scope.drawable("clipboard-filter-$name-idle.png") ?: return@mapNotNull null
            val active = scope.drawable("clipboard-filter-$name-active.png") ?: return@mapNotNull null
            filter to (idle to active)
        }.toMap()
    }
    private val historyCount by lazy {
        TextView(context).apply {
            textSize = 13f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, context.dp(6), context.dp(14f / 3).roundToInt())
            setTextColor(0xFF757575.toInt())
        }
    }

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste
    private val lockUpdates = mutableListOf<() -> Unit>()
    private val sendModeUpdates = mutableListOf<(CherrySendMode) -> Unit>()
    private val recommendationPreferences = service.getSharedPreferences("cherry_recommendations", Context.MODE_PRIVATE)
    private var recommendationSendMode = CherrySendMode.entries.firstOrNull {
        it.name == recommendationPreferences.getString("send_mode", null)
    } ?: if (recommendationPreferences.getBoolean("automatic_send", false)) CherrySendMode.AUTOMATIC else CherrySendMode.MANUAL

    private val clipboardBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allBeans() }
    }
    private val collectionBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { CollectionHelper.allBeans() }
    }
    private var clipboardBeansSubmitJob: Job? = null
    private var clipboardCountJob: Job? = null
    private var collectionBeansSubmitJob: Job? = null
    private var translationPopup: PopupWindow? = null
    private var deleteConfirmation: PopupWindow? = null
    private lateinit var selectionFooter: LinearLayout
    private lateinit var selectionDelete: TextView
    private lateinit var selectionAll: CheckBox

    private val clipboardBeansAdapter by lazy {
        object : ClipboardAdapter(scope, usesCherryLayout) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                val committed = service.commitText(text)
                if (committed && usesCherryLayout && prefs.cherryClipboardMoveToTopAfterPaste.getValue()) {
                    service.lifecycleScope.launch { ClipboardHelper.moveToTop(bean.id) }
                }
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onSearch(bean: DatabaseBean) {
                bean.text?.let(::searchClipboard)
            }

            override fun onTranslate(bean: DatabaseBean) {
                bean.text?.let(::translateClipboard)
            }

            override fun onMultiSelect(id: Int) {
                val selected = selection.orEmpty()
                updateSelection(if (id in selected) selected - id else selected + id)
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
                showSegments(text)
            }

            override fun onCollect(bean: DatabaseBean) {
                service.lifecycleScope.launch {
                    CollectionHelper.addNewBean(bean.text ?: "")
                }
            }

            override fun onDelete(id: Int) {
                if (usesCherryLayout) {
                    confirmCherryDelete { ClipboardHelper.delete(id) }
                } else {
                    service.lifecycleScope.launch { ClipboardHelper.delete(id) }
                }
            }

            override val enableCollection: Boolean = true
        }
    }

    private val collectionBeansAdapter by lazy {
        object : ClipboardAdapter(scope, usesCherryLayout) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                if (usesCherryLayout && recommendationSendMode == CherrySendMode.SEQUENTIAL) {
                    val index = displayedPhrases.indexOfFirst { it.id == bean.id }
                    if (index < 0) return
                    undoStagedPhrase = service.stageRecommendation(text) ?: return
                    phraseSequenceIndex = index
                    updatePhraseSequence()
                    return
                }
                if (service.commitText(text) && usesCherryLayout) {
                    rememberPhrase(bean)
                    if (recommendationSendMode == CherrySendMode.AUTOMATIC) {
                        service.sendRecommendationInCurrentEditor()
                    }
                }
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onPin(id: Int) {
                service.lifecycleScope.launch { CollectionHelper.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { CollectionHelper.unpin(id) }
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
                showSegments(text)
            }

            override fun onDelete(id: Int) {
                if (usesCherryLayout) {
                    deleteConfirmation?.dismiss()
                    deleteConfirmation = showCherryPhraseDeleteConfirmation(clipboardLayout, scope) {
                        service.lifecycleScope.launch { CollectionHelper.delete(id) }
                    }
                } else {
                    service.lifecycleScope.launch { CollectionHelper.delete(id) }
                }
            }

            override val enableCollection: Boolean = false
        }
    }

    private val clipboardPage: ClipboardPageUi by lazy {
        ClipboardPageUi(context, usesCherryLayout).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = clipboardBeansAdapter
            }
            if (usesCherryLayout) clipboardBeansAdapter.addOnPagesUpdatedListener(::refreshCompactBackground)
            if (usesCherryLayout && prefs.cherryClipboardCompactLayout.getValue()) {
                setCompact(true)
                clipboardBeansAdapter.compact = true
            }
            if (usesCherryLayout) {
                root.setBackgroundColor(0xFF353535.toInt())
                recyclerView.itemAnimator = null
                val categories = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(0xFF363636.toInt())
                    setPadding(context.dp(8), 0, context.dp(8), 0)
                }
                categoryLabels = Category.entries.map { filter ->
                    TextView(context).apply {
                        setText(filter.label)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(context.dp(8), 0, context.dp(8), 0)
                        setOnClickListener {
                                if (clipboardBeansAdapter.selection != null) updateSelection(null)
                            category.value = filter
                            refreshCategoryLabels()
                        }
                        categories.addView(
                            this,
                            LinearLayout.LayoutParams(
                                if (filter in categoryArtwork) context.dp(45) else LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
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
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(30)),
                        )
                        addView(
                            recyclerView,
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                        )
                        addView(
                            FrameLayout(context).apply {
                                setBackgroundColor(0xFF424242.toInt())
                                addView(createClipboardLock(), FrameLayout.LayoutParams(context.dp(61), FrameLayout.LayoutParams.MATCH_PARENT))
                                addView(createClipboardSettingsButton(), FrameLayout.LayoutParams(context.dp(44), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                                    marginStart = context.dp(96)
                                })
                                addView(createSelectionFooter(), FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                ))
                            },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(37)),
                        )
                    },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
                refreshCategoryLabels()
            }
        }
    }

    private fun createClipboardLock(recommendation: Boolean = false): ImageView = ImageView(context).apply {
        contentDescription = context.getString(R.string.clipboard_return_after_paste)
        val update = {
            val locked = !clipboardReturnAfterPaste
            val state = if (locked) "locked" else "unlocked"
            val reference = if (recommendation) scope.drawable("clipboard-recommend-lock-$state.png") else null
            isSelected = locked
            if (reference != null) {
                scaleType = ImageView.ScaleType.FIT_XY
                imageTintList = null
                setImageDrawable(reference)
            } else {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val artwork = scope.drawable("clipboard-lock-$state.png")
                if (artwork == null) setImageResource(android.R.drawable.ic_lock_lock) else setImageDrawable(artwork)
                imageTintList = ColorStateList.valueOf(
                    if (locked) 0xFF9D1E42.toInt() else 0xFFB3B3B3.toInt(),
                )
            }
        }
        lockUpdates.add(update)
        update()
        setOnClickListener {
            prefs.clipboardReturnAfterPaste.setValue(!clipboardReturnAfterPaste)
            lockUpdates.forEach { it() }
        }
    }

    private fun createClipboardSettingsButton(recommendation: Boolean = false): ImageView = ImageView(context).apply {
        contentDescription = "设置"
        val reference = if (recommendation) scope.drawable("clipboard-recommend-settings.png") else null
        if (reference != null) {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(reference)
        } else {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageDrawable(scope.drawable("clipboard-footer-settings.png"))
            if (drawable == null) setImageResource(android.R.drawable.ic_menu_preferences)
            imageTintList = ColorStateList.valueOf(0xFFB3B3B3.toInt())
        }
        setOnClickListener { clipboardLayout.showSettings(createClipboardSettings()) }
    }

    private fun createPhraseSendFooter(onModeChanged: (CherrySendMode) -> Unit = {}) = FrameLayout(context).apply {
        setBackgroundColor(0xFF424242.toInt())
        addView(View(context).apply {
            setBackgroundColor(0xFF4D4D4D.toInt())
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(1f / 3).roundToInt().coerceAtLeast(1)))
        addView(createClipboardLock(recommendation = true), FrameLayout.LayoutParams(context.dp(61), FrameLayout.LayoutParams.MATCH_PARENT))
        addView(
            createClipboardSettingsButton(recommendation = true),
            FrameLayout.LayoutParams(context.dp(44), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = context.dp(96)
            },
        )
        val sendingMode = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(0xFFB3B3B3.toInt())
        }
        val updateMode: (CherrySendMode) -> Unit = { mode ->
            val name = mode.name.lowercase()
            val label = context.getString(mode.footer)
            val artwork = when (label) {
                "手动", "自动", "随机", "顺序" -> scope.drawable("clipboard-recommend-send-$name.png")
                else -> null
            }
            sendingMode.foreground = artwork
            sendingMode.text = "$label ▾"
            sendingMode.setTextColor(if (artwork == null) 0xFFB3B3B3.toInt() else Color.TRANSPARENT)
        }
        updateMode(recommendationSendMode)
        sendModeUpdates.add { mode ->
            updateMode(mode)
            onModeChanged(mode)
        }
        sendingMode.setOnClickListener {
            clipboardLayout.showPanelOverlay(createCherrySendModePanel(
                context,
                scope,
                recommendationSendMode,
                clipboardLayout::dismissPanelOverlay,
            ) { mode ->
                recommendationSendMode = mode
                recommendationPreferences.edit().putString("send_mode", mode.name).apply()
                sendModeUpdates.forEach { it(mode) }
                clipboardLayout.dismissPanelOverlay()
            })
        }
        addView(sendingMode, FrameLayout.LayoutParams(context.dp(56), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            marginStart = context.dp(205)
        })
    }

    private fun createSelectionFooter() = LinearLayout(context).apply {
        selectionFooter = this
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF424242.toInt())
        setPadding(context.dp(10), 0, context.dp(10), 0)
        isClickable = true
        visibility = View.GONE
        selectionAll = CheckBox(context).apply {
            setText(R.string.cherry_clipboard_select_all)
            setTextColor(0xFFB3B3B3.toInt())
            buttonTintList = null
            setButtonDrawable(R.drawable.cherry_clipboard_checkbox)
            setPaddingRelative(context.dp(6), paddingTop, paddingEnd, paddingBottom)
            setOnClickListener {
                val select = isChecked
                val filter = category.value
                service.lifecycleScope.launch {
                    val ids = ClipboardHelper.clipboardRecords().filter(historyFilter(filter)).map { it.id }.toSet()
                    if (clipboardBeansAdapter.selection != null && category.value == filter) {
                        updateSelection(if (select) ids else emptySet())
                        selectionAll.isChecked = select && ids.isNotEmpty()
                    }
                }
            }
        }
        addView(selectionAll)
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(TextView(context).apply {
            setText(R.string.cancel)
            setTextColor(0xFFB3B3B3.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { updateSelection(null) }
        }, LinearLayout.LayoutParams(context.dp(56), LinearLayout.LayoutParams.MATCH_PARENT))
        selectionDelete = TextView(context).apply {
            setTextColor(0xFFB3B3B3.toInt())
            gravity = Gravity.CENTER
            setOnClickListener {
                val ids = clipboardBeansAdapter.selection.orEmpty().toList()
                if (ids.isNotEmpty()) {
                    confirmCherryDelete {
                        ClipboardHelper.deleteByIds(ids)
                        updateSelection(null)
                    }
                }
            }
        }
        addView(selectionDelete, LinearLayout.LayoutParams(context.dp(77), LinearLayout.LayoutParams.MATCH_PARENT))
    }

    private fun updateSelection(ids: Set<Int>?) {
        clipboardBeansAdapter.selection = ids
        selectionFooter.visibility = if (ids == null) View.GONE else View.VISIBLE
        selectionDelete.text = context.getString(R.string.cherry_clipboard_delete_count, ids.orEmpty().size)
        selectionDelete.isEnabled = !ids.isNullOrEmpty()
        selectionAll.isChecked = !ids.isNullOrEmpty() && ids.size == clipboardBeansAdapter.snapshot().items.size
    }

    private fun confirmCherryDelete(delete: suspend () -> Unit) {
        deleteConfirmation?.dismiss()
        deleteConfirmation = showCherryDeleteConfirmation(clipboardLayout) {
            service.lifecycleScope.launch { delete() }
        }
    }

    private fun showSegments(text: String) {
        val segments = SegmentsWindow(di, text)
        if (usesCherryLayout) {
            clipboardLayout.showSegments(segments.createClipboardOverlay(
                { clipboardLayout.dismissSegments() },
                ::translateClipboard,
                ::searchClipboard,
            ))
        } else {
            windowManager.attachWindow(segments)
        }
    }

    internal fun handleClipboardBack(commit: Boolean): Boolean {
        if (deleteConfirmation?.isShowing == true) {
            if (commit) deleteConfirmation?.dismiss()
            return true
        }
        if (clipboardLayout.isShowingPanelOverlay) {
            if (commit) clipboardLayout.dismissPanelOverlay()
            return true
        }
        return if (commit) clipboardLayout.dismissSegments() else clipboardLayout.isShowingSegments
    }

    private fun translateClipboard(text: String) {
        if (text.isBlank()) return
        translationPopup?.dismiss()
        translationPopup = showClipboardTranslation(clipboardLayout, text, service::insertTranslation)
    }

    private fun searchClipboard(text: String) {
        val url = Uri.parse("https://m.baidu.com/s").buildUpon().appendQueryParameter("word", text).build()
        val intent = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Search requires a browser.
        }
    }

    private fun refreshCategoryLabels() {
        Category.entries.zip(categoryLabels).forEach { (filter, label) ->
            val selected = filter == category.value
            label.isSelected = selected
            val artwork = categoryArtwork[filter]
            label.foreground = artwork?.let { if (selected) it.second else it.first }
            label.setTextColor(when {
                artwork != null -> Color.TRANSPARENT
                selected -> 0xFFC51640.toInt()
                else -> scope.colors.keyTextColor
            })
            label.paint.isUnderlineText = artwork == null && selected
        }
    }

    private fun createClipboardSettings() = ScrollView(context).apply {
        setBackgroundColor(0xFF353535.toInt())
        isClickable = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(7), 0, context.dp(7))
            addView(createClipboardLayoutSelector(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(context.dp(9), 0, context.dp(9), context.dp(7))
            })
            val rows = listOf(
                "启用剪贴板" to prefs.clipboardListening,
                "候选条展示复制内容" to prefs.clipboardSuggestion,
                "使用后移至最前" to prefs.cherryClipboardMoveToTopAfterPaste,
            )
            val controls = rows.map { (label, preference) ->
                SwitchCompat(context).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(0xFFB3B3B3.toInt())
                    isClickable = true
                    isChecked = preference.getValue()
                    applyCherrySwitchArtwork(this)
                    setPadding(context.dp(14), 0, context.dp(14), 0)
                    background = GradientDrawable().apply {
                        setColor(0xFF434343.toInt())
                        setStroke(context.dp(1f / 3f).toInt().coerceAtLeast(1), 0xFF4F4F4F.toInt())
                        cornerRadius = context.dp(3.5f)
                    }
                    addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(42)).apply {
                        setMargins(context.dp(9), 0, context.dp(9), context.dp(7))
                    })
                }
            }
            controls[1].isEnabled = controls[0].isChecked
            controls.forEachIndexed { index, toggle ->
                toggle.setOnCheckedChangeListener { _, enabled ->
                    rows[index].second.setValue(enabled)
                    if (index == 0) controls[1].isEnabled = enabled
                }
            }
            addView(TextView(context).apply {
                text = "清空剪贴板"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(0xFFB3B3B3.toInt())
                setPadding(context.dp(14), 0, context.dp(14), 0)
                setOnClickListener { deleteCurrentPage(0) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(56)))
        })
    }

    private fun createClipboardLayoutSelector() = LinearLayout(context).apply {
        setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(12))
        background = GradientDrawable().apply {
            setColor(0xFF434343.toInt())
            cornerRadius = context.dp(3.5f)
        }
        val accent = 0xFF9D1E42.toInt()
        val previewWidth = minOf(context.dp(145), (clipboardLayout.width - context.dp(46)) / 2)
        val controls = listOf(
            false to R.string.cherry_clipboard_regular_layout,
            true to R.string.cherry_clipboard_compact_layout,
        ).map { (compact, title) ->
            if (compact) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            val option = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val outline = GradientDrawable().apply {
                cornerRadius = context.dp(9f)
                setColor(Color.TRANSPARENT)
            }
            val preview = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                val mode = if (compact) "compact" else "regular"
                setImageDrawable(scope.drawable("clipboard-layout-$mode.png"))
                foreground = outline
            }
            val button = RadioButton(context).apply {
                setText(title)
                textSize = 14f
                setTextColor(0xFFB3B3B3.toInt())
                buttonDrawable = null
                compoundDrawablePadding = context.dp(6)
                gravity = Gravity.CENTER_VERTICAL
            }
            option.addView(preview, LinearLayout.LayoutParams(previewWidth, context.dp(93)))
            option.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(25)).apply {
                topMargin = context.dp(2)
            })
            addView(option, LinearLayout.LayoutParams(previewWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
            Triple(preview, button, outline)
        }
        val render = { compact: Boolean ->
            controls.forEachIndexed { index, (_, button, outline) ->
                val selected = compact == (index == 1)
                button.isChecked = selected
                val indicator = context.getDrawable(
                    if (selected) R.drawable.cherry_clipboard_checkbox_checked else R.drawable.cherry_clipboard_checkbox_unchecked,
                )?.apply { setBounds(0, 0, context.dp(16), context.dp(16)) }
                button.setCompoundDrawablesRelative(indicator, null, null, null)
                outline.setStroke(if (selected) context.dp(1f / 2).toInt().coerceAtLeast(1) else 0, accent)
            }
        }
        render(prefs.cherryClipboardCompactLayout.getValue())
        controls.forEachIndexed { index, (preview, button, _) ->
            val choose = View.OnClickListener {
                val compact = index == 1
                if (prefs.cherryClipboardCompactLayout.getValue() != compact) {
                    prefs.cherryClipboardCompactLayout.setValue(compact)
                    clipboardPage.setCompact(compact)
                    clipboardBeansAdapter.compact = compact
                    collectionPage.setCompact(compact)
                    collectionBeansAdapter.compact = compact
                }
                render(compact)
            }
            preview.setOnClickListener(choose)
            button.setOnClickListener(choose)
        }
    }

    private fun applyCherrySwitchArtwork(toggle: SwitchCompat) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val accent = 0xFF9D1E42.toInt()
        val thumbSize = context.dp(11.4f).toInt()
        val inset = context.dp(3.8f).toInt()
        toggle.showText = false
        toggle.switchMinWidth = context.dp(34)
        toggle.thumbDrawable = StateListDrawable().apply {
            listOf(Color.TRANSPARENT, accent).forEachIndexed { index, color ->
                val thumb = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize(thumbSize, thumbSize)
                    setColor(color)
                }
                addState(if (index == 0) checked else intArrayOf(), LayerDrawable(arrayOf(thumb)).apply {
                    setLayerInset(0, inset, inset, inset, inset)
                })
            }
        }
        val trackOn = scope.drawable("clipboard-switch-on.png")
        val trackOff = scope.drawable("clipboard-switch-off.png")
        if (trackOn != null && trackOff != null) {
            val tint = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_ATOP)
            trackOn.colorFilter = tint
            trackOff.colorFilter = tint
            toggle.trackDrawable = StateListDrawable().apply {
                addState(checked, trackOn)
                addState(intArrayOf(), trackOff)
            }
        } else {
            toggle.trackTintList = ColorStateList(arrayOf(checked, intArrayOf()), intArrayOf(accent, 0xFF777777.toInt()))
        }
    }

    private fun rememberPhrase(bean: DatabaseBean) {
        val ids = (listOf(bean.id) + recentPhraseIds.value.filterNot { it == bean.id }).take(300)
        if (ids != recentPhraseIds.value) {
            recentPhraseIds.value = ids
            recentPhrasePrefs.edit().putString("ids", ids.joinToString(",")).apply()
        }
    }

    private fun createRandomPhraseGrid(): ScrollView {
        val counts = mutableMapOf<PhraseFilter, TextView>()
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(8), context.dp(5f / 3f).roundToInt(), context.dp(8), 0)
            for (pair in PhraseFilter.entries.chunked(2)) {
                addView(LinearLayout(context).apply {
                    for ((index, filter) in pair.withIndex()) {
                        val count = TextView(context).apply {
                            gravity = Gravity.END or Gravity.CENTER_VERTICAL
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(0xFFA0A0A0.toInt())
                        }
                        counts[filter] = count
                        val card = FrameLayout(context).apply {
                            val name = filter.name.lowercase()
                            val artwork = scope.drawable("clipboard-phrase-random-$name.png")
                            background = artwork ?: GradientDrawable().apply {
                                setColor(0xFF434343.toInt())
                                cornerRadius = context.dp(4f)
                                setStroke(context.dp(1f / 3f).roundToInt().coerceAtLeast(1), 0xFF747474.toInt())
                            }
                            addView(TextView(context).apply {
                                text = filter.text
                                textSize = 14f
                                gravity = Gravity.CENTER_VERTICAL
                                setTextColor(if (artwork == null) 0xFFB3B3B3.toInt() else Color.TRANSPARENT)
                            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                                marginStart = context.dp(15)
                            })
                            addView(count, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END).apply {
                                marginEnd = context.dp(14)
                            })
                            setOnClickListener {
                                val phrases = phraseGroups[filter].orEmpty()
                                if (phrases.isEmpty()) return@setOnClickListener
                                val bean = phrases.random()
                                val text = bean.text ?: return@setOnClickListener
                                if (service.commitText(text)) {
                                    rememberPhrase(bean)
                                    service.sendRecommendationInCurrentEditor()
                                    if (clipboardReturnAfterPaste) windowManager.attachWindow(KeyboardWindow)
                                }
                            }
                        }
                        addView(FrameLayout(context).apply {
                            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                                if (index == 0) marginEnd = context.dp(6)
                            })
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                    }
                    if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(43)).apply {
                    bottomMargin = context.dp(6)
                })
            }
        }
        randomPhraseCounts = counts
        updateRandomPhraseCounts()
        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(rows)
        }
    }

    private fun updateRandomPhraseCounts() {
        randomPhraseCounts.forEach { (filter, count) ->
            count.text = phraseGroups[filter].orEmpty().size.toString()
        }
    }

    private fun updatePhraseSequence() {
        val visible = recommendationSendMode == CherrySendMode.SEQUENTIAL && phraseSequenceIndex in displayedPhrases.indices
        collectionBeansAdapter.sequencePosition = if (visible) phraseSequenceIndex else -1
        if (!::phraseSendButton.isInitialized) return
        phraseSendButton.visibility = if (visible) View.VISIBLE else View.GONE
        phraseUndoButton.visibility = if (visible && undoStagedPhrase != null) View.VISIBLE else View.GONE
        val asset = if (undoStagedPhrase == null) "clipboard-recommend-send-plane.png" else "clipboard-recommend-staged-plane.png"
        phraseSendButton.background = scope.drawable(asset)
    }

    private fun sendNextPhrase() {
        val bean = displayedPhrases.getOrNull(phraseSequenceIndex) ?: return
        val text = bean.text ?: return
        if (undoStagedPhrase == null) undoStagedPhrase = service.stageRecommendation(text) ?: return
        if (!service.sendRecommendationInCurrentEditor()) {
            updatePhraseSequence()
            return
        }
        undoStagedPhrase = null
        val next = displayedPhrases.getOrNull(phraseSequenceIndex + 1) ?: displayedPhrases.first()
        rememberPhrase(bean)
        phraseSequenceIndex = displayedPhrases.indexOfFirst { it.id == next.id }
        updatePhraseSequence()
        collectionPage.recyclerView.smoothScrollToPosition(phraseSequenceIndex)
    }

    private val collectionPage: ClipboardPageUi by lazy {
        ClipboardPageUi(context, usesCherryLayout, phrase = usesCherryLayout).apply {
            if (usesCherryLayout) root.setBackgroundColor(0xFF363636.toInt())
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = collectionBeansAdapter
            }
            if (usesCherryLayout) {
                collectionBeansAdapter.addOnPagesUpdatedListener(::refreshCompactBackground)
                recyclerView.itemAnimator = null
                val filters = LinearLayout(context).apply {
                    setBackgroundColor(0xFF363636.toInt())
                    setPadding(context.dp(8), 0, 0, 0)
                }
                val labels = PhraseFilter.entries.associateWith { filter ->
                    TextView(context).apply {
                        text = filter.text
                        textSize = 14f
                        gravity = Gravity.CENTER
                        filters.addView(this, LinearLayout.LayoutParams(context.dp(filter.width), LinearLayout.LayoutParams.MATCH_PARENT))
                    }
                }
                fun select(filter: PhraseFilter) {
                    if (phraseFilter.value != filter) {
                        phraseSequenceIndex = 0
                        displayedPhrases = emptyList()
                        undoStagedPhrase = null
                    }
                    phraseFilter.value = filter
                    labels.forEach { (item, label) ->
                        val state = if (item == filter) "active" else "idle"
                        label.foreground = scope.drawable("clipboard-phrase-${item.name.lowercase()}-$state.png")
                        label.setTextColor(if (label.foreground != null) Color.TRANSPARENT else if (item == filter) 0xFF9D1E42.toInt() else 0xFFB3B3B3.toInt())
                    }
                    recyclerView.scrollToPosition(0)
                }
                labels.forEach { (filter, label) -> label.setOnClickListener { select(filter) } }
                select(phraseFilter.value)
                val randomGrid = createRandomPhraseGrid()
                fun showMode(mode: CherrySendMode) {
                    val random = mode == CherrySendMode.RANDOM
                    filters.visibility = if (random) View.GONE else View.VISIBLE
                    recyclerView.visibility = if (random) View.GONE else View.VISIBLE
                    randomGrid.visibility = if (random) View.VISIBLE else View.GONE
                }
                showMode(recommendationSendMode)
                root.removeView(recyclerView)
                root.addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(filters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(34)))
                        addView(FrameLayout(context).apply {
                            addView(recyclerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                            addView(randomGrid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                            phraseSendButton = TextView(context).apply {
                                contentDescription = context.getText(R.string.cherry_phrase_send)
                                gravity = Gravity.CENTER
                                if (scope.drawable("clipboard-recommend-send-plane.png") == null) setText(R.string.cherry_phrase_send)
                                setOnClickListener { sendNextPhrase() }
                            }
                            phraseUndoButton = TextView(context).apply {
                                contentDescription = context.getText(R.string.cherry_phrase_undo)
                                gravity = Gravity.CENTER
                                background = scope.drawable("clipboard-recommend-revoke.png")
                                if (background == null) setText(R.string.cherry_phrase_undo)
                                setOnClickListener {
                                    if (undoStagedPhrase?.invoke() == true) undoStagedPhrase = null
                                    updatePhraseSequence()
                                }
                            }
                            addView(phraseSendButton, FrameLayout.LayoutParams(context.dp(55), context.dp(40), Gravity.END or Gravity.BOTTOM).apply {
                                bottomMargin = context.dp(29)
                            })
                            addView(phraseUndoButton, FrameLayout.LayoutParams(context.dp(44), context.dp(40), Gravity.END or Gravity.BOTTOM).apply {
                                bottomMargin = context.dp(29)
                                marginEnd = context.dp(55)
                            })
                            updatePhraseSequence()
                        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                        addView(
                            createPhraseSendFooter { mode ->
                                showMode(mode)
                                phraseSequenceIndex = 0
                                undoStagedPhrase = null
                                updatePhraseSequence()
                                recyclerView.scrollToPosition(0)
                            },
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(29)),
                        )
                    },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            }
            if (usesCherryLayout && prefs.cherryClipboardCompactLayout.getValue()) {
                setCompact(true)
                collectionBeansAdapter.compact = true
            }
        }
    }

    private val recommendationPage by lazy {
        ClipboardPageUi(context).apply {
            root.removeView(recyclerView)
            root.setBackgroundColor(0xFF353535.toInt())
            val recommendations = CherryClipboardRecommendationView.loadProfile()
            if (recommendations.isNotEmpty()) {
                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val recommendationsView = CherryClipboardRecommendationView(
                        context,
                        scope,
                        recommendations,
                        recommendationSendMode,
                        stage = service::stageRecommendation,
                        send = service::sendRecommendationInCurrentEditor,
                    ) { phrase ->
                        if (service.commitText(phrase)) {
                            if (recommendationSendMode != CherrySendMode.MANUAL && !clipboardReturnAfterPaste) {
                                service.sendRecommendationInCurrentEditor()
                            }
                            if (clipboardReturnAfterPaste) windowManager.attachWindow(KeyboardWindow)
                        }
                    }
                    addView(recommendationsView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(
                        createPhraseSendFooter(recommendationsView::updateMode),
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(29)),
                    )
                }
                root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                return@apply
            }
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val illustration = scope.drawable("clipboard-recommend-empty.png")
                if (illustration != null) {
                    addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(illustration)
                    }, LinearLayout.LayoutParams(context.dp(120), context.dp(68)))
                }
                addView(TextView(context).apply {
                    setText(R.string.cherry_clipboard_recommend_empty)
                    textSize = 14f
                    setTextColor(0xFFB3B3B3.toInt())
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (illustration != null) topMargin = context.dp(11)
                })
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }

    private val inputView: InputView by instance()

    override fun onCreateView() = ClipboardLayout(context, scope, inputView::attachClipboardChooser).apply {
        clipboardLayout = this
        if (usesCherryLayout) {
            setBackgroundColor(0xFF363636.toInt())
            titleUi.tabLayout.alpha = 0f
            viewPager.alpha = 0f
        }
        clipboardPagesAdapter = object : ClipboardPagesAdapter() {
            override fun getItemCount(): Int = tabLabels.size
            override fun onCreatePage(position: Int): ClipboardPageUi = when (position) {
                0 -> clipboardPage
                1 -> collectionPage
                else -> recommendationPage
            }
        }
        viewPager.apply {
            adapter = clipboardPagesAdapter
            isUserInputEnabled = !usesCherryLayout
            if (usesCherryLayout) {
                // A no-op transformer disables ViewPager2's RecyclerView item animations, as in CHERRY.
                setPageTransformer { _, _ -> }
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        if (position != 0 && clipboardBeansAdapter.selection != null) updateSelection(null)
                    }
                })
            }
        }
        titleUi.apply {
            tabLayout.onConfigureTab(viewPager) { tabUi, position ->
                configureTab(tabUi, position)
            }
            backButton?.setOnClickListener {
                if (dismissSegments()) return@setOnClickListener
                if (clipboardBeansAdapter.selection != null) updateSelection(null)
                else if (!dismissSettings()) windowManager.attachWindow(KeyboardWindow)
            }
            actionButton.setOnClickListener {
                if (usesCherryLayout) {
                    toggleTabChooser(tabLabels)
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
        val label = tabLabels[position]
        tabUi.label.apply {
            setText(label)
            textSize = if (usesCherryLayout) 14f else theme.style.candidateTextSize
            setTypeface(theme.fonts.candidate, if (usesCherryLayout) Typeface.NORMAL else Typeface.BOLD)
            setTextColor(scope.colors.keyTextColor)
        }
        if (usesCherryLayout) {
            tabUi.root.minimumWidth = context.dp(if (position == 2) 42 else 56)
            tabUi.root.minimumHeight = context.dp(35)
            val isVivo = Build.BRAND.equals("vivo", ignoreCase = true)
            if (isVivo && tabUi.label.text.endsWith('板')) {
                val title = SpannableString(tabUi.label.text)
                val lastGlyphOffset = context.dp(1f / 3) / 0.975f
                title.setSpan(
                    object : ReplacementSpan() {
                        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
                            paint.measureText(text, start, end).roundToInt()

                        override fun draw(
                            canvas: Canvas, text: CharSequence, start: Int, end: Int,
                            x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
                        ) {
                            canvas.drawText(text, start, end, x + lastGlyphOffset, y.toFloat(), paint)
                        }
                    },
                    title.length - 1, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                tabUi.label.text = title
            }
            val tabName = listOf("history", "phrases", "recommend")[position]
            val referenceText = listOf("剪贴板", "常用语", "推荐")[position]
            val tabArtwork = if (isVivo && tabUi.label.text.toString() == referenceText) {
                val idle = scope.drawable("clipboard-tab-$tabName-idle.png")
                val active = scope.drawable("clipboard-tab-$tabName-active.png")
                if (idle != null && active != null) idle to active else null
            } else null
            val contour = tabArtwork?.let { scope.drawable("clipboard-tab-$tabName-contour.png") }
            tabUi.activeStyle = { active ->
                tabUi.root.foreground = tabArtwork?.let { if (active) it.second else it.first }
                val bold = active && !isVivo
                tabUi.label.setTypeface(theme.fonts.candidate, if (bold) Typeface.BOLD else Typeface.NORMAL)
                tabUi.label.scaleX = if (bold) 0.98f else 0.975f
                tabUi.label.scaleY = if (bold) 1f else 0.975f
                tabUi.label.translationX = when (position) {
                    0 -> context.dp(if (bold) 0.6f else 1f / 3)
                    1 -> context.dp(1f / 6)
                    else -> 0f
                }
                val baselineOffset = if (isVivo) -0.375f else if (bold) 2f / 3 else 0.625f
                tabUi.label.translationY = context.dp(baselineOffset)
                tabUi.label.setTextColor(when {
                    tabArtwork != null -> Color.TRANSPARENT
                    !active -> 0xFFB3B3B3.toInt()
                    isVivo -> 0xFFA21E42.toInt()
                    else -> 0xFF9D1E42.toInt()
                })
                tabUi.root.background = if (active) {
                    val contourOffset = when (position) {
                        1 -> context.dp(-1f / 3)
                        2 -> context.dp(-0.5f)
                        else -> 0f
                    }
                    val (left, right) = when (position) {
                        0 -> 28 to 30
                        1 -> 29 to 29
                        else -> 30 to 29
                    }
                    CherryClipboardTabBackground(context, contourOffset, contour, left, right)
                } else {
                    null
                }
            }
        }
    }

    fun open() = windowManager.attachWindow(this)

    private fun startClipboardPaging() {
        clipboardBeansSubmitJob = service.lifecycleScope.launch {
            clipboardPages().collectLatest { clipboardBeansAdapter.submitData(it) }
        }
    }

    private fun startCollectionPaging() {
        collectionBeansSubmitJob = service.lifecycleScope.launch {
            val pages = if (usesCherryLayout) {
                combine(phraseFilter, recentPhraseIds, CollectionHelper.observeRecords()) { filter, ids, records ->
                    val currentId = displayedPhrases.getOrNull(phraseSequenceIndex)?.id
                    val byId = records.associateBy { it.id }
                    phraseGroups = mapOf(
                        PhraseFilter.DEFAULT to records,
                        PhraseFilter.FAVORITES to records.filter { it.pinned },
                        PhraseFilter.RECENT to ids.mapNotNull(byId::get),
                    )
                    displayedPhrases = phraseGroups.getValue(filter)
                    updateRandomPhraseCounts()
                    phraseSequenceIndex = displayedPhrases.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                    updatePhraseSequence()
                    PagingData.from(displayedPhrases)
                }
            } else {
                collectionBeansPager.flow
            }
            pages.collectLatest { collectionBeansAdapter.submitData(it) }
        }
    }

    override fun onAttached() {
        wasAttached = true
        clipboardLayout.viewPager.setCurrentItem(initialTab, false)
        if (clipboardBeansSubmitJob == null) startClipboardPaging()
        if (collectionBeansSubmitJob == null) startCollectionPaging()
        if (usesCherryLayout) {
            val opensHistory = initialTab == 0
            var showHistory = !opensHistory
            var historyItems = 0
            if (opensHistory) {
                clipboardPage.recyclerView.alpha = 0f
                updateHistoryCount(0)
            }
            clipboardCountJob = service.lifecycleScope.launch {
                ClipboardHelper.observeItemCount().collect { count ->
                    historyItems = count
                    if (showHistory) updateHistoryCount(count)
                }
            }
            clipboardLayout.doOnPreDraw { layout ->
                layout.postOnAnimation {
                    if (!layout.isAttachedToWindow) return@postOnAnimation
                    clipboardLayout.titleUi.tabLayout.alpha = 1f
                    clipboardLayout.viewPager.alpha = 1f
                    if (opensHistory) {
                        // Keep the filters and empty counter visible before showing the loaded history.
                        layout.doOnPreDraw {
                            // Round the observed exposure time up to whole display frames.
                            val refreshRate = layout.display?.refreshRate ?: 60f
                            var frames = ceil(refreshRate / 10f).toInt().coerceAtLeast(1)
                            layout.postOnAnimation(object : Runnable {
                                override fun run() {
                                    if (!layout.isAttachedToWindow) return
                                    if (--frames > 0) {
                                        layout.postOnAnimation(this)
                                        return
                                    }
                                    showHistory = true
                                    clipboardPage.recyclerView.alpha = 1f
                                    updateHistoryCount(historyItems)
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun updateHistoryCount(count: Int) {
        val text = context.getString(R.string.cherry_clipboard_count, count, ClipboardHelper.CHERRY_CAPACITY)
        val exceeded = count > ClipboardHelper.CHERRY_CAPACITY
        historyCount.text = if (exceeded) SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.RED), 0, count.toString().length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            text
        }
        historyCount.setOnClickListener(if (exceeded) View.OnClickListener {
            service.showDialog(AlertDialog.Builder(context)
                .setMessage(R.string.cherry_clipboard_over_capacity)
                .setPositiveButton(R.string.cherry_clipboard_capacity_acknowledge, null)
                .create())
        } else {
            null
        })
        historyCount.isClickable = exceeded
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun clipboardPages() = category.flatMapLatest { filter ->
        if (filter == Category.ALL) return@flatMapLatest clipboardBeansPager.flow
        val matches = historyFilter(filter)
        clipboardBeansPager.flow.map { page -> page.filter(matches) }
    }

    private fun historyFilter(filter: Category): (DatabaseBean) -> Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return { bean ->
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

    override fun onDetached() {
        clipboardLayout.dismissPanelOverlay()
        clipboardLayout.dismissSegments()
        deleteConfirmation?.dismiss()
        deleteConfirmation = null
        translationPopup?.dismiss()
        translationPopup = null
        clipboardBeansAdapter.dismissPopupMenu()
        collectionBeansAdapter.dismissPopupMenu()
        clipboardBeansSubmitJob?.cancel()
        clipboardCountJob?.cancel()
        collectionBeansSubmitJob?.cancel()
    }

    override fun refreshColors() {
        if (!wasAttached) return
        clipboardLayout.titleUi.actionButton.refreshColors()
        if (::categoryLabels.isInitialized) {
            refreshCategoryLabels()
        }
        clipboardLayout.titleUi.tabLayout.reconfigureTabs { tabUi, position ->
            configureTab(tabUi, position)
        }
        clipboardBeansAdapter.refreshColors()
        collectionBeansAdapter.refreshColors()
    }

    override fun onCreateBarView(): View = clipboardLayout.titleUi.root
}
