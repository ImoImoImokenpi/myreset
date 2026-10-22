package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
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
class HabitServiceTests {

    @Autowired
    HabitService service;

    @Autowired
    UserService users;

    AppUser alice;
    AppUser bob;

    @BeforeEach
    void setUp() {
        alice = users.register("alice", "password1");
        bob = users.register("bob", "password2");
    }

    @Test
    void levelUpsEvery100TimesLevelXp() {
        assertThat(Level.fromTotalXp(0)).isEqualTo(new Level(1, 0, 100));
        assertThat(Level.fromTotalXp(99)).isEqualTo(new Level(1, 99, 100));
        assertThat(Level.fromTotalXp(100)).isEqualTo(new Level(2, 0, 200));
        assertThat(Level.fromTotalXp(350)).isEqualTo(new Level(3, 50, 300));
    }

    @Test
    void streakCountsBackFromTodayOrYesterday() {
        LocalDate today = LocalDate.of(2026, 10, 22);
        assertThat(HabitService.streak(Set.of(), today)).isZero();
        assertThat(HabitService.streak(Set.of(today, today.minusDays(1), today.minusDays(3)), today)).isEqualTo(2);
        // 今日まだ未達成でも、昨日までの連続は途切れていない
        assertThat(HabitService.streak(Set.of(today.minusDays(1), today.minusDays(2)), today)).isEqualTo(2);
        assertThat(HabitService.streak(Set.of(today.minusDays(2)), today)).isZero();
    }

    @Test
    void toggleAddsAndRemovesXp() {
        Habit habit = service.create(alice, "  読書  ", Category.SKILL, 15);
        LocalDate today = LocalDate.now();

        service.toggle(alice, habit.getId(), today);
        assertThat(service.totalXp(alice)).isEqualTo(15);
        assertThat(service.rows(alice, today).getFirst().doneToday()).isTrue();
        assertThat(service.rows(alice, today).getFirst().habit().getName()).isEqualTo("読書");

        service.toggle(alice, habit.getId(), today);
        assertThat(service.totalXp(alice)).isZero();
    }

    @Test
    void archivedHabitKeepsEarnedXp() {
        Habit habit = service.create(alice, "ランニング", Category.BODY, 20);
        service.toggle(alice, habit.getId(), LocalDate.now());

        service.archive(alice, habit.getId());

        assertThat(service.activeHabits(alice)).isEmpty();
        assertThat(service.totalXp(alice)).isEqualTo(20);
    }

    @Test
    void usersCannotSeeOrTouchEachOthersData() {
        Habit alicesHabit = service.create(alice, "瞑想", Category.MIND, 10);
        service.toggle(alice, alicesHabit.getId(), LocalDate.now());
        service.saveReflection(alice, LocalDate.now(), "alice のメモ");

        assertThat(service.activeHabits(bob)).isEmpty();
        assertThat(service.totalXp(bob)).isZero();
        assertThat(service.reflectionText(bob, LocalDate.now())).isEmpty();
        assertThatThrownBy(() -> service.toggle(bob, alicesHabit.getId(), LocalDate.now()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.archive(bob, alicesHabit.getId()))
                .isInstanceOf(NoSuchElementException.class);

        // 同じ日に別々の振り返りを書ける
        service.saveReflection(bob, LocalDate.now(), "bob のメモ");
        assertThat(service.reflectionText(alice, LocalDate.now())).isEqualTo("alice のメモ");
        assertThat(service.reflectionText(bob, LocalDate.now())).isEqualTo("bob のメモ");
    }
}
