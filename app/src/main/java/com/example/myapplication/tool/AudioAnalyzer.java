package com.example.myapplication.tool;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;

import android.net.Uri;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.Arrays;

public class AudioAnalyzer {

    private static final String TAG = "AudioAnalyzer";
    private static final int FFT_SIZE = 2048;
    private static final int MAX_FREQ = 2000;
    private static final int SAMPLE_RATE = 44100;

    private Context context;
    private MediaPlayer mediaPlayer;
    private AudioRecord audioRecord;
    private Visualizer visualizer;
    private boolean isMicActive = false;
    public boolean isSystemAudioActive = false;
    private boolean isPlaying = false;
    private float playbackRate = 1.0f;

    private int bufferSize;
    private short[] audioBuffer;
    private double[] frequencyData;
    private double[] fftRe, fftIm;

    private double maxEnergy = 0.0;
    private final double agcDecay = 0.995;
    private double currentFreqPerBin = SAMPLE_RATE / (double) FFT_SIZE;

    private OnAudioDataListener listener;
    private Thread processingThread;
    private volatile boolean isProcessing = false;

    public interface OnAudioDataListener {
        void onAudioData(double[] frequencyData, double db, int peakFreq);

        void onPlaybackComplete();

        void onError(String error);
    }

    public AudioAnalyzer(Context context) {
        System.out.println(">>>AudioAnalyzer");
        this.context = context;
        this.bufferSize = Math.max(2048, AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
        if (bufferSize <= 0) {
            bufferSize = 4096;
        }
        this.audioBuffer = new short[bufferSize];
        this.frequencyData = new double[FFT_SIZE / 2];
        this.fftRe = new double[FFT_SIZE / 2];
        this.fftIm = new double[FFT_SIZE / 2];
    }

    public void setOnAudioDataListener(OnAudioDataListener listener) {
        this.listener = listener;
    }

    public void setPlaybackRate(float rate) {
        this.playbackRate = rate;
        if (mediaPlayer != null) {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(rate));
        }
    }

    public float getPlaybackRate() {
        return playbackRate;
    }

    public boolean isMicActive() {
        return isMicActive;
    }

    public boolean isPlaying() {
        return isPlaying && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public double getFreqPerBin() {
        return currentFreqPerBin;
    }

    public void playAudio(String filePath) {
        stopAll();

        try {
            mediaPlayer = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(playbackRate));
            mediaPlayer.prepare();

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                stopVisualizer();
                if (listener != null) {
                    listener.onPlaybackComplete();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (listener != null) {
                    listener.onError("播放错误: " + what);
                }
                return true;
            });

            mediaPlayer.start();
            isPlaying = true;
            Log.d(TAG, "MediaPlayer(startAudio) started, audioSessionId=" + mediaPlayer.getAudioSessionId());
            startVisualizer();

        } catch (IOException e) {
            Log.e(TAG, "播放音频失败", e);
            if (listener != null) {
                listener.onError("播放失败: " + e.getMessage());
            }
        }
    }

    public void playAudio(Context context, Uri uri) {
        stopAll();

        try {
            mediaPlayer = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(playbackRate));
            mediaPlayer.prepare();

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                stopVisualizer();
                if (listener != null) {
                    listener.onPlaybackComplete();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (listener != null) {
                    listener.onError("播放错误: " + what);
                }
                return true;
            });

            mediaPlayer.start();
            isPlaying = true;
            Log.d(TAG, "MediaPlayer(playAudioUri) started, audioSessionId=" + mediaPlayer.getAudioSessionId());
            startVisualizer();

        } catch (IOException e) {
            Log.e(TAG, "播放音频失败", e);
            if (listener != null) {
                listener.onError("播放失败: " + e.getMessage());
            }
        }
    }

    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            stopVisualizer();
        }
    }

    public void resumeAudio() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            startVisualizer();
        }
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void toggleMic() {
        if (isMicActive) {
            stopMic();
        } else {
            startMic();
        }
    }

    private void startMic() {
        stopAll();

        try {
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                if (listener != null) {
                    listener.onError("麦克风初始化失败");
                }
                return;
            }

            audioRecord.startRecording();
            isMicActive = true;
            startProcessing();

        } catch (Exception e) {
            Log.e(TAG, "麦克风启动失败", e);
            if (listener != null) {
                listener.onError("麦克风启动失败: " + e.getMessage());
            }
        }
    }

    private void stopMic() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "停止麦克风失败", e);
            }
            audioRecord = null;
        }
        isMicActive = false;
        stopProcessing();
    }

    private void startAudioRecordCapture() {
        if (audioRecord != null) return;
        
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, 
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize <= 0) {
                bufferSize = 4096;
            }
            
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, 
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败");
                audioRecord = null;
                return;
            }
            
            audioRecord.startRecording();
            isMicActive = true;
            startProcessing();
            
            Log.d(TAG, "AudioRecord 捕获启动成功");
            
        } catch (Exception e) {
            Log.e(TAG, "AudioRecord 启动失败", e);
            audioRecord = null;
        }
    }
    
    public void processSystemAudioData(short[] data, int size) {
        if (!isSystemAudioActive) return;
        
        int n = Math.min(size, audioBuffer.length);
        System.arraycopy(data, 0, audioBuffer, 0, n);
        
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            if (i < n) {
                fftRe[i] = audioBuffer[i] / 32768.0;
            } else {
                fftRe[i] = 0;
            }
            fftIm[i] = 0;
        }
        
        fft();
        processFFTDataFromDouble(SAMPLE_RATE);
    }
    
    public void stopSystemAudioCapture() {
        isSystemAudioActive = false;
        stopProcessing();
        Log.d(TAG, "系统音频捕获已停止");
    }
    
    public boolean isSystemAudioActive() {
        return isSystemAudioActive;
    }
    
    private void startVisualizer() {
        if (mediaPlayer == null) return;
        
        try {
            int audioSessionId = mediaPlayer.getAudioSessionId();
            if (audioSessionId == 0) {
                Log.e(TAG, "无效的音频会话ID");
                return;
            }
            
            visualizer = new Visualizer(audioSessionId);
            
            int[] captureSizes = Visualizer.getCaptureSizeRange();
            int captureSize = captureSizes[1];
            for (int size : captureSizes) {
                if (size >= 1024) {
                    captureSize = size;
                    break;
                }
            }
            visualizer.setCaptureSize(captureSize);
            
            int rate = Visualizer.getMaxCaptureRate();
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {}
                
                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    Log.d(TAG, "onFftDataCapture: fft.length=" + fft.length + ", rate=" + samplingRate);
                    processFFTData(fft, samplingRate);
                }
            }, rate, false, true);
            
            visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            
            visualizer.setEnabled(true);
            
            Log.d(TAG, "Visualizer 启动成功: sessionId=" + audioSessionId + ", captureSize=" + captureSize);
            
        } catch (Exception e) {
            Log.e(TAG, "Visualizer 启动失败", e);
            startAudioRecordCapture();
        }
    }
    
    private void stopVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.e(TAG, "Visualizer 停止失败", e);
            }
            visualizer = null;
        }
    }

    private void startProcessing() {
        if (isProcessing) return;

        isProcessing = true;
        processingThread = new Thread(() -> {
            while (isProcessing) {
                try {
                    processMicData();
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "AudioProcessing");
        processingThread.start();
    }

    private void stopProcessing() {
        isProcessing = false;
        if (processingThread != null) {
            processingThread.interrupt();
            try {
                processingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            processingThread = null;
        }
    }

    private void processMicData() {
        if (!isMicActive || audioRecord == null) return;

        int shortsRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
        if (shortsRead <= 0) return;

        int n = FFT_SIZE / 2;
        for (int i = 0; i < n; i++) {
            if (i < shortsRead) {
                fftRe[i] = audioBuffer[i] / 32768.0;
            } else {
                fftRe[i] = 0;
            }
            fftIm[i] = 0;
        }

        fft();
        processFFTDataFromDouble(SAMPLE_RATE);
    }

    private void fft() {
        int n = FFT_SIZE / 2;

        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; j >= bit; bit >>= 1) {
                j -= bit;
            }
            j += bit;
            if (i < j) {
                double tempRe = fftRe[i];
                double tempIm = fftIm[i];
                fftRe[i] = fftRe[j];
                fftIm[i] = fftIm[j];
                fftRe[j] = tempRe;
                fftIm[j] = tempIm;
            }
        }

        for (int s = 1; s <= 10; s++) {
            int m = 1 << s;
            int m2 = m >> 1;
            double wBase = -2 * Math.PI / m;

            for (int k = 0; k < n; k += m) {
                for (int j = 0; j < m2; j++) {
                    double w = j * wBase;
                    double cosW = Math.cos(w);
                    double sinW = Math.sin(w);

                    int t = k + j;
                    int u = t + m2;

                    double tempRe = fftRe[t];
                    double tempIm = fftIm[t];

                    double uRe = cosW * fftRe[u] - sinW * fftIm[u];
                    double uIm = sinW * fftRe[u] + cosW * fftIm[u];

                    fftRe[t] = tempRe + uRe;
                    fftIm[t] = tempIm + uIm;
                    fftRe[u] = tempRe - uRe;
                    fftIm[u] = tempIm - uIm;
                }
            }
        }
    }

    private void processFFTData(byte[] fft, int samplingRate) {
        int freqBinCount = fft.length / 2;
        double actualSamplingRate = SAMPLE_RATE;
        double freqPerBin = actualSamplingRate / (double) freqBinCount;
        currentFreqPerBin = freqPerBin;
        int maxBin = Math.min((int) (MAX_FREQ / freqPerBin) + 1, freqBinCount);
        
        if (maxBin > frequencyData.length) {
            Log.e(TAG, "maxBin(" + maxBin + ") > frequencyData.length(" + frequencyData.length + ")");
            maxBin = frequencyData.length;
        }

        Log.d(TAG, "FFT Data: samplingRate=" + samplingRate + ", freqBinCount=" + freqBinCount + ", freqPerBin=" + freqPerBin + ", maxBin=" + maxBin);

        for (int i = 0; i < maxBin && 2 * i + 1 < fft.length; i++) {
            int realIndex = 2 * i;
            int imagIndex = 2 * i + 1;
            double real = fft[realIndex] / 128.0;
            double imag = fft[imagIndex] / 128.0;
            frequencyData[i] = Math.sqrt(real * real + imag * imag);
        }

        StringBuilder fftLog = new StringBuilder(">>> FFT frequencyData[0-20]: ");
        for (int i = 0; i < Math.min(21, maxBin); i++) {
            fftLog.append(String.format("%.3f ", frequencyData[i]));
        }
        Log.d(TAG, fftLog.toString());

        double totalEnergy = 0;
        for (int i = 0; i < maxBin; i++) {
            totalEnergy += frequencyData[i];
        }
        Log.d(TAG, "Total FFT energy: " + totalEnergy);

        applyAGC(maxBin);
        calculateAndNotify(freqPerBin, maxBin);
    }

    private void processFFTDataFromDouble(int samplingRate) {
        double freqPerBin = samplingRate / (double) FFT_SIZE;
        currentFreqPerBin = freqPerBin;
        int maxBin = Math.min((int) (MAX_FREQ / freqPerBin) + 1, FFT_SIZE / 2);

        for (int i = 0; i < maxBin; i++) {
            frequencyData[i] = Math.sqrt(fftRe[i] * fftRe[i] + fftIm[i] * fftIm[i]);
        }

        applyAGC(maxBin);
        calculateAndNotify(freqPerBin, maxBin);
    }

    private void applyAGC(int maxBin) {
        double currentMax = 0;
        for (int i = 0; i < maxBin; i++) {
            if (frequencyData[i] > currentMax) {
                currentMax = frequencyData[i];
            }
        }

        maxEnergy = Math.max(currentMax, maxEnergy * agcDecay);
        if (maxEnergy < 1e-5) maxEnergy = 1e-5;

        double scale = 1.0 / maxEnergy;
        for (int i = 0; i < maxBin; i++) {
            frequencyData[i] = Math.min(1.0, frequencyData[i] * scale);
        }
    }

    private void calculateAndNotify(double freqPerBin, int maxBin) {
        double sumSq = 0;
        for (int i = 0; i < maxBin; i++) {
            sumSq += frequencyData[i] * frequencyData[i];
        }
        double rms = Math.sqrt(sumSq / maxBin);
        double db = 20 * Math.log10(rms + 1e-5);

        int peakIndex = 0;
        double peakValue = 0;
        for (int i = 0; i < maxBin; i++) {
            if (frequencyData[i] > peakValue) {
                peakValue = frequencyData[i];
                peakIndex = i;
            }
        }
        int peakFreq = (int) (peakIndex * freqPerBin);

        if (listener != null) {
            listener.onAudioData(frequencyData, db, peakFreq);
        }
    }

    public double calcModel(double[] data, double iL, double iM, double iR, String model) {
        double freqPerBin = currentFreqPerBin;

        double L_float = iL / freqPerBin;
        double M_float = iM / freqPerBin;
        double R_float = iR / freqPerBin;

        int L = Math.max(0, Math.min(data.length - 1, (int) Math.floor(L_float)));
        int M = Math.max(L, Math.min(data.length - 1, (int) Math.floor(M_float)));
        int R = Math.max(M, Math.min(data.length - 1, (int) Math.floor(R_float)));

        int N = R - L;
        if (N <= 0) return 0;
        
        Log.d(TAG, "calcModel: freqPerBin=" + freqPerBin + ", range[" + iL + "," + iR + "] -> indices[" + L + "," + R + "], N=" + N);

        double value = 0;
        double maxWeight = 0;
        switch (model) {
            case "mean":
                for (int i = L; i < R; i++) {
                    value += data[i];
                    maxWeight += 1;
                }
                return maxWeight > 0 ? value / maxWeight : 0;
            case "increase":
                for (int i = L; i < R; i++) {
                    double weight = (i - L_float) / N;
                    value += data[i] * weight;
                    maxWeight += weight;
                }
                return maxWeight > 0 ? value / maxWeight : 0;
            case "decrease":
                for (int i = L; i < R; i++) {
                    double weight = (R_float - i) / N;
                    value += data[i] * weight;
                    maxWeight += weight;
                }
                return maxWeight > 0 ? value / maxWeight : 0;
            case "mountain":
                int N1 = M - L;
                int N2 = R - M;
                double v1 = 0, v2 = 0;
                double w1 = 0, w2 = 0;
                for (int i = L; i < M; i++) {
                    double weight = (i - L_float) / N;
                    v1 += data[i] * weight;
                    w1 += weight;
                }
                for (int i = M; i < R; i++) {
                    double weight = (R_float - i) / N;
                    v2 += data[i] * weight;
                    w2 += weight;
                }
                return (N1 > 0 ? v1 / Math.max(w1, 1) : 0) + (N2 > 0 ? v2 / Math.max(w2, 1) : 0);
            case "valley":
                N1 = M - L;
                N2 = R - M;
                v1 = 0; v2 = 0;
                w1 = 0; w2 = 0;
                for (int i = L; i < M; i++) {
                    double weight = (M_float - i) / N;
                    v1 += data[i] * weight;
                    w1 += weight;
                }
                for (int i = M; i < R; i++) {
                    double weight = (i - M_float) / N;
                    v2 += data[i] * weight;
                    w2 += weight;
                }
                return (N1 > 0 ? v1 / Math.max(w1, 1) : 0) + (N2 > 0 ? v2 / Math.max(w2, 1) : 0);
            default:
                return 0;
        }
    }

    public void stopAll() {
        stopProcessing();
        stopMic();
        stopVisualizer();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void release() {
        stopAll();
    }
}