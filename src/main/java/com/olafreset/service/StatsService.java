package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記録ページ用の集計（月のカレンダーと、習慣ごとの統計）。 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /** カレンダーの1マス。date が null なら月の前後の空きマス。 */
    public record CalendarDay(LocalDate date, int done, int total, boolean future, boolean today) {

        /** 色の濃さ（0〜4）。実施日がない日や未来の日は -1。 */
        public int level() {
            if (date == null || future || total == 0) {
                return -1;
            }
            if (done == 0) {
                return 0;
            }
            int percent = done * 100 / total;
            return percent >= 100 ? 4 : percent >= 66 ? 3 : percent >= 33 ? 2 : 1;
        }
    }

    public record Calendar(YearMonth month, List<List<CalendarDay>> weeks, int completions, int perfectDays,
                           int scheduledDays, int rate) {
    }

    /** 曜日ごとの達成率。scheduled が 0 なら実施しない曜日。 */
    public record WeekdayRate(DayOfWeek day, String label, int done, int scheduled) {
        public int percent() {
            return scheduled == 0 ? 0 : done * 100 / scheduled;
        }
    }

    public record HabitStats(Habit habit, int currentStreak, int bestStreak, int total, int rate30,
                             List<WeekdayRate> weekdays) {
    }

    private final HabitService habits;

    public StatsService(HabitService habits) {
        this.habits = habits;
    }

    public Calendar calendar(AppUser user, YearMonth month, LocalDate today) {
        List<Habit> active = habits.activeHabits(user);
        Map<Long, Set<LocalDate>> done = habits.completedDates(user);

        List<List<CalendarDay>> weeks = new ArrayList<>();
        List<CalendarDay> week = new ArrayList<>();
        // 月曜始まり。1日の前を空きマスで埋める
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) {
            week.add(new CalendarDay(null, 0, 0, false, false));
        }
        int completions = 0;
        int perfect = 0;
        int scheduledDays = 0;
        int doneSum = 0;
        int totalSum = 0;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            boolean future = date.isAfter(today);
            int total = 0;
            int count = 0;
            for (Habit h : active) {
                if (h.isScheduledOn(date) && !h.getCreatedAt().isAfter(date)) {
                    total++;
                    if (done.getOrDefault(h.getId(), Set.of()).contains(date)) {
                        count++;
                    }
                }
            }
            CalendarDay day = new CalendarDay(date, count, total, future, date.equals(today));
            week.add(day);
            if (!future && total > 0) {
                scheduledDays++;
                completions += count;
                doneSum += count;
                // 今日はまだ終わっていないので、達成率の分母には達成した分だけ入れる
                totalSum += date.equals(today) ? count : total;
                if (count == total) {
                    perfect++;
                }
            }
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }
        if (!week.isEmpty()) {
            while (week.size() < 7) {
                week.add(new CalendarDay(null, 0, 0, false, false));
            }
            weeks.add(week);
        }
        int rate = totalSum == 0 ? 0 : doneSum * 100 / totalSum;
        return new Calendar(month, weeks, completions, perfect, scheduledDays, rate);
    }

    public List<HabitStats> habitStats(AppUser user, LocalDate today) {
        Map<Long, Set<LocalDate>> done = habits.completedDates(user);
        List<HabitStats> result = new ArrayList<>();
        for (Habit h : habits.activeHabits(user)) {
            Set<LocalDate> dates = done.getOrDefault(h.getId(), Set.of());
            result.add(new HabitStats(h,
                    Streaks.current(h, dates, today),
                    Streaks.best(h, dates, today),
                    (int) dates.stream().filter(h::isScheduledOn).count(),
                    rate(h, dates, today, 30),
                    weekdays(h, dates, today)));
        }
        return result;
    }

    /** 直近 days 日の達成率。今日は達成していれば数に入れる。 */
    static int rate(Habit h, Set<LocalDate> dates, LocalDate today, int days) {
        int scheduled = 0;
        int count = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = today.minusDays(i);
            if (d.isBefore(h.getCreatedAt())) {
                break;
            }
            if (!h.isScheduledOn(d)) {
                continue;
            }
            boolean ok = dates.contains(d);
            if (d.equals(today) && !ok) {
                continue;
            }
            scheduled++;
            if (ok) {
                count++;
            }
        }
        return scheduled == 0 ? 0 : count * 100 / scheduled;
    }

    /** 直近12週の曜日ごとの達成率。 */
    static List<WeekdayRate> weekdays(Habit h, Set<LocalDate> dates, LocalDate today) {
        int[] scheduled = new int[7];
        int[] count = new int[7];
        for (int i = 0; i < 84; i++) {
            LocalDate d = today.minusDays(i);
            if (d.isBefore(h.getCreatedAt())) {
                break;
            }
            if (!h.isScheduledOn(d) || (d.equals(today) && !dates.contains(d))) {
                continue;
            }
            int idx = d.getDayOfWeek().getValue() - 1;
            scheduled[idx]++;
            if (dates.contains(d)) {
                count[idx]++;
            }
        }
        List<WeekdayRate> list = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            int idx = d.getValue() - 1;
            list.add(new WeekdayRate(d, Habit.shortName(d), count[idx], scheduled[idx]));
        }
        return list;
    }
}
