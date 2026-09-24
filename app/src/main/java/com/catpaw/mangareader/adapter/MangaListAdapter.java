package com.catpaw.mangareader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.model.MangaItem;
import com.catpaw.mangareader.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MangaListAdapter extends RecyclerView.Adapter<MangaListAdapter.ViewHolder> {

    private List<MangaItem> mangaItems;
    private OnMangaClickListener listener;

    public interface OnMangaClickListener {
        void onMangaClick(MangaItem item);
    }

    public MangaListAdapter() {
        this.mangaItems = new ArrayList<>();
    }

    public MangaListAdapter(List<MangaItem> items) {
        this.mangaItems = items != null ? items : new ArrayList<>();
    }

    public void setOnMangaClickListener(OnMangaClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<MangaItem> items) {
        this.mangaItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addData(List<MangaItem> items) {
        if (items != null && !items.isEmpty()) {
            int startPosition = mangaItems.size();
            mangaItems.addAll(items);
            notifyItemRangeInserted(startPosition, items.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manga, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MangaItem item = mangaItems.get(position);
        holder.title.setText(item.getTitle());
        ImageLoader.getInstance().loadImage(item.getCoverUrl(), holder.cover);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMangaClick(item);
            } else {
                Toast.makeText(v.getContext(),
                        "Opening: " + item.getTitle(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return mangaItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.iv_cover);
            title = itemView.findViewById(R.id.tv_title);
        }
    }
}
