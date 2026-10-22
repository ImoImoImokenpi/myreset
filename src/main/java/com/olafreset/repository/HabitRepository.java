package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    List<Habit> findByOwnerAndActiveTrueOrderByCategoryAscIdAsc(AppUser owner);

    Optional<Habit> findByIdAndOwner(Long id, AppUser owner);

    long countByOwnerAndActiveTrue(AppUser owner);

    /** ユーザー管理を入れる前に作られた（持ち主のいない）習慣を引き取る。 */
    @Modifying
    @Query("update Habit h set h.owner = :owner where h.owner is null")
    int claimOrphans(AppUser owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Habit h where h.owner = :owner")
    void deleteByOwner(AppUser owner);
}
