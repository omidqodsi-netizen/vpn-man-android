package ir.omid.vpnman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.omid.vpnman.data.LatencyTester
import ir.omid.vpnman.data.ProxyHealthTester
import ir.omid.vpnman.data.VpnPanelApi
import ir.omid.vpnman.model.PreConnectAd
import ir.omid.vpnman.model.VpnServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val verifyingFree: Boolean = false,
    val servers: List<VpnServer> = emptyList(),
    val selectedServerId: String? = null,
    val latencies: Map<String, Int?> = emptyMap(),
    val ad: PreConnectAd? = null,
    val maintenance: Boolean = false,
    val minimumVersion: String = "1.0.0",
    val freeRejectedCount: Int = 0,
    val error: String? = null
) {
    val selectedServer: VpnServer? get() = servers.firstOrNull { it.id == selectedServerId } ?: servers.firstOrNull()
    val manualServers: List<VpnServer> get() = servers.filter { !it.autoManaged }
    val freeServers: List<VpnServer> get() = servers.filter { it.autoManaged }
}

class MainViewModel : ViewModel() {
    private val api = VpnPanelApi()
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private var manuallySelected = false

    init { refresh(false) }

    fun refresh(userInitiated: Boolean = true) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = !userInitiated && it.servers.isEmpty(),
                    refreshing = userInitiated,
                    verifyingFree = false,
                    error = null,
                    freeRejectedCount = 0
                )
            }

            api.fetchManifest().fold(
                onSuccess = { manifest ->
                    val supported = manifest.servers
                        .filter { it.protocol in setOf("vless", "vmess", "trojan", "ss") }
                    val manual = supported.filter { !it.autoManaged }
                    val free = supported.filter { it.autoManaged }
                        .sortedWith(
                            compareByDescending<VpnServer> { it.clientVerified }
                                .thenBy { it.clientLatencyMs ?: Int.MAX_VALUE }
                                .thenByDescending { it.healthScore }
                                .thenBy { it.serverLatencyMs ?: Int.MAX_VALUE }
                        )

                    _ui.update { state ->
                        val keepId = state.selectedServerId?.takeIf { id -> manual.any { it.id == id } }
                        state.copy(
                            loading = false,
                            refreshing = false,
                            verifyingFree = free.isNotEmpty(),
                            servers = manual,
                            selectedServerId = keepId ?: manual.firstOrNull()?.id,
                            latencies = emptyMap(),
                            ad = manifest.ad,
                            maintenance = manifest.maintenance,
                            minimumVersion = manifest.minimumAppVersion,
                            error = if (manual.isEmpty() && free.isEmpty() && !manifest.maintenance) "سرور قابل پشتیبانی پیدا نشد" else null
                        )
                    }

                    if (!manifest.maintenance) {
                        measureManualServers(manual)
                        verifyFreeServers(free)
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            verifyingFree = false,
                            error = e.message ?: "خطا در دریافت سرورها"
                        )
                    }
                }
            )
        }
    }

    fun select(server: VpnServer) {
        manuallySelected = true
        _ui.update { it.copy(selectedServerId = server.id) }
    }

    private suspend fun measureManualServers(servers: List<VpnServer>) {
        if (servers.isEmpty()) return
        val semaphore = Semaphore(8)
        val results = servers.map { server ->
            viewModelScope.async {
                semaphore.withPermit { server.id to LatencyTester.test(server) }
            }
        }.awaitAll().toMap()

        _ui.update { state ->
            val merged = state.latencies + results
            val ordered = sortServers(state.servers, merged)
            val best = ordered.firstOrNull { !it.autoManaged && merged[it.id] != null }?.id
            state.copy(
                servers = ordered,
                latencies = merged,
                selectedServerId = if (!manuallySelected && best != null) best else state.selectedServerId
            )
        }
    }

    private suspend fun verifyFreeServers(candidates: List<VpnServer>) {
        if (candidates.isEmpty()) {
            _ui.update { it.copy(verifyingFree = false) }
            return
        }

        val verified = mutableListOf<VpnServer>()
        val resultMap = mutableMapOf<String, Int?>()
        var rejected = 0
        val semaphore = Semaphore(4)

        for (chunk in candidates.chunked(8)) {
            val results = chunk.map { server ->
                viewModelScope.async {
                    semaphore.withPermit {
                        val latency = ProxyHealthTester.test(server)
                        Triple(server, latency, latency != null)
                    }
                }
            }.awaitAll()

            for ((server, latency, ok) in results) {
                resultMap[server.id] = latency
                if (ok) {
                    verified += server.copy(clientVerified = true, clientLatencyMs = latency)
                } else {
                    rejected++
                }
                viewModelScope.launch { api.reportNodeFeedback(server.id, ok, latency) }
            }

            _ui.update { state ->
                val manual = state.servers.filter { !it.autoManaged }
                val combined = manual + verified
                val mergedLatency = state.latencies + resultMap
                val ordered = sortServers(combined, mergedLatency)
                val selectedStillExists = state.selectedServerId?.takeIf { id -> ordered.any { it.id == id } }
                val best = ordered.minByOrNull { mergedLatency[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs ?: Int.MAX_VALUE }?.id
                state.copy(
                    servers = ordered,
                    latencies = mergedLatency,
                    selectedServerId = selectedStillExists ?: if (!manuallySelected) best else state.selectedServerId,
                    freeRejectedCount = rejected
                )
            }

            // A dozen actually-working free configs is enough for one refresh; avoids long waits on huge feeds.
            if (verified.size >= 12) break
        }

        _ui.update { state ->
            val noServers = state.servers.isEmpty()
            state.copy(
                verifyingFree = false,
                error = if (noServers) "هیچ کانفیگ سالمی در تست واقعی پیدا نشد" else state.error
            )
        }
    }

    private fun sortServers(servers: List<VpnServer>, latencies: Map<String, Int?>): List<VpnServer> {
        return servers.sortedWith(
            compareBy<VpnServer> { if (it.autoManaged) 1 else 0 }
                .thenBy { latencies[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs ?: Int.MAX_VALUE }
                .thenByDescending { it.healthScore }
        )
    }
}
