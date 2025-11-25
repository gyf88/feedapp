package com.example.feedapp.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.databinding.ItemFeedTextBinding;

import java.util.ArrayList;
import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.TextViewHolder> {

    public interface OnItemLongClickListener {
        void onItemLongClick(FeedCard card);
    }

    private final List<FeedCard> items = new ArrayList<>();
    private OnItemLongClickListener longClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void submitList(List<FeedCard> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedTextBinding binding = ItemFeedTextBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TextViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        FeedCard card = items.get(position);
        holder.bind(card);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(card);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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
}
