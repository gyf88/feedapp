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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.databinding.FragmentFeedBinding;

public class FeedFragment extends Fragment {

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private FeedAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);

        // 初始化 RecyclerView + Adapter
        adapter = new FeedAdapter();
        adapter.setOnItemLongClickListener(this::showDeleteDialog);

        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        binding.recyclerView.setAdapter(adapter);

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refresh();
        });

        // 滑到底自动 loadMore（预加载 3 个）
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (total > 3 && lastVisible >= total - 3) {
                    viewModel.loadMore();
                }
            }
        });

        // 观察数据变化
        viewModel.getCards().observe(getViewLifecycleOwner(), cards -> {
            adapter.submitList(cards);
        });

        viewModel.getRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            binding.swipeRefresh.setRefreshing(Boolean.TRUE.equals(refreshing));
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        // 第一次自动触发刷新
        if (savedInstanceState == null) {
            binding.swipeRefresh.setRefreshing(true);
            viewModel.refresh();
        }
    }

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
