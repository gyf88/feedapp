package com.example.feedapp.data.remote;

import android.os.SystemClock;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.model.FeedPageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 用来模拟一个网络接口：分页返回列表数据
public class FeedRemoteDataSource {

    private static final long NETWORK_DELAY_MS = 600L;

    public FeedPageResult loadFeedPage(int page, int pageSize) {
        // 模拟网络延迟
        SystemClock.sleep(NETWORK_DELAY_MS);

        List<FeedCard> list = new ArrayList<>();
        int start = page * pageSize;
        int end = start + pageSize;

        for (int i = start; i < end; i++) {
            FeedCard card = new FeedCard();
            card.setId(UUID.randomUUID().toString());
            card.setTitle("标题 " + i);
            card.setSubTitle("副标题 " + i);
            card.setContent("这是第 " + i + " 条卡片的内容，用来模拟服务端返回的文案。");

            // 模拟服务器控制卡片类型
            int typeMod = i % 3;
            if (typeMod == 0) {
                card.setCardType(FeedCard.TYPE_TEXT);
            } else if (typeMod == 1) {
                card.setCardType(FeedCard.TYPE_IMAGE);
                card.setImageUrl("https://example.com/image_" + i + ".jpg");
            } else {
                card.setCardType(FeedCard.TYPE_VIDEO);
                card.setFakeVideoDurationSec(10 + (i % 10));
            }

            // 模拟服务器控制排版（单列 / 双列）
            if (i % 4 == 0) {
                card.setLayoutType(FeedCard.LAYOUT_SINGLE); // 占一整行
            } else {
                card.setLayoutType(FeedCard.LAYOUT_DOUBLE); // 占半行
            }

            list.add(card);
        }

        FeedPageResult result = new FeedPageResult();
        result.setCards(list);
        result.setHasMore(page < 10);  // 假设最多 10 页
        result.setNextPage(page + 1);
        return result;
    }
}
