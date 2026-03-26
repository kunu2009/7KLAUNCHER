package com.sevenk.launcher.ecosystem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StudioTemplatesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Studio Templates"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101010.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K Studio Templates"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })

        content.addView(TextView(this).apply {
            text = "Quick-start looks for photo + video edits"
            textSize = 13f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(0, 8, 0, 16)
        })

        val templates = listOf(
            "Cinematic Contrast",
            "Portrait Soft Glow",
            "Vibrant Social Boost",
            "Night Clarity",
            "Document Clean Scan",
            "Retro Film Fade",
            "Sports Action Sharp"
        )

        templates.forEach { template ->
            val btn = Button(this).apply {
                text = template
                isAllCaps = false
                setOnClickListener { openStudio(template) }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10
            content.addView(btn, lp)
        }

        val hint = TextView(this).apply {
            text = "Template selections are wired for Phase 3 deep integration with Studio parameter presets."
            textSize = 12f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, 16, 0, 0)
        }
        content.addView(hint)

        root.addView(content)
        setContentView(root)
    }

    private fun openStudio(templateName: String) {
        val i = Intent(this, com.sevenk.studio.StudioActivity::class.java).apply {
            putExtra("studio_template_name", templateName)
        }
        startActivity(i)
    }
}
