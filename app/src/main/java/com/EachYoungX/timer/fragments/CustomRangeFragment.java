package com.EachYoungX.timer.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.database.LogDatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class CustomRangeFragment extends Fragment {

    private Button btnStartDate, btnEndDate;
    private TextView tvTotalDuration, tvRangeCount, tvRangeAvg, tvRangeMax, tvRangeMaxDate;
    private Calendar startCalendar, endCalendar;
    private LogDatabaseHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_custom_range, container, false);

        btnStartDate = view.findViewById(R.id.btn_start_date);
        btnEndDate = view.findViewById(R.id.btn_end_date);
        tvTotalDuration = view.findViewById(R.id.tv_range_total);
        tvRangeCount = view.findViewById(R.id.tv_range_count);
        tvRangeAvg = view.findViewById(R.id.tv_range_avg);
        tvRangeMax = view.findViewById(R.id.tv_range_max);
        tvRangeMaxDate = view.findViewById(R.id.tv_range_max_date);

        dbHelper = new LogDatabaseHelper(getActivity());

        // 初始化日期为今年
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.MONTH, Calendar.JANUARY);
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);

        endCalendar = Calendar.getInstance();
        endCalendar.set(Calendar.MONTH, Calendar.DECEMBER);
        endCalendar.set(Calendar.DAY_OF_MONTH, 31);

        updateDateButtons();
        loadStats();

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));

        return view;
    }

    private void showDatePicker(boolean isStart) {
        Calendar calendar = isStart ? startCalendar : endCalendar;
        DatePickerDialog dialog = new DatePickerDialog(
                getActivity(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateButtons();
                    loadStats();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void updateDateButtons() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        btnStartDate.setText(sdf.format(startCalendar.getTime()));
        btnEndDate.setText(sdf.format(endCalendar.getTime()));
    }

    private void loadStats() {
        String startDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startCalendar.getTime());
        String endDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endCalendar.getTime());

        Map<String, Object> stats = dbHelper.getCustomRangeStats(startDateKey, endDateKey);

        long totalDuration = (long) stats.get("totalDuration");
        int count = (int) stats.get("count");
        long avgDuration = (long) stats.get("avgDuration");
        long maxDuration = (long) stats.get("maxDuration");
        String maxDate = (String) stats.get("maxDate");

        tvTotalDuration.setText(DateUtils.formatDuration(totalDuration));
        tvRangeCount.setText(String.valueOf(count));
        tvRangeAvg.setText(DateUtils.formatDuration(avgDuration));
        tvRangeMax.setText(DateUtils.formatDuration(maxDuration));
        tvRangeMaxDate.setText((maxDate == null || maxDate.isEmpty()) ? "--" : maxDate);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}