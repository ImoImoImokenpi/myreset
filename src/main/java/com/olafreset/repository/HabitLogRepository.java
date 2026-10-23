package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.domain.HabitLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    Optional<HabitLog> findByHabitAndDate(Habit habit, LocalDate date);

    List<HabitLog> findByHabitOwnerAndDateBetween(AppUser owner, LocalDate from, LocalDate to);

    List<HabitLog> findByHabitOrderByDateDesc(Habit habit);

    /** 達成した記録だけ（習慣も一緒に読み込む）。連続日数・統計・カレンダーに使う。 */
    @Query("select l from HabitLog l join fetch l.habit h where h.owner = :owner and l.completed = true")
    List<HabitLog> findCompletedByOwner(AppUser owner);

    /** その日の記録（目標に届いていないものも含む）。 */
    @Query("select l from HabitLog l join fetch l.habit h where h.owner = :owner and l.date = :date")
    List<HabitLog> findByOwnerAndDate(AppUser owner, LocalDate date);

    @Query("select coalesce(sum(l.xp), 0) from HabitLog l where l.habit.owner = :owner and l.completed = true")
    long sumXp(AppUser owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HabitLog l where l.habit in (select h from Habit h where h.owner = :owner)")
    void deleteByOwner(AppUser owner);
}
