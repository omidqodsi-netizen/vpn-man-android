package ir.omid.vpnman.vpn

import android.content.Context
import go.Seq
import libv2ray.Libv2ray

object XrayRuntime {
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        Seq.setContext(app)
        Libv2ray.initCoreEnv(app.filesDir.absolutePath, "")
        initialized = true
    }
}
