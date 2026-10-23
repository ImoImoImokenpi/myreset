package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
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
    void quantityHabitCompletesWhenTargetIsReached() {
        Habit water = service.create(alice, "水を飲む", Category.LIFE, 10, Set.of(DayOfWeek.values()), 8, 2, "杯");
        LocalDate today = LocalDate.now();

        assertThat(service.addAmount(alice, water.getId(), today, 2)).isEqualTo(2);
        assertThat(service.addAmount(alice, water.getId(), today, 4)).isEqualTo(6);
        var row = service.rows(alice, today).getFirst();
        assertThat(row.doneToday()).isFalse();
        assertThat(row.amountToday()).isEqualTo(6);
        assertThat(row.percent()).isEqualTo(75);
        // 進めた分だけ XP がもらえる：10 × 6 ÷ 8 = 7（切り捨て）
        assertThat(row.earnedToday()).isEqualTo(7);
        assertThat(service.totalXp(alice)).isEqualTo(7);

        service.addAmount(alice, water.getId(), today, 2);
        assertThat(service.rows(alice, today).getFirst().doneToday()).isTrue();
        assertThat(service.totalXp(alice)).isEqualTo(10);

        // 目標を超えても満額まで
        service.addAmount(alice, water.getId(), today, 4);
        assertThat(service.totalXp(alice)).isEqualTo(10);

        // 減らして目標を下回ったら未達成に戻り、XP も減る。0 になったら記録が消える
        service.addAmount(alice, water.getId(), today, -6);
        assertThat(service.rows(alice, today).getFirst().doneToday()).isFalse();
        assertThat(service.totalXp(alice)).isEqualTo(7);
        assertThat(service.addAmount(alice, water.getId(), today, -100)).isZero();
        assertThat(service.rows(alice, today).getFirst().amountToday()).isZero();
    }

    @Test
    void partialXpMatchesTheExample() {
        // 50XP・目標30分で10分なら 50 × 10 ÷ 30 = 16（切り捨て）
        Habit study = service.create(alice, "勉強", Category.SKILL, 50, Set.of(DayOfWeek.values()), 30, 10, "分");
        LocalDate today = LocalDate.now();

        service.addAmount(alice, study.getId(), today, 10);

        assertThat(service.rows(alice, today).getFirst().earnedToday()).isEqualTo(16);
        assertThat(service.totalXp(alice)).isEqualTo(16);
        assertThat(service.level(alice).xpIntoLevel()).isEqualTo(16);
        // 未達成なので連続日数には数えない
        assertThat(service.rows(alice, today).getFirst().streak()).isZero();
    }

    @Test
    void lastWeekShowsAverageProgressIncludingPartialAmounts() {
        LocalDate today = LocalDate.now();
        Habit water = service.create(alice, "水", Category.LIFE, 10, Set.of(DayOfWeek.values()), 8, 1, "杯");
        Habit pushUp = service.create(alice, "腕立て", Category.BODY, 10);
        service.create(alice, "読書", Category.SKILL, 10);

        assertThat(service.lastWeek(alice, today).getLast().percent()).isZero();

        // 水 4/8（50%）＋ 腕立て 達成（100%）＋ 読書 0% → 150 ÷ 3 = 50%
        service.addAmount(alice, water.getId(), today, 4);
        service.toggle(alice, pushUp.getId(), today);
        var day = service.lastWeek(alice, today).getLast();
        assertThat(day.percent()).isEqualTo(50);
        assertThat(day.done()).isEqualTo(1);
        assertThat(day.total()).isEqualTo(3);
        assertThat(day.xp()).isEqualTo(5 + 10);

        // 目標を超えても 1つの習慣は 100% まで
        service.addAmount(alice, water.getId(), today, 20);
        assertThat(service.lastWeek(alice, today).getLast().percent()).isEqualTo(67);
    }

    @Test
    void checkOnQuantityHabitFillsTargetAndUncheckClears() {
        Habit reading = service.create(alice, "読書", Category.SKILL, 15, Set.of(DayOfWeek.values()), 30, 10, "分");
        LocalDate today = LocalDate.now();
        service.addAmount(alice, reading.getId(), today, 10);

        service.toggle(alice, reading.getId(), today);
        var row = service.rows(alice, today).getFirst();
        assertThat(row.doneToday()).isTrue();
        assertThat(row.amountToday()).isEqualTo(30);

        service.toggle(alice, reading.getId(), today);
        assertThat(service.rows(alice, today).getFirst().amountToday()).isZero();
    }

    @Test
    void habitsOffTodayAreNotUnfinished() {
        LocalDate today = LocalDate.now();
        DayOfWeek other = today.getDayOfWeek().plus(1);
        service.create(alice, "今日やる", Category.BODY, 10, Set.of(today.getDayOfWeek()), 1, 1, null);
        service.create(alice, "今日は休み", Category.BODY, 10, Set.of(other), 1, 1, null);

        var rows = service.rows(alice, today);
        assertThat(rows).filteredOn(HabitService.HabitRow::scheduledToday)
                .extracting(r -> r.habit().getName()).containsExactly("今日やる");
        assertThat(service.unfinishedToday(alice, today)).extracting(Habit::getName).containsExactly("今日やる");
        assertThat(service.lastWeek(alice, today).getLast().total()).isEqualTo(1);
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

    @Test
    void renameChangesOnlyTheNameOfOwnHabit() {
        Habit habit = service.create(alice, "読書", Category.SKILL, 15);
        service.toggle(alice, habit.getId(), LocalDate.now());

        service.rename(alice, habit.getId(), "  読書30分  ");

        Habit renamed = service.activeHabits(alice).getFirst();
        assertThat(renamed.getName()).isEqualTo("読書30分");
        assertThat(renamed.getCategory()).isEqualTo(Category.SKILL);
        assertThat(renamed.getXp()).isEqualTo(15);
        assertThat(service.totalXp(alice)).isEqualTo(15);
        assertThatThrownBy(() -> service.rename(bob, habit.getId(), "乗っ取り"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
