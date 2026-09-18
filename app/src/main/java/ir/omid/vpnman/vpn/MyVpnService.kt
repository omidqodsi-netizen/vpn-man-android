package ir.omid.vpnman.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import ir.omid.vpnman.MainActivity
import ir.omid.vpnman.R
import ir.omid.vpnman.model.ConnectionState
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var currentName: String = ""
    @Volatile private var startGeneration: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        XrayRuntime.initialize(applicationContext)
        coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0L
            override fun shutdown(): Long = 0L
            override fun onEmitStatus(code: Long, message: String?): Long = 0L
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                startGeneration++
                stopVpn()
            }
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "سرور منتخب" }
                if (config.isBlank()) {
                    VpnStateStore.update(ConnectionState.ERROR, error = "کانفیگ سرور خالی است")
                    stopSelf()
                } else {
                    val generation = ++startGeneration
                    currentName = name
                    startForeground(NOTIFICATION_ID, buildNotification("در حال اتصال به $name…"))
                    Thread { startVpn(config, name, generation) }.start()
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun startVpn(rawConfig: String, name: String, generation: Long) {
        try {
            VpnStateStore.update(ConnectionState.CONNECTING, name)
            stopCoreOnly()

            val xrayConfig = XrayConfigFactory.build(rawConfig)
            if (generation != startGeneration) return

            val builder = Builder()
                .setSession("وی پی ان من")
                .setMtu(TUN_MTU)
                .addAddress("10.88.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            // Xray runs in this app process. Excluding the whole package keeps its
            // upstream sockets outside the VPN route and prevents a routing loop.
            runCatching { builder.addDisallowedApplication(packageName) }

            vpnInterface = builder.establish() ?: error("اندروید اجازه ساخت رابط VPN را نداد")
            if (generation != startGeneration) {
                stopCoreOnly()
                return
            }

            coreController?.startLoop(xrayConfig, vpnInterface!!.fd)

            // startLoop is synchronous for core creation/startup. A short grace period
            // lets immediate startup failures settle, but we do NOT run measureDelay
            // after enabling TUN: that probe can false-fail and previously kept the UI
            // spinning for up to ~24 seconds even when the core was already running.
            Thread.sleep(250)
            if (generation != startGeneration) {
                stopCoreOnly()
                return
            }
            if (coreController?.isRunning != true) error("هسته Xray شروع نشد")

            VpnStateStore.update(ConnectionState.CONNECTED, name)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification("متصل به $name"))
        } catch (t: Throwable) {
            if (generation == startGeneration) {
                stopCoreOnly()
                VpnStateStore.update(
                    ConnectionState.ERROR,
                    name,
                    t.message?.replace(Regex("\\s+"), " ")?.trim()
                        ?: "خطای ناشناخته در اتصال"
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopVpn() {
        VpnStateStore.update(ConnectionState.DISCONNECTING, currentName)
        stopCoreOnly()
        VpnStateStore.update(ConnectionState.DISCONNECTED, currentName)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Synchronized
    private fun stopCoreOnly() {
        runCatching { if (coreController?.isRunning == true) coreController?.stopLoop() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    override fun onRevoke() {
        startGeneration++
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        startGeneration++
        stopCoreOnly()
        if (VpnStateStore.state.value != ConnectionState.ERROR) {
            VpnStateStore.update(ConnectionState.DISCONNECTED, currentName)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "اتصال VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "وضعیت اتصال وی پی ان من"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 2, Intent(this, MyVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("وی پی ان من")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "قطع اتصال", disconnectIntent)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "ir.omid.vpnman.CONNECT"
        const val ACTION_DISCONNECT = "ir.omid.vpnman.DISCONNECT"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "vpn_connection"
        private const val NOTIFICATION_ID = 901
        private const val TUN_MTU = 1400
    }
}
