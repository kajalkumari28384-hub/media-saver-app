package com.mediasaver.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        var sharedUrl = "No link shared yet"
        if (intent?.action == "android.intent.action.SEND" && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra("android.intent.extra.TEXT") ?: sharedUrl
        }
        tv.text = "Media Saver\nBackend: media-saver-app-cu8d.onrender.com\n\nShared link: $sharedUrl"
        tv.textSize = 18f
        tv.setPadding(40, 100, 40, 40)
        setContentView(tv)
    }
}
