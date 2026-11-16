package com.dsh.ringthrough;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** 白名单与设置项。 */
public final class Prefs {

    private static final String FILE = "ringthrough";
    private static final String K_ENTRIES = "entries";
    private static final String K_ONLY_SILENT = "onlySilent";
    private static final String K_VOLUME = "volumePercent";
    private static final String K_FLIP = "flipRinger";
    private static final String K_WATCH = "watch";
    private static final String K_RINGTONE = "ringtone";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean onlyWhenSilent(Context c) {
        return sp(c).getBoolean(K_ONLY_SILENT, true);
    }

    public static void setOnlyWhenSilent(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ONLY_SILENT, v).apply();
    }

    /** 响铃时把闹钟音量设成系统的百分之多少（10~100）。 */
    public static int volumePercent(Context c) {
        int v = sp(c).getInt(K_VOLUME, 100);
        if (v < 10) v = 10;
        if (v > 100) v = 100;
        return v;
    }

    public static void setVolumePercent(Context c, int v) {
        if (v < 10) v = 10;
        if (v > 100) v = 100;
        sp(c).edit().putInt(K_VOLUME, v).apply();
    }

    public static boolean flipRinger(Context c) {
        return sp(c).getBoolean(K_FLIP, false);
    }

    public static void setFlipRinger(Context c, boolean v) {
        sp(c).edit().putBoolean(K_FLIP, v).apply();
    }

    public static boolean watch(Context c) {
        return sp(c).getBoolean(K_WATCH, false);
    }

    public static void setWatch(Context c, boolean v) {
        sp(c).edit().putBoolean(K_WATCH, v).apply();
    }

    public static Uri ringtone(Context c) {
        String s = sp(c).getString(K_RINGTONE, null);
        if (s == null || s.isEmpty()) return null;
        try {
            return Uri.parse(s);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setRingtone(Context c, Uri u) {
        sp(c).edit().putString(K_RINGTONE, u == null ? "" : u.toString()).apply();
    }

    public static List<String[]> entries(Context c) {
        String raw = sp(c).getString(K_ENTRIES, "");
        List<String[]> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int p = line.indexOf('\t');
            if (p <= 0) continue;
            String name = line.substring(0, p);
            String num = line.substring(p + 1);
            if (!num.isEmpty()) out.add(new String[]{name, num});
        }
        return out;
    }

    public static void setEntries(Context c, List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : list) sb.append(e[0]).append('\t').append(e[1]).append('\n');
        sp(c).edit().putString(K_ENTRIES, sb.toString()).apply();
    }

    public static void addEntry(Context c, String name, String number) {
        List<String[]> list = entries(c);
        list.add(new String[]{name == null ? "" : name, number});
        setEntries(c, Numbers.dedupe(list));
    }

    public static void removeEntry(Context c, String number) {
        List<String[]> list = entries(c);
        List<String[]> keep = new ArrayList<>();
        for (String[] e : list) {
            if (!Numbers.same(e[1], number)) keep.add(e);
        }
        setEntries(c, keep);
    }

    public static boolean matches(Context c, String number) {
        if (number == null || number.isEmpty()) return false;
        List<String[]> list = entries(c);
        for (String[] e : list) {
            if (Numbers.same(e[1], number)) return true;
        }
        return false;
    }

    /** 白名单里保存的名字，找不到返回 null。 */
    public static String nameFor(Context c, String number) {
        if (number == null) return null;
        List<String[]> list = entries(c);
        for (String[] e : list) {
            if (Numbers.same(e[1], number)) return e[0];
        }
        return null;
    }
}
