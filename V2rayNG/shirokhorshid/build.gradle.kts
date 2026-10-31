plugins {
    alias(libs.plugins.android.library)
}

// ساخت EmbeddedValues.java از متغیرهای محیطی (مقادیر خالی = بیلد ادامه دارد با هشدار)
val embeddedTemplate = """package com.psiphon3.psiphonlibrary;

import android.content.Context;

public class EmbeddedValues {
    public static final String CLIENT_VERSION = "1";
    public static final String[] EMBEDDED_SERVER_LIST = {__EMBEDDED_SERVERS__};
    public static final boolean IGNORE_NON_EMBEDDED_SERVER_ENTRIES = false;
    public static final boolean IS_PLAY_STORE_BUILD = false;
    public static final String PROPAGATION_CHANNEL_ID = "__PROPAGATION__";
    public static final String SPONSOR_ID = "__SPONSOR__";
    public static final String INFO_LINK_URL = "https://psiphon.ca";
    public static final String GET_NEW_VERSION_URL = "https://psiphon.ca";
    public static final String GET_NEW_VERSION_EMAIL = "";
    public static final String FAQ_URL = "https://psiphon.ca/faq.html";
    public static final String DATA_COLLECTION_INFO_URL = "https://psiphon.ca/privacy.html";
    public static final String[] HOME_TAB_URL_EXCLUSIONS = {"psiphon_external_homepage"};
    public static final String ADDITIONAL_PARAMETERS = "";
    public static final String FEEDBACK_ENCRYPTION_PUBLIC_KEY = "";
    public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_URLS_JSON = "[]";
    public static final String REMOTE_SERVER_LIST_URLS_JSON = "__REMOTE_URLS__";
    public static final String REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY = "__SIG__";
    public static final String OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON = "[]";
    public static final String UPGRADE_URLS_JSON = "[]";
    public static final String UPGRADE_SIGNATURE_PUBLIC_KEY = "";
    public static final String SERVER_ENTRY_SIGNATURE_PUBLIC_KEY = "";
    public static final String SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY = "";
    public static final String CONDUIT_COMPARTMENT_ID = "";
    public static final String PROXIED_WEB_APP_HTTP_AUTH_USERNAME = "";
    public static final String PROXIED_WEB_APP_HTTP_AUTH_PASSWORD = "";

    public static void initialize(Context context) {}

    public static boolean hasEverBeenSideLoaded(Context context) {
        return true;
    }
}
"""

val embeddedOut = file("src/main/java/com/psiphon3/psiphonlibrary/EmbeddedValues.java")
// مقادیر پیش‌فرض از پروژه UC-Guard گرفته شده‌اند (همان‌هایی که در آن پروژه با موفقیت وصل می‌شوند).
// این مقادیر عمومی‌اند و نیازی به Secrets ندارند. اگر لازم شد با متغیرهای محیطی بازنویسی می‌شوند.
val propagationId = System.getenv("PSIPHON_PROPAGATION_CHANNEL_ID")?.takeIf { it.isNotBlank() } ?: "FFFFFFFFFFFFFFFF"
val sponsorId = System.getenv("PSIPHON_SPONSOR_ID")?.takeIf { it.isNotBlank() } ?: "FFFFFFFFFFFFFFFF"
val remoteUrlsJson = System.getenv("PSIPHON_REMOTE_SERVER_LIST_URLS_JSON") ?: """[{"URL":"aHR0cHM6Ly9zMy5hbWF6b25hd3MuY29tLy9wc2lwaG9uL3dlYi9tanI0LXAyM3ItcHV3bC9zZXJ2ZXJfbGlzdF9jb21wcmVzc2Vk"}]"""
val remoteSigKey = System.getenv("PSIPHON_REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY") ?: "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="
// لیست سرورهای داخلی Psiphon (هر خط یک سرور، به‌صورت "0 0 0 0 {json}").
// اولویت با متغیر محیطی PSIPHON_EMBEDDED_SERVER_LIST است؛ اگر خالی بود، فایل embedded_server_list.txt کنار همین فایل خوانده می‌شود.
val embeddedListFile = file("embedded_server_list.txt")
val embeddedListText = System.getenv("PSIPHON_EMBEDDED_SERVER_LIST")?.takeIf { it.isNotBlank() }
    ?: if (embeddedListFile.exists()) embeddedListFile.readText(Charsets.UTF_8) else ""
// هر سرور به‌صورت hex کل خط (شامل پیشوند "0 0 0 0 " و JSON) در یک خط بدون فاصله نوشته می‌شود.
// فقط با خط جدید جدا می‌شود، چون هر ورودی خودش فاصله دارد.
val embeddedServerEntries = embeddedListText
    .split('\n', '\r')
    .map { it.trim() }
    .filter { it.contains('{') }
    .map { line ->
        line.toByteArray(Charsets.UTF_8)
            .joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }
if (embeddedServerEntries.isEmpty()) {
    logger.warn("No embedded Psiphon server entries found: Psiphon will rely on the remote server list only.")
}
val embeddedServersJava = embeddedServerEntries.joinToString(",\n        ") { entry ->
    "\"" + entry.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
val generateEmbedded = tasks.register("generateEmbeddedValues") {
    outputs.file(embeddedOut)
    outputs.upToDateWhen { false }
    doLast {
        embeddedOut.parentFile.mkdirs()
        embeddedOut.writeText(
            embeddedTemplate
                .replace("__PROPAGATION__", propagationId)
                .replace("__SPONSOR__", sponsorId)
                .replace("__REMOTE_URLS__", remoteUrlsJson.replace("\"", "\\\""))
                .replace("__SIG__", remoteSigKey)
                .replace("__EMBEDDED_SERVERS__", embeddedServersJava)
        )
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateEmbedded) }

android {
    namespace = "com.psiphon3"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            ndkBuild {
                cFlags += "-DNDEBUG"
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(files("libs/jackson-core-2.2.0.jar"))
    implementation(files("libs/achartengine-1.0.0.jar"))
    implementation(files("libs/snakeyaml-1.10-android.jar"))
    implementation(files("libs/jndcrash-release.aar"))

    implementation(project(":tray"))

    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
    implementation("androidx.work:work-runtime:2.10.0")
    implementation("androidx.work:work-rxjava2:2.10.0")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.5")
    implementation("com.jakewharton.rxrelay2:rxrelay:2.1.0")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.paging:paging-runtime:2.1.2")
    implementation("androidx.paging:paging-rxjava2:2.1.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.github.drfonfon:android-geohash:0.22")
    compileOnly("com.google.auto.value:auto-value:1.5.2")
    annotationProcessor("com.google.auto.value:auto-value:1.5.2")
}
