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

                val serversJson = root.optJSONArray("servers")
                val servers = buildList {
                    if (serversJson != null) {
                        for (i in 0 until serversJson.length()) {
                            val item = serversJson.optJSONObject(i) ?: continue
                            val config = item.optString("config")
                            if (config.isBlank()) continue
                            val autoManaged = item.optBoolean("auto_managed", false)
                            add(
                                VpnServer(
                                    id = item.optString("id"),
                                    name = cleanServerName(item.optString("name", "سرور")),
                                    protocol = item.optString("protocol", "unknown").lowercase(),
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
                    ad = ad
                )
            }
        }
    }

    suspend fun reportNodeFeedback(nodeId: String, success: Boolean, latencyMs: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (nodeId.isBlank() || BuildConfig.VPN_API_BASE_URL.isBlank() || BuildConfig.VPN_APP_API_KEY.isBlank()) return@runCatching
            val bodyJson = JSONObject()
                .put("node_id", nodeId)
                .put("status", if (success) "good" else "bad")
            latencyMs?.takeIf { it > 0 }?.let { bodyJson.put("latency_ms", it) }

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

    private fun cleanServerName(value: String): String {
        return value
            .replace(Regex("[\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "سرور" }
    }
}
