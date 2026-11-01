package com.v2ray.ang.core

import android.content.Context
import ca.psiphon.PsiphonTunnel
import com.psiphon3.psiphonlibrary.EmbeddedValues
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/**
 * مدیریت هستهٔ Psiphon (باینری libpsiphon.so).
 *
 * Psiphon به‌صورت هاپ SOCKS محلی [LOCAL_SOCKS_HOST]:[LOCAL_SOCKS_PORT] در دسترس Xray قرار می‌گیرد.
 * پروفایل هاپ در حافظه ساخته می‌شود و در لیست سرورها ذخیره/نمایش داده نمی‌شود.
 */
object PsiphonCoreManager {

    const val LOCAL_SOCKS_HOST = "127.0.0.1"
    const val LOCAL_SOCKS_PORT = 10850

    /** inbound SOCKS کانفیگ Xray که Psiphon از آن به V2ray وصل می‌شود (حالت «Psiphon از V2ray») */
    const val PSIPHON_UPSTREAM_PORT = 10851

    /** مهلت انتظار: هم پورت SOCKS و هم اتصال واقعی تونل باید در این زمان برقرار شوند */
    private const val START_TIMEOUT_MS = 90_000L
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"

    @Volatile
    private var tunnel: PsiphonTunnel? = null

    /** انتظارهای در حال اجرای start؛ برای لغو فوری با دکمهٔ قطع */
    @Volatile
    private var pendingPort: CompletableDeferred<Unit>? = null

    @Volatile
    private var pendingTunnel: CompletableDeferred<Unit>? = null

    /**
     * لغو فوری راه‌اندازی در حال انجام (بدون قفل، تا از UI همیشه بتواند قطع کند).
     * start با خطا برمی‌گردد و هسته هم متوقف می‌شود.
     */
    fun cancelPending() {
        val cancelled = CancellationException("Psiphon start cancelled by user")
        pendingPort?.completeExceptionally(cancelled)
        pendingTunnel?.completeExceptionally(cancelled)
        tunnel?.stop()
    }

    fun upstreamProxyUrl(): String = "socks5://127.0.0.1:$PSIPHON_UPSTREAM_PORT"

    /** آیا پروفایل یا یکی از اعضای زنجیره‌اش به هاپ محلی Psiphon نیاز دارد؟ */
    fun isRequiredByChain(config: ProfileItem): Boolean {
        if (isLocalHop(config)) return true
        val members = config.proxyChainProfiles
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return members.any { guid -> com.v2ray.ang.handler.MmkvManager.decodeServerConfig(guid)?.let(::isLocalHop) == true }
    }

    fun isLocalHop(profile: ProfileItem): Boolean =
        profile.configType == EConfigType.SOCKS &&
            profile.server == LOCAL_SOCKS_HOST &&
            profile.serverPort == LOCAL_SOCKS_PORT.toString()

    /** پروفایل هاپ محلی Psiphon (فقط در حافظه) */
    fun buildHopProfile(): ProfileItem =
        ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = "Psiphon (local)"
            server = LOCAL_SOCKS_HOST
            serverPort = LOCAL_SOCKS_PORT.toString()
        }

    /** شناسهٔ هاپ Psiphon؛ پروفایل در لیست ذخیره نمی‌شود */
    fun createHopProfile(): String = VirtualProfiles.PSIPHON_HOP_GUID

    /**
     * راه‌اندازی Psiphon و انتظار محدود تا (۱) پورت SOCKS آماده شود و (۲) تونل واقعاً وصل شود.
     * اگر فقط پورت آماده باشد ولی تونل وصل نباشد، SOCKS پیام‌ها را قطع می‌کند و Xray خطای closed pipe می‌دهد.
     * فراخوانی تکراری وقتی Psiphon در حال اجراست بی‌اثر است.
     */
    @Synchronized
    fun start(context: Context, upstreamProxyUrl: String? = null) {
        if (tunnel != null) {
            LogUtil.i(AppConfig.TAG, "Psiphon: هسته از قبل در حال اجراست")
            return
        }

        val portReady = CompletableDeferred<Unit>()
        val tunnelConnected = CompletableDeferred<Unit>()
        val host = object : PsiphonTunnel.HostService {
            override fun getContext(): Context = context.applicationContext

            override fun getPsiphonConfig(): String = buildConfigJson(context, upstreamProxyUrl)

            override fun onListeningSocksProxyPort(port: Int) {
                if (port == LOCAL_SOCKS_PORT) {
                    LogUtil.i(AppConfig.TAG, "Psiphon: پورت SOCKS آماده شد ($port)")
                    portReady.complete(Unit)
                } else {
                    portReady.completeExceptionally(IllegalStateException("پورت غیرمنتظرهٔ Psiphon: $port"))
                }
            }

            override fun onSocksProxyPortInUse(port: Int) {
                portReady.completeExceptionally(IllegalStateException("پورت SOCKS در حال استفاده است: $port"))
            }

            override fun onConnected() {
                LogUtil.i(AppConfig.TAG, "Psiphon: تونل واقعاً برقرار شد")
                tunnelConnected.complete(Unit)
            }

            override fun onDiagnosticMessage(message: String) {
                LogUtil.d(AppConfig.TAG, "Psiphon: $message")
            }

            override fun onExiting() {
                LogUtil.w(AppConfig.TAG, "Psiphon: پروسه خاتمه یافت")
                val error = IllegalStateException("پروسهٔ Psiphon قبل از آماده شدن خاتمه یافت")
                portReady.completeExceptionally(error)
                tunnelConnected.completeExceptionally(error)
            }
        }

        val psiphon = PsiphonTunnel.newPsiphonTunnel(host)
        tunnel = psiphon
        pendingPort = portReady
        pendingTunnel = tunnelConnected
        try {
            psiphon.startTunneling(EmbeddedValues.EMBEDDED_SERVER_LIST.joinToString("\n"))
            // ترتیب ایمنی: ترافیک زنجیره بدون تونل آماده نباید خارج شود. انتظار حتماً محدود است.
            runBlocking {
                withTimeout(START_TIMEOUT_MS) {
                    portReady.await()
                    tunnelConnected.await()
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: راه‌اندازی ناموفق بود", e)
            stop()
            throw IllegalStateException("راه‌اندازی Psiphon ناموفق بود: ${e.message}", e)
        } finally {
            pendingPort = null
            pendingTunnel = null
        }
    }

    /**
     * توقف فوری Psiphon. عمداً قفل ندارد: start تا ۹۰ ثانیه قفل را نگه می‌دارد و دکمهٔ قطع
     * نباید پشت آن منتظر اتصال بماند. ابتدا انتظارهای در حال اجرا لغو می‌شوند.
     * فراخوانی تکراری بی‌اثر است.
     */
    fun stop() {
        val cancelled = CancellationException("Psiphon stopped by user")
        pendingPort?.completeExceptionally(cancelled)
        pendingTunnel?.completeExceptionally(cancelled)
        val current = tunnel ?: return
        tunnel = null
        try {
            current.stop()
            LogUtil.i(AppConfig.TAG, "Psiphon: متوقف شد")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: خطا هنگام توقف", e)
        }
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
        // IPهای سالم اسکنر (آکامای) به‌عنوان دامنه‌های dial مسیر CDN به Psiphon داده می‌شوند
        val edgeIps = CdnIpScanner.savedIps(context)
        if (edgeIps.isNotEmpty()) {
            val overrides = JSONArray()
            edgeIps.forEachIndexed { index, ip ->
                overrides.put(
                    JSONObject()
                        .put("OverrideID", "uc-max-edge-$index")
                        .put("MatchDialAddressRegexes", JSONArray().put(".*"))
                        .put("DialAddresses", JSONArray().put(ip))
                        .put("SNIServerName", CdnIpScanner.PROBE_SNI)
                        .put("VerifyServerNames", JSONArray().put(CdnIpScanner.PROBE_SNI).put("a248.e.akamai.net"))
                        .put("ALPNProtocols", JSONArray().put("http/1.1"))
                        .put("TLSProfile", "Chrome-83")
                )
            }
            json.put("FrontedMeekDialOverrides", overrides)
            json.put("FrontedMeekDialOverridesProbability", 1.0)
        }
        return json.toString()
    }
}
