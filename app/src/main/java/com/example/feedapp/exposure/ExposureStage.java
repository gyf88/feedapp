package com.example.feedapp.exposure;

/**
 * 曝光阶段（枚举）：
 *
 * 说明：我们对「一条卡片在屏幕上的可见程度」做了一个简单离散分类：
 *
 * - ENTER：卡片有任何部分露出（曝光 > 0% 且 < 50%）
 * - HALF ：露出超过 50%（曝光 ≥ 50% 且 < 100%）
 * - FULL ：完整露出（曝光 ≥ 100%，也就是整张卡都完全在可见区域里）
 * - EXIT ：从有曝光 → 完全不可见（曝光 = 0）
 *
 * ExposureTracker 根据卡片在屏幕上的可见高度比例计算出当前阶段，
 * 如果阶段发生变化，就生成一个 ExposureEvent 并交给 ExposureLogger。
 */
public enum ExposureStage {
    ENTER,   // 卡片有任何部分露出（0 ~ 50%）
    HALF,    // 露出超过 50%（0.5 ~ <1）
    FULL,    // 完整露出（>=1）
    EXIT     // 完全不可见（从有曝光 -> 0）
}
