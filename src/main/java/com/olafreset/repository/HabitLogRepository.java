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

    @Query("select coalesce(sum(l.xp), 0) from HabitLog l where l.habit.owner = :owner")
    long sumXp(AppUser owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HabitLog l where l.habit in (select h from Habit h where h.owner = :owner)")
    void deleteByOwner(AppUser owner);
}
