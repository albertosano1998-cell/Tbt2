package com.tbt2.chargestop;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView batteryText, statusText, shizukuText;
    private int limit = 85;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) ->
        runOnUiThread(() -> updateShizukuStatus());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF4F6FA);
        TextView title = text("Charge Stop 85", 28, true);
        root.addView(title);
        root.addView(text("Battery limit helper • Shizuku, no root", 14, false));
        batteryText = text("Battery: reading…", 24, true);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2); gap.topMargin = 36;
        root.addView(batteryText, gap);
        statusText = text("Charging control: not verified", 16, true);
        root.addView(statusText);
        root.addView(text("Cutoff threshold (%)", 16, true));
        SeekBar seek = new SeekBar(this); seek.setMax(15); seek.setProgress(10);
        root.addView(seek);
        TextView limitText = text("85%", 20, true); root.addView(limitText);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) { limit = 75 + p; limitText.setText(limit + "%"); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        shizukuText = text("Checking Shizuku…", 15, false); root.addView(shizukuText);
        Button grant = new Button(this); grant.setText("Grant Shizuku permission");
        grant.setOnClickListener(v -> {
            if (!Shizuku.pingBinder()) Toast.makeText(this, "Start Shizuku first.", Toast.LENGTH_LONG).show();
            else if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1001);
            else Toast.makeText(this, "Shizuku permission already granted.", Toast.LENGTH_SHORT).show();
            updateShizukuStatus();
        });
        root.addView(grant);
        TextView note = text("Important: Android cannot stop charging through a normal app. This prototype monitors battery level and checks Shizuku availability, but does NOT claim to disconnect charging until a compatible Infinix charging-control interface is identified and tested.", 14, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2); noteParams.topMargin = 24; root.addView(note, noteParams);
        setContentView(root);
        refreshBattery();
        updateShizukuStatus();
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(0xFF172033);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, 8, 0, 8); return t;
    }
    private void refreshBattery() {
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : level;
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        batteryText.setText("Battery: " + pct + "%");
        statusText.setText(plugged == 0 ? "Charger: disconnected" : (pct >= limit ? "Threshold reached — cutoff not yet supported" : "Charger: connected"));
    }
    private void updateShizukuStatus() {
        if (shizukuText == null) return;
        boolean running = Shizuku.pingBinder();
        shizukuText.setText(!running ? "Shizuku: not running" :
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED ? "Shizuku: connected and permission granted" : "Shizuku: running; permission not granted");
        refreshBattery();
    }
    @Override protected void onResume() { super.onResume(); updateShizukuStatus(); }
    @Override protected void onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy(); }
}
