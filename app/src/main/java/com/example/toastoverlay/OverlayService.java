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
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ACTION_SHOW_TEXT = "com.example.toastoverlay.SHOW_TEXT";
    public static final String ACTION_UPDATE_SETTINGS = "com.example.toastoverlay.UPDATE_SETTINGS";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TEXT_FLOAT = "text_float";

    public static boolean isRunning = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout overlayView;
    private ScrollView scrollView;
    private TextView textView;

    private SharedPreferences prefs;
    private int width, height, color, transparency, textSizeSp, decodeMode;
    private float alpha;
    private int posX, posY; // 坐标变量

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
        int defaultDecodeMode = 0;
        int defaultPosX = 0;
        int defaultPosY = 0;

        width = prefs.getInt("width", defaultWidth);
        height = prefs.getInt("height", defaultHeight);
        color = prefs.getInt("color", defaultColor);
        transparency = prefs.getInt("transparency", defaultTransparency);
        textSizeSp = prefs.getInt("text_size", defaultTextSize);
        decodeMode = prefs.getInt("decode_mode", defaultDecodeMode);
        posX = prefs.getInt("posX", defaultPosX);
        posY = prefs.getInt("posY", defaultPosY);
        alpha = transparency / 100f;
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = (LinearLayout) View.inflate(this, R.layout.overlay_layout, null);
        scrollView = overlayView.findViewById(R.id.scrollView);
        textView = overlayView.findViewById(R.id.tv_overlay_text);

        overlayView.setBackgroundColor(color);
        overlayView.setAlpha(alpha);
        textView.setTextSize(textSizeSp);

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
        layoutParams.x = posX;   // 恢复保存的X坐标
        layoutParams.y = posY;   // 恢复保存的Y坐标

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
                    case MotionEvent.ACTION_UP:
                        // 拖动结束后保存坐标
                        savePosition(layoutParams.x, layoutParams.y);
                        break;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, layoutParams);
    }

    // 保存坐标到 SharedPreferences
    private void savePosition(int x, int y) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("posX", x);
        editor.putInt("posY", y);
        editor.apply();
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
        String decodedText = rawText;
        try {
            switch (decodeMode) {
                case 1:
                    decodedText = new String(rawText.getBytes(StandardCharsets.ISO_8859_1), "GBK");
                    break;
                case 2:
                    decodedText = new String(rawText.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    break;
                case 3:
                    byte[] bytes = Base64.decode(rawText, Base64.DEFAULT);
                    decodedText = new String(bytes, StandardCharsets.UTF_8);
                    break;
                case 4:
                    decodedText = unescapeUnicode(rawText);
                    break;
                default:
                    decodedText = rawText;
                    break;
            }
        } catch (Exception e) {
            decodedText = rawText;
        }

        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = timeStamp + " " + decodedText;

        if (textBuilder.length() > 0) {
            textBuilder.append("\n");
        }
        textBuilder.append(line);

        textView.setText(textBuilder.toString());
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String unescapeUnicode(String input) {
        if (!input.contains("\\u")) return input;
        try {
            Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            Matcher matcher = pattern.matcher(input);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int code = Integer.parseInt(matcher.group(1), 16);
                matcher.appendReplacement(sb, new String(Character.toChars(code)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private void updateOverlay() {
        if (overlayView != null && windowManager != null) {
            layoutParams.width = width;
            layoutParams.height = height;
            overlayView.setBackgroundColor(color);
            overlayView.setAlpha(alpha);
            textView.setTextSize(textSizeSp);
            // 注意：更新设置时不要重置坐标，只修改尺寸/颜色等
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
