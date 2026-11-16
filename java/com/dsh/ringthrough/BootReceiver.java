package com.dsh.ringthrough;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        if (Prefs.watch(context)) {
            WatchService.start(context);
        }
    }
}
