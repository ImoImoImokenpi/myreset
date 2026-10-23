package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AppUser> findByInviteCode(String inviteCode);

    List<AppUser> findAllByOrderByIdAsc();

    /** リマインダーの時刻を過ぎていて、今日まだ判定していないユーザー。 */
    @Query("""
            select u from AppUser u
            where u.reminderTime is not null and u.reminderTime <= :now
              and (u.lastRemindedOn is null or u.lastRemindedOn < :today)
            """)
    List<AppUser> findDueForReminder(LocalTime now, LocalDate today);
}
