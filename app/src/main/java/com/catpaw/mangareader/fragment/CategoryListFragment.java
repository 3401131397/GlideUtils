package com.catpaw.mangareader.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.adapter.MangaListAdapter;
import com.catpaw.mangareader.api.JMApiClient;
import com.catpaw.mangareader.model.MangaItem;

import java.util.List;

public class CategoryListFragment extends Fragment {

    public static final String ARG_CATEGORY_ID = "category_id";
    public static final String ARG_FILTER_TYPE = "filter_type";
    public static final String ARG_RANK_MODE = "rank_mode";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private MangaListAdapter adapter;
    private String categoryId;
    private String filterType;
    private boolean rankMode;

    public CategoryListFragment() {
        super(R.layout.fragment_category_list);
    }

    public static CategoryListFragment newInstance(String categoryId) {
        CategoryListFragment fragment = new CategoryListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_ID, categoryId);
        args.putString(ARG_FILTER_TYPE, "mr");
        args.putBoolean(ARG_RANK_MODE, false);
        fragment.setArguments(args);
        return fragment;
    }

    public static CategoryListFragment newRankInstance(String rankFilter) {
        CategoryListFragment fragment = new CategoryListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_ID, "0");
        args.putString(ARG_FILTER_TYPE, rankFilter);
        args.putBoolean(ARG_RANK_MODE, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryId = getArguments().getString(ARG_CATEGORY_ID, "0");
            filterType = getArguments().getString(ARG_FILTER_TYPE, "mr");
            rankMode = getArguments().getBoolean(ARG_RANK_MODE, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);

        adapter = new MangaListAdapter();
        adapter.setOnMangaClickListener(item -> {
            Toast.makeText(getContext(), "Opening: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary,
                R.color.secondary,
                R.color.tertiary);
        swipeRefreshLayout.setOnRefreshListener(() -> loadMangaList());

        loadMangaList();
    }

    private void loadMangaList() {
        if (categoryId == null) {
            categoryId = "0";
        }

        JMApiClient.getInstance().getCategoryFilter(filterType, categoryId, 1,
                new JMApiClient.ApiCallback<List<MangaItem>>() {
            @Override
            public void onSuccess(List<MangaItem> result) {
                swipeRefreshLayout.setRefreshing(false);
                if (result != null) {
                    adapter.updateData(result);
                }
            }

            @Override
            public void onFailure(String error) {
                swipeRefreshLayout.setRefreshing(false);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Load failed: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
