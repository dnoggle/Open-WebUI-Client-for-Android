package com.maticcm.openwebuiclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.maticcm.openwebuiclient.databinding.ActivityWebviewBinding

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding
    private lateinit var baseUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize WebView settings globally for better performance
        WebView.enableSlowWholeDocumentDraw()
        
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("URL") ?: return
        baseUrl = url
        setupWebView(url)
    }

    private fun setupWebView(url: String) {
        binding.webView.apply {
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // Performance optimizations
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // Enable hardware acceleration
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.progressBar.isVisible = false
                    binding.webView.alpha = 1f // Remove animation for faster display
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url?.startsWith(baseUrl) == true) {
                        return false
                    }
                    url?.let {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                        startActivity(intent)
                    }
                    return true
                }
            }

            binding.progressBar.isVisible = true
            loadUrl(url)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onDestroy() {
        binding.webView.destroy() // Clean up WebView
        super.onDestroy()
    }
} 