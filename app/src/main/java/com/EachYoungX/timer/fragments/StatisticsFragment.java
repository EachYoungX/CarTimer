package com.EachYoungX.timer.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.EachYoungX.timer.R;
import com.EachYoungX.timer.adapters.StatisticsPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StatisticsFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TextView tvYear;
    private ImageButton btnPrevYear, btnNextYear;

    private int currentYear;
    private StatisticsPagerAdapter pagerAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);

        initViews(view);
        setupViewPager();
        setupListeners();

        return view;
    }

    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);
        tvYear = view.findViewById(R.id.tv_year);
        btnPrevYear = view.findViewById(R.id.btn_prev_year);
        btnNextYear = view.findViewById(R.id.btn_next_year);

        currentYear = java.time.Year.now().getValue();
        updateYearDisplay();
    }

    private void setupViewPager() {
        pagerAdapter = new StatisticsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // 关联 TabLayout 和 ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("月度趋势");
                            break;
                        case 1:
                            tab.setText("周度分布");
                            break;
                        case 2:
                            tab.setText("自定义区间");
                            break;
                    }
                }).attach();
    }

    private void setupListeners() {
        btnPrevYear.setOnClickListener(v -> {
            currentYear--;
            updateYearDisplay();
            refreshCurrentTab();
        });

        btnNextYear.setOnClickListener(v -> {
            currentYear++;
            updateYearDisplay();
            refreshCurrentTab();
        });
    }

    private void updateYearDisplay() {
        tvYear.setText(String.valueOf(currentYear));
        btnPrevYear.setEnabled(currentYear > 2000);
        btnNextYear.setEnabled(currentYear < 2100);
    }

    private void refreshCurrentTab() {
        int currentItem = viewPager.getCurrentItem();
        Fragment fragment = pagerAdapter.getFragment(currentItem);
        if (fragment != null) {
            if (fragment instanceof MonthlyTrendFragment) {
                ((MonthlyTrendFragment) fragment).setYear(currentYear);
            } else if (fragment instanceof WeeklyDistributionFragment) {
                ((WeeklyDistributionFragment) fragment).setYear(currentYear);
            }
        }
    }

    public int getCurrentYear() {
        return currentYear;
    }
}