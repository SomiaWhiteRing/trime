/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp

class ClipboardBeanUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val cherry: Boolean,
    private val expandable: Boolean,
) : Ui {
    private val theme: Theme get() = scope.theme

    val textView =
        textView {
            minLines = 1
            maxLines = if (expandable) 1 else 4
            textSize = if (expandable || !cherry) 14f else 12f
            typeface = theme.fonts.key
            if (cherry) setPaddingDp(8, 8, if (expandable) 37 else 8, 8) else setPaddingDp(8, 4, 8, 4)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(scope.colors.keyTextColor)
        }

    val pin =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_baseline_push_pin_24)!!.apply {
                    setTint(scope.colors.keySymbolColor)
                    setAlpha(0.3f)
                }
        }

    private val expand = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_START
        setPadding(dp(12), dp(12), dp(12), 0)
        contentDescription = ctx.getString(R.string.cherry_clipboard_expand)
    }

    private val actions = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(ctx.dp(8), 0, ctx.dp(8), 0)
    }
    private val actionScroller = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
        addView(actions, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    private val checkbox = CheckBox(ctx).apply {
        visibility = View.GONE
        buttonTintList = null
        setButtonDrawable(R.drawable.cherry_clipboard_checkbox)
        background = null
    }

    private val layout =
        constraintLayout {
            if (cherry) minimumHeight = dp(39)
            add(
                textView,
                lParams(matchParent, wrapContent) {
                    centerVertically()
                },
            )
            add(
                pin,
                lParams(dp(12), dp(12)) {
                    bottomOfParent(dp(2))
                    endOfParent(dp(if (expandable) 31 else 2))
                },
            )
            if (expandable) {
                add(expand, lParams(dp(36), 0) {
                    endOfParent()
                    topOfParent()
                    bottomOfParent()
                })
                add(checkbox, lParams(dp(34), dp(39)) {
                    startOfParent(dp(11))
                    centerVertically()
                })
            }
        }

    override val root =
        GestureFrame(ctx).apply {
            isClickable = true
            minimumHeight = dp(if (cherry) 39 else 30)
            if (expandable) {
                add(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(layout, LinearLayout.LayoutParams(matchParent, wrapContent))
                    addView(actionScroller, LinearLayout.LayoutParams(matchParent, dp(44)).apply {
                        topMargin = dp(7)
                        bottomMargin = dp(12)
                    })
                }, lParams(matchParent, wrapContent))
            } else {
                add(layout, lParams(matchParent, matchParent))
            }
            layoutParams = lParams(matchParent, wrapContent)
        }

    fun setBean(
        text: String,
        pinned: Boolean,
    ) {
        textView.text = text
        pin.visibility = if (pinned) View.VISIBLE else View.GONE
    }

    fun bindExpansion(
        preview: String,
        content: String,
        compact: Boolean,
        expanded: Boolean,
        fullText: Boolean,
        entries: List<Triple<Int, String, () -> Unit>>,
        onViewMore: () -> Unit,
        onToggle: () -> Unit,
    ) {
        checkbox.visibility = View.GONE
        expand.visibility = View.VISIBLE
        expand.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginEnd = ctx.dp(if (compact) 8 else 0)
        }
        textView.setPaddingDp(if (compact) 20 else 12, 8, if (compact) 45 else 37, 8)
        actions.removeAllViews()
        for ((label, icon, action) in entries) {
            actions.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                scope.drawable("clipboard-action-$icon.png")?.let { artwork ->
                    addView(ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(artwork)
                        setColorFilter(0xFFB3B3B3.toInt())
                    }, LinearLayout.LayoutParams(ctx.dp(18), ctx.dp(18)).apply {
                        bottomMargin = ctx.dp(4)
                    })
                }
                addView(TextView(ctx).apply {
                    setText(label)
                    textSize = 13f
                    setSingleLine()
                    gravity = Gravity.CENTER
                    setTextColor(0xFFB3B3B3.toInt())
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = ctx.dp(38)
            })
        }
        actionScroller.visibility = if (expanded) View.VISIBLE else View.GONE
        textView.maxLines = when {
            fullText -> Int.MAX_VALUE
            expanded -> 6
            compact -> 1
            else -> 2
        }
        textView.text = if (expanded) content else preview
        textView.setOnTouchListener(null)
        if (expanded && !fullText) {
            textView.doOnLayout {
                if (textView.text == content && textView.maxLines == 6) {
                    bindViewMore(content, onViewMore)
                }
            }
        }
        expand.setImageResource(if (expanded) R.drawable.ic_cherry_clipboard_collapse else R.drawable.ic_cherry_clipboard_expand)
        expand.setOnClickListener { onToggle() }
        actionScroller.scrollTo(0, 0)
    }

    private fun bindViewMore(content: String, onViewMore: () -> Unit) {
        val width = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight
        val more = ctx.getString(R.string.cherry_clipboard_view_more)
        val suffix = "…$more"
        val available = width - textView.paint.measureText(suffix)
        if (available <= 0) return
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, textView.paint, width)
            .setMaxLines(7)
            .setBreakStrategy(textView.breakStrategy)
            .build()
        if (layout.lineCount <= 6) return
        val start = layout.getLineStart(5)
        val end = layout.getLineEnd(5)
        val lastLine = TextUtils.ellipsize(
            content.substring(start, end).trimEnd(),
            textView.paint,
            available,
            TextUtils.TruncateAt.END,
        ).toString().removeSuffix("…")
        val span = object : ClickableSpan() {
            override fun onClick(widget: View) = onViewMore()
            override fun updateDrawState(ds: android.text.TextPaint) {
                ds.color = 0xFF9D1E42.toInt()
            }
        }
        val clipped = SpannableStringBuilder(content.substring(0, start)).append(lastLine).append(suffix)
        val buttonStart = clipped.length - more.length
        clipped.setSpan(span, buttonStart, clipped.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = clipped
        textView.setOnTouchListener { _, event ->
            val current = textView.layout
            val line = current.getLineForOffset(buttonStart)
            val x = event.x - textView.totalPaddingLeft
            val y = event.y - textView.totalPaddingTop
            val hit = y >= current.getLineTop(line) && y < current.getLineBottom(line) &&
                x in current.getPrimaryHorizontal(buttonStart)..current.getPrimaryHorizontal(clipped.length)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> hit
                MotionEvent.ACTION_UP -> {
                    if (hit) span.onClick(textView)
                    true
                }
                else -> true
            }
        }
    }

    fun bindSelection(selected: Boolean, toggle: () -> Unit) {
        actionScroller.visibility = View.GONE
        expand.visibility = View.GONE
        textView.setOnTouchListener(null)
        textView.maxLines = 1
        textView.setPaddingDp(41, 8, 37, 8)
        checkbox.visibility = View.VISIBLE
        checkbox.isChecked = selected
        checkbox.setOnClickListener { toggle() }
        root.setOnClickListener { toggle() }
        root.setOnLongClickListener(null)
    }

    /** Restyles the row after a scheme switch. */
    fun refreshColors(compact: Boolean = false) {
        textView.setTextColor(if (cherry) 0xFFB3B3B3.toInt() else scope.colors.keyTextColor)
        pin.drawable?.setTint(scope.colors.keySymbolColor)
        root.background = if (compact) {
            null
        } else if (cherry) {
            GradientDrawable().apply {
                setColor(0xFF434343.toInt())
                setStroke(ctx.dp(1f / 3f).toInt().coerceAtLeast(1), 0xFF6E6E6E.toInt())
                cornerRadius = ctx.dp(4f)
            }
        } else {
            scope.decorDrawable(
                "key_back_color",
                "key_border_color",
                ctx.dp(theme.style.keyBorder),
                ctx.dp(theme.style.roundCorner),
            )
        }
    }
}
