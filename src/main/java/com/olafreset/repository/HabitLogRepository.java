package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.domain.HabitLog;
import com.olafreset.domain.HabitTeam;
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

    /** 期間内の記録（一覧から外した習慣も含む。習慣も一緒に読み込む）。 */
    @Query("""
            select l from HabitLog l join fetch l.habit h
            where h.owner = :owner and l.date between :from and :to
            order by h.category, h.id
            """)
    List<HabitLog> findWithHabitByOwnerAndDateBetween(AppUser owner, LocalDate from, LocalDate to);

    /** 達成した記録だけ（習慣も一緒に読み込む）。連続日数・統計・カレンダーに使う。 */
    @Query("select l from HabitLog l join fetch l.habit h where h.owner = :owner and l.completed = true")
    List<HabitLog> findCompletedByOwner(AppUser owner);

    /** その日の記録（目標に届いていないものも含む）。 */
    @Query("select l from HabitLog l join fetch l.habit h where h.owner = :owner and l.date = :date")
    List<HabitLog> findByOwnerAndDate(AppUser owner, LocalDate date);

    /** チームの全員の、その日の記録。 */
    @Query("select l from HabitLog l join fetch l.habit h where h.team = :team and l.date = :date")
    List<HabitLog> findByTeamAndDate(HabitTeam team, LocalDate date);

    /** チームでクリアした日の記録（ボーナスが付いている記録）。 */
    List<HabitLog> findByHabitAndBonusXpGreaterThan(Habit habit, int bonusXp);

    /** もらった XP の合計。量の習慣は進めた分だけ＋チームのボーナス（HabitLog#getEarnedXp と同じ計算）。 */
    @Query("""
            select coalesce(sum(l.xp * (case when l.amount < h.target then l.amount else h.target end) / h.target
                                + l.bonusXp), 0)
            from HabitLog l join l.habit h where h.owner = :owner
            """)
    long sumXp(AppUser owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HabitLog l where l.habit in (select h from Habit h where h.owner = :owner)")
    void deleteByOwner(AppUser owner);
}
