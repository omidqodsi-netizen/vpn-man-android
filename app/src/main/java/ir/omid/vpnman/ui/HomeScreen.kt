package ir.omid.vpnman.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.omid.vpnman.BuildConfig
import ir.omid.vpnman.model.ConnectionState
import ir.omid.vpnman.model.PreConnectAd
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.fa
import ir.omid.vpnman.vpn.VpnStateStore
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnect: (VpnServer) -> Unit,
    onDisconnect: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val connection by VpnStateStore.state.collectAsStateWithLifecycle()
    val vpnError by VpnStateStore.error.collectAsStateWithLifecycle()
    var showServers by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var pendingAd by remember { mutableStateOf<PreConnectAd?>(null) }
    var adServer by remember { mutableStateOf<VpnServer?>(null) }

    val compact = LocalConfiguration.current.screenHeightDp < 700
    val selected = ui.selectedServer
    val errorText = vpnError ?: ui.error
    val updateRequired = remember(ui.minimumVersion) { isNewerVersion(ui.minimumVersion, BuildConfig.VERSION_NAME) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080B14), Color(0xFF0D1423), Color(0xFF080B14))
                )
            )
    ) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(
                    loading = ui.refreshing,
                    onRefresh = { viewModel.refresh(true) },
                    onAbout = { showAbout = true }
                )
                Spacer(Modifier.height(30.dp))

                if (ui.loading) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text("در حال دریافت سرورها…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else if (updateRequired) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("نسخه جدید اپ لازم است", style = MaterialTheme.typography.titleLarge)
                    Text("حداقل نسخه مورد نیاز: ${ui.minimumVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { viewModel.refresh(true) }) { Text("بررسی دوباره") }
                    Spacer(Modifier.weight(1f))
                } else if (ui.maintenance) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("سرویس موقتاً در حال بروزرسانی است", style = MaterialTheme.typography.titleLarge)
                    Text("چند دقیقه دیگر دوباره امتحان کنید", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else {
                    ConnectionStatus(connection)
                    Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
                    PowerButton(
                        state = connection,
                        enabled = selected != null,
                        compact = compact,
                        onClick = {
                            if (connection == ConnectionState.CONNECTED || connection == ConnectionState.CONNECTING) {
                                onDisconnect()
                            } else if (selected != null) {
                                val ad = ui.ad
                                if (ad != null && ad.showBeforeConnect) {
                                    adServer = selected
                                    pendingAd = ad
                                } else onConnect(selected)
                            }
                        }
                    )
                    Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
                    Text(
                        when (connection) {
                            ConnectionState.CONNECTED -> "برای قطع اتصال لمس کنید"
                            ConnectionState.CONNECTING -> "در حال برقراری تونل امن…"
                            ConnectionState.DISCONNECTING -> "در حال قطع اتصال…"
                            ConnectionState.ERROR -> "اتصال ناموفق بود"
                            else -> "برای اتصال لمس کنید"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
                    ServerCard(selected, ui.latencies[selected?.id], onClick = { showServers = true })
                    Spacer(Modifier.height(16.dp))
                    if (errorText != null) ErrorCard(errorText)
                    Spacer(Modifier.weight(1f))
                    Footer()
                }
            }
        }
    }

    if (showServers) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showServers = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ServerList(ui.servers, ui.selectedServerId, ui.latencies) { server ->
                viewModel.select(server)
                showServers = false
            }
        }
    }

    pendingAd?.let { ad ->
        PreConnectAdDialog(ad = ad, onFinished = {
            pendingAd = null
            adServer?.let(onConnect)
            adServer = null
        }, onCancel = {
            pendingAd = null
            adServer = null
        })
    }

    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("وی پی ان من", style = MaterialTheme.typography.headlineSmall)
                    Text("نسخه ۱.۰.۰", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("طراحی و توسعه: امید", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { showAbout = false }, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
                }
            }
        }
    }
}

@Composable
private fun Header(loading: Boolean, onRefresh: () -> Unit, onAbout: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("وی پی ان من", style = MaterialTheme.typography.headlineSmall)
            Text("ساده، سریع و امن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, "بروزرسانی")
        }
        IconButton(onClick = onAbout) { Icon(Icons.Default.Info, "درباره") }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val active = state == ConnectionState.CONNECTED
    Row(
        Modifier
            .background(if (active) Color(0x1F7DE3C3) else Color(0x14FFFFFF), RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF788399), CircleShape))
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = state, label = "status") { s ->
            Text(
                when (s) {
                    ConnectionState.CONNECTED -> "متصل و محافظت‌شده"
                    ConnectionState.CONNECTING -> "در حال اتصال"
                    ConnectionState.DISCONNECTING -> "در حال قطع"
                    ConnectionState.ERROR -> "خطای اتصال"
                    else -> "اتصال برقرار نیست"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PowerButton(state: ConnectionState, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val busy = state == ConnectionState.CONNECTING || state == ConnectionState.DISCONNECTING
    val connected = state == ConnectionState.CONNECTED
    val scale by animateFloatAsState(if (connected) 1.04f else 1f, label = "powerScale")
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    val outer = if (compact) 150.dp else 190.dp
    val ring = if (compact) 142.dp else 180.dp
    val inner = if (compact) 126.dp else 158.dp
    val icon = if (compact) 46.dp else 58.dp
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(outer).scale(scale)) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(ring).rotate(rotation),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            Modifier
                .size(inner)
                .background(
                    Brush.radialGradient(
                        if (connected) listOf(Color(0xFF1C4A40), Color(0xFF101B1A))
                        else listOf(Color(0xFF192338), Color(0xFF0E1523))
                    ), CircleShape
                )
                .border(1.dp, if (connected) MaterialTheme.colorScheme.primary else Color(0xFF2C3850), CircleShape)
                .clickable(enabled = enabled && !busy, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = "اتصال",
                tint = if (connected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(icon)
            )
        }
    }
}

@Composable
private fun ServerCard(server: VpnServer?, latency: Int?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFF222E43))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).background(Color(0x1F7DE3C3), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server?.name ?: "سروری انتخاب نشده", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    server?.let { "${protocolLabel(it.protocol)}${if (it.sourceName.isNotBlank()) " • ${it.sourceName}" else ""}" } ?: "لیست سرورها را باز کنید",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (latency != null) Text("${latency.fa()} ms", color = latencyColor(latency), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    Surface(Modifier.fillMaxWidth(), color = Color(0x22FF7E86), shape = RoundedCornerShape(16.dp)) {
        Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Footer() {
    Text("طراحی و توسعه: امید", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6F7B8E))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ServerList(servers: List<VpnServer>, selectedId: String?, latencies: Map<String, Int?>, onSelect: (VpnServer) -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.78f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("انتخاب سرور", style = MaterialTheme.typography.headlineSmall)
                Text("سرور سریع‌تر به‌صورت خودکار انتخاب می‌شود", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = Color(0xFF202A3D))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            items(servers, key = { it.id }) { server ->
                val selected = server.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(server) }
                        .background(if (selected) Color(0x147DE3C3) else Color.Transparent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(38.dp).background(Color(0x12FFFFFF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text(protocolShort(server.protocol), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(protocolLabel(server.protocol), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val latency = latencies[server.id]
                    if (latency != null) Text("${latency.fa()} ms", color = latencyColor(latency), style = MaterialTheme.typography.labelMedium)
                    else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
                    if (selected) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PreConnectAdDialog(ad: PreConnectAd, onFinished: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var seconds by remember(ad.id) { mutableIntStateOf(ad.displaySeconds) }
    LaunchedEffect(ad.id) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title,
                    modifier = Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF0B101C), RoundedCornerShape(20.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(ad.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (!ad.targetUrl.isNullOrBlank()) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.targetUrl))) }
                    }) { Text("مشاهده پیشنهاد") }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onFinished,
                    enabled = seconds <= 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (seconds > 0) "اتصال تا ${seconds.fa()} ثانیه دیگر" else "اتصال به وی پی ان", color = MaterialTheme.colorScheme.onPrimary)
                }
                TextButton(onClick = onCancel) { Text("انصراف") }
            }
        }
    }
}

private fun protocolLabel(value: String) = when (value.lowercase()) {
    "vless" -> "VLESS"
    "vmess" -> "VMess"
    "trojan" -> "Trojan"
    "ss" -> "Shadowsocks"
    else -> value.uppercase()
}

private fun protocolShort(value: String) = when (value.lowercase()) {
    "vless" -> "VL"
    "vmess" -> "VM"
    "trojan" -> "TR"
    "ss" -> "SS"
    else -> "VPN"
}

private fun latencyColor(ms: Int) = when {
    ms < 120 -> Color(0xFF7DE3C3)
    ms < 250 -> Color(0xFFFFD166)
    else -> Color(0xFFFF7E86)
}

private fun isNewerVersion(required: String, current: String): Boolean {
    val a = required.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av > bv
    }
    return false
}
