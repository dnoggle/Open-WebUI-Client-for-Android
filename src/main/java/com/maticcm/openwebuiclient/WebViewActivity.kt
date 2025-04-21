package com.maticcm.openwebuiclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.maticcm.openwebuiclient.databinding.ActivityWebviewBinding
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding
    private lateinit var baseUrl: String
    private var isWebViewReady = false
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val CONNECTION_TIMEO = 30000L // 30 seconds timeout
    private val timeoutRunnable = Runnable {
        if (!isWebViewReady) {
            Log.e("WebViewActivity", "Connection timeout")
            binding.progressBar.isVisible = false
            showConnectionError()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val singleFileChooserLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            filePathCallback?.onReceiveValue(arrayOf(it))
        } ?: run {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
        cameraImageUri = null
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize WebView settings globally for better performance
        WebView.enableSlowWholeDocumentDraw()
        
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the saved URL
        baseUrl = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            .getString("SAVED_URL", null) ?: return

        setupWebView()
        handleIntent(intent)

        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            // Basic settings
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            
            // File upload settings
            allowContentAccess = true
            allowFileAccess = true
            
            // Display settings
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Performance optimizations
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Enable hardware acceleration
        binding.webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        // Set up WebViewClient with error handling
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                binding.progressBar.isVisible = false
                binding.webView.alpha = 1f
                isWebViewReady = true
                Log.d("WebViewActivity", "WebView loaded: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                timeoutHandler.removeCallbacks(timeoutRunnable)
                Log.e("WebViewActivity", "WebView error: ${error?.description}")
                showConnectionError()
            }

            @Deprecated("Deprecated in Java", ReplaceWith("shouldOverrideUrlLoading(view, request)"))
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

        // Set up WebChromeClient for file uploads
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@WebViewActivity.filePathCallback?.onReceiveValue(null)
                this@WebViewActivity.filePathCallback = filePathCallback

                try {
                    val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
                    
                    // Check if this is a camera capture request
                    val isCapture = acceptTypes.any { it.contains("capture") || it.contains("image/*") }
                    
                    if (isCapture && fileChooserParams?.isCaptureEnabled == true) {
                        // Handle camera capture
                        val imageFile = createImageFile()
                        cameraImageUri = FileProvider.getUriForFile(
                            this@WebViewActivity,
                            "${packageName}.provider",
                            imageFile
                        )
                        cameraLauncher.launch(cameraImageUri)
                    } else {
                        // Handle file selection
                        if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            fileChooserLauncher.launch("*/*")
                        } else {
                            singleFileChooserLauncher.launch("*/*")
                        }
                    }
                    return true
                } catch (e: Exception) {
                    Log.e("WebViewActivity", "Error launching file chooser", e)
                    filePathCallback?.onReceiveValue(null)
                    this@WebViewActivity.filePathCallback = null
                    return false
                }
            }
        }

        binding.progressBar.isVisible = true
        
        // Start timeout timer
        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEO)
        
        // Load the URL
        binding.webView.loadUrl(baseUrl)
    }

    private fun injectCompatibilityScript() {
        val javascript = """
            // Universal button click handler
            function handleButtonClick(e) {
                const target = e.target;
                if (target.tagName === 'BUTTON' || 
                    target.type === 'button' || 
                    target.getAttribute('role') === 'button' ||
                    target.classList.contains('button')) {
                    e.preventDefault();
                    const clickEvent = new MouseEvent('click', {
                        view: window,
                        bubbles: true,
                        cancelable: true
                    });
                    target.dispatchEvent(clickEvent);
                }
            }

            // Universal form submission handler
            function handleFormSubmit(e) {
                if (e.target.tagName === 'FORM') {
                    e.preventDefault();
                    const submitEvent = new Event('submit', {
                        bubbles: true,
                        cancelable: true
                    });
                    e.target.dispatchEvent(submitEvent);
                }
            }

            // Add event listeners
            document.addEventListener('click', handleButtonClick, true);
            document.addEventListener('submit', handleFormSubmit, true);

            // Fix for dynamically added elements
            const observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) { // Element node
                                if (node.tagName === 'BUTTON' || 
                                    node.type === 'button' || 
                                    node.getAttribute('role') === 'button' ||
                                    node.classList.contains('button')) {
                                    node.addEventListener('click', handleButtonClick);
                                }
                                if (node.tagName === 'FORM') {
                                    node.addEventListener('submit', handleFormSubmit);
                                }
                            }
                        });
                    }
                });
            });

            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        """.trimIndent()
        
        binding.webView.evaluateJavascript(javascript, null)
    }

    private fun showConnectionError() {
        Log.e("WebViewActivity", "Connection failed or timed out")
        binding.webView.loadUrl("about:blank")
    }

    private fun handleIntent(intent: Intent) {
        Log.d("WebViewActivity", "Handling intent: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_SEND -> {
                when {
                    intent.type?.startsWith("image/") == true -> {
                        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
                        imageUri?.let { uri ->
                            Log.d("WebViewActivity", "Received image URI: $uri")
                            if (isWebViewReady) {
                                sendImageToWebView(uri)
                            } else {
                                binding.webView.post {
                                    sendImageToWebView(uri)
                                }
                            }
                        }
                    }
                    intent.type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        text?.let {
                            Log.d("WebViewActivity", "Received text: $it")
                            if (isWebViewReady) {
                                sendTextToWebView(it)
                            } else {
                                binding.webView.post {
                                    sendTextToWebView(it)
                                }
                            }
                        }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
                imageUris?.let { uris ->
                    Log.d("WebViewActivity", "Received multiple images: ${uris.size}")
                    if (isWebViewReady) {
                        sendMultipleImagesToWebView(uris)
                    } else {
                        binding.webView.post {
                            sendMultipleImagesToWebView(uris)
                        }
                    }
                }
            }
        }
    }

    private fun sendImageToWebView(uri: Uri) {
        try {
            val shareData = JSONObject().apply {
                put("type", "image")
                put("uri", uri.toString())
                put("model", getSelectedModel())
                put("prefilledMessage", getPrefilledMessage("image"))
            }
            Log.d("WebViewActivity", "Sending image data: ${shareData.toString()}")
            injectShareData(shareData)
        } catch (e: Exception) {
            Log.e("WebViewActivity", "Error sending image to WebView", e)
        }
    }

    private fun sendTextToWebView(text: String) {
        try {
            val shareData = JSONObject().apply {
                put("type", "text")
                put("content", text)
                put("model", getSelectedModel())
                put("prefilledMessage", getPrefilledMessage("text"))
            }
            Log.d("WebViewActivity", "Sending text data: ${shareData.toString()}")
            injectShareData(shareData)
        } catch (e: Exception) {
            Log.e("WebViewActivity", "Error sending text to WebView", e)
        }
    }

    private fun sendMultipleImagesToWebView(uris: ArrayList<Uri>) {
        try {
            val uriStrings = uris.map { it.toString() }
            val shareData = JSONObject().apply {
                put("type", "multiple_images")
                put("uris", uriStrings)
                put("model", getSelectedModel())
                put("prefilledMessage", getPrefilledMessage("image"))
            }
            Log.d("WebViewActivity", "Sending multiple images data: ${shareData.toString()}")
            injectShareData(shareData)
        } catch (e: Exception) {
            Log.e("WebViewActivity", "Error sending multiple images to WebView", e)
        }
    }

    private fun injectShareData(shareData: JSONObject) {
        try {
            val javascript = """
                if (window.handleSharedContent) {
                    window.handleSharedContent(${shareData.toString()});
                } else {
                    console.error('handleSharedContent function not found');
                }
            """.trimIndent()
            
            Log.d("WebViewActivity", "Injecting JavaScript: $javascript")
            binding.webView.evaluateJavascript(javascript) { result ->
                Log.d("WebViewActivity", "JavaScript evaluation result: $result")
            }
        } catch (e: Exception) {
            Log.e("WebViewActivity", "Error injecting share data", e)
        }
    }

    private fun getSelectedModel(): String {
        return getSharedPreferences("OpenWebUIClient", MODE_PRIVATE)
            .getString("selected_model", "default") ?: "default"
    }

    private fun getPrefilledMessage(type: String): String {
        return getSharedPreferences("OpenWebUIClient", MODE_PRIVATE)
            .getString("prefilled_message_$type", "") ?: ""
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("WebViewActivity", "New intent received")
        intent?.let { handleIntent(it) }
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        binding.webView.destroy()
        super.onDestroy()
    }
} 