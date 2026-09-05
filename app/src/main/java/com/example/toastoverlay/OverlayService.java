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
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_SHOW_TEXT = "com.example.toastoverlay.SHOW_TEXT";
    public static final String ACTION_UPDATE_SETTINGS = "com.example.toastoverlay.UPDATE_SETTINGS";
    public static final String EXTRA_TEXT = "text";

    public static boolean isRunning = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout overlayView;
    private TextView textView;

    private SharedPreferences prefs;
    private int width, height, color, transparency;
    private float alpha;

    private BroadcastReceiver textReceiver;
    private BroadcastReceiver updateReceiver;

    private StringBuilder textBuilder = new StringBuilder(); // 用于存储当前显示的所有行（用\n分隔）

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("OverlayPrefs", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // 加载设置
        loadSettings();

        // 创建悬浮窗
        createOverlay();

        // 注册广播接收器
        registerReceivers();

        isRunning = true;
    }

    private void loadSettings() {
        int defaultWidth = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int defaultHeight = (int) (getResources().getDisplayMetrics().density * 200);
        int defaultColor = Color.argb(255, 0, 0, 0);
        int defaultTransparency = 70;

        width = prefs.getInt("width", defaultWidth);
        height = prefs.getInt("height", defaultHeight);
        color = prefs.getInt("color", defaultColor);
        transparency = prefs.getInt("transparency", defaultTransparency);
        alpha = transparency / 100f;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 悬浮窗布局
        overlayView = (LinearLayout) View.inflate(this, R.layout.overlay_layout, null);
        textView = overlayView.findViewById(R.id.tv_overlay_text);

        // 设置背景色和透明度
        overlayView.setBackgroundColor(color);
        overlayView.setAlpha(alpha);

        // WindowManager 参数
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

        // 使悬浮窗可拖动
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

        // 添加到窗口
        windowManager.addView(overlayView, layoutParams);
    }

    private void registerReceivers() {
        // 接收显示文本的广播（全局）
        textReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_SHOW_TEXT.equals(intent.getAction())) {
                    String text = intent.getStringExtra(EXTRA_TEXT);
                    if (text != null) {
                        appendText(text);
                    }
                }
            }
        };
        IntentFilter textFilter = new IntentFilter(ACTION_SHOW_TEXT);
        registerReceiver(textReceiver, textFilter);

        // 接收更新设置的广播（本地）
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_SETTINGS.equals(intent.getAction())) {
                    // 重新加载设置并更新UI
                    loadSettings();
                    updateOverlay();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_SETTINGS));
    }

    private void appendText(String newText) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = timeStamp + " " + newText;

        // 追加到 builder
        if (textBuilder.length() > 0) {
            textBuilder.append("\n");
        }
        textBuilder.append(line);

        // 按行拆分，保留最后4行
        String[] lines = textBuilder.toString().split("\n");
        int maxLines = 4;
        if (lines.length > maxLines) {
            // 只保留最后 maxLines 行
            List<String> lastLines = Arrays.asList(lines).subList(lines.length - maxLines, lines.length);
            textBuilder = new StringBuilder(TextUtils.join("\n", lastLines));
        }

        // 更新 TextView
        textView.setText(textBuilder.toString());
    }

    private void updateOverlay() {
        if (overlayView != null && windowManager != null) {
            // 更新尺寸
            layoutParams.width = width;
            layoutParams.height = height;
            // 更新背景颜色和透明度
            overlayView.setBackgroundColor(color);
            overlayView.setAlpha(alpha);
            windowManager.updateViewLayout(overlayView, layoutParams);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // 当设置发生变化时，重新加载并更新
        loadSettings();
        updateOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 如果服务被系统杀死后重启，重新创建悬浮窗
        if (overlayView == null) {
            createOverlay();
            registerReceivers();
            // 恢复文本？可以从 SharedPreferences 恢复？但我们不保存文本，所以丢失。
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        // 移除悬浮窗
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        // 注销广播
        if (textReceiver != null) {
            unregisterReceiver(textReceiver);
            textReceiver = null;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
        // 取消监听 SharedPreferences
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}