/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.translation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.R
import splitties.dimensions.dp

@SuppressLint("ViewConstructor")
internal class InlineTranslationPanel(
    context: Context,
    private val onClose: () -> Unit,
    private val onResult: (String) -> Unit,
) : LinearLayout(context) {
    private var reverse = false
    private var sourceConnection: InputConnection? = null
    private val request = TranslationRequest(this)

    private val edit = EditText(context).apply {
        setSingleLine(true)
        showSoftInputOnFocus = false
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setHint(R.string.cherry_translation_hint)
        textSize = 14f
        setTextColor(0xFFE0E0E0.toInt())
        setHintTextColor(0xFFB1B1B1.toInt())
        setPadding(context.dp(8), 0, context.dp(8), 0)
        background = null
    }

    private val direction = control(R.string.cherry_translation_zh_ja) {
        request.cancel()
        reverse = !reverse
        directionLabel()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(13), 0, context.dp(10), 0)
        setBackgroundColor(0xFF4A4A4A.toInt())
        val input = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF5D5D5D.toInt())
                cornerRadius = context.dp(16).toFloat()
                setStroke(context.dp(1), 0xFF747474.toInt())
            }
            addView(direction, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(edit, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(input, LayoutParams(0, context.dp(30), 1f))
        addView(control(R.string.cherry_translation_send, ::translate))
        addView(control(R.string.cherry_translation_close, onClose))
        visibility = GONE
    }

    private fun control(label: Int, click: () -> Unit) = TextView(context).apply {
        setText(label)
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(0xFFBEBEBE.toInt())
        val padding = context.dp(9)
        setPadding(padding, 0, padding, 0)
        setOnClickListener { click() }
    }

    private fun directionLabel() {
        direction.setText(if (reverse) R.string.cherry_translation_ja_zh else R.string.cherry_translation_zh_ja)
    }

    val inputConnection: InputConnection?
        get() = sourceConnection

    fun open() {
        val connection = edit.onCreateInputConnection(EditorInfo()) ?: return
        visibility = VISIBLE
        sourceConnection = object : InputConnectionWrapper(connection, false) {
            override fun performEditorAction(editorAction: Int): Boolean {
                translate()
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_ENTER) return super.sendKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP) translate()
                return true
            }
        }
    }

    fun close() {
        visibility = GONE
        sourceConnection = null
        request.cancel()
        edit.text.clear()
    }

    private fun translate() {
        request.start(edit.text.toString().trim(), reverse) { text ->
            if (!text.isNullOrBlank()) onResult(text)
        }
    }
}
