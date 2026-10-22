package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.UserService;
import com.olafreset.service.UserService.CannotDeleteSelfException;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 管理者用のユーザー管理画面。/admin/** は SecurityConfig で ADMIN のみに制限している。 */
@Controller
public class AdminController {

    /** 一覧の1行分。 */
    public record UserRow(AppUser user, long habitCount) {
    }

    private final UserService users;

    public AdminController(UserService users) {
        this.users = users;
    }

    @GetMapping("/admin/users")
    String list(Model model) {
        List<UserRow> rows = users.all().stream()
                .map(u -> new UserRow(u, users.habitCount(u)))
                .toList();
        model.addAttribute("rows", rows);
        return "admin-users";
    }

    @PostMapping("/admin/users/{id}/delete")
    String delete(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @PathVariable long id, RedirectAttributes ra) {
        try {
            users.delete(me, id);
            ra.addFlashAttribute("message", "ユーザーを削除しました");
        } catch (CannotDeleteSelfException e) {
            ra.addFlashAttribute("message", "自分自身は削除できません");
        }
        return "redirect:/admin/users";
    }
}
