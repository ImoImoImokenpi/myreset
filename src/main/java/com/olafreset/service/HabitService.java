package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.domain.HabitLog;
import com.olafreset.domain.HabitTeam;
import com.olafreset.domain.Reflection;
import com.olafreset.repository.HabitLogRepository;
import com.olafreset.repository.HabitRepository;
import com.olafreset.repository.ReflectionRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** 直近7日の印の状態。CSS のクラス名としても使う。 */
    public enum DayMark {
        DONE("on"), MISSED(""), OFF("off");

        private final String css;

        DayMark(String css) {
            this.css = css;
        }

        public String getCss() {
            return css;
        }
    }

    /**
     * ダッシュボードの1行分。
     * scheduledToday: 今日が実施日か / amountToday: 今日記録した量 / streak: 連続日数 / lastWeek: 直近7日の印
     */
    public record HabitRow(Habit habit, boolean scheduledToday, boolean doneToday, int amountToday,
                           int streak, List<DayMark> lastWeek) {

        /** 今日ここまでにもらった XP（量の習慣は進めた分だけ）。 */
        public int earnedToday() {
            return HabitLog.earnedXp(habit.getXp(), amountToday, habit.getTarget());
        }

        /** 目標に対する今日の進み具合（0〜100）。 */
        public int percent() {
            return Math.min(100, amountToday * 100 / habit.getTarget());
        }
    }

    /**
     * 直近7日の1日分のまとめ。total はその日が実施日の習慣の数、done は目標まで達成した数。
     * percent はその日の達成度（0〜100）：各習慣の「進めた量 ÷ 目標」（最大 100%）の平均。
     */
    public record DaySummary(LocalDate date, int done, int total, int xp, int percent) {
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

    /** 習慣ごとの、達成した日の集合。 */
    Map<Long, Set<LocalDate>> completedDates(AppUser user) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        for (HabitLog log : logs.findCompletedByOwner(user)) {
            result.computeIfAbsent(log.getHabit().getId(), k -> new HashSet<>()).add(log.getDate());
        }
        return result;
    }

    public List<HabitRow> rows(AppUser user, LocalDate today) {
        Map<Long, Set<LocalDate>> done = completedDates(user);
        Map<Long, Integer> amounts = logs.findByOwnerAndDate(user, today).stream()
                .collect(Collectors.toMap(l -> l.getHabit().getId(), HabitLog::getAmount));
        List<HabitRow> rows = new ArrayList<>();
        for (Habit habit : activeHabits(user)) {
            Set<LocalDate> doneDates = done.getOrDefault(habit.getId(), Set.of());
            List<DayMark> lastWeek = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                lastWeek.add(doneDates.contains(d) ? DayMark.DONE
                        : habit.isScheduledOn(d) ? DayMark.MISSED : DayMark.OFF);
            }
            rows.add(new HabitRow(habit, habit.isScheduledOn(today), doneDates.contains(today),
                    amounts.getOrDefault(habit.getId(), 0), Streaks.current(habit, doneDates, today), lastWeek));
        }
        return rows;
    }

    public List<DaySummary> lastWeek(AppUser user, LocalDate today) {
        List<Habit> active = activeHabits(user);
        // 目標に届いていない記録も、進めた分を達成度に入れる
        Map<LocalDate, List<HabitLog>> byDate = logs.findByHabitOwnerAndDateBetween(user, today.minusDays(6), today)
                .stream()
                .filter(l -> l.getHabit().isActive())
                .collect(Collectors.groupingBy(HabitLog::getDate));
        List<DaySummary> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int total = (int) active.stream().filter(h -> h.isScheduledOn(d) && !h.getCreatedAt().isAfter(d)).count();
            List<HabitLog> dayLogs = byDate.getOrDefault(d, List.of()).stream()
                    .filter(l -> l.getHabit().isScheduledOn(d))
                    .toList();
            int done = (int) dayLogs.stream().filter(HabitLog::isCompleted).count();
            int xp = dayLogs.stream().mapToInt(HabitLog::getEarnedXp).sum();
            double progress = dayLogs.stream()
                    .mapToDouble(l -> Math.min(l.getAmount(), l.getHabit().getTarget()) / (double) l.getHabit().getTarget())
                    .sum();
            int percent = total == 0 ? 0 : (int) Math.round(progress * 100 / total);
            days.add(new DaySummary(d, done, total, xp, Math.min(100, percent)));
        }
        return days;
    }

    /** 今日が実施日で、まだ達成していない習慣（リマインダー用）。 */
    public List<Habit> unfinishedToday(AppUser user, LocalDate today) {
        return rows(user, today).stream()
                .filter(r -> r.scheduledToday() && !r.doneToday())
                .map(HabitRow::habit)
                .toList();
    }

    public long totalXp(AppUser user) {
        return logs.sumXp(user);
    }

    public Level level(AppUser user) {
        return Level.fromTotalXp(totalXp(user));
    }

    @Transactional
    public Habit create(AppUser user, String name, Category category, int xp) {
        return create(user, name, category, xp, Set.of(DayOfWeek.values()), 1, 1, null);
    }

    @Transactional
    public Habit create(AppUser user, String name, Category category, int xp,
                        Set<DayOfWeek> days, int target, int step, String unit) {
        Habit habit = new Habit(user, name.strip(), category, xp);
        habit.configure(days, target, step, unit);
        return habits.save(habit);
    }

    @Transactional
    public Habit rename(AppUser user, long habitId, String name) {
        Habit habit = ownHabit(user, habitId);
        if (habit.isTeamHabit()) {
            // チームの習慣は、参加者全員の名前をそろえる
            habits.findActiveByTeam(habit.getTeam()).forEach(h -> h.rename(name.strip()));
        }
        habit.rename(name.strip());
        return habit;
    }

    /** 習慣を一覧から外す。獲得済みの XP は残す。チームの習慣なら、チームから抜ける。 */
    @Transactional
    public void archive(AppUser user, long habitId) {
        Habit habit = ownHabit(user, habitId);
        habit.archive();
        // 抜けた人を除いて、今日のクリアを判定し直す
        updateTeamClear(habit.getTeam(), LocalDate.now());
    }

    /** フレンドに見せるかどうかを切り替える。 */
    @Transactional
    public boolean toggleShared(AppUser user, long habitId) {
        Habit habit = ownHabit(user, habitId);
        habit.setShared(!habit.isShared());
        return habit.isShared();
    }

    /**
     * 達成チェックを付けたり外したりする。
     * 量の習慣では、チェックを付けると目標量まで記録し、外すと記録を消す。
     */
    @Transactional
    public void toggle(AppUser user, long habitId, LocalDate date) {
        Habit habit = ownHabit(user, habitId);
        var existing = logs.findByHabitAndDate(habit, date);
        if (existing.isPresent() && existing.get().isCompleted()) {
            logs.delete(existing.get());
        } else if (existing.isPresent()) {
            existing.get().setAmount(habit.getTarget());
        } else {
            logs.save(new HabitLog(habit, date, habit.getTarget()));
        }
        updateTeamClear(habit.getTeam(), date);
    }

    /** 量を増減する（「＋」「−」ボタン）。0 になったら記録を消す。 */
    @Transactional
    public int addAmount(AppUser user, long habitId, LocalDate date, int delta) {
        Habit habit = ownHabit(user, habitId);
        var existing = logs.findByHabitAndDate(habit, date);
        int amount = Math.max(0, existing.map(HabitLog::getAmount).orElse(0) + delta);
        if (amount == 0) {
            existing.ifPresent(logs::delete);
        } else if (existing.isPresent()) {
            existing.get().setAmount(amount);
        } else {
            logs.save(new HabitLog(habit, date, amount));
        }
        updateTeamClear(habit.getTeam(), date);
        return amount;
    }

    /**
     * その日にチームの参加者全員が達成していればクリアとし、全員の記録にボーナス XP を付ける。
     * 1人でも未達成ならボーナスを外す。参加者が2人以上で、実施日のときだけクリアになる。
     */
    @Transactional
    void updateTeamClear(HabitTeam team, LocalDate date) {
        if (team == null) {
            return;
        }
        List<Habit> members = habits.findActiveByTeam(team).stream()
                .filter(h -> !h.getCreatedAt().isAfter(date))
                .toList();
        Map<Long, HabitLog> byHabit = logs.findByTeamAndDate(team, date).stream()
                .collect(Collectors.toMap(l -> l.getHabit().getId(), l -> l));
        boolean cleared = members.size() >= 2
                && members.getFirst().isScheduledOn(date)
                && members.stream().allMatch(h -> {
                    HabitLog log = byHabit.get(h.getId());
                    return log != null && log.isCompleted();
                });
        for (Habit h : members) {
            HabitLog log = byHabit.get(h.getId());
            if (log != null) {
                log.setBonusXp(cleared ? HabitLog.teamBonus(h.getXp()) : 0);
            }
        }
    }

    /** 自分の習慣（一覧から外したものも含む）。他のユーザーの習慣は見つからない扱い。 */
    public Habit find(AppUser user, long habitId) {
        return ownHabit(user, habitId);
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
