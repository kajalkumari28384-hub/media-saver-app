package com.mediasaver.app

import android.os.Bundle
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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
    private val backendUrl = "https://media-saver-app-cu8d.onrender.com"

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

        button.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isEmpty()) {
                statusText.text = "Please paste a link"
                return@setOnClickListener
            }
            statusText.text = "Downloading... (server is processing, please wait, can take 1-2 min)"

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
                    runOnUiThread { statusText.text = "Done!\n$res" }
                }
            })
        }

        setContentView(layout)
    }
}
