package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.StatsService.CalendarDay;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StatsServiceTests {

    @Autowired
    StatsService stats;

    @Autowired
    HabitService habits;

    @Autowired
    UserService users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void calendarAndHabitStatsReflectCompletions() {
        AppUser user = users.register("stats", "password1");
        LocalDate today = LocalDate.of(2026, 10, 22); // 木曜
        Habit run = habits.create(user, "ランニング", Category.BODY, 10, Set.of(DayOfWeek.values()), 1, 1, null);
        // 作成日を月初に戻して、過去の記録を付けられるようにする
        entityManager.flush();
        jdbc.update("update habit set created_at = ? where id = ?", LocalDate.of(2026, 10, 1), run.getId());
        entityManager.clear();
        for (int d : new int[] {10, 19, 20, 21, 22}) {
            habits.toggle(user, run.getId(), LocalDate.of(2026, 10, d));
        }

        var calendar = stats.calendar(user, YearMonth.of(2026, 10), today);
        // 10/1 は木曜なので、月曜始まりの週の前に空きマスが3つ
        assertThat(calendar.weeks().getFirst()).extracting(CalendarDay::date)
                .startsWith(null, null, null, LocalDate.of(2026, 10, 1));
        assertThat(calendar.completions()).isEqualTo(5);
        assertThat(calendar.perfectDays()).isEqualTo(5);
        assertThat(calendar.scheduledDays()).isEqualTo(22);
        assertThat(calendar.rate()).isEqualTo(5 * 100 / 22);
        CalendarDay oct23 = calendar.weeks().stream().flatMap(List::stream)
                .filter(d -> LocalDate.of(2026, 10, 23).equals(d.date())).findFirst().orElseThrow();
        assertThat(oct23.future()).isTrue();
        assertThat(oct23.level()).isEqualTo(-1);

        var s = stats.habitStats(user, today).getFirst();
        assertThat(s.currentStreak()).isEqualTo(4);
        assertThat(s.bestStreak()).isEqualTo(4);
        assertThat(s.total()).isEqualTo(5);
        assertThat(s.rate30()).isEqualTo(5 * 100 / 22);
        assertThat(s.weekdays()).hasSize(7);
        var thursday = s.weekdays().get(3);
        assertThat(thursday.label()).isEqualTo("木");
        assertThat(thursday.done()).isEqualTo(1);
        assertThat(thursday.scheduled()).isEqualTo(4);
    }

    @Test
    void calendarCanBeFilteredToOneHabitIncludingArchivedOnes() {
        AppUser user = users.register("filter", "password1");
        LocalDate today = LocalDate.of(2026, 10, 22);
        Habit run = habits.create(user, "ランニング", Category.BODY, 10);
        Habit read = habits.create(user, "読書", Category.SKILL, 10);
        entityManager.flush();
        jdbc.update("update habit set created_at = ? where id in (?, ?)", LocalDate.of(2026, 10, 1),
                run.getId(), read.getId());
        entityManager.clear();
        habits.toggle(user, run.getId(), LocalDate.of(2026, 10, 5));
        habits.toggle(user, run.getId(), LocalDate.of(2026, 10, 6));
        habits.toggle(user, read.getId(), LocalDate.of(2026, 10, 6));
        habits.toggle(user, read.getId(), LocalDate.of(2026, 10, 9));

        Habit runHabit = habits.find(user, run.getId());
        var onlyRun = stats.calendar(user, YearMonth.of(2026, 10), today, runHabit);
        assertThat(day(onlyRun, 5).level()).isEqualTo(4);
        assertThat(day(onlyRun, 9).level()).isZero();
        assertThat(onlyRun.completions()).isEqualTo(2);
        assertThat(onlyRun.scheduledDays()).isEqualTo(22);

        // 外した習慣は、外した日より後を数えない（外した日を記録する前のものは最後に達成した日まで）
        habits.archive(user, read.getId());
        entityManager.flush();
        jdbc.update("update habit set archived_on = null where id = ?", read.getId());
        entityManager.clear();
        Habit readHabit = habits.find(user, read.getId());
        var onlyRead = stats.calendar(user, YearMonth.of(2026, 10), today, readHabit);
        assertThat(day(onlyRead, 9).level()).isEqualTo(4);
        assertThat(day(onlyRead, 10).level()).isEqualTo(-1);
        assertThat(onlyRead.scheduledDays()).isEqualTo(9);
        assertThat(onlyRead.completions()).isEqualTo(2);

        // 全体のカレンダーには、外した習慣は入らない
        var all = stats.calendar(user, YearMonth.of(2026, 10), today);
        assertThat(day(all, 6).total()).isEqualTo(1);
    }

    @Test
    void archivingRecordsTheDay() {
        AppUser user = users.register("archiver", "password1");
        Habit run = habits.create(user, "ランニング", Category.BODY, 10);
        habits.archive(user, run.getId());
        assertThat(habits.find(user, run.getId()).getArchivedOn()).isEqualTo(LocalDate.now());
    }

    private static CalendarDay day(StatsService.Calendar calendar, int dayOfMonth) {
        return calendar.weeks().stream().flatMap(List::stream)
                .filter(d -> d.date() != null && d.date().getDayOfMonth() == dayOfMonth)
                .findFirst().orElseThrow();
    }
}
