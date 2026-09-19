package ir.omid.vpnman.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.omid.vpnman.BuildConfig
import ir.omid.vpnman.model.ConnectionState
import ir.omid.vpnman.model.FreeCheckInfo
import ir.omid.vpnman.model.FreeCheckState
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
    val updateRequired = remember(ui.minimumVersion) {
        isNewerVersion(ui.minimumVersion, BuildConfig.VERSION_NAME)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050812), Color(0xFF0A1020), Color(0xFF070A12))
                )
            )
    ) {
        AnimatedBackdrop(connection)

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
                Spacer(Modifier.height(if (compact) 18.dp else 26.dp))

                if (ui.loading) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text("در حال دریافت سرورها…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else if (updateRequired) {
                    Spacer(Modifier.weight(1f))
                    Text("↑", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("نسخه جدید اپ لازم است", style = MaterialTheme.typography.titleLarge)
                    Text("حداقل نسخه مورد نیاز: ${ui.minimumVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { viewModel.refresh(true) }) { Text("بررسی دوباره") }
                    Spacer(Modifier.weight(1f))
                } else if (ui.maintenance) {
                    Spacer(Modifier.weight(1f))
                    Text("☁", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("سرویس موقتاً در حال بروزرسانی است", style = MaterialTheme.typography.titleLarge)
                    Text("چند دقیقه دیگر دوباره امتحان کنید", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else {
                    ConnectionStatus(connection)
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
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
                                } else {
                                    onConnect(selected)
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    Text(
                        when (connection) {
                            ConnectionState.CONNECTED -> "اتصال امن برقرار است"
                            ConnectionState.CONNECTING -> "در حال راه‌اندازی تونل…"
                            ConnectionState.DISCONNECTING -> "در حال قطع اتصال…"
                            ConnectionState.ERROR -> "اتصال برقرار نشد"
                            else -> "برای اتصال، دکمه را لمس کنید"
                        },
                        color = if (connection == ConnectionState.ERROR) Color(0xFFFFB2BA)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
                    ServerCard(selected, ui.latencies[selected?.id], onClick = { showServers = true })
                    if (ui.verifyingFree) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "در حال تست واقعی کانفیگ‌های رایگان…",
                            color = Color(0xFF8FA7FF),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.height(14.dp))
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
            containerColor = Color(0xFF0D1422),
            contentColor = Color.White
        ) {
            ServerList(ui.servers, ui.selectedServerId, ui.latencies, ui.freeChecks, ui.verifyingFree) { server ->
                viewModel.select(server)
                showServers = false
            }
        }
    }

    pendingAd?.let { ad ->
        PreConnectAdDialog(
            ad = ad,
            onFinished = {
                pendingAd = null
                adServer?.let(onConnect)
                adServer = null
            },
            onCancel = {
                pendingAd = null
                adServer = null
            }
        )
    }

    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF111A2B), contentColor = Color.White) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◈", fontSize = 38.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("وی پی ان من", style = MaterialTheme.typography.headlineSmall)
                    Text("نسخه ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun AnimatedBackdrop(state: ConnectionState) {
    val transition = rememberInfiniteTransition(label = "background")
    val drift by transition.animateFloat(
        initialValue = -22f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val accent = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF43E8B6)
        ConnectionState.CONNECTING -> Color(0xFF70A7FF)
        ConnectionState.ERROR -> Color(0xFFFF6375)
        else -> Color(0xFF586BFF)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-95f + drift).dp, y = 95.dp)
                .size(285.dp)
                .scale(breathe)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (90f - drift).dp, y = 80.dp)
                .size(340.dp)
                .scale(1.05f + (breathe - 1f) * 0.6f)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF7DE3C3).copy(alpha = 0.10f), Color.Transparent)),
                    CircleShape
                )
        )
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
            if (loading) {
                CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
            } else {
                Text("↻", fontSize = 25.sp, color = Color.White)
            }
        }
        IconButton(onClick = onAbout) {
            Text("ⓘ", fontSize = 22.sp, color = Color.White)
        }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val statusColor = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF62E6BD)
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Color(0xFF8EB6FF)
        ConnectionState.ERROR -> Color(0xFFFF8793)
        else -> Color(0xFF8B96AA)
    }
    val statusBackground = when (state) {
        ConnectionState.CONNECTED -> Color(0x2229D6A3)
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Color(0x222F80ED)
        ConnectionState.ERROR -> Color(0x33C93D50)
        else -> Color(0x16FFFFFF)
    }

    Row(
        Modifier
            .background(statusBackground, RoundedCornerShape(50.dp))
            .border(1.dp, statusColor.copy(alpha = 0.28f), RoundedCornerShape(50.dp))
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = state, label = "status") { s ->
            Text(
                when (s) {
                    ConnectionState.CONNECTED -> "متصل"
                    ConnectionState.CONNECTING -> "در حال اتصال"
                    ConnectionState.DISCONNECTING -> "در حال قطع"
                    ConnectionState.ERROR -> "خطای اتصال"
                    else -> "آماده اتصال"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (s == ConnectionState.ERROR) Color(0xFFFFDDE1) else Color.White
            )
        }
    }
}

@Composable
private fun PowerButton(state: ConnectionState, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val busy = state == ConnectionState.CONNECTING || state == ConnectionState.DISCONNECTING
    val connected = state == ConnectionState.CONNECTED
    val error = state == ConnectionState.ERROR
    val buttonScale by animateFloatAsState(
        targetValue = if (connected) 1.045f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "powerScale"
    )
    val transition = rememberInfiniteTransition(label = "powerAnim")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val softPulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "softPulse"
    )

    val outer = if (compact) 164.dp else 205.dp
    val halo = if (compact) 154.dp else 193.dp
    val ring = if (compact) 146.dp else 184.dp
    val inner = if (compact) 126.dp else 156.dp
    val accent = when {
        connected -> Color(0xFF62E6BD)
        busy -> Color(0xFF82AFFF)
        error -> Color(0xFFFF7E8D)
        else -> Color(0xFFB9C6DD)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(outer).scale(buttonScale)) {
        Box(
            Modifier
                .size(halo)
                .scale(if (connected || busy) pulse else 1f)
                .background(accent.copy(alpha = if (connected) 0.11f else if (busy) 0.08f else 0.035f), CircleShape)
        )
        Box(
            Modifier
                .size(ring)
                .scale(if (connected) softPulse else 1f)
                .border(1.dp, accent.copy(alpha = if (connected || busy) 0.55f else 0.25f), CircleShape)
        )
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(ring).rotate(rotation),
                strokeWidth = 3.dp,
                color = accent
            )
        }
        Box(
            Modifier
                .size(inner)
                .background(
                    Brush.radialGradient(
                        when {
                            connected -> listOf(Color(0xFF174238), Color(0xFF0A1716))
                            error -> listOf(Color(0xFF3A1A22), Color(0xFF151018))
                            else -> listOf(Color(0xFF17243A), Color(0xFF0B111D))
                        }
                    ),
                    CircleShape
                )
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                .clickable(enabled = enabled && !busy, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            ConnectionGlyph(state = state, color = accent, sizeDp = if (compact) 54.dp else 66.dp)
        }
    }
}

@Composable
private fun ConnectionGlyph(state: ConnectionState, color: Color, sizeDp: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(sizeDp)) {
        val w = size.minDimension
        val stroke = w * 0.095f
        when (state) {
            ConnectionState.CONNECTED -> {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.22f, w * 0.53f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.43f, w * 0.73f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.43f, w * 0.73f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.80f, w * 0.30f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            ConnectionState.ERROR -> {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.22f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.58f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawCircle(color = color, radius = stroke * 0.55f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.78f))
            }
            else -> {
                drawArc(
                    color = color,
                    startAngle = -45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.16f, w * 0.16f),
                    size = androidx.compose.ui.geometry.Size(w * 0.68f, w * 0.68f)
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.10f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.47f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ServerCard(server: VpnServer?, latency: Int?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xD9121A2A),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF26334A))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Color(0x247DE3C3), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server?.name ?: "سروری انتخاب نشده", style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrRtl), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    server?.let {
                        "${if (it.autoManaged) "رایگان • " else "شخصی • "}${protocolLabel(it.protocol)}${if (it.sourceName.isNotBlank()) " • ${it.sourceName}" else ""}"
                    } ?: "لیست سرورها را باز کنید",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            val shownLatency = latency ?: server?.serverLatencyMs
            if (shownLatency != null) {
                Text("${shownLatency.fa()} ms", color = latencyColor(shownLatency), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(7.dp))
            Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val friendly = friendlyError(text)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF431D27),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x88FF7E8D))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Color(0xFFFF7E8D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color(0xFF2A0B12), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("اتصال برقرار نشد", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(friendly, color = Color(0xFFFFDDE1), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(if (expanded) "بستن جزئیات" else "جزئیات فنی", color = Color(0xFFFFB7C0))
            }
            if (expanded) {
                Text(
                    text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x66130A0D), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    color = Color(0xFFFFC8CE),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    Text("طراحی و توسعه: امید", style = MaterialTheme.typography.labelMedium, color = Color(0xFF78859A))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ServerList(
    servers: List<VpnServer>,
    selectedId: String?,
    latencies: Map<String, Int?>,
    freeChecks: Map<String, FreeCheckInfo>,
    verifyingFree: Boolean,
    onSelect: (VpnServer) -> Unit
) {
    var filter by remember { mutableStateOf(ServerFilter.ALL) }
    val manualCount = servers.count { !it.autoManaged }
    val freeCount = servers.count { it.autoManaged }
    val visible = when (filter) {
        ServerFilter.ALL -> servers
        ServerFilter.MANUAL -> servers.filter { !it.autoManaged }
        ServerFilter.FREE -> servers.filter { it.autoManaged }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("انتخاب سرور", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    if (verifyingFree) "کانفیگ‌های رایگان دیده می‌شوند و هم‌زمان تست واقعی می‌گیرند" else "سرورهای شخصی و رایگان از هم جدا هستند",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text("◉", fontSize = 23.sp, color = MaterialTheme.colorScheme.primary)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            FilterChip("همه ${servers.size.fa()}", filter == ServerFilter.ALL, Modifier.weight(1f)) { filter = ServerFilter.ALL }
            Spacer(Modifier.width(8.dp))
            FilterChip("شخصی ${manualCount.fa()}", filter == ServerFilter.MANUAL, Modifier.weight(1f)) { filter = ServerFilter.MANUAL }
            Spacer(Modifier.width(8.dp))
            FilterChip("رایگان ${freeCount.fa()}", filter == ServerFilter.FREE, Modifier.weight(1f)) { filter = ServerFilter.FREE }
        }

        HorizontalDivider(color = Color(0xFF202A3D))

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == ServerFilter.FREE && verifyingFree) "در حال دریافت/بررسی کانفیگ‌های رایگان…" else "سروری در این بخش وجود ندارد",
                    color = Color(0xFFB8C4D6),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                items(visible, key = { it.id }) { server ->
                    val selected = server.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(server) }
                            .background(if (selected) Color(0x187DE3C3) else Color.Transparent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).background(Color(0x13FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(protocolShort(server.protocol), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrRtl),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val freeCheck = freeChecks[server.id]
                            Text(
                                buildString {
                                    append(if (server.autoManaged) "رایگان" else "شخصی")
                                    append(" • ")
                                    append(protocolLabel(server.protocol))
                                    if (server.autoManaged) {
                                        when (freeCheck?.state) {
                                            FreeCheckState.VERIFIED -> append(" • تأیید قبلی ✓")
                                            FreeCheckState.TESTING -> append(" • در حال بررسی…")
                                            FreeCheckState.FAILED -> append(" • تست ناموفق")
                                            FreeCheckState.PENDING, null -> append(" • آماده اتصال")
                                        }
                                    }
                                    if (server.healthScore > 0) append(" • امتیاز ${server.healthScore.fa()}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (freeCheck?.state) {
                                    FreeCheckState.VERIFIED -> Color(0xFF79E8C5)
                                    FreeCheckState.FAILED -> Color(0xFFFF9FAA)
                                    FreeCheckState.TESTING -> Color(0xFF9CB8FF)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val failureMessage = freeCheck?.message
                            if (server.autoManaged && freeCheck?.state == FreeCheckState.FAILED && !failureMessage.isNullOrBlank()) {
                                Text(
                                    failureMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9CA9BC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        val latency = latencies[server.id] ?: server.clientLatencyMs ?: server.serverLatencyMs
                        if (latency != null) {
                            Text("${latency.fa()} ms", color = latencyColor(latency), style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Spacer(Modifier.width(10.dp))
                            Text("✓", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private enum class ServerFilter { ALL, MANUAL, FREE }

@Composable
private fun FilterChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF1B5B52) else Color(0xFF121B2A),
        contentColor = if (selected) Color(0xFFB9FFE9) else Color(0xFFB4C0D1),
        border = BorderStroke(1.dp, if (selected) Color(0xFF46CFA8) else Color(0xFF26334A))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium
        )
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
            color = Color(0xFF111A2B),
            contentColor = Color.White
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFF0B101C), RoundedCornerShape(20.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(ad.title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                if (!ad.targetUrl.isNullOrBlank()) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.targetUrl))) }
                    }) {
                        Text("مشاهده پیشنهاد", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onFinished,
                    enabled = seconds <= 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        if (seconds > 0) "اتصال تا ${seconds.fa()} ثانیه دیگر" else "اتصال به وی پی ان",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                TextButton(onClick = onCancel) { Text("انصراف", color = Color(0xFFC5D0E0)) }
            }
        }
    }
}

private fun friendlyError(text: String): String {
    val v = text.lowercase()
    return when {
        "xray.xudp.basekey" in v -> "هسته اتصال به‌درستی آماده نشده بود؛ این مورد در نسخه‌های جدید اصلاح شده است."
        "vless without tls" in v -> "این سرور از VLESS قدیمی بدون TLS استفاده می‌کند. سرور دیگری را انتخاب کنید."
        "trojan without tls" in v -> "این سرور Trojan بدون TLS است و توسط هسته جدید پذیرفته نمی‌شود."
        "failed to parse json config" in v || "config error" in v -> "تنظیمات این سرور با هسته فعلی سازگار نیست. سرور دیگری را امتحان کنید."
        "timeout" in v || "timed out" in v -> "پاسخ سرور دیر رسید. یک سرور با پینگ کمتر انتخاب کنید."
        "network" in v || "connection" in v -> "ارتباط با سرور برقرار نشد. اینترنت یا سرور را بررسی کنید."
        else -> "سرور پاسخ مناسب نداد. سرور دیگری را امتحان کنید."
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
