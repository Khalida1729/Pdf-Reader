package com.example.pdfreader.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.example.pdfreader.data.*
import androidx.core.content.edit

class RecentFilesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun get(): List<RecentFile> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(RecentFile(o.getString("uri"), o.getString("name"), o.optInt("page", 0), o.optLong("time")))
            }
        }
    }

    fun put(uri: String, name: String, page: Int) {
        val items = get().filterNot { it.uri == uri }.toMutableList()
        items.add(0, RecentFile(uri, name, page, System.currentTimeMillis()))
        val arr = JSONArray()
        items.take(10).forEach {
            arr.put(JSONObject().apply {
                put("uri", it.uri); put("name", it.name); put("page", it.lastPage); put("time", it.openedAt)
            })
        }
        prefs.edit { putString("items", arr.toString()) }
    }
}
