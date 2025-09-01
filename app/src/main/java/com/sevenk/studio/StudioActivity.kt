package com.sevenk.studio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sevenk.launcher.R

class StudioActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_studio)

        findViewById<android.view.View>(R.id.btnPhoto).setOnClickListener {
            startActivity(Intent(this, PhotoEditorActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnVideo).setOnClickListener {
            startActivity(Intent(this, VideoEditorActivity::class.java))
        }
    }
}
