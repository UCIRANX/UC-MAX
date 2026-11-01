package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.InflaterInputStream

/**
 * Supplies the Psiphon server entries that are handed to the tunnel core at start.
 *
 * The core is given these entries directly, so it does not need to download a server list itself.
 * Sources are tried in this order:
 *   1. GitHub mirror (refreshed every 12 hours by the workflow)
 *   2. Amazon S3 original
 *   3. the last list that was downloaded successfully (cached on disk)
 *
 * The APK carries no server entries. If every source fails, the core starts with none.
 *
 * Each entry is a hex-encoded line.
 */
object PsiphonServerList {

    const val MIRROR_URL =
        "https://raw.githubusercontent.com/UCIRANX/UC-MAX/main/psiphon-mirror/server_list_compressed"
    const val ORIGINAL_URL =
        "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"

    private const val CACHE_FILE = "uc_psiphon_entries_cache.txt"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 16 * 1024 * 1024

    /** Returns the hex-encoded entries to pass to the core. Blocking; call from a background thread. */
    fun resolve(context: Context): List<String> {
        val sources = listOf("mirror" to MIRROR_URL, "s3" to ORIGINAL_URL)
        for ((name, url) in sources) {
            val entries = download(url)
            if (entries != null) {
                saveCache(context, entries)
                LogUtil.i(AppConfig.TAG, "Psiphon: ${entries.size} server entries from $name")
                return entries
            }
        }
        val cached = loadCache(context)
        if (cached != null) {
            LogUtil.w(AppConfig.TAG, "Psiphon: download failed, using cached entries (${cached.size})")
            return cached
        }
        LogUtil.w(AppConfig.TAG, "Psiphon: download failed and no cache, starting with no server entries")
        return emptyList()
    }

    /**
     * Downloads one server list and returns its hex entries, or null if the download is unusable.
     * The file is zlib-compressed JSON whose "data" field holds hex lines, one server per line.
     */
    private fun download(url: String): List<String>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                LogUtil.w(AppConfig.TAG, "Psiphon: $url returned HTTP ${connection.responseCode}")
                return null
            }
            val compressed = readBounded(connection.inputStream)
            val plain = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
            val data = JSONObject(String(plain, Charsets.UTF_8)).getString("data")
            val lines = data.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || !isValidEntry(lines.first())) {
                LogUtil.w(AppConfig.TAG, "Psiphon: $url has no usable server entries")
                null
            } else {
                lines
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Psiphon: $url failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most MAX_BYTES. Uses a loop, not readNBytes, because minSdk is 24. */
    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (total < MAX_BYTES) {
                val read = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
        }
        return out.toByteArray()
    }

    /** A usable entry decodes from hex to text that contains the JSON object. */
    private fun isValidEntry(hex: String): Boolean = try {
        val text = String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
        text.contains('{')
    } catch (e: Exception) {
        false
    }

    private fun saveCache(context: Context, entries: List<String>) {
        try {
            val target = File(context.filesDir, CACHE_FILE)
            val temp = File(context.filesDir, "$CACHE_FILE.tmp")
            temp.writeText(entries.joinToString("\n"))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Psiphon: could not save entries cache: ${e.message}")
        }
    }

    private fun loadCache(context: Context): List<String>? = try {
        File(context.filesDir, CACHE_FILE)
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
