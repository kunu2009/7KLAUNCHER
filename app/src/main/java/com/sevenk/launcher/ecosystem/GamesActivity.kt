package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GamesActivity : AppCompatActivity() {

    private var score = 0
    private var remainingSeconds = 10
    private var running = false
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K Games"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val titleView = TextView(this).apply {
            text = "Tap Sprint"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val infoView = TextView(this).apply {
            text = "Tap as fast as you can in 10 seconds"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }

        val scoreView = TextView(this).apply {
            text = "Score: 0"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        val timerView = TextView(this).apply {
            text = "Time: 10s"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val startButton = Button(this).apply {
            text = "Start Game"
            isAllCaps = false
            setOnClickListener {
                if (!running) startGame(scoreView, timerView, text)
            }
        }

        val tapButton = Button(this).apply {
            text = "TAP!"
            isAllCaps = false
            textSize = 24f
            isEnabled = false
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                if (running) {
                    score++
                    scoreView.text = "Score: $score"
                    animate().scaleX(0.94f).scaleY(0.94f).setDuration(50).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(50).start()
                    }.start()
                }
            }
        }

        val bestView = TextView(this).apply {
            val best = getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE).getInt("games_tap_best", 0)
            text = "Best: $best"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        container.addView(titleView)
        container.addView(infoView)
        container.addView(scoreView)
        container.addView(timerView)
        container.addView(startButton)
        container.addView(tapButton)
        container.addView(bestView)

        setContentView(container)
    }

    private fun startGame(scoreView: TextView, timerView: TextView, startButtonText: CharSequence) {
        score = 0
        remainingSeconds = 10
        running = true
        scoreView.text = "Score: 0"

        val root = findViewById<LinearLayout>(android.R.id.content).getChildAt(0) as LinearLayout
        val startButton = root.getChildAt(4) as Button
        val tapButton = root.getChildAt(5) as Button
        val bestView = root.getChildAt(6) as TextView

        startButton.isEnabled = false
        tapButton.isEnabled = true

        timer?.cancel()
        timer = object : CountDownTimer(10_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
                timerView.text = "Time: ${remainingSeconds}s"
            }

            override fun onFinish() {
                running = false
                tapButton.isEnabled = false
                startButton.isEnabled = true
                timerView.text = "Time: 0s"
                startButton.text = "Play Again"

                val prefs = getSharedPreferences("sevenk_launcher_prefs", MODE_PRIVATE)
                val best = prefs.getInt("games_tap_best", 0)
                if (score > best) {
                    prefs.edit().putInt("games_tap_best", score).apply()
                    bestView.text = "Best: $score (new)"
                } else {
                    bestView.text = "Best: $best"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
