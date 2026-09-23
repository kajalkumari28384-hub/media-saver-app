package com.mediasaver.app

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(20, 60, 20, 20)

        val tabsRow = LinearLayout(this)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        val platforms = listOf("All", "youtube", "instagram", "pinterest", "twitter")
        for (p in platforms) {
            val b = Button(this)
            b.text = if (p == "All") "All" else p.replaceFirstChar { it.uppercase() }
            b.setOnClickListener { showHistory(p) }
            tabsRow.addView(b)
        }
        root.addView(tabsRow)

        listView = ListView(this)
        root.addView(listView)
        setContentView(root)

        showHistory("All")
    }

    private fun showHistory(filterPlatform: String) {
        val prefs = getSharedPreferences("history", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val items = mutableListOf<String>()
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            val platform = obj.optString("platform", "unknown")
            if (filterPlatform == "All" || platform == filterPlatform) {
                items.add("[$platform] ${obj.optString("filename")}\n${obj.optString("time")}")
            }
        }
        if (items.isEmpty()) items.add("No downloads yet")
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }
}
