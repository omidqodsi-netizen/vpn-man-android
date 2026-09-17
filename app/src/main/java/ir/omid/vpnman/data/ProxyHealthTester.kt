package ir.omid.vpnman.data

import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.vpn.XrayConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray

object ProxyHealthTester {
    private val testUrls = listOf(
        "https://www.gstatic.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace"
    )

    suspend fun test(server: VpnServer): Int? = withContext(Dispatchers.IO) {
        val config = runCatching { XrayConfigFactory.build(server.config) }.getOrNull() ?: return@withContext null
        for (url in testUrls) {
            val delay = runCatching { Libv2ray.measureOutboundDelay(config, url) }.getOrNull()
            if (delay != null && delay > 0) return@withContext delay.coerceAtMost(9999).toInt()
        }
        null
    }
}
