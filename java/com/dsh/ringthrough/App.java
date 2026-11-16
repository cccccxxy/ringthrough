package com.dsh.ringthrough;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class App extends Application {

    public static final String CH_RING = "ring";
    public static final String CH_WATCH = "watch";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel ring = new NotificationChannel(CH_RING, "白名单来电", NotificationManager.IMPORTANCE_HIGH);
        ring.setDescription("白名单号码来电时的提醒");
        ring.setSound(null, null);
        ring.enableVibration(false);
        ring.setShowBadge(true);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);

        NotificationChannel watch = new NotificationChannel(CH_WATCH, "后台监听", NotificationManager.IMPORTANCE_LOW);
        watch.setDescription("后台监听来电状态用的常驻通知");
        watch.setSound(null, null);
        watch.enableVibration(false);
        watch.setShowBadge(false);
        nm.createNotificationChannel(watch);
    }
}
