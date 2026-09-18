package ir.omid.vpnman.data

import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.vpn.XrayConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray

data class ProxyTestResult(
    val ok: Boolean,
    val latencyMs: Int? = null,
    val reason: String? = null
)

object ProxyHealthTester {
    private val testUrls = listOf(
        "https://www.gstatic.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace"
    )

    suspend fun test(server: VpnServer): ProxyTestResult = withContext(Dispatchers.IO) {
        val configResult = runCatching { XrayConfigFactory.build(server.config) }
        val config = configResult.getOrNull()
            ?: return@withContext ProxyTestResult(
                ok = false,
                reason = shortReason("ساخت کانفیگ", configResult.exceptionOrNull())
            )

        var lastReason: String? = null
        for (url in testUrls) {
            val measured = runCatching { Libv2ray.measureOutboundDelay(config, url) }
            val delay = measured.getOrNull()
            if (delay != null && delay > 0) {
                return@withContext ProxyTestResult(
                    ok = true,
                    latencyMs = delay.coerceAtMost(9999).toInt()
                )
            }
            lastReason = shortReason("تست اینترنت", measured.exceptionOrNull())
        }

        ProxyTestResult(false, reason = lastReason ?: "پاسخ اینترنتی از تونل دریافت نشد")
    }

    private fun shortReason(prefix: String, error: Throwable?): String {
        val raw = error?.message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(260)
        return if (raw.isNullOrBlank()) "$prefix ناموفق بود" else "$prefix: $raw"
    }
}
