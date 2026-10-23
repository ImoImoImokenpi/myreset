package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.FriendService;
import com.olafreset.service.FriendService.FriendException;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** フレンドの一覧・申請・招待リンク・応援。 */
@Controller
public class FriendController {

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping("/friends")
    String friends(@ModelAttribute(name = "currentUser", binding = false) AppUser me, Model model) {
        model.addAttribute("board", friends.board(me, LocalDate.now()));
        model.addAttribute("incoming", friends.incoming(me));
        model.addAttribute("outgoing", friends.outgoing(me));
        model.addAttribute("inviteUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/invite/{code}").buildAndExpand(friends.inviteCode(me)).toUriString());
        return "friends";
    }

    @PostMapping("/friends/request")
    String request(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                   @RequestParam String username, RedirectAttributes ra) {
        try {
            boolean nowFriends = friends.request(me, username);
            ra.addFlashAttribute("message", nowFriends
                    ? username.strip() + " さんとフレンドになりました"
                    : username.strip() + " さんに申請しました");
        } catch (FriendException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/friends";
    }

    @PostMapping("/friends/requests/{id}/accept")
    String accept(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @PathVariable long id, RedirectAttributes ra) {
        AppUser friend = friends.accept(me, id);
        ra.addFlashAttribute("message", friend.getUsername() + " さんとフレンドになりました");
        return "redirect:/friends";
    }

    @PostMapping("/friends/requests/{id}/dismiss")
    String dismiss(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long id) {
        friends.dismiss(me, id);
        return "redirect:/friends";
    }

    @PostMapping("/friends/{userId}/remove")
    String remove(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @PathVariable long userId, RedirectAttributes ra) {
        friends.remove(me, userId);
        ra.addFlashAttribute("message", "フレンドを解除しました");
        return "redirect:/friends";
    }

    @PostMapping("/friends/{userId}/cheer")
    String cheer(@ModelAttribute(name = "currentUser", binding = false) AppUser me, @PathVariable long userId) {
        friends.toggleCheer(me, userId, LocalDate.now());
        return "redirect:/friends";
    }

    @PostMapping("/friends/invite/reset")
    String resetInvite(@ModelAttribute(name = "currentUser", binding = false) AppUser me, RedirectAttributes ra) {
        friends.resetInviteCode(me);
        ra.addFlashAttribute("message", "招待リンクを作り直しました（前のリンクは使えません）");
        return "redirect:/friends";
    }

    /** 招待リンク。未ログインならログイン（または登録）後にここへ戻ってくる。 */
    @GetMapping("/invite/{code}")
    String invite(@ModelAttribute(name = "currentUser", binding = false) AppUser me,
                  @PathVariable String code, RedirectAttributes ra) {
        try {
            AppUser inviter = friends.acceptInvite(me, code);
            ra.addFlashAttribute("message", inviter.getUsername() + " さんとフレンドになりました");
        } catch (FriendException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/friends";
    }
}
