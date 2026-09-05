package com.example.toastoverlay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_SHOW_TEXT = "com.example.toastoverlay.SHOW_TEXT";
    public static final String ACTION_UPDATE_SETTINGS = "com.example.toastoverlay.UPDATE_SETTINGS";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TEXT_FLOAT = "text_float";

    public static boolean isRunning = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout overlayView;
    private TextView textView;

    private SharedPreferences prefs;
    private int width, height, color, transparency, textSizeSp;
    private float alpha;
    private boolean fixChinese;

    private BroadcastReceiver textReceiver;
    private BroadcastReceiver updateReceiver;

    private StringBuilder textBuilder = new StringBuilder();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("OverlayPrefs", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        loadSettings();
        createOverlay();
        registerReceivers();

        isRunning = true;
    }

    private void loadSettings() {
        int defaultWidth = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int defaultHeight = (int) (getResources().getDisplayMetrics().density * 200);
        int defaultColor = Color.argb(255, 0, 0, 0);
        int defaultTransparency = 70;
        int defaultTextSize = 7;
        boolean defaultFixChinese = false;

        width = prefs.getInt("width", defaultWidth);
        height = prefs.getInt("height", defaultHeight);
        color = prefs.getInt("color", defaultColor);
        transparency = prefs.getInt("transparency", defaultTransparency);
        textSizeSp = prefs.getInt("text_size", defaultTextSize);
        fixChinese = prefs.getBoolean("fix_chinese", defaultFixChinese);
        alpha = transparency / 100f;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = (LinearLayout) View.inflate(this, R.layout.overlay_layout, null);
        textView = overlayView.findViewById(R.id.tv_overlay_text);

        overlayView.setBackgroundColor(color);
        overlayView.setAlpha(alpha);
        textView.setTextSize(textSizeSp); // 应用字号

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams = new WindowManager.LayoutParams(
                width,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 0;
        layoutParams.y = 0;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, layoutParams);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, layoutParams);
    }

    private void registerReceivers() {
        textReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_SHOW_TEXT.equals(intent.getAction())) {
                    String text = null;
                    if (intent.hasExtra(EXTRA_TEXT)) {
                        text = intent.getStringExtra(EXTRA_TEXT);
                    } else if (intent.hasExtra(EXTRA_TEXT_FLOAT)) {
                        float f = intent.getFloatExtra(EXTRA_TEXT_FLOAT, 0f);
                        text = String.valueOf(f);
                    }
                    if (text != null) {
                        appendText(text);
                    }
                }
            }
        };
        IntentFilter textFilter = new IntentFilter(ACTION_SHOW_TEXT);
        registerReceiver(textReceiver, textFilter);

        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_SETTINGS.equals(intent.getAction())) {
                    loadSettings();
                    updateOverlay();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_SETTINGS));
    }

    private void appendText(String rawText) {
        String text = rawText;
        // 如果启用了中文修复，尝试将 ISO-8859-1 字节转为 UTF-8
        if (fixChinese) {
            try {
                byte[] bytes = rawText.getBytes(StandardCharsets.ISO_8859_1);
                text = new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 转换失败则保留原样
            }
        }

        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = timeStamp + " " + text;

        if (textBuilder.length() > 0) {
            textBuilder.append("\n");
        }
        textBuilder.append(line);

        String[] lines = textBuilder.toString().split("\n");
        int maxLines = 4;
        if (lines.length > maxLines) {
            List<String> lastLines = Arrays.asList(lines).subList(lines.length - maxLines, lines.length);
            textBuilder = new StringBuilder(TextUtils.join("\n", lastLines));
        }

        textView.setText(textBuilder.toString());
    }

    private void updateOverlay() {
        if (overlayView != null && windowManager != null) {
            // 更新尺寸
            layoutParams.width = width;
            layoutParams.height = height;
            // 更新背景和透明度
            overlayView.setBackgroundColor(color);
            overlayView.setAlpha(alpha);
            // 更新字号
            textView.setTextSize(textSizeSp);
            // 应用新布局
            windowManager.updateViewLayout(overlayView, layoutParams);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        loadSettings();
        updateOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (overlayView == null) {
            createOverlay();
            registerReceivers();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        if (textReceiver != null) {
            unregisterReceiver(textReceiver);
            textReceiver = null;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
