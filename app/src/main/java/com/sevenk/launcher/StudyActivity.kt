package com.sevenk.launcher

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class StudyActivity : AppCompatActivity() {
    
    private lateinit var subjectsContainer: LinearLayout
    private lateinit var webViewContainer: LinearLayout
    private lateinit var webView: WebView
    private lateinit var backButton: ImageButton
    private lateinit var urlText: TextView
    
    // Study subjects data
    private val studySubjects = listOf(
        StudySubject("Law", "7klwprep.me", "https://7klwprep.me", R.drawable.ic_study_law),
        StudySubject("Sanskrit", "polyglot.7kc.me", "https://polyglot.7kc.me", R.drawable.ic_study_sanskrit),
        StudySubject("History", "his.7kc.me", "https://his.7kc.me", R.drawable.ic_study_history),
        StudySubject("Economics", "eco.7kc.me", "https://eco.7kc.me", R.drawable.ic_study_economics),
        StudySubject("Political Science", "pol.7kc.me", "https://pol.7kc.me", R.drawable.ic_study_politics)
    )
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d("StudyActivity", "Starting onCreate")
            setContentView(R.layout.activity_study)
            Log.d("StudyActivity", "Layout set successfully")
            
            supportActionBar?.title = "7K Study"
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            
            initializeViews()
            Log.d("StudyActivity", "Views initialized")
            
            setupWebView()
            Log.d("StudyActivity", "WebView setup complete")
            
            createSubjectCards()
            Log.d("StudyActivity", "Subject cards created")

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webViewContainer.visibility == View.VISIBLE) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            showSubjects()
                        }
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("StudyActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error loading Study activity: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initializeViews() {
        try {
            Log.d("StudyActivity", "Finding views...")
            subjectsContainer = findViewById(R.id.subjectsContainer)
            webViewContainer = findViewById(R.id.webViewContainer)
            webView = findViewById(R.id.studyWebView)
            backButton = findViewById(R.id.backToSubjects)
            urlText = findViewById(R.id.currentUrl)

            backButton.setOnClickListener {
                showSubjects()
            }
            Log.d("StudyActivity", "All views initialized successfully")
        } catch (e: Exception) {
            Log.e("StudyActivity", "Error initializing views", e)
            throw e
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        Log.d("StudyActivity", "Setting up WebView...")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            // Improved security settings
            allowFileAccess = false
            allowContentAccess = false
            // Mixed content handling
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        Log.d("StudyActivity", "WebView settings configured")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    view?.loadUrl(url)
                    urlText.text = url
                    true
                } else {
                    false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlText.text = url ?: ""
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("StudyActivity", "WebView error: ${error?.description}")
            }
        }
        Log.d("StudyActivity", "WebView client configured")
    }
    
    private fun createSubjectCards() {
        try {
            // Get the actual cards container from the ScrollView
            val cardsContainer = findViewById<LinearLayout>(R.id.subjectsCardsContainer)
            if (cardsContainer == null) {
                android.util.Log.e("StudyActivity", "subjectsCardsContainer not found")
                return
            }
            
            cardsContainer.removeAllViews()
            
            // Create cards in rows of 2
            var currentRow: LinearLayout? = null
            
            studySubjects.forEachIndexed { index, subject ->
                try {
                    if (index % 2 == 0) {
                        // Create new row
                        currentRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 16)
                            }
                        }
                        cardsContainer.addView(currentRow)
                    }
                    
                    val card = createSubjectCard(subject)
                    currentRow?.addView(card)
                } catch (e: Exception) {
                    android.util.Log.e("StudyActivity", "Error creating card for subject: ${subject.name}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StudyActivity", "Error creating subject cards", e)
        }
    }
    
    private fun createSubjectCard(subject: StudySubject): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (studySubjects.indexOf(subject) % 2 == 0) {
                    setMargins(0, 0, 8, 0)
                } else {
                    setMargins(8, 0, 0, 0)
                }
            }
            radius = 12f
            cardElevation = 6f
            setCardBackgroundColor(ContextCompat.getColor(this@StudyActivity, android.R.color.white))
        }
        
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        // Subject icon (using a placeholder for now since we need to create the icons)
        val icon = androidx.appcompat.widget.AppCompatImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 16)
            }
            try {
                setImageResource(subject.iconRes)
            } catch (e: Exception) {
                // Fallback to default app icon if resource not found
                android.util.Log.w("StudyActivity", "Icon resource not found for ${subject.name}, using default", e)
                setImageResource(R.drawable.ic_app_default)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        
        // Subject title
        val title = TextView(this).apply {
            text = subject.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@StudyActivity, android.R.color.black))
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        // Subject URL
        val url = TextView(this).apply {
            text = subject.domain
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@StudyActivity, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 0)
            }
        }
        
        cardContent.addView(icon)
        cardContent.addView(title)
        cardContent.addView(url)
        card.addView(cardContent)
        
        // Set click listener
        card.setOnClickListener {
            openSubjectWebsite(subject)
        }
        
        return card
    }
    
    private fun openSubjectWebsite(subject: StudySubject) {
        urlText.text = subject.url
        webView.loadUrl(subject.url)
        showWebView()
    }
    
    private fun showSubjects() {
        subjectsContainer.visibility = View.VISIBLE
        webViewContainer.visibility = View.GONE
    }
    
    private fun showWebView() {
        subjectsContainer.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    data class StudySubject(
        val name: String,
        val domain: String,
        val url: String,
        val iconRes: Int
    )
}