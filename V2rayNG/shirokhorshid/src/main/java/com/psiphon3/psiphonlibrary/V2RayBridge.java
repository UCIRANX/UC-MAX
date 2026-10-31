package com.psiphon3.psiphonlibrary;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.psiphon3.TunnelState;

/**
 * پل ارتباطی صفحات شیروخورشید با هستهٔ اپ V2ray.
 * دکمهٔ اتصال Psiphon همان تاگل اپ اصلی را فعال می‌کند تا حالت انتخاب‌شده (تکی/زنجیره‌ای) اعمال شود.
 */
public final class V2RayBridge {
    public static final String ACTION_TOGGLE_SUFFIX = ".action.widget.click";
    public static final String ACTION_STATUS_SUFFIX = ".action.activity";
    public static final String ACTION_STATUS_REQUEST_SUFFIX = ".action.status.request";
    private static final String TOGGLE_RECEIVER = "com.v2ray.ang.receiver.WidgetProvider";

    // کدهای پیام وضعیت (همان مقادیر AppConfig در اپ V2ray)
    private static final int MSG_STATE_RUNNING = 11;
    private static final int MSG_STATE_NOT_RUNNING = 12;
    private static final int MSG_STATE_START_SUCCESS = 31;
    private static final int MSG_STATE_START_FAILURE = 32;
    private static final int MSG_STATE_STOP_SUCCESS = 41;

    private V2RayBridge() {}

    /** اجرا یا توقف هستهٔ V2ray (همان منطق تاگل ویجت) */
    public static void toggle(Context ctx) {
        send(ctx, ACTION_TOGGLE_SUFFIX);
    }

    /** درخواست وضعیت فعلی هسته؛ پاسخ به‌صورت پیام وضعیت می‌رسد */
    public static void requestStatus(Context ctx) {
        send(ctx, ACTION_STATUS_REQUEST_SUFFIX);
    }

    private static void send(Context ctx, String suffix) {
        String pkg = ctx.getPackageName();
        Intent intent = new Intent(pkg + suffix)
                .setComponent(new ComponentName(pkg, TOGGLE_RECEIVER));
        ctx.sendBroadcast(intent);
    }

    /** تبدیل کد پیام V2ray به حالت تونل؛ کدهای ناشناخته null برمی‌گردانند */
    public static TunnelState stateFromKey(int key) {
        switch (key) {
            case MSG_STATE_RUNNING:
            case MSG_STATE_START_SUCCESS:
                return TunnelState.running(
                        TunnelState.ConnectionData.builder()
                                .setNetworkConnectionState(TunnelState.ConnectionData.NetworkConnectionState.CONNECTED)
                                .build());
            case MSG_STATE_NOT_RUNNING:
            case MSG_STATE_START_FAILURE:
            case MSG_STATE_STOP_SUCCESS:
                return TunnelState.stopped();
            default:
                return null;
        }
    }
}
