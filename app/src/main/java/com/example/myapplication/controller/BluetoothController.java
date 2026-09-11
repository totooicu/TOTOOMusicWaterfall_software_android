package com.example.myapplication.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothController {

    private static final String TAG = "BluetoothController";
    private static final String BLUETOOTH_NAME = "TOTOOMusicWaterfall";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // 与硬件约定的音频频段数：16 × 100Hz（对应屏幕 FFT 柱状图）
    public static final int AUDIO_BAND_COUNT = 16;
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    /**
     * 写流锁：音频 '$' 帧（音频线程 ~20fps）与位图/文本帧（主线程，位图帧最长 1.3KB）
     * 共用一条 SPP 字节流。若不加锁，长位图帧传输期间会被 '$' 帧字节插入，固件状态机
     * 把它们当载荷吃掉导致 XOR 校验失败、整帧丢弃（表现为长歌词不显示）。
     * 所有 write+flush 必须持此锁，保证“一帧”在字节流中原子。
     */
    private final Object writeLock = new Object();
    private InputStream inputStream;
    private boolean isConnected = false;
    private OnBluetoothStatusListener listener;
    private long lastSendTime = 0;
    private static final long SEND_INTERVAL_MS = 50;

    // 接收线程控制：cleanup 前置 false，使 read() 因 socket 关闭退出时不误判为意外断开
    private volatile boolean receiving = false;
    private Thread receiveThread;

    // 固件上行二进制 PCM 帧同步头
    private static final int PCM_FRAME_MAGIC0 = 0xA5;
    private static final int PCM_FRAME_MAGIC1 = 0x5A;
    private static final int PCM_PAYLOAD_MAX = 2048;

    // 是否允许自动连接/自动重连：唯一真源，只由用户的手动连接/手动断开动作翻转
    private volatile boolean shouldAutoConnect = true;
    // 是否有连接尝试正在进行（防止重试任务与手动触发并发）
    private volatile boolean isConnecting = false;

    private static final long AUTO_RECONNECT_DELAY_MS = 3000;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable reconnectRunnable = this::connectToDevice;

    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public interface OnBluetoothStatusListener {
        void onBluetoothConnected();
        void onBluetoothDisconnected();
        void onBluetoothError(String message);
        // 固件周期上报的温湿度
        void onSensorData(double temp, double humid);
        // 固件上行的一帧麦克风 PCM（16kHz/16bit/mono），在蓝牙接收线程回调
        void onMicPcm(byte[] pcm, int len);
    }

    public BluetoothController(Context context, OnBluetoothStatusListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void requestEnableBluetooth(int requestCode) {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((android.app.Activity) context).startActivityForResult(enableIntent, requestCode);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isAutoConnectEnabled() {
        return shouldAutoConnect;
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice() {
        if (isConnected || isConnecting) {
            return;
        }

        if (!isBluetoothAvailable()) {
            notifyError("设备不支持蓝牙");
            return;
        }

        if (!isBluetoothEnabled()) {
            notifyError("蓝牙未开启");
            // 蓝牙开关/权限就绪后由页面回调主动触发，这里不做无意义轮询
            return;
        }

        if (!hasBluetoothConnectPermission()) {
            notifyError("缺少蓝牙连接权限");
            return;
        }

        isConnecting = true;
        new Thread(() -> {
            try {
                BluetoothDevice device = findDeviceByName(BLUETOOTH_NAME);
                
                if (device == null) {
                    isConnecting = false;
                    notifyError("未找到设备: " + BLUETOOTH_NAME);
                    scheduleAutoReconnect();
                    return;
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                
                bluetoothAdapter.cancelDiscovery();
                
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;
                isConnecting = false;
                cancelAutoReconnect();

                Log.d(TAG, "蓝牙连接成功: " + device.getName());
                notifyConnected();
                startReceiveLoop();

                startConnectionWatchdog();
                
            } catch (IOException e) {
                isConnecting = false;
                Log.e(TAG, "蓝牙连接失败", e);
                notifyError("连接失败: " + e.getMessage());
                cleanupSocket();
                scheduleAutoReconnect();
            }
        }).start();
    }

    /**
     * 用户手动点击“连接”：恢复自动连接并立即尝试连接
     */
    public void manualConnect() {
        shouldAutoConnect = true;
        cancelAutoReconnect();
        connectToDevice();
    }

    /**
     * 用户手动点击“连接”但蓝牙尚未开启：仅记录用户意图，
     * 等系统“开启蓝牙”弹窗同意后续接连接
     */
    public void markManualConnectRequested() {
        shouldAutoConnect = true;
        cancelAutoReconnect();
    }

    /**
     * 用户手动点击“断开连接”：关闭连接且不再自动重连，直到用户再次手动连接
     */
    public void manualDisconnect() {
        shouldAutoConnect = false;
        cancelAutoReconnect();
        disconnect();
    }

    private void scheduleAutoReconnect() {
        if (!shouldAutoConnect || isConnected || isConnecting) {
            return;
        }
        if (!isBluetoothAvailable() || !isBluetoothEnabled() || !hasBluetoothConnectPermission()) {
            return;
        }
        reconnectHandler.removeCallbacks(reconnectRunnable);
        reconnectHandler.postDelayed(reconnectRunnable, AUTO_RECONNECT_DELAY_MS);
        Log.d(TAG, "将在 " + AUTO_RECONNECT_DELAY_MS + "ms 后自动重连");
    }

    private void cancelAutoReconnect() {
        reconnectHandler.removeCallbacks(reconnectRunnable);
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice findDeviceByName(String name) {
        if (!hasBluetoothConnectPermission()) {
            return null;
        }
        
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        
        for (BluetoothDevice device : pairedDevices) {
            if (name.equals(device.getName())) {
                return device;
            }
        }
        
        return null;
    }

    public void disconnect() {
        cleanupSocket();
        cancelAutoReconnect();
        notifyDisconnected();
        Log.d(TAG, "蓝牙已断开");
    }

    /**
     * 意外断开（链路中断/发送失败/看门狗检测）：清理后按 shouldAutoConnect 自动重连
     */
    private void onUnexpectedDisconnect() {
        boolean wasConnected = isConnected;
        cleanupSocket();
        if (wasConnected) {
            notifyDisconnected();
        }
        scheduleAutoReconnect();
    }

    private void cleanupSocket() {
        isConnected = false;
        isConnecting = false;
        // 先标记主动关闭，接收线程退出时不再触发意外断开重连
        receiving = false;
        // 中断歌词分块流线程
        cancelLyricStream();

        synchronized (writeLock) {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭输出流失败", e);
                }
                outputStream = null;
            }
        }

        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭输入流失败", e);
            }
            inputStream = null;
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭蓝牙socket失败", e);
            }
            bluetoothSocket = null;
        }
    }

    /**
     * 发送一帧音频数据：当前颜色 + 16 个频段电平(0~255)。
     * 帧格式：'$' + RRGGBB + 32个十六进制字符 + '\n'，共 40 字节。
     */
    public void sendAudioFrame(int r, int g, int b, byte[] levels) {
        if (!isConnected || outputStream == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSendTime < SEND_INTERVAL_MS) {
            return;
        }
        lastSendTime = now;

        int bandCount = Math.min(AUDIO_BAND_COUNT, levels == null ? 0 : levels.length);
        StringBuilder sb = new StringBuilder(1 + 6 + AUDIO_BAND_COUNT * 2 + 1);
        sb.append('$');
        appendHex(sb, Math.max(0, Math.min(255, r)));
        appendHex(sb, Math.max(0, Math.min(255, g)));
        appendHex(sb, Math.max(0, Math.min(255, b)));
        for (int i = 0; i < bandCount; i++) {
            appendHex(sb, levels[i] & 0xFF);
        }
        // 电平数组不足约定长度时补 0，保证帧长固定
        for (int i = bandCount; i < AUDIO_BAND_COUNT; i++) {
            sb.append("00");
        }
        sb.append('\n');

        byte[] audioBytes = sb.toString().getBytes();
        try {
            synchronized (writeLock) {
                if (outputStream == null) {
                    return;
                }
                outputStream.write(audioBytes);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "发送数据失败", e);
            onUnexpectedDisconnect();
        }
    }

    private static void appendHex(StringBuilder sb, int value) {
        sb.append(HEX_CHARS[(value >> 4) & 0x0F]);
        sb.append(HEX_CHARS[value & 0x0F]);
    }

    /**
     * 通知固件开始/停止上行硬件麦克风 PCM（M1/M0 命令）
     */
    public void setMicMonitoring(boolean enable) {
        writeTextFrame(enable ? "M1\n" : "M0\n");
    }

    // 固件文字位图帧（歌名 A5 5B / 歌词 A5 5C）参数上限，需与固件 bluetooth.cpp 一致
    private static final int TITLE_FRAME_MAGIC0 = 0xA5;
    private static final int TITLE_MAGIC_TITLE = 0x5B;
    private static final int TITLE_MAGIC_LYRIC = 0x5C;
    private static final int TITLE_MAX_W = 512;
    private static final int TITLE_MAX_H = 24;

    /** 下发当前歌名 1bpp 位图（width=0/mask=null 表示清除）。 */
    public void sendTitleMask(byte[] mask, int width, int height) {
        sendTextBitmap(TITLE_MAGIC_TITLE, mask, width, height);
    }

    /** 下发当前歌词行 1bpp 位图（width=0/mask=null 表示清除），仅歌词文本变化时由上层调用。 */
    public void sendLyricMask(byte[] mask, int width, int height) {
        // 歌词改用分块流式发送，见 startLyricStream / cancelLyricStream
        startLyricStream(mask, width, height);
    }

    // ---- 歌词分块流式发送 ----
    // App 把 1bpp mask 切成 200B 块，每 250ms 发一块；
    // 发完后从 reset 帧开始循环；歌词变化时调 cancelLyricStream 中断旧流、发新流。
    // 固件收到 reset 清空缓冲并 ver++，收到 data 块写入 offset 处并标 dirty 触发重绘。
    private volatile int lyricStreamId = 0;
    private Thread lyricThread = null;
    private static final int LYRIC_CHUNK_SIZE = 200;
    private static final int LYRIC_CHUNK_INTERVAL_MS = 250;

    public void startLyricStream(byte[] mask, int width, int height) {
        final int myId = ++lyricStreamId;
        if (lyricThread != null) {
            lyricThread.interrupt();
        }
        if (!isConnected || outputStream == null) {
            return;
        }
        if (mask == null || width == 0) {
            sendLyricClearFrame();
            return;
        }

        final byte[] myMask = mask;
        final int myWidth = width;
        final int myHeight = height;
        final int totalBytes = ((width + 7) / 8) * height;

        lyricThread = new Thread(() -> {
            while (myId == lyricStreamId && isConnected) {
                // reset 帧：A5 5C 00 wH wL H xor8
                sendLyricResetFrame(myWidth, myHeight);
                // 分块发 data
                for (int off = 0; off < totalBytes; off += LYRIC_CHUNK_SIZE) {
                    if (myId != lyricStreamId || !isConnected) return;
                    int len = Math.min(LYRIC_CHUNK_SIZE, totalBytes - off);
                    sendLyricDataFrame(off, myMask, len);
                    try {
                        Thread.sleep(LYRIC_CHUNK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "LyricStream");
        lyricThread.setDaemon(true);
        lyricThread.start();
    }

    public void cancelLyricStream() {
        lyricStreamId++;
        if (lyricThread != null) {
            lyricThread.interrupt();
            lyricThread = null;
        }
    }

    private void sendLyricResetFrame(int width, int height) {
        byte[] frame = new byte[7];
        frame[0] = (byte) TITLE_FRAME_MAGIC0;
        frame[1] = (byte) TITLE_MAGIC_LYRIC;
        frame[2] = 0x00;
        frame[3] = (byte) ((width >> 8) & 0xFF);
        frame[4] = (byte) (width & 0xFF);
        frame[5] = (byte) height;
        int xor = 0;
        for (int i = 0; i < 6; i++) xor ^= frame[i] & 0xFF;
        frame[6] = (byte) xor;
        writeFrameLocked(frame);
    }

    private void sendLyricDataFrame(int offset, byte[] mask, int len) {
        byte[] frame = new byte[7 + len];
        frame[0] = (byte) TITLE_FRAME_MAGIC0;
        frame[1] = (byte) TITLE_MAGIC_LYRIC;
        frame[2] = 0x01;
        frame[3] = (byte) ((offset >> 8) & 0xFF);
        frame[4] = (byte) (offset & 0xFF);
        frame[5] = (byte) len;
        System.arraycopy(mask, offset, frame, 6, len);
        int xor = 0;
        for (int i = 0; i < 6 + len; i++) xor ^= frame[i] & 0xFF;
        frame[6 + len] = (byte) xor;
        writeFrameLocked(frame);
    }

    private void sendLyricClearFrame() {
        byte[] frame = new byte[4];
        frame[0] = (byte) TITLE_FRAME_MAGIC0;
        frame[1] = (byte) TITLE_MAGIC_LYRIC;
        frame[2] = 0x02;
        frame[3] = (byte) (TITLE_FRAME_MAGIC0 ^ TITLE_MAGIC_LYRIC ^ 0x02);
        writeFrameLocked(frame);
    }

    private void writeFrameLocked(byte[] frame) {
        synchronized (writeLock) {
            if (outputStream == null) return;
            try {
                outputStream.write(frame);
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Lyric frame write failed", e);
                onUnexpectedDisconnect();
            }
        }
    }

    /**
     * 下发一行文字的 1bpp 位图（App 用系统字体渲染，天然支持中文）。
     * mask 行主序、每行 (w+7)/8 字节、MSB 对应最左像素；
     * 帧格式：A5 magic1 wH wL H payload xor8。
     */
    private void sendTextBitmap(int magic1, byte[] mask, int width, int height) {
        if (!isConnected || outputStream == null) {
            return;
        }
        if (width < 0 || width > TITLE_MAX_W || height <= 0 || height > TITLE_MAX_H) {
            Log.w(TAG, "歌名位图参数非法 w=" + width + " h=" + height);
            return;
        }
        int stride = (width + 7) / 8;
        int payload = stride * height;
        if (mask != null && mask.length < payload) {
            Log.w(TAG, "歌名位图数据不足: " + mask.length + " < " + payload);
            return;
        }

        byte[] frame = new byte[5 + payload + 1];
        frame[0] = (byte) TITLE_FRAME_MAGIC0;
        frame[1] = (byte) magic1;
        frame[2] = (byte) ((width >> 8) & 0xFF);
        frame[3] = (byte) (width & 0xFF);
        frame[4] = (byte) height;
        if (mask != null) {
            System.arraycopy(mask, 0, frame, 5, payload);
        }
        int xor = 0;
        for (int i = 0; i < 5 + payload; i++) {
            xor ^= frame[i] & 0xFF;
        }
        frame[frame.length - 1] = (byte) xor;

        try {
            // ESP32 SPP RX 环形缓冲仅 ~256 字节，长帧（歌词最长 ~450 字节）
            // 必须分块写入，每块 ≤200 字节间隔 20ms，给固件 bluetooth_update()
            // 时间排空缓冲。持锁保证整帧不被 20fps 音频帧插入。
            synchronized (writeLock) {
                if (outputStream == null) {
                    return;
                }
                int chunkSize = 200;
                for (int off = 0; off < frame.length; off += chunkSize) {
                    int len = Math.min(chunkSize, frame.length - off);
                    outputStream.write(frame, off, len);
                    outputStream.flush();
                    if (off + len < frame.length) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "发送文字位图失败", e);
            onUnexpectedDisconnect();
        }
    }

    /**
     * 下发播放进度（P 帧，单位秒），总时长 0 表示清除屏幕进度。
     */
    public void sendMediaProgress(int positionSec, int durationSec) {
        writeTextFrame("P" + positionSec + "," + durationSec + "\n");
    }

    /**
     * 文本命令帧统一发送出口：失败按意外断开处理并触发自动重连。
     * 与音频帧不同，这类帧不做 50ms 节流（歌名/进度变化频率很低）。
     */
    private void writeTextFrame(String frame) {
        if (!isConnected || outputStream == null) {
            return;
        }
        byte[] textBytes = frame.getBytes();
        try {
            synchronized (writeLock) {
                if (outputStream == null) {
                    return;
                }
                outputStream.write(textBytes);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "发送文本帧失败: " + frame, e);
            onUnexpectedDisconnect();
        }
    }

    /**
     * 固件上行接收线程：同一 SPP 字节流中混合了
     *  - 文本帧（'S' 温湿度、'CONNECTED' 等），以 '\n' 结束
     *  - 二进制 PCM 帧：A5 5A seq lenH lenL payload xor8
     */
    private void startReceiveLoop() {
        receiving = true;
        receiveThread = new Thread(() -> {
            InputStream localIn = new BufferedInputStream(inputStream, 4096);
            StringBuilder line = new StringBuilder(64);
            byte[] hdr = new byte[3];
            byte[] payload = new byte[PCM_PAYLOAD_MAX];
            int state = 0; // 0=文本 1=等第二魔数 2=收头部 3=收载荷 4=收校验
            int hdrIdx = 0;
            int payloadLen = 0;
            int payloadIdx = 0;
            int xor = 0;

            try {
                int b;
                while (receiving && (b = localIn.read()) >= 0) {
                    switch (state) {
                        case 0:
                            if (b == PCM_FRAME_MAGIC0) {
                                state = 1;
                            } else if (b == '\n') {
                                if (line.length() > 0) {
                                    handleUplinkText(line.toString());
                                }
                                line.setLength(0);
                            } else if (b != '\r' && line.length() < 64) {
                                line.append((char) b);
                            }
                            break;
                        case 1:
                            if (b == PCM_FRAME_MAGIC1) {
                                state = 2;
                                hdrIdx = 0;
                            } else {
                                state = 0;
                            }
                            break;
                        case 2:
                            hdr[hdrIdx++] = (byte) b;
                            if (hdrIdx == hdr.length) {
                                payloadLen = ((hdr[1] & 0xFF) << 8) | (hdr[2] & 0xFF);
                                xor = (hdr[0] & 0xFF) ^ (hdr[1] & 0xFF) ^ (hdr[2] & 0xFF);
                                if (payloadLen <= 0 || payloadLen > payload.length) {
                                    Log.w(TAG, "PCM帧长度异常: " + payloadLen);
                                    state = 0;
                                } else {
                                    state = 3;
                                    payloadIdx = 0;
                                }
                            }
                            break;
                        case 3:
                            payload[payloadIdx++] = (byte) b;
                            xor ^= (b & 0xFF);
                            if (payloadIdx == payloadLen) {
                                state = 4;
                            }
                            break;
                        case 4:
                            state = 0;
                            if ((b & 0xFF) == (xor & 0xFF)) {
                                byte[] copy = new byte[payloadLen];
                                System.arraycopy(payload, 0, copy, 0, payloadLen);
                                notifyMicPcm(copy);
                            }
                            break;
                        default:
                            state = 0;
                            break;
                    }
                }
            } catch (IOException e) {
                if (receiving) {
                    Log.e(TAG, "蓝牙接收线程异常", e);
                }
            }

            if (receiving) {
                // 非主动关闭导致的接收结束，视为链路断开
                onUnexpectedDisconnect();
            }
        }, "bt-rx");
        receiveThread.start();
    }

    private void handleUplinkText(String text) {
        // 温湿度帧：S<temp>,<humid>
        if (text.startsWith("S")) {
            String body = text.substring(1);
            int comma = body.indexOf(',');
            if (comma > 0) {
                try {
                    double temp = Double.parseDouble(body.substring(0, comma));
                    double humid = Double.parseDouble(body.substring(comma + 1));
                    notifySensor(temp, humid);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "温湿度帧解析失败: " + text);
                }
            }
        }
        // "CONNECTED" 等其它文本忽略
    }

    private void notifySensor(double temp, double humid) {
        if (listener != null) {
            ((android.app.Activity) context).runOnUiThread(() ->
                    listener.onSensorData(temp, humid));
        }
    }

    private void notifyMicPcm(byte[] pcm) {
        if (listener != null) {
            // 直接在接收线程回调，接收方入队即可，避免线程切换造成延迟
            listener.onMicPcm(pcm, pcm.length);
        }
    }

    private void startConnectionWatchdog() {
        new Thread(() -> {
            while (isConnected && bluetoothSocket != null) {
                try {
                    Thread.sleep(1000);
                    if (!bluetoothSocket.isConnected()) {
                        onUnexpectedDisconnect();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "连接检测失败", e);
                    onUnexpectedDisconnect();
                    break;
                }
            }
        }).start();
    }

    private void notifyConnected() {
        if (listener != null) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                listener.onBluetoothConnected();
            });
        }
    }

    private void notifyDisconnected() {
        if (listener != null) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                listener.onBluetoothDisconnected();
            });
        }
    }

    private void notifyError(String message) {
        if (listener != null) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                listener.onBluetoothError(message);
            });
        }
    }
}
