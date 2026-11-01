package ca.psiphon;

import android.content.Context;
import android.net.VpnService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * نسخهٔ جایگزین ca.psiphon.PsiphonTunnel بدون AAR.
 *
 * هستهٔ Psiphon به‌صورت یک باینری جدا (libpsiphon.so) داخل APK قرار دارد و اینجا
 * به‌عنوان یک پروسه اجرا می‌شود. نوتیس‌ها از stdout آن به‌صورت JSON خوانده و به
 * callbackهای HostService تبدیل می‌شوند. API عمومی را حفظ کرده‌ایم تا بقیه کد بدون تغییر کامپایل شود.
 *
 * نکته: نام نوتیس‌ها و کلیدهای JSON بر اساس قرارداد معمول Psiphon نوشته شده‌اند و
 * باید با اولین اجرای واقعی لاگ بررسی شوند.
 */
public class PsiphonTunnel {

    private static final String BINARY_NAME = "libpsiphon.so";
    private static final String CONFIG_FILE = "psiphon.config";
    private static final String SERVER_LIST_FILE = "psiphon_server_entries.txt";
    private static final String UPGRADE_DOWNLOAD_FILE = "psiphon_upgrade.download";

    public static class Exception extends java.lang.Exception {
        public Exception(String message) {
            super(message);
        }
    }

    /** callbackهای میزبان؛ همه پیش‌فرض خالی هستند تا پیاده‌سازی‌ها فقط آنچه لازم دارند را بازنویسی کنند. */
    public interface HostService {
        default Context getContext() { return null; }
        default void bindToDevice(long fileDescriptor) { }
        default VpnService.Builder vpnServiceBuilder() { return null; }
        default String getPsiphonConfig() { return ""; }
        default void onDiagnosticMessage(String message) { }
        default void onAvailableEgressRegions(List<String> regions) { }
        default void onSocksProxyPortInUse(int port) { }
        default void onHttpProxyPortInUse(int port) { }
        default void onListeningSocksProxyPort(int port) { }
        default void onListeningHttpProxyPort(int port) { }
        default void onUpstreamProxyError(String message) { }
        default void onConnecting() { }
        default void onConnected() { }
        default void onHomepage(String url) { }
        default void onClientRegion(String region) { }
        default void onClientUpgradeDownloaded(String filename) { }
        default void onClientIsLatestVersion() { }
        default void onUntunneledAddress(String address) { }
        default void onBytesTransferred(long sent, long received) { }
        default void onStartedWaitingForNetworkConnectivity() { }
        default void onStoppedWaitingForNetworkConnectivity() { }
        default void onServerAlert(String reason, String subject, List<String> actionURLs) { }
        default void onApplicationParameters(Object o) { }
        default void onExiting() { }
    }

    public interface HostLogger {
        void onDiagnosticMessage(String message);
    }

    public interface HostFeedbackHandler {
        void sendFeedbackCompleted(java.lang.Exception e);
    }

    /** ارسال فیدبک در حالت باینری پشتیبانی نمی‌شود؛ فقط برای سازگاری API نگه داشته شده. */
    public static class PsiphonTunnelFeedback {
        public void startSendFeedback(Context context,
                                      HostFeedbackHandler feedbackHandler,
                                      HostLogger logger,
                                      String feedbackConfigJson,
                                      String diagnosticsJson,
                                      String uploadPath,
                                      String clientPlatformPrefix,
                                      String clientPlatformSuffix) {
            feedbackHandler.sendFeedbackCompleted(
                    new Exception("ارسال بازخورد در نسخهٔ باینری Psiphon پشتیبانی نمی‌شود"));
        }

        public void shutdown() { }
    }

    private final HostService hostService;
    private Process process;
    private Thread readerThread;
    private volatile int localSocksProxyPort = 0;
    private volatile boolean vpnMode = false;
    private String clientPlatformPrefix = "";
    private String clientPlatformSuffix = "";

    private PsiphonTunnel(HostService hostService) {
        this.hostService = hostService;
    }

    public static PsiphonTunnel newPsiphonTunnel(HostService hostService) {
        return new PsiphonTunnel(hostService);
    }

    public static String getDefaultUpgradeDownloadFilePath(Context context) {
        return new File(context.getFilesDir(), UPGRADE_DOWNLOAD_FILE).getAbsolutePath();
    }

    public void setVpnMode(boolean vpnMode) {
        this.vpnMode = vpnMode;
    }

    public void setClientPlatformAffixes(String prefix, String suffix) {
        this.clientPlatformPrefix = prefix == null ? "" : prefix;
        this.clientPlatformSuffix = suffix == null ? "" : suffix;
    }

    public int getLocalSocksProxyPort() {
        return localSocksProxyPort;
    }

    /** انتقال کلید NFC در حالت باینری فعلاً پشتیبانی نمی‌شود. */
    public String exportExchangePayload() {
        return "";
    }

    public void importExchangePayload(String payload) {
        // no-op در حالت باینری
    }

    public synchronized void startTunneling(String serverEntries) throws Exception {
        stop();
        Context context = hostService.getContext();
        if (context == null) {
            throw new Exception("context برای اجرای Psiphon در دسترس نیست");
        }

        File dataDir = context.getFilesDir();
        File configFile = new File(dataDir, CONFIG_FILE);
        File serverListFile = new File(dataDir, SERVER_LIST_FILE);

        try {
            writeFile(configFile, hostService.getPsiphonConfig());
            writeFile(serverListFile, serverEntries == null ? "" : serverEntries);
        } catch (java.io.IOException e) {
            throw new Exception("نوشتن فایل‌های کانفیگ Psiphon ناموفق بود: " + e.getMessage());
        }

        String binary = context.getApplicationInfo().nativeLibraryDir + "/" + BINARY_NAME;
        if (!new File(binary).exists()) {
            throw new Exception("باینری Psiphon پیدا نشد: " + binary);
        }

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add("-config");
        command.add(configFile.getAbsolutePath());
        command.add("-dataRootDirectory");
        command.add(dataDir.getAbsolutePath());
        command.add("-serverList");
        command.add(serverListFile.getAbsolutePath());

        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            throw new Exception("اجرای باینری Psiphon ناموفق بود: " + e.getMessage());
        }

        final Process current = process;
        readerThread = new Thread(() -> readNotices(current), "psiphon-notices");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void restartPsiphon() throws Exception {
        // برای راه‌اندازی مجدد، پروسهٔ قبلی را متوقف و با همان تنظیمات دوباره اجرا می‌کنیم.
        Context context = hostService.getContext();
        if (context == null) {
            throw new Exception("context برای راه‌اندازی مجدد در دسترس نیست");
        }
        String entries = "";
        File serverListFile = new File(context.getFilesDir(), SERVER_LIST_FILE);
        if (serverListFile.exists()) {
            try {
                entries = new String(java.nio.file.Files.readAllBytes(serverListFile.toPath()), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new Exception("خواندن فایل لیست سرورها ناموفق بود: " + e.getMessage());
            }
        }
        startTunneling(entries);
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
            try {
                process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process = null;
        }
        localSocksProxyPort = 0;
    }

    private void readNotices(Process p) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatch(line);
            }
        } catch (java.io.IOException ignored) {
        } finally {
            hostService.onExiting();
        }
    }

    private void dispatch(String line) {
        JSONObject notice;
        try {
            notice = new JSONObject(line);
        } catch (org.json.JSONException e) {
            // خطوط غیر JSON را به‌صورت تشخیصی گزارش می‌کنیم
            hostService.onDiagnosticMessage(line);
            return;
        }

        String type = notice.optString("noticeType", "");
        JSONObject data = notice.optJSONObject("data");
        if (data == null) {
            data = new JSONObject();
        }

        switch (type) {
            case "ListeningSocksProxyPort":
                localSocksProxyPort = data.optInt("port", 0);
                hostService.onListeningSocksProxyPort(localSocksProxyPort);
                break;
            case "ListeningHttpProxyPort":
                hostService.onListeningHttpProxyPort(data.optInt("port", 0));
                break;
            case "SocksProxyPortInUse":
                hostService.onSocksProxyPortInUse(data.optInt("port", 0));
                break;
            case "HttpProxyPortInUse":
                hostService.onHttpProxyPortInUse(data.optInt("port", 0));
                break;
            case "Connecting":
            case "ConnectingServer":
                hostService.onConnecting();
                break;
            case "ActiveTunnel":
                // نوتیس واقعی اتصال تونل (در لاگ دیده شد)
                hostService.onConnected();
                break;
            case "ConnectedServerRegion":
                hostService.onClientRegion(data.optString("serverRegion", ""));
                break;
            case "Tunnels":
                if (data.optInt("count", 0) > 0) {
                    hostService.onConnected();
                }
                break;
            case "Homepage":
                hostService.onHomepage(data.optString("url", ""));
                break;
            case "ClientRegion":
                hostService.onClientRegion(data.optString("region", ""));
                break;
            case "AvailableEgressRegions":
                hostService.onAvailableEgressRegions(toStringList(data.optJSONArray("regions")));
                break;
            case "UntunneledAddress":
                hostService.onUntunneledAddress(data.optString("address", ""));
                break;
            case "BytesTransferred":
                hostService.onBytesTransferred(data.optLong("sent", 0), data.optLong("received", 0));
                break;
            case "StartedWaitingForNetworkConnectivity":
                hostService.onStartedWaitingForNetworkConnectivity();
                break;
            case "StoppedWaitingForNetworkConnectivity":
                hostService.onStoppedWaitingForNetworkConnectivity();
                break;
            case "ClientUpgradeDownloaded":
                hostService.onClientUpgradeDownloaded(data.optString("filename", ""));
                break;
            case "UpstreamProxyError":
                hostService.onUpstreamProxyError(data.optString("message", ""));
                break;
            default:
                hostService.onDiagnosticMessage(line);
                break;
        }
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                result.add(array.optString(i));
            }
        }
        return result;
    }

    private static void writeFile(File file, String content) throws java.io.IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}
