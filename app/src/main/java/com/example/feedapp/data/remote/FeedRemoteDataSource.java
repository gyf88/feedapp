package com.example.feedapp.data.remote;

import android.os.SystemClock;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.model.FeedPageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.example.feedapp.R;


/**
 * FeedRemoteDataSource 用来「模拟服务端接口」：
 *
 * - 在真实项目里，这一层通常会用 Retrofit / OKHttp 去请求网络；
 * - 在本次作业中，我们用纯 Java 代码 + SystemClock.sleep 来假装有网络请求；
 * - 这样可以不依赖后端，也能完整跑通：
 *      下拉刷新 / 加载更多 / 多种卡片样式 / 单列双列排版 / 视频卡片 等功能。
 *
 * 对外暴露一个主要方法：
 * - loadPage(int page, int pageSize)：根据页码返回一页 FeedPageResult。
 *
 * 它和：
 * - FeedRepository：组合 Remote + Local 数据源；
 * - FeedCard      ：每一条记录的数据结构；
 * - FeedPageResult：分页结果的包裹对象；
 * 有直接的协作关系。
 */
public class FeedRemoteDataSource {

    private static final long NETWORK_DELAY_MS = 2000L;

    /**
     * 用来模拟「视频资源池」：
     * - 实际是 5 个 res/raw 目录下的本地 mp4；
     * - loadPage 时，视频卡片会轮流使用这些资源。
     *
     * 注意：这里的名字要和 res/raw 下的文件一致，例如：
     *   app/src/main/res/raw/video1.mp4
     *   app/src/main/res/raw/video2.mp4
     *   ...
     */
    private static final int[] VIDEO_RES_IDS = {
            R.raw.video1,
            R.raw.video2,
            R.raw.video3,
            R.raw.video4,
            R.raw.video5
    };

    /**
     * 模拟一次分页网络请求。
     *
     * @param page     第几页，从 1 开始
     * @param pageSize 每页多少条
     * @return FeedPageResult，包含：
     *          - 本页的 List<FeedCard>
     *          - 是否还有更多 hasMore
     *          - 下一页页码 nextPage
     */
    public FeedPageResult loadFeedPage(int page, int pageSize) {
        // -------------------- 1. 模拟网络耗时 --------------------
        // SystemClock.sleep 会阻塞当前线程，这里用来营造「网络请求」的感觉，
        // 方便你在加载更多 / 下拉刷新时看到 loading 状态。
        SystemClock.sleep(NETWORK_DELAY_MS);

        // -------------------- 调试：根据 failMode 决定是否“故意失败” --------------------
        if (shouldFail(page)) {
            throw new RuntimeException("模拟网络异常，page=" + page);
        }

        // -------------------- 2. 构造本页的卡片列表 --------------------
        List<FeedCard> list = new ArrayList<>();
        int start = page * pageSize;
        int end = start + pageSize;


        // 这里用一个非常简单的规则来生成 mock 数据：
        // - 每一条的 id 用 UUID 保证唯一；
        // - cardType 根据 index 取模，交替生成：文字 / 图片 / 视频；
        // - layoutType 根据 index 决定单列 or 双列。
        for (int i = start; i < end; i++) {
            FeedCard card = new FeedCard();
            card.setId(UUID.randomUUID().toString());
            card.setTitle("标题 " + i);
            card.setSubTitle("副标题 " + i);
            card.setContent("这是第 " + i + " 条卡片的内容，用来模拟服务端返回的文案。");

            //  使用 picsum 生成一张随机图片
            String imageUrl = "https://picsum.photos/seed/" + i + "/400/300";

            // 模拟服务器控制卡片类型
            int typeMod = i % 3;
            if (typeMod == 0) {
                card.setCardType(FeedCard.TYPE_TEXT);
            } else if (typeMod == 1) {
                card.setCardType(FeedCard.TYPE_IMAGE);
                card.setImageUrl(imageUrl);          // 图片卡片显示这张图片
            } else {
                card.setCardType(FeedCard.TYPE_VIDEO);
                card.setImageUrl(imageUrl);

                //  轮流使用本地 5 个视频
                int index = (i / 3) % VIDEO_RES_IDS.length;
                card.setVideoResId(VIDEO_RES_IDS[index]);
            }


            // -------- 排版方式：单列 / 双列 --------
            // 这里只是简单地做一个例子：
            // - 偶数 index 用单列
            // - 奇数 index 用双列
            if (i % 5 == 0) {
                card.setLayoutType(FeedCard.LAYOUT_SINGLE); // 占一整行
            } else {
                card.setLayoutType(FeedCard.LAYOUT_DOUBLE); // 占半行
            }

            list.add(card);
        }

        // -------------------- 3. 组装分页结果 --------------------
        FeedPageResult result = new FeedPageResult();
        result.setCards(list);
//        result.setHasMore(page < 10);  // 假设最多 10 页
        result.setHasMore(true);        // 无限生成
        result.setNextPage(page + 1);
        return result;
    }

    // ======= 调试用的失败模式开关 =======
    public enum FailMode {
        NONE,               // 正常模式
        REFRESH_ALWAYS_FAIL,// 只有刷新（page=0）会失败
        LOAD_MORE_ALWAYS_FAIL, // 只有加载更多（page>0）会失败
        RANDOM_FAIL         // 随机失败
    }

    private static FailMode failMode = FailMode.NONE;

    public static void setFailMode(FailMode mode) {
        failMode = mode;
    }

    private boolean shouldFail(int page) {
        switch (failMode) {
            case REFRESH_ALWAYS_FAIL:
                return page == 0;
            case LOAD_MORE_ALWAYS_FAIL:
                return page > 0;
            case RANDOM_FAIL:
                return Math.random() < 0.5;  // 50% 概率失败
            case NONE:
            default:
                return false;
        }
    }
}
