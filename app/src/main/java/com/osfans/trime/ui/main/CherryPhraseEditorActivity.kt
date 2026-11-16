/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.osfans.trime.R
import com.osfans.trime.data.db.CherryPhraseGroups
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.dimensions.dp

class CherryPhraseEditorActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val group = intent.getStringExtra(GROUP)
        if (group != null && CherryPhraseGroups.all(this).none { it.id == group }) {
            finish()
            return
        }
        val foreground = 0xFFB3B3B3.toInt()
        val panel = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                val r = dp(16f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(0xFF363636.toInt())
            }
        }
        panel.addView(TextView(this).apply {
            setText(R.string.cherry_library_add_phrase)
            textSize = 16f
            setTextColor(foreground)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, dp(48)))
        panel.addView(TextView(this).apply {
            text = "×"
            textSize = 24f
            setTextColor(foreground)
            gravity = Gravity.CENTER
            contentDescription = getText(R.string.cancel)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(54), dp(48)))
        val body = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(0xFF434343.toInt())
            }
        }
        panel.addView(body, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(48)
            bottomMargin = dp(10)
            marginStart = dp(10)
            marginEnd = dp(10)
        })
        val input = EditText(this).apply {
            setHint(R.string.cherry_library_phrase_hint)
            setHintTextColor(0xFF797979.toInt())
            setTextColor(foreground)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), 0)
            filters = arrayOf(InputFilter.LengthFilter(3000))
            background = null
        }
        body.addView(input, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(66) })
        val count = TextView(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(0xFF939393.toInt())
            textSize = 12f
            text = "0/3000"
        }
        body.addView(count, FrameLayout.LayoutParams(dp(90), dp(26), Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(12)
            bottomMargin = dp(59)
        })
        val save = TextView(this).apply {
            setText(R.string.cherry_library_save_phrase)
            setTextColor(foreground)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(0xFF505050.toInt())
            }
            isEnabled = false
            setOnClickListener {
                val text = input.text.toString()
                if (text.isBlank()) return@setOnClickListener
                isEnabled = false
                scope.launch {
                    CollectionHelper.insert(DatabaseBean(text = text, phraseGroup = group))
                    finish()
                }
            }
        }
        input.doAfterTextChanged {
            count.text = "${it?.length ?: 0}/3000"
            save.isEnabled = !it.isNullOrBlank()
            (save.background as GradientDrawable).setColor(if (save.isEnabled) 0xFFA21E42.toInt() else 0xFF505050.toInt())
        }
        panel.addView(save, FrameLayout.LayoutParams(dp(52), dp(28), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(10)
            marginEnd = dp(12)
        })
        setContentView(panel)
        window.apply {
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(-1, dp(268))
            setDimAmount(0.5f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        input.requestFocus()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GROUP = "phrase_group"

        fun open(context: Context, group: String?) {
            context.startActivity(Intent(context, CherryPhraseEditorActivity::class.java)
                .putExtra(GROUP, group)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
