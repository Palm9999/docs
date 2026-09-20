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
 * Fetches a URL by running `fetch()` inside a real (invisible) WebView instead of a plain HTTP
 * client. ESPN's fantasy site sits behind bot-mitigation that a bare OkHttp/Retrofit request
 * can't pass no matter which cookies it carries — a real WebView runs real JavaScript and shares
 * Android's persistent [android.webkit.CookieManager] session from [EspnLoginDialog], so a
 * `fetch()` issued from inside the page behaves exactly like the site's own requests.
 *
 * The WebView navigates to [url] itself first (rendering ESPN's app shell, which we ignore) so
 * the follow-up `fetch()` call to that same URL is guaranteed same-origin — issuing it from an
 * unrelated page (e.g. the site's homepage, which may itself land on a different subdomain)
 * makes it cross-origin and browsers block reading the response, surfacing as "Failed to fetch"
 * with no other detail.
 */
@Singleton
class EspnWebViewFetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Must be called from the main thread — WebView requires it. viewModelScope defaults there. */
    suspend fun fetchJson(url: String): String = withTimeout(FETCH_TIMEOUT_MILLIS) {
        fetchJsonOnce(url)
    }

    private suspend fun fetchJsonOnce(url: String): String = suspendCancellableCoroutine { continuation ->
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
                view.evaluateJavascript(fetchScript(url), null)
            }
        }
        webView.loadUrl(url)

        continuation.invokeOnCancellation {
            finished = true
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun fetchScript(url: String): String = """
        (function() {
            try {
                fetch(${jsStringLiteral(url)}, { credentials: 'include', headers: { 'Accept': 'application/json' } })
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
