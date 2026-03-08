package com.EachYoungX.timer.fragments;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.EachYoungX.timer.services.FloatingWindowService;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.services.TimerService;

/**
 * 主页 Fragment - 精简版
 * 只保留计时器核心功能
 */
public class HomeFragment extends Fragment {
    private TextView timerTextView;
    private Button startButton, pauseButton;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    // 广播接收器
    private BroadcastReceiver timerReceiver;

    // 计时器状态
    private boolean isTimerRunning = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 注册权限请求启动器
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 检查权限是否已授予
                    android.provider.Settings.canDrawOverlays(getActivity());// 权限已授予
                });

        initViews(view);
        checkPermissions();
        registerTimerReceiver();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 不再需要注册客户端，由 MainActivity 统一管理
        // TimerService.registerClient();

        // 2. 无论计时器是否在走，都发一个 Intent 过去
        // 这会触发 Service 的 onStartCommand，从而取消 Service 那边的自毁任务
        Intent intent = new Intent(getActivity(), TimerService.class);
        intent.setAction(TimerService.ACTION_GET_STATUS);
        requireActivity().startService(intent);
    }

    @Override
    public void onPause() {
        super.onPause();
        // 不再需要注销客户端，由 MainActivity 统一管理
        // TimerService.unregisterClient();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timerReceiver != null) {
            requireActivity().unregisterReceiver(timerReceiver);
        }
    }

    private void registerTimerReceiver() {
        timerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long time = intent.getLongExtra("time", 0);
                timerTextView.setText(formatTime(time));

                // 获取运行状态并更新按钮
                isTimerRunning = intent.getBooleanExtra(TimerService.EXTRA_IS_RUNNING, false);
                updateButtonStates();
            }
        };

        IntentFilter filter = new IntentFilter(TimerService.ACTION_TIME_UPDATE);
        requireActivity().registerReceiver(timerReceiver, filter);
    }

    // 更新按钮状态的辅助方法
    private void updateButtonStates() {
        if (startButton != null && pauseButton != null) {
            // 正在计时：开始按钮激活（深色），暂停按钮取消激活（正常）
            // 暂停计时：开始按钮取消激活（正常），暂停按钮激活（深色）
            startButton.setActivated(isTimerRunning);
            pauseButton.setActivated(!isTimerRunning);

            // 可点击性建议：
            // 计时中，开始按钮不可点；暂停中，开始按钮可点（恢复）
            startButton.setEnabled(!isTimerRunning);
            // 只有在计时或有进度时，暂停按钮才可点
            pauseButton.setEnabled(isTimerRunning);
        }
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(long timeInMillis) {
        int hours = (int) (timeInMillis / 3600000);
        int minutes = (int) ((timeInMillis % 3600000) / 60000);
        int seconds = (int) ((timeInMillis % 60000) / 1000);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void initViews(View view) {
        timerTextView = view.findViewById(R.id.timerTextView);
        startButton = view.findViewById(R.id.startButton);
        pauseButton = view.findViewById(R.id.pauseButton);
        Button stopButton = view.findViewById(R.id.stopButton);
        Button minimizeButton = view.findViewById(R.id.minimizeButton);

        // 开始按钮
        startButton.setOnClickListener(v -> {
            startTimer();
            // 检查自动最小化开关，决定是否延迟最小化到后台
            SharedPreferences prefs = requireActivity().getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
            boolean autoMinimize = prefs.getBoolean("auto_minimize", false);
            if (autoMinimize) {
                // 延迟 1 秒最小化到后台
                new android.os.Handler().postDelayed(this::minimizeToFloatingWindow, 1000);
            }
        });

        // 暂停按钮
        pauseButton.setOnClickListener(v -> pauseTimer());

        // 停止按钮
        stopButton.setOnClickListener(v -> stopTimer());

        // 最小化按钮
        minimizeButton.setOnClickListener(v -> minimizeToFloatingWindow());
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(getActivity())) {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + requireActivity().getPackageName()));
            overlayPermissionLauncher.launch(intent);
        }
    }

    private void startTimer() {
        Intent intent = new Intent(getActivity(), TimerService.class);
        intent.setAction(TimerService.ACTION_START);
        requireActivity().startService(intent);
    }

    private void pauseTimer() {
        Intent intent = new Intent(getActivity(), TimerService.class);
        intent.setAction(TimerService.ACTION_PAUSE);
        requireActivity().startService(intent);
    }

    private void stopTimer() {
        Intent intent = new Intent(getActivity(), TimerService.class);
        intent.setAction(TimerService.ACTION_STOP);
        requireActivity().startService(intent);
    }

    private void minimizeToFloatingWindow() {
        // 启动悬浮窗服务
        Intent intent = new Intent(getActivity(), FloatingWindowService.class);
        requireActivity().startService(intent);

        // 将应用切换到后台
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        requireActivity().startActivity(homeIntent);
    }
}
