package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.FriendService.FriendException;
import com.olafreset.service.FriendService.FriendRow;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FriendServiceTests {

    @Autowired
    FriendService friends;

    @Autowired
    UserService users;

    @Autowired
    HabitService habits;

    AppUser alice;
    AppUser bob;
    AppUser carol;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        alice = users.register("alice", "password1");
        bob = users.register("bob", "password2");
        carol = users.register("carol", "password3");
    }

    @Test
    void requestNeedsApprovalBeforeBecomingFriends() {
        assertThat(friends.request(alice, "bob")).isFalse();
        assertThat(friends.areFriends(alice, bob)).isFalse();
        assertThat(friends.outgoing(alice)).hasSize(1);
        assertThat(friends.incomingCount(bob)).isEqualTo(1);

        AppUser requester = friends.accept(bob, friends.incoming(bob).getFirst().getId());

        assertThat(requester.getUsername()).isEqualTo("alice");
        assertThat(friends.areFriends(alice, bob)).isTrue();
        assertThat(friends.friends(alice)).extracting(AppUser::getUsername).containsExactly("bob");
        assertThat(friends.friends(bob)).extracting(AppUser::getUsername).containsExactly("alice");
        assertThat(friends.incomingCount(bob)).isZero();
    }

    @Test
    void requestingBackAcceptsTheExistingRequest() {
        friends.request(alice, "bob");
        assertThat(friends.request(bob, "Alice")).isTrue();
        assertThat(friends.areFriends(alice, bob)).isTrue();
    }

    @Test
    void invalidRequestsAreExplained() {
        assertThatThrownBy(() -> friends.request(alice, "nobody")).isInstanceOf(FriendException.class)
                .hasMessageContaining("見つかりません");
        assertThatThrownBy(() -> friends.request(alice, "alice")).isInstanceOf(FriendException.class)
                .hasMessageContaining("自分自身");
        friends.request(alice, "bob");
        assertThatThrownBy(() -> friends.request(alice, "bob")).isInstanceOf(FriendException.class)
                .hasMessageContaining("すでに申請");
    }

    @Test
    void onlyAddresseeCanAcceptButBothCanDismiss() {
        friends.request(alice, "bob");
        long id = friends.outgoing(alice).getFirst().getId();

        assertThatThrownBy(() -> friends.accept(alice, id)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> friends.accept(carol, id)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> friends.dismiss(carol, id)).isInstanceOf(NoSuchElementException.class);

        friends.dismiss(alice, id);
        assertThat(friends.incoming(bob)).isEmpty();
    }

    @Test
    void inviteLinkMakesFriendsImmediately() {
        String code = friends.inviteCode(alice);
        assertThat(friends.inviteCode(alice)).isEqualTo(code);

        AppUser inviter = friends.acceptInvite(bob, code);

        assertThat(inviter.getUsername()).isEqualTo("alice");
        assertThat(friends.areFriends(alice, bob)).isTrue();
        assertThatThrownBy(() -> friends.acceptInvite(alice, code)).isInstanceOf(FriendException.class);
        assertThatThrownBy(() -> friends.acceptInvite(carol, "WRONGCODE")).isInstanceOf(FriendException.class);
    }

    @Test
    void resetInviteCodeInvalidatesOldLink() {
        String old = friends.inviteCode(alice);
        friends.resetInviteCode(alice);
        assertThat(friends.inviteCode(alice)).isNotEqualTo(old);
        assertThatThrownBy(() -> friends.acceptInvite(bob, old)).isInstanceOf(FriendException.class);
    }

    @Test
    void boardShowsOnlySharedHabitsAndSortsByProgress() {
        makeFriends(alice, bob);
        makeFriends(alice, carol);
        Habit run = habits.create(bob, "ランニング", Category.BODY, 10);
        Habit diary = habits.create(bob, "秘密の日記", Category.MIND, 10);
        habits.toggleShared(bob, diary.getId());
        habits.toggle(bob, run.getId(), today);
        habits.toggle(bob, diary.getId(), today);
        habits.create(carol, "読書", Category.SKILL, 10);

        var board = friends.board(alice, today);

        assertThat(board).extracting(r -> r.user().getUsername()).containsExactly("bob", "carol");
        FriendRow bobRow = board.getFirst();
        assertThat(bobRow.habits()).extracting(r -> r.habit().getName()).containsExactly("ランニング");
        assertThat(bobRow.done()).isEqualTo(1);
        assertThat(bobRow.total()).isEqualTo(1);
        assertThat(bobRow.percent()).isEqualTo(100);
        assertThat(board.get(1).percent()).isZero();
    }

    @Test
    void cheerIsOncePerDayAndOnlyForFriends() {
        makeFriends(alice, bob);

        assertThat(friends.toggleCheer(alice, bob.getId(), today)).isTrue();
        assertThat(friends.cheersReceived(bob, today)).extracting(AppUser::getUsername).containsExactly("alice");
        assertThat(friends.board(alice, today).getFirst().cheered()).isTrue();

        assertThat(friends.toggleCheer(alice, bob.getId(), today)).isFalse();
        assertThat(friends.cheersReceived(bob, today)).isEmpty();

        assertThatThrownBy(() -> friends.toggleCheer(alice, carol.getId(), today))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removingFriendAndDeletingUserCleansUp() {
        makeFriends(alice, bob);
        makeFriends(alice, carol);
        friends.toggleCheer(bob, alice.getId(), today);

        friends.remove(alice, bob.getId());
        assertThat(friends.areFriends(alice, bob)).isFalse();
        assertThat(friends.cheersReceived(alice, today)).isEmpty();

        users.delete(alice, carol.getId());
        assertThat(friends.friends(alice)).isEmpty();
    }

    private void makeFriends(AppUser a, AppUser b) {
        friends.acceptInvite(b, friends.inviteCode(a));
    }
}
