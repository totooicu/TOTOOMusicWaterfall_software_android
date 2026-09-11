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
        /** 供屏幕第 1 行显示的歌名（已剔除被播放器塞进 title 的歌词行） */
        public final String title;
        public final long durationMs;
        public final boolean playing;
        // 进度基准：positionBaseMs 是 positionBaseElapsed 时刻（elapsedRealtime 时钟）的位置，
        // 读取时按播放速度实时外推，避免播放器不高频回调时进度冻在歌词刷新瞬间
        private final long positionBaseMs;
        private final long positionBaseElapsed;
        private final float playbackSpeed;

        MediaInfo(String packageName, String title, long durationMs,
                  long positionBaseMs, long positionBaseElapsed, float playbackSpeed,
                  boolean playing) {
            this.packageName = packageName;
            this.title = title;
            this.durationMs = durationMs;
            this.positionBaseMs = positionBaseMs;
            this.positionBaseElapsed = positionBaseElapsed;
            this.playbackSpeed = playbackSpeed;
            this.playing = playing;
        }

        /** 当前实时播放位置（ms），已按速度外推并钳制在总时长内。 */
        public long getPositionMs() {
            long p = positionBaseMs;
            if (playing && !Float.isNaN(playbackSpeed) && playbackSpeed > 0) {
                p += (long) ((SystemClock.elapsedRealtime() - positionBaseElapsed) * playbackSpeed);
            }
            if (p < 0) {
                p = 0;
            }
            if (durationMs > 0 && p > durationMs) {
                p = durationMs;
            }
            return p;
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
                // session metadata 来源标记 trusted，直接采信。
                boolean freqOk = c.trusted
                        || c.updates >= 2
                        || (c.updates >= 1 && now - c.firstMs < 8_000 && hasCjk(c.text));
                if (!freqOk) {
                    continue;
                }
                // 通知栏歌词（non-trusted）优先于 title 抖动歌词（trusted），
                // 因为通知栏歌词是播放器明确推送的歌词，更可靠
                if (best == null
                        || (!c.trusted && best.trusted)
                        || (c.trusted == best.trusted && c.lastMs > best.lastMs)) {
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

    // ---- “歌词写进 title”的播放器识别（汽水音乐/抖音等）----
    // 这类播放器不发独立歌词通知，而是把当前歌词行不断写进 MediaMetadata.TITLE，
    // 表现为播放中 title 每几秒变一次。检测到后：抖动的 title 作为歌词走 A5 5C，
    // 真正歌名取“抖动前/持续最久”的标题或播放控制通知里稳定的 EXTRA_TITLE。
    private static final long TITLE_CHURN_WINDOW_MS = 20_000;
    private static final int TITLE_CHURN_DISTINCT = 3;
    private static final long TITLE_STABLE_HOLD_MS = 12_000;

    private static final class TitleBehavior {
        // 最近 title 变化事件：text + 发生时刻
        final ArrayList<String> changeTexts = new ArrayList<>();
        final ArrayList<Long> changeTimes = new ArrayList<>();
        String currentTitle;
        long currentSinceMs;
        String stableTitle;   // 最近一个持续超过 12s 的标题（通常是真歌名）
    }

    private static final class NotifTitleState {
        String text;
        int distinct;
        long lastMs;
    }

    private static final Object TITLE_LOCK = new Object();
    private static final Map<String, TitleBehavior> titleBehaviors = new HashMap<>();
    private static final Map<String, NotifTitleState> notifTitles = new HashMap<>();

    /**
     * 观察某播放器的 metadata title，返回应在“歌名行”显示的标题；
     * 若检测到 title 正在被歌词刷屏，则把当前 title 记为歌词候选并返回真歌名。
     */
    private static String observeMetaTitle(String pkg, String metaTitle) {
        long now = SystemClock.elapsedRealtime();
        synchronized (TITLE_LOCK) {
            TitleBehavior b = titleBehaviors.get(pkg);
            if (b == null) {
                b = new TitleBehavior();
                titleBehaviors.put(pkg, b);
            }
            if (!metaTitle.equals(b.currentTitle)) {
                // 上一个标题持续了足够久：它是真歌名（前奏/间奏/停顿时可捕获）
                if (b.currentTitle != null && now - b.currentSinceMs >= TITLE_STABLE_HOLD_MS) {
                    b.stableTitle = b.currentTitle;
                }
                b.changeTexts.add(metaTitle);
                b.changeTimes.add(now);
                b.currentTitle = metaTitle;
                b.currentSinceMs = now;
                // 首个标题先当真歌名（起播时通常先上歌名，歌词几秒后才开始刷）
                if (b.stableTitle == null) {
                    b.stableTitle = metaTitle;
                }
            }

            // 清理观察窗口
            while (!b.changeTimes.isEmpty() && now - b.changeTimes.get(0) > TITLE_CHURN_WINDOW_MS) {
                b.changeTimes.remove(0);
                b.changeTexts.remove(0);
            }
            int distinct = new HashSet<>(b.changeTexts).size();
            boolean churning = distinct >= TITLE_CHURN_DISTINCT;

            if (churning) {
                // 当前 title 就是歌词行（trusted，直接采信）
                putLyricCandidate(pkg + "|metaTitle", pkg, metaTitle, true);
                String name = pickStableSongName(pkg, b);
                return name != null ? name : metaTitle;
            }
            return metaTitle;
        }
    }

    /** 抖动模式下选真歌名：播放控制通知里稳定不变的 EXTRA_TITLE 优先，否则用记住的 stableTitle。 */
    private static String pickStableSongName(String pkg, TitleBehavior b) {
        NotifTitleState n = notifTitles.get(pkg);
        if (n != null && n.text != null && n.text.trim().length() > 0
                && n.distinct <= 2 && !n.text.equals(b.currentTitle)) {
            return n.text;
        }
        if (b.stableTitle != null && !b.stableTitle.equals(b.currentTitle)) {
            return b.stableTitle;
        }
        return b.stableTitle;
    }

    /** 记录播放控制通知的 EXTRA_TITLE，用于判断它是否稳定（真歌名）。 */
    private static void observeNotificationTitle(String pkg, String title) {
        if (title == null) {
            return;
        }
        String t = title.trim();
        if (t.isEmpty() || t.length() > 80) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (TITLE_LOCK) {
            NotifTitleState n = notifTitles.get(pkg);
            if (n == null) {
                n = new NotifTitleState();
                n.text = t;
                n.distinct = 1;
                notifTitles.put(pkg, n);
            } else {
                if (!t.equals(n.text)) {
                    n.text = t;
                    n.distinct++;
                }
            }
            n.lastMs = now;
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
        synchronized (TITLE_LOCK) {
            titleBehaviors.clear();
            notifTitles.clear();
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
     * 识别播放器歌词通知，两种形态：
     * 1. 独立“通知栏歌词”通知（非 MediaStyle）：网易云/QQ音乐等，唱到新词高频更新；
     * 2. 直接更新播放控制通知（MediaStyle）的 EXTRA_TEXT：部分播放器把通知文字当歌词。
     *    此时 EXTRA_TITLE 通常是稳定歌名，记录其稳定性供“title 抖动”场景选真歌名；
     *    EXTRA_TEXT 走更新次数过滤——歌手名等静态文本因只出现 1 次不会被采信。
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
        boolean transport = Notification.CATEGORY_TRANSPORT.equals(n.category)
                || n.extras.get("android.mediaSession") != null;

        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title != null) {
            observeNotificationTitle(pkg, title.toString());
        }

        if (transport) {
            // MediaStyle 通知：只有 EXTRA_TEXT 可作歌词候选（用同一候选机制+频率过滤）
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isCandidateText(text)) {
                putLyricCandidate(pkg + "|transportText", pkg, text.toString(), false);
            }
            return;
        }

        // 独立歌词通知：依次尝试 text/title/bigText
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
        String pkg = controller.getPackageName();
        String metaTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (metaTitle == null) {
            metaTitle = "";
        }
        // 识别“歌词写进 title”的播放器：返回真歌名，当前歌词行进入歌词候选
        String displayTitle = playing ? observeMetaTitle(pkg, metaTitle) : metaTitle;

        // 位置只存基准，由 MediaInfo.getPositionMs() 在读取时按速度实时外推
        PlaybackState state = controller.getPlaybackState();
        long positionBase = 0;
        long positionElapsed = SystemClock.elapsedRealtime();
        float speed = 1f;
        if (state != null) {
            if (state.getPosition() >= 0) {
                positionBase = state.getPosition();
                positionElapsed = state.getLastPositionUpdateTime();
            }
            float s = state.getPlaybackSpeed();
            if (!Float.isNaN(s)) {
                speed = s;
            }
        }
        // 少数播放器（如部分系统音乐、厂商音乐）把当前歌词放在 session 自定义 extras 里，
        // 作为通知栏歌词之外的兜底来源，标记 trusted 直接采信
        String sessionLyric = extractSessionLyric(controller, metadata);
        if (sessionLyric != null) {
            putLyricCandidate(pkg + "|session", pkg, sessionLyric, true);
        }
        return new MediaInfo(pkg, displayTitle, durationMs,
                positionBase, positionElapsed, speed, playing);
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
