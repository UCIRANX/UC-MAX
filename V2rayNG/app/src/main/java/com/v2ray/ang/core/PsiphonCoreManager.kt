package com.v2ray.ang.core

import android.content.Context
import android.util.Log
import ca.psiphon.PsiphonTunnel
import com.psiphon3.R
import com.psiphon3.log.MyLog
import com.psiphon3.psiphonlibrary.EgressRegionStore
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
 *
 * تمام رویدادهای مهم هسته (شروع، پورت، اتصال، لیست مناطق، notice ها، تایم‌اوت و توقف)
 * هم در logcat و هم در صفحهٔ Logs داخل برنامه ثبت می‌شوند. صفحهٔ Logs فقط status log ها را
 * نشان می‌دهد، پس همه‌چیز با string resource و از طریق MyLog.i/w/e ثبت می‌شود.
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

    /** وضعیت یک بار شروع، برای نوشتن خلاصهٔ علت در صورت شکست. */
    private class StartState {
        val startedAt = System.currentTimeMillis()

        @Volatile
        var portReady = false

        @Volatile
        var tunnelConnected = false

        @Volatile
        var regionCount = -1

        @Volatile
        var noticeCount = 0

        @Volatile
        var lastNotice = ""

        fun elapsedMs(): Long = System.currentTimeMillis() - startedAt
    }

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
        val state = StartState()
        val psiphon: PsiphonTunnel
        synchronized(stateLock) {
            if (tunnel != null) {
                psLog(Log.INFO, R.string.psiphon_core_already_running, MyLog.Sensitivity.NOT_SENSITIVE, "core is active")
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
                        psLog(
                            Log.INFO,
                            R.string.psiphon_core_socks_ready,
                            MyLog.Sensitivity.NOT_SENSITIVE,
                            port.toString()
                        )
                        state.portReady = true
                        portReady.set(true)
                        checkReady()
                    } else {
                        ready.completeExceptionally(IllegalStateException("پورت غیرمنتظرهٔ Psiphon: $port"))
                    }
                }

                override fun onSocksProxyPortInUse(port: Int) {
                    psLog(
                        Log.ERROR,
                        R.string.psiphon_core_socks_in_use,
                        MyLog.Sensitivity.NOT_SENSITIVE,
                        port.toString()
                    )
                    ready.completeExceptionally(IllegalStateException("پورت SOCKS در حال استفاده است: $port"))
                }

                override fun onConnected() {
                    psLog(
                        Log.INFO,
                        R.string.psiphon_core_connected,
                        MyLog.Sensitivity.NOT_SENSITIVE,
                        state.elapsedMs().toString()
                    )
                    state.tunnelConnected = true
                    tunnelConnected.set(true)
                    checkReady()
                }

                override fun onDiagnosticMessage(message: String) {
                    state.noticeCount++
                    state.lastNotice = message
                    // Notice text can contain server details, so it is marked as format args (hidden from feedback).
                    psLog(
                        Log.WARN,
                        R.string.psiphon_core_notice,
                        MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS,
                        message
                    )
                }

                override fun onAvailableEgressRegions(regions: List<String>) {
                    state.regionCount = regions.size
                    // The region list must reach RegionListPreference; without this it stays empty
                    // (only "Best performance" is shown).
                    EgressRegionStore.saveKnownRegions(context, regions)
                    psLog(
                        Log.INFO,
                        R.string.psiphon_core_regions,
                        MyLog.Sensitivity.NOT_SENSITIVE,
                        regions.size.toString(),
                        regions.joinToString(",").ifEmpty { "none" }
                    )
                }

                override fun onExiting() {
                    psLog(
                        Log.WARN,
                        R.string.psiphon_core_exited,
                        MyLog.Sensitivity.NOT_SENSITIVE,
                        state.portReady.toString(),
                        state.tunnelConnected.toString()
                    )
                    ready.completeExceptionally(IllegalStateException("پروسهٔ Psiphon قبل از آماده شدن خاتمه یافت"))
                }
            }

            psiphon = PsiphonTunnel.newPsiphonTunnel(host)
            tunnel = psiphon
            pendingReady = ready
        }

        val embeddedCount = EmbeddedValues.EMBEDDED_SERVER_LIST.size
        val remoteUrlCount = runCatching { JSONArray(EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON).length() }
            .getOrDefault(-1)
        psLog(
            Log.INFO,
            R.string.psiphon_core_starting,
            MyLog.Sensitivity.NOT_SENSITIVE,
            embeddedCount.toString(),
            remoteUrlCount.toString(),
            (!upstreamProxyUrl.isNullOrBlank()).toString(),
            EgressRegionStore.getSelectedRegion(context).ifEmpty { "best" }
        )
        if (embeddedCount == 0) {
            psLog(
                Log.WARN,
                R.string.psiphon_core_no_embedded,
                MyLog.Sensitivity.NOT_SENSITIVE,
                embeddedCount.toString()
            )
        }

        try {
            psiphon.startTunneling(EmbeddedValues.EMBEDDED_SERVER_LIST.joinToString("\n"))
            // ترتیب ایمنی (leak prevention): ترافیک زنجیره بدون هاپ آماده نباید خارج شود.
            // انتظار حتماً محدود (timeout) است و بیرون از قفل انجام می‌شود.
            runBlocking { withTimeout(START_TIMEOUT_MS) { ready.await() } }
        } catch (e: Exception) {
            // Snapshot the state before stop(): stop() triggers onExiting, which would change it.
            psLog(
                Log.ERROR,
                R.string.psiphon_core_start_failed,
                MyLog.Sensitivity.SENSITIVE_FORMAT_ARGS,
                state.elapsedMs().toString(),
                "${e.javaClass.simpleName}: ${e.message}",
                state.portReady.toString(),
                state.tunnelConnected.toString(),
                state.regionCount.toString(),
                state.noticeCount.toString(),
                state.lastNotice.ifEmpty { "none" }
            )
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
            psLog(Log.INFO, R.string.psiphon_core_stopped, MyLog.Sensitivity.NOT_SENSITIVE, "requested")
        } catch (e: Exception) {
            psLog(
                Log.ERROR,
                R.string.psiphon_core_stop_error,
                MyLog.Sensitivity.NOT_SENSITIVE,
                "${e.javaClass.simpleName}: ${e.message}"
            )
            LogUtil.e(AppConfig.TAG, "Psiphon: خطا هنگام توقف", e)
        }
    }

    /**
     * ثبت یک رویداد هسته هم در logcat و هم در صفحهٔ Logs داخل برنامه.
     * هرگز exception پرتاب نمی‌کند تا ثبت لاگ نتواند شروع/توقف Psiphon را خراب کند.
     */
    private fun psLog(level: Int, resId: Int, sensitivity: Int, vararg args: String) {
        val summary = args.joinToString(" | ")
        when (level) {
            Log.ERROR -> LogUtil.e(AppConfig.TAG, "Psiphon[$resId]: $summary")
            Log.WARN -> LogUtil.w(AppConfig.TAG, "Psiphon[$resId]: $summary")
            else -> LogUtil.i(AppConfig.TAG, "Psiphon[$resId]: $summary")
        }
        try {
            when (level) {
                Log.ERROR -> MyLog.e(resId, sensitivity, *args)
                Log.WARN -> MyLog.w(resId, sensitivity, *args)
                else -> MyLog.i(resId, sensitivity, *args)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: نوشتن در صفحهٔ Logs ناموفق بود", e)
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
            // Region chosen in the Options tab; empty means "best performance" (any region).
            .put("EgressRegion", EgressRegionStore.getSelectedRegion(context))
        if (!upstreamProxyUrl.isNullOrBlank()) {
            json.put("UpstreamProxyUrl", upstreamProxyUrl)
        }
        return json.toString()
    }
}
