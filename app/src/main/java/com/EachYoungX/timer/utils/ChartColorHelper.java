package com.EachYoungX.timer.utils;

import android.content.Context;
import androidx.core.content.ContextCompat;

import com.EachYoungX.timer.R;
import com.EachYoungX.timer.ui.ThemeManager;

/**
 * 图表颜色辅助类
 * 根据当前主题提供对应的图表颜色
 */
public class ChartColorHelper {

    /**
     * 获取当前主题的主色
     */
    public static int getPrimaryColor(Context context) {
        ThemeManager.ThemeColor currentTheme = ThemeManager.getInstance().getThemeColor();
        switch (currentTheme) {
            case ORANGE:
                return ContextCompat.getColor(context, R.color.chart_orange_theme);
            case WHITE:
                return ContextCompat.getColor(context, R.color.chart_white_theme);
            case BLACK:
                return ContextCompat.getColor(context, R.color.chart_black_theme);
            case BLUE:
            default:
                return ContextCompat.getColor(context, R.color.chart_blue_theme);
        }
    }

    /**
     * 获取当前主题的高亮色
     */
    public static int getHighlightColor(Context context) {
        ThemeManager.ThemeColor currentTheme = ThemeManager.getInstance().getThemeColor();
        switch (currentTheme) {
            case ORANGE:
                return ContextCompat.getColor(context, R.color.chart_orange_theme_highlight);
            case WHITE:
                return ContextCompat.getColor(context, R.color.chart_white_theme_highlight);
            case BLACK:
                return ContextCompat.getColor(context, R.color.chart_black_theme_highlight);
            case BLUE:
            default:
                return ContextCompat.getColor(context, R.color.chart_blue_theme_highlight);
        }
    }

    /**
     * 获取当前主题的文本颜色
     */
    public static int getTextColor(Context context) {
        ThemeManager.ThemeColor currentTheme = ThemeManager.getInstance().getThemeColor();
        switch (currentTheme) {
            case ORANGE:
                return ContextCompat.getColor(context, R.color.chart_orange_theme_text);
            case WHITE:
                return ContextCompat.getColor(context, R.color.chart_white_theme_text);
            case BLACK:
                return ContextCompat.getColor(context, R.color.chart_black_theme_text);
            case BLUE:
            default:
                return ContextCompat.getColor(context, R.color.chart_blue_theme_text);
        }
    }

    /**
     * 获取当前主题的网格颜色
     */
    public static int getGridColor(Context context) {
        ThemeManager.ThemeColor currentTheme = ThemeManager.getInstance().getThemeColor();
        switch (currentTheme) {
            case ORANGE:
                return ContextCompat.getColor(context, R.color.chart_orange_theme_grid);
            case WHITE:
                return ContextCompat.getColor(context, R.color.chart_white_theme_grid);
            case BLACK:
                return ContextCompat.getColor(context, R.color.chart_black_theme_grid);
            case BLUE:
            default:
                return ContextCompat.getColor(context, R.color.chart_blue_theme_grid);
        }
    }

    /**
     * 获取当前主题的轴颜色
     */
    public static int getAxisColor(Context context) {
        ThemeManager.ThemeColor currentTheme = ThemeManager.getInstance().getThemeColor();
        switch (currentTheme) {
            case ORANGE:
                return ContextCompat.getColor(context, R.color.chart_orange_theme_axis);
            case WHITE:
                return ContextCompat.getColor(context, R.color.chart_white_theme_axis);
            case BLACK:
                return ContextCompat.getColor(context, R.color.chart_black_theme_axis);
            case BLUE:
            default:
                return ContextCompat.getColor(context, R.color.chart_blue_theme_axis);
        }
    }
}
