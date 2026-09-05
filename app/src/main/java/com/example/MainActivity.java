package com.example.toastoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private EditText etWidth, etHeight, etColor;
    private SeekBar sbTransparency;
    private TextView tvTransparencyValue;
    private Button btnToggleService;

    private SharedPreferences prefs;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("OverlayPrefs", MODE_PRIVATE);

        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        etColor = findViewById(R.id.et_color);
        sbTransparency = findViewById(R.id.sb_transparency);
        tvTransparencyValue = findViewById(R.id.tv_transparency_value);
        btnToggleService = findViewById(R.id.btn_toggle_service);

        // 加载保存的设置
        loadSettings();

        // 透明度 SeekBar 监听
        sbTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTransparencyValue.setText(progress + "%");
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 其他输入框失去焦点时保存
        View.OnFocusChangeListener saveListener = (v, hasFocus) -> {
            if (!hasFocus) saveSettings();
        };
        etWidth.setOnFocusChangeListener(saveListener);
        etHeight.setOnFocusChangeListener(saveListener);
        etColor.setOnFocusChangeListener(saveListener);

        // 按钮点击：启动/停止服务
        btnToggleService.setOnClickListener(v -> {
            if (hasOverlayPermission()) {
                toggleService();
            } else {
                requestOverlayPermission();
            }
        });

        // 检查服务是否在运行
        isServiceRunning = OverlayService.isRunning;
        updateButtonText();
    }

    private void loadSettings() {
        int defaultWidth = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int defaultHeight = (int) (getResources().getDisplayMetrics().density * 200); // 约4行文字高度
        int defaultColor = Color.argb(255, 0, 0, 0); // 黑色背景
        int defaultTransparency = 70; // 0-100

        int width = prefs.getInt("width", defaultWidth);
        int height = prefs.getInt("height", defaultHeight);
        int color = prefs.getInt("color", defaultColor);
        int transparency = prefs.getInt("transparency", defaultTransparency);

        etWidth.setText(String.valueOf(width));
        etHeight.setText(String.valueOf(height));
        etColor.setText(String.format("#%08X", color));
        sbTransparency.setProgress(transparency);
        tvTransparencyValue.setText(transparency + "%");
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        try {
            int width = Integer.parseInt(etWidth.getText().toString());
            editor.putInt("width", width);
        } catch (NumberFormatException ignored) {}

        try {
            int height = Integer.parseInt(etHeight.getText().toString());
            editor.putInt("height", height);
        } catch (NumberFormatException ignored) {}

        String colorStr = etColor.getText().toString();
        if (!TextUtils.isEmpty(colorStr)) {
            try {
                int color = Color.parseColor(colorStr);
                editor.putInt("color", color);
            } catch (IllegalArgumentException ignored) {
                Toast.makeText(this, "颜色格式错误，使用 #RRGGBB 或 #AARRGGBB", Toast.LENGTH_SHORT).show();
            }
        }

        int transparency = sbTransparency.getProgress();
        editor.putInt("transparency", transparency);

        editor.apply();

        // 如果服务在运行，通知它更新设置
        if (isServiceRunning) {
            Intent intent = new Intent(OverlayService.ACTION_UPDATE_SETTINGS);
            sendBroadcast(intent);
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
                toggleService();
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能运行", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toggleService() {
        if (isServiceRunning) {
            stopService(new Intent(this, OverlayService.class));
            isServiceRunning = false;
        } else {
            startService(new Intent(this, OverlayService.class));
            isServiceRunning = true;
            // 保存当前设置到服务（服务启动时会自己读取，但为了立即生效，发送更新广播）
            saveSettings();
        }
        updateButtonText();
    }

    private void updateButtonText() {
        if (isServiceRunning) {
            btnToggleService.setText("停止服务");
        } else {
            btnToggleService.setText("启动服务");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新检查服务状态（可能在设置页面被系统杀死）
        isServiceRunning = OverlayService.isRunning;
        updateButtonText();
        loadSettings(); // 重新加载可能被外部修改？
    }
}