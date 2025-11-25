package com.example.feedapp.data.repository;

import android.content.Context;

import com.example.feedapp.data.local.FeedLocalDataSource;
import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.model.FeedPageResult;
import com.example.feedapp.data.remote.FeedRemoteDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedRepository {

    public interface Callback {
        void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage);
        void onError(Throwable t, List<FeedCard> cache);
    }

    private final FeedRemoteDataSource remote;
    private final FeedLocalDataSource local;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<FeedCard> currentList = new ArrayList<>();
    private int nextPage = 0;
    private boolean hasMore = true;
    private boolean loading = false;

    public FeedRepository(Context context) {
        this.remote = new FeedRemoteDataSource();
        this.local = new FeedLocalDataSource(context);
    }

    public boolean canLoadMore() {
        return hasMore && !loading;
    }

    // 刷新（从第一页重新拉取）
    public void refresh(Callback callback) {
        if (loading) return;
        loading = true;
        executor.execute(() -> {
            try {
                FeedPageResult result = remote.loadFeedPage(0, 20);
                synchronized (currentList) {
                    currentList.clear();
                    currentList.addAll(result.getCards());
                    hasMore = result.isHasMore();
                    nextPage = result.getNextPage();
                    local.saveCache(currentList);
                }
                callback.onSuccess(getCurrentSnapshot(), hasMore, nextPage);
            } catch (Exception e) {
                // 失败，从本地缓存拉一份
                List<FeedCard> cache = local.loadCache();
                callback.onError(e, cache);
            } finally {
                loading = false;
            }
        });
    }

    // 加载更多
    public void loadMore(Callback callback) {
        if (loading || !hasMore) return;
        loading = true;
        final int pageToLoad = nextPage;
        executor.execute(() -> {
            try {
                FeedPageResult result = remote.loadFeedPage(pageToLoad, 20);
                synchronized (currentList) {
                    currentList.addAll(result.getCards());
                    hasMore = result.isHasMore();
                    nextPage = result.getNextPage();
                    local.saveCache(currentList);
                }
                callback.onSuccess(getCurrentSnapshot(), hasMore, nextPage);
            } catch (Exception e) {
                // 加载更多失败时，不动当前列表
                callback.onError(e, Collections.emptyList());
            } finally {
                loading = false;
            }
        });
    }

    // 删除某一条卡片（长按删除会用到）
    public void deleteCard(String id) {
        synchronized (currentList) {
            Iterator<FeedCard> iterator = currentList.iterator();
            while (iterator.hasNext()) {
                FeedCard card = iterator.next();
                if (card.getId().equals(id)) {
                    iterator.remove();
                    break;
                }
            }
            local.saveCache(currentList);
        }
    }

    public List<FeedCard> getCurrentSnapshot() {
        synchronized (currentList) {
            return new ArrayList<>(currentList);
        }
    }
}
