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
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class HistoryActivity : AppCompatActivity() {

    private val bg = Color.rgb(10, 10, 11)
    private val card = Color.rgb(20, 20, 22)
    private val border = Color.rgb(52, 52, 56)
    private val white = Color.rgb(245, 245, 245)
    private val muted = Color.rgb(145, 145, 150)

    private lateinit var list:
        LinearLayout

    private var filter = "All"

    private fun rounded(
        color: Int,
        stroke: Int = color,
        radius: Float = 20f
    ) = GradientDrawable().apply {
        setColor(color)
        setStroke(1, stroke)
        cornerRadius = radius
    }

    private fun text(
        value: String,
        size: Float,
        color: Int = white
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildUi()
    }

    private fun buildUi() {

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setBackgroundColor(bg)
                setPadding(
                    20,
                    24,
                    20,
                    20
                )
            }

        root.addView(
            text("History", 28f)
        )

        root.addView(
            text(
                "Your saved media",
                13f,
                muted
            )
        )

        root.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        1,
                        18
                    )
            }
        )

        // FILTER STRIP

        val horizontal =
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled =
                    false
            }

        val filters =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        val names =
            listOf(
                "All",
                "Instagram",
                "Pinterest",
                "X",
                "Reddit"
            )

        for (name in names) {

            val chip =
                TextView(this).apply {
                    text = name
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(
                        18,
                        11,
                        18,
                        11
                    )
                    setTextColor(
                        if (name == "All")
                            Color.BLACK
                        else white
                    )

                    background =
                        rounded(
                            if (name == "All")
                                white
                            else card,
                            border,
                            100f
                        )
                }

            val params =
                LinearLayout.LayoutParams(
                    -2,
                    42
                )

            params.setMargins(
                0,
                0,
                8,
                0
            )

            filters.addView(
                chip,
                params
            )

            chip.setOnClickListener {
                filter = name

                for (j in 0 until filters.childCount) {
                    val child =
                        filters.getChildAt(j)
                            as TextView

                    val selected =
                        child.text.toString() ==
                            filter

                    child.setTextColor(
                        if (selected)
                            Color.BLACK
                        else white
                    )

                    child.background =
                        rounded(
                            if (selected)
                                white
                            else card,
                            border,
                            100f
                        )
                }

                render()
            }
        }

        horizontal.addView(filters)

        root.addView(
            horizontal,
            LinearLayout.LayoutParams(
                -1,
                48
            )
        )

        root.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        1,
                        12
                    )
            }
        )

        val scroll =
            ScrollView(this)

        list =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        val home =
            TextView(this).apply {
                text = "←  HOME"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(white)
                setPadding(0, 15, 0, 15)
                background =
                    rounded(
                        card,
                        border,
                        18f
                    )
            }

        root.addView(home)

        home.setOnClickListener {
            finish()
        }

        setContentView(root)

        render()
    }

    private fun render() {

        list.removeAllViews()

        val prefs =
            getSharedPreferences(
                "history",
                Context.MODE_PRIVATE
            )

        val array =
            JSONArray(
                prefs.getString(
                    "items",
                    "[]"
                ) ?: "[]"
            )

        var shown = 0

        for (
            i in array.length() - 1 downTo 0
        ) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            val platform =
                item.optString(
                    "platform",
                    "Unknown"
                )

            if (
                filter != "All" &&
                platform != filter
            ) {
                continue
            }

            shown++

            addCard(
                item,
                i,
                platform
            )
        }

        if (shown == 0) {
            val empty =
                text(
                    "Nothing here yet",
                    15f,
                    muted
                )

            empty.gravity =
                Gravity.CENTER

            empty.setPadding(
                0,
                70,
                0,
                70
            )

            list.addView(empty)
        }
    }

    private fun addCard(
        item: org.json.JSONObject,
        index: Int,
        platform: String
    ) {

        val uriString =
            item.optString("uri", "")

        val filename =
            item.optString(
                "filename",
                "Media"
            )

        val time =
            item.optString(
                "time",
                ""
            )

        val platformColor =
            when (platform) {
                "Instagram" ->
                    Color.rgb(
                        230,
                        70,
                        125
                    )

                "Pinterest" ->
                    Color.rgb(
                        230,
                        65,
                        70
                    )

                "X" ->
                    Color.WHITE

                "Reddit" ->
                    Color.rgb(
                        255,
                        105,
                        45
                    )

                else ->
                    Color.GRAY
            }

        val cardView =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    16,
                    16,
                    16,
                    14
                )
                background =
                    rounded(
                        card,
                        border,
                        22f
                    )
            }

        val top =
            LinearLayout(this).apply {
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val dot =
            TextView(this).apply {
                text = "●"
                textSize = 14f
                setTextColor(
                    platformColor
                )
            }

        top.addView(
            dot,
            LinearLayout.LayoutParams(
                24,
                30
            )
        )

        val title =
            text(
                platform,
                14f
            ).apply {
                typeface =
                    Typeface.DEFAULT_BOLD
            }

        top.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        top.addView(
            text(
                time,
                11f,
                muted
            )
        )

        cardView.addView(top)

        cardView.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        1,
                        9
                    )
            }
        )

        cardView.addView(
            text(
                filename,
                13f,
                muted
            )
        )

        cardView.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        1,
                        12
                    )
            }
        )

        val actions =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        fun action(
            name: String
        ): TextView =
            TextView(this).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(white)
                background =
                    rounded(
                        Color.rgb(
                            28,
                            28,
                            30
                        ),
                        border,
                        14f
                    )
                setPadding(
                    13,
                    9,
                    13,
                    9
                )
            }

        val open =
            action("OPEN")

        val share =
            action("SHARE")

        val delete =
            action("DELETE")

        actions.addView(
            open,
            LinearLayout.LayoutParams(
                0,
                40,
                1f
            )
        )

        actions.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        7,
                        1
                    )
            }
        )

        actions.addView(
            share,
            LinearLayout.LayoutParams(
                0,
                40,
                1f
            )
        )

        actions.addView(
            Space(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        7,
                        1
                    )
            }
        )

        actions.addView(
            delete,
            LinearLayout.LayoutParams(
                0,
                40,
                1f
            )
        )

        cardView.addView(actions)

        open.setOnClickListener {

            if (uriString.isNotEmpty()) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(uriString)
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }

        share.setOnClickListener {

            if (uriString.isNotEmpty()) {

                val intent =
                    Intent(
                        Intent.ACTION_SEND
                    ).apply {
                        type =
                            if (
                                filename.endsWith(
                                    ".jpg",
                                    true
                                ) ||
                                filename.endsWith(
                                    ".png",
                                    true
                                )
                            )
                                "image/*"
                            else
                                "video/*"

                        putExtra(
                            Intent.EXTRA_STREAM,
                            Uri.parse(uriString)
                        )

                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                startActivity(
                    Intent.createChooser(
                        intent,
                        "Share media"
                    )
                )
            }
        }

        delete.setOnClickListener {

            val prefs =
                getSharedPreferences(
                    "history",
                    Context.MODE_PRIVATE
                )

            val array =
                JSONArray(
                    prefs.getString(
                        "items",
                        "[]"
                    ) ?: "[]"
                )

            val newArray =
                JSONArray()

            for (i in 0 until array.length()) {
                if (i != index) {
                    newArray.put(
                        array.get(i)
                    )
                }
            }

            prefs.edit()
                .putString(
                    "items",
                    newArray.toString()
                )
                .apply()

            render()
        }

        list.addView(
            cardView,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    10
                )
            }
        )

        // subtle entrance animation
        val animation =
            AlphaAnimation(
                0f,
                1f
            ).apply {
                duration = 180
            }

        cardView.startAnimation(animation)
    }
}
