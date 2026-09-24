package com.catpaw.mangareader.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.model.TagItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TagChipAdapter extends RecyclerView.Adapter<TagChipAdapter.ViewHolder> {

    private List<TagItem> tagItems;
    private OnTagClickListener listener;
    private final Random random;

    public interface OnTagClickListener {
        void onTagClick(TagItem tag);
    }

    public TagChipAdapter() {
        this.tagItems = new ArrayList<>();
        this.random = new Random();
    }

    public TagChipAdapter(List<TagItem> items) {
        this.tagItems = items != null ? items : new ArrayList<>();
        this.random = new Random();
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<TagItem> items) {
        this.tagItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TagItem item = tagItems.get(position);
        holder.tagName.setText(item.getTagName());

        int bgColor = generatePastelColor();
        holder.tagName.setBackgroundColor(bgColor);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagClick(item);
            }
        });
    }

    private int generatePastelColor() {
        int hue = random.nextInt(360);
        float[] hsv = new float[]{hue, 0.3f, 0.9f};
        return Color.HSVToColor(100, hsv);
    }

    @Override
    public int getItemCount() {
        return tagItems != null ? tagItems.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tagName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tagName = itemView.findViewById(R.id.tv_tag_name);
        }
    }
}
