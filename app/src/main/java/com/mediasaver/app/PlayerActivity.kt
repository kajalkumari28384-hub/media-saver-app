package com.mediasaver.app

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.getStringExtra("uri")?.let { Uri.parse(it) }
        val type = intent.getStringExtra("type") ?: "video"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(android.graphics.Color.BLACK)
        root.setPadding(20, 20, 20, 20)

        if (uri == null) {
            val error = TextView(this)
            error.text = "Media not available"
            error.setTextColor(android.graphics.Color.WHITE)
            root.addView(error)
            setContentView(root)
            return
        }

        if (type == "image") {
            val image = ImageView(this)
            image.setImageURI(uri)
            image.adjustViewBounds = true
            image.scaleType = ImageView.ScaleType.FIT_CENTER

            root.addView(
                image,
                LinearLayout.LayoutParams(-1, 0, 1f)
            )
        } else {
            val video = VideoView(this)
            video.setVideoURI(uri)
            video.setMediaController(MediaController(this))
            video.start()

            root.addView(
                video,
                LinearLayout.LayoutParams(-1, 0, 1f)
            )
        }

        setContentView(root)
    }
}
