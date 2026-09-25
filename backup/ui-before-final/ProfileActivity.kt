package com.mediasaver.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class ProfileActivity : AppCompatActivity() {

    private val bg = Color.rgb(250,250,249)
    private val black = Color.rgb(18,18,18)
    private val muted = Color.rgb(125,125,125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun build() {

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(24,30,24,20)
        }

        val title = TextView(this).apply {
            text = "Profile"
            textSize = 30f
            setTextColor(black)
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(title)

        val avatar = Avatar(this)

        root.addView(
            avatar,
            LinearLayout.LayoutParams(72,72).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 24
            }
        )

        val name = TextView(this).apply {
            text = "Youwank Raj"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(black)
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(
            name,
            LinearLayout.LayoutParams(-1,34)
        )

        val sub = TextView(this).apply {
            text = "Media Saver"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        root.addView(sub)

        val stats = getStats()

        val total = TextView(this).apply {
            text = stats.total.toString()
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(black)
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(
            total,
            LinearLayout.LayoutParams(-1,52).apply {
                topMargin = 30
            }
        )

        val totalLabel = TextView(this).apply {
            text = "TOTAL DOWNLOADS"
            textSize = 10f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        root.addView(totalLabel)

        root.addView(
            PieView(this, stats.counts),
            LinearLayout.LayoutParams(-1,210).apply {
                topMargin = 18
            }
        )

        val breakdown = TextView(this).apply {
            text = buildBreakdown(stats.counts)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        root.addView(
            breakdown,
            LinearLayout.LayoutParams(-1,58)
        )

        val history = TextView(this).apply {
            text = "VIEW HISTORY"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(black)
                cornerRadius = 16f
            }
        }

        history.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    HistoryActivity::class.java
                )
            )
        }

        root.addView(
            history,
            LinearLayout.LayoutParams(-1,52).apply {
                topMargin = 14
            }
        )

        val home = TextView(this).apply {
            text = "←  Home"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        home.setOnClickListener {
            finish()
        }

        root.addView(
            home,
            LinearLayout.LayoutParams(-1,42)
        )

        setContentView(root)
    }

    private data class Stats(
        val total: Int,
        val counts: Map<String,Int>
    )

    private fun getStats(): Stats {

        val arr = JSONArray(
            getSharedPreferences(
                "history",
                Context.MODE_PRIVATE
            ).getString("items","[]") ?: "[]"
        )

        val map = mutableMapOf(
            "instagram" to 0,
            "pinterest" to 0,
            "x" to 0,
            "reddit" to 0
        )

        for (i in 0 until arr.length()) {

            var p = arr.getJSONObject(i)
                .optString("platform")
                .lowercase()

            if (p == "twitter") p = "x"

            if (map.containsKey(p)) {
                map[p] = map[p]!! + 1
            }
        }

        return Stats(arr.length(), map)
    }

    private fun buildBreakdown(
        map: Map<String,Int>
    ): String {
        return "Instagram ${map["instagram"] ?: 0}   •   " +
                "Pinterest ${map["pinterest"] ?: 0}   •   " +
                "X ${map["x"] ?: 0}   •   " +
                "Reddit ${map["reddit"] ?: 0}"
    }
}

class Avatar(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {

        paint.color = Color.rgb(232,232,232)

        canvas.drawCircle(
            width / 2f,
            height / 2f,
            34f,
            paint
        )

        paint.color = Color.rgb(100,100,100)

        canvas.drawCircle(
            width / 2f,
            height / 2f - 9f,
            8f,
            paint
        )

        canvas.drawOval(
            RectF(
                width / 2f - 15,
                height / 2f + 2f,
                width / 2f + 15,
                height / 2f + 22f
            ),
            paint
        )
    }
}

class PieView(
    context: Context,
    private val values: Map<String,Int>
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {

        val total = values.values.sum()

        val rect = RectF(
            width/2f - 75,
            height/2f - 75,
            width/2f + 75,
            height/2f + 75
        )

        if (total == 0) {

            paint.color = Color.rgb(230,230,230)

            canvas.drawCircle(
                width/2f,
                height/2f,
                75f,
                paint
            )

            paint.color = Color.rgb(130,130,130)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 11f

            canvas.drawText(
                "No downloads",
                width/2f,
                height/2f + 4,
                paint
            )

            return
        }

        val colors = mapOf(
            "instagram" to Color.rgb(225,48,108),
            "pinterest" to Color.rgb(210,20,40),
            "x" to Color.BLACK,
            "reddit" to Color.rgb(255,69,0)
        )

        var start = -90f

        for ((key,value) in values) {

            if (value <= 0) continue

            val sweep =
                value.toFloat() / total * 360f

            paint.color =
                colors[key] ?: Color.GRAY

            canvas.drawArc(
                rect,
                start,
                sweep,
                true,
                paint
            )

            start += sweep
        }

        paint.color = Color.rgb(250,250,249)

        canvas.drawCircle(
            width/2f,
            height/2f,
            38f,
            paint
        )

        paint.color = Color.rgb(18,18,18)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f

        canvas.drawText(
            total.toString(),
            width/2f,
            height/2f + 6,
            paint
        )
    }
}
