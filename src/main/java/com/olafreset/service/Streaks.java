package com.olafreset.service;

import com.olafreset.domain.Habit;
import java.time.LocalDate;
import java.util.Set;

/**
 * 連続日数の計算。実施しない曜日は飛ばす（途切れない）。
 * 実施しない曜日に達成しても、連続日数には数えない。
 */
final class Streaks {

    private Streaks() {
    }

    /** 今日（まだ未達成なら前の実施日）から遡って、何回の実施日を続けて達成しているか。 */
    static int current(Habit habit, Set<LocalDate> done, LocalDate today) {
        return current(habit.getDays(), habit.getCreatedAt(), done, today);
    }

    static int current(int days, LocalDate createdAt, Set<LocalDate> done, LocalDate today) {
        LocalDate day = today;
        // 今日はまだ終わっていないので、未達成でも途切れたことにしない
        if (scheduled(days, today) && !done.contains(today)) {
            day = today.minusDays(1);
        }
        int count = 0;
        while (!day.isBefore(createdAt)) {
            if (scheduled(days, day)) {
                if (!done.contains(day)) {
                    break;
                }
                count++;
            }
            day = day.minusDays(1);
        }
        return count;
    }

    /** これまでで一番長く続いた連続日数。 */
    static int best(Habit habit, Set<LocalDate> done, LocalDate today) {
        int best = 0;
        int run = 0;
        for (LocalDate day = habit.getCreatedAt(); !day.isAfter(today); day = day.plusDays(1)) {
            if (!habit.isScheduledOn(day)) {
                continue;
            }
            if (done.contains(day)) {
                run++;
                best = Math.max(best, run);
            } else if (!day.equals(today)) {
                run = 0;
            }
        }
        return best;
    }

    static boolean scheduled(int days, LocalDate date) {
        return (days >> (date.getDayOfWeek().getValue() - 1) & 1) == 1;
    }
}
