package ir.omid.vpnman.data

import ir.omid.vpnman.BuildConfig
import ir.omid.vpnman.model.ManifestPayload
import ir.omid.vpnman.model.PreConnectAd
import ir.omid.vpnman.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VpnPanelApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchManifest(): Result<ManifestPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.VPN_API_BASE_URL.isNotBlank()) { "آدرس API تنظیم نشده است" }
            require(BuildConfig.VPN_APP_API_KEY.isNotBlank()) { "کلید API تنظیم نشده است" }

            val url = BuildConfig.VPN_API_BASE_URL.trimEnd('/') + "/api/v1/manifest.php"
            val request = Request.Builder()
                .url(url)
                .header("X-App-Key", BuildConfig.VPN_APP_API_KEY)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("خطای سرور: ${response.code}")
                val root = JSONObject(body)
                if (!root.optBoolean("ok", false)) error(root.optString("error", "پاسخ نامعتبر سرور"))

                // v1.1.2 deliberately reads manual/free groups separately when the panel
                // provides them. This prevents free-config validation from ever hiding
                // the user's own subscription servers.
                val explicitManual = parseServers(root.optJSONArray("manual_servers"), forceAutoManaged = false)
                val explicitFree = parseServers(root.optJSONArray("free_servers"), forceAutoManaged = true)
                val legacyAll = parseServers(root.optJSONArray("servers"), forceAutoManaged = null)

                // Be defensive across mixed panel/app versions. If an explicit group is
                // accidentally empty but the legacy `servers` array still contains that group,
                // keep the legacy entries instead of hiding the user's own servers.
                val legacyManual = legacyAll.filter { !it.autoManaged }
                val legacyFree = legacyAll.filter { it.autoManaged }
                val manual = mergeUnique(explicitManual + legacyManual)
                val free = mergeUnique(explicitFree + legacyFree)
                val servers = mergeUnique(manual + free + legacyAll)

                val adJson = root.optJSONObject("pre_connect_ad")
                val ad = adJson?.let {
                    PreConnectAd(
                        id = it.optInt("id"),
                        title = it.optString("title", "پیشنهاد ویژه"),
                        imageUrl = it.optString("image_url", ""),
                        targetUrl = it.optString("target_url", "").takeIf(String::isNotBlank),
                        displaySeconds = it.optInt("display_seconds", 4).coerceIn(1, 30),
                        showBeforeConnect = it.optBoolean("show_before_connect", true)
                    )
                }

                ManifestPayload(
                    maintenance = root.optBoolean("maintenance", false),
                    minimumAppVersion = root.optString("minimum_app_version", "1.0.0"),
                    servers = servers,
                    manualServers = mergeUnique(manual),
                    freeServers = mergeUnique(free),
                    ad = ad
                )
            }
        }
    }

    suspend fun reportNodeFeedback(
        nodeId: String,
        success: Boolean,
        latencyMs: Int?,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (nodeId.isBlank() || BuildConfig.VPN_API_BASE_URL.isBlank() || BuildConfig.VPN_APP_API_KEY.isBlank()) return@runCatching
            val bodyJson = JSONObject()
                .put("node_id", nodeId)
                .put("status", if (success) "good" else "bad")
            latencyMs?.takeIf { it > 0 }?.let { bodyJson.put("latency_ms", it) }
            reason?.trim()?.takeIf { it.isNotBlank() }?.take(450)?.let { bodyJson.put("reason", it) }

            val request = Request.Builder()
                .url(BuildConfig.VPN_API_BASE_URL.trimEnd('/') + "/api/v1/node-feedback.php")
                .header("X-App-Key", BuildConfig.VPN_APP_API_KEY)
                .header("Accept", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("feedback http ${response.code}")
            }
        }
    }

    private fun parseServers(array: JSONArray?, forceAutoManaged: Boolean?): List<VpnServer> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val config = item.optString("config").trim()
                if (config.isBlank()) continue
                val autoManaged = forceAutoManaged ?: item.optBoolean("auto_managed", false)
                val protocol = item.optString("protocol", "unknown").lowercase()
                add(
                    VpnServer(
                        id = item.optString("id").ifBlank { config.hashCode().toUInt().toString(16) },
                        name = cleanServerName(item.optString("name", "سرور")),
                        protocol = protocol,
                        config = config,
                        sourceName = item.optString("source_name", ""),
                        serverLatencyMs = item.optInt("server_latency_ms", -1).takeIf { it >= 0 },
                        healthScore = item.optInt("health_score", 0).coerceIn(0, 100),
                        autoManaged = autoManaged,
                        serverGroup = item.optString("server_group", if (autoManaged) "free" else "manual"),
                        clientVerified = item.optBoolean("client_verified", false),
                        clientLatencyMs = item.optInt("client_latency_ms", -1).takeIf { it >= 0 }
                    )
                )
            }
        }
    }

    private fun mergeUnique(input: List<VpnServer>): List<VpnServer> {
        val seen = HashSet<String>()
        return input.filter { server ->
            val key = server.id.ifBlank { server.config }
            seen.add(key)
        }
    }

    private fun cleanServerName(value: String): String {
        return value
            .replace(Regex("[\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "سرور" }
    }
}
