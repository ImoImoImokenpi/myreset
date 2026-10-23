package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.domain.HabitLog;
import com.olafreset.domain.Reflection;
import com.olafreset.repository.HabitLogRepository;
import com.olafreset.repository.HabitRepository;
import com.olafreset.repository.ReflectionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 習慣・達成記録・振り返りの操作。すべてログイン中ユーザーのデータだけを対象にする。 */
@Service
@Transactional(readOnly = true)
public class HabitService {

    /** ダッシュボードの1行分（習慣・今日の達成状況・連続日数・直近7日）。 */
    public record HabitRow(Habit habit, boolean doneToday, int streak, List<Boolean> lastWeek) {
    }

    /** 直近7日の1日分のまとめ。 */
    public record DaySummary(LocalDate date, int done, int total, int xp) {
    }

    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final ReflectionRepository reflections;

    public HabitService(HabitRepository habits, HabitLogRepository logs, ReflectionRepository reflections) {
        this.habits = habits;
        this.logs = logs;
        this.reflections = reflections;
    }

    public List<Habit> activeHabits(AppUser user) {
        return habits.findByOwnerAndActiveTrueOrderByCategoryAscIdAsc(user);
    }

    public List<HabitRow> rows(AppUser user, LocalDate today) {
        List<HabitRow> rows = new ArrayList<>();
        for (Habit habit : activeHabits(user)) {
            Set<LocalDate> doneDates = logs.findByHabitOrderByDateDesc(habit).stream()
                    .map(HabitLog::getDate)
                    .collect(Collectors.toCollection(HashSet::new));
            List<Boolean> lastWeek = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                lastWeek.add(doneDates.contains(today.minusDays(i)));
            }
            rows.add(new HabitRow(habit, doneDates.contains(today), streak(doneDates, today), lastWeek));
        }
        return rows;
    }

    /** 今日（未達成なら昨日）から遡って何日連続で達成しているか。 */
    static int streak(Set<LocalDate> doneDates, LocalDate today) {
        LocalDate day = doneDates.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (doneDates.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    public List<DaySummary> lastWeek(AppUser user, LocalDate today) {
        int total = activeHabits(user).size();
        Map<LocalDate, List<HabitLog>> byDate = logs.findByHabitOwnerAndDateBetween(user, today.minusDays(6), today)
                .stream()
                .filter(l -> l.getHabit().isActive())
                .collect(Collectors.groupingBy(HabitLog::getDate));
        List<DaySummary> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<HabitLog> dayLogs = byDate.getOrDefault(d, List.of());
            int xp = dayLogs.stream().mapToInt(HabitLog::getXp).sum();
            days.add(new DaySummary(d, dayLogs.size(), total, xp));
        }
        return days;
    }

    public long totalXp(AppUser user) {
        return logs.sumXp(user);
    }

    public Level level(AppUser user) {
        return Level.fromTotalXp(totalXp(user));
    }

    @Transactional
    public Habit create(AppUser user, String name, Category category, int xp) {
        return habits.save(new Habit(user, name.strip(), category, xp));
    }

    /** 習慣を一覧から外す。獲得済みの XP は残す。 */
    @Transactional
    public void archive(AppUser user, long habitId) {
        ownHabit(user, habitId).archive();
    }

    /** フレンドに見せるかどうかを切り替える。 */
    @Transactional
    public boolean toggleShared(AppUser user, long habitId) {
        Habit habit = ownHabit(user, habitId);
        habit.setShared(!habit.isShared());
        return habit.isShared();
    }

    /** 達成チェックを付けたり外したりする。 */
    @Transactional
    public void toggle(AppUser user, long habitId, LocalDate date) {
        Habit habit = ownHabit(user, habitId);
        logs.findByHabitAndDate(habit, date).ifPresentOrElse(
                logs::delete,
                () -> logs.save(new HabitLog(habit, date)));
    }

    /** 他のユーザーの習慣は存在しないものとして扱う。 */
    private Habit ownHabit(AppUser user, long habitId) {
        return habits.findByIdAndOwner(habitId, user).orElseThrow();
    }

    public String reflectionText(AppUser user, LocalDate date) {
        return reflections.findByOwnerAndDate(user, date).map(Reflection::getText).orElse("");
    }

    @Transactional
    public void saveReflection(AppUser user, LocalDate date, String text) {
        Reflection r = reflections.findByOwnerAndDate(user, date)
                .orElseGet(() -> reflections.save(new Reflection(user, date)));
        r.setText(text);
    }
}
