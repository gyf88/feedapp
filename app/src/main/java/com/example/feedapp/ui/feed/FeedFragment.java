package com.example.feedapp.ui.feed;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.databinding.FragmentFeedBinding;

import com.example.feedapp.exposure.ExposureTracker;

import android.graphics.Rect;


import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.RawResourceDataSource;
import androidx.media3.exoplayer.ExoPlayer;
/**
 * FeedFragment 是「信息流主界面」：
 *
 * - UI 层只负责展示 & 处理交互：
 *      - 下拉刷新、上拉加载更多
 *      - 多种卡片（文字、图片、视频）
 *      - 删除卡片（长按）
 *      - 空页面 / 错误页面
 *      - 视频自动播放（滚动时锁定“最靠中间的那条视频”）
 * - 具体数据从哪来？由 ViewModel 提供；
 *   ViewModel 背后再去找 Repository，Repository 又组合 Remote + Local。
 *
 * 关系图：
 *   Fragment(UI) -> FeedViewModel -> FeedRepository -> Remote/LocalDataSource
 *                                   -> 同时提供 List<FeedCard> 给 FeedAdapter
 */

public class FeedFragment extends Fragment {
    /** ViewBinding 对应 fragment_feed.xml，负责拿到界面上的所有控件引用 */
    private FragmentFeedBinding binding;
    /** ViewModel：负责拿数据 + 管理刷新/加载更多/错误状态等 */
    private FeedViewModel viewModel;
    /** RecyclerView 的适配器：把 List<FeedCard> 展示成多种卡片 */
    private FeedAdapter adapter;
    /** 曝光埋点工具：内部监听 RecyclerView 滚动，计算卡片曝光状态 */
    private ExposureTracker exposureTracker;
    /** 当前正在播放视频的 adapter position（-1 表示没有） */
    private int currentPlayingVideoPos = RecyclerView.NO_POSITION;
    /** 全局只有一个 ExoPlayer，多个视频卡片复用它（谁在中心就绑定给谁） */
    private ExoPlayer player;

    // -------------------- Fragment 生命周期：创建视图 --------------------
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 使用 ViewBinding 生成根布局对象，避免手写 findViewById
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    // -------------------- Fragment 生命周期：视图创建完毕后的初始化逻辑 --------------------
    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. 创建全局 ExoPlayer（整个 Fragment 只用这一份）
        player = new ExoPlayer.Builder(requireContext()).build();
        // 播放完自动重播当前视频（类似抖音循环播）
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        // 2. 拿到 ViewModel（生命周期与 Fragment 绑定）
        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);
        // 3. 创建 Adapter，并设置长按回调 -> 弹出删除对话框
        adapter = new FeedAdapter();
        adapter.setOnItemLongClickListener(this::showDeleteDialog);

        // 4. 使用 GridLayoutManager 实现「单列/双列混排」
        // spanCount = 2：表示一行分成 2 份
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);

        // 预取优化：让 RecyclerView 提前预加载下面要出现的 item，滑动更流畅
        layoutManager.setItemPrefetchEnabled(true);
        layoutManager.setInitialPrefetchItemCount(6);
        // 设置布局管理器与适配器
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);

        // RecyclerView 性能优化：
        // - setHasFixedSize(true)：item 高宽不会因为内容改变而频繁变动；
        // - setItemViewCacheSize(20)：缓存更多 ViewHolder，减少频繁创建。
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setItemViewCacheSize(20);

        // spanSizeLookup 决定「每个 position 占几列」：
        // - Footer 一定占满一行（2 列）；
        // - layoutType = SINGLE：占 2 列（整行）；
        // - layoutType = DOUBLE：占 1 列（半行，两张卡并排）。
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = adapter.getItemViewType(position);
                if (viewType == FeedAdapter.VIEW_TYPE_FOOTER) {
                    return 2; // Footer 占满一行
                }
                FeedCard card = adapter.getItemOrNull(position);
                if (card == null) return 2;
                // 单列：占两列；双列：占一列
                return (card.getLayoutType() == FeedCard.LAYOUT_SINGLE) ? 2 : 1;
            }
        });
        // 再次设置（虽然上面已经 set 过一次，但不会有问题）
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);

        // 5. 绑定曝光跟踪器：内部会监听 RecyclerView 的滚动 / 布局变化
        exposureTracker = new ExposureTracker(binding.recyclerView, adapter);

        // 6. 下拉刷新：交给 ViewModel.refresh()
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
        });

        // 7. 滑动监听：负责两件事
        //    a) 滚动过程中自动寻找「中心视频卡片」进行播放
        //    b) 滑到底部时触发 loadMore
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);

                // a) 每次滚动都尝试锁定“中心视频”进行自动播放
                autoPlayCenterVideo();

                // b) 只有向下滑动（dy > 0）才尝试触发加载更多
                if (dy <= 0) return;

                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof GridLayoutManager)) return;

                GridLayoutManager glm = (GridLayoutManager) lm;
                int lastVisible = glm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();

                // 当最后一个可见位置 >= 总数 - 3 时，说明接近底部，尝试加载更多
                if (total > 3 && lastVisible >= total - 3) {
                    viewModel.loadMore();
                }
            }
        });



        // 8. 监听 ViewModel 提供的各种 LiveData，更新 UI

        // 8.1 列表数据变化：
        // - submitList 会通过 DiffUtil 做高效局部刷新；
        // - 数据更新完后，post 一个 autoPlayCenterVideo，保证视频联动。
        viewModel.getCards().observe(getViewLifecycleOwner(), cards -> {
            adapter.submitList(cards);

            // 数据来到之后，等 RecyclerView 布局完，再尝试自动选择一个中心视频
            binding.recyclerView.post(this::autoPlayCenterVideo);
        });


        // 8.2 下拉刷新状态：控制 SwipeRefreshLayout 的小圈圈
        viewModel.getRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing));
        });

        // 8.3 加载更多状态：控制底部 Footer 显示 / 隐藏
        viewModel.getLoadingMore().observe(getViewLifecycleOwner(), loadingMore -> {
            adapter.setShowFooter(Boolean.TRUE.equals(loadingMore));
        });

        // 8.4 全局 Toast 提示：比如“网络失败，展示本地缓存”、“加载更多失败”等
        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        // 8.5 首次进入页面时自动刷新一次（只在 savedInstanceState == null 时触发）
        if (savedInstanceState == null) {
            binding.swipeRefresh.setRefreshing(true);
            viewModel.refresh();
        }

        // 8.6 空页面显示控制：
        // - 当列表为空时显示“空页面”占位；
        // - 当列表有数据时隐藏。
        viewModel.getShowEmptyView().observe(getViewLifecycleOwner(), showEmpty -> {
            if (showEmpty != null && showEmpty) {
                binding.emptyView.setVisibility(View.VISIBLE);
            } else {
                binding.emptyView.setVisibility(View.GONE);
            }
        });

        // 8.7 错误覆盖层显示控制：
        // - 网络失败且本地没有缓存时，显示一个全屏错误层 + 重试按钮。
        viewModel.getShowErrorView().observe(getViewLifecycleOwner(), showError -> {
            if (showError != null && showError) {
                binding.errorView.setVisibility(View.VISIBLE);
            } else {
                binding.errorView.setVisibility(View.GONE);
            }
        });

        // 8.8 错误页上的重试按钮，重新触发刷新
        binding.btnRetry.setOnClickListener(v -> {
            viewModel.refresh();
        });

    }

    // -------------------- 删除卡片：弹出确认框 --------------------

    /**
     * 当用户长按某条卡片时，从 Adapter 回调到这里。
     * 这里弹出一个 AlertDialog，确认后再调用 ViewModel.deleteCard。
     */
    private void showDeleteDialog(FeedCard card) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除卡片")
                .setMessage("确定要删除这条卡片吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deleteCard(card);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    // -------------------- 生命周期：销毁视图时释放播放器 --------------------
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (player != null) {
            player.release(); // 释放底层解码器、内存等资源
            player = null;
        }
        // 如果在 onCreateView 中写了 binding = FragmentFeedBinding.inflate(...)
        // 通常还会在这里写 binding = null; 避免内存泄漏。

    }


    // -------------------- 自动播放：选择“最靠屏幕中心的那条视频卡片” --------------------

    /**
     * 在当前屏幕可见范围内，找到：
     * - cardType = VIDEO 的 item；
     * - 且其中心 Y 距离 RecyclerView 中心 Y 最近；
     * 然后让这条卡片播放视频，其余视频停止。
     *
     * 调用时机：
     * - RecyclerView 滚动（onScrolled）；
     * - 列表数据刚更新完（cardsLiveData.observe 回调里，post 一次）。
     */
    private void autoPlayCenterVideo() {
        RecyclerView rv = binding.recyclerView;
        if (rv == null || player == null) return;

        int childCount = rv.getChildCount();
        if (childCount == 0) {
            stopVideoAt(currentPlayingVideoPos);
            currentPlayingVideoPos = RecyclerView.NO_POSITION;
            return;
        }
        // 计算 RecyclerView 在屏幕上的可见区域与中心 Y 坐标
        Rect rvRect = new Rect();
        rv.getGlobalVisibleRect(rvRect);
        int centerY = (rvRect.top + rvRect.bottom) / 2;

        int bestPos = RecyclerView.NO_POSITION;
        int minDistance = Integer.MAX_VALUE;

        // 遍历当前屏幕上所有可见的 child view
        for (int i = 0; i < childCount; i++) {
            android.view.View child = rv.getChildAt(i);
            int pos = rv.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) continue;

            FeedCard card = adapter.getItemOrNull(pos);
            // 只关心“视频卡片”
            if (card == null || card.getCardType() != FeedCard.TYPE_VIDEO) continue;
            // 计算这个 child 自己在屏幕上的中心 Y 坐标
            Rect childRect = new Rect();
            child.getGlobalVisibleRect(childRect);
            int childCenterY = (childRect.top + childRect.bottom) / 2;

            int distance = Math.abs(childCenterY - centerY);
            if (distance < minDistance) {
                minDistance = distance;
                bestPos = pos;
            }
        }

        if (bestPos == RecyclerView.NO_POSITION) {
            // 当前屏幕没有视频卡片：停止之前正在播的那条视频
            stopVideoAt(currentPlayingVideoPos);
            currentPlayingVideoPos = RecyclerView.NO_POSITION;
            return;
        }

        if (bestPos == currentPlayingVideoPos) {
            // 还是同一条视频，不需要切换
            return;
        }

        // 切换播放对象：先停掉旧的，再启动新的
        stopVideoAt(currentPlayingVideoPos);
        startVideoAt(bestPos);
        currentPlayingVideoPos = bestPos;
    }

    // -------------------- 在某个位置启动播放本地 raw 视频 --------------------

    /**
     * 在给定位置 pos 的 VideoViewHolder 上启动播放器：
     * 1. 从 ViewHolder 里拿到 videoResId（本地 R.raw.xxx）；
     * 2. 调用 vvh.attachPlayer(player)，把全局 ExoPlayer 绑定到这个卡片上；
     * 3. 用 RawResourceDataSource + MediaItem 构造本地视频资源；
     * 4. prepare() + play()。
     */
    private void startVideoAt(int pos) {
        if (player == null) return;
        if (pos == RecyclerView.NO_POSITION) return;

        RecyclerView.ViewHolder vh =
                binding.recyclerView.findViewHolderForAdapterPosition(pos);
        if (!(vh instanceof FeedAdapter.VideoViewHolder)) return;// 对应位置不是视频卡片（极端情况）

        FeedAdapter.VideoViewHolder vvh = (FeedAdapter.VideoViewHolder) vh;
        int resId = vvh.getVideoResId();
        if (resId == 0) return;// 没有设置有效的视频资源 ID

        // 先把 player 绑定到当前卡片的 PlayerView 上
        vvh.attachPlayer(player);

        // 使用 RawResourceDataSource 把 res/raw 下的 mp4 构造成 Uri
        android.net.Uri uri = RawResourceDataSource.buildRawResourceUri(resId);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    // -------------------- 在某个位置停止视频播放并解绑 Player --------------------

    /**
     * 停止 pos 位置的 VideoViewHolder 所对应的视频播放：
     * - detachPlayer：让这个卡片恢复到“只显示封面”的状态；
     * - player.pause()：暂停全局 ExoPlayer（不销毁）。
     */
    private void stopVideoAt(int pos) {
        if (player == null) return;
        if (pos == RecyclerView.NO_POSITION) return;

        RecyclerView.ViewHolder vh =
                binding.recyclerView.findViewHolderForAdapterPosition(pos);
        if (vh instanceof FeedAdapter.VideoViewHolder) {
            ((FeedAdapter.VideoViewHolder) vh).detachPlayer();
        }
        player.pause();
    }



}
