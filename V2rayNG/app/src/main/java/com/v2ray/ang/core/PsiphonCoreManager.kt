package com.v2ray.ang.core

import android.content.Context
import android.util.Base64
import ca.psiphon.PsiphonTunnel
import com.psiphon3.psiphonlibrary.EgressRegionStore
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
import java.net.HttpURLConnection
import java.net.URL
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
    /** Mirror of the Psiphon remote server list, refreshed by .github/workflows/psiphon-server-list-mirror.yml */
    private const val MIRROR_SERVER_LIST_URL =
        "https://raw.githubusercontent.com/UCIRANX/UC-MAX/main/psiphon-mirror/server_list_compressed"
    /** Original Psiphon remote server list on Amazon S3. Used whenever the mirror is not usable. */
    private const val ORIGINAL_SERVER_LIST_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"
    private const val MIRROR_PROBE_TIMEOUT_MS = 10_000
    private const val MIRROR_MIN_BYTES = 1024
    private const val MIRROR_MAX_BYTES = 16 * 1024 * 1024
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

            override fun onAvailableEgressRegions(regions: List<String>) {
                LogUtil.i(AppConfig.TAG, "Psiphon: ${regions.size} کشور/سرور در دسترس است")
                EgressRegionStore.saveKnownRegions(context.applicationContext, regions)
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

    /**
     * Remote server list URLs for Psiphon.
     * The GitHub mirror is probed first. If the probe succeeds, it comes first and the Amazon S3
     * original follows it as a fallback. If the probe fails, only the S3 original is used.
     */
    private fun remoteServerListUrls(): JSONArray {
        val result = JSONArray()
        if (isMirrorReachable()) {
            LogUtil.i(AppConfig.TAG, "Psiphon: mirror available, using it first")
            result.put(JSONObject().put("URL", encodeUrl(MIRROR_SERVER_LIST_URL)))
        } else {
            LogUtil.i(AppConfig.TAG, "Psiphon: mirror unavailable, using S3 original")
        }
        result.put(JSONObject().put("URL", encodeUrl(ORIGINAL_SERVER_LIST_URL)))
        return result
    }

    /** Probes the mirror with a plain GET. Any error or an implausibly small body counts as unavailable. */
    private fun isMirrorReachable(): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(MIRROR_SERVER_LIST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = MIRROR_PROBE_TIMEOUT_MS
                readTimeout = MIRROR_PROBE_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                LogUtil.w(AppConfig.TAG, "Psiphon: mirror probe HTTP ${connection.responseCode}")
                return false
            }
            // readNBytes needs API 33 on Android, and minSdk is 24, so read with a bounded loop instead.
            var total = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (total < MIRROR_MAX_BYTES) {
                    val read = input.read(buffer, 0, minOf(buffer.size, MIRROR_MAX_BYTES - total))
                    if (read < 0) break
                    total += read
                }
            }
            if (total < MIRROR_MIN_BYTES) {
                LogUtil.w(AppConfig.TAG, "Psiphon: mirror probe body too small ($total bytes)")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Psiphon: mirror probe failed: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    /** Psiphon expects each remote list URL base64-encoded inside an object with a "URL" field. */
    private fun encodeUrl(url: String): String =
        Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun buildConfigJson(context: Context, upstreamProxyUrl: String?): String {
        val json = JSONObject()
            .put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            .put("SponsorId", SPONSOR_ID)
            .put("LocalSocksProxyPort", LOCAL_SOCKS_PORT)
            .put("LocalHttpProxyPort", 0)
            .put("EgressRegion", EgressRegionStore.getSelectedRegion(context))
            .put("EmitDiagnosticNotices", true)
            .put("DataRootDirectory", context.filesDir.absolutePath)
            .put("RemoteServerListDownloadFilename", File(context.filesDir, "psiphon_remote_server_list").absolutePath)
            .put("RemoteServerListUrls", remoteServerListUrls())
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
