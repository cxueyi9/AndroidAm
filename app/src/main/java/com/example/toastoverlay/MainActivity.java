package com.example.toastoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private EditText etWidth, etHeight, etColor, etTextSize;
    private Spinner spinnerDecode;
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
        etTextSize = findViewById(R.id.et_text_size);
        spinnerDecode = findViewById(R.id.spinner_decode);
        sbTransparency = findViewById(R.id.sb_transparency);
        tvTransparencyValue = findViewById(R.id.tv_transparency_value);
        btnToggleService = findViewById(R.id.btn_toggle_service);

        // 填充下拉选项（从strings加载）
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.decode_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDecode.setAdapter(adapter);

        loadSettings();

        sbTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTransparencyValue.setText(progress + "%");
                if (fromUser) saveSettings();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        View.OnFocusChangeListener saveListener = (v, hasFocus) -> {
            if (!hasFocus) saveSettings();
        };
        etWidth.setOnFocusChangeListener(saveListener);
        etHeight.setOnFocusChangeListener(saveListener);
        etColor.setOnFocusChangeListener(saveListener);
        etTextSize.setOnFocusChangeListener(saveListener);
        // Spinner 选择变化时保存
        spinnerDecode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveSettings();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnToggleService.setOnClickListener(v -> {
            if (hasOverlayPermission()) {
                toggleService();
            } else {
                requestOverlayPermission();
            }
        });

        // 自动启动服务
        if (hasOverlayPermission()) {
            if (!OverlayService.isRunning) {
                startService(new Intent(this, OverlayService.class));
            }
        }
        isServiceRunning = OverlayService.isRunning;
        updateButtonText();
    }

    private void loadSettings() {
        int defaultWidth = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int defaultHeight = (int) (getResources().getDisplayMetrics().density * 200);
        int defaultColor = Color.argb(255, 0, 0, 0);
        int defaultTransparency = 70;
        int defaultTextSize = 7;
        int defaultDecodeMode = 0; // 0=无, 1=GBK, 2=UTF-8, 3=Base64

        int width = prefs.getInt("width", defaultWidth);
        int height = prefs.getInt("height", defaultHeight);
        int color = prefs.getInt("color", defaultColor);
        int transparency = prefs.getInt("transparency", defaultTransparency);
        int textSize = prefs.getInt("text_size", defaultTextSize);
        int decodeMode = prefs.getInt("decode_mode", defaultDecodeMode);

        etWidth.setText(String.valueOf(width));
        etHeight.setText(String.valueOf(height));
        etColor.setText(String.format("#%08X", color));
        sbTransparency.setProgress(transparency);
        tvTransparencyValue.setText(transparency + "%");
        etTextSize.setText(String.valueOf(textSize));
        spinnerDecode.setSelection(decodeMode);
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
                Toast.makeText(this, "颜色格式错误", Toast.LENGTH_SHORT).show();
            }
        }

        int transparency = sbTransparency.getProgress();
        editor.putInt("transparency", transparency);

        try {
            int textSize = Integer.parseInt(etTextSize.getText().toString());
            editor.putInt("text_size", textSize);
        } catch (NumberFormatException ignored) {}

        int decodeMode = spinnerDecode.getSelectedItemPosition();
        editor.putInt("decode_mode", decodeMode);

        editor.apply();

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
            saveSettings();
        }
        updateButtonText();
    }

    private void updateButtonText() {
        btnToggleService.setText(isServiceRunning ? "停止服务" : "启动服务");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isServiceRunning = OverlayService.isRunning;
        updateButtonText();
        loadSettings();
    }
}
