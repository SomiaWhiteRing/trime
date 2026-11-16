/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.theme.ThemeScope
import org.json.JSONArray
import splitties.dimensions.dp
import kotlin.math.roundToInt

internal class CherryClipboardRecommendationView(
    context: Context,
    private val scope: ThemeScope,
    private val categories: List<Category>,
    initialMode: CherrySendMode,
    private val stage: (String) -> (() -> Boolean)?,
    private val send: () -> Boolean,
    private val paste: (String) -> Unit,
) : LinearLayout(context) {
    data class Group(val title: String, val phrases: List<String>)
    data class Category(val title: String, val phrases: List<String>, val groups: List<Group>)

    private val foregroundColor = 0xFFB3B3B3.toInt()
    private val accentColor = 0xFFA21E42.toInt()
    private val tabs = LinearLayout(context).apply { setPadding(dp(17f + 1f / 3).roundToInt(), 0, dp(12), 0) }
    private val tabsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(tabs, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val cards = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(8), 0, dp(8), dp(4))
    }
    private val cardsScroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(cards, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private var mode = initialMode
    private var selected = 0
    private var expanded = -1
    private var sequenceIndex = 0
    private var undoStagedPhrase: (() -> Boolean)? = null
    private val singleSendArtwork = scope.drawable("clipboard-recommend-send-plane.png")
    private val stagedSendArtwork = scope.drawable("clipboard-recommend-staged-plane.png")
    private val sendButton = FrameLayout(context).apply {
        contentDescription = context.getString(R.string.cherry_send_action)
        if (singleSendArtwork != null) {
            background = singleSendArtwork
        } else {
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(20f)
            }
            addView(TextView(context).apply {
                text = "➤"
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(0xFF363636.toInt())
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        setOnClickListener { sendNext() }
    }
    private val revokeButton = TextView(context).apply {
        val artwork = scope.drawable("clipboard-recommend-revoke.png")
        if (artwork == null) {
            text = "↶"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFF363636.toInt())
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(20f)
            }
        } else {
            background = artwork
        }
        contentDescription = context.getString(R.string.cherry_send_revoke)
        setOnClickListener {
            val undo = undoStagedPhrase ?: return@setOnClickListener
            if (undo()) {
                undoStagedPhrase = null
                updateSendControls()
            }
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF363636.toInt())
        addView(tabsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(36f + 2f / 3).roundToInt()))
        addView(FrameLayout(context).apply {
            addView(cardsScroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(sendButton, FrameLayout.LayoutParams(dp(55), dp(40), Gravity.END or Gravity.BOTTOM).apply {
                bottomMargin = dp(29)
            })
            addView(revokeButton, FrameLayout.LayoutParams(dp(44), dp(40), Gravity.END or Gravity.BOTTOM).apply {
                bottomMargin = dp(29)
                marginEnd = dp(55)
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        renderTabs()
        renderCards()
    }

    fun updateMode(next: CherrySendMode) {
        if (mode == next) return
        mode = next
        expanded = -1
        sequenceIndex = 0
        undoStagedPhrase = null
        renderCards()
        cardsScroll.scrollTo(0, 0)
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        categories.forEachIndexed { index, group ->
            val active = index == selected
            val tab = LinearLayout(context).apply {
                orientation = VERTICAL
                val text = TextView(context).apply {
                    setText(group.title)
                    textSize = 14f
                    typeface = scope.theme.fonts.candidate
                    setTextColor(if (active) accentColor else foregroundColor)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    if (!active && group.title.length > 5) maxWidth = dp(65)
                }
                addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, dp(29)))
                if (active) {
                    addView(
                        View(context).apply { setBackgroundColor(accentColor) },
                        LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(1) },
                    )
                }
                isSelected = active
                setOnClickListener {
                    if (selected == index) return@setOnClickListener
                    selected = index
                    expanded = -1
                    sequenceIndex = 0
                    undoStagedPhrase = null
                    renderTabs()
                    renderCards()
                    cardsScroll.scrollTo(0, 0)
                    tabsScroll.post {
                        val target = tabs.getChildAt(selected)
                        val visibleRight = tabsScroll.scrollX + tabsScroll.width - dp(12)
                        if (target.right > visibleRight) tabsScroll.smoothScrollTo(target.right - tabsScroll.width + dp(12), 0)
                        if (target.left < tabsScroll.scrollX) tabsScroll.smoothScrollTo(target.left - dp(12), 0)
                    }
                }
            }
            tabs.addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginEnd = dp(17) })
        }
    }

    private fun renderCards() {
        cards.removeAllViews()
        val category = categories[selected]
        updateSendControls()
        if (mode == CherrySendMode.RANDOM) {
            renderRandomGroups(category.groups)
            return
        }
        category.phrases.forEachIndexed { index, phrase ->
            val open = expanded == index
            val current = mode == CherrySendMode.SEQUENTIAL && index == sequenceIndex
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (current) 0xFF363636.toInt() else 0xFF434343.toInt())
                    cornerRadius = dp(4f)
                    setStroke(dp(1f / 3).roundToInt().coerceAtLeast(1), if (current) accentColor else 0xFF5A5A5A.toInt())
                }
            }
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(TextView(context).apply {
                    text = phrase
                    textSize = 14f
                    typeface = scope.theme.fonts.candidate
                    setTextColor(foregroundColor)
                    maxLines = if (open) Int.MAX_VALUE else 1
                    if (!open) ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener {
                        if (mode != CherrySendMode.SEQUENTIAL) {
                            paste(phrase)
                        } else {
                            val undo = stage(phrase) ?: return@setOnClickListener
                            sequenceIndex = index
                            undoStagedPhrase = undo
                            redrawAtCurrentScroll()
                        }
                    }
                    minHeight = dp(39)
                    gravity = Gravity.CENTER_VERTICAL
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = if (open) "⌃" else "⌄"
                    textSize = 16f
                    setTextColor(foregroundColor)
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        expanded = if (open) -1 else index
                        val scrollY = cardsScroll.scrollY
                        renderCards()
                        cardsScroll.post { cardsScroll.scrollTo(0, scrollY) }
                    }
                }, LayoutParams(dp(20), dp(39)))
            }
            card.addView(row)
            if (open) {
                val collect = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    scope.drawable("clipboard-action-collect.png")?.let { image ->
                        addView(ImageView(context).apply {
                            setImageDrawable(image)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }, LayoutParams(dp(19), dp(19)))
                    }
                    addView(TextView(context).apply {
                        setText(R.string.collect)
                        textSize = 13f
                        setTextColor(foregroundColor)
                    })
                    setOnClickListener { CollectionHelper.addNewBean(phrase) }
                }
                card.addView(collect, LayoutParams(LayoutParams.MATCH_PARENT, dp(64)))
            }
            cards.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4f + 1f / 3).roundToInt()
            })
        }
    }

    private fun updateSendControls() {
        val show = mode == CherrySendMode.SEQUENTIAL && sequenceIndex in categories[selected].phrases.indices
        sendButton.visibility = if (show) View.VISIBLE else View.GONE
        revokeButton.visibility = if (show && undoStagedPhrase != null) View.VISIBLE else View.GONE
        if (singleSendArtwork != null) {
            sendButton.background = if (undoStagedPhrase == null) singleSendArtwork else stagedSendArtwork ?: singleSendArtwork
        }
    }

    private fun sendNext() {
        val phrase = categories[selected].phrases.getOrNull(sequenceIndex) ?: return
        if (undoStagedPhrase == null) {
            undoStagedPhrase = stage(phrase) ?: return
        }
        if (!send()) {
            updateSendControls()
            return
        }
        undoStagedPhrase = null
        sequenceIndex++
        redrawAtCurrentScroll()
        cardsScroll.post {
            cards.getChildAt(sequenceIndex)?.requestRectangleOnScreen(android.graphics.Rect(
                0, 0, cards.width, dp(43),
            ))
        }
    }

    private fun redrawAtCurrentScroll() {
        val scrollY = cardsScroll.scrollY
        renderCards()
        cardsScroll.post { cardsScroll.scrollTo(0, scrollY) }
    }

    private fun renderRandomGroups(groups: List<Group>) {
        for (pair in groups.chunked(2)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for ((index, group) in pair.withIndex()) {
                val card = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(15), 0, dp(14), 0)
                    background = GradientDrawable().apply {
                        setColor(0xFF434343.toInt())
                        cornerRadius = dp(4f)
                        setStroke(dp(1f / 3).roundToInt().coerceAtLeast(1), 0xFF5A5A5A.toInt())
                    }
                    addView(TextView(context).apply {
                        text = group.title
                        textSize = 14f
                        setTextColor(foregroundColor)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = group.phrases.size.toString()
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(0xFFA0A0A0.toInt())
                    })
                    setOnClickListener { paste(group.phrases.random()) }
                }
                row.addView(card, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index == 0) marginEnd = dp(6)
                })
            }
            if (pair.size == 1) row.addView(View(context), LayoutParams(0, 1, 1f))
            cards.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(43f + 1f / 3).roundToInt()).apply {
                bottomMargin = dp(4)
            })
        }
    }

    companion object {
        private fun readPhrases(entries: JSONArray) =
            (0 until entries.length()).map(entries::getString).filter(String::isNotBlank)

        fun loadProfile(): List<Category> {
            val path = DataManager.userDataDir.resolve("clipboard_recommendations.json")
            if (!path.isFile) return emptyList()
            return runCatching {
                val data = JSONArray(path.readText())
                (0 until data.length()).mapNotNull { index ->
                    val group = data.getJSONObject(index)
                    val name = group.getString("title")
                    val groups = group.optJSONArray("groups")?.let { entries ->
                        (0 until entries.length()).mapNotNull { i ->
                            val entry = entries.getJSONObject(i)
                            val title = entry.getString("title")
                            val phrases = readPhrases(entry.getJSONArray("phrases"))
                            if (title.isNotBlank() && phrases.isNotEmpty()) Group(title, phrases) else null
                        }
                    }.orEmpty()
                    val phrases = group.optJSONArray("phrases")?.let(::readPhrases) ?: groups.flatMap { it.phrases }
                    if (name.isNotBlank() && phrases.isNotEmpty()) {
                        Category(name, phrases, groups.ifEmpty { listOf(Group(name, phrases)) })
                    } else null
                }
            }.getOrDefault(emptyList())
        }
    }
}
