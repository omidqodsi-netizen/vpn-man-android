package ir.omid.vpnman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.omid.vpnman.data.LatencyTester
import ir.omid.vpnman.data.ProxyHealthTester
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
            ?: freeServers.firstOrNull { it.clientVerified }
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
                    val manual = manifest.manualServers
                        .filter { it.protocol in supportedProtocols }
                    val free = manifest.freeServers
                        .filter { it.protocol in supportedProtocols }
                        .sortedWith(
                            compareByDescending<VpnServer> { it.clientVerified }
                                .thenBy { it.clientLatencyMs ?: Int.MAX_VALUE }
                                .thenByDescending { it.healthScore }
                                .thenBy { it.serverLatencyMs ?: Int.MAX_VALUE }
                        )

                    val combined = dedupe(manual + free)
                    val initialChecks = free.associate { server ->
                        server.id to FreeCheckInfo(
                            state = if (server.clientVerified) FreeCheckState.VERIFIED else FreeCheckState.PENDING,
                            latencyMs = server.clientLatencyMs
                        )
                    }

                    _ui.update { state ->
                        val keepId = state.selectedServerId?.takeIf { id -> combined.any { it.id == id } }
                        val preferred = manual.firstOrNull()?.id
                            ?: free.firstOrNull { it.clientVerified }?.id
                            ?: free.firstOrNull()?.id
                        state.copy(
                            loading = false,
                            refreshing = false,
                            verifyingFree = free.isNotEmpty(),
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

                    if (!manifest.maintenance) {
                        // Keep personal servers visible no matter what happens to the free tests.
                        viewModelScope.launch { measureManualServers(manual) }
                        viewModelScope.launch { verifyFreeServers(free) }
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
            val ordered = sortServers(state.servers, merged, state.freeChecks)
            val bestManual = ordered
                .filter { !it.autoManaged }
                .minByOrNull { merged[it.id] ?: Int.MAX_VALUE }
                ?.takeIf { merged[it.id] != null }
                ?.id
            state.copy(
                servers = ordered,
                latencies = merged,
                selectedServerId = if (!manuallySelected && bestManual != null) bestManual else state.selectedServerId
            )
        }
    }

    private suspend fun verifyFreeServers(candidates: List<VpnServer>) {
        if (candidates.isEmpty()) {
            _ui.update { it.copy(verifyingFree = false) }
            return
        }

        // All free candidates stay visible. We only verify a bounded set per refresh so
        // the UI never waits minutes on dead public proxies.
        val toTest = candidates.take(MAX_FREE_TESTS_PER_REFRESH)
        val semaphore = Semaphore(1)
        var freshVerified = 0
        var rejected = 0

        for (chunk in toTest.chunked(4)) {
            _ui.update { state ->
                val checks = state.freeChecks.toMutableMap()
                chunk.forEach { server ->
                    val old = checks[server.id]
                    checks[server.id] = FreeCheckInfo(
                        state = FreeCheckState.TESTING,
                        latencyMs = old?.latencyMs,
                        message = null
                    )
                }
                state.copy(freeChecks = checks)
            }

            val results = chunk.map { server ->
                viewModelScope.async {
                    semaphore.withPermit { server to ProxyHealthTester.test(server) }
                }
            }.awaitAll()

            _ui.update { state ->
                val checks = state.freeChecks.toMutableMap()
                val newLatencies = state.latencies.toMutableMap()
                val byId = results.associateBy { it.first.id }
                val updatedServers = state.servers.map { server ->
                    val result = byId[server.id]?.second ?: return@map server
                    if (result.ok) {
                        result.latencyMs?.let { newLatencies[server.id] = it }
                        checks[server.id] = FreeCheckInfo(
                            state = FreeCheckState.VERIFIED,
                            latencyMs = result.latencyMs,
                            message = "ترافیک واقعی از کانفیگ عبور کرد"
                        )
                        server.copy(clientVerified = true, clientLatencyMs = result.latencyMs)
                    } else {
                        checks[server.id] = FreeCheckInfo(
                            state = FreeCheckState.FAILED,
                            message = result.reason ?: "تست واقعی ناموفق بود"
                        )
                        server
                    }
                }

                val ordered = sortServers(updatedServers, newLatencies, checks)
                val bestVerifiedFree = ordered
                    .filter { it.autoManaged && checks[it.id]?.state == FreeCheckState.VERIFIED }
                    .minByOrNull { newLatencies[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs ?: Int.MAX_VALUE }
                    ?.id
                val selectedExists = state.selectedServerId?.takeIf { id -> ordered.any { it.id == id } }
                val manualExists = ordered.any { !it.autoManaged }
                state.copy(
                    servers = ordered,
                    latencies = newLatencies,
                    freeChecks = checks,
                    selectedServerId = when {
                        selectedExists != null -> selectedExists
                        !manuallySelected && manualExists -> ordered.firstOrNull { !it.autoManaged }?.id
                        !manuallySelected && bestVerifiedFree != null -> bestVerifiedFree
                        else -> ordered.firstOrNull()?.id
                    }
                )
            }

            results.forEach { (server, result) ->
                if (result.ok) freshVerified++ else rejected++
                viewModelScope.launch {
                    api.reportNodeFeedback(server.id, result.ok, result.latencyMs, result.reason)
                }
            }

            if (freshVerified >= TARGET_VERIFIED_FREE) break
        }

        _ui.update { state ->
            val hasManual = state.manualServers.isNotEmpty()
            val hasVerifiedFree = state.freeServers.any { state.freeChecks[it.id]?.state == FreeCheckState.VERIFIED }
            val hasFreeCandidates = state.freeServers.isNotEmpty()
            state.copy(
                verifyingFree = false,
                freeRejectedCount = rejected,
                error = when {
                    hasManual -> state.error
                    hasVerifiedFree -> null
                    hasFreeCandidates -> "کانفیگ‌های رایگان دریافت شدند اما در تست این اینترنت تأیید نشدند؛ می‌توانید از لیست آن‌ها را دستی امتحان کنید."
                    else -> state.error
                }
            )
        }
    }

    private fun sortServers(
        servers: List<VpnServer>,
        latencies: Map<String, Int?>,
        checks: Map<String, FreeCheckInfo>
    ): List<VpnServer> {
        fun freeRank(server: VpnServer): Int = when (checks[server.id]?.state) {
            FreeCheckState.VERIFIED -> 0
            FreeCheckState.TESTING -> 1
            FreeCheckState.PENDING, null -> 2
            FreeCheckState.FAILED -> 3
        }

        return servers.sortedWith(
            compareBy<VpnServer> { if (it.autoManaged) 1 else 0 }
                .thenBy { if (it.autoManaged) freeRank(it) else 0 }
                .thenBy { latencies[it.id] ?: it.clientLatencyMs ?: it.serverLatencyMs ?: Int.MAX_VALUE }
                .thenByDescending { it.healthScore }
        )
    }

    private fun dedupe(input: List<VpnServer>): List<VpnServer> {
        val seen = HashSet<String>()
        return input.filter { seen.add(it.id.ifBlank { it.config }) }
    }

    companion object {
        private const val MAX_FREE_TESTS_PER_REFRESH = 14
        private const val TARGET_VERIFIED_FREE = 6
    }
}
