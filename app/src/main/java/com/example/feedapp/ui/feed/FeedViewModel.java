package com.example.feedapp.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.repository.FeedRepository;

import java.util.List;

public class FeedViewModel extends AndroidViewModel {

    private final FeedRepository repository;

    private final MutableLiveData<List<FeedCard>> cardsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> refreshingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMoreLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> toastLiveData = new MutableLiveData<>();

    public FeedViewModel(@NonNull Application application) {
        super(application);
        repository = new FeedRepository(application);
    }

    public LiveData<List<FeedCard>> getCards() {
        return cardsLiveData;
    }

    public LiveData<Boolean> getRefreshing() {
        return refreshingLiveData;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMoreLiveData;
    }

    public LiveData<String> getToastMessage() {
        return toastLiveData;
    }

    // 下拉刷新
    public void refresh() {
        refreshingLiveData.setValue(true);

        repository.refresh(new FeedRepository.Callback() {
            @Override
            public void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage) {
                // 在子线程回调，这里用 postValue
                refreshingLiveData.postValue(false);
                cardsLiveData.postValue(list);
            }

            @Override
            public void onError(Throwable t, List<FeedCard> cache) {
                refreshingLiveData.postValue(false);
                if (cache != null && !cache.isEmpty()) {
                    cardsLiveData.postValue(cache);
                    toastLiveData.postValue("网络失败，展示本地缓存");
                } else {
                    toastLiveData.postValue("刷新失败：" + t.getMessage());
                }
            }
        });
    }

    // 滑到底加载更多
    public void loadMore() {
        if (!repository.canLoadMore()) {
            return;
        }

        loadingMoreLiveData.setValue(true);

        repository.loadMore(new FeedRepository.Callback() {
            @Override
            public void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage) {
                loadingMoreLiveData.postValue(false);
                cardsLiveData.postValue(list);
            }

            @Override
            public void onError(Throwable t, List<FeedCard> cache) {
                loadingMoreLiveData.postValue(false);
                toastLiveData.postValue("加载更多失败");
            }
        });
    }

    // 删除某一条卡片
    public void deleteCard(FeedCard card) {
        if (card == null) return;
        repository.deleteCard(card.getId());
        cardsLiveData.setValue(repository.getCurrentSnapshot());
    }
}
