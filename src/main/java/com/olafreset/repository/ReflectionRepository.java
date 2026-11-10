package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Reflection;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReflectionRepository extends JpaRepository<Reflection, Long> {

    Optional<Reflection> findByOwnerAndDate(AppUser owner, LocalDate date);

    /** ユーザー管理を入れる前に作られた（持ち主のいない）振り返りを引き取る。 */
    @Modifying
    @Query("update Reflection r set r.owner = :owner where r.owner is null")
    int claimOrphans(AppUser owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Reflection r where r.owner = :owner")
    void deleteByOwner(AppUser owner);
}
