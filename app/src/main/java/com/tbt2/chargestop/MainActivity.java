package com.tbt2.chargestop;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import rikka.shizuku.Shizuku;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private TextView batteryText, statusText, limitText, shizukuText, scanText;
    private int limit = 85;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshBattery();
            updateShizukuStatus();
            handler.postDelayed(this, 10000);
        }
    };
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> runOnUiThread(this::updateShizukuStatus);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("charge_stop", MODE_PRIVATE);
        limit = Math.max(75, Math.min(95, prefs.getInt("limit", 85)));
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(32, 28, 32, 32);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            scroll.addView(root);

            root.addView(makeText("Charge Stop 85", 28, true));
            root.addView(makeText("Shizuku charging-control diagnostic", 15, false));
            batteryText = makeText("Battery: reading…", 24, true);
            root.addView(batteryText);
            statusText = makeText("Checking charging state…", 17, true);
            root.addView(statusText);
            root.addView(makeText("Target limit (%)", 17, true));

            SeekBar seek = new SeekBar(this);
            seek.setMax(20);
            seek.setProgress(limit - 75);
            root.addView(seek);
            limitText = makeText(limit + "%", 22, true);
            root.addView(limitText);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    limit = 75 + progress;
                    limitText.setText(limit + "%");
                    if (fromUser) prefs.edit().putInt("limit", limit).apply();
                    refreshBattery();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });

            shizukuText = makeText("Shizuku: checking…", 16, true);
            root.addView(shizukuText);

            Button connect = new Button(this);
            connect.setText("Connect / grant Shizuku");
            connect.setOnClickListener(v -> requestShizuku());
            root.addView(connect);

            Button scan = new Button(this);
            scan.setText("Scan charging-control interfaces");
            scan.setOnClickListener(v -> scanControls());
            root.addView(scan);

            scanText = makeText("Not scanned yet. The scan is read-only; it will not change charging.", 14, false);
            root.addView(scanText);
            Button copy = new Button(this);
            copy.setText("COPY SCAN RESULTS");
            copy.setOnClickListener(v -> {
                String result = scanText == null ? "" : scanText.getText().toString();
                if (result.trim().isEmpty() || result.startsWith("Not scanned yet")) {
                    Toast.makeText(this, "Run the scan first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Charge Stop scan results", result));
                    Toast.makeText(this, "Scan results copied. Paste them into ChatGPT.", Toast.LENGTH_LONG).show();
                }
            });
            root.addView(copy);
            root.addView(makeText(
                "Important: this version does not yet cut off charging. It checks whether Shizuku can see likely device-specific control files. A writable file is not enough by itself—we must confirm its meaning before safely using it to stop and resume charging.",
                14, false));
            setContentView(scroll);
            refreshBattery();
            updateShizukuStatus();
        } catch (Throwable error) {
            TextView fallback = new TextView(this);
            fallback.setText("Charge Stop 85 startup error: " + error.getClass().getSimpleName());
            setContentView(fallback);
        }
    }

    private TextView makeText(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, 8, 0, 8);
        return view;
    }

    private void refreshBattery() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null || batteryText == null) return;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int percent = scale > 0 && level >= 0 ? level * 100 / scale : -1;
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            batteryText.setText("Battery: " + (percent >= 0 ? percent + "%" : "unknown"));
            statusText.setText(plugged == 0 ? "Charger: disconnected" :
                (percent >= limit ? "Target reached — charging may continue" : "Charger: connected"));
        } catch (Throwable ignored) {
            if (statusText != null) statusText.setText("Battery status unavailable");
        }
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Start Shizuku first, then tap Connect again.", Toast.LENGTH_LONG).show();
                updateShizukuStatus();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(85);
            } else {
                Toast.makeText(this, "Shizuku permission is already granted.", Toast.LENGTH_SHORT).show();
            }
            updateShizukuStatus();
        } catch (Throwable error) {
            if (shizukuText != null) shizukuText.setText("Shizuku unavailable: " + error.getClass().getSimpleName());
        }
    }

    private void updateShizukuStatus() {
        if (shizukuText == null) return;
        try {
            if (!Shizuku.pingBinder()) {
                shizukuText.setText("Shizuku: not running");
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuText.setText("Shizuku: connected; permission granted");
            } else {
                shizukuText.setText("Shizuku: running; permission needed");
            }
        } catch (Throwable error) {
            shizukuText.setText("Shizuku status unavailable");
        }
    }

    private void scanControls() {
        try {
            if (!Shizuku.pingBinder()) {
                scanText.setText("Shizuku is not running. Start it, then tap Scan again.");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(85);
                scanText.setText("Grant the Shizuku permission prompt, then tap Scan again.");
                return;
            }
        } catch (Throwable error) {
            scanText.setText("Cannot connect to Shizuku: " + error.getClass().getSimpleName());
            return;
        }

        scanText.setText("Scanning read-only controls…");
        new Thread(() -> {
            String result = runReadOnlyScan();
            runOnUiThread(() -> {
                if (scanText != null) scanText.setText(result);
            });
        }, "charging-control-scan").start();
    }

    private String runReadOnlyScan() {
        String script =
            "echo 'Shell identity:'; id; " +
            "echo; echo 'Power supply directories and types:'; " +
            "for d in /sys/class/power_supply/* /sys/devices/platform/mt_charger/power_supply/* /sys/devices/platform/mtk_charger/power_supply/* /sys/devices/platform/mtk-charger/power_supply/*; do " +
            "if [ -d \"$d\" ]; then echo \"DIR: $d\"; for n in type status online present health capacity charge_type usb_type; do if [ -r \"$d/$n\" ]; then printf '  %s=' \"$n\"; head -c 100 \"$d/$n\" 2>/dev/null; echo; fi; done; fi; done; " +
            "echo; echo 'Likely charging controls (read-only inspection):'; " +
            "for base in /sys/class/power_supply /sys/devices/platform/mt_charger/power_supply /sys/devices/platform/mtk_charger/power_supply /sys/devices/platform/mtk-charger/power_supply; do " +
            "if [ -d \"$base\" ]; then find \"$base\" -maxdepth 5 -type f \\( -iname '*charg*' -o -iname '*suspend*' -o -iname '*enable*' -o -iname '*limit*' -o -iname '*current*' -o -iname '*disable*' \\) 2>/dev/null; fi; done | head -140; " +
            "echo; echo 'Candidate values and shell permissions:'; " +
            "for d in /sys/class/power_supply/* /sys/devices/platform/mt_charger/power_supply/* /sys/devices/platform/mtk_charger/power_supply/* /sys/devices/platform/mtk-charger/power_supply/*; do " +
            "if [ -d \"$d\" ]; then for n in charging_enabled charge_disable input_suspend charge_control_limit constant_charge_current_max enable_charger charge_enable charge_full_design; do p=\"$d/$n\"; if [ -e \"$p\" ]; then echo \"FOUND: $p\"; if [ -r \"$p\" ]; then printf '  value: '; head -c 100 \"$p\" 2>&1; echo; fi; if [ -w \"$p\" ]; then echo '  shell_writable: YES'; else echo '  shell_writable: NO'; fi; fi; done; fi; done; " +
            "echo; echo 'MediaTek charger-driver tree (read-only):'; for base in /sys/devices/platform/mt_charger /sys/devices/platform/mtk_charger /sys/devices/platform/mtk-charger /sys/class/power_supply; do if [ -d \"$base\" ]; then echo \"BASE: $base\"; find \"$base\" -maxdepth 6 -type f \\( -iname '*charg*' -o -iname '*enable*' -o -iname '*disable*' -o -iname '*suspend*' -o -iname '*limit*' -o -iname '*current*' -o -iname '*input*' -o -iname '*powerpath*' -o -iname '*shipmode*' \\) 2>/dev/null | head -100; fi; done; echo; echo 'Known vendor proc/debug interfaces (existence and readable first line only):'; for p in /proc/driver/charger /proc/driver/battery /proc/mtk_battery_cmd /proc/mtk_battery /proc/mtk_charger /proc/charging /proc/battery_cmd /proc/driver/mtk_battery; do if [ -e \"$p\" ]; then ls -ld \"$p\" 2>/dev/null; if [ -f \"$p\" ] && [ -r \"$p\" ]; then printf '  first_line: '; head -c 160 \"$p\" 2>/dev/null; echo; fi; fi; done; echo; echo 'No files were written.'";
        try {
            Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            Object process = method.invoke(null, new String[]{"sh", "-c", script}, null, null);
            Method getInputStream = process.getClass().getMethod("getInputStream");
            InputStream input = (InputStream) getInputStream.invoke(process);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && output.length() < 9000) {
                output.append(line).append('\n');
            }
            try { process.getClass().getMethod("waitFor").invoke(process); } catch (Throwable ignored) {}
            if (output.length() == 0) return "Scan returned no output. Check Shizuku and try again.";
            return output.toString();
        } catch (Throwable error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            return "Scan failed: " + cause.getClass().getSimpleName() + ": " +
                (cause.getMessage() == null ? "no details" : cause.getMessage()) +
                "\nNo charging settings were changed.";
        }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresher);
        try { Shizuku.removeRequestPermissionResultListener(permissionListener); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
