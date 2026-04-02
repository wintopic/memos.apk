package com.usememos.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.usememos.android.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding

  private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
  private var pendingPermissionAction: PendingPermissionAction? = null
  private var baseUrl: String? = null

  private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val callback = fileChooserCallback ?: return@registerForActivityResult
    val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
    callback.onReceiveValue(uris)
    fileChooserCallback = null
  }

  private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
    val granted = grants.values.all { it }
    when (val action = pendingPermissionAction) {
      is PendingPermissionAction.Geolocation -> {
        action.callback.invoke(action.origin, granted, false)
      }

      is PendingPermissionAction.WebPermission -> {
        if (granted) {
          action.request.grant(action.request.resources)
        } else {
          action.request.deny()
        }
      }

      null -> Unit
    }
    pendingPermissionAction = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    configureWebView()
    configureBackNavigation()
    startLocalApp()
  }

  override fun onDestroy() {
    if (isFinishing) {
      runCatching { GoBackend.stop() }
    }
    binding.webView.apply {
      stopLoading()
      webChromeClient = WebChromeClient()
      webViewClient = WebViewClient()
      destroy()
    }
    super.onDestroy()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebView() {
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
    }

    binding.webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowContentAccess = true
      allowFileAccess = false
      setSupportMultipleWindows(false)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = true
      }
    }

    binding.webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return handleNavigation(uri)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        showOverlay(false)
      }

      override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && errorResponse != null) {
          showError(getString(R.string.error_http, errorResponse.statusCode))
        }
      }
    }

    binding.webView.webChromeClient = object : WebChromeClient() {
      override fun onPermissionRequest(request: PermissionRequest?) {
        val safeRequest = request ?: return
        runOnUiThread {
          val permissions = buildRuntimePermissions(safeRequest) ?: run {
            safeRequest.deny()
            return@runOnUiThread
          }

          if (permissions.all { permission -> hasPermission(permission) }) {
            safeRequest.grant(safeRequest.resources)
            return@runOnUiThread
          }

          pendingPermissionAction = PendingPermissionAction.WebPermission(safeRequest)
          permissionLauncher.launch(permissions.toTypedArray())
        }
      }

      override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (origin == null || callback == null) {
          return
        }

        val permissions = arrayOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (permissions.all(::hasPermission)) {
          callback.invoke(origin, true, false)
          return
        }

        pendingPermissionAction = PendingPermissionAction.Geolocation(origin, callback)
        permissionLauncher.launch(permissions)
      }

      override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
      ): Boolean {
        val callback = filePathCallback ?: return false
        this@MainActivity.fileChooserCallback?.onReceiveValue(null)
        this@MainActivity.fileChooserCallback = callback

        val chooserIntent = runCatching { fileChooserParams?.createIntent() }.getOrNull() ?: buildFallbackChooserIntent()
        return try {
          fileChooserLauncher.launch(chooserIntent)
          true
        } catch (_: ActivityNotFoundException) {
          this@MainActivity.fileChooserCallback?.onReceiveValue(null)
          this@MainActivity.fileChooserCallback = null
          false
        }
      }
    }

    binding.retryButton.setOnClickListener {
      startLocalApp()
    }
  }

  private fun configureBackNavigation() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (binding.webView.canGoBack()) {
          binding.webView.goBack()
          return
        }
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
      }
    })
  }

  private fun startLocalApp() {
    binding.retryButton.visibility = View.GONE
    showLoading(getString(R.string.loading_starting))

    lifecycleScope.launch {
      try {
        val installed = withContext(Dispatchers.Default) { GoBackend.isInstalled() }
        if (!installed) {
          showError(getString(R.string.error_missing_bindings))
          return@launch
        }

        val resolvedBaseUrl = withContext(Dispatchers.IO) {
          GoBackend.start(resolveDataDir().absolutePath, DEFAULT_PORT)
        }
        baseUrl = resolvedBaseUrl
        waitForServer(resolvedBaseUrl)
        binding.webView.loadUrl(resolvedBaseUrl)
      } catch (error: Exception) {
        showError(error.message ?: getString(R.string.error_unknown))
      }
    }
  }

  private suspend fun waitForServer(baseUrl: String) {
    val healthzUrl = "$baseUrl/healthz"
    repeat(40) {
      val ready = withContext(Dispatchers.IO) { isServerReady(healthzUrl) }
      if (ready) {
        return
      }
      delay(250)
    }
    error(getString(R.string.error_server_timeout))
  }

  private fun isServerReady(url: String): Boolean {
    val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return false
    return try {
      connection.requestMethod = "GET"
      connection.connectTimeout = 1000
      connection.readTimeout = 1000
      connection.responseCode in 200..299
    } catch (_: Exception) {
      false
    } finally {
      connection.disconnect()
    }
  }

  private fun handleNavigation(uri: Uri): Boolean {
    val currentBaseUrl = baseUrl ?: return false
    val baseUri = Uri.parse(currentBaseUrl)
    val isLocalOrigin = uri.scheme == baseUri.scheme && uri.host == baseUri.host && uri.port == baseUri.port
    if (isLocalOrigin || uri.host == "127.0.0.1" || uri.host == "localhost") {
      return false
    }

    startActivity(Intent(Intent.ACTION_VIEW, uri))
    return true
  }

  private fun buildRuntimePermissions(request: PermissionRequest): List<String>? {
    val permissions = mutableSetOf<String>()
    request.resources.forEach { resource ->
      when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> permissions += Manifest.permission.RECORD_AUDIO
        else -> return null
      }
    }
    return permissions.toList()
  }

  private fun resolveDataDir(): File = File(filesDir, "memos")

  private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

  private fun showLoading(message: String) {
    binding.statusText.text = message
    binding.progressIndicator.visibility = View.VISIBLE
    binding.retryButton.visibility = View.GONE
    showOverlay(true)
  }

  private fun showError(message: String) {
    binding.statusText.text = message
    binding.progressIndicator.visibility = View.GONE
    binding.retryButton.visibility = View.VISIBLE
    showOverlay(true)
  }

  private fun showOverlay(visible: Boolean) {
    binding.overlay.visibility = if (visible) View.VISIBLE else View.GONE
  }

  private fun buildFallbackChooserIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = "*/*"
    }

  private sealed interface PendingPermissionAction {
    data class Geolocation(val origin: String, val callback: GeolocationPermissions.Callback) : PendingPermissionAction

    data class WebPermission(val request: PermissionRequest) : PendingPermissionAction
  }

  companion object {
    private const val DEFAULT_PORT = 5230
  }
}
