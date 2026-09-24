package com.catpaw.mangareader.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.fragment.HomeFragment;
import com.catpaw.mangareader.fragment.RankFragment;
import com.catpaw.mangareader.fragment.TagGroupFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements TagGroupFragment.OnTagSearchListener {

    private BottomNavigationView bottomNavigationView;
    private ViewPager2 contentPager;
    private ContentPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupFrostedGlassEffect();

        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_nav);
        contentPager = findViewById(R.id.view_pager);

        pagerAdapter = new ContentPagerAdapter(this);
        contentPager.setAdapter(pagerAdapter);
        contentPager.setUserInputEnabled(false);
        contentPager.setOffscreenPageLimit(3);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                contentPager.setCurrentItem(0, false);
                return true;
            } else if (itemId == R.id.nav_tags) {
                contentPager.setCurrentItem(1, false);
                return true;
            } else if (itemId == R.id.nav_rank) {
                contentPager.setCurrentItem(2, false);
                return true;
            }
            return false;
        });
    }

    private void setupFrostedGlassEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

            getWindow().setBackgroundDrawableResource(R.drawable.bg_glass);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    @Override
    public void onTagSearch(String tagName) {
        android.widget.Toast.makeText(this, "Search tag: " + tagName, android.widget.Toast.LENGTH_SHORT).show();
    }

    private class ContentPagerAdapter extends FragmentStateAdapter {

        private static final int PAGE_COUNT = 3;

        public ContentPagerAdapter(@NonNull AppCompatActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    return new TagGroupFragment();
                case 2:
                    return new RankFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }
    }
}
