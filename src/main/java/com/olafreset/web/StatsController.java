package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.service.HabitService;
import com.olafreset.service.JournalService;
import com.olafreset.service.JournalService.Period;
import com.olafreset.service.StatsService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** 記録ページ（統計タブ：月のカレンダーと習慣ごとの統計 / 振り返りタブ：過去のメモと記録）。 */
@Controller
public class StatsController {

    private final StatsService stats;
    private final JournalService journal;
    private final HabitService habits;

    public StatsController(StatsService stats, JournalService journal, HabitService habits) {
        this.stats = stats;
        this.journal = journal;
        this.habits = habits;
    }

    /**
     * 統計タブ。day を指定するとその日の記録とメモを、habit を指定するとその習慣だけで色付けしたカレンダーを出す。
     * 形式のおかしい値は無視する。
     */
    @GetMapping("/stats")
    String stats(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                 @RequestParam(required = false) String month,
                 @RequestParam(required = false) String day,
                 @RequestParam(required = false) String habit, Model model) {
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);
        LocalDate selectedDay = parseDate(day);
        if (selectedDay != null && selectedDay.isAfter(today)) {
            selectedDay = null;
        }
        YearMonth shown = parseMonth(month);
        if (shown == null) {
            shown = selectedDay != null ? YearMonth.from(selectedDay) : current;
        }
        if (shown.isAfter(current)) {
            shown = current;
        }
        // 表示している月の日だけ選べる。何も選んでいなければ、今月なら今日を選ぶ
        if (selectedDay != null && !YearMonth.from(selectedDay).equals(shown)) {
            selectedDay = null;
        }
        if (selectedDay == null && (day == null || day.isBlank()) && shown.equals(current)) {
            selectedDay = today;
        }
        Habit filter = parseId(habit) == null ? null : habits.find(me, parseId(habit));

        var all = journal.allHabits(me);
        model.addAttribute("activeHabits", all.stream().filter(Habit::isActive).toList());
        model.addAttribute("archivedHabits", all.stream().filter(h -> !h.isActive()).toList());
        model.addAttribute("filterHabit", filter);
        model.addAttribute("filterId", filter == null ? null : filter.getId());
        model.addAttribute("selectedDay", selectedDay);
        model.addAttribute("dayEntry", selectedDay == null ? null : journal.day(me, selectedDay));
        model.addAttribute("calendar", stats.calendar(me, shown, today, filter));
        model.addAttribute("prevMonth", shown.minusMonths(1));
        model.addAttribute("nextMonth", shown.isBefore(current) ? shown.plusMonths(1) : null);
        model.addAttribute("habitStats", stats.habitStats(me, today));
        return "stats";
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static YearMonth parseMonth(String value) {
        try {
            return value == null || value.isBlank() ? null : YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 振り返りタブ。習慣（一覧から外したものも含む）・期間・メモの有無で絞り込める。 */
    @GetMapping("/stats/notes")
    String notes(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                 @RequestParam(required = false) Long habit,
                 @RequestParam(defaultValue = "30") String period,
                 @RequestParam(defaultValue = "false") boolean notesOnly, Model model) {
        Period p = Period.parse(period);
        var habits = journal.allHabits(me);
        model.addAttribute("activeHabits", habits.stream().filter(h -> h.isActive()).toList());
        model.addAttribute("archivedHabits", habits.stream().filter(h -> !h.isActive()).toList());
        model.addAttribute("periods", Period.values());
        model.addAttribute("selectedHabit", habit);
        model.addAttribute("period", p);
        model.addAttribute("notesOnly", notesOnly);
        model.addAttribute("journal", journal.journal(me, habit, p, notesOnly, LocalDate.now()));
        return "stats-notes";
    }
}
