package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialog

class MusicActivity : AppCompatActivity() {

    private data class Playlist(val name: String, val songCount: Int)
    
    private val playlists = mutableListOf<Playlist>()
    private val prefs by lazy { getSharedPreferences("sevenk_music", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Music"

        val root = ScrollView(this).apply {
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 24, 16, 24)
        }

        // Header
        val heading = TextView(this).apply {
            text = "7K Music Player"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val sub = TextView(this).apply {
            text = "Organize & play local music files"
            textSize = 13f
            setTextColor(0xFFAAAAAAA.toInt())
            setPadding(0, 8, 0, 24)
        }

        content.addView(heading)
        content.addView(sub)

        // Now Playing Card
        val nowPlayingCard = CardView(this).apply {
            radius = 12f
            cardElevation = 4f
            setCardBackgroundColor(0xFF252525.toInt())
        }

        val nowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val playIcon = ImageView(this).apply {
            setImageDrawable(android.graphics.drawable.ShapeDrawable().apply {
                this.paint.color = 0xFF0D7377.toInt()
            })
            layoutParams = LinearLayout.LayoutParams(80, 80)
        }

        val nowTitle = TextView(this).apply {
            text = "No song playing"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 4)
        }

        val nowArtist = TextView(this).apply {
            text = "Select music from your library"
            textSize = 12f
            setTextColor(0xFFAAAAA.toInt())
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 0)
        }

        val playBtn = Button(this).apply {
            text = "▶ Play"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { Toast.makeText(this@MusicActivity, "Tap music file to play", Toast.LENGTH_SHORT).show() }
        }

        val pauseBtn = Button(this).apply {
            text = "⏸ Pause"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nextBtn = Button(this).apply {
            text = "⏭ Next"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        controls.addView(playBtn)
        controls.addView(pauseBtn)
        controls.addView(nextBtn)

        nowLayout.addView(playIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            80
        ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        nowLayout.addView(nowTitle)
        nowLayout.addView(nowArtist)
        nowLayout.addView(controls)
        nowPlayingCard.addView(nowLayout)
        content.addView(nowPlayingCard)

        // Playlists Section
        val playlistsTitle = TextView(this).apply {
            text = "Playlists"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 12)
        }
        content.addView(playlistsTitle)

        val createPlaylistBtn = Button(this).apply {
            text = "+ Create Playlist"
            isAllCaps = false
            setOnClickListener { showCreatePlaylistDialog() }
        }
        content.addView(createPlaylistBtn)

        // Example playlists
        loadPlaylists()

        playlists.forEach { playlist ->
            val card = CardView(this).apply {
                radius = 10f
                cardElevation = 2f
                setCardBackgroundColor(0xFF2D2D2D.toInt())
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
            }

            val pName = TextView(this).apply {
                text = playlist.name
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
            }

            val pCount = TextView(this).apply {
                text = "${playlist.songCount} songs"
                textSize = 11f
                setTextColor(0xFF0D7377.toInt())
            }

            layout.addView(pName)
            layout.addView(pCount)
            card.addView(layout)

            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.bottomMargin = 10
            content.addView(card, cardParams)
        }

        root.addView(content)
        setContentView(root)
    }

    private fun loadPlaylists() {
        playlists.clear()
        repeat(3) { i ->
            val count = prefs.getInt("playlist_${i}_count", (i + 1) * 5)
            playlists.add(Playlist("Favorites ${i+1}", count))
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Playlist name"
        }

        showGlassInputSheet(
            title = "Create Playlist",
            input = input,
            primaryLabel = "Create"
        ) {
                if (input.text.isNotEmpty()) {
                    playlists.add(Playlist(input.text.toString(), 0))
                    Toast.makeText(this, "Playlist created!", Toast.LENGTH_SHORT).show()
                    recreate()
                }
        }
    }

    private fun showGlassInputSheet(
        title: String,
        input: EditText,
        primaryLabel: String,
        secondaryLabel: String = "Cancel",
        onPrimary: () -> Unit
    ) {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundColor(0xFF1B1730.toInt())
        }

        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        })

        input.setTextColor(0xFFFFFFFF.toInt())
        input.setHintTextColor(0x99FFFFFF.toInt())
        input.setBackgroundColor(0xFF2A235F.toInt())
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        val inputLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(input, inputLp)

        root.addView(TextView(this).apply {
            text = primaryLabel
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF6C4BFF.toInt())
            foreground = AppCompatResources.getDrawable(this@MusicActivity, android.R.drawable.list_selector_background)
            setOnClickListener {
                onPrimary()
                sheet.dismiss()
            }
        })

        root.addView(TextView(this).apply {
            text = secondaryLabel
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sheet.dismiss() }
        })

        sheet.setContentView(root)
        sheet.setOnShowListener {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
