package com.v2ray.ang.core

import android.content.Context
import ca.psiphon.PsiphonTunnel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.psiphon3.psiphonlibrary.EmbeddedValues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * مدیریت هستهٔ Psiphon (باینری libpsiphon.so) به‌عنوان یک هاپ SOCKS محلی.
 *
 * Psiphon فقط زمانی اجرا می‌شود که پروفایل انتخابی (یا یکی از اعضای زنجیره‌اش)
 * یک هاپ SOCKS با آدرس محلی [LOCAL_SOCKS_HOST]:[LOCAL_SOCKS_PORT] باشد.
 * این شیء فقط چرخهٔ حیات Psiphon را مدیریت می‌کند؛ تصمیم‌های Xray با CoreConfigManager است.
 */
object PsiphonCoreManager {

    const val LOCAL_SOCKS_HOST = "127.0.0.1"
    const val LOCAL_SOCKS_PORT = 10850

    private const val START_TIMEOUT_MS = 30_000L
    private const val PROPAGATION_CHANNEL_ID = "FFFFFFFFFFFFFFFF"
    private const val SPONSOR_ID = "FFFFFFFFFFFFFFFF"

    @Volatile
    private var tunnel: PsiphonTunnel? = null

    /** آیا پروفایل یا زنجیره‌اش به هاپ محلی Psiphon نیاز دارد؟ */
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
     * Psiphon را اجرا می‌کند و تا رسیدن پورت SOCKS منتظر می‌ماند.
     * اگر در مدت [START_TIMEOUT_MS] آماده نشود، Psiphon متوقف و خطا پرتاب می‌شود.
     * فراخوانی تکراری وقتی Psiphon در حال اجراست بی‌اثر است.
     */
    @Synchronized
    fun start(context: Context) {
        if (tunnel != null) {
            LogUtil.i(AppConfig.TAG, "Psiphon: هسته از قبل در حال اجراست")
            return
        }

        val ready = CompletableDeferred<Unit>()
        val host = object : PsiphonTunnel.HostService {
            override fun getContext(): Context = context.applicationContext

            override fun getPsiphonConfig(): String = buildConfigJson(context)

            override fun onListeningSocksProxyPort(port: Int) {
                if (port == LOCAL_SOCKS_PORT) {
                    LogUtil.i(AppConfig.TAG, "Psiphon: پورت SOCKS آماده شد ($port)")
                    ready.complete(Unit)
                } else {
                    ready.completeExceptionally(IllegalStateException("پورت غیرمنتظره Psiphon: $port"))
                }
            }

            override fun onSocksProxyPortInUse(port: Int) {
                ready.completeExceptionally(IllegalStateException("پورت SOCKS در حال استفاده است: $port"))
            }

            override fun onConnected() {
                LogUtil.i(AppConfig.TAG, "Psiphon: تونل برقرار شد")
            }

            override fun onDiagnosticMessage(message: String) {
                LogUtil.d(AppConfig.TAG, "Psiphon: $message")
            }

            override fun onExiting() {
                LogUtil.w(AppConfig.TAG, "Psiphon: پروسه خاتمه یافت")
                ready.completeExceptionally(IllegalStateException("پروسهٔ Psiphon قبل از آماده شدن خاتمه یافت"))
            }
        }

        val psiphon = PsiphonTunnel.newPsiphonTunnel(host)
        tunnel = psiphon
        try {
            val serverList = EmbeddedValues.EMBEDDED_SERVER_LIST.joinToString("\n")
            psiphon.startTunneling(serverList)
            // ترتیب ایمنی (leak prevention): Xray فقط بعد از آماده شدن پورت SOCKS Psiphon بالا می‌آید،
            // تا ترافیک زنجیره هرگز بدون هاپ Psiphon خارج نشود. انتظار حتماً محدود (timeout) است.
            runBlocking { withTimeout(START_TIMEOUT_MS) { ready.await() } }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: راه‌اندازی ناموفق بود", e)
            stop()
            throw IllegalStateException("راه‌اندازی Psiphon ناموفق بود: ${e.message}", e)
        }
    }

    /** توقف Psiphon؛ فراخوانی تکراری بی‌اثر است. */
    @Synchronized
    fun stop() {
        val current = tunnel ?: return
        tunnel = null
        try {
            current.stop()
            LogUtil.i(AppConfig.TAG, "Psiphon: متوقف شد")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Psiphon: خطا هنگام توقف", e)
        }
    }

    /** پروفایل هاپ SOCKS محلی Psiphon را می‌سازد تا کاربر در زنجیره استفاده کند. */
    fun createHopProfile(): String {
        val profile = ProfileItem.create(EConfigType.SOCKS).apply {
            remarks = "Psiphon (local)"
            server = LOCAL_SOCKS_HOST
            serverPort = LOCAL_SOCKS_PORT.toString()
        }
        return MmkvManager.encodeServerConfig("", profile)
    }

    private fun buildConfigJson(context: Context): String =
        JSONObject()
            .put("PropagationChannelId", PROPAGATION_CHANNEL_ID)
            .put("SponsorId", SPONSOR_ID)
            .put("LocalSocksProxyPort", LOCAL_SOCKS_PORT)
            .put("LocalHttpProxyPort", 0)
            .put("EmitDiagnosticNotices", true)
            .put("DataRootDirectory", context.filesDir.absolutePath)
            .put("RemoteServerListDownloadFilename", File(context.filesDir, "psiphon_remote_server_list").absolutePath)
            .put("RemoteServerListUrls", JSONArray(EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON))
            .put("RemoteServerListSignaturePublicKey", EmbeddedValues.REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY)
            .toString()
}
