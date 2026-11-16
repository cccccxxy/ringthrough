package com.dsh.ringthrough;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 真正出声的地方：把铃声走「闹钟」音频通道播放（静音模式默认不静音闹钟），
 * 响铃期间临时拉高闹钟音量，结束后恢复；可选地临时切回响铃模式。
 */
public final class RingEngine {

    private static final String TAG = "RingEngine";
    public static final int DEFAULT_DURATION_MS = 45000;

    private static final Object LOCK = new Object();

    private static Context app;
    private static Handler main;
    private static NotificationManager nm;

    private static MediaPlayer player;
    private static ToneGenerator tone;
    private static PowerManager.WakeLock wakeLock;
    private static TelephonyManager tele;
    private static PhoneStateListener listener;

    private static boolean ringing;
    private static boolean sawRinging;
    private static long startedAt;
    private static int savedAlarmVolume = -1;
    private static int savedRingerMode = -1;

    private RingEngine() {}

    public static boolean isRinging() {
        synchronized (LOCK) {
            return ringing;
        }
    }

    public static boolean start(Context ctx, String number, String name) {
        return start(ctx, number, name, DEFAULT_DURATION_MS, false);
    }

    /**
     * @param force true 时忽略「仅静音时响铃」判断（试听用）
     */
    public static boolean start(Context ctx, String number, String name, int durationMs, boolean force) {
        synchronized (LOCK) {
            ensure(ctx);
            long now = SystemClock.elapsedRealtime();
            if (ringing) {
                if (now - startedAt < 8000) {
                    Log.i(TAG, "already ringing, skip duplicate");
                    return false;
                }
                stopInternal();
            }

            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;

            if (!force && Prefs.onlyWhenSilent(app)
                    && am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                Log.i(TAG, "ringer is normal, system will ring");
                return false;
            }

            ringing = true;
            sawRinging = false;
            startedAt = now;

            // 按用户设置的百分比临时改闹钟音量，响完再恢复
            try {
                savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                if (max > 0) {
                    int pct = Prefs.volumePercent(app);
                    int target = (int) Math.round(max * pct / 100.0);
                    if (target < 0) target = 0;
                    am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
                }
            } catch (Throwable t) {
                savedAlarmVolume = -1;
            }

            if (Prefs.flipRinger(app)) {
                try {
                    if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                        savedRingerMode = am.getRingerMode();
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    }
                } catch (Throwable t) {
                    savedRingerMode = -1;
                    Log.w(TAG, "flip ringer failed", t);
                }
            }

            acquireWakeLock();
            playRingtone();
            showNotification(number, name);
            watchCallState();

            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stop(app);
                }
            }, Math.max(3000, durationMs));

            Log.i(TAG, "ringing for " + number);
            return true;
        }
    }

    public static void stop(Context ctx) {
        synchronized (LOCK) {
            if (app == null) app = ctx.getApplicationContext();
            stopInternal();
        }
    }

    private static void ensure(Context ctx) {
        if (app != null) return;
        app = ctx.getApplicationContext();
        main = new Handler(Looper.getMainLooper());
        nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void playRingtone() {
        Uri chosen = Prefs.ringtone(app);
        Uri[] candidates = new Uri[]{
                chosen,
                Settings.System.DEFAULT_RINGTONE_URI,
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI
        };
        for (Uri u : candidates) {
            if (u == null) continue;
            MediaPlayer mp = null;
            try {
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setLooping(true);
                mp.setDataSource(app, u);
                final MediaPlayer ref = mp;
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer p) {
                        synchronized (LOCK) {
                            if (!ringing) return;
                            try {
                                p.start();
                            } catch (Throwable t) {
                                Log.w(TAG, "start failed", t);
                            }
                        }
                    }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer p, int what, int extra) {
                        Log.w(TAG, "player error " + what + "/" + extra);
                        return true;
                    }
                });
                mp.prepareAsync();
                player = ref;
                return;
            } catch (Throwable t) {
                Log.w(TAG, "cannot play " + u, t);
                if (mp != null) {
                    try {
                        mp.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        toneLoop();
    }

    private static void toneLoop() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            startTone();
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (LOCK) {
                        if (!ringing) return;
                    }
                    startTone();
                    main.postDelayed(this, 3200);
                }
            }, 3200);
        } catch (Throwable t) {
            Log.w(TAG, "tone generator failed", t);
        }
    }

    private static void startTone() {
        try {
            if (tone != null) tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
        } catch (Throwable ignored) {
        }
    }

    private static void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ringthrough:ring");
            wakeLock.acquire(120000L);
        } catch (Throwable t) {
            wakeLock = null;
        }
    }

    private static void watchCallState() {
        try {
            if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) return;
            tele = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
            if (tele == null) return;
            listener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        sawRinging = true;
                        return;
                    }
                    // 注册监听时系统会立刻回调一次当前状态，
                    // 这时候通话可能还没进入 RINGING，别把自己的铃声掐掉
                    if (state != TelephonyManager.CALL_STATE_OFFHOOK
                            && !sawRinging
                            && SystemClock.elapsedRealtime() - startedAt < 3000) return;
                    // 已接听 / 已挂断 -> 立刻停
                    stop(app);
                }
            };
            tele.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Throwable t) {
            Log.w(TAG, "watch call state failed", t);
            tele = null;
            listener = null;
        }
    }

    private static void showNotification(String number, String name) {
        if (nm == null) return;
        try {
            String title;
            if (name != null && !name.isEmpty()) title = name + " 来电";
            else if (number != null && !number.isEmpty()) title = Numbers.pretty(number) + " 来电";
            else title = "白名单来电";

            Intent open = new Intent(app, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(app, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent stopIntent = new Intent(app, StopReceiver.class);
            stopIntent.setAction(StopReceiver.ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getBroadcast(app, 2, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = new Notification.Builder(app, App.CH_RING)
                    .setSmallIcon(R.drawable.ic_app)
                    .setContentTitle(title)
                    .setContentText("白名单来电，正在响铃")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setShowWhen(true)
                    .setContentIntent(pi)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(app, R.drawable.ic_app),
                            "停止响铃", stopPi).build());
            nm.notify(NotificationIds.RING, b.build());
        } catch (Throwable t) {
            Log.w(TAG, "notification failed", t);
        }
    }

    private static void cancelNotification() {
        try {
            if (nm != null) nm.cancel(NotificationIds.RING);
        } catch (Throwable ignored) {
        }
    }

    private static void stopInternal() {
        if (!ringing && player == null && tone == null && savedAlarmVolume < 0) {
            return;
        }
        ringing = false;

        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        if (tone != null) {
            try {
                tone.stopTone();
            } catch (Throwable ignored) {
            }
            try {
                tone.release();
            } catch (Throwable ignored) {
            }
            tone = null;
        }
        if (tele != null && listener != null) {
            try {
                tele.listen(listener, PhoneStateListener.LISTEN_NONE);
            } catch (Throwable ignored) {
            }
        }
        tele = null;
        listener = null;

        if (main != null) main.removeCallbacksAndMessages(null);

        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            if (savedAlarmVolume >= 0) {
                try {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
                } catch (Throwable ignored) {
                }
            }
            if (savedRingerMode >= 0) {
                try {
                    if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(savedRingerMode);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        savedAlarmVolume = -1;
        savedRingerMode = -1;

        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Throwable ignored) {
            }
            wakeLock = null;
        }
        cancelNotification();
        Log.i(TAG, "stopped");
    }
}
