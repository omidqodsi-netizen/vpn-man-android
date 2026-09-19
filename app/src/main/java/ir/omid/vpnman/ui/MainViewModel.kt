package ir.omid.vpnman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.omid.vpnman.data.LatencyTester
import ir.omid.vpnman.data.VpnPanelApi
import ir.omid.vpnman.model.FreeCheckInfo
import ir.omid.vpnman.model.FreeCheckState
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
    val freeChecks: Map<String, FreeCheckInfo> = emptyMap(),
    val ad: PreConnectAd? = null,
    val maintenance: Boolean = false,
    val minimumVersion: String = "1.0.0",
    val freeRejectedCount: Int = 0,
    val error: String? = null
) {
    val manualServers: List<VpnServer> get() = servers.filter { !it.autoManaged }
    val freeServers: List<VpnServer> get() = servers.filter { it.autoManaged }
    val selectedServer: VpnServer?
        get() = servers.firstOrNull { it.id == selectedServerId }
            ?: manualServers.firstOrNull()
            ?: freeServers.firstOrNull()
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
                    val supportedProtocols = setOf("vless", "vmess", "trojan", "ss")
                    val manual = manifest.manualServers.filter { it.protocol in supportedProtocols }
                    val free = manifest.freeServers.filter { it.protocol in supportedProtocols }
                    val combined = dedupe(manual + free)

                    val initialChecks = free.associate { server ->
                        server.id to FreeCheckInfo(
                            state = if (server.clientVerified) FreeCheckState.VERIFIED else FreeCheckState.PENDING,
                            latencyMs = server.clientLatencyMs,
                            message = if (server.clientVerified) "تأیید قبلی پنل" else null
                        )
                    }

                    _ui.update { state ->
                        val keepId = state.selectedServerId?.takeIf { id -> combined.any { it.id == id } }
                        val preferred = manual.firstOrNull()?.id ?: free.firstOrNull()?.id
                        state.copy(
                            loading = false,
                            refreshing = false,
                            verifyingFree = false,
                            servers = combined,
                            selectedServerId = keepId ?: preferred,
                            latencies = emptyMap(),
                            freeChecks = initialChecks,
                            ad = manifest.ad,
                            maintenance = manifest.maintenance,
                            minimumVersion = manifest.minimumAppVersion,
                            error = if (combined.isEmpty() && !manifest.maintenance) "هیچ سروری از پنل دریافت نشد" else null
                        )
                    }

                    if (!manifest.maintenance) testLatencies(combined)
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

    private fun testLatencies(servers: List<VpnServer>) {
        if (servers.isEmpty()) return
        viewModelScope.launch {
            val semaphore = Semaphore(8)
            val results = servers.map { server ->
                async {
                    semaphore.withPermit { server.id to LatencyTester.test(server) }
                }
            }.awaitAll().toMap()

            _ui.update { state ->
                val ordered = state.servers.sortedWith(
                    compareBy<VpnServer> { if (it.autoManaged) 1 else 0 }
                        .thenBy { results[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs ?: Int.MAX_VALUE }
                        .thenByDescending { it.healthScore }
                )
                val bestManual = ordered.firstOrNull { !it.autoManaged && (results[it.id] ?: it.serverLatencyMs) != null }?.id
                val bestAny = ordered.firstOrNull { (results[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs) != null }?.id
                state.copy(
                    servers = ordered,
                    latencies = results,
                    selectedServerId = if (!manuallySelected) (bestManual ?: bestAny ?: state.selectedServerId) else state.selectedServerId
                )
            }
        }
    }

    private fun dedupe(input: List<VpnServer>): List<VpnServer> {
        val seen = HashSet<String>()
        return input.filter { seen.add(it.id.ifBlank { it.config }) }
    }
}
