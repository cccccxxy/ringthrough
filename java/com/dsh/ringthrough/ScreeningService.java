package com.dsh.ringthrough;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

/**
 * 系统原生「来电筛选」入口：每通来电先经过这里，不需要常驻后台。
 */
public class ScreeningService extends CallScreeningService {

    private static final String TAG = "ScreeningService";

    @Override
    public void onScreenCall(Call.Details details) {
        try {
            if (details != null && details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                Uri handle = details.getHandle();
                String number = handle == null ? null : handle.getSchemeSpecificPart();
                if (number != null && !number.isEmpty() && Prefs.matches(this, number)) {
                    RingEngine.start(this, number, Prefs.nameFor(this, number));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "screen failed", t);
        }
        // 必须在 5 秒内回应，否则系统会断开；这里一律放行，只负责响铃
        try {
            respondToCall(details, new CallResponse.Builder().build());
        } catch (Throwable t) {
            Log.w(TAG, "respond failed", t);
        }
    }
}
