package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Cheer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

    Optional<Cheer> findByFromUserAndToUserAndDate(AppUser fromUser, AppUser toUser, LocalDate date);

    @Query("select c from Cheer c join fetch c.fromUser where c.toUser = :user and c.date = :date order by c.createdAt")
    List<Cheer> findReceived(AppUser user, LocalDate date);

    @Query("select c from Cheer c join fetch c.toUser where c.fromUser = :user and c.date = :date")
    List<Cheer> findSent(AppUser user, LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Cheer c where c.fromUser = :user or c.toUser = :user")
    void deleteInvolving(AppUser user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Cheer c where (c.fromUser = :a and c.toUser = :b) or (c.fromUser = :b and c.toUser = :a)")
    void deleteBetween(AppUser a, AppUser b);
}
