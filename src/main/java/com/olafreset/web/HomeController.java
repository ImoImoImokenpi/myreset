package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.FriendService;
import com.olafreset.service.HabitService;
import com.olafreset.service.HabitService.HabitRow;
import com.olafreset.service.Level;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final HabitService service;
    private final FriendService friends;

    public HomeController(HabitService service, FriendService friends) {
        this.service = service;
        this.friends = friends;
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    /** 曜日の選択肢（月〜日）。 */
    public record WeekdayOption(DayOfWeek day, String label) {
    }

    @ModelAttribute("weekdays")
    List<WeekdayOption> weekdays() {
        return Arrays.stream(DayOfWeek.values()).map(d -> new WeekdayOption(d, Habit.shortName(d))).toList();
    }

    @GetMapping("/")
    String dashboard(@ModelAttribute(name = "currentUser", binding = false) AppUser me, Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        List<HabitRow> rows = service.rows(me, today);
        // 今日が実施日の習慣と、お休みの習慣に分ける
        List<HabitRow> todays = rows.stream().filter(HabitRow::scheduledToday).toList();
        model.addAttribute("rows", todays);
        model.addAttribute("restRows", rows.stream().filter(r -> !r.scheduledToday()).toList());
        model.addAttribute("doneCount", todays.stream().filter(HabitRow::doneToday).count());
        model.addAttribute("bestStreak", rows.stream().mapToInt(HabitRow::streak).max().orElse(0));
        model.addAttribute("week", service.lastWeek(me, today));
        long totalXp = service.totalXp(me);
        model.addAttribute("level", Level.fromTotalXp(totalXp));
        model.addAttribute("totalXp", totalXp);
        model.addAttribute("reflection", service.reflectionText(me, today));
        model.addAttribute("cheers", friends.cheersReceived(me, today));
        return "dashboard";
    }

    @PostMapping("/habits/{id}/toggle")
    String toggle(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long id) {
        service.toggle(me, id, LocalDate.now());
        return "redirect:/";
    }

    /** 量の習慣の「＋」「−」。 */
    @PostMapping("/habits/{id}/amount")
    String amount(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long id,
                  @RequestParam int delta) {
        service.addAmount(me, id, LocalDate.now(), Math.max(-10000, Math.min(10000, delta)));
        return "redirect:/";
    }

    @PostMapping("/reflection")
    String saveReflection(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                          @RequestParam(defaultValue = "") String text, RedirectAttributes ra) {
        service.saveReflection(me, LocalDate.now(), text);
        ra.addFlashAttribute("message", "振り返りを保存しました");
        return "redirect:/";
    }

    @GetMapping("/habits")
    String habits(@ModelAttribute(name = "currentUser", binding = false) AppUser me, Model model) {
        model.addAttribute("habits", service.activeHabits(me));
        if (!model.containsAttribute("habitForm")) {
            model.addAttribute("habitForm", new HabitForm());
        }
        return "habits";
    }

    @PostMapping("/habits")
    String create(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @Valid HabitForm habitForm, BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("habits", service.activeHabits(me));
            return "habits";
        }
        boolean quantity = habitForm.isUseTarget();
        service.create(me, habitForm.getName(), habitForm.getCategory(), habitForm.getXp(), habitForm.getDays(),
                quantity ? habitForm.getTarget() : 1, quantity ? habitForm.getStep() : 1,
                quantity ? habitForm.getUnit() : null);
        ra.addFlashAttribute("message", "「" + habitForm.getName().strip() + "」を追加しました");
        return "redirect:/habits";
    }

    @PostMapping("/habits/{id}/rename")
    String rename(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long id,
                  @RequestParam String name, RedirectAttributes ra) {
        String newName = name.strip();
        if (newName.isEmpty() || newName.length() > 50) {
            ra.addFlashAttribute("message", "名前は1〜50文字にしてください");
            return "redirect:/habits";
        }
        service.rename(me, id, newName);
        ra.addFlashAttribute("message", "「" + newName + "」に変更しました");
        return "redirect:/habits";
    }

    @PostMapping("/habits/{id}/share")
    String toggleShare(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                       @PathVariable long id, RedirectAttributes ra) {
        boolean shared = service.toggleShared(me, id);
        ra.addFlashAttribute("message", shared ? "フレンドに見せるようにしました" : "フレンドに見せないようにしました");
        return "redirect:/habits";
    }

    @PostMapping("/habits/{id}/archive")
    String archive(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                   @PathVariable long id, RedirectAttributes ra) {
        service.archive(me, id);
        ra.addFlashAttribute("message", "習慣を一覧から外しました（獲得XPは残ります）");
        return "redirect:/habits";
    }
}
