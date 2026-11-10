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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記録ページの「振り返り」タブ：過去の振り返りメモと記録を、習慣・期間で絞り込んで見る。 */
@Service
@Transactional(readOnly = true)
public class JournalService {

    /** 絞り込みの期間。 */
    public enum Period {
        DAYS30("30", "直近30日", 30), DAYS90("90", "直近90日", 90), ALL("all", "すべて", 0);

        private final String key;
        private final String label;
        private final int days;

        Period(String key, String label, int days) {
            this.key = key;
            this.label = label;
            this.days = days;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        /** 期間の最初の日。「すべて」なら十分に古い日。 */
        public LocalDate from(LocalDate today) {
            return days == 0 ? LocalDate.of(2000, 1, 1) : today.minusDays(days - 1);
        }

        /** URL の値から。知らない値なら直近30日。 */
        public static Period parse(String key) {
            for (Period p : values()) {
                if (p.key.equals(key)) {
                    return p;
                }
            }
            return DAYS30;
        }
    }

    /** ある日の、1つの習慣の記録。 */
    public record Mark(long habitId, String name, Category category, boolean completed, int amount, int target,
                       String unit, boolean quantity) {

        /** 「達成」「5 / 8杯」のような表示。 */
        public String amountLabel() {
            return quantity ? amount + " / " + target + (unit == null ? "回" : unit) : "達成";
        }
    }

    /** 1日分。reflection は空のこともある。 */
    public record Entry(LocalDate date, String reflection, List<Mark> marks) {

        public boolean hasReflection() {
            return reflection != null && !reflection.isBlank();
        }
    }

    /** 習慣を選んだときの、期間内のまとめ。 */
    public record HabitSummary(Habit habit, int recordedDays, int completedDays, long totalAmount,
                               LocalDate first, LocalDate last) {
    }

    public record Journal(List<Entry> entries, HabitSummary summary) {
    }

    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final ReflectionRepository reflections;

    public JournalService(HabitRepository habits, HabitLogRepository logs, ReflectionRepository reflections) {
        this.habits = habits;
        this.logs = logs;
        this.reflections = reflections;
    }

    /** 絞り込みに出す習慣（一覧から外したものも含む）。 */
    public List<Habit> allHabits(AppUser user) {
        return habits.findByOwnerOrderByCategoryAscIdAsc(user);
    }

    /**
     * 振り返りの一覧（新しい日から）。
     * habitId を指定すると、その習慣を記録した日だけを、その日の振り返りメモと一緒に並べる。
     * 指定しなければ、何か記録した日か振り返りを書いた日を並べる。notesOnly なら振り返りを書いた日だけ。
     */
    public Journal journal(AppUser user, Long habitId, Period period, boolean notesOnly, LocalDate today) {
        LocalDate from = period.from(today);
        Habit habit = habitId == null ? null : habits.findByIdAndOwner(habitId, user).orElseThrow();

        Map<LocalDate, String> notes = reflections.findByOwnerAndDateBetween(user, from, today).stream()
                .filter(r -> r.getText() != null && !r.getText().isBlank())
                .collect(Collectors.toMap(Reflection::getDate, Reflection::getText));
        List<HabitLog> periodLogs = logs.findWithHabitByOwnerAndDateBetween(user, from, today).stream()
                .filter(l -> habit == null || l.getHabit().getId().equals(habit.getId()))
                .toList();
        Map<LocalDate, List<Mark>> marks = periodLogs.stream()
                .collect(Collectors.groupingBy(HabitLog::getDate,
                        Collectors.mapping(JournalService::mark, Collectors.toList())));

        // 習慣を選んだときはその習慣を記録した日、選ばないときは記録かメモがある日
        TreeSet<LocalDate> dates = new TreeSet<>(Comparator.reverseOrder());
        dates.addAll(marks.keySet());
        if (habit == null) {
            dates.addAll(notes.keySet());
        }
        List<Entry> entries = new ArrayList<>();
        for (LocalDate d : dates) {
            Entry entry = new Entry(d, notes.get(d), marks.getOrDefault(d, List.of()));
            if (!notesOnly || entry.hasReflection()) {
                entries.add(entry);
            }
        }
        return new Journal(entries, habit == null ? null : summary(habit, periodLogs));
    }

    private static HabitSummary summary(Habit habit, List<HabitLog> habitLogs) {
        int completed = (int) habitLogs.stream().filter(HabitLog::isCompleted).count();
        long total = habitLogs.stream().mapToLong(HabitLog::getAmount).sum();
        LocalDate first = habitLogs.stream().map(HabitLog::getDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = habitLogs.stream().map(HabitLog::getDate).max(Comparator.naturalOrder()).orElse(null);
        return new HabitSummary(habit, habitLogs.size(), completed, total, first, last);
    }

    private static Mark mark(HabitLog log) {
        Habit h = log.getHabit();
        return new Mark(h.getId(), h.getName(), h.getCategory(), log.isCompleted(), log.getAmount(), h.getTarget(),
                h.getUnit(), h.isQuantity());
    }
}
