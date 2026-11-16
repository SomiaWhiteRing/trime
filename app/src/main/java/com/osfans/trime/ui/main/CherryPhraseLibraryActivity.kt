/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ui.main

import android.app.Activity
import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.osfans.trime.R
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.db.CherryPhraseGroups
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import splitties.dimensions.dp

class CherryPhraseLibraryActivity : Activity() {
    private val scope = MainScope()
    private val groups by lazy { CherryPhraseGroups.all(this).toMutableList() }
    private lateinit var tabs: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var library: LinearLayout
    private var management: LinearLayout? = null
    private var selected = 1
    private var phrases = emptyList<DatabaseBean>()

    private fun artwork(name: String): BitmapDrawable? {
        val path = DataManager.userDataDir.resolve("backgrounds/baidu_cherry/$name.png")
        return BitmapFactory.decodeFile(path.path)?.let { BitmapDrawable(resources, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(CherryPhraseEditorActivity.GROUP)?.let { group ->
            val index = groups.indexOfFirst { it.id == group }
            if (index >= 0) selected = index + 2
        }
        window.statusBarColor = 0xFFDEEDFF.toInt()
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        library = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = FrameLayout(this).apply {
            val reference = artwork("clipboard-phrase-library-header")
            background = reference ?: GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFDEEDFF.toInt(), Color.WHITE),
            )
            addView(TextView(context).apply {
                text = if (reference == null) "‹" else ""
                textSize = 32f
                gravity = Gravity.CENTER
                contentDescription = context.getText(android.R.string.cancel)
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(dp(48), dp(56)))
            if (reference == null) {
                addView(TextView(context).apply {
                    setText(R.string.cherry_clipboard_phrases)
                    textSize = 18f
                }, FrameLayout.LayoutParams(dp(90), dp(48)).apply {
                    marginStart = dp(128)
                    topMargin = dp(68)
                })
            }
        }
        library.addView(header, LinearLayout.LayoutParams(-1, dp(176)))
        val controls = FrameLayout(this)
        tabs = LinearLayout(this)
        controls.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }, FrameLayout.LayoutParams(-1, -1).apply { marginEnd = dp(112) })
        controls.addView(TextView(this).apply {
            text = "⊕"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF7B8492.toInt())
            contentDescription = context.getText(R.string.cherry_library_add_group)
            setOnClickListener { addGroup() }
        }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END or Gravity.BOTTOM).apply { marginEnd = dp(28) })
        controls.addView(TextView(this).apply {
            val image = artwork("clipboard-phrase-library-gear")
            background = image
            text = if (image == null) "⚙" else ""
            gravity = Gravity.CENTER
            contentDescription = getText(R.string.cherry_library_group_settings)
            setOnClickListener { showGroupSettings() }
        }, FrameLayout.LayoutParams(dp(44), dp(56), Gravity.END or Gravity.BOTTOM))
        library.addView(controls, LinearLayout.LayoutParams(-1, dp(84)))
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, 0, 0) }
        library.addView(ScrollView(this).apply { addView(rows) }, LinearLayout.LayoutParams(-1, 0, 1f))
        library.addView(TextView(this).apply {
            setText(R.string.cherry_library_add_phrase)
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF4C6FFF.toInt(), 0xFF66B2FF.toInt())).apply { cornerRadius = dp(24f) }
            setOnClickListener { CherryPhraseEditorActivity.open(this@CherryPhraseLibraryActivity, groups.getOrNull(selected - 2)?.id) }
        }, LinearLayout.LayoutParams(-1, dp(49)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            topMargin = dp(8)
            bottomMargin = dp(12)
        })
        setContentView(library)
        refreshTabs()
        scope.launch {
            CollectionHelper.observeRecords().collect { records ->
                phrases = records
                refreshRows()
            }
        }
    }

    private fun refreshTabs() {
        tabs.removeAllViews()
        val titles = listOf(getString(R.string.collect), getString(R.string.cherry_library_default)) + groups.map { it.title }
        for ((index, title) in titles.withIndex()) {
            tabs.addView(FrameLayout(this).apply {
                addView(TextView(context).apply {
                    text = title
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(dp(12), 0, dp(12), 0)
                    setTextColor(Color.BLACK)
                }, FrameLayout.LayoutParams(-2, dp(38), Gravity.BOTTOM).apply { bottomMargin = dp(18) })
                if (selected == index) addView(View(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(2f)
                        setColor(0xFF008CFF.toInt())
                    }
                }, FrameLayout.LayoutParams(dp(26), dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(18)
                })
                isSelected = index == selected
                setOnClickListener {
                    selected = index
                    refreshTabs()
                    refreshRows()
                }
            }, LinearLayout.LayoutParams(-2, -1))
        }
    }

    private fun refreshRows() {
        rows.removeAllViews()
        val visible = when (selected) {
            0 -> phrases.filter { it.pinned }
            1 -> phrases.filter { !it.recentOnly && it.phraseGroup == null }
            else -> phrases.filter { !it.recentOnly && it.phraseGroup == groups[selected - 2].id }
        }
        for (phrase in visible) {
            rows.addView(TextView(this).apply {
                text = phrase.text
                textSize = 14f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                setOnClickListener { AppUtils.launchClipEdit(this@CherryPhraseLibraryActivity, phrase.id, ClipEditActivity.FROM_COLLECTION) }
            }, LinearLayout.LayoutParams(-1, dp(43)))
            rows.addView(View(this).apply { setBackgroundColor(0xFFECECEC.toInt()) }, LinearLayout.LayoutParams(-1, dp(1f / 3f).toInt().coerceAtLeast(1)))
        }
    }

    private fun showGroupSettings() {
        val screen = management ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }.also {
            management = it
            setContentView(it)
        }
        screen.removeAllViews()
        screen.addView(FrameLayout(this).apply {
            addView(TextView(context).apply {
                setText(R.string.cherry_library_group_settings)
                textSize = 16f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(context).apply {
                text = "‹"
                textSize = 28f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                contentDescription = getText(android.R.string.cancel)
                setOnClickListener { closeGroupSettings() }
            }, FrameLayout.LayoutParams(dp(42), -1))
        }, LinearLayout.LayoutParams(-1, dp(58)))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val names = listOf(getString(R.string.cherry_library_favorites_folder), getString(R.string.cherry_library_default)) +
            groups.map { it.title }
        for ((index, title) in names.withIndex()) {
            list.addView(TextView(this).apply {
                text = title
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                textSize = 14f
                setTextColor(if (index == 0) 0xFF7D7D7D.toInt() else Color.BLACK)
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f)
                    setColor(Color.WHITE)
                    setStroke(dp(1f / 3f).toInt().coerceAtLeast(1), 0xFFEDEDED.toInt())
                }
                if (index >= 2) setOnClickListener { addGroup(index - 2) }
            }, LinearLayout.LayoutParams(-1, dp(if (index == 0) 70 else 48)).apply { bottomMargin = dp(13) })
        }
        screen.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        screen.addView(TextView(this).apply {
            setText(R.string.cherry_library_create_group)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF4C6FFF.toInt(), 0xFF66B2FF.toInt())).apply { cornerRadius = dp(24f) }
            setOnClickListener { addGroup() }
        }, LinearLayout.LayoutParams(-1, dp(45)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(12)
        })
    }

    private fun closeGroupSettings() {
        management = null
        setContentView(library)
    }

    @Deprecated("Android invokes this on older versions")
    override fun onBackPressed() {
        if (management != null) closeGroupSettings() else super.onBackPressed()
    }

    private fun addGroup(index: Int? = null) {
        val existing = index?.let(groups::get)
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(25), dp(14), dp(25), dp(25))
            background = GradientDrawable().apply {
                val r = dp(16f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(Color.WHITE)
            }
        }
        surface.addView(TextView(this).apply {
            setText(if (existing == null) R.string.cherry_library_add_group else R.string.cherry_library_rename_group)
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(-1, dp(44)))
        val input = EditText(this).apply {
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(6))
            setHint(R.string.cherry_library_group_hint)
            setText(existing?.title.orEmpty())
            setSelection(length())
            textSize = 14f
            setPadding(dp(14), 0, dp(57), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(22f)
                setColor(0xFFF0F0F0.toInt())
            }
        }
        val count = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFFA0A0A0.toInt())
            text = "0/6"
        }
        surface.addView(FrameLayout(this).apply {
            addView(input, FrameLayout.LayoutParams(-1, -1))
            addView(count, FrameLayout.LayoutParams(dp(46), -1, Gravity.END))
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(7) })
        val buttons = LinearLayout(this)
        fun button(label: Int, backgroundColor: Int, foreground: Int, action: () -> Unit) = TextView(this).apply {
            setText(label)
            setTextColor(foreground)
            gravity = Gravity.CENTER
            textSize = 15f
            background = GradientDrawable().apply {
                cornerRadius = dp(22f)
                setColor(backgroundColor)
            }
            setOnClickListener { action() }
            buttons.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5); marginStart = dp(5) })
        }
        button(R.string.cancel, 0xFFF3F3F3.toInt(), Color.BLACK) { dialog.dismiss() }
        val confirm = button(R.string.cherry_phrase_confirm, 0xFF4B8FFF.toInt(), Color.WHITE) {
            val title = input.text.toString().trim()
            if (existing == null) {
                groups.add(CherryPhraseGroups.Group(UUID.randomUUID().toString(), title))
                selected = groups.lastIndex + 2
            } else {
                groups[index] = existing.copy(title = title)
            }
            CherryPhraseGroups.save(this, groups)
            refreshTabs()
            refreshRows()
            dialog.dismiss()
            if (management != null) showGroupSettings()
        }
        fun updateButton() {
            val name = input.text.toString().trim()
            count.text = "${input.length()}/6"
            confirm.isEnabled = name.isNotEmpty() && groups.none { it.id != existing?.id && it.title == name } &&
                name != getString(R.string.cherry_library_default) && name != getString(R.string.collect)
            confirm.alpha = if (confirm.isEnabled) 1f else 0.32f
        }
        input.doAfterTextChanged { updateButton() }
        updateButton()
        surface.addView(buttons, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(17) })
        dialog.setContentView(surface)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -2)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
