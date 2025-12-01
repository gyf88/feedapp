package com.example.feedapp.ui.debug;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.databinding.ItemExposureLogBinding;

import java.util.List;

/**
 * 简单的 RecyclerView.Adapter，用于在调试页面中展示曝光日志文本列表。
 *
 * 每一行：
 * - 对应一条日志字符串（比如 "[12:01:02.123] pos=5 id=xxx stage=FULL title=xxx type=2"）；
 * - 使用 item_exposure_log.xml 布局（内部只有一个 TextView: tvLog）。
 *
 * 数据来源：
 * - 由 ExposureDebugActivity 传入一个 List<String> data；
 * - 每次 ExposureDebugActivity.onNewEvent(...) 被调用时：
 *      - 会往 data 的头部插入一个新的日志字符串；
 *      - 调用 adapter.notifyItemInserted(0) 刷新列表。
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
    /** 要展示的日志文本列表：引用的是外部传入的同一个 List 实例 */
    private final List<String> data;
    /**
     * 构造函数：
     *
     * @param data 外部维护的日志列表，通常是 ExposureDebugActivity.logs
     */
    public LogAdapter(List<String> data) {
        this.data = data;
    }
    // -------------------- 创建 ViewHolder --------------------
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 使用 ViewBinding 加载 item_exposure_log.xml 布局
        ItemExposureLogBinding binding =
                ItemExposureLogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }
    // -------------------- 绑定数据到 ViewHolder --------------------
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.tvLog.setText(data.get(position));
    }
    // -------------------- 返回 item 总数量 --------------------
    @Override
    public int getItemCount() {
        return data.size();
    }
    // -------------------- 内部 ViewHolder 类 --------------------

    /**
     * 日志 ViewHolder：
     * - 持有一个 TextView，用来显示日志字符串。
     */
    static class VH extends RecyclerView.ViewHolder {
        TextView tvLog;

        public VH(ItemExposureLogBinding binding) {
            super(binding.getRoot());
            // ViewBinding 已经为我们生成了 tvLog 字段
            tvLog = binding.tvLog;
        }
    }
}
