package com.dsh.ringthrough;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PICK_CONTACT = 101;
    private static final int REQ_ROLE = 102;
    private static final int REQ_RINGTONE = 103;
    private static final int REQ_PERM_CONTACTS = 104;
    private static final int REQ_PERM_WATCH = 105;
    private static final int REQ_PERM_NOTIF = 106;

    private TextView tvScreening, tvScreeningHint, tvWatch, tvWatchHint, tvMode, tvModeHint, tvEmpty, tvRingtone;
    private Button btnGrant, btnAddContact, btnAddManual, btnPick, btnTest, btnBattery;
    private Switch swWatch, swSilentOnly, swFlip;
    private SeekBar sbVolume;
    private TextView tvVolume;
    private LinearLayout listEntries;

    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyInsets();

        tvScreening = (TextView) findViewById(R.id.tvScreening);
        tvScreeningHint = (TextView) findViewById(R.id.tvScreeningHint);
        tvWatch = (TextView) findViewById(R.id.tvWatch);
        tvWatchHint = (TextView) findViewById(R.id.tvWatchHint);
        tvMode = (TextView) findViewById(R.id.tvMode);
        tvModeHint = (TextView) findViewById(R.id.tvModeHint);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        tvRingtone = (TextView) findViewById(R.id.tvRingtone);

        btnGrant = (Button) findViewById(R.id.btnGrant);
        btnAddContact = (Button) findViewById(R.id.btnAddContact);
        btnAddManual = (Button) findViewById(R.id.btnAddManual);
        btnPick = (Button) findViewById(R.id.btnPick);
        btnTest = (Button) findViewById(R.id.btnTest);
        btnBattery = (Button) findViewById(R.id.btnBattery);

        swWatch = (Switch) findViewById(R.id.swWatch);
        swSilentOnly = (Switch) findViewById(R.id.swSilentOnly);
        sbVolume = (SeekBar) findViewById(R.id.sbVolume);
        tvVolume = (TextView) findViewById(R.id.tvVolume);
        swFlip = (Switch) findViewById(R.id.swFlip);

        listEntries = (LinearLayout) findViewById(R.id.listEntries);

        ((TextView) findViewById(R.id.tvNote)).setText(
                "说明\n"
                        + "• 铃声走「闹钟」通道：静音、振动下都能响；系统的「勿扰 - 完全静音」则任何 App 都放不出声。\n"
                        + "• 只拦白名单号码，其他来电保持安静。\n"
                        + "• 小米 / 红米：还要在系统设置里允许本 App「自启动」，省电策略设为「无限制」，否则被清理后就不响了。");

        btnGrant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestScreeningRole();
            }
        });

        btnAddContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERM_CONTACTS);
                    return;
                }
                openContactPicker();
            }
        });

        btnAddManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showManualDialog();
            }
        });

        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickRingtone();
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ok = RingEngine.start(MainActivity.this, "13800138000", "试听", 6000, true);
                toast(ok ? "正在播放 6 秒，注意听" : "播放失败，试试换个铃声");
            }
        });

        btnBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });

        swWatch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                if (!checked) {
                    Prefs.setWatch(MainActivity.this, false);
                    WatchService.stop(MainActivity.this);
                    refresh();
                    return;
                }
                String[] need = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG};
                if (!hasAll(need)) {
                    requestPermissions(need, REQ_PERM_WATCH);
                    return;
                }
                startWatch();
            }
        });

        swSilentOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                Prefs.setOnlyWhenSilent(MainActivity.this, checked);
            }
        });

        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvVolume.setText(progress + "%");
                if (fromUser && !binding) {
                    Prefs.setVolumePercent(MainActivity.this, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Prefs.setVolumePercent(MainActivity.this, bar.getProgress());
            }
        });

        swFlip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                Prefs.setFlipRinger(MainActivity.this, checked);
                if (checked) ensurePolicyAccess();
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void applyInsets() {
        final View root = findViewById(R.id.root);
        final int pl = root.getPaddingLeft();
        final int pt = root.getPaddingTop();
        final int pr = root.getPaddingRight();
        final int pb = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                v.setPadding(pl + ins.getSystemWindowInsetLeft(),
                        pt + ins.getSystemWindowInsetTop(),
                        pr + ins.getSystemWindowInsetRight(),
                        pb + ins.getSystemWindowInsetBottom());
                return ins;
            }
        });
    }

    private void refresh() {
        binding = true;
        try {
            boolean held = isScreeningHeld();
            tvScreening.setText(held ? "来电筛选：已授权" : "来电筛选：未授权");
            tvScreening.setTextColor(getColor(held ? R.color.good : R.color.text));
            btnGrant.setVisibility(held ? View.GONE : View.VISIBLE);
            tvScreeningHint.setText(held
                    ? "每通来电先交给本 App 判断，不用常驻后台"
                    : "点「去授权」；机型不支持时改用下面的后台监听");

            boolean w = Prefs.watch(this);
            swWatch.setChecked(w);
            tvWatch.setText(w ? "后台监听：运行中" : "后台监听：已关闭");
            tvWatch.setTextColor(getColor(w ? R.color.good : R.color.text));
            tvWatchHint.setText(w
                    ? "下拉通知里可以一键关掉"
                    : "来电筛选拿不到时用这个，需要电话 / 通话记录权限");

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int mode = am == null ? AudioManager.RINGER_MODE_NORMAL : am.getRingerMode();
            String modeText = mode == AudioManager.RINGER_MODE_SILENT ? "静音"
                    : mode == AudioManager.RINGER_MODE_VIBRATE ? "振动" : "响铃";
            tvMode.setText("当前铃声模式：" + modeText);

            if (mode != AudioManager.RINGER_MODE_NORMAL && !held && !w) {
                tvModeHint.setText("现在没有任何监听在跑，静音来电不会响");
                tvModeHint.setTextColor(getColor(R.color.bad));
            } else {
                tvModeHint.setText("静音 / 振动时用闹钟通道播放，响完自动恢复原状");
                tvModeHint.setTextColor(getColor(R.color.sub));
            }

            swSilentOnly.setChecked(Prefs.onlyWhenSilent(this));
            int vol = Prefs.volumePercent(this);
            sbVolume.setProgress(vol);
            tvVolume.setText(vol + "%");
            swFlip.setChecked(Prefs.flipRinger(this));

            Uri u = Prefs.ringtone(this);
            tvRingtone.setText("铃声：" + (u == null ? "系统默认" : ringtoneTitle(u)));

            renderEntries(Prefs.entries(this));
        } finally {
            binding = false;
        }
    }

    private void renderEntries(List<String[]> entries) {
        listEntries.removeAllViews();
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (final String[] e : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(this);
            name.setText(e[0] == null || e[0].isEmpty() ? "未命名号码" : e[0]);
            name.setTextSize(15);
            name.setTextColor(getColor(R.color.text));

            TextView num = new TextView(this);
            num.setText(Numbers.pretty(e[1]));
            num.setTextSize(12);
            num.setTextColor(getColor(R.color.sub));

            col.addView(name);
            col.addView(num);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText("移除");
            del.setTextSize(13);
            del.setTextColor(getColor(R.color.bad));
            del.setPadding(dp(14), dp(8), dp(2), dp(8));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.removeEntry(MainActivity.this, e[1]);
                    refresh();
                }
            });
            row.addView(del);

            listEntries.addView(row);
        }
    }

    private void requestScreeningRole() {
        try {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    refresh();
                    return;
                }
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
        } catch (Throwable t) {
            toast("这台机型没有开放「来电筛选」，请打开后台监听");
        }
    }

    private boolean isScreeningHeld() {
        try {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            return rm != null
                    && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                    && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        } catch (Throwable t) {
            return false;
        }
    }

    private void startWatch() {
        Prefs.setWatch(this, true);
        WatchService.start(this);
        toast("后台监听已开启，建议再做一次「后台保活设置」");
        refresh();
    }

    private void openContactPicker() {
        try {
            Intent i = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(i, REQ_PICK_CONTACT);
        } catch (Throwable t) {
            toast("没有可用的通讯录选择器，请手动输入号码");
        }
    }

    private void showManualDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(22);
        box.setPadding(p, dp(6), p, 0);

        final EditText name = new EditText(this);
        name.setHint("名字（可留空）");
        final EditText number = new EditText(this);
        number.setHint("电话号码");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        box.addView(name);
        box.addView(number);

        new AlertDialog.Builder(this)
                .setTitle("手动添加号码")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String n = number.getText().toString().trim();
                        if (Numbers.normalize(n).length() < 5) {
                            toast("号码太短了");
                            return;
                        }
                        Prefs.addEntry(MainActivity.this, name.getText().toString().trim(), n);
                        refresh();
                    }
                })
                .show();
    }

    private void pickRingtone() {
        try {
            Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            Uri cur = Prefs.ringtone(this);
            if (cur != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur);
            startActivityForResult(i, REQ_RINGTONE);
        } catch (Throwable t) {
            toast("这台机型没有铃声选择器");
        }
    }

    private void ensurePolicyAccess() {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) return;
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            toast("打开本 App 的「勿扰访问权限」后，切换铃声模式才有效");
        } catch (Throwable t) {
            toast("打不开勿扰权限设置，请手动到系统设置里授予");
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                toast("请到系统设置里把本 App 的省电策略设为无限制");
            }
        }
    }

    private String ringtoneTitle(Uri u) {
        try {
            android.media.Ringtone r = RingtoneManager.getRingtone(this, u);
            if (r != null) {
                String t = r.getTitle(this);
                if (t != null && !t.isEmpty()) return t;
            }
        } catch (Throwable ignored) {
        }
        return "已选择";
    }

    private boolean hasAll(String[] perms) {
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_PERM_CONTACTS) {
            if (granted) openContactPicker();
            else toast("没有通讯录权限，可以手动输入号码");
        } else if (requestCode == REQ_PERM_WATCH) {
            if (granted && hasAll(new String[]{Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG})) {
                startWatch();
            } else {
                toast("后台监听需要电话和通话记录权限");
                refresh();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            refresh();
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_PERM_WATCH) refresh();
            return;
        }
        if (requestCode == REQ_PICK_CONTACT) {
            Uri uri = data.getData();
            if (uri == null) return;
            Cursor c = null;
            try {
                c = getContentResolver().query(uri,
                        new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                if (c != null && c.moveToFirst()) {
                    String n = c.getString(0);
                    String num = c.getString(1);
                    if (num != null && Numbers.normalize(num).length() >= 5) {
                        Prefs.addEntry(this, n, num);
                        refresh();
                        toast("已添加");
                    } else {
                        toast("这个联系人没有号码");
                    }
                }
            } catch (Throwable t) {
                toast("读取联系人失败，请手动输入");
            } finally {
                if (c != null) c.close();
            }
        } else if (requestCode == REQ_RINGTONE) {
            Uri picked = null;
            try {
                picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            } catch (Throwable ignored) {
            }
            Prefs.setRingtone(this, picked);
            refresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (RingEngine.isRinging()) {
            RingEngine.stop(this);
        }
        super.onBackPressed();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
