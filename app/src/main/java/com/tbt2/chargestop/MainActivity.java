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
import android.widget.SeekBar;
import android.widget.ScrollView;

public class MainActivity extends Activity {
    private TextView batteryText, statusText, limitText;
    private int limit = 85;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("charge_stop", MODE_PRIVATE);
        limit = Math.max(75, Math.min(95, prefs.getInt("limit", 85)));
        try {
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(32, 32, 32, 32);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            scroll.addView(root);

            TextView title = makeText("Charge Stop 85", 28, true);
            root.addView(title);
            root.addView(makeText("Battery monitor • Android 11+", 15, false));
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
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    limit = 75 + progress;
                    limitText.setText(limit + "%");
                    if (fromUser) prefs.edit().putInt("limit", limit).apply();
                    refreshBattery();
                }
                public void onStartTrackingTouch(SeekBar bar) {}
                public void onStopTrackingTouch(SeekBar bar) {}
            });
            root.addView(makeText("Important: this diagnostic build only monitors battery level. It does not stop charging automatically. Automatic cutoff requires a device-specific charging control.", 15, false));
            setContentView(scroll);
            refreshBattery();
        } catch (Throwable error) {
            TextView fallback = new TextView(this);
            fallback.setText("Charge Stop 85 could not build its screen: " + error.getClass().getSimpleName());
            setContentView(fallback);
        }
    }

    private TextView makeText(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, 10, 0, 10);
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
}