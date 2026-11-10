package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.FriendService;
import com.olafreset.service.TeamService;
import com.olafreset.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** ログイン中のユーザーを、すべての画面とコントローラーで "currentUser" として使えるようにする。 */
@ControllerAdvice
class CurrentUserAdvice {

    private static final String CACHE_KEY = CurrentUserAdvice.class.getName() + ".user";

    private final UserService users;
    private final FriendService friends;
    private final TeamService teams;

    CurrentUserAdvice(UserService users, FriendService friends, TeamService teams) {
        this.users = users;
        this.friends = friends;
        this.teams = teams;
    }

    @ModelAttribute("currentUser")
    AppUser currentUser(Principal principal, HttpServletRequest request) {
        return lookup(principal, request);
    }

    /** ナビの「フレンド」に出す、届いているフレンド申請と「一緒にやろう」の招待の数。 */
    @ModelAttribute("friendRequestCount")
    long friendRequestCount(Principal principal, HttpServletRequest request) {
        AppUser me = lookup(principal, request);
        return me == null ? 0 : friends.incomingCount(me) + teams.incomingCount(me);
    }

    /** 1回のリクエストの中ではユーザーを1度だけ読み込む。 */
    private AppUser lookup(Principal principal, HttpServletRequest request) {
        if (principal == null) {
            return null;
        }
        Object cached = request.getAttribute(CACHE_KEY);
        if (cached instanceof AppUser user) {
            return user;
        }
        AppUser user = users.require(principal.getName());
        request.setAttribute(CACHE_KEY, user);
        return user;
    }

    /** 存在しない、または他のユーザーのデータを指定されたとき。 */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
