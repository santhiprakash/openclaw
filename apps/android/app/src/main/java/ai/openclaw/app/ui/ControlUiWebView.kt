package ai.openclaw.app.ui

import ai.openclaw.app.NodeRuntime
import ai.openclaw.app.gateway.normalizeGatewayTlsFingerprintInput
import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Locale

private const val X509_CERTIFICATE_BUNDLE_KEY = "x509-certificate"

/** Shared authenticated host for gateway-served Control UI pages. */
@SuppressLint("SetJavaScriptEnabled")
// Deprecated file-URL settings are still force-disabled defensively, like the canvas host.
@Suppress("DEPRECATION")
@Composable
internal fun ControlUiWebView(
  page: NodeRuntime.GatewayControlPage,
  pageUrl: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val webViewRef = remember { arrayOfNulls<WebView>(1) }

  DisposableEffect(Unit) {
    onDispose {
      val webView = webViewRef[0] ?: return@onDispose
      webView.stopLoading()
      webView.destroy()
      webViewRef[0] = null
    }
  }

  AndroidView(
    modifier = modifier,
    factory = {
      val webView = WebView(context)
      val webSettings = webView.settings
      webSettings.setAllowContentAccess(false)
      webSettings.setAllowFileAccess(false)
      webSettings.setAllowFileAccessFromFileURLs(false)
      webSettings.setAllowUniversalAccessFromFileURLs(false)
      webSettings.setSafeBrowsingEnabled(true)
      webSettings.javaScriptEnabled = true
      webSettings.domStorageEnabled = true
      webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      webSettings.builtInZoomControls = false
      webSettings.displayZoomControls = false
      webSettings.setSupportZoom(false)
      if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false)
      }
      webView.overScrollMode = View.OVER_SCROLL_NEVER
      webView.webViewClient = ControlUiWebViewClient(page)
      installControlUiAuthScript(webView, page)
      webView.loadUrl(pageUrl)
      webViewRef[0] = webView
      webView
    },
  )
}

internal fun controlUiPageUrl(
  baseUrl: String,
  relativePathAndQuery: String,
): String = "${baseUrl.trimEnd('/')}/${relativePathAndQuery.trimStart('/')}"

private fun installControlUiAuthScript(
  webView: WebView,
  page: NodeRuntime.GatewayControlPage,
) {
  if (page.token == null && page.password == null) return
  if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
  val allowedOrigin = controlUiOrigin(page.baseUrl) ?: return
  val gatewayUrl = controlUiWebSocketUrl(page.baseUrl) ?: return
  val payload =
    buildJsonObject {
      put("gatewayUrl", gatewayUrl)
      page.token?.let { put("token", it) }
      page.password?.let { put("password", it) }
    }
  val script =
    """
    (() => {
      try {
        Object.defineProperty(window, "__OPENCLAW_NATIVE_CONTROL_AUTH__", {
          value: $payload,
          configurable: true,
        });
      } catch (e) {}
    })();
    """.trimIndent()
  WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(allowedOrigin))
}

private class ControlUiWebViewClient(
  private val page: NodeRuntime.GatewayControlPage,
) : WebViewClient() {
  override fun onReceivedSslError(
    view: WebView,
    handler: SslErrorHandler,
    error: SslError,
  ) {
    val encodedCertificate =
      SslCertificate
        .saveState(error.certificate)
        ?.getByteArray(X509_CERTIFICATE_BUNDLE_KEY)
    if (
      shouldProceedForPinnedControlUiSslError(
        pageBaseUrl = page.baseUrl,
        expectedFingerprint = page.tlsFingerprintSha256,
        errorUrl = error.url,
        encodedCertificate = encodedCertificate,
      )
    ) {
      // The native gateway connection already accepted this exact certificate.
      // Never extend the exception to another origin or a different certificate.
      handler.proceed()
    } else {
      handler.cancel()
    }
  }
}

internal fun shouldProceedForPinnedControlUiSslError(
  pageBaseUrl: String,
  expectedFingerprint: String?,
  errorUrl: String?,
  encodedCertificate: ByteArray?,
): Boolean {
  val expected = expectedFingerprint?.let(::normalizeGatewayTlsFingerprintInput) ?: return false
  val certificate = encodedCertificate ?: return false
  if (!sameHttpsOrigin(pageBaseUrl, errorUrl)) return false
  return MessageDigest
    .getInstance("SHA-256")
    .digest(certificate)
    .joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) } == expected
}

private fun sameHttpsOrigin(
  pageBaseUrl: String,
  errorUrl: String?,
): Boolean {
  val pageOrigin = parsedHttpsOrigin(pageBaseUrl) ?: return false
  val errorOrigin = errorUrl?.let(::parsedHttpsOrigin) ?: return false
  return pageOrigin == errorOrigin
}

private data class HttpsOrigin(
  val host: String,
  val port: Int,
)

private fun parsedHttpsOrigin(rawUrl: String): HttpsOrigin? {
  val uri = Uri.parse(rawUrl)
  if (!uri.scheme.equals("https", ignoreCase = true)) return null
  val host = uri.host?.lowercase(Locale.US) ?: return null
  val port = uri.port.takeIf { it >= 0 } ?: 443
  return HttpsOrigin(host = host, port = port)
}

private fun controlUiOrigin(baseUrl: String): String? {
  val uri = Uri.parse(baseUrl)
  val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
  val authority = uri.encodedAuthority ?: return null
  return "$scheme://$authority"
}

private fun controlUiWebSocketUrl(baseUrl: String): String? {
  val uri = Uri.parse(baseUrl)
  val scheme =
    when (uri.scheme?.lowercase(Locale.US)) {
      "https" -> "wss"
      "http" -> "ws"
      else -> return null
    }
  return uri
    .buildUpon()
    .scheme(scheme)
    .build()
    .toString()
}
