package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.StatsService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** 記録ページ（月のカレンダーと習慣ごとの統計）。 */
@Controller
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
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
}
