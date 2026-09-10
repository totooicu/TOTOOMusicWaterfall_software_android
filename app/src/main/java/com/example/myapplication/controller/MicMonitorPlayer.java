package com.example.myapplication.controller;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 硬件麦克风上行 PCM 的播放器（调试用，验证硬件麦克风音质）。
 * 参数与固件 hw_mic.h 对齐：16kHz / 16bit / 单声道。
 * 接收线程入队，独立播放线程阻塞写入 AudioTrack；
 * 队列满时丢弃最旧一帧，防止网络抖动累积成明显延迟。
 */
public class MicMonitorPlayer {

    private static final String TAG = "MicMonitorPlayer";
    public static final int SAMPLE_RATE = 16000;

    // 约 32ms 一帧（512 samples * 2 bytes），缓存 32 帧 ≈ 1s
    private static final int QUEUE_CAPACITY = 32;

    private AudioTrack audioTrack;
    private Thread playThread;
    private volatile boolean playing = false;
    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    public boolean isPlaying() {
        return playing;
    }

    public void start() {
        if (playing) {
            return;
        }

        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer == AudioTrack.ERROR || minBuffer == AudioTrack.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioTrack 最小缓冲计算失败");
            return;
        }
        // 至少缓冲 200ms，抗蓝牙抖动
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE * 2 / 5);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        queue.clear();
        audioTrack.play();
        playing = true;

        playThread = new Thread(() -> {
            while (playing || !queue.isEmpty()) {
                try {
                    byte[] data = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (data != null && audioTrack != null) {
                        audioTrack.write(data, 0, data.length);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "播放线程异常", e);
                    break;
                }
            }
        }, "hw-mic-player");
        playThread.start();
    }

    /**
     * 送入一帧 PCM（蓝牙接收线程调用）。队列满时丢弃最旧帧。
     */
    public void enqueue(byte[] pcm, int len) {
        if (!playing) {
            return;
        }
        byte[] frame;
        if (pcm.length == len) {
            frame = pcm;
        } else {
            frame = new byte[len];
            System.arraycopy(pcm, 0, frame, 0, len);
        }
        if (!queue.offer(frame)) {
            queue.poll();
            queue.offer(frame);
        }
    }

    public void stop() {
        if (!playing) {
            return;
        }
        playing = false;
        if (playThread != null) {
            playThread.interrupt();
            try {
                playThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playThread = null;
        }
        queue.clear();
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "释放 AudioTrack 失败", e);
            }
            audioTrack = null;
        }
    }
}
