/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import org.json.JSONArray
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.roundToInt

internal class CherrySymbolCatalog private constructor(val groups: List<Group>) {
    data class Group(val label: String, val entries: List<String>)

    companion object {
        fun load(): CherrySymbolCatalog? {
            val file = DataManager.userDataDir.resolve("cherry_symbols.json")
            if (!file.isFile) return null
            return runCatching {
                val data = JSONArray(file.readText())
                val groups = List(data.length()) { index ->
                    val group = data.getJSONObject(index)
                    val entries = group.getJSONArray("entries")
                    Group(group.getString("label"), List(entries.length()) { entries.getString(it) })
                }
                require(groups.isNotEmpty())
                CherrySymbolCatalog(groups)
            }.getOrNull()
        }
    }
}

internal class CherryNumberWindow(
    di: DI,
    private val catalog: CherrySymbolCatalog,
) : BoardWindow.NoBarBoardWindow(di) {
    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val manager: BoardWindowManager by instance()
    private val enterKey: EnterKeyDisplayDelegate by instance()
    private val actions: CommonKeyboardActionListener by instance()

    override fun onCreateView(): View = CherryPanelLayout(context, 702).apply {
        background = scope.drawable("cherry-number-panel.png")
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        numbers.forEachIndexed { index, number ->
            val key = hitTarget(context, number) { service.commitText(number) }.withNumericPress(number)
            cell(key, 200 + 230 * (index % 3), 2 + 175 * (index / 3), 220, 175)
        }
        cell(hitTarget(context, ".") { service.commitText(".") }.withNumericPress("period"), 200, 527, 220, 175)
        cell(hitTarget(context, "0") { service.commitText("0") }.apply {
            setOnLongClickListener { key("VOICE_ASSIST"); true }
        }.withNumericPress("0"), 430, 527, 220, 175)
        cell(hitTarget(context, "符号") {
            manager.attachWindow(CherrySymbolsWindow(di, catalog) { manager.attachWindow(this@CherryNumberWindow) })
        }.withNumericPress("symbols"), 660, 527, 220, 175)
        val backspace = cherryBackspace(context) { key("BackSpace") }.withNumericPress("backspace")
        cell(backspace, 890, 2, 226, 175)
        val enter = label(context, enterKey.keyLabel, 14f) { key("Return") }.apply {
            withNumericPress("enter", scope.drawable("cherry-number-enter.png"))
            setPadding(0, 0, 0, context.dp(5))
        }
        cell(enter, 892, 177, 224, 175)
        cell(hitTarget(context, "空格") { service.commitText(" ") }.apply {
            setOnLongClickListener { key("VOICE_ASSIST"); true }
        }.withNumericPress("space"), 890, 352, 226, 175)
        cell(hitTarget(context, "返回") { manager.attachWindow(KeyboardWindow) }.withNumericPress("return"), 890, 527, 226, 175)

        val sidebar = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            background = scope.drawable("cherry-number-sidebar.png")
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                listOf("@", ":", "'", "+", "-", "*", "/", "=", "%", "￥").forEach { symbol ->
                    addView(label(context, symbol, 15f) { service.commitText(symbol) }, LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, context.dp(42),
                    ))
                }
            })
        }
        cell(sidebar, 9, 32, 177, 635)
    }

    private fun key(action: String) {
        actions.listener.onAction(scope.theme.resolveAction(action))
    }

    private fun View.withNumericPress(name: String, idle: Drawable? = null): View = apply {
        val pressed = scope.drawable("cherry-number-$name-pressed.png")
        background = if (pressed != null) StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), idle ?: ColorDrawable(Color.TRANSPARENT))
        } else idle
    }

    override fun onAttached() = Unit
    override fun onDetached() = Unit
}

internal class CherrySymbolsWindow(
    di: DI,
    private val catalog: CherrySymbolCatalog,
    private val onReturn: () -> Unit,
) : BoardWindow.NoBarBoardWindow(di) {
    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val manager: BoardWindowManager by instance()
    private val inputBar: InputBarDelegate by instance()
    private val actions: CommonKeyboardActionListener by instance()
    private val preferences = service.getSharedPreferences("cherry_symbols", Context.MODE_PRIVATE)
    private lateinit var grid: LinearLayout
    private lateinit var categoryLabels: List<TextView>
    private lateinit var previous: View
    private lateinit var next: View
    private lateinit var lock: View
    private var category = 0
    private var page = 0
    private var locked = preferences.getBoolean("locked", true)
    private var previousInputBarVisibility = View.VISIBLE
    private var previousWindowHeight = 0

    override fun onCreateView(): View = CherryPanelLayout(context, 825).apply {
        background = scope.drawable("cherry-symbol-panel.png")
        val sidebar = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            background = scope.drawable("cherry-symbol-sidebar.png")
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                categoryLabels = catalog.groups.mapIndexed { index, group ->
                    label(context, group.label, 15f) {
                        category = index
                        page = 0
                        render()
                    }.also { addView(it, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(40))) }
                }
            })
        }
        cell(sidebar, 16, 13, 169, 609)
        grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        cell(grid, 199, 9, 905, 640)
        lock = hitTarget(context, "锁定") {
            locked = !locked
            preferences.edit().putBoolean("locked", locked).apply()
            render()
        }
        previous = hitTarget(context, "上一页") {
            if (page > 0) {
                page--
                render()
            }
        }
        next = hitTarget(context, "下一页") {
            page++
            render()
        }
        cell(lock, 20, 675, 222, 121)
        cell(previous, 243, 675, 203, 121)
        cell(next, 447, 675, 214, 121)
        cell(cherryBackspace(context) {
            actions.listener.onAction(scope.theme.resolveAction("BackSpace"))
        }, 662, 675, 210, 121)
        cell(hitTarget(context, "返回", onReturn), 873, 675, 226, 121)
        render()
    }

    private fun recent(): List<String> {
        val raw = JSONArray(preferences.getString("recent", "[]"))
        return (List(raw.length()) { raw.getString(it) } + catalog.groups.first().entries).distinct().take(31)
    }

    private fun render() {
        categoryLabels.forEachIndexed { index, label ->
            label.isSelected = index == category
            label.background = if (label.isSelected) GradientDrawable().apply {
                setColor(0xFF141414.toInt())
                cornerRadius = context.dp(4).toFloat()
            } else null
        }
        val entries = if (category == 0) recent() else catalog.groups[category].entries
        val rows = mutableListOf<MutableList<Pair<String, Int>>>()
        var used = 4
        entries.forEachIndexed { index, symbol ->
            val span = if (category == 3 || symbol.length >= 8 || category == 4 && index >= 12) 2 else 1
            if (used + span > 4) {
                rows.add(mutableListOf())
                used = 0
            }
            rows.last().add(symbol to span)
            used += span
        }
        val visibleRows = rows.drop(page * 4).take(4)
        grid.removeAllViews()
        repeat(4) { row ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val items = visibleRows.getOrNull(row).orEmpty()
                items.forEach { (symbol, span) ->
                    val button = label(context, symbol, 18f) {
                        service.commitText(symbol)
                        val updated = (listOf(symbol) + recent()).distinct().take(31)
                        preferences.edit().putString("recent", JSONArray(updated).toString()).apply()
                        if (locked) {
                            if (category == 0) render()
                        } else {
                            onReturn()
                        }
                    }
                    addView(button, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, span.toFloat()))
                }
                val freeSpans = 4 - items.sumOf { it.second }
                if (freeSpans > 0) {
                    addView(View(context), LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, freeSpans.toFloat()))
                }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        lock.isSelected = locked
        previous.isEnabled = page > 0
        next.isEnabled = (page + 1) * 4 < rows.size
    }

    override fun onAttached() {
        previousInputBarVisibility = inputBar.view.visibility
        inputBar.view.visibility = View.GONE
        val layout = manager.view.layoutParams
        previousWindowHeight = layout.height
        layout.height += context.dp(inputBar.themedHeight)
        manager.view.layoutParams = layout
    }

    override fun onDetached() {
        inputBar.view.visibility = previousInputBarVisibility
        manager.view.layoutParams = manager.view.layoutParams.apply { height = previousWindowHeight }
    }
}

private fun cherryBackspace(context: Context, click: () -> Unit) = GestureFrame(context).apply {
    contentDescription = "退格"
    isRepeatable = true
    repeatDelayMillis = 450
    repeatIntervalMillis = 175
    setOnClickListener { click() }
}

private fun hitTarget(context: Context, description: String, click: () -> Unit) = View(context).apply {
    contentDescription = description
    setOnClickListener { click() }
}

private fun label(context: Context, display: String, size: Float, click: () -> Unit) = TextView(context).apply {
    text = display
    textSize = size
    gravity = Gravity.CENTER
    setTextColor(0xFFBEBEBE.toInt())
    setOnClickListener { click() }
}

@SuppressLint("ViewConstructor")
private class CherryPanelLayout(context: Context, private val referenceHeight: Int) : ViewGroup(context) {
    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)
    private val bounds = ArrayList<Bounds>()

    fun cell(view: View, x: Int, y: Int, width: Int, height: Int) {
        bounds.add(Bounds(x, y, width, height))
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        bounds.forEachIndexed { index, cell ->
            val childWidth = (cell.width * width / 1116f).roundToInt()
            val childHeight = (cell.height * height / referenceHeight.toFloat()).roundToInt()
            getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        bounds.forEachIndexed { index, cell ->
            val child = getChildAt(index)
            val x = (cell.x * width / 1116f).roundToInt()
            val y = (cell.y * height / referenceHeight.toFloat()).roundToInt()
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }
}
