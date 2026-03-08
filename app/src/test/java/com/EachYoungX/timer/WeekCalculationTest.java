package com.EachYoungX.timer;

import com.EachYoungX.timer.utils.DateUtils;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 周计算逻辑测试
 * 用于验证统计页和详情页使用的周计算逻辑是否一致
 */
public class WeekCalculationTest {

    public static void main(String[] args) {
        System.out.println("=== 周计算逻辑测试 ===\n");

        // 测试 2026 年 3 月
        int year = 2026;
        int month = 3;

        System.out.println("测试月份：" + year + "年" + month + "月\n");

        // 1. 计算该月所有周
        System.out.println("该月包含的所有周：");
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        System.out.println("月初：" + firstDay + " (" + firstDay.getDayOfWeek() + ")");
        System.out.println("月末：" + lastDay + " (" + lastDay.getDayOfWeek() + ")\n");

        // 找到第一个周一
        LocalDate firstMonday = firstDay.with(
                java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        System.out.println("第一个周一：" + firstMonday);

        int weekIndex = 0;
        LocalDate currentMonday = firstMonday;
        while (!currentMonday.isAfter(lastDay)) {
            LocalDate sunday = currentMonday.plusDays(6);
            System.out.printf("第%d周 (索引=%d): %s ~ %s%n",
                    weekIndex + 1, weekIndex, currentMonday, sunday);

            currentMonday = currentMonday.plusWeeks(1);
            weekIndex++;
        }

        System.out.println("\n总共 " + weekIndex + " 周");

        // 2. 测试日期属于哪一周
        System.out.println("\n=== 日期周索引测试 ===");
        LocalDate testDate = LocalDate.of(2026, 3, 5);
        int weekIndexOfDate = getWeekIndexOfDate(year, month, testDate.getDayOfMonth());
        System.out.println(testDate + " 属于第 " + (weekIndexOfDate + 1) + " 周 (索引=" + weekIndexOfDate + ")");

        // 3. 测试周范围计算
        System.out.println("\n=== 周范围计算测试 ===");
        DateUtils.WeekDateRange weekRange = DateUtils.getWeekDateRange(year, month, 0);
        System.out.println("第 1 周 (索引=0): " + weekRange.getStartDate() + " ~ " + weekRange.getEndDate());

        weekRange = DateUtils.getWeekDateRange(year, month, 1);
        System.out.println("第 2 周 (索引=1): " + weekRange.getStartDate() + " ~ " + weekRange.getEndDate());
    }

    /**
     * 计算某一天属于该月的第几周（0-based 索引）
     */
    public static int getWeekIndexOfDate(int year, int month, int day) {
        LocalDate date = LocalDate.of(year, month, day);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);

        // 找到该月第一个周一
        LocalDate firstMonday = firstDayOfMonth.with(
                java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));

        // 计算日期差
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, date);

        // 如果日期在第一个周一之前，属于第 0 周
        if (daysDiff < 0) {
            return 0;
        }

        // 计算周索引（0-based）
        return (int) (daysDiff / 7);
    }
}
