//UI 不直接接触 Repository，而是走 ViewModel
//LiveData 列表：cardsLiveData
//刷新状态：refreshingLiveData
//加载更多状态：loadingMoreLiveData
//提示消息：toastLiveData
//refresh() → 调 Repository.refresh()
//loadMore() → 调 Repository.loadMore()
//deleteCard(card) → 调 Repository.deleteCard()

package com.example.feedapp.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.repository.FeedRepository;

import java.util.List;

/**
 * FeedViewModel 是“界面与数据仓库（Repository）之间的中间层”：
 *
 * 职责：
 * - 持有一个 FeedRepository 实例；
 * - 暴露各种 LiveData 给 Fragment 使用：
 *      - cardsLiveData：列表数据（List<FeedCard>）
 *      - refreshingLiveData：下拉刷新是否在进行
 *      - loadingMoreLiveData：是否在加载更多
 *      - toastLiveData：给 UI 用于弹 Toast 的消息
 *      - showEmptyViewLiveData：是否显示“空页面占位”
 *      - showErrorViewLiveData：是否显示“错误覆盖层”
 * - 提供三类对外操作：
 *      - refresh()：刷新列表
 *      - loadMore()：加载更多
 *      - deleteCard()：删除某条卡片
 *
 * 这样 Fragment 不需要关心 Repository 的细节，只管调这几个方法 + 观察 LiveData。
 */
public class FeedViewModel extends AndroidViewModel {
    /** 数据仓库：封装了 Remote + Local + 内存 currentList + 分页状态 */
    private final FeedRepository repository;

    // -------- 暴露给 UI 的 LiveData --------

    /** 当前列表数据（UI 只观察这个，不自己持有列表） */
    private final MutableLiveData<List<FeedCard>> cardsLiveData = new MutableLiveData<>();
    /** 下拉刷新进行中？用于控制 SwipeRefreshLayout 的 loading 圈 */
    private final MutableLiveData<Boolean> refreshingLiveData = new MutableLiveData<>(false);
    /** 加载更多进行中？用于控制底部 Footer 的显示 */
    private final MutableLiveData<Boolean> loadingMoreLiveData = new MutableLiveData<>(false);
    /** 需要给用户弹出的文本提示（Toast 内容） */
    private final MutableLiveData<String> toastLiveData = new MutableLiveData<>();
    /** 是否显示“空页面”占位 View */
    private final MutableLiveData<Boolean> showEmptyViewLiveData = new MutableLiveData<>(false);
    /** 是否显示“错误覆盖层”（全屏错误 + 重试按钮） */
    private final MutableLiveData<Boolean> showErrorViewLiveData = new MutableLiveData<>(false);
    // -------------------- 构造函数：创建 Repository --------------------
    public FeedViewModel(@NonNull Application application) {
        super(application);
        // Repository 需要一个 Context 来创建 LocalDataSource（写入缓存文件）
        repository = new FeedRepository(application);
    }
    // -------------------- 各种 LiveData 的 Getter（供 UI 观察） --------------------
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


    public LiveData<Boolean> getShowEmptyView() {return showEmptyViewLiveData;}

    public LiveData<Boolean> getShowErrorView() {return showErrorViewLiveData;}


    // -------------------- 下拉刷新 --------------------

    /**
     * 下拉刷新入口：
     * - Fragment 的 SwipeRefreshLayout 调用 viewModel.refresh()；
     * - 这里再调用 repository.refresh(...)；
     * - 成功 / 失败后，通过回调更新各个 LiveData。
     */
    public void refresh() {
        // 1. 先把刷新状态设为 true，UI 会立刻看到小圈圈转起来
        refreshingLiveData.setValue(true);
        // 2. 刷新前先隐藏错误层（用户点了“重试”之后，先把错误罩层收起来）
        showErrorViewLiveData.setValue(false);
        // 3. 调用 Repository 的刷新逻辑（后台线程执行）
        repository.refresh(new FeedRepository.Callback() {
            @Override
            public void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage) {
                // 刷新结束 -> 关闭下拉刷新 loading
                refreshingLiveData.postValue(false);
                // 更新列表数据
                cardsLiveData.postValue(list);
                // 成功时：若列表为空 -> 显示空页面，否则隐藏空页面
                boolean isEmpty = (list == null || list.isEmpty());
                showEmptyViewLiveData.postValue(isEmpty);
                // 成功时一定不显示错误层
                showErrorViewLiveData.postValue(false);
            }

            @Override
            public void onError(Throwable t, List<FeedCard> cache) {
                // 无论如何刷新结束
                refreshingLiveData.postValue(false);
                if (cache != null && !cache.isEmpty()) {
                    // 情况 1：有本地缓存 -> 用缓存填充列表
                    cardsLiveData.postValue(cache);
                    showEmptyViewLiveData.postValue(false);
                    showErrorViewLiveData.postValue(false);
                    toastLiveData.postValue("网络失败，展示本地缓存");
                } else {
                    // 情况 2：既没网络也没缓存 -> 显示错误覆盖层
                    showErrorViewLiveData.postValue(true);
                    showEmptyViewLiveData.postValue(false);
                    toastLiveData.postValue("刷新失败：" + t.getMessage());
                }
            }
        });
    }


    // -------------------- 加载更多 --------------------

    /**
     * 滑到底部触发加载更多：
     * - Fragment 在 onScrolled 接近底部时调用 viewModel.loadMore()；
     * - 这里先询问 repository.canLoadMore()，避免无意义请求；
     * - 然后调用 repository.loadMore(...)；
     * - 成功后更新列表，失败则只弹 Toast，不动已有列表。
     */
    public void loadMore() {
        // 如果 Repository 判断不能加载更多（比如已经到底 / 正在加载中），直接 return
        if (!repository.canLoadMore()) {
            return;
        }
        // 标记“正在加载更多”，UI 底部 Footer 会显示 loading
        loadingMoreLiveData.setValue(true);

        repository.loadMore(new FeedRepository.Callback() {
            @Override
            public void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage) {
                // 加载更多结束
                loadingMoreLiveData.postValue(false);
                // 更新最新列表
                cardsLiveData.postValue(list);
                // 如果列表为空，说明可能是服务器返回空数据，也可以显示空页面
                showEmptyViewLiveData.postValue(list == null || list.isEmpty());
                // 成功时不显示错误层
                showErrorViewLiveData.postValue(false);
            }

            @Override
            public void onError(Throwable t, List<FeedCard> cache) {
                // 加载更多失败：不改变已有列表，只提示用户一下
                loadingMoreLiveData.postValue(false);
                toastLiveData.postValue("加载更多失败");
                // 不显示错误覆盖层，用户还能看到已加载的数据
            }
        });
    }


    // -------------------- 删除某一条卡片 --------------------

    /**
     * 删除一条卡片：
     * - Fragment 在删除确认对话框点击“删除”后，会调用 viewModel.deleteCard(card)；
     * - 这里再转发到 Repository.deleteCard(id)，让数据层去删；
     * - 删完之后，再从 Repository 拿一份最新快照，更新列表 LiveData。
     */
    public void deleteCard(FeedCard card) {
        if (card == null) return;
        repository.deleteCard(card.getId());
        cardsLiveData.setValue(repository.getCurrentSnapshot());
    }
}
