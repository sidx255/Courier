package ca.pkay.rcloneexplorer.guided

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.Socket

object HomeNetworkAwareness {

    enum class State {
        NAS_REACHABLE,
        NAS_UNAVAILABLE,
        AWAY,
        ROUTE_UNAVAILABLE,
        OFFLINE
    }

    data class Result(val state: State)

    fun check(context: Context, host: String, port: Int): Result {
        if (host.isNotBlank() && port in 1..65535 && canConnect(host, port)) {
            return Result(State.NAS_REACHABLE)
        }
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return Result(State.OFFLINE)
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return Result(State.OFFLINE)
        val wifiOrEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        if (!wifiOrEthernet && !vpn) return Result(State.AWAY)
        val target = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: return Result(State.ROUTE_UNAVAILABLE)
        val hasSpecificRoute = connectivity.getLinkProperties(network)
            ?.routes
            ?.any { route -> route.destination.prefixLength > 0 && route.matches(target) }
            ?: false
        return Result(
            when {
                hasSpecificRoute -> State.NAS_UNAVAILABLE
                vpn -> State.ROUTE_UNAVAILABLE
                target.isSiteLocalAddress && !hasSpecificRoute -> State.AWAY
                else -> State.ROUTE_UNAVAILABLE
            }
        )
    }

    private fun canConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}