/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ime.translation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.view.View
import dev.davidv.translator.ITranslationCallback
import dev.davidv.translator.ITranslationService
import dev.davidv.translator.TranslationError

internal class TranslationRequest(private val view: View) {
    private var pending: ServiceConnection? = null

    fun cancel() {
        val request = pending ?: return
        pending = null
        view.context.unbindService(request)
    }

    fun start(source: String, reverse: Boolean, onResult: (String?) -> Unit) {
        if (pending != null || source.isBlank()) return
        val from = if (reverse) "ja" else "zh"
        val to = if (reverse) "zh" else "ja"
        val request = object : ServiceConnection {
            private fun finish(text: String?) {
                view.post {
                    if (pending === this) {
                        cancel()
                        onResult(text)
                    }
                }
            }

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val callback = object : ITranslationCallback.Stub() {
                    override fun onTranslationResult(translatedText: String?) = finish(translatedText)
                    override fun onTranslationError(error: TranslationError?) = finish(null)
                }
                try {
                    ITranslationService.Stub.asInterface(binder).translate(source, from, to, callback)
                } catch (_: RemoteException) {
                    finish(null)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = finish(null)
            override fun onBindingDied(name: ComponentName) = finish(null)
            override fun onNullBinding(name: ComponentName) = finish(null)
        }
        val intent = Intent("dev.davidv.translator.ITranslationService").setComponent(
            ComponentName("dev.davidv.translator", "dev.davidv.translator.AidlTranslationService"),
        )
        if (view.context.bindService(intent, request, Context.BIND_AUTO_CREATE)) {
            pending = request
        } else {
            onResult(null)
        }
    }
}
