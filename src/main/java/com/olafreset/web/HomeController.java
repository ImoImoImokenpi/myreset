package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.service.FriendService;
import com.olafreset.service.HabitService;
import com.olafreset.service.HabitService.HabitRow;
import com.olafreset.service.Level;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

    @GetMapping("/")
    String dashboard(@ModelAttribute(name = "currentUser", binding = false) AppUser me, Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        List<HabitRow> rows = service.rows(me, today);
        model.addAttribute("rows", rows);
        model.addAttribute("doneCount", rows.stream().filter(HabitRow::doneToday).count());
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
        service.create(me, habitForm.getName(), habitForm.getCategory(), habitForm.getXp());
        ra.addFlashAttribute("message", "「" + habitForm.getName().strip() + "」を追加しました");
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
