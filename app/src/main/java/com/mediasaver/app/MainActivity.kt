package com.mediasaver.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

    private fun saveToGallery(bytes: ByteArray, filename: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MediaSaver")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    private fun addToHistory(platform: String, filename: String) {
        val prefs = getSharedPreferences("history", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val entry = JSONObject()
        entry.put("platform", platform)
        entry.put("filename", filename)
        entry.put("time", SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date()))
        arr.put(entry)
        prefs.edit().putString("items", arr.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 100, 40, 40)

        val title = TextView(this)
        title.text = "Media Saver"
        title.textSize = 24f
        layout.addView(title)

        val input = EditText(this)
        input.hint = "Paste video link here"
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            val sharedUrl = intent.getStringExtra("android.intent.extra.TEXT") ?: ""
            input.setText(sharedUrl)
        }
        layout.addView(input)

        val statusText = TextView(this)
        statusText.text = "Ready"
        statusText.setPadding(0, 30, 0, 30)
        layout.addView(statusText)

        val button = Button(this)
        button.text = "Download"
        layout.addView(button)

        val historyButton = Button(this)
        historyButton.text = "View History"
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
                    val obj = JSONObject(res)
                    val filename = obj.optString("filename", "")
                    val platform = obj.optString("platform", "unknown")
                    if (filename.isEmpty()) {
                        runOnUiThread { statusText.text = "Failed: no file returned\n$res" }
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
                                saveToGallery(bytes, filename)
                                addToHistory(platform, filename)
                                runOnUiThread { statusText.text = "Saved to gallery: $filename" }
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
