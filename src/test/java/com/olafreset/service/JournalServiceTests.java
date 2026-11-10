package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.JournalService.Entry;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
    void dayShowsThatDaysRecordsAndNote() {
        LocalDate d = today.minusDays(2);
        habits.toggle(alice, run.getId(), d);
        habits.addAmount(alice, water.getId(), d, 3);
        habits.saveReflection(alice, d, "雨だった");
        habits.toggle(alice, run.getId(), today);

        Entry entry = journal.day(alice, d);

        assertThat(entry.reflection()).isEqualTo("雨だった");
        assertThat(entry.marks()).extracting(m -> m.name() + ":" + m.completed() + ":" + m.amountLabel())
                .containsExactly("ランニング:true:達成", "水:false:3 / 8杯");
        assertThat(journal.day(alice, today.minusDays(1)).marks()).isEmpty();
        assertThat(journal.day(alice, today.minusDays(1)).hasReflection()).isFalse();
    }

    @Test
    void blankNoteCountsAsNoNote() {
        habits.saveReflection(alice, today, "   ");
        assertThat(journal.day(alice, today).hasReflection()).isFalse();
    }

    @Test
    void archivedHabitsStayInTheDayAndTheFilterList() {
        habits.toggle(alice, run.getId(), today.minusDays(1));
        habits.archive(alice, run.getId());
        em.flush();
        em.clear();

        assertThat(journal.allHabits(alice)).extracting(Habit::getName).contains("ランニング", "水");
        assertThat(journal.day(alice, today.minusDays(1)).marks()).extracting(JournalService.Mark::name)
                .containsExactly("ランニング");
    }
}
