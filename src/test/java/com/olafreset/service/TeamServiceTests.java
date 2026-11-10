package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.TeamService.Member;
import com.olafreset.service.TeamService.TeamException;
import com.olafreset.service.TeamService.TeamRow;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TeamServiceTests {

    @Autowired
    TeamService teams;

    @Autowired
    FriendService friends;

    @Autowired
    UserService users;

    @Autowired
    HabitService habits;

    @Autowired
    EntityManager em;

    AppUser alice;
    AppUser bob;
    AppUser carol;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        alice = users.register("alice", "password1");
        bob = users.register("bob", "password2");
        carol = users.register("carol", "password3");
        makeFriends(alice, bob);
        makeFriends(alice, carol);
    }

    @Test
    void acceptingInviteCopiesTheHabit() {
        Habit water = habits.create(alice, "水を飲む", Category.BODY, 20, Set.of(DayOfWeek.values()), 8, 1, "杯");

        teams.invite(alice, water.getId(), bob.getId());

        assertThat(teams.incomingCount(bob)).isEqualTo(1);
        long inviteId = teams.incoming(bob).getFirst().getId();
        // 本番と同じく、招待元の習慣がまだ読み込まれていない（遅延読み込みになる）状態で参加する
        em.flush();
        em.clear();
        Habit bobs = teams.accept(bob, inviteId, today);

        assertThat(bobs.getOwner().getId()).isEqualTo(bob.getId());
        assertThat(bobs.getName()).isEqualTo("水を飲む");
        assertThat(bobs.getXp()).isEqualTo(20);
        assertThat(bobs.getTarget()).isEqualTo(8);
        assertThat(bobs.getUnit()).isEqualTo("杯");
        assertThat(teams.incomingCount(bob)).isZero();
        assertThat(teams.teams(alice, today).getFirst().members())
                .extracting(m -> m.user().getUsername()).containsExactly("alice", "bob");
    }

    @Test
    void everyoneDoneClearsTheDayAndGivesBonus() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        Habit bobs = join(run, bob);

        habits.toggle(alice, run.getId(), today);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isFalse();
        assertThat(habits.totalXp(alice)).isEqualTo(10);

        habits.toggle(bob, bobs.getId(), today);
        TeamRow row = teams.teams(alice, today).getFirst();
        assertThat(row.cleared()).isTrue();
        assertThat(row.done()).isEqualTo(2);
        assertThat(row.streak()).isEqualTo(1);
        assertThat(habits.totalXp(alice)).isEqualTo(15);
        assertThat(habits.totalXp(bob)).isEqualTo(15);
        assertThat(teams.statusByHabit(bob, today).get(bobs.getId()).cleared()).isTrue();

        // 1人がチェックを外すと、全員のボーナスが取り消される
        habits.toggle(bob, bobs.getId(), today);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isFalse();
        assertThat(habits.totalXp(alice)).isEqualTo(10);
        assertThat(habits.totalXp(bob)).isZero();
    }

    @Test
    void quantityHabitClearsOnlyWhenEveryoneReachesTheTarget() {
        Habit water = habits.create(alice, "水を飲む", Category.BODY, 20, Set.of(DayOfWeek.values()), 2, 1, "杯");
        Habit bobs = join(water, bob);

        habits.addAmount(alice, water.getId(), today, 2);
        habits.addAmount(bob, bobs.getId(), today, 1);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isFalse();

        habits.addAmount(bob, bobs.getId(), today, 1);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isTrue();
    }

    @Test
    void newMemberMustAlsoFinishAndLeavingRejudges() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        Habit bobs = join(run, bob);
        habits.toggle(alice, run.getId(), today);
        habits.toggle(bob, bobs.getId(), today);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isTrue();

        // carol が参加すると、carol も達成するまでクリアではない
        Habit carols = join(run, carol);
        assertThat(teams.teams(alice, today).getFirst().cleared()).isFalse();

        // carol が抜けると、残りの2人でクリアに戻る
        habits.archive(carol, carols.getId());
        assertThat(teams.teams(alice, today).getFirst().cleared()).isTrue();
    }

    @Test
    void aloneIsNeverCleared() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        teams.invite(alice, run.getId(), bob.getId());
        habits.toggle(alice, run.getId(), today);

        TeamRow row = teams.teams(alice, today).getFirst();
        assertThat(row.members()).extracting(Member::me).containsExactly(true);
        assertThat(row.invited()).extracting(i -> i.getInvitee().getUsername()).containsExactly("bob");
        assertThat(row.invitable()).extracting(AppUser::getUsername).containsExactly("carol");
        assertThat(row.cleared()).isFalse();
    }

    @Test
    void renamingRenamesEveryonesHabit() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        Habit bobs = join(run, bob);

        habits.rename(alice, run.getId(), "朝ラン");

        assertThat(habits.activeHabits(bob)).extracting(Habit::getName).containsExactly("朝ラン");
        assertThat(bobs.getId()).isNotNull();
    }

    @Test
    void invalidInvitesAreRejected() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        Habit bobsOwn = habits.create(bob, "読書", Category.SKILL, 10);

        // フレンドでない人・他人の習慣は存在しないものとして扱う
        assertThatThrownBy(() -> teams.invite(bob, bobsOwn.getId(), carol.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> teams.invite(bob, run.getId(), alice.getId()))
                .isInstanceOf(NoSuchElementException.class);

        teams.invite(alice, run.getId(), bob.getId());
        assertThatThrownBy(() -> teams.invite(alice, run.getId(), bob.getId()))
                .isInstanceOf(TeamException.class).hasMessageContaining("すでに招待");

        teams.accept(bob, teams.incoming(bob).getFirst().getId(), today);
        assertThatThrownBy(() -> teams.invite(alice, run.getId(), bob.getId()))
                .isInstanceOf(TeamException.class).hasMessageContaining("すでに参加");
    }

    @Test
    void inviteToFinishedHabitCannotBeAccepted() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        teams.invite(alice, run.getId(), bob.getId());
        long inviteId = teams.incoming(bob).getFirst().getId();
        habits.archive(alice, run.getId());

        assertThatThrownBy(() -> teams.accept(bob, inviteId, today))
                .isInstanceOf(TeamException.class).hasMessageContaining("終了");
        assertThat(teams.incomingCount(bob)).isZero();
    }

    @Test
    void inviteCanBeDeclinedAndIsRemovedWithFriendshipOrUser() {
        Habit run = habits.create(alice, "ランニング", Category.BODY, 10);
        teams.invite(alice, run.getId(), bob.getId());
        teams.dismiss(bob, teams.incoming(bob).getFirst().getId());
        assertThat(teams.incomingCount(bob)).isZero();

        teams.invite(alice, run.getId(), bob.getId());
        friends.remove(alice, bob.getId());
        assertThat(teams.incomingCount(bob)).isZero();

        teams.invite(alice, run.getId(), carol.getId());
        users.delete(bob, alice.getId());
        assertThat(teams.incomingCount(carol)).isZero();
    }

    private Habit join(Habit habit, AppUser friend) {
        teams.invite(habit.getOwner(), habit.getId(), friend.getId());
        return teams.accept(friend, teams.incoming(friend).getFirst().getId(), today);
    }

    private void makeFriends(AppUser a, AppUser b) {
        friends.acceptInvite(b, friends.inviteCode(a));
    }
}
