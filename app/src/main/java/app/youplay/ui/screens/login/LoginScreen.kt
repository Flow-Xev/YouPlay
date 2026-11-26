package app.youplay.ui.screens.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import app.youplay.ui.components.YpIconTap
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONTokener

@HiltViewModel
class LoginViewModel @Inject constructor(private val repo: VideoRepository) : ViewModel() {
    fun signIn(cookie: String, visitor: String?, sync: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            try { repo.signIn(cookie, visitor, sync) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { }
            onDone()
        }
    }
}

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=" +
        "https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3D%252F"

// Google blocks the default Android WebView UA for sign-in, so present a desktop browser.
private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val finished = remember { AtomicBoolean(false) }
    // Navigate on the next main-loop tick so the WebView isn't torn down inside its own callback.
    val done = remember { { Handler(Looper.getMainLooper()).post { onDone() }; Unit } }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            YpIconTap(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text("Sign in to YouTube", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    val web = this
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = DESKTOP_UA
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(web, true)
                    }
                    webViewClient = object : WebViewClient() {
                        // Renderer crash/kill would otherwise take the whole app down.
                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true

                        override fun onPageFinished(view: WebView, url: String?) {
                            try {
                                if (finished.get() || url == null) return
                                if (!url.startsWith("https://www.youtube.com") && !url.startsWith("https://m.youtube.com")) return
                                val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return
                                if (!cookie.contains("SAPISID")) return
                                CookieManager.getInstance().flush()
                                val js = "(function(){try{return JSON.stringify([ytcfg.get('VISITOR_DATA')||''," +
                                    "ytcfg.get('DATASYNC_ID')||''])}catch(e){return ''}})()"
                                view.evaluateJavascript(js) { raw ->
                                    if (!finished.compareAndSet(false, true)) return@evaluateJavascript
                                    var visitor: String? = null
                                    var sync: String? = null
                                    runCatching {
                                        val arr = JSONArray(JSONTokener(raw).nextValue() as String)
                                        visitor = arr.optString(0).ifBlank { null }
                                        sync = arr.optString(1).ifBlank { null }
                                    }
                                    viewModel.signIn(cookie, visitor, sync, done)
                                }
                            } catch (_: Throwable) { }
                        }
                    }
                    // Start from a clean web session so "Add account" shows Google's sign-in form instead of silently
                    // re-using the account that is already logged in. Saved accounts live in the app, not in the WebView.
                    CookieManager.getInstance().removeAllCookies { web.post { web.loadUrl(LOGIN_URL) } }
                    android.webkit.WebStorage.getInstance().deleteAllData()
                }
            },
            onRelease = { w ->
                runCatching {
                    w.stopLoading(); w.webViewClient = WebViewClient()
                    (w.parent as? ViewGroup)?.removeView(w)
                    w.destroy()
                }
            },
        )
    }
}
