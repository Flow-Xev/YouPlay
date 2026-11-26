package app.youplay.data.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetKind { WIFI, ETHERNET, CELLULAR, OTHER, NONE }

/** What the connection and device look like right now; the inputs Auto quality and buffering react to. */
data class NetProfile(
    val kind: NetKind,
    val metered: Boolean,
    val linkKbps: Int,       // OS-reported link speed (a rough ceiling, not a measurement)
    val dataSaver: Boolean,  // Android Data Saver is on and we're on a metered network
    val powerSave: Boolean,  // Battery Saver
)

@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<NetProfile> = _profile.asStateFlow()

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _profile.value = read() }
                override fun onLost(network: Network) { _profile.value = read() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _profile.value = read() }
            })
        }
        runCatching {
            val refresh = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { _profile.value = read() } }
            val f = IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            }
            ContextCompat.registerReceiver(context, refresh, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    fun read(): NetProfile {
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val kind = when {
            caps == null -> NetKind.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
            else -> NetKind.OTHER
        }
        val metered = runCatching { cm.isActiveNetworkMetered }.getOrDefault(kind == NetKind.CELLULAR)
        val saver = metered && runCatching { cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(false)
        return NetProfile(kind, metered, caps?.linkDownstreamBandwidthKbps ?: 0, saver, runCatching { pm.isPowerSaveMode }.getOrDefault(false))
    }
}
