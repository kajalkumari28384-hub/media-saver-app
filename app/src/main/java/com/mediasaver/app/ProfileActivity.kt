package com.mediasaver.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class ProfileActivity : AppCompatActivity() {

    private val bg = Color.rgb(10, 10, 11)
    private val card = Color.rgb(20, 20, 22)
    private val border = Color.rgb(52, 52, 56)
    private val white = Color.rgb(245, 245, 245)
    private val muted = Color.rgb(145, 145, 150)

    private fun rounded(
        color: Int,
        stroke: Int = color,
        radius: Float = 24f
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

    private fun gap(h: Int) =
        Space(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(1, h)
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

        var instagram = 0
        var pinterest = 0
        var x = 0
        var reddit = 0

        for (i in 0 until array.length()) {
            when (
                array.optJSONObject(i)
                    ?.optString("platform")
            ) {
                "Instagram" -> instagram++
                "Pinterest" -> pinterest++
                "X" -> x++
                "Reddit" -> reddit++
            }
        }

        val total =
            instagram + pinterest + x + reddit

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setBackgroundColor(bg)
            }

        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    20,
                    24,
                    20,
                    105
                )
            }

        content.addView(
            text("Profile", 28f)
        )

        content.addView(gap(22))

        // PROFILE CARD

        val profileCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    18,
                    24,
                    18,
                    24
                )
                background = rounded(
                    card,
                    border,
                    26f
                )
            }

        val avatar =
            TextView(this).apply {
                text = "●"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(
                    Color.rgb(
                        210,
                        210,
                        214
                    )
                )
                background = rounded(
                    Color.rgb(
                        38,
                        38,
                        41
                    ),
                    Color.rgb(
                        70,
                        70,
                        74
                    ),
                    100f
                )
            }

        profileCard.addView(
            avatar,
            LinearLayout.LayoutParams(
                62,
                62
            )
        )

        profileCard.addView(gap(13))

        profileCard.addView(
            text(
                "Youwank Raj",
                22f
            ).apply {
                gravity = Gravity.CENTER
                typeface =
                    Typeface.DEFAULT_BOLD
            }
        )

        profileCard.addView(gap(4))

        profileCard.addView(
            text(
                "Media Saver",
                13f,
                muted
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        content.addView(profileCard)

        content.addView(gap(14))

        // TOTAL

        val totalCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    18,
                    20,
                    18,
                    20
                )
                background = rounded(
                    card,
                    border,
                    26f
                )
            }

        totalCard.addView(
            text(
                total.toString(),
                38f
            ).apply {
                gravity = Gravity.CENTER
                typeface =
                    Typeface.DEFAULT_BOLD
            }
        )

        totalCard.addView(gap(4))

        totalCard.addView(
            text(
                "TOTAL DOWNLOADS",
                11f,
                muted
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        content.addView(totalCard)

        content.addView(gap(14))

        // CHART

        val chartCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    18,
                    18,
                    18,
                    18
                )
                background = rounded(
                    card,
                    border,
                    26f
                )
            }

        chartCard.addView(
            text(
                "Download breakdown",
                17f
            )
        )

        chartCard.addView(gap(12))

        chartCard.addView(
            DownloadPieView(
                this,
                instagram,
                pinterest,
                x,
                reddit
            ),
            LinearLayout.LayoutParams(
                -1,
                220
            )
        )

        chartCard.addView(gap(10))

        chartCard.addView(
            text(
                "Instagram  $instagram   •   Pinterest  $pinterest",
                12f,
                Color.rgb(
                    235,
                    105,
                    145
                )
            )
        )

        chartCard.addView(gap(6))

        chartCard.addView(
            text(
                "X  $x   •   Reddit  $reddit",
                12f,
                muted
            )
        )

        content.addView(chartCard)

        content.addView(gap(14))

        val history =
            TextView(this).apply {
                text = "VIEW HISTORY"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                typeface =
                    Typeface.DEFAULT_BOLD
                background = rounded(
                    white,
                    white,
                    18f
                )
                setPadding(
                    0,
                    15,
                    0,
                    15
                )
            }

        content.addView(history)

        history.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    HistoryActivity::class.java
                )
            )
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        // NAV

        val nav =
            LinearLayout(this).apply {
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    20,
                    6,
                    20,
                    6
                )
                setBackgroundColor(
                    Color.rgb(
                        15,
                        15,
                        16
                    )
                )
            }

        val home =
            text("⌂", 29f, muted).apply {
                gravity = Gravity.CENTER
            }

        val profile =
            text("●", 20f, white).apply {
                gravity = Gravity.CENTER
            }

        nav.addView(
            home,
            LinearLayout.LayoutParams(
                0,
                58,
                1f
            )
        )

        nav.addView(
            profile,
            LinearLayout.LayoutParams(
                0,
                58,
                1f
            )
        )

        root.addView(nav)

        home.setOnClickListener {
            finish()
        }

        setContentView(root)
    }

    class DownloadPieView(
        context: Context,
        private val instagram: Int,
        private val pinterest: Int,
        private val x: Int,
        private val reddit: Int
    ) : androidx.appcompat.widget.AppCompatTextView(context) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val total =
                instagram +
                    pinterest +
                    x +
                    reddit

            if (total == 0) {
                paint.color =
                    Color.rgb(45, 45, 48)
                paint.style =
                    Paint.Style.STROKE
                paint.strokeWidth = 26f

                canvas.drawCircle(
                    width / 2f,
                    height / 2f,
                    65f,
                    paint
                )

                paint.style =
                    Paint.Style.FILL
                paint.textSize = 13f
                paint.color = Color.GRAY
                paint.textAlign =
                    Paint.Align.CENTER

                canvas.drawText(
                    "No downloads",
                    width / 2f,
                    height / 2f + 5f,
                    paint
                )

                return
            }

            val rect =
                RectF(
                    width / 2f - 75f,
                    height / 2f - 75f,
                    width / 2f + 75f,
                    height / 2f + 75f
                )

            paint.style =
                Paint.Style.STROKE
            paint.strokeWidth = 28f

            var start = -90f

            val values =
                listOf(
                    instagram,
                    pinterest,
                    x,
                    reddit
                )

            val colors =
                listOf(
                    Color.rgb(
                        230,
                        70,
                        125
                    ),
                    Color.rgb(
                        230,
                        70,
                        65
                    ),
                    Color.WHITE,
                    Color.rgb(
                        255,
                        110,
                        45
                    )
                )

            for (i in values.indices) {

                if (values[i] <= 0) continue

                val sweep =
                    values[i]
                        .toFloat() /
                        total
                        .toFloat() *
                        360f

                paint.color =
                    colors[i]

                canvas.drawArc(
                    rect,
                    start,
                    sweep,
                    false,
                    paint
                )

                start += sweep
            }

            paint.style =
                Paint.Style.FILL
            paint.color = white
            paint.textSize = 30f
            paint.textAlign =
                Paint.Align.CENTER

            canvas.drawText(
                total.toString(),
                width / 2f,
                height / 2f + 10f,
                paint
            )

            paint.textSize = 10f
            paint.color = Color.GRAY

            canvas.drawText(
                "downloads",
                width / 2f,
                height / 2f + 28f,
                paint
            )
        }
    }
}
