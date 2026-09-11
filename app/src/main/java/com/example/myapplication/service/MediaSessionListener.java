package com.example.myapplication.service;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通过通知使用权读取其他音乐 App 的 MediaSession：
 * 正在播放的歌名、总时长与实时进度（网易云/QQ音乐/系统播放器等走标准
 * MediaSession 的应用均可）。需要用户在系统设置中授予“通知使用权”。
 */
public class MediaSessionListener extends NotificationListenerService {

    private static final String TAG = "MediaSessionListener";

    /** 某个播放会话在某一时刻的快照 */
    public static class MediaInfo {
        public final String packageName;
        public final String title;
        public final long durationMs;
        public final long positionMs;
        public final boolean playing;

        MediaInfo(String packageName, String title, long durationMs, long positionMs, boolean playing) {
            this.packageName = packageName;
            this.title = title;
            this.durationMs = durationMs;
            this.positionMs = positionMs;
            this.playing = playing;
        }
    }

    private static volatile boolean connected = false;
    private static volatile MediaInfo current = null;

    // ---- 歌词捕获 ----
    // 主流播放器（网易云/QQ音乐等）的歌词走“通知栏歌词”：一条独立于 MediaStyle
    // 播放控制通知的普通通知，唱到新词就高频更新。这里记录这些通知的最新文本，
    // getLyric() 取最近更新者；trusted 来源（session extras）不受更新次数限制。
    private static final class LyricCandidate {
        final String pkg;
        String text;
        int updates;
        long firstMs;
        long lastMs;
        final boolean trusted;

        LyricCandidate(String pkg, String text, long now, boolean trusted) {
            this.pkg = pkg;
            this.text = text;
            this.updates = 1;
            this.firstMs = now;
            this.lastMs = now;
            this.trusted = trusted;
        }
    }

    private static final Object LYRIC_LOCK = new Object();
    private static final Map<String, LyricCandidate> lyricCandidates = new HashMap<>();
    private static final long LYRIC_FRESH_MS = 15_000;

    /** 活跃媒体 App 包名（只把这些 App 的非媒体通知当作歌词候选，防误报） */
    private static volatile Set<String> activeMediaPackages = new HashSet<>();

    private final List<MediaController> controllers = new ArrayList<>();
    private MediaSessionManager sessionManager;
    private MediaController.Callback controllerCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;

    public static boolean isReady() {
        return connected;
    }

    public static MediaInfo getCurrent() {
        return current;
    }

    /**
     * 返回指定播放器当前歌词行；无歌词返回 null。
     * 依赖播放器开启“通知栏歌词/桌面歌词”（在对应播放器设置里打开）。
     */
    public static String getLyric(String pkg, String currentTitle) {
        if (pkg == null) {
            return null;
        }
        long now = SystemClock.elapsedRealtime();
        LyricCandidate best = null;
        synchronized (LYRIC_LOCK) {
            Iterator<Map.Entry<String, LyricCandidate>> it = lyricCandidates.entrySet().iterator();
            while (it.hasNext()) {
                LyricCandidate c = it.next().getValue();
                if (now - c.lastMs > LYRIC_FRESH_MS) {
                    it.remove();
                    continue;
                }
                if (!pkg.equals(c.pkg)) {
                    continue;
                }
                if (!isPlausibleLyric(c.text, currentTitle)) {
                    continue;
                }
                // 普通通知须短时间内更新过 2 次以上（歌词通知唱到新词会持续刷新），
                // 首次出现且含中文的歌词句在 8s 宽限期内也采信，避免漏掉第一句；
                // session extras 来源标记 trusted，直接采信。
                boolean freqOk = c.trusted
                        || c.updates >= 2
                        || (c.updates >= 1 && now - c.firstMs < 8_000 && hasCjk(c.text));
                if (!freqOk) {
                    continue;
                }
                if (best == null || c.lastMs > best.lastMs) {
                    best = c;
                }
            }
        }
        return best == null ? null : best.text;
    }

    private static boolean isPlausibleLyric(String text, String currentTitle) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        int len = t.length();
        if (len < 2 || len > 60) {
            return false;
        }
        if (t.contains("http") || t.contains("www.") || t.contains("://")) {
            return false;
        }
        if (currentTitle != null && t.equals(currentTitle.trim())) {
            return false;
        }
        return true;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private static void putLyricCandidate(String key, String pkg, String text, boolean trusted) {
        String clean = text.trim();
        long now = SystemClock.elapsedRealtime();
        synchronized (LYRIC_LOCK) {
            LyricCandidate c = lyricCandidates.get(key);
            if (c == null) {
                lyricCandidates.put(key, new LyricCandidate(pkg, clean, now, trusted));
            } else {
                if (!clean.equals(c.text)) {
                    c.text = clean;
                    c.updates++;
                }
                c.lastMs = now;
            }
        }
    }

    /** 系统“通知使用权”列表中是否已勾选本应用 */
    public static boolean isNotificationAccessEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        String self = context.getPackageName();
        for (String entry : flat.split(":")) {
            if (entry.trim().startsWith(self + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        sessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            Log.w(TAG, "设备不支持 MediaSessionManager");
            return;
        }

        controllerCallback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                rebuildSnapshot();
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                rebuildSnapshot();
            }

            @Override
            public void onSessionDestroyed() {
                rebuildSnapshot();
            }
        };

        sessionsListener = this::bindSessions;

        ComponentName component = new ComponentName(this, MediaSessionListener.class);
        try {
            bindSessions(sessionManager.getActiveSessions(component));
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component);
        } catch (SecurityException e) {
            Log.w(TAG, "尚未取得通知使用权，无法读取活动会话", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        current = null;
        synchronized (LYRIC_LOCK) {
            lyricCandidates.clear();
        }
        activeMediaPackages = new HashSet<>();
        if (sessionManager != null && sessionsListener != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
        }
        unbindAll();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 部分播放器发媒体通知后才注册会话，收到通知时主动刷新一次会话列表
        if (sessionManager != null) {
            try {
                ComponentName component = new ComponentName(this, MediaSessionListener.class);
                bindSessions(sessionManager.getActiveSessions(component));
            } catch (SecurityException ignored) {
            }
        }
        captureLyricNotification(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (LYRIC_LOCK) {
            lyricCandidates.remove(sbn.getKey());
        }
    }

    /**
     * 识别播放器的“通知栏歌词”通知：必须来自活跃媒体 App，且不是 MediaStyle
     * 播放控制通知本身（那条里是歌名/歌手）。歌词文本可能位于 text/title/bigText。
     */
    private void captureLyricNotification(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName()) || !activeMediaPackages.contains(pkg)) {
            return;
        }
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) {
            return;
        }
        // 排除媒体播放控制通知
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)
                || n.extras.get("android.mediaSession") != null) {
            return;
        }
        // 前台常驻服务通知一般是播放控制；歌词通知通常也是前台，故不能以此排除
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (!isCandidateText(text)) {
            text = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        }
        if (!isCandidateText(text)) {
            text = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }
        if (isCandidateText(text)) {
            putLyricCandidate(sbn.getKey(), pkg, text.toString(), false);
        }
    }

    private static boolean isCandidateText(CharSequence cs) {
        if (cs == null) {
            return false;
        }
        String s = cs.toString().trim();
        return s.length() >= 2 && s.length() <= 60;
    }

    private synchronized void bindSessions(List<MediaController> active) {
        unbindAll();
        Set<String> pkgs = new HashSet<>();
        if (active != null) {
            for (MediaController controller : active) {
                controllers.add(controller);
                pkgs.add(controller.getPackageName());
                try {
                    controller.registerCallback(controllerCallback);
                } catch (Exception ignored) {
                }
            }
        }
        activeMediaPackages = pkgs;
        rebuildSnapshot();
    }

    private void unbindAll() {
        for (MediaController controller : controllers) {
            try {
                controller.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {
            }
        }
        controllers.clear();
    }

    /** 优先选正在播放且有时长的会话，其次选暂停的会话 */
    private synchronized void rebuildSnapshot() {
        MediaController paused = null;
        for (MediaController controller : controllers) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) {
                continue;
            }
            long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (duration <= 0) {
                continue;
            }
            boolean playing = isPlaying(controller.getPlaybackState());
            if (playing) {
                current = buildInfo(controller, metadata, duration, true);
                return;
            }
            if (paused == null) {
                paused = controller;
            }
        }
        if (paused != null) {
            current = buildInfo(paused, paused.getMetadata(),
                    paused.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION), false);
        } else {
            current = null;
        }
    }

    private boolean isPlaying(PlaybackState state) {
        if (state == null) {
            return false;
        }
        return state.getState() == PlaybackState.STATE_PLAYING;
    }

    private MediaInfo buildInfo(MediaController controller, MediaMetadata metadata,
                                long durationMs, boolean playing) {
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        PlaybackState state = controller.getPlaybackState();
        long positionMs = 0;
        if (state != null && state.getPosition() >= 0) {
            positionMs = state.getPosition();
            if (playing) {
                // PlaybackState 的 position 是 lastUpdateTime 时刻的值，按速度外推到现在
                long elapsed = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
                float speed = state.getPlaybackSpeed();
                if (!Float.isNaN(speed) && speed > 0) {
                    positionMs += (long) (elapsed * speed);
                }
            }
        }
        if (positionMs > durationMs) {
            positionMs = durationMs;
        }
        // 少数播放器（如部分系统音乐、厂商音乐）把当前歌词放在 session 自定义 extras 里，
        // 作为通知栏歌词之外的兜底来源，标记 trusted 直接采信
        String sessionLyric = extractSessionLyric(controller, metadata);
        if (sessionLyric != null) {
            putLyricCandidate(controller.getPackageName() + "|session",
                    controller.getPackageName(), sessionLyric, true);
        }
        return new MediaInfo(controller.getPackageName(),
                title == null ? "" : title, durationMs, positionMs, playing);
    }

    /** 从 PlaybackState/MediaMetadata 的自定义字段里找键名含 lyric 的文本（非标准）。 */
    private static String extractSessionLyric(MediaController controller, MediaMetadata metadata) {
        PlaybackState state = controller.getPlaybackState();
        if (state != null) {
            Bundle b = state.getExtras();
            String v = lyricFromBundle(b);
            if (v != null) {
                return v;
            }
        }
        if (metadata != null) {
            for (String key : metadata.keySet()) {
                if (key != null && key.toLowerCase().contains("lyric")) {
                    String v = metadata.getString(key);
                    if (v == null) {
                        CharSequence cs = metadata.getText(key);
                        v = cs == null ? null : cs.toString();
                    }
                    if (v != null && v.trim().length() >= 2) {
                        return v.trim();
                    }
                }
            }
        }
        return null;
    }

    private static String lyricFromBundle(Bundle b) {
        if (b == null) {
            return null;
        }
        for (String key : b.keySet()) {
            if (key != null && key.toLowerCase().contains("lyric")) {
                Object o = b.get(key);
                if (o instanceof CharSequence) {
                    String v = o.toString().trim();
                    if (v.length() >= 2) {
                        return v;
                    }
                }
            }
        }
        return null;
    }
}
