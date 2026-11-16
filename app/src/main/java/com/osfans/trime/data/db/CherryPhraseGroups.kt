/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.data.db

import android.content.Context
import org.json.JSONArray

internal object CherryPhraseGroups {
    private fun prefs(context: Context) = context.getSharedPreferences("cherry_phrase_library", Context.MODE_PRIVATE)

    fun names(context: Context): List<String> {
        val stored = JSONArray(prefs(context).getString("groups", "[]") ?: "[]")
        return List(stored.length(), stored::getString)
    }

    fun save(context: Context, names: List<String>) {
        prefs(context).edit().putString("groups", JSONArray(names).toString()).apply()
    }
}
