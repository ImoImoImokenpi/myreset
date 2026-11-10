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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記録ページのカレンダーで選んだ日の記録と振り返りメモ、絞り込みに出す習慣。 */
@Service
@Transactional(readOnly = true)
public class JournalService {

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

    /** カレンダーで選んだ1日分（その日の記録と振り返りメモ）。一覧から外した習慣の記録も含む。 */
    public Entry day(AppUser user, LocalDate date) {
        String note = reflections.findByOwnerAndDate(user, date).map(Reflection::getText).orElse(null);
        List<Mark> dayMarks = logs.findWithHabitByOwnerAndDateBetween(user, date, date).stream()
                .map(JournalService::mark)
                .toList();
        return new Entry(date, note, dayMarks);
    }

    private static Mark mark(HabitLog log) {
        Habit h = log.getHabit();
        return new Mark(h.getId(), h.getName(), h.getCategory(), log.isCompleted(), log.getAmount(), h.getTarget(),
                h.getUnit(), h.isQuantity());
    }
}
