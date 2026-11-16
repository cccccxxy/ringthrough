package com.dsh.ringthrough;

import java.util.ArrayList;
import java.util.List;

/** 号码归一化与匹配。 */
public final class Numbers {

    private Numbers() {}

    /** 只保留数字，去掉空格、+86、括号、横线等。 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String s = sb.toString();
        // 去掉中国区号前缀 0086 / 86（仅当后面还有 11 位手机号时）
        if (s.length() == 13 && s.startsWith("861")) s = s.substring(2);
        if (s.length() == 15 && s.startsWith("0086")) s = s.substring(4);
        return s;
    }

    /** 后 N 位比较：允许 +86 前缀、老号码少一位等差异。 */
    public static boolean same(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.length() < 5 || y.length() < 5) return false;
        if (x.equals(y)) return true;
        int n = Math.min(11, Math.min(x.length(), y.length()));
        int from = 11;
        if (n < 11) {
            // 比较结尾 n 位
            return x.regionMatches(x.length() - n, y, y.length() - n, n);
        }
        return x.regionMatches(x.length() - from, y, y.length() - from, from);
    }

    /** 138 0013 8000 */
    public static String pretty(String raw) {
        String s = normalize(raw);
        if (s.length() == 11) return s.substring(0, 3) + " " + s.substring(3, 7) + " " + s.substring(7);
        if (raw == null) return "";
        return raw;
    }

    public static List<String[]> dedupe(List<String[]> in) {
        List<String[]> out = new ArrayList<>();
        for (String[] e : in) {
            boolean dup = false;
            for (String[] o : out) {
                if (same(e[1], o[1])) { dup = true; break; }
            }
            if (!dup) out.add(e);
        }
        return out;
    }
}
