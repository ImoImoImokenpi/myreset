package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Friendship;
import com.olafreset.domain.Friendship.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** 自分が関わっている、指定した状態のフレンド関係。 */
    @Query("""
            select f from Friendship f join fetch f.requester join fetch f.addressee
            where (f.requester = :user or f.addressee = :user) and f.status = :status
            order by f.createdAt
            """)
    List<Friendship> findInvolving(AppUser user, Status status);

    @Query("select f from Friendship f join fetch f.requester where f.addressee = :user and f.status = 'PENDING'")
    List<Friendship> findIncoming(AppUser user);

    @Query("select f from Friendship f join fetch f.addressee where f.requester = :user and f.status = 'PENDING'")
    List<Friendship> findOutgoing(AppUser user);

    long countByAddresseeAndStatus(AppUser addressee, Status status);

    /** 2人の間のフレンド関係（どちらから申請したかは問わない）。 */
    @Query("""
            select f from Friendship f
            where (f.requester = :a and f.addressee = :b) or (f.requester = :b and f.addressee = :a)
            """)
    Optional<Friendship> findBetween(AppUser a, AppUser b);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Friendship f where f.requester = :user or f.addressee = :user")
    void deleteInvolving(AppUser user);
}
