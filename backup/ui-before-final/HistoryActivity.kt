package com.mediasaver.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {

    private val bg = Color.rgb(250,250,249)
    private val black = Color.rgb(18,18,18)
    private val muted = Color.rgb(125,125,125)

    private lateinit var list: LinearLayout
    private var filter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        build()
        refresh()
    }

    private fun build() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(24,30,24,20)
        }

        val title = TextView(this).apply {
            text = "History"
            textSize = 30f
            setTextColor(black)
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(title)

        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val names = listOf(
            "All" to "all",
            "Instagram" to "instagram",
            "Pinterest" to "pinterest",
            "X" to "x",
            "Reddit" to "reddit"
        )

        for ((name,value) in names) {

            val tab = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(16,0,16,0)
                setTextColor(
                    if (filter == value)
                        black else muted
                )
            }

            tab.setOnClickListener {
                filter = value
                refresh()

                tab.animate()
                    .alpha(.5f)
                    .setDuration(80)
                    .withEndAction {
                        tab.animate()
                            .alpha(1f)
                            .setDuration(120)
                            .start()
                    }
                    .start()
            }

            tabs.addView(
                tab,
                LinearLayout.LayoutParams(
                    -2, 48
                )
            )
        }

        tabsScroll.addView(tabs)

        root.addView(
            tabsScroll,
            LinearLayout.LayoutParams(-1,58)
        )

        val scroll = ScrollView(this)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1,0,1f)
        )

        val back = TextView(this).apply {
            text = "←  Home"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        back.setOnClickListener {
            finish()
        }

        root.addView(
            back,
            LinearLayout.LayoutParams(-1,42)
        )

        setContentView(root)
    }

    private fun refresh() {

        list.removeAllViews()

        val arr = JSONArray(
            getSharedPreferences(
                "history",
                Context.MODE_PRIVATE
            ).getString("items","[]") ?: "[]"
        )

        var count = 0

        for (i in arr.length()-1 downTo 0) {

            val item = arr.getJSONObject(i)

            var p = item
                .optString("platform","")
                .lowercase()

            if (p == "twitter") p = "x"

            if (filter != "all" && p != filter)
                continue

            count++

            addCard(item,p,i)
        }

        if (count == 0) {

            val empty = TextView(this).apply {
                text = "No downloads yet"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(muted)
            }

            list.addView(
                empty,
                LinearLayout.LayoutParams(-1,160)
            )
        }
    }

    private fun addCard(
        item: org.json.JSONObject,
        platform: String,
        index: Int
    ) {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0,14,0,14)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val p = TextView(this).apply {
            text = when(platform) {
                "instagram" -> "Instagram"
                "pinterest" -> "Pinterest"
                "reddit" -> "Reddit"
                "x" -> "X"
                else -> platform
            }
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                when(platform) {
                    "instagram" -> Color.rgb(225,48,108)
                    "pinterest" -> Color.rgb(210,20,40)
                    "reddit" -> Color.rgb(255,69,0)
                    else -> black
                }
            )
        }

        top.addView(
            p,
            LinearLayout.LayoutParams(0,35,1f)
        )

        val time = TextView(this).apply {
            text = item.optString("time")
            textSize = 10f
            setTextColor(muted)
        }

        top.addView(time)

        row.addView(top)

        val filename = TextView(this).apply {
            text = item.optString(
                "filename",
                "Media"
            )
            textSize = 14f
            maxLines = 2
            setTextColor(black)
        }

        row.addView(filename)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val open = smallButton("OPEN")
        val share = smallButton("SHARE")
        val delete = smallButton("DELETE")

        actions.addView(
            open,
            LinearLayout.LayoutParams(0,36,1f)
        )

        actions.addView(
            share,
            LinearLayout.LayoutParams(0,36,1f)
        )

        actions.addView(
            delete,
            LinearLayout.LayoutParams(0,36,1f)
        )

        row.addView(
            actions,
            LinearLayout.LayoutParams(-1,42).apply {
                topMargin = 8
            }
        )

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(232,232,230))
        }

        row.addView(
            divider,
            LinearLayout.LayoutParams(-1,1)
        )

        val uri = item.optString("uri","")

        open.setOnClickListener {
            if(uri.isNotBlank()) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(uri)
                        ).apply {
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    )
                } catch(_:Exception) {}
            }
        }

        share.setOnClickListener {
            if(uri.isNotBlank()) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_STREAM,
                                Uri.parse(uri)
                            )
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        },
                        "Share media"
                    )
                )
            }
        }

        delete.setOnClickListener {
            deleteItem(index)
        }

        list.addView(
            row,
            LinearLayout.LayoutParams(-1,-2)
        )

        row.alpha = 0f
        row.animate()
            .alpha(1f)
            .setDuration(180)
            .start()
    }

    private fun smallButton(text:String):TextView =
        TextView(this).apply {
            this.text = text
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(black)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 12f
                setStroke(
                    1,
                    Color.rgb(220,220,218)
                )
            }
        }

    private fun deleteItem(index:Int) {

        val prefs =
            getSharedPreferences(
                "history",
                Context.MODE_PRIVATE
            )

        val old = JSONArray(
            prefs.getString("items","[]") ?: "[]"
        )

        val fresh = JSONArray()

        for(i in 0 until old.length()) {
            if(i != index)
                fresh.put(old.getJSONObject(i))
        }

        prefs.edit()
            .putString("items",fresh.toString())
            .apply()

        refresh()
    }
}
