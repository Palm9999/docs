package com.sitorplay.app.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val ESPN_FANTASY_HOME = "https://fantasy.espn.com/"

/**
 * A full-screen embedded browser for logging into ESPN. A real WebView runs a real JS engine,
 * so it passes whatever bot-mitigation checks a bare HTTP client can't — the same reason a
 * regular browser tab works. The resulting session is stored in Android's own, already-persistent
 * [CookieManager] (shared by every WebView in the app), so [EspnWebViewFetcher] can reuse it
 * later without this dialog needing to hand back anything itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspnLoginDialog(onDismiss: () -> Unit, onLoggedIn: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Log in to ESPN") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            CookieManager.getInstance().flush()
                            onLoggedIn()
                        }) {
                            Text("Done")
                        }
                    }
                )
                Text(
                    "Log in below like you normally would. Once you can see your team, tap " +
                        "\"Done\" above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> createEspnWebView(context) }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createEspnWebView(context: android.content.Context): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    webViewClient = WebViewClient()
    loadUrl(ESPN_FANTASY_HOME)
}
