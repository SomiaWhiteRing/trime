/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ime.translation

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.osfans.trime.R
import splitties.dimensions.dp

internal fun showClipboardTranslation(anchor: View, source: String, insert: (String) -> Unit): PopupWindow {
    val context = anchor.context
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF353535.toInt())
    }
    val popup = PopupWindow(body, anchor.width, anchor.height, true).apply {
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
    }
    val request = TranslationRequest(body)
    var reverse = false
    val title = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF434343.toInt())
    }
    val direction = TextView(context).apply {
        setText(R.string.cherry_translation_zh_ja)
        setTextColor(0xFFB3B3B3.toInt())
        gravity = Gravity.CENTER
    }
    title.addView(TextView(context).apply {
        text = "‹"
        gravity = Gravity.CENTER
        textSize = 28f
        setTextColor(0xFFB3B3B3.toInt())
        contentDescription = context.getString(R.string.cherry_translation_close)
        setOnClickListener { popup.dismiss() }
    }, LinearLayout.LayoutParams(context.dp(37), LinearLayout.LayoutParams.MATCH_PARENT))
    title.addView(direction, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    body.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(37)))
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), 0)
    }
    body.addView(ScrollView(context).apply { addView(content) })
    content.addView(TextView(context).apply {
        setText(R.string.cherry_clipboard_original)
        textSize = 12f
        setTextColor(0xFFB3B3B3.toInt())
    })
    content.addView(TextView(context).apply {
        text = source
        textSize = 14f
        setPadding(0, context.dp(8), 0, context.dp(16))
        setTextColor(0xFFB3B3B3.toInt())
    })
    content.addView(TextView(context).apply {
        setText(R.string.cherry_clipboard_translate)
        textSize = 12f
        setTextColor(0xFFB3B3B3.toInt())
    })
    val result = TextView(context).apply {
        textSize = 14f
        setPadding(0, context.dp(8), 0, 0)
        setTextColor(0xFFB3B3B3.toInt())
    }
    content.addView(result)
    val translate = {
        result.text = ""
        result.setOnClickListener(null)
        request.start(source, reverse) { text ->
            if (text.isNullOrBlank()) {
                result.setText(R.string.cherry_clipboard_translation_empty)
            } else {
                result.text = text
                result.setOnClickListener {
                    popup.dismiss()
                    insert(text)
                }
            }
        }
    }
    direction.setOnClickListener {
        request.cancel()
        reverse = !reverse
        direction.setText(if (reverse) R.string.cherry_translation_ja_zh else R.string.cherry_translation_zh_ja)
        translate()
    }
    popup.setOnDismissListener(request::cancel)
    popup.showAsDropDown(anchor, 0, -anchor.height)
    translate()
    return popup
}
