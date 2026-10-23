package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUser(AppUser user);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    long countByUser(AppUser user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushSubscription s where s.user = :user")
    void deleteByUser(AppUser user);
}
