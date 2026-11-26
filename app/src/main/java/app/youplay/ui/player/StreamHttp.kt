package app.youplay.ui.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource

/**
 * googlevideo URLs are bound to the InnerTube client that produced them (`c=` query parameter): a request with a
 * different User-Agent is answered with HTTP 403, which ExoPlayer reports as a bare "Source error".
 * This factory sends the User-Agent that matches each URL's client.
 */
@UnstableApi
object StreamHttp {
    private fun uaFor(client: String?): String = app.youplay.data.remote.InnerTubeClients.userAgentForParam(client)

    fun factory(context: Context, transferListener: androidx.media3.datasource.TransferListener? = null): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory() // no fixed UA: the resolver sets it per URL
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val resolving = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec: DataSpec ->
            val scheme = spec.uri.scheme
            if (scheme != "http" && scheme != "https") spec
            else {
                val client = spec.uri.getQueryParameter("c")
                val headers = HashMap(spec.httpRequestHeaders)
                headers["User-Agent"] = uaFor(client)
                if (client == null || client.equals("WEB", true) || client.contains("WEB_", true)) {
                    headers["Origin"] = "https://www.youtube.com"
                    headers["Referer"] = "https://www.youtube.com/"
                }
                spec.withRequestHeaders(headers)
            }
        })
        val f = DefaultDataSource.Factory(context, resolving)
        if (transferListener != null) f.setTransferListener(transferListener)
        return f
    }

    /** "Source error (HTTP 403, client IOS)" instead of a bare "Source error". */
    fun describe(e: PlaybackException): String {
        var t: Throwable? = e
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) {
                val c = t.dataSpec.uri.getQueryParameter("c")
                return "${e.message} (HTTP ${t.responseCode}${if (c != null) ", client $c" else ""})"
            }
            t = t.cause
        }
        val inner = e.cause?.message
        return if (inner.isNullOrBlank()) "${e.message} [${e.errorCodeName}]" else "${e.message} [${e.errorCodeName}: $inner]"
    }
}
