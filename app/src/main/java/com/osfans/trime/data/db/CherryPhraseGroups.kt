/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.data.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object CherryPhraseGroups {
    data class Group(val id: String, val title: String)
    private fun prefs(context: Context) = context.getSharedPreferences("cherry_phrase_library", Context.MODE_PRIVATE)

    fun all(context: Context): List<Group> {
        val stored = JSONArray(prefs(context).getString("groups", "[]") ?: "[]")
        return List(stored.length()) { index ->
            val group = stored.optJSONObject(index)
            if (group != null) Group(group.getString("id"), group.getString("title"))
            else stored.getString(index).let { Group(it, it) }
        }
    }

    fun save(context: Context, groups: List<Group>) {
        val stored = JSONArray()
        groups.forEach { group ->
            stored.put(JSONObject().put("id", group.id).put("title", group.title))
        }
        prefs(context).edit().putString("groups", stored.toString()).apply()
    }
}
