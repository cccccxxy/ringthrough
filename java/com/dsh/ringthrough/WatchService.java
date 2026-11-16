package com.dsh.ringthrough;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 备用方案：常驻监听通话状态。来电筛选权限拿不到时用它。
 */
@SuppressWarnings("deprecation")
public class WatchService extends Service {

    private static final String TAG = "WatchService";

    private TelephonyManager tele;
    private PhoneStateListener listener;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, WatchService.class);
            c.startForegroundService(i);
        } catch (Throwable t) {
            Log.w(TAG, "start failed", t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WatchService.class));
        } catch (Throwable t) {
            Log.w(TAG, "stop failed", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NotificationIds.WATCH, buildNotification());
        register();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregister();
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void register() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no READ_PHONE_STATE");
            return;
        }
        unregister();
        try {
            tele = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tele == null) return;
            listener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        if (incomingNumber != null && !incomingNumber.isEmpty()
                                && Prefs.matches(WatchService.this, incomingNumber)) {
                            RingEngine.start(WatchService.this, incomingNumber,
                                    Prefs.nameFor(WatchService.this, incomingNumber));
                        }
                    } else {
                        RingEngine.stop(WatchService.this);
                    }
                }
            };
            tele.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Throwable t) {
            Log.w(TAG, "listen failed", t);
        }
    }

    private void unregister() {
        try {
            if (tele != null && listener != null) {
                tele.listen(listener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Throwable ignored) {
        }
        tele = null;
        listener = null;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent off = new Intent(this, StopReceiver.class);
        off.setAction(StopReceiver.ACTION_WATCH_OFF);
        PendingIntent offPi = PendingIntent.getBroadcast(this, 4, off,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, App.CH_WATCH)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("白名单监听运行中")
                .setContentText("静音时白名单来电会响铃")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .addAction(0, "关闭监听", offPi)
                .build();
    }
}
