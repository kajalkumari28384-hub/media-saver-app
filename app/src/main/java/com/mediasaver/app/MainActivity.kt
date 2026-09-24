package com.mediasaver.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

    private fun saveToGallery(bytes: ByteArray, filename: String, isImage: Boolean): Uri? {
        val values = ContentValues()
        val uri: Uri?
        if (isImage) {
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaSaver")
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            values.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MediaSaver")
            uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
        return uri
    }

    private fun addToHistory(platform: String, filename: String, uri: Uri?) {
        val prefs = getSharedPreferences("history", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val entry = JSONObject()
        entry.put("platform", platform)
        entry.put("filename", filename)
        entry.put("uri", uri?.toString() ?: "")
        entry.put("time", SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date()))
        arr.put(entry)
        prefs.edit().putString("items", arr.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accent = Color.parseColor("#4A90D9")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(56, 100, 56, 56)
        layout.setBackgroundColor(Color.parseColor("#FAFAFA"))

        val title = TextView(this)
        title.text = "Media Saver"
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#212121"))
        layout.addView(title)

        val spacer1 = TextView(this); spacer1.height = 40; layout.addView(spacer1)

        val input = EditText(this)
        input.hint = "Paste video link here"
        input.setPadding(24, 24, 24, 24)
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            val sharedUrl = intent.getStringExtra("android.intent.extra.TEXT") ?: ""
            input.setText(sharedUrl)
        }
        layout.addView(input)

        val statusText = TextView(this)
        statusText.text = "Ready"
        statusText.setTextColor(Color.parseColor("#757575"))
        statusText.setPadding(0, 30, 0, 30)
        layout.addView(statusText)

        val button = Button(this)
        button.text = "DOWNLOAD"
        button.setBackgroundColor(accent)
        button.setTextColor(Color.WHITE)
        layout.addView(button)

        val spacer2 = TextView(this); spacer2.height = 20; layout.addView(spacer2)

        val historyButton = Button(this)
        historyButton.text = "VIEW HISTORY"
        historyButton.setBackgroundColor(Color.parseColor("#E0E0E0"))
        historyButton.setTextColor(Color.parseColor("#212121"))
        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        layout.addView(historyButton)

        button.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isEmpty()) {
                statusText.text = "Please paste a link"
                return@setOnClickListener
            }
            statusText.text = "Step 1/2: Processing on server..."

            val json = JSONObject()
            json.put("url", url)
            json.put("quality", "720p")
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$backendUrl/download")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { statusText.text = "Failed: ${e.message}" }
                }
                override fun onResponse(call: Call, response: Response) {
                    val res = response.body?.string() ?: "{}"
                    val obj = try { JSONObject(res) } catch (ex: Exception) {
                        runOnUiThread { statusText.text = "Server error: $res" }
                        return
                    }
                    val filename = obj.optString("filename", "")
                    val platform = obj.optString("platform", "unknown")
                    val type = obj.optString("type", "video")
                    if (filename.isEmpty()) {
                        runOnUiThread { statusText.text = "Failed: ${obj.optString("error", res)}" }
                        return
                    }
                    runOnUiThread { statusText.text = "Step 2/2: Saving to phone..." }

                    val fileReq = Request.Builder().url("$backendUrl/file/$filename").build()
                    client.newCall(fileReq).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            runOnUiThread { statusText.text = "Failed to fetch file: ${e.message}" }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                val uri = saveToGallery(bytes, filename, type == "image")
                                addToHistory(platform, filename, uri)
                                runOnUiThread { statusText.text = "Saved: $filename" }
                            } else {
                                runOnUiThread { statusText.text = "Failed: empty file" }
                            }
                        }
                    })
                }
            })
        }

        setContentView(layout)
    }
}
