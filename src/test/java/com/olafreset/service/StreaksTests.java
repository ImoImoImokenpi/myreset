package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olafreset.domain.Habit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreaksTests {

    // 2026-10-22 は木曜日
    static final LocalDate THU = LocalDate.of(2026, 10, 22);
    static final LocalDate LONG_AGO = THU.minusYears(1);
    static final int EVERY_DAY = Habit.ALL_DAYS;

    @Test
    void everyDayHabitCountsBackFromTodayOrYesterday() {
        assertThat(Streaks.current(EVERY_DAY, LONG_AGO, Set.of(), THU)).isZero();
        assertThat(Streaks.current(EVERY_DAY, LONG_AGO, Set.of(THU, THU.minusDays(1), THU.minusDays(3)), THU))
                .isEqualTo(2);
        // 今日まだ未達成でも、昨日までの連続は途切れていない
        assertThat(Streaks.current(EVERY_DAY, LONG_AGO, Set.of(THU.minusDays(1), THU.minusDays(2)), THU))
                .isEqualTo(2);
        assertThat(Streaks.current(EVERY_DAY, LONG_AGO, Set.of(THU.minusDays(2)), THU)).isZero();
    }

    @Test
    void daysOffDoNotBreakTheStreak() {
        int monWedFri = Habit.toMask(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        LocalDate wed = THU.minusDays(1);
        LocalDate mon = THU.minusDays(3);
        LocalDate prevFri = THU.minusDays(6);
        // 木曜（お休み）から見て、水・月・前の金を続けて達成
        assertThat(Streaks.current(monWedFri, LONG_AGO, Set.of(wed, mon, prevFri), THU)).isEqualTo(3);
        // 月曜を休んだら水曜の1回だけ
        assertThat(Streaks.current(monWedFri, LONG_AGO, Set.of(wed, prevFri), THU)).isEqualTo(1);
        // お休みの曜日に達成しても数えない
        assertThat(Streaks.current(monWedFri, LONG_AGO, Set.of(THU, wed), THU)).isEqualTo(1);
    }

    @Test
    void streakStopsAtCreationDate() {
        assertThat(Streaks.current(EVERY_DAY, THU.minusDays(1), Set.of(THU, THU.minusDays(1)), THU)).isEqualTo(2);
    }
}
