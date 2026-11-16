/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.doOnLayout
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.horizontalPadding

class PreeditDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {

    private val context: ContextThemeWrapper by instance()
    private val scope: ThemeScope by instance()
    private val rime: RimeSession by instance()

    private val theme: Theme
        get() = scope.theme

    private val cherry: Boolean get() = "cherry_edit" in theme.presetKeyboards

    /** Applies the text-background styling; re-applied on scheme refreshes. */
    private fun setupPreeditBackground(view: View) {
        val startRadius = if (cherry) 0f else view.dp(theme.preedit.topStartRadius)
        val endRadius = if (cherry) 0f else view.dp(theme.preedit.topEndRadius)
        val radii = if (view.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            floatArrayOf(startRadius, startRadius, endRadius, endRadius, 0f, 0f, 0f, 0f)
        } else {
            floatArrayOf(endRadius, endRadius, startRadius, startRadius, 0f, 0f, 0f, 0f)
        }
        view.background = GradientDrawable().apply {
            setColor(if (cherry) 0xFF4D4D4D.toInt() else scope.colors.textBackColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
        }
        view.clipToOutline = true
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private val service: TrimeInputMethodService by instance()
    private var cherryPanel: CherryPreeditPanel? = null
    private val ui =
        PreeditUi(
            context,
            scope,
            setupPreeditView = {
                setupPreeditBackground(this)
                horizontalPadding = dp(if (cherry) 6 else theme.preedit.horizontalPadding)
                if (cherry) {
                    gravity = Gravity.CENTER_VERTICAL
                    height = dp(21)
                    setPadding(dp(6), 0, dp(10), 0)
                }
            },
            compactPinyin = cherry,
            onMoveCursor = { pos ->
                cherryPanel?.open()
                rime.launchOnReady { it.moveCursorPos(pos) }
            },
        ).apply {
            root.alpha = if (cherry) 1f else theme.preedit.alpha
            root.visibility = View.INVISIBLE
        }

    /** Restyles the text and its background after a scheme switch. */
    fun refreshColors() {
        ui.refreshColors()
        setupPreeditBackground(ui.preedit)
    }

    val view: View = if (cherry) {
        CherryPreeditPanel(context, scope, ui, { updateTouchWindow() }) { forward ->
            service.postRimeJob { stepRawInputCaret(forward) }
        }.also {
            it.visibility = ui.root.visibility
            cherryPanel = it
        }
    } else {
        ui.root
    }
    private val touchEventReceiverWindow = TouchEventReceiverWindow(view)

    private fun updateTouchWindow() {
        view.doOnLayout {
            if (view.isAttachedToWindow && view.visibility == View.VISIBLE) touchEventReceiverWindow.show()
        }
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        ui.update(data)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        view.visibility = ui.root.visibility
        if (data.length > 0) {
            cherryPanel?.updateCursor()
            updateTouchWindow()
        } else {
            cherryPanel?.close()
            touchEventReceiverWindow.dismiss()
        }
    }
}
