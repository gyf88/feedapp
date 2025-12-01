package com.example.feedapp.exposure;
/**
 * ExposureEvent 表示「一次曝光状态变化」：
 *
 * 例如：
 * - 某条卡片 id=xxx 第一次进入屏幕，从不可见 -> ENTER；
 * - 滚动时，它从 30% 露出 -> 70% 露出：ENTER -> HALF；
 * - 完全露出：HALF -> FULL；
 * - 滚出屏幕：FULL -> EXIT。
 *
 * 每次阶段变化，我们都会生成一条 ExposureEvent，交给 ExposureLogger，
 * 既可以写日志，也可以给曝光测试浮窗实时展示。
 */
public class ExposureEvent {
    /** 卡片 id（来自 FeedCard.getId()） */
    private final String cardId;
    /** 当前事件发生时，这条卡片在 Adapter 中的位置（position） */
    private final int position;
    /** 曝光阶段（ENTER / HALF / FULL / EXIT） */
    private final ExposureStage stage;
    /** 事件发生时间（毫秒时间戳） */
    private final long timestamp;

    /** 卡片标题（来自 FeedCard.getTitle()） */
    private final String title;
    /** 卡片类型（文字 / 图片 / 视频，对应 FeedCard.TYPE_xxx） */
    private final int cardType;

    /**
     * 构造函数：
     *
     * @param cardId    卡片唯一 id
     * @param position  当前 Adapter position（对于 EXIT 事件，可能是 -1，表示已经不可见）
     * @param stage     曝光阶段
     * @param timestamp 事件时间戳（System.currentTimeMillis()）
     * @param title     卡片标题（可为空）
     * @param cardType  卡片类型（FeedCard.TYPE_TEXT / IMAGE / VIDEO；EXIT 时填 -1 也可以）
     */
    public ExposureEvent(String cardId,
                         int position,
                         ExposureStage stage,
                         long timestamp,
                         String title,
                         int cardType) {
        this.cardId = cardId;
        this.position = position;
        this.stage = stage;
        this.timestamp = timestamp;
        this.title = title;
        this.cardType = cardType;
    }
    // -------------------- Getter --------------------
    public String getCardId() {
        return cardId;
    }

    public int getPosition() {
        return position;
    }

    public ExposureStage getStage() {
        return stage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTitle() {
        return title;
    }

    public int getCardType() {
        return cardType;
    }
}
