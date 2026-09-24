package com.catpaw.mangareader.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.api.JMApiClient;
import com.catpaw.mangareader.model.CategoryItem;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private CategoryPagerAdapter pagerAdapter;
    private List<CategoryItem> categories;
    private boolean isLoading = false;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout_categories);
        viewPager = view.findViewById(R.id.view_pager_home);

        categories = new ArrayList<>();
        pagerAdapter = new CategoryPagerAdapter(requireActivity());
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position < categories.size()) {
                tab.setText(categories.get(position).getTitle());
            }
        }).attach();

        loadCategories();
    }

    private void loadCategories() {
        if (isLoading) return;
        isLoading = true;

        JMApiClient.getInstance().getCategories(new JMApiClient.ApiCallback<List<CategoryItem>>() {
            @Override
            public void onSuccess(List<CategoryItem> result) {
                isLoading = false;
                if (result != null && !result.isEmpty()) {
                    categories.clear();
                    categories.addAll(result);
                    pagerAdapter.notifyDataSetChanged();

                    new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                        if (position < categories.size()) {
                            tab.setText(categories.get(position).getTitle());
                        }
                    }).attach();
                }
            }

            @Override
            public void onFailure(String error) {
                isLoading = false;
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load categories: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private class CategoryPagerAdapter extends FragmentStateAdapter {

        public CategoryPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position < categories.size()) {
                return CategoryListFragment.newInstance(categories.get(position).getCategoryId());
            }
            return CategoryListFragment.newInstance("");
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }
    }
}
