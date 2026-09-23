package com.mediasaver.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private var uris = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 60, 24, 24)

        val tabsRow = LinearLayout(this)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        val platforms = listOf("All", "youtube", "instagram", "pinterest", "twitter")
        for (p in platforms) {
            val b = Button(this)
            b.text = if (p == "All") "All" else p.replaceFirstChar { it.uppercase() }
            b.textSize = 12f
            b.setOnClickListener { showHistory(p) }
            tabsRow.addView(b)
        }
        root.addView(tabsRow)

        listView = ListView(this)
        root.addView(listView)
        setContentView(root)

        listView.setOnItemClickListener { _, _, position, _ ->
            val uriStr = uris.getOrNull(position)
            if (!uriStr.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(uriStr), "video/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) { }
            }
        }

        showHistory("All")
    }

    private fun showHistory(filterPlatform: String) {
        val prefs = getSharedPreferences("history", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val items = mutableListOf<String>()
        val uriList = mutableListOf<String>()
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            val platform = obj.optString("platform", "unknown")
            if (filterPlatform == "All" || platform == filterPlatform) {
                items.add("${obj.optString("filename")}\n$platform  •  ${obj.optString("time")}")
                uriList.add(obj.optString("uri", ""))
            }
        }
        uris = uriList
        if (items.isEmpty()) items.add("No downloads yet")
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }
}
