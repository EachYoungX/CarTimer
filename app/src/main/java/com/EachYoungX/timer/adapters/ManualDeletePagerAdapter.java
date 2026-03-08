package com.EachYoungX.timer.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.EachYoungX.timer.fragments.MonthlyDeleteFragment;
import com.EachYoungX.timer.fragments.WeeklyDeleteFragment;
import com.EachYoungX.timer.activities.ManualDeleteActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ManualDeletePagerAdapter extends FragmentStateAdapter {

    private final List<Fragment> fragments = new ArrayList<>();
    private int currentYear;
    private Set<Long> selectedRecords;

    public ManualDeletePagerAdapter(@NonNull ManualDeleteActivity fragment, Set<Long> selectedRecords) {
        super(fragment);
        this.currentYear = fragment.getCurrentYear();
        this.selectedRecords = selectedRecords;
        fragments.add(new MonthlyDeleteFragment());
        fragments.add(new WeeklyDeleteFragment());
    }

    public void setYear(int year) {
        this.currentYear = year;
        for (Fragment f : fragments) {
            if (f instanceof MonthlyDeleteFragment) {
                ((MonthlyDeleteFragment) f).setYear(year);
            } else if (f instanceof WeeklyDeleteFragment) {
                ((WeeklyDeleteFragment) f).setYear(year);
            }
        }
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment = fragments.get(position);
        if (fragment instanceof MonthlyDeleteFragment) {
            ((MonthlyDeleteFragment) fragment).setYear(currentYear);
            ((MonthlyDeleteFragment) fragment).setSelectedRecords(selectedRecords);
        } else if (fragment instanceof WeeklyDeleteFragment) {
            ((WeeklyDeleteFragment) fragment).setYear(currentYear);
            ((WeeklyDeleteFragment) fragment).setSelectedRecords(selectedRecords);
        }
        return fragment;
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }

    public Fragment getFragment(int position) {
        return fragments.get(position);
    }
}
