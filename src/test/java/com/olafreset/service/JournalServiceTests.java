package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.JournalService.Entry;
import com.olafreset.service.JournalService.Journal;
import com.olafreset.service.JournalService.Period;
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
class JournalServiceTests {

    @Autowired
    JournalService journal;

    @Autowired
    HabitService habits;

    @Autowired
    UserService users;

    @Autowired
    EntityManager em;

    AppUser alice;
    LocalDate today = LocalDate.now();
    Habit run;
    Habit water;

    @BeforeEach
    void setUp() {
        alice = users.register("alice", "password1");
        run = habits.create(alice, "ランニング", Category.BODY, 10);
        water = habits.create(alice, "水", Category.BODY, 10, Set.of(DayOfWeek.values()), 8, 1, "杯");
    }

    @Test
    void showsPastReflectionsWithTheDaysRecords() {
        habits.saveReflection(alice, today.minusDays(3), "よく走れた");
        habits.toggle(alice, run.getId(), today.minusDays(3));
        habits.addAmount(alice, water.getId(), today.minusDays(3), 5);
        habits.toggle(alice, run.getId(), today.minusDays(1));
        habits.saveReflection(alice, today.minusDays(2), "   ");

        Journal j = journal.journal(alice, null, Period.DAYS30, false, today);

        // 新しい日から。空白だけのメモは無いものとして扱う
        assertThat(j.entries()).extracting(Entry::date).containsExactly(today.minusDays(1), today.minusDays(3));
        Entry old = j.entries().get(1);
        assertThat(old.reflection()).isEqualTo("よく走れた");
        assertThat(old.marks()).extracting(m -> m.name() + ":" + m.amountLabel())
                .containsExactly("ランニング:達成", "水:5 / 8杯");
        assertThat(j.entries().getFirst().hasReflection()).isFalse();
        assertThat(j.summary()).isNull();
    }

    @Test
    void notesOnlyShowsDaysWithReflection() {
        habits.toggle(alice, run.getId(), today.minusDays(1));
        habits.saveReflection(alice, today.minusDays(5), "記録はないけどメモ");

        Journal j = journal.journal(alice, null, Period.DAYS30, true, today);

        assertThat(j.entries()).extracting(Entry::reflection).containsExactly("記録はないけどメモ");
    }

    @Test
    void filteringByHabitShowsOnlyItsDaysAndSummary() {
        habits.addAmount(alice, water.getId(), today.minusDays(4), 8);
        habits.addAmount(alice, water.getId(), today.minusDays(2), 3);
        habits.toggle(alice, run.getId(), today.minusDays(1));
        habits.saveReflection(alice, today.minusDays(2), "少ししか飲めなかった");
        habits.saveReflection(alice, today.minusDays(1), "走った日のメモ");

        Journal j = journal.journal(alice, water.getId(), Period.DAYS30, false, today);

        assertThat(j.entries()).extracting(Entry::date).containsExactly(today.minusDays(2), today.minusDays(4));
        assertThat(j.entries().getFirst().reflection()).isEqualTo("少ししか飲めなかった");
        assertThat(j.summary().recordedDays()).isEqualTo(2);
        assertThat(j.summary().completedDays()).isEqualTo(1);
        assertThat(j.summary().totalAmount()).isEqualTo(11);
        assertThat(j.summary().first()).isEqualTo(today.minusDays(4));
        assertThat(j.summary().last()).isEqualTo(today.minusDays(2));
    }

    @Test
    void archivedHabitsCanStillBeReviewed() {
        habits.toggle(alice, run.getId(), today.minusDays(1));
        habits.archive(alice, run.getId());
        em.flush();
        em.clear();

        assertThat(journal.allHabits(alice)).extracting(Habit::getName).contains("ランニング");
        Journal j = journal.journal(alice, run.getId(), Period.DAYS30, false, today);
        assertThat(j.entries()).hasSize(1);
        assertThat(j.summary().habit().isActive()).isFalse();
    }

    @Test
    void periodLimitsHowFarBack() {
        habits.saveReflection(alice, today.minusDays(29), "30日前");
        habits.saveReflection(alice, today.minusDays(30), "31日前");
        habits.saveReflection(alice, today.minusDays(200), "ずっと前");

        assertThat(journal.journal(alice, null, Period.DAYS30, false, today).entries())
                .extracting(Entry::reflection).containsExactly("30日前");
        assertThat(journal.journal(alice, null, Period.DAYS90, false, today).entries()).hasSize(2);
        assertThat(journal.journal(alice, null, Period.ALL, false, today).entries()).hasSize(3);
        assertThat(Period.parse("unknown")).isEqualTo(Period.DAYS30);
    }

    @Test
    void dayShowsThatDaysRecordsAndNote() {
        LocalDate d = today.minusDays(2);
        habits.toggle(alice, run.getId(), d);
        habits.addAmount(alice, water.getId(), d, 3);
        habits.saveReflection(alice, d, "雨だった");
        habits.toggle(alice, run.getId(), today);

        Entry entry = journal.day(alice, d);

        assertThat(entry.reflection()).isEqualTo("雨だった");
        assertThat(entry.marks()).extracting(m -> m.name() + ":" + m.completed())
                .containsExactly("ランニング:true", "水:false");
        assertThat(journal.day(alice, today.minusDays(1)).marks()).isEmpty();
        assertThat(journal.day(alice, today.minusDays(1)).hasReflection()).isFalse();
    }

    @Test
    void otherUsersHabitIsNotFound() {
        AppUser bob = users.register("bob", "password2");
        Habit bobs = habits.create(bob, "秘密", Category.MIND, 10);

        assertThatThrownBy(() -> journal.journal(alice, bobs.getId(), Period.ALL, false, today))
                .isInstanceOf(NoSuchElementException.class);
    }
}
