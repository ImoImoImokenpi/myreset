package com.olafreset.web;

import com.olafreset.domain.AppUser;
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

    public StatsController(StatsService stats, JournalService journal) {
        this.stats = stats;
        this.journal = journal;
    }

    @GetMapping("/stats")
    String stats(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                 @RequestParam(required = false) String month, Model model) {
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);
        YearMonth shown = current;
        if (month != null) {
            try {
                shown = YearMonth.parse(month);
            } catch (DateTimeParseException ignored) {
                // 形式がおかしいときは今月を表示する
            }
        }
        if (shown.isAfter(current)) {
            shown = current;
        }
        model.addAttribute("calendar", stats.calendar(me, shown, today));
        model.addAttribute("prevMonth", shown.minusMonths(1));
        model.addAttribute("nextMonth", shown.isBefore(current) ? shown.plusMonths(1) : null);
        model.addAttribute("habitStats", stats.habitStats(me, today));
        return "stats";
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
