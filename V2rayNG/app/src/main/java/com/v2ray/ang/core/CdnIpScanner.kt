package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * اسکنر IP لبه (Edge) آکامای.
 *
 * - هر رنج (مثل 2.8.7 یا 5.3.8 یا CIDR) در هر دور یک IP تصادفی داخل خودش می‌گیرد.
 * - ترتیب رنج‌ها در هر دور تصادفی است و IPهای تست‌شده در اجراهای قبلی دوباره انتخاب نمی‌شوند.
 * - IPهای سالم در تنظیمات UC MAX و در فیلد «CDN edge IPs» Psiphon ذخیره می‌شوند.
 */
object CdnIpScanner {

    /** رنج‌های رسمی Akamai که به‌صورت پیش‌فرض در اسکنر قرار دارند */
    val DEFAULT_AKAMAI_RANGES: List<String> = listOf(
        "2.16.0.0/13",
        "2.16.0.0/24",
        "2.16.1.0/24",
        "2.16.2.0/24",
        "2.16.3.0/24",
        "2.16.4.0/23",
        "2.16.6.0/24",
        "2.16.7.0/24",
        "2.16.8.0/24",
        "2.16.10.0/24",
        "2.16.11.0/24",
        "2.16.12.0/23",
        "2.16.14.0/24",
        "2.16.15.0/24",
        "2.16.16.0/24",
        "2.16.18.0/24",
        "2.16.22.0/24",
        "2.16.23.0/24",
        "2.16.24.0/23",
        "2.16.26.0/24",
        "2.16.27.0/24",
        "2.16.28.0/24",
        "2.16.29.0/24",
        "2.16.30.0/23",
        "2.16.32.0/23",
        "2.16.34.0/23",
        "2.16.34.0/24",
        "2.16.35.0/24",
        "2.16.36.0/23",
        "2.16.36.0/24",
        "2.16.37.0/24",
        "2.16.38.0/23",
        "2.16.40.0/24",
        "2.16.41.0/24",
        "2.16.42.0/23",
        "2.16.45.0/24",
        "2.16.46.0/23",
        "2.16.48.0/24",
        "2.16.49.0/24",
        "2.16.50.0/23",
        "2.16.50.0/24",
        "2.16.52.0/24",
        "2.16.53.0/24",
        "2.16.54.0/24",
        "2.16.55.0/24",
        "2.16.56.0/23",
        "2.16.58.0/23",
        "2.16.60.0/23",
        "2.16.61.0/24",
        "2.16.62.0/24",
        "2.16.63.0/24",
        "2.16.64.0/24",
        "2.16.65.0/24",
        "2.16.66.0/23",
        "2.16.68.0/23",
        "2.16.70.0/24",
        "2.16.71.0/24",
        "2.16.72.0/23",
        "2.16.74.0/23",
        "2.16.76.0/24",
        "2.16.77.0/24",
        "2.16.78.0/23",
        "2.16.80.0/23",
        "2.16.82.0/24",
        "2.16.83.0/24",
        "2.16.84.0/24",
        "2.16.85.0/24",
        "2.16.86.0/23",
        "2.16.88.0/24",
        "2.16.89.0/24",
        "2.16.90.0/23",
        "2.16.92.0/24",
        "2.16.93.0/24",
        "2.16.94.0/24",
        "2.16.95.0/24",
        "2.16.96.0/22",
        "2.16.100.0/23",
        "2.16.102.0/24",
        "2.16.103.0/24",
        "2.16.104.0/23",
        "2.16.106.0/24",
        "2.16.107.0/24",
        "2.16.108.0/24",
        "2.16.109.0/24",
        "2.16.110.0/24",
        "2.16.111.0/24",
        "2.16.112.0/23",
        "2.16.114.0/23",
        "2.16.116.0/23",
        "2.16.116.0/24",
        "2.16.117.0/24",
        "2.16.118.0/24",
        "2.16.119.0/24",
        "2.16.120.0/24",
        "2.16.121.0/24",
        "2.16.122.0/24",
        "2.16.123.0/24",
        "2.16.124.0/23",
        "2.16.126.0/23",
        "2.16.128.0/24",
        "2.16.129.0/24",
        "2.16.130.0/24",
        "2.16.131.0/24",
        "2.16.134.0/24",
        "2.16.135.0/24",
        "2.16.136.0/24",
        "2.16.137.0/24",
        "2.16.138.0/23",
        "2.16.140.0/23",
        "2.16.142.0/24",
        "2.16.143.0/24",
        "2.16.144.0/24",
        "2.16.145.0/24",
        "2.16.146.0/24",
        "2.16.147.0/24",
        "2.16.148.0/24",
        "2.16.149.0/24",
        "2.16.150.0/23",
        "2.16.152.0/24",
        "2.16.153.0/24",
        "2.16.154.0/24",
        "2.16.155.0/24",
        "2.16.156.0/23",
        "2.16.158.0/24",
        "2.16.159.0/24",
        "2.16.160.0/23",
        "2.16.162.0/24",
        "2.16.163.0/24",
        "2.16.164.0/24",
        "2.16.165.0/24",
        "2.16.166.0/24",
        "2.16.167.0/24",
        "2.16.168.0/24",
        "2.16.169.0/24",
        "2.16.170.0/24",
        "2.16.171.0/24",
        "2.16.172.0/24",
        "2.16.173.0/24",
        "2.16.174.0/23",
        "2.16.176.0/22",
        "2.16.177.0/24",
        "2.16.180.0/24",
        "2.16.181.0/24",
        "2.16.182.0/24",
        "2.16.183.0/24",
        "2.16.184.0/23",
        "2.16.186.0/24",
        "2.16.187.0/24",
        "2.16.188.0/22",
        "2.16.188.0/23",
        "2.16.192.0/22",
        "2.16.196.0/24",
        "2.16.197.0/24",
        "2.16.198.0/24",
        "2.16.199.0/24",
        "2.16.200.0/24",
        "2.16.201.0/24",
        "2.16.202.0/24",
        "2.16.203.0/24",
        "2.16.204.0/24",
        "2.16.205.0/24",
        "2.16.206.0/24",
        "2.16.207.0/24",
        "2.16.208.0/22",
        "2.16.212.0/22",
        "2.16.216.0/22",
        "2.16.224.0/22",
        "2.16.228.0/22",
        "2.16.232.0/22",
        "2.16.236.0/24",
        "2.16.237.0/24",
        "2.16.238.0/24",
        "2.16.239.0/24",
        "2.16.240.0/24",
        "2.16.241.0/24",
        "2.16.242.0/24",
        "2.16.243.0/24",
        "2.16.244.0/24",
        "2.16.245.0/24",
        "2.16.246.0/24",
        "2.16.247.0/24",
        "2.16.248.0/22",
        "2.16.252.0/22",
        "2.17.0.0/22",
        "2.17.4.0/22",
        "2.17.8.0/22",
        "2.17.12.0/23",
        "2.17.14.0/24",
        "2.17.15.0/24",
        "2.17.16.0/22",
        "2.17.20.0/23",
        "2.17.22.0/24",
        "2.17.23.0/24",
        "2.17.24.0/22",
        "2.17.28.0/22",
        "2.17.32.0/23",
        "2.17.35.0/24",
        "2.17.36.0/24",
        "2.17.37.0/24",
        "2.17.38.0/24",
        "2.17.39.0/24",
        "2.17.40.0/24",
        "2.17.41.0/24",
        "2.17.42.0/24",
        "2.17.43.0/24",
        "2.17.44.0/24",
        "2.17.45.0/24",
        "2.17.46.0/24",
        "2.17.47.0/24",
        "2.17.48.0/21",
        "2.17.56.0/21",
        "2.17.64.0/20",
        "2.17.80.0/20",
        "2.17.96.0/22",
        "2.17.101.0/24",
        "2.17.102.0/24",
        "2.17.103.0/24",
        "2.17.104.0/24",
        "2.17.105.0/24",
        "2.17.106.0/24",
        "2.17.107.0/24",
        "2.17.112.0/24",
        "2.17.113.0/24",
        "2.17.114.0/24",
        "2.17.115.0/24",
        "2.17.116.0/22",
        "2.17.128.0/22",
        "2.17.128.0/23",
        "2.17.130.0/24",
        "2.17.136.0/22",
        "2.17.140.0/22",
        "2.17.147.0/24",
        "2.17.148.0/22",
        "2.17.152.0/22",
        "2.17.156.0/22",
        "2.17.160.0/24",
        "2.17.161.0/24",
        "2.17.162.0/24",
        "2.17.163.0/24",
        "2.17.164.0/24",
        "2.17.165.0/24",
        "2.17.166.0/24",
        "2.17.167.0/24",
        "2.17.168.0/22",
        "2.17.172.0/22",
        "2.17.176.0/20",
        "2.17.196.0/22",
        "2.17.200.0/24",
        "2.17.201.0/24",
        "2.17.202.0/24",
        "2.17.203.0/24",
        "2.17.208.0/24",
        "2.17.209.0/24",
        "2.17.210.0/24",
        "2.17.211.0/24",
        "2.17.212.0/22",
        "2.17.216.0/23",
        "2.17.220.0/22",
        "2.17.224.0/22",
        "2.17.228.0/22",
        "2.17.232.0/22",
        "2.17.236.0/22",
        "2.17.240.0/22",
        "2.17.244.0/22",
        "2.17.248.0/24",
        "2.17.249.0/24",
        "2.17.250.0/24",
        "2.17.251.0/24",
        "2.17.252.0/22",
        "23.0.0.0/12",
        "23.32.0.0/11",
        "23.48.0.0/13",
        "23.64.0.0/14",
        "23.72.0.0/13",
        "23.192.0.0/11",
    )

    const val SAVED_IPS_KEY = "pref_cdn_edge_ips"
    const val PROBE_SNI = "www.akamai.com"

    /** نام فایل تنظیمات Psiphon و کلید فیلد «CDN edge IPs» (همان مقادیر منابع Psiphon) */
    private const val PSIPHON_PREFS_NAME = "moreOptionsPreferencesName"
    private const val PSIPHON_CDN_IP_KEY = "cdnFrontingCustomIpListPreference"

    private const val CONCURRENCY = 40
    private const val MAX_RUN_TESTED = 2_000_000
    private const val CONNECT_TIMEOUT_MS = 2000
    private const val IO_TIMEOUT_MS = 3000

    data class State(
        val running: Boolean = false,
        val scanned: Int = 0,
        val healthy: Int = 0,
        val saved: Int = 0,
    )

    private val _state = MutableStateFlow(State(saved = savedIps().size))
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Any()
    /** IPهای تست‌شدهٔ همین اجرا؛ با شروع اجرای جدید پاک می‌شود تا هر اسکن ترتیب تازه داشته باشد */
    private val runTested = LinkedHashSet<String>()
    private var job: Job? = null
    private var appContext: Context? = null

    fun parseEntries(text: String): List<String> =
        text.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    fun savedIps(): List<String> =
        parseEntries(MmkvManager.decodeSettingsString(SAVED_IPS_KEY, "").orEmpty()).filter { isIpv4(it) }

    /**
     * IP تصادفی از یک ورودی:
     * - a.b.c   → a.b.c.(1..254)
     * - a.b     → a.b.(0..255).(1..254)
     * - a.b.c.d → همان IP
     * - a.b.c.d/n → IP تصادفی داخل CIDR
     */
    fun randomIpFrom(entry: String, rnd: Random): String? {
        if (entry.contains('/')) {
            val base = entry.substringBefore('/')
            val bits = entry.substringAfter('/').toIntOrNull() ?: return null
            if (!isIpv4(base) || bits !in 8..32) return null
            val size = 1L shl (32 - bits)
            val start = ipToLong(base) and (size - 1).inv() and 0xFFFFFFFFL
            if (size <= 2) return longToIp(start)
            return longToIp(start + rnd.nextLong(1, size - 1))
        }
        val parts = entry.split('.')
        if (parts.any { part -> part.toIntOrNull()?.let { it in 0..255 } != true }) return null
        return when (parts.size) {
            4 -> entry
            3 -> "${parts[0]}.${parts[1]}.${parts[2]}.${rnd.nextInt(1, 255)}"
            2 -> "${parts[0]}.${parts[1]}.${rnd.nextInt(0, 256)}.${rnd.nextInt(1, 255)}"
            else -> null
        }
    }

    private fun ipToLong(ip: String): Long =
        ip.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun longToIp(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }

    /** IP تازه از یک ورودی که در همین اجرا تست نشده؛ null اگر رنج پر شده یا ورودی نامعتبر است */
    private fun freshIpFrom(entry: String, rnd: Random): String? {
        repeat(8) {
            val ip = randomIpFrom(entry, rnd) ?: return null
            synchronized(runTested) {
                if (runTested.add(ip)) {
                    if (runTested.size > MAX_RUN_TESTED) {
                        runTested.remove(runTested.first())
                    }
                    return ip
                }
            }
        }
        return null
    }

    /** تست یک IP: اتصال TCP به 443 و هندشیک TLS با SNI آکامای */
    private fun probe(ip: String): Boolean = try {
        val raw = Socket()
        raw.connect(InetSocketAddress(ip, 443), CONNECT_TIMEOUT_MS)
        raw.soTimeout = IO_TIMEOUT_MS
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(raw, PROBE_SNI, 443, true) as SSLSocket
        ssl.startHandshake()
        ssl.close()
        true
    } catch (e: Exception) {
        false
    }

    /** شروع اسکن. manualMode=false یعنی رنج‌ها، true یعنی IP/رنج‌های دستی. */
    @Synchronized
    fun start(context: Context, rangesText: String, manualText: String, manualMode: Boolean) {
        if (job?.isActive == true) return
        appContext = context.applicationContext
        val entries = parseEntries(if (manualMode) manualText else rangesText)
            .filter { it.isNotEmpty() }
        if (entries.isEmpty()) return

        val scanned = AtomicInteger(0)
        val healthy = AtomicInteger(0)
        synchronized(runTested) { runTested.clear() }
        _state.value = State(running = true, saved = savedIps().size)

        job = scope.launch {
            val semaphore = Semaphore(CONCURRENCY)
            val rnd = Random(System.nanoTime())
            try {
                coroutineScope {
                    // اسکن تا زمانی که کاربر توقف کند ادامه دارد؛ همهٔ IPهای رنج‌ها در نهایت بررسی می‌شوند
                    while (isActive) {
                        var producedThisRound = 0
                        // ترتیب رنج‌ها در هر دور تصادفی است؛ هر رنج در نوبت خودش یک IP تصادفی می‌گیرد
                        for (entry in entries.shuffled(rnd)) {
                            if (!isActive) break
                            val ip = freshIpFrom(entry, rnd) ?: continue
                            producedThisRound++
                            launch {
                                semaphore.withPermit {
                                    if (!isActive) return@withPermit
                                    val ok = probe(ip)
                                    val done = scanned.incrementAndGet()
                                    var savedCount = _state.value.saved
                                    if (ok) {
                                        healthy.incrementAndGet()
                                        savedCount = saveIp(ip)
                                    }
                                    _state.update {
                                        it.copy(scanned = done, healthy = healthy.get(), saved = savedCount)
                                    }
                                }
                            }
                        }
                        // اگر همهٔ ورودی‌ها دیگر IP تازه‌ای نداشتند، اسکن تمام می‌شود
                        if (producedThisRound == 0) break
                    }
                }
            } finally {
                _state.update { it.copy(running = false) }
                LogUtil.i(AppConfig.TAG, "CdnIpScanner: پایان اسکن؛ اسکن‌شده=${scanned.get()} سالم=${healthy.get()}")
            }
        }
    }

    /** توقف اسکن؛ IPهای سالم تا این لحظه قبلاً ذخیره شده‌اند */
    fun stop() {
        job?.cancel()
        _state.update { it.copy(running = false) }
    }

    /** ذخیرهٔ یک IP سالم و همگام‌سازی با فیلد CDN edge IPs در Psiphon؛ تعداد کل ذخیره‌شده را برمی‌گرداند */
    private fun saveIp(ip: String): Int = synchronized(saveLock) {
        val all = savedIps().toMutableList()
        if (ip !in all) {
            all.add(ip)
            MmkvManager.encodeSettings(SAVED_IPS_KEY, all.joinToString(","))
            appContext?.getSharedPreferences(PSIPHON_PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(PSIPHON_CDN_IP_KEY, all.joinToString(", "))
                ?.apply()
        }
        all.size
    }
}
