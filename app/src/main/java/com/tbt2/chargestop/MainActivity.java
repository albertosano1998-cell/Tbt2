package com.tbt2.chargestop;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView batteryText, statusText, shizukuText, scanText, limitText;
    private int limit = 85;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshBattery();
            handler.postDelayed(this, 10000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("charge_stop", MODE_PRIVATE);
        limit = Math.max(75, Math.min(95, prefs.getInt("limit", 85)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF4F6FA);
        scroll.addView(root);

        root.addView(text("Charge Stop 85", 28, true));
        root.addView(text("Charging limit diagnostics • no root", 14, false));
        batteryText = text("Battery: reading…", 24, true);
        root.addView(batteryText);
        statusText = text("Charging state: checking…", 16, true);
        root.addView(statusText);

        root.addView(text("Target limit (%)", 16, true));
        SeekBar seek = new SeekBar(this);
        seek.setMax(20);
        seek.setProgress(limit - 75);
        root.addView(seek);
        limitText = text(limit + "%", 20, true);
        root.addView(limitText);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                limit = 75 + p;
                limitText.setText(limit + "%");
                if (user) prefs.edit().putInt("limit", limit).apply();
                refreshBattery();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        shizukuText = text("Shizuku: not checked yet", 15, false);
        root.addView(shizukuText);
        Button grant = new Button(this);
        grant.setText("Connect / grant Shizuku");
        grant.setOnClickListener(v -> checkShizuku(true));
        root.addView(grant);

        Button scan = new Button(this);
        scan.setText("Scan for charging-control interfaces");
        scan.setOnClickListener(v -> scanControls());
        root.addView(scan);

        scanText = text("Diagnostic: not scanned yet. Scan is read-only and does not change charging.", 14, false);
        root.addView(scanText);
        TextView note = text("Important: this app does not yet stop charging automatically. Android has no public app API for cutting battery charging. Device-specific control must be identified and safely tested before automatic cutoff can be implemented.", 14, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = 18;
        root.addView(note, np);

        setContentView(scroll);
        refreshBattery();
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xFF172033);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, 7, 0, 7);
        return t;
    }

    private void refreshBattery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null || batteryText == null) return;
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = scale > 0 && level >= 0 ? level * 100 / scale : level;
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            batteryText.setText("Battery: " + (pct >= 0 ? pct + "%" : "unknown"));
            statusText.setText(plugged == 0 ? "Charger: disconnected" :
                (pct >= limit ? "Target reached — charging may still continue" : "Charger: connected"));
        } catch (Throwable e) {
            if (statusText != null) statusText.setText("Battery status unavailable");
        }
    }

    private void checkShizuku(boolean showToast) {
        try {
            boolean running = Shizuku.pingBinder();
            if (!running) {
                shizukuText.setText("Shizuku: not running");
                if (showToast) Toast.makeText(this, "Open Shizuku and start its service first.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001);
                shizukuText.setText("Shizuku: permission requested; approve it if prompted");
            } else {
                shizukuText.setText("Shizuku: connected; permission granted");
                if (showToast) Toast.makeText(this, "Shizuku permission is already granted.", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            if (shizukuText != null) shizukuText.setText("Shizuku unavailable: " + e.getClass().getSimpleName());
            if (showToast) Toast.makeText(this, "Shizuku error. Ensure it is installed and running.", Toast.LENGTH_LONG).show();
        }
    }

    private void scanControls() {
        try {
            if (!Shizuku.pingBinder()) {
                scanText.setText("Shizuku is not running. Start it, then try again.");
                return;
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001);
                scanText.setText("Grant Shizuku permission, then tap Scan again.");
                return;
            }
            scanText.setText("Shizuku permission is available. Actual charging-control scanning is not implemented in this build; no device settings have been changed.");
        } catch (Throwable e) {
            scanText.setText("Shizuku error: " + e.getClass().getSimpleName() + ". Check that Shizuku is installed and running.");
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
        super.onDestroy();
    }
}