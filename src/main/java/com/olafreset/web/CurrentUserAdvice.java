package com.olafreset.web;

import com.olafreset.domain.AppUser;
import com.olafreset.service.FriendService;
import com.olafreset.service.UserService;
import java.security.Principal;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** ログイン中のユーザーを、すべての画面とコントローラーで "currentUser" として使えるようにする。 */
@ControllerAdvice
class CurrentUserAdvice {

    private final UserService users;
    private final FriendService friends;

    CurrentUserAdvice(UserService users, FriendService friends) {
        this.users = users;
        this.friends = friends;
    }

    @ModelAttribute("currentUser")
    AppUser currentUser(Principal principal) {
        return principal == null ? null : users.require(principal.getName());
    }

    /** ナビの「フレンド」に出す、届いている申請の数。 */
    @ModelAttribute("friendRequestCount")
    long friendRequestCount(Principal principal) {
        return principal == null ? 0 : friends.incomingCount(users.require(principal.getName()));
    }

    /** 存在しない、または他のユーザーのデータを指定されたとき。 */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
