package com.example.myapplication;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.example.myapplication.controller.AudioController;
import com.example.myapplication.controller.BluetoothController;
import com.example.myapplication.controller.HeaderController;
import com.example.myapplication.controller.ParamsController;
import com.example.myapplication.controller.RGBController;
import com.example.myapplication.service.SystemAudioCaptureService;
import com.example.myapplication.tool.AudioAnalyzer;
import com.example.myapplication.tool.ParamsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AudioAnalyzer.OnAudioDataListener, BluetoothController.OnBluetoothStatusListener {

    private static final int REQUEST_PERMISSION_CODE = 100;
    private static final int REQUEST_MEDIA_PROJECTION = 101;
    private static final int REQUEST_BLUETOOTH_ENABLE = 102;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 103;

    // 页面索引
    private static final int PAGE_HOME = 0;
    private static final int PAGE_SOURCE = 1;
    private static final int PAGE_PARAMS = 2;

    private AudioAnalyzer audioAnalyzer;
    private ParamsManager paramsManager;
    private MediaProjectionManager mediaProjectionManager;
    private SystemAudioCaptureService audioCaptureService;
    private boolean isServiceBound = false;

    private HeaderController headerController;
    private AudioController audioController;
    private RGBController rgbController;
    private ParamsController paramsController;
    private BluetoothController bluetoothController;
    private TextView tvSensor;

    // 页面导航
    private View pageHome, pageSource, pageParams;
    private View topBar, bottomNav;
    private Button btnTabHome, btnTabSource, btnTabParams;
    private Button btnFullscreen;
    private int currentPage = PAGE_HOME;
    private boolean isFullscreen = false;
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        System.out.println(">>> onCreate");
        try {
            setContentView(R.layout.main_page);
            System.out.println(">>> setContentView OK");

            initManagers();
            System.out.println(">>> initManagers OK");

            initControllers();
            System.out.println(">>> initControllers OK");

            initNavigation();
            System.out.println(">>> initNavigation OK");

            tvSensor = findViewById(R.id.tvSensor);

            checkPermissions();
            System.out.println(">>> checkPermissions OK");

            audioController.startProgressUpdates();
            // App 启动后主动连接蓝牙（未配对/未开启时内部会拉起权限或蓝牙开关流程）
            tryAutoConnectBluetooth();
            System.out.println(">>> onCreate completed");
        } catch (Exception e) {
            System.out.println(">>> onCreate ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initManagers() {
        paramsManager = new ParamsManager(this);
        audioAnalyzer = new AudioAnalyzer(this);
        audioAnalyzer.setOnAudioDataListener(this);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        bluetoothController = new BluetoothController(this, this);
    }

    private void initControllers() {
        headerController = new HeaderController(this);
        audioController = new AudioController(this, audioAnalyzer, paramsManager, headerController, bluetoothController);
        rgbController = new RGBController(this, audioAnalyzer, paramsManager, bluetoothController);
        paramsController = new ParamsController(this, paramsManager);
    }

    /**
     * 初始化页面导航：底部 Tab 切换、左右滑动切换、全屏模式。
     */
    private void initNavigation() {
        pageHome = findViewById(R.id.pageHome);
        pageSource = findViewById(R.id.pageSource);
        pageParams = findViewById(R.id.pageParams);
        topBar = findViewById(R.id.topBar);
        bottomNav = findViewById(R.id.bottomNav);

        btnTabHome = findViewById(R.id.btnTabHome);
        btnTabSource = findViewById(R.id.btnTabSource);
        btnTabParams = findViewById(R.id.btnTabParams);
        btnFullscreen = findViewById(R.id.btnFullscreen);

        if (btnTabHome != null) btnTabHome.setOnClickListener(v -> switchPage(PAGE_HOME));
        if (btnTabSource != null) btnTabSource.setOnClickListener(v -> switchPage(PAGE_SOURCE));
        if (btnTabParams != null) btnTabParams.setOnClickListener(v -> switchPage(PAGE_PARAMS));
        if (btnFullscreen != null) btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        // 左右滑动切换页面：在 dispatchTouchEvent 中统一检测，避免被 ScrollView 消费
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            private boolean touchOnSeekBar = false;

            @Override
            public boolean onDown(MotionEvent e) {
                // 记录触摸起点是否落在 SeekBar 上，避免拖动滑块时误触发翻页
                touchOnSeekBar = isTouchOnSeekBar(e);
                return super.onDown(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (touchOnSeekBar) return false; // 拖动滑块时不翻页
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                // 只处理水平方向为主的滑动
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // 向右滑 -> 上一页
                        switchPage(currentPage - 1);
                    } else {
                        // 向左滑 -> 下一页
                        switchPage(currentPage + 1);
                    }
                    return true;
                }
                return false;
            }
        });

        switchPage(PAGE_HOME);
    }

    /**
     * 判断触摸点是否落在某个 SeekBar 上（包括其子 View）。
     */
    private boolean isTouchOnSeekBar(MotionEvent e) {
        View root = getWindow().getDecorView();
        int[] location = new int[2];
        return findSeekBarAt(root, e.getRawX(), e.getRawY(), location);
    }

    private boolean findSeekBarAt(View view, float x, float y, int[] location) {
        if (view.getVisibility() != View.VISIBLE) return false;
        view.getLocationOnScreen(location);
        if (x < location[0] || x > location[0] + view.getWidth()
                || y < location[1] || y > location[1] + view.getHeight()) {
            return false;
        }
        if (view instanceof android.widget.SeekBar) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (findSeekBarAt(vg.getChildAt(i), x, y, location)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 先交给手势检测器识别水平滑动，再正常分发
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 切换到指定页面，并更新底部 Tab 高亮状态。
     */
    private void switchPage(int page) {
        if (page < PAGE_HOME || page > PAGE_PARAMS) return;
        currentPage = page;

        if (pageHome != null) pageHome.setVisibility(page == PAGE_HOME ? View.VISIBLE : View.GONE);
        if (pageSource != null) pageSource.setVisibility(page == PAGE_SOURCE ? View.VISIBLE : View.GONE);
        if (pageParams != null) pageParams.setVisibility(page == PAGE_PARAMS ? View.VISIBLE : View.GONE);

        updateTabStyle(btnTabHome, page == PAGE_HOME);
        updateTabStyle(btnTabSource, page == PAGE_SOURCE);
        updateTabStyle(btnTabParams, page == PAGE_PARAMS);
    }

    private void updateTabStyle(Button btn, boolean selected) {
        if (btn == null) return;
        if (selected) {
            btn.setBackgroundColor(android.graphics.Color.parseColor("#FF3b82f6"));
            btn.setTextColor(getResources().getColor(R.color.text_primary));
        } else {
            btn.setBackgroundColor(getResources().getColor(R.color.bg_disabled));
            btn.setTextColor(getResources().getColor(R.color.text_hint));
        }
    }

    /**
     * 全屏模式：隐藏顶部状态栏与底部导航，仅保留页面内容与全屏切换按钮。
     */
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (topBar != null) topBar.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        if (bottomNav != null) bottomNav.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        if (btnFullscreen != null) {
            btnFullscreen.setText(isFullscreen ? R.string.btn_exit_fullscreen : R.string.btn_fullscreen);
        }
    }

    private void checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!missingPermissions.isEmpty()) {
            requestPermissions(missingPermissions.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * 主动连接蓝牙：权限/蓝牙开关就绪后直接连接；
     * 未授权或蓝牙关闭时先拉起对应系统流程，结果回调里再续接连接。
     */
    private void tryAutoConnectBluetooth() {
        if (bluetoothController == null || !bluetoothController.isBluetoothAvailable()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
            // 启动时 checkPermissions() 已统一申请，授权结果回调里会再次续接
            return;
        }
        if (!bluetoothController.isBluetoothEnabled()) {
            bluetoothController.requestEnableBluetooth(REQUEST_BLUETOOTH_ENABLE);
            return;
        }
        bluetoothController.connectToDevice();
    }

    public boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void requestBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    headerController.setStatus(granted ? "麦克风权限已获取" : "麦克风权限被拒绝",
                            granted ? "success" : "error");
                }
                if (Manifest.permission.BLUETOOTH_CONNECT.equals(permissions[i]) && granted) {
                    // 启动批量授权中的蓝牙权限到位后，续接主动连接
                    tryAutoConnectBluetooth();
                }
            }
        } else if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                headerController.setStatus("蓝牙权限已获取");
                tryAutoConnectBluetooth();
            } else {
                headerController.setStatus("蓝牙权限被拒绝", "error");
            }
        }
    }

    public void startSystemAudioCapture() {
        if (audioAnalyzer.isSystemAudioActive()) {
            stopSystemAudioCapture();
            return;
        }
        
        if (mediaProjectionManager == null) {
            headerController.setStatus("设备不支持系统音频捕获", "error");
            return;
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            headerController.setStatus("系统音频捕获需要 Android 10 及以上版本", "error");
            return;
        }
        
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }
    
    public void stopSystemAudioCapture() {
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        
        Intent serviceIntent = new Intent(this, SystemAudioCaptureService.class);
        stopService(serviceIntent);
        
        audioAnalyzer.stopSystemAudioCapture();
        headerController.setStatus("系统音频捕获已停止");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Intent serviceIntent = new Intent(this, SystemAudioCaptureService.class);
                serviceIntent.putExtra("START_CAPTURE", true);
                serviceIntent.putExtra("RESULT_CODE", resultCode);
                serviceIntent.putExtra("DATA_INTENT", data);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Intent bindIntent = new Intent(this, SystemAudioCaptureService.class);
                bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE);
                
                audioAnalyzer.stopAll();
                audioAnalyzer.isSystemAudioActive = true;
                
                headerController.setStatus("系统音频捕获已启动");
            } else {
                headerController.setStatus("系统音频捕获权限被拒绝", "error");
            }
        } else if (requestCode == REQUEST_BLUETOOTH_ENABLE) {
            // 用户同意开启蓝牙后续接主动连接
            if (resultCode == RESULT_OK) {
                tryAutoConnectBluetooth();
            } else {
                headerController.setStatus("蓝牙未开启，无法自动连接", "error");
            }
        }
    }
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SystemAudioCaptureService.LocalBinder binder = (SystemAudioCaptureService.LocalBinder) service;
            audioCaptureService = binder.getService();
            
            audioCaptureService.setOnAudioDataListener((data, size) -> {
                audioAnalyzer.processSystemAudioData(data, size);
            });
            
            isServiceBound = true;
            Log.d("MainActivity", "系统音频服务已绑定");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioCaptureService = null;
            isServiceBound = false;
            Log.d("MainActivity", "系统音频服务已断开");
        }
    };

    @Override
    public void onAudioData(double[] frequencyData, double db, int peakFreq) {
        runOnUiThread(() -> {
            rgbController.updateRGB(frequencyData);
            rgbController.updateDisplay(db, peakFreq);
            headerController.updateNoise(db, audioAnalyzer.isMicActive());
        });
    }

    @Override
    public void onPlaybackComplete() {
        audioController.onPlaybackComplete();
        rgbController.reset();
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            headerController.setStatus(error, "error");
        });
    }

    @Override
    public void onBluetoothConnected() {
        headerController.setStatus("蓝牙连接成功", "success");
        audioController.updateBluetoothButton(true);
        if (tvSensor != null) {
            tvSensor.setText("硬件温湿度：等待数据...");
        }
    }

    @Override
    public void onBluetoothDisconnected() {
        audioController.updateBluetoothButton(false);
        audioController.stopHwMicMonitoring();
        if (tvSensor != null) {
            tvSensor.setText("硬件温湿度：未连接");
        }
        if (bluetoothController != null && bluetoothController.isAutoConnectEnabled()) {
            headerController.setStatus("蓝牙已断开，正在自动重连...");
        } else {
            headerController.setStatus("蓝牙已断开");
        }
    }

    @Override
    public void onBluetoothError(String message) {
        headerController.setStatus(message, "error");
        audioController.updateBluetoothButton(false);
    }

    @Override
    public void onSensorData(double temp, double humid) {
        if (tvSensor != null) {
            tvSensor.setText(String.format(Locale.getDefault(),
                    "硬件温湿度：%.1f°C    %.1f%%RH", temp, humid));
        }
    }

    @Override
    public void onMicPcm(byte[] pcm, int len) {
        audioController.feedHwMicPcm(pcm, len);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioAnalyzer != null) {
            audioAnalyzer.release();
        }
        if (audioController != null) {
            audioController.stopHwMicMonitoring();
            audioController.release();
        }
        if (bluetoothController != null) {
            bluetoothController.disconnect();
        }
    }
}