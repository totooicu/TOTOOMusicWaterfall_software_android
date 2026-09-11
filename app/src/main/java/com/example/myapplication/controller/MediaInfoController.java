package com.example.myapplication.controller;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.example.myapplication.service.MediaSessionListener;
import com.example.myapplication.tool.AudioAnalyzer;

/**
 * 每 0.5s 汇总“当前正在播放什么”，并通过蓝牙下发给硬件屏幕：
 * 1. 优先：其他音乐 App 的活动 MediaSession（需通知使用权，系统音频捕获场景）；
 * 2. 其次：本 App 内选中的本地音频（文件名 + MediaPlayer 进度，无歌词来源）；
 * 3. 都没有：清空屏幕上的歌名/歌词/进度。
 *
 * 屏幕右半区三行：歌名、歌词、进度。歌名/歌词由手机系统字体渲染成 1bpp
 * 位图（A5 5B / A5 5C 帧），支持中文等任意字形，固件无需中文字库；
 * 歌词还会在播放器通知更新（唱到新词）后的下一个 0.5s tick 内发出。
 */
public class MediaInfoController {

    private static final long TICK_INTERVAL_MS = 500;

    // 位图几何，必须与固件 bluetooth.cpp/app_data_display.cpp 一致
    public static final int TITLE_H = 20;
    private static final int TITLE_MAX_W = 512;
    // 屏幕右半区可见宽度 172px，歌词尽量缩字号在一屏内完整显示
    private static final int LYRIC_FIT_W = 168;
    private static final int TEXT_SIZE = 15;
    private static final int MIN_TEXT_SIZE = 10;
    private static final int PADDING_X = 2;
    private static final int ALPHA_THRESHOLD = 120;

    private final Context context;
    private final BluetoothController bluetoothController;
    private final AudioAnalyzer audioAnalyzer;
    private final AudioController audioController;
    private final HeaderController headerController;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tickRunnable = this::tick;
    private boolean running = false;
    // 已下发内容缓存：变化才重发（一句歌词只发一次，约 0.2~1.3KB）
    private String sentTitle = null;
    private String sentLyric = null;
    private String sentProgress = null;
    private boolean accessPromptShown = false;

    public MediaInfoController(Context context, BluetoothController bluetoothController,
                               AudioAnalyzer audioAnalyzer, AudioController audioController,
                               HeaderController headerController) {
        this.context = context;
        this.bluetoothController = bluetoothController;
        this.audioAnalyzer = audioAnalyzer;
        this.audioController = audioController;
        this.headerController = headerController;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        sentTitle = null;
        sentLyric = null;
        sentProgress = null;
        handler.post(tickRunnable);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tickRunnable);
        bluetoothController.cancelLyricStream();
        // 下次连接后强制全量重发
        sentTitle = null;
        sentLyric = null;
        sentProgress = null;
    }

    private void tick() {
        if (!running) {
            return;
        }
        try {
            String title = "";
            String lyric = "";
            int positionSec = 0;
            int durationSec = 0;

            MediaSessionListener.MediaInfo external = MediaSessionListener.getCurrent();
            if (external != null && external.playing
                    && !context.getPackageName().equals(external.packageName)
                    && external.durationMs > 0) {
                // 后台其他音乐 App 正在播放
                title = cleanText(external.title);
                String lyricRaw = MediaSessionListener.getLyric(external.packageName, external.title);
                lyric = lyricRaw != null ? cleanText(lyricRaw) : "";
                // 进度在读取时按播放速度实时外推，播放器不高频回调也能每秒跳动
                positionSec = (int) (external.getPositionMs() / 1000);
                durationSec = (int) (external.durationMs / 1000);
            } else if (audioAnalyzer.getDuration() > 0) {
                // 本 App 本地音频（含暂停态，暂停时进度停在最后位置），无歌词来源
                title = cleanText(audioController.getLocalTitle());
                positionSec = audioAnalyzer.getCurrentPosition() / 1000;
                durationSec = audioAnalyzer.getDuration() / 1000;
            }

            if (!title.equals(sentTitle)) {
                bluetoothController.sendTitleMask(renderMask(title, TITLE_MAX_W),
                        lastMaskWidth, TITLE_H);
                sentTitle = title;
            }
            if (!lyric.equals(sentLyric)) {
                byte[] mask = renderMask(lyric, LYRIC_FIT_W);
                if (mask != null) {
                    bluetoothController.startLyricStream(mask, lastMaskWidth, TITLE_H);
                } else {
                    // 空歌词：发清除帧并中断旧流
                    bluetoothController.cancelLyricStream();
                    bluetoothController.startLyricStream(null, 0, 0);
                }
                sentLyric = lyric;
            }
            String progress = positionSec + "," + durationSec;
            if (!progress.equals(sentProgress)) {
                bluetoothController.sendMediaProgress(positionSec, durationSec);
                sentProgress = progress;
            }
        } finally {
            handler.postDelayed(tickRunnable, TICK_INTERVAL_MS);
        }
    }

    // renderMask 输出宽度（跨方法传递，避免每帧重复量宽）
    private int lastMaskWidth = 0;

    /**
     * 用系统默认字体把文字渲染为 1bpp mask（行主序 MSB 在左）。
     * fitWidth 以内优先缩小字号完整显示；仍超宽则截断到 512px（固件会滚动）。
     * 空文本返回 null（配合 w=0 作为清除帧）。
     */
    private byte[] renderMask(String text, int fitWidth) {
        lastMaskWidth = 0;
        if (text == null || text.isEmpty()) {
            return null;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.WHITE);
        paint.setTextSize(TEXT_SIZE);

        float measured = paint.measureText(text);
        while (measured > fitWidth - PADDING_X * 2 && paint.getTextSize() > MIN_TEXT_SIZE) {
            paint.setTextSize(paint.getTextSize() - 1);
            measured = paint.measureText(text);
        }
        int width = (int) Math.ceil(measured) + PADDING_X * 2;
        if (width > TITLE_MAX_W) {
            width = TITLE_MAX_W;
        }
        lastMaskWidth = width;

        Bitmap bmp = Bitmap.createBitmap(width, TITLE_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint.FontMetrics fm = paint.getFontMetrics();
        // 20px 条带内垂直居中
        float baseline = (TITLE_H - fm.ascent - fm.descent) / 2f;
        canvas.drawText(text, PADDING_X, baseline, paint);

        int stride = (width + 7) / 8;
        byte[] mask = new byte[stride * TITLE_H];
        int[] pixels = new int[width * TITLE_H];
        bmp.getPixels(pixels, 0, width, 0, 0, width, TITLE_H);
        for (int y = 0; y < TITLE_H; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = pixels[y * width + x] >>> 24;
                if (alpha >= ALPHA_THRESHOLD) {
                    mask[y * stride + (x >> 3)] |= (byte) (0x80 >> (x & 7));
                }
            }
        }
        bmp.recycle();
        return mask;
    }

    /**
     * 清理文本：保留中文等任意字符（固件显示的是位图，与字符集无关），
     * 只折叠空白、去掉换行，避免破坏位图排版。
     */
    private static String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = raw.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
        // 防御性截断，避免极端长文本的渲染开销
        return collapsed.length() > 80 ? collapsed.substring(0, 80).trim() : collapsed;
    }

    /**
     * 未授予通知使用权时引导用户去系统设置开启（每次运行只提示一次；
     * 不授予也不影响本 App 本地文件名/进度的下发）。
     * 注意：歌词还需要在音乐播放器自身设置里打开“通知栏歌词/桌面歌词”。
     */
    public void promptNotificationAccessIfNeeded() {
        if (accessPromptShown || MediaSessionListener.isNotificationAccessEnabled(context)) {
            return;
        }
        accessPromptShown = true;
        if (headerController != null) {
            headerController.setStatus("后台歌名/歌词需授予“通知使用权”", "error");
        }
        try {
            context.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
