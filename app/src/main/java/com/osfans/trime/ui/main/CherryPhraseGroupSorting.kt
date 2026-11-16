/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ui.main

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.CherryPhraseGroups
import splitties.dimensions.dp
import kotlin.math.roundToInt

internal class CherryPhraseGroupSorting(
    context: Context,
    groups: List<CherryPhraseGroups.Group>,
    private val artwork: (String) -> Drawable?,
    onBack: () -> Unit,
    onDone: (List<CherryPhraseGroups.Group>) -> Unit,
    private val delete: (List<String>, () -> Unit) -> Unit,
) : LinearLayout(context) {
    private val draft = groups.toMutableList()
    private val checked = mutableSetOf<String>()
    private val accent = 0xFF008CFF.toInt()
    private val all = CheckBox(context)
    private val remove = TextView(context)
    private val done = TextView(context)
    private var deleting = false
    private val headerArtwork = artwork("clipboard-group-sort-header")
    private val disabledDeleteArtwork = artwork("clipboard-group-sort-delete-idle")
    private val gesture: ItemTouchHelper
    private val rows: RecyclerView.Adapter<GroupRow>

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        addView(FrameLayout(context).apply {
            background = headerArtwork
            addView(TextView(context).apply {
                setText(R.string.cherry_library_sort_groups)
                textSize = 16f
                setTextColor(if (headerArtwork == null) Color.BLACK else Color.TRANSPARENT)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(context).apply {
                text = if (headerArtwork == null) "‹" else ""
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                contentDescription = context.getText(android.R.string.cancel)
                setOnClickListener { if (!deleting) onBack() }
            }, FrameLayout.LayoutParams(dp(42), dp(52)))
            done.apply {
                setText(R.string.cherry_library_sort_done)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (headerArtwork == null) accent else Color.TRANSPARENT)
                setOnClickListener { if (!deleting) onDone(draft.toList()) }
            }
            addView(done, FrameLayout.LayoutParams(dp(58), dp(52), Gravity.END))
        }, LayoutParams(-1, if (headerArtwork == null) dp(52) else dp(505f / 3f).roundToInt()))
        if (headerArtwork == null) addView(TextView(context).apply {
            setText(R.string.cherry_library_sort_hint)
            textSize = 12f
            setTextColor(0xFFB9BDC5.toInt())
            gravity = Gravity.CENTER
        }, LayoutParams(-1, dp(30)))
        val fixedRows = if (headerArtwork == null) listOf(R.string.cherry_library_favorites_folder, R.string.cherry_library_default)
        else listOf(R.string.cherry_library_default)
        for (label in fixedRows) {
            addView(TextView(context).apply {
                setText(label)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                val reference = if (label == R.string.cherry_library_default) artwork("clipboard-group-sort-default") else null
                setTextColor(if (reference == null) 0xFF555555.toInt() else Color.TRANSPARENT)
                background = reference ?: rowBackground()
            }, LayoutParams(-1, dp(if (label == R.string.cherry_library_favorites_folder) 70 else 48)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(12)
            })
        }

        rows = object : RecyclerView.Adapter<GroupRow>() {
            override fun getItemCount() = draft.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GroupRow()
            override fun onBindViewHolder(holder: GroupRow, position: Int) = holder.bind(draft[position])
        }
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = rows
            setPadding(dp(16), 0, dp(16), 0)
            clipToPadding = false
        }
        gesture = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (deleting) return false
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in draft.indices || to !in draft.indices) return false
                draft.add(to, draft.removeAt(from))
                rows.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        gesture.attachToRecyclerView(recycler)
        addView(recycler, LayoutParams(-1, 0, 1f))
        addView(FrameLayout(context).apply {
            val footer = artwork("clipboard-group-sort-footer")
            background = footer
            all.apply {
                if (footer != null) buttonDrawable = null
                setText(R.string.cherry_clipboard_select_all)
                setTextColor(if (footer == null) Color.BLACK else Color.TRANSPARENT)
                setOnClickListener {
                    checked.clear()
                    if (isChecked) draft.forEach { checked.add(it.id) }
                    rows.notifyDataSetChanged()
                    updateFooter()
                }
            }
            addView(all, FrameLayout.LayoutParams(dp(96), -1))
            remove.apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 14f
                background = actionBackground()
                setOnClickListener { confirmDeletion() }
            }
            addView(remove, FrameLayout.LayoutParams(dp(94), dp(32), Gravity.TOP or Gravity.END).apply {
                marginEnd = dp(16)
                topMargin = dp(18)
            })
        }, LayoutParams(-1, dp(68)))
        updateFooter()
    }

    private fun updateSelectionArtwork(view: CheckBox, surface: String) {
        val state = if (view.isChecked) "active" else "idle"
        val image = artwork("clipboard-group-sort-$surface-$state") ?: return
        view.buttonDrawable = null
        view.background = image
    }

    private fun rowBackground() = GradientDrawable().apply {
        cornerRadius = dp(8f)
        setColor(Color.WHITE)
        setStroke(dp(1f / 3f).toInt().coerceAtLeast(1), 0xFFEDEDED.toInt())
    }

    private fun actionBackground() = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFF4C6FFF.toInt(), 0xFF66B2FF.toInt())).apply { cornerRadius = dp(22f) }

    private fun updateFooter() {
        all.isEnabled = draft.isNotEmpty() && !deleting
        all.isChecked = draft.isNotEmpty() && checked.size == draft.size
        done.isEnabled = !deleting
        remove.isEnabled = checked.isNotEmpty() && !deleting
        updateSelectionArtwork(all, "select-all")
        val idle = checked.isEmpty() && disabledDeleteArtwork != null
        remove.background = if (idle) disabledDeleteArtwork else actionBackground()
        remove.setTextColor(if (idle) Color.TRANSPARENT else Color.WHITE)
        remove.alpha = if (idle || remove.isEnabled) 1f else 0.32f
        remove.text = context.getString(R.string.cherry_library_delete_groups, checked.size)
    }

    private fun confirmDeletion() {
        val selected = checked.toList()
        val dialog = Dialog(context).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(26), dp(40), dp(26), dp(27))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16f) }
        }
        content.addView(TextView(context).apply {
            text = context.getString(R.string.cherry_library_confirm_delete_groups, selected.size)
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LayoutParams(-1, dp(70)))
        content.addView(LinearLayout(context).apply {
            for (positive in listOf(false, true)) {
                addView(TextView(context).apply {
                    setText(if (positive) R.string.cherry_phrase_confirm else R.string.cancel)
                    textSize = 15f
                    setTextColor(if (positive) Color.WHITE else Color.BLACK)
                    gravity = Gravity.CENTER
                    background = if (positive) actionBackground() else GradientDrawable().apply {
                        cornerRadius = dp(22f)
                        setColor(0xFFF3F3F3.toInt())
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        if (positive) {
                            deleting = true
                            updateFooter()
                            delete(selected) {
                                draft.removeAll { it.id in selected }
                                checked.clear()
                                deleting = false
                                rows.notifyDataSetChanged()
                                updateFooter()
                            }
                        }
                    }
                }, LayoutParams(0, -1, 1f).apply { if (!positive) marginEnd = dp(10) })
            }
        }, LayoutParams(-1, dp(44)).apply { topMargin = dp(18) })
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.apply {
            setLayout(dp(302), -2)
            setDimAmount(0.5f)
        }
    }

    private inner class GroupRow : RecyclerView.ViewHolder(LinearLayout(context)) {
        private val selector = CheckBox(context)
        private val title = TextView(context)
        private val handle = TextView(context)

        init {
            val root = itemView as LinearLayout
            root.gravity = Gravity.CENTER_VERTICAL
            root.background = artwork("clipboard-group-sort-row") ?: rowBackground()
            root.layoutParams = RecyclerView.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) }
            root.addView(selector, LayoutParams(dp(42), -1))
            title.textSize = 14f
            title.setTextColor(Color.BLACK)
            root.addView(title, LayoutParams(0, -2, 1f))
            val handleArtwork = artwork("clipboard-group-sort-handle")
            handle.background = handleArtwork
            handle.text = if (handleArtwork == null) "≡" else ""
            handle.textSize = 22f
            handle.setTextColor(0xFFB9BDC5.toInt())
            handle.gravity = Gravity.CENTER
            handle.contentDescription = context.getText(R.string.cherry_library_sort_groups)
            root.addView(handle, LayoutParams(dp(42), -1))
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !deleting) {
                    gesture.startDrag(this)
                    true
                } else false
            }
        }

        fun bind(group: CherryPhraseGroups.Group) {
            title.text = group.title
            selector.isChecked = group.id in checked
            updateSelectionArtwork(selector, "row-check")
            selector.setOnClickListener {
                if (selector.isChecked) checked.add(group.id) else checked.remove(group.id)
                updateSelectionArtwork(selector, "row-check")
                updateFooter()
            }
        }
    }
}
