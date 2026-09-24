package com.catpaw.mangareader.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.model.MangaItem;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class RankFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private RankPagerAdapter pagerAdapter;

    private static final String[] RANK_TITLES = {"Daily", "Weekly", "Monthly", "All Time"};
    private static final String[] RANK_FILTERS = {"daily", "weekly", "monthly", "all"};

    public RankFragment() {
        super(R.layout.fragment_rank);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rank, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout_rank);
        viewPager = view.findViewById(R.id.view_pager_rank);

        pagerAdapter = new RankPagerAdapter(requireActivity());
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(RANK_TITLES[position]);
        }).attach();
    }

    private class RankPagerAdapter extends FragmentStateAdapter {

        public RankPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return CategoryListFragment.newInstance(RANK_FILTERS[position]);
        }

        @Override
        public int getItemCount() {
            return RANK_FILTERS.length;
        }
    }
}
