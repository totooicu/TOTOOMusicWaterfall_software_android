package com.example.myapplication.controller;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.tool.AudioAnalyzer;
import com.example.myapplication.tool.ParamsManager;

public class AudioController {
    private final AppCompatActivity activity;
    private final AudioAnalyzer audioAnalyzer;
    private final ParamsManager paramsManager;
    private final Handler handler;

    private TextView tvFileName;
    private Button btnSelectFile;
    private Button btnPlay;
    private Button btnMic;
    private Button btnSystemAudio;
    private Button btnDisplay;
    private Button btnHwMic;

    private Button btnSpeedMinus25, btnSpeedMinus05, btnSpeedMinus01;
    private Button btnSpeedPlus01, btnSpeedPlus05, btnSpeedPlus25;
    private TextView tvSpeed;

    private SeekBar sbProgress;
    private TextView tvCurrentTime, tvTotalTime;

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private Uri currentAudioUri = null;
    private boolean isPlaying = false;
    private boolean isMicActive = false;

    private final HeaderController headerController;
    private final Runnable progressUpdateRunnable;
    private BluetoothController bluetoothController;
    private Button btnBluetooth;
    // 硬件麦克风上行音频播放器（调试用）
    private final MicMonitorPlayer hwMicPlayer = new MicMonitorPlayer();

    public AudioController(AppCompatActivity activity, AudioAnalyzer audioAnalyzer, 
                          ParamsManager paramsManager, HeaderController headerController,
                          BluetoothController bluetoothController) {
        this.activity = activity;
        this.audioAnalyzer = audioAnalyzer;
        this.paramsManager = paramsManager;
        this.headerController = headerController;
        this.bluetoothController = bluetoothController;
        this.handler = new Handler();

        progressUpdateRunnable = this::startProgressUpdate;
        initFilePicker();
        bindEvents();
        updateSpeedUI();
    }

    private void initFilePicker() {
        filePickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getData();
                            if (uri != null) {
                                currentAudioUri = uri;
                                String fileName = getFileNameFromUri(uri);
                                if (tvFileName != null) {
                                    tvFileName.setText(fileName);
                                }
                                if (btnPlay != null) {
                                    btnPlay.setEnabled(true);
                                }
                                headerController.setStatus("音频文件已加载", "success");

                                try {
                                    MediaPlayer mp = new MediaPlayer();
                                    mp.setDataSource(activity, uri);
                                    mp.prepare();
                                    int duration = mp.getDuration();
                                    mp.release();
                                    if (tvTotalTime != null) {
                                        tvTotalTime.setText(formatTime(duration));
                                    }
                                } catch (Exception e) {
                                    if (tvTotalTime != null) {
                                        tvTotalTime.setText("00:00");
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "未知文件";
        ContentResolver resolver = activity.getContentResolver();
        String[] projection = {MediaStore.Audio.Media.DISPLAY_NAME};
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            if (columnIndex != -1) {
                fileName = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return fileName;
    }

    private void bindEvents() {
        tvFileName = activity.findViewById(R.id.tvFileName);
        btnSelectFile = activity.findViewById(R.id.btnSelectFile);
        btnPlay = activity.findViewById(R.id.btnPlay);
        btnMic = activity.findViewById(R.id.btnMic);
        btnSystemAudio = activity.findViewById(R.id.btnSystemAudio);
        btnDisplay = activity.findViewById(R.id.btnDisplay);

        btnSpeedMinus25 = activity.findViewById(R.id.btnSpeedMinus25);
        btnSpeedMinus05 = activity.findViewById(R.id.btnSpeedMinus05);
        btnSpeedMinus01 = activity.findViewById(R.id.btnSpeedMinus01);
        btnSpeedPlus01 = activity.findViewById(R.id.btnSpeedPlus01);
        btnSpeedPlus05 = activity.findViewById(R.id.btnSpeedPlus05);
        btnSpeedPlus25 = activity.findViewById(R.id.btnSpeedPlus25);
        tvSpeed = activity.findViewById(R.id.tvSpeed);

        sbProgress = activity.findViewById(R.id.sbProgress);
        tvCurrentTime = activity.findViewById(R.id.tvCurrentTime);
        tvTotalTime = activity.findViewById(R.id.tvTotalTime);

        if (btnSelectFile != null) {
            btnSelectFile.setOnClickListener(v -> selectAudioFile());
        }
        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> togglePlay());
        }

        if (btnMic != null) {
            btnMic.setOnClickListener(v -> {
                if (checkMicPermission()) {
                    audioAnalyzer.toggleMic();
                    isMicActive = audioAnalyzer.isMicActive();
                    updateMicButton();
                }
            });
        }

        if (btnSystemAudio != null) {
            btnSystemAudio.setOnClickListener(v -> {
                ((MainActivity) activity).startSystemAudioCapture();
            });
        }

        if (btnDisplay != null) {
            btnDisplay.setOnClickListener(v -> {
                headerController.setStatus("显示模式切换功能", "success");
            });
        }

        btnBluetooth = activity.findViewById(R.id.btnBluetooth);
        if (btnBluetooth != null) {
            btnBluetooth.setOnClickListener(v -> {
                if (bluetoothController.isConnected()) {
                    bluetoothController.manualDisconnect();
                    updateBluetoothButton(false);
                } else {
                    if (activity instanceof com.example.myapplication.MainActivity) {
                        com.example.myapplication.MainActivity mainActivity = (com.example.myapplication.MainActivity) activity;
                        if (!mainActivity.hasBluetoothConnectPermission()) {
                            bluetoothController.markManualConnectRequested();
                            mainActivity.requestBluetoothConnectPermission();
                            return;
                        }
                    }
                    
                    if (!bluetoothController.isBluetoothEnabled()) {
                        bluetoothController.markManualConnectRequested();
                        bluetoothController.requestEnableBluetooth(102);
                    } else {
                        bluetoothController.manualConnect();
                        btnBluetooth.setText("连接中...");
                    }
                }
            });
        }

        btnHwMic = activity.findViewById(R.id.btnHwMic);
        if (btnHwMic != null) {
            btnHwMic.setOnClickListener(v -> toggleHwMicMonitoring());
        }

        if (btnSpeedMinus01 != null) btnSpeedMinus01.setOnClickListener(v -> adjustSpeed(-0.1f));
        if (btnSpeedMinus05 != null) btnSpeedMinus05.setOnClickListener(v -> adjustSpeed(-0.5f));
        if (btnSpeedMinus25 != null) btnSpeedMinus25.setOnClickListener(v -> setSpeed(0.5f));
        if (btnSpeedPlus01 != null) btnSpeedPlus01.setOnClickListener(v -> adjustSpeed(0.1f));
        if (btnSpeedPlus05 != null) btnSpeedPlus05.setOnClickListener(v -> adjustSpeed(0.5f));
        if (btnSpeedPlus25 != null) btnSpeedPlus25.setOnClickListener(v -> setSpeed(2.5f));

        if (sbProgress != null) {
            sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && audioAnalyzer.getDuration() > 0) {
                        int position = (int) ((progress / 1000.0) * audioAnalyzer.getDuration());
                        audioAnalyzer.seekTo(position);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    public void updateBluetoothButton(boolean connected) {
        if (btnBluetooth == null) return;
        btnBluetooth.post(() -> btnBluetooth.setText(connected ? "断开连接" : "蓝牙连接"));
    }

    private void toggleHwMicMonitoring() {
        if (hwMicPlayer.isPlaying()) {
            stopHwMicMonitoring();
            return;
        }
        if (!bluetoothController.isConnected()) {
            headerController.setStatus("请先连接硬件蓝牙", "error");
            return;
        }
        hwMicPlayer.start();
        bluetoothController.setMicMonitoring(true);
        if (btnHwMic != null) {
            btnHwMic.setText("停止监听");
        }
        headerController.setStatus("正在监听硬件麦克风", "success");
    }

    /**
     * 接收蓝牙线程转发的硬件麦克风 PCM
     */
    public void feedHwMicPcm(byte[] pcm, int len) {
        hwMicPlayer.enqueue(pcm, len);
    }

    /**
     * 停止监听：通知固件关闭上行（若已断开则静默忽略）并释放本地播放器
     */
    public void stopHwMicMonitoring() {
        if (!hwMicPlayer.isPlaying()) {
            return;
        }
        bluetoothController.setMicMonitoring(false);
        hwMicPlayer.stop();
        if (btnHwMic != null) {
            btnHwMic.post(() -> btnHwMic.setText("HW麦克风"));
        }
    }

    private void selectAudioFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        filePickerLauncher.launch(intent);
    }

    private void togglePlay() {
        if (audioAnalyzer.isPlaying()) {
            audioAnalyzer.pauseAudio();
            isPlaying = false;
            if (btnPlay != null) btnPlay.setText("播放音频");
            headerController.setStatus("已暂停");
        } else {
            if (currentAudioUri != null) {
                audioAnalyzer.playAudio(activity, currentAudioUri);
                isPlaying = true;
                if (btnPlay != null) btnPlay.setText("暂停");
                headerController.setStatus("音频播放中", "success");
            }
        }
    }

    private void adjustSpeed(float delta) {
        float newSpeed = paramsManager.playbackRate + delta;
        setSpeed(newSpeed);
    }

    private void setSpeed(float speed) {
        speed = Math.max(0.5f, Math.min(2.5f, speed));
        paramsManager.playbackRate = speed;
        audioAnalyzer.setPlaybackRate(speed);
        if (tvSpeed != null) {
            tvSpeed.setText(String.format("%.1fx", speed));
        }
    }

    private void updateSpeedUI() {
        if (tvSpeed != null) {
            tvSpeed.setText(String.format("%.1fx", paramsManager.playbackRate));
        }
    }

    private void updateMicButton() {
        if (btnMic != null) {
            if (isMicActive) {
                btnMic.setBackgroundColor(android.graphics.Color.parseColor("#FF9c27b0"));
                headerController.setStatus("麦克风已开启", "success");
            } else {
                btnMic.setBackgroundColor(android.graphics.Color.parseColor("#FF673ab7"));
                headerController.setStatus("麦克风已关闭");
            }
        }
    }

    private boolean checkMicPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void startProgressUpdate() {
        if (audioAnalyzer.getDuration() > 0) {
            int current = audioAnalyzer.getCurrentPosition();
            int duration = audioAnalyzer.getDuration();
            int progress = (int) ((current / (double) duration) * 1000);
            if (sbProgress != null) sbProgress.setProgress(progress);
            if (tvCurrentTime != null) tvCurrentTime.setText(formatTime(current));
        }
        handler.postDelayed(progressUpdateRunnable, 100);
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void startProgressUpdates() {
        handler.post(progressUpdateRunnable);
    }

    public void stopProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable);
    }

    public void onPlaybackComplete() {
        activity.runOnUiThread(() -> {
            if (btnPlay != null) btnPlay.setText("播放音频");
            isPlaying = false;
            headerController.setStatus("已暂停");
        });
    }

    public void release() {
        stopProgressUpdates();
    }
}