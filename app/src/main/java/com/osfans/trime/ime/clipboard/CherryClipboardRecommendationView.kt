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
    private val paste: (String) -> Unit,
) : LinearLayout(context) {
    data class Category(val title: String, val phrases: List<String>)

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
    private var selected = 0
    private var expanded = -1

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF363636.toInt())
        addView(tabsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(36f + 2f / 3).roundToInt()))
        addView(cardsScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        renderTabs()
        renderCards()
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
        categories[selected].phrases.forEachIndexed { index, phrase ->
            val open = expanded == index
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                background = GradientDrawable().apply {
                    setColor(0xFF434343.toInt())
                    cornerRadius = dp(4f)
                    setStroke(dp(1f / 3).roundToInt().coerceAtLeast(1), 0xFF5A5A5A.toInt())
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
                    setOnClickListener { paste(phrase) }
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

    companion object {
        fun loadProfile(): List<Category> {
            val path = DataManager.userDataDir.resolve("clipboard_recommendations.json")
            if (!path.isFile) return emptyList()
            return runCatching {
                val data = JSONArray(path.readText())
                (0 until data.length()).mapNotNull { index ->
                    val group = data.getJSONObject(index)
                    val name = group.getString("title")
                    val entries = group.getJSONArray("phrases")
                    val phrases = (0 until entries.length()).map(entries::getString).filter(String::isNotBlank)
                    if (name.isNotBlank() && phrases.isNotEmpty()) Category(name, phrases) else null
                }
            }.getOrDefault(emptyList())
        }
    }
}
