/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view

open class PreeditUi(
    final override val ctx: Context,
    private val scope: ThemeScope,
    private val setupPreeditView: (TextView.() -> Unit)? = null,
    private val onMoveCursor: ((Int) -> Unit)? = null,
    private val compactPinyin: Boolean = false,
) : Ui {
    private val theme: Theme
        get() = scope.theme

    // Read at use time so a scheme switch re-renders with the new colors.
    private val textColor: Int get() = if (compactPinyin) 0xFFB3B3B3.toInt() else scope.colors.textColor
    private val highlightTextColor: Int get() = if (compactPinyin) textColor else scope.colors.hilitedTextColor

    val preedit =
        view(::PreeditTextView) {
            setTextColor(textColor)
            textSize = theme.preedit.foreground.fontSize
            typeface = theme.fonts.text
            setupPreeditView?.invoke(this)
            onMoveCursor = this@PreeditUi.onMoveCursor
        }

    override val root =
        object : LinearLayout(ctx) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

            init {
                orientation = HORIZONTAL
                add(preedit, lParams())
            }
        }

    private fun CompositionProto.toSpannedString() = buildSpannedString {
        if (!preedit.isNullOrEmpty()) {
            append(preedit)
            setSpan(ForegroundColorSpan(highlightTextColor), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }

    var visible = false
        private set

    /** Re-applies the text color after a scheme switch. */
    fun refreshColors() {
        preedit.setTextColor(textColor)
    }

    private fun updateTextView(
        str: CharSequence,
        visible: Boolean,
    ) = preedit.run {
        text = str
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun update(composition: CompositionProto) {
        val pinyin = composition.preedit
        fun isFormatting(char: Char) = char == ' ' || char == '\u2038'
        val display = if (compactPinyin && pinyin != null && pinyin.any(::isFormatting) &&
            pinyin.all { isFormatting(it) || it == '\'' || it in 'a'..'z' || it in 'A'..'Z' }
        ) {
            // Rime's syllable spacing and soft caret are not part of the editable spelling.
            val text = pinyin.filterNot(::isFormatting)
            fun offset(index: Int) = index - pinyin.take(index).count(::isFormatting)
            composition.copy(
                length = text.length,
                cursorPos = offset(composition.cursorPos),
                selStart = offset(composition.selStart),
                selEnd = offset(composition.selEnd),
                preedit = text,
            )
        } else {
            composition
        }
        val string = display.toSpannedString()
        val cursorPos = display.cursorPos
        val hasPreedit = composition.length > 0
        visible = hasPreedit
        if (!visible) {
            updateTextView("", false)
            return
        }
        val stringWithCursor =
            if (cursorPos == 0 || cursorPos == string.length) {
                string
            } else {
                buildSpannedString {
                    if (cursorPos > 0) append(string, 0, cursorPos)
                    append(string, cursorPos, string.length)
                }
            }
        updateTextView(stringWithCursor, true)
    }
}
