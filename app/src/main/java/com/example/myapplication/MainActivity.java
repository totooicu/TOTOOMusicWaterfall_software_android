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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.controller.AudioController;
import com.example.myapplication.controller.BluetoothController;
import com.example.myapplication.controller.HeaderController;
import com.example.myapplication.controller.MediaInfoController;
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
    private MediaInfoController mediaInfoController;
    private TextView tvSensor;

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
        mediaInfoController = new MediaInfoController(this, bluetoothController,
                audioAnalyzer, audioController, headerController);
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

                // 捕获后台音乐时引导一次通知使用权授权，用于读取其他播放器的歌名/进度
                if (mediaInfoController != null) {
                    mediaInfoController.promptNotificationAccessIfNeeded();
                }

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
        if (mediaInfoController != null) {
            // 开始周期下发歌名位图/进度（后台会话或本地文件）
            mediaInfoController.start();
            // 想显示其他音乐 App 的歌名/进度必须有通知使用权，连接后引导一次
            mediaInfoController.promptNotificationAccessIfNeeded();
        }
        if (tvSensor != null) {
            tvSensor.setText("硬件温湿度：等待数据...");
        }
    }

    @Override
    public void onBluetoothDisconnected() {
        audioController.updateBluetoothButton(false);
        audioController.stopHwMicMonitoring();
        if (mediaInfoController != null) {
            mediaInfoController.stop();
        }
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
        if (mediaInfoController != null) {
            mediaInfoController.stop();
        }
        if (bluetoothController != null) {
            bluetoothController.disconnect();
        }
    }
}