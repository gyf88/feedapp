package com.example.feedapp.exposure;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 简单的「曝光日志记录器」：
 *
 * 功能：
 * 1. 在内存中维护一份曝光事件的文本日志列表 LOGS；
 * 2. 支持注册监听器 Listener，当有新日志时回调给监听器（比如浮动调试面板）；
 * 3. 对外提供 getLogs() 方法，可以一次性拿到当前所有日志文本。
 *
 * 位置关系：
 * - ExposureTracker 负责“算曝光 + 生成 ExposureEvent”；
 * - ExposureLogger.log(event) 负责“把事件格式化成一行字符串 + 存储 + 通知监听器”；
 * - 浮动测试窗（你之前做的曝光测试 UI）通过 addListener(...) 实时收到新日志。
 */
public class ExposureLogger {

    /**
     * 监听器接口：
     * - 一旦有新的曝光事件被记录，会调用 onNewEvent(event, formattedString)；
     * - formatted 是我们已经格式化好的字符串，方便直接展示。
     */
    public interface Listener {
        void onNewEvent(ExposureEvent event, String formatted);
    }
    /** 内存中的日志文本列表（只保留最近若干条） */
    private static final List<String> LOGS = new ArrayList<>();
    /** 注册的监听器列表（例如你的曝光测试浮动窗） */
    private static final List<Listener> LISTENERS = new ArrayList<>();
    /** 时间格式化工具，用于在日志前面打印时间 */
    private static final SimpleDateFormat sdf =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    /**
     * 把一条曝光事件记录成日志，并通知监听器。
     *
     * @param event 曝光事件（由 ExposureTracker 构造）
     */
    public static void log(ExposureEvent event) {
        if (event == null) return;
        // 1. 先把事件转成一行好读的文本
        String time = sdf.format(new Date(event.getTimestamp()));

        String typeLabel;
        switch (event.getCardType()) {
            case 0: // FeedCard.TYPE_TEXT
                typeLabel = "TEXT";
                break;
            case 1: // FeedCard.TYPE_IMAGE
                typeLabel = "IMAGE";
                break;
            case 2: // FeedCard.TYPE_VIDEO
                typeLabel = "VIDEO";
                break;
            default:
                typeLabel = "UNKNOWN";
                break;
        }

        String title = event.getTitle();
        if (title == null) {
            title = "";
        }
        // 这里的格式可以根据自己需要调整
        String msg = String.format(
                Locale.getDefault(),
                "[%s] pos=%d type=%s title=%s stage=%s",
                time,
                event.getPosition(),
                typeLabel,
                title,
                event.getStage().name()
        );
        // 2. 存到内存日志列表里（加个简单的上限，避免无限增长）
        synchronized (LOGS) {
            LOGS.add(0, msg); // 最新在最上面
        }
        // 3. 通知所有监听器
        synchronized (LISTENERS) {
            for (Listener listener : LISTENERS) {
                listener.onNewEvent(event, msg);
            }
        }
    }

    /**
     * 获取当前已经记录的日志文本列表的一个拷贝。
     * - 返回的是 new ArrayList<>(LOGS)，避免调用方修改内部列表。
     */
    public static List<String> getAllLogs() {
        synchronized (LOGS) {
            return new ArrayList<>(LOGS);
        }
    }
    /**
     * 注册一个监听器：
     * - 一般在你的曝光测试浮窗初始化时调用；
     * - 后续每次有新曝光事件，都会回调到这个 listener。
     */
    public static void addListener(Listener listener) {
        if (listener == null) return;
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(listener)) {
                LISTENERS.add(listener);
            }
        }
    }
    /**
     * 取消注册监听器：
     * - 比如当浮动窗关闭时，调用 removeListener(this)，避免内存泄漏。
     */
    public static void removeListener(Listener listener) {
        if (listener == null) return;
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }
}
