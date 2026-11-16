package com.dsh.ringthrough;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "com.dsh.ringthrough.STOP";
    public static final String ACTION_WATCH_OFF = "com.dsh.ringthrough.WATCH_OFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_WATCH_OFF.equals(a)) {
            Prefs.setWatch(context, false);
            WatchService.stop(context);
            return;
        }
        RingEngine.stop(context);
    }
}
