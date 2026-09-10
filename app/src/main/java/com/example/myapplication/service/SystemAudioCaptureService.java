package com.example.myapplication.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;

public class SystemAudioCaptureService extends Service {

    private static final String TAG = "SystemAudioCapture";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "SystemAudioCapture";

    private AudioRecord audioRecord;
    private boolean isRunning = false;
    private Thread captureThread;
    private OnAudioDataListener listener;
    private MediaProjection mediaProjection;

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

    public interface OnAudioDataListener {
        void onAudioData(short[] data, int size);
    }

    public void setOnAudioDataListener(OnAudioDataListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        
        if (intent != null && intent.getBooleanExtra("START_CAPTURE", false)) {
            int resultCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent data = intent.getParcelableExtra("DATA_INTENT");
            
            if (resultCode != 0 && data != null) {
                startCapture(resultCode, data);
            }
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public SystemAudioCaptureService getService() {
            return SystemAudioCaptureService.this;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "系统音频捕获",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RGB氛围灯")
                .setContentText("正在捕获系统音频...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startCapture(int resultCode, Intent data) {
        if (isRunning) return;
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "系统音频捕获需要 Android 10 及以上版本");
            return;
        }

        try {
            MediaProjectionManager projectionManager = 
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();

            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败");
                cleanup();
                return;
            }

            audioRecord.startRecording();
            isRunning = true;

            captureThread = new Thread(() -> {
                short[] buffer = new short[BUFFER_SIZE];
                while (isRunning) {
                    int shortsRead = audioRecord.read(buffer, 0, buffer.length);
                    if (shortsRead > 0 && listener != null) {
                        listener.onAudioData(buffer, shortsRead);
                    }
                }
            }, "SystemAudioCaptureThread");

            captureThread.start();
            Log.d(TAG, "系统音频捕获已启动");

        } catch (SecurityException e) {
            Log.e(TAG, "系统音频捕获权限错误", e);
            cleanup();
        } catch (Exception e) {
            Log.e(TAG, "启动系统音频捕获失败", e);
            cleanup();
        }
    }

    private void stopCapture() {
        isRunning = false;
        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "停止音频捕获失败", e);
            }
            audioRecord = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        Log.d(TAG, "系统音频捕获已停止");
    }

    private void cleanup() {
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
}