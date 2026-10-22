package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.UserService;
import com.olafreset.service.UserService.UsernameTakenException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ログイン・新規登録・パスワード変更。 */
@Controller
public class AccountController {

    private final UserService users;

    public AccountController(UserService users) {
        this.users = users;
    }

    @GetMapping("/login")
    String login(Principal principal) {
        return principal == null ? "login" : "redirect:/";
    }

    @GetMapping("/register")
    String registerForm(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("registerForm", new RegisterForm());
        addRegisterInfo(model);
        return "register";
    }

    private void addRegisterInfo(Model model) {
        model.addAttribute("registrationOpen", users.isRegistrationOpen());
        model.addAttribute("firstUser", users.all().isEmpty());
    }

    @PostMapping("/register")
    String register(@Valid RegisterForm registerForm, BindingResult result, Model model,
                    HttpServletRequest request, RedirectAttributes ra) throws ServletException {
        if (!users.isRegistrationOpen()) {
            return "redirect:/register";
        }
        if (!result.hasFieldErrors("username") && users.isTaken(registerForm.getUsername())) {
            result.rejectValue("username", "taken", "このユーザー名はすでに使われています");
        }
        if (!result.hasErrors()) {
            try {
                users.register(registerForm.getUsername(), registerForm.getPassword());
            } catch (UsernameTakenException e) {
                result.rejectValue("username", "taken", "このユーザー名はすでに使われています");
            }
        }
        if (result.hasErrors()) {
            addRegisterInfo(model);
            return "register";
        }
        // 登録したらそのままログイン状態にする
        request.login(registerForm.getUsername().strip(), registerForm.getPassword());
        ra.addFlashAttribute("message", "ようこそ、" + registerForm.getUsername().strip() + " さん！");
        return "redirect:/";
    }

    @ModelAttribute("themePresets")
    List<ThemeColor> themePresets() {
        return ThemeColor.PRESETS;
    }

    @GetMapping("/account")
    String account(Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        return "account";
    }

    @PostMapping("/account/theme")
    String changeTheme(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                       @RequestParam String color, RedirectAttributes ra) {
        if (ThemeColor.isValid(color)) {
            users.changeThemeColor(me, color);
            ra.addFlashAttribute("message", "メインカラーを変更しました");
        } else {
            ra.addFlashAttribute("message", "色の形式が正しくありません");
        }
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    String changePassword(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                          @Valid PasswordForm passwordForm, BindingResult result, RedirectAttributes ra) {
        if (!result.hasErrors()
                && !users.changePassword(me, passwordForm.getCurrentPassword(), passwordForm.getNewPassword())) {
            result.rejectValue("currentPassword", "wrong", "現在のパスワードが違います");
        }
        if (result.hasErrors()) {
            return "account";
        }
        ra.addFlashAttribute("message", "パスワードを変更しました");
        return "redirect:/account";
    }
}
