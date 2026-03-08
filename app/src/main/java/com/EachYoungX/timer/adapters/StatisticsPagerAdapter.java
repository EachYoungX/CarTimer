package com.EachYoungX.timer.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.EachYoungX.timer.fragments.CustomRangeFragment;
import com.EachYoungX.timer.fragments.MonthlyTrendFragment;
import com.EachYoungX.timer.fragments.StatisticsFragment;
import com.EachYoungX.timer.fragments.WeeklyDistributionFragment;

public class StatisticsPagerAdapter extends FragmentStateAdapter {

    private final java.util.List<Fragment> fragments = new java.util.ArrayList<>();
    {
        fragments.add(new MonthlyTrendFragment());
        fragments.add(new WeeklyDistributionFragment());
        fragments.add(new CustomRangeFragment());
    }

    public StatisticsPagerAdapter(@NonNull StatisticsFragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }

    public Fragment getFragment(int position) {
        return fragments.get(position);
    }
}