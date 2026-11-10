package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.service.TeamService;
import com.olafreset.service.TeamService.TeamException;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** フレンドと一緒にやる習慣の招待・参加。画面はフレンドページにある。 */
@Controller
public class TeamController {

    private final TeamService teams;

    public TeamController(TeamService teams) {
        this.teams = teams;
    }

    @PostMapping("/teams/invite")
    String invite(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @RequestParam long habitId, @RequestParam long userId, RedirectAttributes ra) {
        try {
            teams.invite(me, habitId, userId);
            ra.addFlashAttribute("message", "招待しました。相手が参加すると一緒に取り組めます");
        } catch (TeamException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/friends";
    }

    @PostMapping("/teams/invites/{id}/accept")
    String accept(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @PathVariable long id, RedirectAttributes ra) {
        try {
            Habit habit = teams.accept(me, id, LocalDate.now());
            ra.addFlashAttribute("message", "「" + habit.getName() + "」に参加しました");
        } catch (TeamException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/friends";
    }

    @PostMapping("/teams/invites/{id}/dismiss")
    String dismiss(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long id) {
        teams.dismiss(me, id);
        return "redirect:/friends";
    }
}
