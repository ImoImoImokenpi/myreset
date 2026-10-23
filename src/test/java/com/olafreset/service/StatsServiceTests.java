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
}
