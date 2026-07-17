package ca.pkay.rcloneexplorer.guided

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NasDiscoveryManager(context: Context, private val listener: Listener) {

    data class NasHost(val name: String, val address: String, val port: Int)

    interface Listener {
        fun onHostFound(host: NasHost)
        fun onScanStateChanged(scanning: Boolean)
        fun onScanUnavailable(message: String)
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scanExecutor = Executors.newFixedThreadPool(32)
    private val discovered = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var started = false
    private var stopped = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType.equals(SERVICE_TYPE, ignoreCase = true)) {
                resolveQueue.add(serviceInfo)
                resolveNext()
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            postUnavailable("Automatic discovery was unavailable. Local network scan is still running.")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    fun start() {
        if (started) return
        started = true
        stopped = false
        handler.post { listener.onScanStateChanged(true) }
        multicastLock = wifiManager.createMulticastLock("courier-smb-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: RuntimeException) {
            postUnavailable("Automatic discovery was unavailable. Local network scan is still running.")
        }
        startSubnetScan()
    }

    fun stop() {
        if (!started || stopped) return
        stopped = true
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: RuntimeException) {
        }
        scanExecutor.shutdownNow()
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun resolveNext() {
        if (stopped || !resolving.compareAndSet(false, true)) return
        val service = resolveQueue.poll()
        if (service == null) {
            resolving.set(false)
            return
        }
        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false)
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val address = serviceInfo.host?.hostAddress
                    if (!address.isNullOrBlank()) {
                        publish(NasHost(serviceInfo.serviceName.ifBlank { address }, address, serviceInfo.port))
                    }
                    resolving.set(false)
                    resolveNext()
                }
            })
        } catch (_: RuntimeException) {
            resolving.set(false)
            resolveNext()
        }
    }

    private fun startSubnetScan() {
        val localAddress = activeIpv4Address()
        if (localAddress == null) {
            handler.post {
                listener.onScanStateChanged(false)
                listener.onScanUnavailable("Connect to your home Wi-Fi or enter the NAS address manually.")
            }
            return
        }
        val octets = localAddress.split('.')
        if (octets.size != 4) {
            handler.post { listener.onScanStateChanged(false) }
            return
        }
        val prefix = octets.take(3).joinToString(".")
        val remaining = AtomicInteger(254)
        for (hostNumber in 1..254) {
            scanExecutor.execute {
                val address = "$prefix.$hostNumber"
                if (!stopped && address != localAddress && isSmbOpen(address)) {
                    val canonical = runCatching { java.net.InetAddress.getByName(address).canonicalHostName }
                        .getOrDefault(address)
                    val name = if (canonical.equals(address, ignoreCase = true)) address else canonical
                    publish(NasHost(name, address, SMB_PORT))
                }
                if (remaining.decrementAndGet() == 0 && !stopped) {
                    handler.post { listener.onScanStateChanged(false) }
                }
            }
        }
    }

    private fun activeIpv4Address(): String? {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return null
        return connectivity.getLinkProperties(network)
            ?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }

    private fun isSmbOpen(address: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, SMB_PORT), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun publish(host: NasHost) {
        val key = "${host.address.lowercase(Locale.ROOT)}:${host.port}"
        if (!discovered.add(key) || stopped) return
        handler.post { if (!stopped) listener.onHostFound(host) }
    }

    private fun postUnavailable(message: String) {
        handler.post { if (!stopped) listener.onScanUnavailable(message) }
    }

    companion object {
        private const val SERVICE_TYPE = "_smb._tcp."
        private const val SMB_PORT = 445
        private const val CONNECT_TIMEOUT_MS = 250
    }
}