package com.mediasaver.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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

    private val backendUrl = "https://media-saver-app-cu8d.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var platform: TextView
    private lateinit var download: TextView

    private val bg = Color.rgb(250, 250, 249)
    private val black = Color.rgb(18, 18, 18)
    private val muted = Color.rgb(125, 125, 125)
    private val line = Color.rgb(225, 225, 223)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildHome()

        if (intent?.action == Intent.ACTION_SEND &&
            intent.type == "text/plain") {
            input.setText(
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            )
            detectPlatform(input.text.toString())
        }
    }

    private fun buildHome() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 30)
        }

        // HEADER
        val title = TextView(this).apply {
            text = "Media Saver"
            textSize = 30f
            setTextColor(black)
            typeface = Typeface.create("sans", Typeface.BOLD)
        }

        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Save your media, your way."
            textSize = 14f
            setTextColor(muted)
        }

        content.addView(
            subtitle,
            LinearLayout.LayoutParams(-1, 26)
        )

        val brand = TextView(this).apply {
            text = "by Youwank Raj"
            textSize = 11f
            setTextColor(Color.rgb(155,155,155))
        }

        content.addView(brand)

        // LINK LABEL
        val linkLabel = TextView(this).apply {
            text = "MEDIA LINK"
            textSize = 10f
            letterSpacing = .12f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        }

        content.addView(
            linkLabel,
            LinearLayout.LayoutParams(-1, 24).apply {
                topMargin = 34
            }
        )

        // INPUT
        input = EditText(this).apply {
            hint = "Paste a link"
            textSize = 16f
            setSingleLine(true)
            setTextColor(black)
            setHintTextColor(Color.rgb(175,175,175))
            setPadding(0, 4, 0, 8)
            background = null
        }

        val inputWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 0)
        }

        inputWrap.addView(
            input,
            LinearLayout.LayoutParams(-1, 52)
        )

        val divider = View(this).apply {
            setBackgroundColor(line)
        }

        inputWrap.addView(
            divider,
            LinearLayout.LayoutParams(-1, 1)
        )

        content.addView(
            inputWrap,
            LinearLayout.LayoutParams(-1, 60)
        )

        platform = TextView(this).apply {
            text = "Waiting for a link"
            textSize = 12f
            setTextColor(muted)
        }

        content.addView(
            platform,
            LinearLayout.LayoutParams(-1, 30)
        )

        input.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int,
                    count: Int, after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?, start: Int,
                    before: Int, count: Int
                ) {
                    detectPlatform(s?.toString() ?: "")
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        // DOWNLOAD
        download = TextView(this).apply {
            text = "DOWNLOAD"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(black, 16f)
        }

        content.addView(
            download,
            LinearLayout.LayoutParams(-1, 54).apply {
                topMargin = 12
            }
        )

        download.setOnClickListener {
            downloadSingle()
        }

        status = TextView(this).apply {
            text = "Ready"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }

        content.addView(
            status,
            LinearLayout.LayoutParams(-1, 42)
        )

        // BULK
        val bulkLabel = TextView(this).apply {
            text = "BULK DOWNLOAD"
            textSize = 10f
            letterSpacing = .12f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        }

        content.addView(
            bulkLabel,
            LinearLayout.LayoutParams(-1, 24).apply {
                topMargin = 20
            }
        )

        val bulk = TextView(this).apply {
            text = "+  Download multiple links"
            textSize = 14f
            setTextColor(black)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }

        content.addView(
            bulk,
            LinearLayout.LayoutParams(-1, 50)
        )

        bulk.setOnClickListener {
            showBulkDialog()
        }

        // RECENT
        val recent = TextView(this).apply {
            text = "RECENT ACTIVITY"
            textSize = 10f
            letterSpacing = .12f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        }

        content.addView(
            recent,
            LinearLayout.LayoutParams(-1, 24).apply {
                topMargin = 24
            }
        )

        val recentInfo = TextView(this).apply {
            text = "Your downloads appear in Profile → History."
            textSize = 13f
            setTextColor(muted)
        }

        content.addView(recentInfo)

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        // BOTTOM NAV
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(black)
            setPadding(24, 8, 24, 8)
        }

        val home = navButton("⌂", "Home")
        val profile = navButton("○", "Profile")

        nav.addView(
            home,
            LinearLayout.LayoutParams(0, 62, 1f)
        )

        nav.addView(
            profile,
            LinearLayout.LayoutParams(0, 62, 1f)
        )

        profile.setOnClickListener {
            startActivity(
                Intent(this, ProfileActivity::class.java)
            )
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(-1, 78)
        )

        setContentView(root)
    }

    private fun navButton(
        icon: String,
        text: String
    ): LinearLayout {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val i = TextView(this).apply {
            this.text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        val t = TextView(this).apply {
            this.text = text
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(190,190,190))
        }

        box.addView(i)
        box.addView(t)

        return box
    }

    private fun rounded(
        color: Int,
        radius: Float
    ) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun detectPlatform(url: String) {

        val u = url.lowercase()

        when {
            u.contains("instagram.com") -> {
                platform.text = "Instagram"
                platform.setTextColor(Color.rgb(225,48,108))
            }

            u.contains("pinterest.com") ||
            u.contains("pin.it") -> {
                platform.text = "Pinterest"
                platform.setTextColor(Color.rgb(210,20,40))
            }

            u.contains("twitter.com") ||
            u.contains("x.com") -> {
                platform.text = "X"
                platform.setTextColor(black)
            }

            u.contains("reddit.com") ||
            u.contains("redd.it") -> {
                platform.text = "Reddit"
                platform.setTextColor(Color.rgb(255,69,0))
            }

            u.contains("youtube.com") ||
            u.contains("youtu.be") -> {
                platform.text = "YouTube isn't supported"
                platform.setTextColor(Color.rgb(190,50,50))
            }

            u.isBlank() -> {
                platform.text = "Waiting for a link"
                platform.setTextColor(muted)
            }

            else -> {
                platform.text = "Unsupported link"
                platform.setTextColor(muted)
            }
        }
    }

    private fun downloadSingle() {

        val url = input.text.toString().trim()

        if (url.isBlank()) {
            status.text = "Paste a link first"
            return
        }

        if (url.lowercase().contains("youtube.com") ||
            url.lowercase().contains("youtu.be")) {
            status.text = "YouTube isn't supported"
            return
        }

        download.isEnabled = false
        download.alpha = .55f
        status.text = "Processing…"

        val body = JSONObject()
            .put("url", url)
            .toString()
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
                        finishDownload("Couldn't connect")
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
                        obj.optString("filename")

                    if (filename.isBlank()) {
                        runOnUiThread {
                            finishDownload(
                                obj.optString(
                                    "error",
                                    "Download failed"
                                )
                            )
                        }
                        return
                    }

                    val p =
                        obj.optString(
                            "platform",
                            "unknown"
                        )

                    val type =
                        obj.optString(
                            "type",
                            "video"
                        )

                    val fileRequest =
                        Request.Builder()
                            .url("$backendUrl/file/$filename")
                            .build()

                    client.newCall(fileRequest)
                        .enqueue(
                            object : Callback {

                                override fun onFailure(
                                    call: Call,
                                    e: IOException
                                ) {
                                    runOnUiThread {
                                        finishDownload(
                                            "Couldn't save file"
                                        )
                                    }
                                }

                                override fun onResponse(
                                    call: Call,
                                    response: Response
                                ) {

                                    try {
                                        val bytes =
                                            response.body?.bytes()
                                                ?: throw Exception()

                                        val uri =
                                            saveToGallery(
                                                bytes,
                                                filename,
                                                type == "image"
                                            )

                                        addHistory(
                                            p,
                                            filename,
                                            uri
                                        )

                                        runOnUiThread {
                                            finishDownload(
                                                "Saved successfully"
                                            )
                                        }

                                    } catch (_: Exception) {
                                        runOnUiThread {
                                            finishDownload(
                                                "Couldn't save file"
                                            )
                                        }
                                    }
                                }
                            }
                        )
                }
            }
        )
    }

    private fun finishDownload(message: String) {
        download.isEnabled = true
        download.alpha = 1f
        status.text = message
    }

    private fun saveToGallery(
        bytes: ByteArray,
        filename: String,
        image: Boolean
    ): Uri? {

        val values = ContentValues()

        if (image) {
            values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                filename
            )
            values.put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES +
                    "/MediaSaver"
            )

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            uri?.let {
                contentResolver.openOutputStream(it)
                    ?.use { out -> out.write(bytes) }
            }

            return uri

        } else {
            values.put(
                MediaStore.Video.Media.DISPLAY_NAME,
                filename
            )
            values.put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )
            values.put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES +
                    "/MediaSaver"
            )

            val uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            )

            uri?.let {
                contentResolver.openOutputStream(it)
                    ?.use { out -> out.write(bytes) }
            }

            return uri
        }
    }

    private fun addHistory(
        platform: String,
        filename: String,
        uri: Uri?
    ) {

        val prefs =
            getSharedPreferences(
                "history",
                Context.MODE_PRIVATE
            )

        val arr = JSONArray(
            prefs.getString("items", "[]")
                ?: "[]"
        )

        arr.put(
            JSONObject()
                .put("platform", platform)
                .put("filename", filename)
                .put("uri", uri?.toString() ?: "")
                .put(
                    "time",
                    SimpleDateFormat(
                        "dd MMM, HH:mm",
                        Locale.getDefault()
                    ).format(Date())
                )
        )

        prefs.edit()
            .putString("items", arr.toString())
            .apply()
    }

    private fun showBulkDialog() {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 8)
        }

        val info = TextView(this).apply {
            text = "One link per line"
            textSize = 13f
            setTextColor(muted)
        }

        val input = EditText(this).apply {
            hint = "Instagram link\nPinterest link\nX link\nReddit link"
            minLines = 6
            gravity = Gravity.TOP
        }

        box.addView(info)
        box.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Bulk Download")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("START") { _, _ ->

                val urls = input.text.toString()
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .filterNot {
                        it.contains(
                            "youtube.com",
                            true
                        ) || it.contains(
                            "youtu.be",
                            true
                        )
                    }

                if (urls.isNotEmpty()) {
                    processBulk(urls, 0, 0)
                }
            }
            .show()
    }

    private fun processBulk(
        urls: List<String>,
        index: Int,
        success: Int
    ) {

        if (index >= urls.size) {
            runOnUiThread {
                status.text =
                    "Completed • $success/${urls.size} saved"
            }
            return
        }

        runOnUiThread {
            status.text =
                "Downloading ${index + 1}/${urls.size}"
        }

        val body = JSONObject()
            .put("url", urls[index])
            .toString()
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
                    processBulk(
                        urls,
                        index + 1,
                        success
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {

                    try {
                        val obj = JSONObject(
                            response.body?.string()
                                ?: "{}"
                        )

                        val filename =
                            obj.optString("filename")

                        if (filename.isBlank()) {
                            processBulk(
                                urls,
                                index + 1,
                                success
                            )
                            return
                        }

                        val p =
                            obj.optString(
                                "platform",
                                "unknown"
                            )

                        val type =
                            obj.optString(
                                "type",
                                "video"
                            )

                        val req =
                            Request.Builder()
                                .url(
                                    "$backendUrl/file/$filename"
                                )
                                .build()

                        client.newCall(req).enqueue(
                            object : Callback {

                                override fun onFailure(
                                    call: Call,
                                    e: IOException
                                ) {
                                    processBulk(
                                        urls,
                                        index + 1,
                                        success
                                    )
                                }

                                override fun onResponse(
                                    call: Call,
                                    response: Response
                                ) {

                                    try {
                                        val bytes =
                                            response.body?.bytes()
                                                ?: throw Exception()

                                        val uri =
                                            saveToGallery(
                                                bytes,
                                                filename,
                                                type == "image"
                                            )

                                        addHistory(
                                            p,
                                            filename,
                                            uri
                                        )

                                        processBulk(
                                            urls,
                                            index + 1,
                                            success + 1
                                        )

                                    } catch (_: Exception) {
                                        processBulk(
                                            urls,
                                            index + 1,
                                            success
                                        )
                                    }
                                }
                            }
                        )

                    } catch (_: Exception) {
                        processBulk(
                            urls,
                            index + 1,
                            success
                        )
                    }
                }
            }
        )
    }
}
