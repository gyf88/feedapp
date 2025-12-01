package com.example.feedapp.exposure;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.ui.feed.FeedAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * ExposureTracker：
 *
 * 这是整个「曝光埋点系统」的核心：
 * - 监听 RecyclerView 的滚动、子 View attach/detach；
 * - 计算每个可见子项在屏幕中的可见比例（0 ~ 1）；
 * - 根据比例映射到四个阶段：ENTER / HALF / FULL / EXIT；
 * - 当阶段发生变化时，生成 ExposureEvent，并交给 ExposureLogger 记录。
 *
 * 使用方式（在 FeedFragment 里已经这样写了）：
 *
 *   exposureTracker = new ExposureTracker(recyclerView, adapter);
 *
 * 这样：
 * - 构造函数里会自动：
 *      recyclerView.addOnScrollListener(this);
 *      recyclerView.addOnChildAttachStateChangeListener(this);
 * - 之后每次滚动 / 布局变化都会自动触发曝光计算，不需要额外手动调用。
 */
public class ExposureTracker extends RecyclerView.OnScrollListener
        implements RecyclerView.OnChildAttachStateChangeListener {
    /** 被监听的 RecyclerView */
    private final RecyclerView recyclerView;
    /** 适配器，用来拿每个 position 对应的 FeedCard 数据（包括 id/title/type） */
    private final FeedAdapter adapter;

    /**
     * 记录“当前认为某个 cardId 的曝光阶段”：
     * - key   ：FeedCard.getId()
     * - value ：最后一次记录的 ExposureStage
     *
     * 用途：
     * - 新计算出一个阶段时，与之前的阶段比较：
     *      - 不同 -> 生成一条曝光事件（阶段变更）；
     *      - 相同 -> 不用重复打点。
     */
    private final Map<String, ExposureStage> stageMap = new HashMap<>();

    public ExposureTracker(RecyclerView recyclerView, FeedAdapter adapter) {
        this.recyclerView = recyclerView;
        this.adapter = adapter;
        // 注册自己为 RecyclerView 的滚动监听器
        this.recyclerView.addOnScrollListener(this);
        // 注册自己为 Child attach/detach 监听器（子 View 进入/离开屏幕时也会触发）
        this.recyclerView.addOnChildAttachStateChangeListener(this);
    }
    // -------------------- RecyclerView.OnScrollListener --------------------
    @Override
    public void onScrolled(RecyclerView rv, int dx, int dy) {
        super.onScrolled(rv, dx, dy);
        // 每次滚动，都重新计算一遍当前所有可见子项的曝光情况
        dispatchExposure();
    }
    // -------------------- RecyclerView.OnChildAttachStateChangeListener --------------------
    @Override
    public void onChildViewAttachedToWindow(View view) {
        // 有新的 item attach 进屏幕时，重新计算一次曝光
        dispatchExposure();
    }

    @Override
    public void onChildViewDetachedFromWindow(View view) {
        // 有 item 离开屏幕时，也重新计算一次，
        // dispatchExposure 内部会发现某些 id 不在可见集合中，从而发 EXIT 事件。
        dispatchExposure();
    }

    /**
     * 遍历当前 RecyclerView 里所有可见的 child：
     * 1. 计算每个 child 在 RecyclerView 可见区域中的高度占比；
     * 2. 根据占比映射到 ENTER / HALF / FULL；
     * 3. 与上一次记录的阶段比较，若不同则发事件 ExposureEvent；
     * 4. 对于上一次可见但这一次不在可见集合内的 cardId，发 EXIT 事件。
     */
    private void dispatchExposure() {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        // 这里加一个类型判断，方便以后换布局管理器时扩展：
        if (!(lm instanceof GridLayoutManager)) {
            // 当前项目中我们用的是 GridLayoutManager（支持单列/双列混排）
            // 其他 LayoutManager 如 LinearLayoutManager 也可以用同样思路来实现。
            // 暂时直接 return，避免出错。
            return;
        }
        // RecyclerView 在屏幕中的可见区域 Rect
        Rect rvRect = new Rect();
        recyclerView.getGlobalVisibleRect(rvRect);

        int childCount = recyclerView.getChildCount();
        long now = System.currentTimeMillis();

        // 为了检测“从可见 -> 完全不可见”，先拷贝一份之前的 stageMap 快照
        Map<String, Float> currentVisibleRatio = new HashMap<>();

        // ---------- 1. 遍历当前所有 child，计算可见比例并生成 ENTER/HALF/FULL ----------
        for (int i = 0; i < childCount; i++) {
            View child = recyclerView.getChildAt(i);
            int position = recyclerView.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;

            FeedCard card = adapter.getItemOrNull(position);
            if (card == null) continue;
            String id = card.getId();
            if (id == null) continue;

            Rect childRect = new Rect();
            child.getGlobalVisibleRect(childRect);

            // 计算 child 在屏幕中的实际可见高度（与 RecyclerView 可见区域相交的部分）
            int visibleHeight = Math.min(childRect.bottom, rvRect.bottom)
                    - Math.max(childRect.top, rvRect.top);
            int totalHeight = child.getHeight();

            float ratio = 0f;
            if (totalHeight > 0) {
                visibleHeight = Math.max(0, visibleHeight);
                ratio = (float) visibleHeight / (float) totalHeight;
            }

            currentVisibleRatio.put(id, ratio);
            // 根据可见比例映射到我们的四个阶段之一
            ExposureStage newStage;
            if (ratio <= 0f) {
                newStage = ExposureStage.EXIT;
            } else if (ratio >= 1f) {
                newStage = ExposureStage.FULL;
            } else if (ratio >= 0.5f) {
                newStage = ExposureStage.HALF;
            } else {
                newStage = ExposureStage.ENTER;
            }

            ExposureStage previous = stageMap.get(id);
            if (previous != newStage) {
                // 阶段发生了变化：记录并发出一个事件
                stageMap.put(id, newStage);

                ExposureEvent event = new ExposureEvent(
                        id,
                        position,
                        newStage,
                        now,
                        card.getTitle(),
                        card.getCardType()
                );
                ExposureLogger.log(event);

                // 通知 Adapter，做视频自动播放控制
                adapter.onExposureEvent(card, position, newStage);

            }
        }
        // ---------- 2. 处理“从有曝光 -> 完全不可见”的 EXIT 事件 ----------

        Map<String, ExposureStage> snapshot = new HashMap<>(stageMap);
        for (Map.Entry<String, ExposureStage> entry : snapshot.entrySet()) {
            String id = entry.getKey();
            if (!currentVisibleRatio.containsKey(id)) {
                ExposureStage prev = entry.getValue();
                // 如果之前是 ENTER/HALF/FULL，但现在已经不在 visibleIds 中，
                // 说明这条卡片已经彻底离开可见区域 -> 需要发 EXIT。
                if (prev != ExposureStage.EXIT) {
                    stageMap.put(id, ExposureStage.EXIT);
                    ExposureEvent event = new ExposureEvent(
                            id,
                            -1,     // 位置未知/不可见，用 -1 标记
                            ExposureStage.EXIT,
                            now,
                            null,    // title 不知道就传 null
                            -1       // cardType 不知道就传 -1
                    );
                    ExposureLogger.log(event);

                    // 位置 -1，表示完全看不到了，这里就不再做 Adapter 通知了
                }
            }
        }
    }
}
