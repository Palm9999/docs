package com.sitorplay.app.data.remote

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val BRIDGE_NAME = "SitOrPlayBridge"
private const val FETCH_TIMEOUT_MILLIS = 20_000L

/**
 * Fetches an API URL by running `fetch()` inside a real (invisible) WebView instead of a plain
 * HTTP client, sharing Android's persistent [android.webkit.CookieManager] session set up by
 * [EspnLoginDialog].
 *
 * Navigating straight to ESPN's `apis/v3/games/ffl/...` URL as a top-level page load always
 * bounces to a generic fantasy hub page, for any client — that appears to just be how ESPN's
 * routing treats that path when it's the page itself being loaded, regardless of session
 * validity. The real site never navigates there directly either: its own JavaScript, running on
 * a normal page like a league or team page, calls that same URL via `fetch()`. So this loads a
 * real page ([pageUrl]) first, then issues the `fetch()` to [apiUrl] from within it — matching
 * exactly what the site's own code does, and same-origin since both are under fantasy.espn.com.
 */
@Singleton
class EspnWebViewFetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Must be called from the main thread — WebView requires it. viewModelScope defaults there. */
    suspend fun fetchJson(pageUrl: String, apiUrl: String): String = withTimeout(FETCH_TIMEOUT_MILLIS) {
        fetchJsonOnce(pageUrl, apiUrl)
    }

    private suspend fun fetchJsonOnce(pageUrl: String, apiUrl: String): String =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            var finished = false
            fun finish(block: () -> Unit) {
                if (finished) return
                finished = true
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
                    if (finished) return
                    view.evaluateJavascript(fetchScript(apiUrl), null)
                }
            }
            webView.loadUrl(pageUrl)

            continuation.invokeOnCancellation {
                finished = true
                webView.stopLoading()
                webView.destroy()
            }
        }

    private fun fetchScript(apiUrl: String): String = """
        (function() {
            try {
                fetch(${jsStringLiteral(apiUrl)}, { credentials: 'include', headers: { 'Accept': 'application/json' } })
                    .then(function(r) {
                        if (!r.ok) { throw new Error('HTTP ' + r.status); }
                        return r.text();
                    })
                    .then(function(t) { $BRIDGE_NAME.onResult(t); })
                    .catch(function(e) {
                        $BRIDGE_NAME.onError('from ' + window.location.href + ': ' + (e && e.message ? e.message : String(e)));
                    });
            } catch (e) {
                $BRIDGE_NAME.onError('sync error from ' + window.location.href + ': ' + String(e));
            }
        })();
    """.trimIndent()

    private fun jsStringLiteral(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
