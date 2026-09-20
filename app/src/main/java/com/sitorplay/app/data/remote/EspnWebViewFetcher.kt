package com.sitorplay.app.data.remote

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ESPN_FANTASY_HOME = "https://fantasy.espn.com/"
private const val BRIDGE_NAME = "SitOrPlayBridge"

/**
 * Fetches a URL by running `fetch()` inside a real (invisible) WebView instead of a plain HTTP
 * client. ESPN's fantasy site sits behind bot-mitigation that a bare OkHttp/Retrofit request
 * can't pass no matter which cookies it carries — a real WebView runs real JavaScript and shares
 * Android's persistent [android.webkit.CookieManager] session from [EspnLoginDialog], so a
 * `fetch()` issued from inside the page behaves exactly like the site's own requests.
 */
@Singleton
class EspnWebViewFetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Must be called from the main thread — WebView requires it. viewModelScope defaults there. */
    suspend fun fetchJson(url: String): String = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        fun finish(block: () -> Unit) {
            if (continuation.isActive) block()
            webView.stopLoading()
            webView.destroy()
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onResult(json: String) = finish { continuation.resume(json) }

                @JavascriptInterface
                fun onError(message: String) =
                    finish { continuation.resumeWithException(IOException("ESPN fetch failed: $message")) }
            },
            BRIDGE_NAME
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loadedUrl: String) {
                if (loadedUrl != ESPN_FANTASY_HOME) return
                view.evaluateJavascript(fetchScript(url), null)
            }
        }
        webView.loadUrl(ESPN_FANTASY_HOME)

        continuation.invokeOnCancellation {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun fetchScript(url: String): String = """
        (function() {
            fetch(${jsStringLiteral(url)}, { credentials: 'include', headers: { 'Accept': 'application/json' } })
                .then(function(r) { return r.text(); })
                .then(function(t) { $BRIDGE_NAME.onResult(t); })
                .catch(function(e) { $BRIDGE_NAME.onError(String(e)); });
        })();
    """.trimIndent()

    private fun jsStringLiteral(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
