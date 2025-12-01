package com.example.feedapp.ui.feed;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.databinding.ItemFeedFooterLoadingBinding;
import com.example.feedapp.databinding.ItemFeedImageBinding;
import com.example.feedapp.databinding.ItemFeedTextBinding;
import com.example.feedapp.databinding.ItemFeedVideoBinding;
import com.example.feedapp.exposure.ExposureStage;

import androidx.media3.exoplayer.ExoPlayer;

import android.os.Handler;
import android.os.Looper;



/**
 * FeedAdapter 负责把「数据层的 FeedCard 列表」展示成 RecyclerView 上的多种卡片。
 *
 * 这一层只关心「如何把一条 FeedCard 显示出来」，不关心「这些数据从哪来」：
 * - 数据来源：FeedViewModel -> FeedRepository -> Remote/LocalDataSource。
 * - 展示方式：这里根据 FeedCard.cardType / layoutType 选择不同 ViewHolder。
 *
 * 主要特性：
 * - 支持多种卡片类型：文字 / 图片 / 视频；
 * - 支持底部 Footer 作为“加载更多中...”的 loading 卡片；
 * - 支持长按删除（回调到 Fragment 里弹出确认框）；
 * - 对视频卡片：使用 ExoPlayer 播放本地 raw 视频，并在右下角显示倒计时；
 * - 为曝光打点预留了 onExposureEvent(...) 接口。
 */
public class FeedAdapter extends ListAdapter<FeedCard, RecyclerView.ViewHolder> {

    // -------------------- ViewType 常量 --------------------

    /** 纯文字卡片 ViewType */
    public static final int VIEW_TYPE_TEXT = 1;
    /** 图片卡片 ViewType */
    public static final int VIEW_TYPE_IMAGE = 2;
    /** 视频卡片 ViewType */
    public static final int VIEW_TYPE_VIDEO = 3;
    /** 底部 Footer（加载更多） ViewType */
    public static final int VIEW_TYPE_FOOTER = 100;

    // -------------------- 长按删除回调接口 --------------------

    /**
     * 用于把「用户在某条卡片上长按」这个事件，抛给外层（FeedFragment）。
     * Fragment 再决定要不要弹出删除确认弹窗。
     */
    public interface OnItemLongClickListener {
        void onItemLongClick(FeedCard card);
    }

    /** 当前设置的长按回调 */
    private OnItemLongClickListener longClickListener;

    // -------------------- Footer 控制 --------------------

    /**
     * 是否在列表末尾显示 Footer（加载更多中的小圆圈）。
     * - true  时：列表最后会多出一个 FooterViewHolder；
     * - false 时：列表只有真实数据的 item。
     *
     * 它通常由 FeedFragment 根据「是否正在加载更多」来控制。
     */
    private boolean showFooter = false;

    // -------------------- RecyclerView 引用 --------------------

    /**
     * Adapter 所附着的 RecyclerView 引用：
     * - 用于某些场景下，需要拿到 RecyclerView 做一些查询；
     * - 比如我们在 Fragment 里通过 recyclerView.findViewHolderForAdapterPosition(...)
     *   拿到某个位置的 ViewHolder，再进行视频播放控制。
     */
    private RecyclerView attachedRv;

    // -------------------- 构造函数 --------------------
    public FeedAdapter() {
        // ListAdapter 需要传入一个 DiffUtil.ItemCallback，
        // 用于在 submitList 时高效计算“新旧列表差异”，只刷新有变化的条目。
        super(DIFF_CALLBACK);
    }
    // -------------------- 对外接口：设置长按监听 --------------------
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    // -------------------- Footer 相关 --------------------

    /**
     * 控制是否展示 Footer（加载更多的 loading 卡片）。
     * 这里使用 notifyDataSetChanged() 简化实现。
     */
    public void setShowFooter(boolean show) {
        if (showFooter != show) {
            showFooter = show;
            // 更精细的做法可以使用 notifyItemInserted/Removed，
            // 但这里为了清晰起见，直接全量刷新一次。
            notifyDataSetChanged();
        }
    }

    /**
     * 真正的数据条目数量（不包含 Footer）。
     */
    private int getRealItemCount() {
        return super.getItemCount();
    }

    /**
     * 对 RecyclerView 来说的总 item 数量：
     * = 实际数据条数 + Footer（如果开启的话）。
     */
    @Override
    public int getItemCount() {
        return getRealItemCount() + (showFooter ? 1 : 0);
    }

    /**
     * 安全获取给定 position 的 FeedCard：
     * - 如果 position 越界（比如 Footer / 负数），返回 null；
     * - 外层在使用时要做好 null 判断。
     */
    public FeedCard getItemOrNull(int position) {
        if (position < 0) return null;
        if (position >= getRealItemCount()) return null;
        return getItem(position);
    }

    /**
     * 告诉 RecyclerView 每个 position 应该使用哪种 ViewHolder。
     * 逻辑：
     * 1. 如果当前 position 是最后一项，并且 showFooter = true，则是 Footer；
     * 2. 否则，根据 FeedCard.cardType 决定 TEXT / IMAGE / VIDEO。
     */
    @Override
    public int getItemViewType(int position) {
        // Footer：单独占据列表最后一个元素的位置
        if (showFooter && position == getItemCount() - 1) {
            return VIEW_TYPE_FOOTER;
        }
        // 对于真实数据 item，根据 cardType 决定 ViewType
        FeedCard card = getItem(position);
        if (card == null) return VIEW_TYPE_TEXT;

        switch (card.getCardType()) {
            case FeedCard.TYPE_IMAGE:
                return VIEW_TYPE_IMAGE;
            case FeedCard.TYPE_VIDEO:
                return VIEW_TYPE_VIDEO;
            case FeedCard.TYPE_TEXT:
            default:
                return VIEW_TYPE_TEXT;
        }
    }

    // ------- 创建 ViewHolder ------- //

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_TEXT) {
            // 纯文字卡片：使用 item_feed_text.xml
            ItemFeedTextBinding binding =
                    ItemFeedTextBinding.inflate(inflater, parent, false);
            return new TextViewHolder(binding);
        } else if (viewType == VIEW_TYPE_IMAGE) {
            // 图片卡片：使用 item_feed_image.xml
            ItemFeedImageBinding binding =
                    ItemFeedImageBinding.inflate(inflater, parent, false);
            return new ImageViewHolder(binding);
        } else if (viewType == VIEW_TYPE_VIDEO) {
            // 视频卡片：使用 item_feed_video.xml（内部是 PlayerView + 封面 + 标题 + 倒计时）
            ItemFeedVideoBinding binding =
                    ItemFeedVideoBinding.inflate(inflater, parent, false);
            return new VideoViewHolder(binding);
        } else {
            // Footer：加载更多中的 loading 卡片
            ItemFeedFooterLoadingBinding binding =
                    ItemFeedFooterLoadingBinding.inflate(inflater, parent, false);
            return new FooterViewHolder(binding);
        }
    }

    // -------------------- 绑定数据到 ViewHolder --------------------

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        // Footer 不需要绑定数据，直接返回即可
        if (getItemViewType(position) == VIEW_TYPE_FOOTER) {
            return;
        }

        FeedCard card = getItem(position);
        if (card == null) return;
        // 根据具体 ViewHolder 类型调用对应的 bind(...)
        if (holder instanceof TextViewHolder) {
            ((TextViewHolder) holder).bind(card);
        } else if (holder instanceof ImageViewHolder) {
            ((ImageViewHolder) holder).bind(card);
        } else if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).bind(card);
        }
        // 为所有普通 item 设置长按监听，用于“删卡操作”
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(card);
            }
            return true;
        });
    }

    // -------------------- 各种 ViewHolder 实现 --------------------

    /**
     * 文字卡片 ViewHolder：
     * - 对应 item_feed_text.xml；
     * - 只负责把 title / subTitle / content 绑定到 TextView 上。
     */
    static class TextViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedTextBinding binding;

        public TextViewHolder(ItemFeedTextBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(FeedCard card) {
            binding.tvTitle.setText(card.getTitle());
            binding.tvSubTitle.setText(card.getSubTitle());
            binding.tvContent.setText(card.getContent());
        }
    }

    /**
     * 图片卡片 ViewHolder：
     * - 对应 item_feed_image.xml；
     * - 除了标题，还会用 Glide 异步加载一张封面图。
     */
    static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedImageBinding binding;

        public ImageViewHolder(ItemFeedImageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(FeedCard card) {
            binding.tvTitle.setText(card.getTitle());
            // 使用 Glide 从网络加载图片：
            // - 异步下载；
            // - 自动做缓存；
            // - centerCrop 表示按比例裁剪填满 ImageView。
            Glide.with(binding.ivCover.getContext())
                    .load(card.getImageUrl())
                    .centerCrop()
                    .into(binding.ivCover);
        }
    }

    /**
     * 视频卡片 ViewHolder：
     * - 对应 item_feed_video.xml；
     * - 内部是：PlayerView + 封面 ivVideoCover + 标题 + 右下角倒计时 TextView。
     *
     * 注意：
     * - 真正的 ExoPlayer 实例是在 FeedFragment 中创建的（全局只一个）；
     * - 这里不 new ExoPlayer，只是在 attachPlayer(...) 时把 player 绑定到 PlayerView；
     * - detachPlayer(...) 时把 Player 从 PlayerView 上解绑，避免复用错乱；
     * - startCountdown(...) 里会根据 ExoPlayer 的 currentPosition / duration 计算剩余秒数，
     *   显示到右下角的小标签中。
     */
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedVideoBinding binding;
        /** 当前这条卡片要播放的本地视频资源 ID（R.raw.xxx） */
        private int videoResId = 0;
        /** 用于定时刷新倒计时的 Handler（绑定主线程 Looper） */
        private final Handler handler = new Handler(Looper.getMainLooper());
        /** 倒计时任务引用，方便后续取消 */
        private Runnable countdownTask;

        public VideoViewHolder(ItemFeedVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * bind(...) 主要做两件事：
         * 1. 根据 FeedCard 填充标题、封面图；
         * 2. 记录 videoResId；并清空任何之前残留的 Player / 倒计时。
         */
        public void bind(FeedCard card) {
            binding.tvVideoTitle.setText(card.getTitle());

            // 保存本地视频资源 ID
            videoResId = card.getVideoResId();

            // 加载封面
            Glide.with(binding.ivVideoCover.getContext())
                    .load(card.getImageUrl())
                    .centerCrop()
                    .into(binding.ivVideoCover);

            // 初始：显示封面，隐藏倒计时
            binding.ivVideoCover.setVisibility(android.view.View.VISIBLE);
            binding.tvCountdown.setText("");
            binding.tvCountdown.setVisibility(android.view.View.GONE);

            // Recycle 复用时，确保不会残留旧的 player 或旧的倒计时任务
            binding.playerView.setPlayer(null);
            cancelCountdown();
        }

        /** 提供给 Fragment，用来拿到当前 item 的本地视频资源 ID */
        public int getVideoResId() {
            return videoResId;
        }

        /**
         * 把一个全局的 ExoPlayer 实例绑定到当前 ViewHolder 的 PlayerView 上，
         * 并开始显示右下角的倒计时。
         *
         * - Fragment 会先调用 vvh.getVideoResId() 拿到 resId，再用 player.setMediaItem(...)；
         * - 然后调用 vvh.attachPlayer(player) 来绑定到 PlayerView 上；
         * - 此时 ExoPlayer 的画面就会渲染到这个卡片里。
         */
        public void attachPlayer(ExoPlayer player) {
            binding.playerView.setPlayer(player);
            binding.ivVideoCover.setVisibility(android.view.View.GONE);
            binding.tvCountdown.setVisibility(android.view.View.VISIBLE);
            startCountdown(player);
        }

        /**
         * 从当前 ViewHolder 上解绑 Player：
         * - 用于 ViewHolder 被滑出屏幕、复用、或中心播放切换到其他卡片时；
         * - 同时恢复封面图并关闭倒计时。
         */
        public void detachPlayer() {
            binding.playerView.setPlayer(null);
            binding.ivVideoCover.setVisibility(android.view.View.VISIBLE);
            binding.tvCountdown.setText("");
            binding.tvCountdown.setVisibility(android.view.View.GONE);
            cancelCountdown();
        }

        // -------------------- 倒计时逻辑 --------------------

        /**
         * 启动一个定时任务，每秒读取一次 ExoPlayer 的进度，
         * 根据 duration - currentPosition 计算剩余秒数并显示出来。
         */
        private void startCountdown(ExoPlayer player) {
            cancelCountdown();// 先取消旧任务，避免多个任务同时跑
            countdownTask = new Runnable() {
                @Override
                public void run() {
                    if (player == null) return;

                    long duration = player.getDuration();           // 总时长（毫秒）
                    long position = player.getCurrentPosition();    // 当前播放位置（毫秒）

                    if (duration > 0) {
                        long remainMs = duration - position;
                        if (remainMs < 0) remainMs = 0;
                        long remainSec = (remainMs + 999) / 1000;   // 四舍五入到秒
                        // 显示为类似“8s / 12s”这样
                        binding.tvCountdown.setText(remainSec + "s");
                        binding.tvCountdown.setVisibility(android.view.View.VISIBLE);
                    } else {
                        // duration 尚不可用时（比如刚开始 prepare），先隐藏倒计时
                        binding.tvCountdown.setText("");
                        binding.tvCountdown.setVisibility(android.view.View.GONE);
                    }

                    // 每秒更新一次
                    handler.postDelayed(this, 1000);
                }
            };
            handler.post(countdownTask);
        }

        /**
         * 取消当前的倒计时任务：
         * - 避免 ViewHolder 被回收后仍然访问已经不再可见的视图；
         * - 在 bind()/detachPlayer()/onViewRecycled 中都会调用。
         */
        private void cancelCountdown() {
            if (countdownTask != null) {
                handler.removeCallbacks(countdownTask);
                countdownTask = null;
            }
        }
    }



    /**
     * Footer ViewHolder：
     * - 对应 item_feed_footer_loading.xml；
     * - 一般里面就是一个 ProgressBar + “正在加载...” 文案；
     * - 逻辑很简单，不需要额外绑定数据。
     */
    static class FooterViewHolder extends RecyclerView.ViewHolder {
        public FooterViewHolder(ItemFeedFooterLoadingBinding binding) {
            super(binding.getRoot());
        }
    }

    // -------------------- DiffUtil：高效计算新旧列表差异 --------------------

    /**
     * DiffUtil 的核心逻辑：
     * - areItemsTheSame：判断是不是“同一条数据”（用 id 比较）；
     * - areContentsTheSame：判断内容是否完全一致，如果不一致则刷新 UI。
     *
     * 注意：
     * - 这里假设 FeedCard.id 全局唯一（RemoteDataSource 用 UUID 生成）；
     * - 如果以后你接真实后端，可以用后端提供的 itemId。
     */
    private static final DiffUtil.ItemCallback<FeedCard> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FeedCard>() {
                @Override
                public boolean areItemsTheSame(@NonNull FeedCard oldItem,
                                               @NonNull FeedCard newItem) {
                    if (oldItem.getId() == null || newItem.getId() == null) return false;
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull FeedCard oldItem,
                                                  @NonNull FeedCard newItem) {
                    return safeEquals(oldItem.getTitle(), newItem.getTitle())
                            && safeEquals(oldItem.getSubTitle(), newItem.getSubTitle())
                            && safeEquals(oldItem.getContent(), newItem.getContent())
                            && oldItem.getCardType() == newItem.getCardType()
                            && oldItem.getLayoutType() == newItem.getLayoutType()
                            && safeEquals(oldItem.getImageUrl(), newItem.getImageUrl())
                            && oldItem.getVideoResId() == newItem.getVideoResId();
                }

                private boolean safeEquals(String a, String b) {
                    if (a == null) return b == null;
                    return a.equals(b);
                }
            };

    // -------------------- 生命周期回调：记录 RecyclerView 引用 --------------------

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRv = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        attachedRv = null;
    }
    /**
     * 提供一个获取当前 RecyclerView 引用的方法：
     * - 一般在 Fragment 中，我们已经持有 recyclerView 对象，不一定需要用到；
     * - 但如果以后你想在 Adapter 内部做一些滚动相关的操作，可以用它。
     */
    public RecyclerView getAttachedRecyclerView() {
        return attachedRv;
    }

    /**
     * 当某个 ViewHolder 被 RecyclerView 回收时调用：
     * - 如果是视频卡片，需要确保解绑 Player，并取消倒计时；
     * - 避免出现“滑出屏幕后还在播放 / 还在更新 UI”这种问题。
     */
    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).detachPlayer();
        }
    }


    // -------------------- 曝光事件：预留接口 --------------------

    /**
     * 曝光事件回调入口：
     * - 当前只作为占位，真正的曝光逻辑掌握在曝光系统（ExposureTracker）里；
     * - 如果你以后想在“第一次完整曝光”时给卡片标记“已读”，可以在这里做 UI 操作。
     *
     * @param card     当前曝光的 FeedCard 数据
     * @param position 在 Adapter 中的位置
     * @param stage    曝光阶段（露出 / 半露出 / 完全露出 / 消失）
     */
    public void onExposureEvent(FeedCard card, int position, ExposureStage stage) {
        // 以后想根据曝光状态做 UI 标记可以写在这里
    }
}
