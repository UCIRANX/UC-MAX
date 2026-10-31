package com.v2ray.ang.core

import android.content.Context
import ca.psiphon.PsiphonTunnel
import com.psiphon3.psiphonlibrary.EmbeddedValues
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * مدیریت هستهٔ Psiphon (باینری libpsiphon.so).
 *
 * Psiphon به‌صورت هاپ SOCKS محلی [LOCAL_SOCKS_HOST]:[LOCAL_SOCKS_PORT] در دسترس Xray قرار می‌گیرد.
 * این شیء فقط چرخهٔ حیات Psiphon را مدیریت می‌کند؛ انتخاب پروفایل‌ها با ConnectionModeManager است.
 */
object PsiphonCoreManager {

    const val LOCAL_SOCKS_HOST = "127.0.0.1"
    const val LOCAL_SOCKS_PORT = 10850

    // Time allowed for the tunnel to connect (not just the SOCKS port to open).
    private const val START_TIMEOUT_MS = 90_000L
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"

    @Volatile
    private var tunnel: PsiphonTunnel? = null

    // Readiness wait of a start that has not finished yet; stop() uses it to wake that start up.
    @Volatile
    private var pendingReady: CompletableDeferred<Unit>? = null

    // Guards tunnel/pendingReady only. It is never held while waiting for Psiphon to connect.
    private val stateLock = Any()

    /** آیا پروفایل یا یکی از اعضای زنجیره‌اش به هاپ محلی Psiphon نیاز دارد؟ */
    fun isRequiredByChain(config: ProfileItem): Boolean {
        if (isLocalHop(config)) return true
        val members = config.proxyChainProfiles
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return members.any { guid -> MmkvManager.decodeServerConfig(guid)?.let(::isLocalHop) == true }
    }

    private fun isLocalHop(profile: ProfileItem): Boolean =
        profile.configType == EConfigType.SOCKS &&
            profile.server == LOCAL_SOCKS_HOST &&
            profile.serverPort == LOCAL_SOCKS_PORT.toString()

    /**
     * راه‌اندازی Psiphon و انتظار محدود (timeout) تا پورت SOCKS باز و تونل برقرار شود.
     * [upstreamProxyUrl] اگر داده شود، ترافیک Psiphon از همان پروکسی (مثلاً SOCKS محلی Xray) عبور می‌کند.
     * فراخوانی تکراری وقتی Psiphon در حال اجراست بی‌اثر است.
     *
     * این تابع تا چند دقیقه می‌تواند بلاک شود؛ باید از ترد اصلی (main) فراخوانی نشود.
     */
    fun start(context: Context, upstreamProxyUrl: String? = null) {
        val ready = CompletableDeferred<Unit>()
        val psiphon: PsiphonTunnel
        synchronized(stateLock) {
            if (tunnel != null) {
                LogUtil.i(AppConfig.TAG, "Psiphon: هسته از قبل در حال اجراست")
                return
            }

            // Ready only when BOTH the SOCKS port is listening AND the tunnel is connected.
            val portReady = AtomicBoolean(false)
            val tunnelConnected = AtomicBoolean(false)
            fun checkReady() {
                if (portReady.get() && tunnelConnected.get()) {
                    ready.complete(Unit)
                }
            }

            val host = object : PsiphonTunnel.HostService {
                override fun getContext(): Context = context.applicationContext

                override fun getPsiphonConfig(): String = buildConfigJson(context, upstreamProxyUrl)

                override fun onListeningSocksProxyPort(port: Int) {
                    if (port == LOCAL_SOCKS_PORT) {
                        LogUtil.i(AppConfig.TAG, "Psiphon: پورت SOCKS آماده شد ($port)")
                        portReady.set(true)
                        checkReady()
                    } else {
                        ready.completeExceptionally(IllegalStateException("پورت غیرمنتظرهٔ Psiphon: $port"))
                    }
                }

                override fun onSocksProxyPortInUse(port: Int) {
                    ready.completeExceptionally(IllegalStateException("پورت SOCKS در حال استفاده است: $port"))
                }

                override fun onConnected() {
                    LogUtil.i(AppConfig.TAG, "Psiphon: تونل برقرار شد")
                    tunnelConnected.set(true)
                    checkReady()
                }

                override fun onDiagnosticMessage(message: String) {
                    LogUtil.d(AppConfig.TAG, "Psiphon: $message")
                }

                override fun onExiting() {
                    LogUtil.w(AppConfig.TAG, "Psiphon: پروسه خاتمه یافت")
                    ready.completeExceptionally(IllegalStateException("پروسهٔ Psiphon قبل از آماده شدن خاتمه یافت"))
                }
            }

            psiphon = PsiphonTunnel.newPsiphonTunnel(host)
            tunnel = psiphon
            pendingReady = ready
        }

        try {
            psiphon.startTunneling(EmbeddedValues.EMBEDDED_SERVER_LIST.joinToString("\n"))
            // ترتیب ایمنی (leak prevention): ترافیک زنجیره بدون هاپ آماده نباید خارج شود.
            // انتظار حتماً محدود (timeout) است و بیرون از قفل انجام می‌شود.
            runBlocking { withTimeout(START_TIMEOUT_MS) { ready.await() } }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: راه‌اندازی ناموفق بود", e)
            stop()
            throw IllegalStateException("راه‌اندازی Psiphon ناموفق بود: ${e.message}", e)
        } finally {
            synchronized(stateLock) {
                if (pendingReady === ready) pendingReady = null
            }
        }
    }

    /** توقف Psiphon؛ فراخوانی تکراری بی‌اثر است. اگر راه‌اندازی هنوز منتظر است، آن را هم قطع می‌کند. */
    fun stop() {
        val current: PsiphonTunnel?
        val waiting: CompletableDeferred<Unit>?
        synchronized(stateLock) {
            current = tunnel
            tunnel = null
            waiting = pendingReady
            pendingReady = null
        }
        // Wake a start that is still waiting, so it fails and returns instead of blocking until timeout.
        waiting?.completeExceptionally(IllegalStateException("Psiphon stopped before it was ready"))
        if (current == null) return
        try {
            current.stop()
            LogUtil.i(AppConfig.TAG, "Psiphon: متوقف شد")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: خطا هنگام توقف", e)
        }
    }

    /** شناسهٔ پروفایل هاپ محلی Psiphon را برمی‌گرداند؛ اگر نبود می‌سازد (بدون تکرار). */
    fun createHopProfile(): String {
        findHopProfileGuid()?.let { return it }
        val profile = ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = "Psiphon (local)"
            server = LOCAL_SOCKS_HOST
            serverPort = LOCAL_SOCKS_PORT.toString()
        }
        return MmkvManager.encodeServerConfig("", profile)
    }

    /** پیدا کردن پروفایل هاپ موجود بدون ساخت مجدد */
    fun findHopProfileGuid(): String? =
        MmkvManager.decodeAllServerList().firstOrNull { guid ->
            MmkvManager.decodeServerConfig(guid)?.let(::isLocalHop) == true
        }

    private fun buildConfigJson(context: Context, upstreamProxyUrl: String?): String {
        val json = JSONObject()
            .put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            .put("SponsorId", SPONSOR_ID)
            .put("LocalSocksProxyPort", LOCAL_SOCKS_PORT)
            .put("LocalHttpProxyPort", 0)
            .put("EmitDiagnosticNotices", true)
            .put("DataRootDirectory", context.filesDir.absolutePath)
            .put("RemoteServerListDownloadFilename", File(context.filesDir, "psiphon_remote_server_list").absolutePath)
            .put("RemoteServerListUrls", JSONArray(EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON))
            .put("RemoteServerListSignaturePublicKey", EmbeddedValues.REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY)
        if (!upstreamProxyUrl.isNullOrBlank()) {
            json.put("UpstreamProxyUrl", upstreamProxyUrl)
        }
        return json.toString()
    }
}
