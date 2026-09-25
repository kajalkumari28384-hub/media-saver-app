package com.mediasaver.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private val backendUrl = "https://media-saver-app-cu8d.onrender.com"

    private val bg = Color.rgb(10, 10, 11)
    private val card = Color.rgb(20, 20, 22)
    private val field = Color.rgb(28, 28, 31)
    private val border = Color.rgb(52, 52, 56)
    private val white = Color.rgb(245, 245, 245)
    private val muted = Color.rgb(145, 145, 150)

    private fun rounded(
        color: Int,
        stroke: Int = color,
        radius: Float = 22f
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
        this.text = value
        textSize = size
        settextColor(color)
        includeFontPadding = false
    }

    private fun gap(height: Int) =
        Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }

    private fun platformFor(url: String): String {
        return when {
            url.contains("instagram.com", true) ||
            url.contains("instagr.am", true) -> "Instagram"

            url.contains("pinterest.", true) ||
            url.contains("pin.it", true) -> "Pinterest"

            url.contains("twitter.com", true) ||
            url.contains("x.com", true) -> "X"

            url.contains("reddit.com", true) ||
            url.contains("redd.it", true) -> "Reddit"

            else -> ""
        }
    }

    private fun saveToGallery(
        bytes: ByteArray,
        filename: String,
        isImage: Boolean
    ): Uri? {
        val values = ContentValues()

        return if (isImage) {
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/MediaSaver"
            )

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                }
            }

            uri
        } else {
            values.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/MediaSaver"
            )

            val uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                }
            }

            uri
        }
    }

    private fun addToHistory(
        platform: String,
        filename: String,
        uri: Uri?
    ) {
        val prefs = getSharedPreferences("history", Context.MODE_PRIVATE)
        val array = JSONArray(
            prefs.getString("items", "[]") ?: "[]"
        )

        val item = JSONObject().apply {
            put("platform", platform)
            put("filename", filename)
            put("uri", uri?.toString() ?: "")
            put(
                "time",
                SimpleDateFormat(
                    "dd MMM, HH:mm",
                    Locale.getDefault()
                ).format(Date())
            )
        }

        array.put(item)

        prefs.edit()
            .putString("items", array.toString())
            .apply()
    }

    private fun downloadOne(
        url: String,
        callback: (Boolean, String) -> Unit
    ) {
        val platform = platformFor(url)

        if (platform.isEmpty()) {
            runOnUiThread {
                callback(
                    false,
                    "Only Instagram, Pinterest, X and Reddit are supported"
                )
            }
            return
        }

        val json = JSONObject().apply {
            put("url", url)
        }

        val body = json.toString()
            .toRequestBody(
                "application/json".toMediaType()
            )

        val request = Request.Builder()
            .url("$backendUrl/download")
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    runOnUiThread {
                        callback(
                            false,
                            e.message ?: "Network error"
                        )
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    val raw =
                        response.body?.string() ?: "{}"

                    val obj = try {
                        JSONObject(raw)
                    } catch (_: Exception) {
                        JSONObject()
                    }

                    val filename =
                        obj.optString("filename", "")

                    if (filename.isEmpty()) {
                        runOnUiThread {
                            callback(
                                false,
                                obj.optString(
                                    "error",
                                    "Server error"
                                )
                            )
                        }
                        return
                    }

                    val fileRequest =
                        Request.Builder()
                            .url("$backendUrl/file/$filename")
                            .build()

                    client.newCall(fileRequest).enqueue(
                        object : Callback {

                            override fun onFailure(
                                call: Call,
                                e: IOException
                            ) {
                                runOnUiThread {
                                    callback(
                                        false,
                                        e.message
                                            ?: "File fetch failed"
                                    )
                                }
                            }

                            override fun onResponse(
                                call: Call,
                                response: Response
                            ) {
                                val bytes =
                                    response.body?.bytes()

                                if (bytes == null) {
                                    runOnUiThread {
                                        callback(
                                            false,
                                            "Empty file"
                                        )
                                    }
                                    return
                                }

                                val uri = saveToGallery(
                                    bytes,
                                    filename,
                                    obj.optString(
                                        "type",
                                        "video"
                                    ) == "image"
                                )

                                addToHistory(
                                    platform,
                                    filename,
                                    uri
                                )

                                runOnUiThread {
                                    callback(
                                        true,
                                        platform
                                    )
                                }
                            }
                        }
                    )
                }
            }
        )
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 105)
        }

        // HEADER

        content.addView(
            text("Media Saver", 28f)
        )

        content.addView(gap(5))

        content.addView(
            text("by Youwank Raj", 13f, muted)
        )

        content.addView(gap(28))

        // MAIN DOWNLOAD CARD

        val downloadCard =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = rounded(
                    card,
                    border,
                    26f
                )
            }

        downloadCard.addView(
            text("SAVE MEDIA", 11f, muted)
        )

        downloadCard.addView(gap(11))

        val input =
            EditText(this).apply {
                hint = "Paste a link"
                hinttextColor =
                    Color.rgb(100, 100, 105)
                settextColor(white)
                textSize = 17f
                setSingleLine(true)
                setPadding(16, 0, 16, 0)
                background = rounded(
                    field,
                    border,
                    17f
                )
            }

        if (
            intent?.action == Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            input.setText(
                intent.getStringExtra(
                    Intent.EXTRA_TEXT
                ) ?: ""
            )
        }

        downloadCard.addView(
            input,
            LinearLayout.LayoutParams(
                -1,
                58
            )
        )

        downloadCard.addView(gap(11))

        val status =
            text("Ready", 13f, muted)

        downloadCard.addView(status)

        downloadCard.addView(gap(11))

        val download =
            TextView(this).apply {
                text = "DOWNLOAD"
                textSize = 14f
                gravity = Gravity.CENTER
                settextColor(Color.BLACK)
                typeface =
                    Typeface.DEFAULT_BOLD
                background = rounded(
                    white,
                    white,
                    17f
                )
                setPadding(0, 15, 0, 15)
            }

        downloadCard.addView(download)

        content.addView(downloadCard)

        // BULK

        content.addView(gap(14))

        val bulkCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)
                background = rounded(
                    card,
                    border,
                    26f
                )
            }

        val bulkTitle =
            LinearLayout(this).apply {
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        bulkTitle.addView(
            text("Bulk download", 18f),
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        val bulkToggle =
            text("＋", 27f, white).apply {
                gravity = Gravity.CENTER
            }

        bulkTitle.addView(
            bulkToggle,
            LinearLayout.LayoutParams(
                42,
                42
            )
        )

        bulkCard.addView(bulkTitle)

        val bulkInput =
            EditText(this).apply {
                hint = "One link per line"
                hinttextColor =
                    Color.rgb(100, 100, 105)
                settextColor(white)
                textSize = 15f
                gravity = Gravity.TOP
                minLines = 4
                setPadding(16, 14, 16, 14)
                background = rounded(
                    field,
                    border,
                    17f
                )
                visibility = View.GONE
            }

        bulkCard.addView(gap(10))
        bulkCard.addView(bulkInput)

        val queue =
            TextView(this).apply {
                text = "START QUEUE"
                textSize = 14f
                gravity = Gravity.CENTER
                settextColor(Color.BLACK)
                typeface =
                    Typeface.DEFAULT_BOLD
                background = rounded(
                    white,
                    white,
                    17f
                )
                setPadding(0, 15, 0, 15)
                visibility = View.GONE
            }

        bulkCard.addView(gap(10))
        bulkCard.addView(queue)

        content.addView(bulkCard)

        bulkToggle.setOnClickListener {
            val open =
                bulkInput.visibility != View.VISIBLE

            bulkInput.visibility =
                if (open) View.VISIBLE
                else View.GONE

            queue.visibility =
                if (open) View.VISIBLE
                else View.GONE

            bulkToggle.text =
                if (open) "−" else "＋"
        }

        content.addView(gap(24))

        content.addView(
            text("SUPPORTED", 11f, muted)
        )

        content.addView(gap(7))

        content.addView(
            text(
                "Instagram  •  Pinterest  •  X  •  Reddit",
                13f,
                muted
            )
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        // BOTTOM NAV

        val nav =
            LinearLayout(this).apply {
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(20, 6, 20, 6)
                setBackgroundColor(
                    Color.rgb(15, 15, 16)
                )
            }

        val home =
            text("⌂", 29f, white).apply {
                gravity = Gravity.CENTER
            }

        val profile =
            text("◯", 25f, muted).apply {
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
            scroll.smoothScrollTo(0, 0)
        }

        profile.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ProfileActivity::class.java
                )
            )
        }

        setContentView(root)

        // DOWNLOAD

        download.setOnClickListener {

            val url =
                input.text.toString().trim()

            if (url.isEmpty()) {
                status.text =
                    "Paste a link first"
                return@setOnClickListener
            }

            download.isEnabled = false
            status.text = "Downloading…"

            downloadOne(url) { success, message ->

                status.text =
                    if (success)
                        "Saved • $message"
                    else
                        "Failed • $message"

                download.isEnabled = true
            }
        }

        // BULK QUEUE

        queue.setOnClickListener {

            val urls =
                bulkInput.text
                    .toString()
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            if (urls.isEmpty()) {
                return@setOnClickListener
            }

            queue.isEnabled = false

            var index = 0

            fun next() {

                if (index >= urls.size) {
                    queue.text = "DONE"
                    queue.isEnabled = true
                    return
                }

                queue.text =
                    "${index + 1}/${urls.size}"

                downloadOne(urls[index]) {
                        _, _ ->

                    index++
                    next()
                }
            }

            next()
        }
    }
}
