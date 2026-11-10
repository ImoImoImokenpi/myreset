package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.HabitTeam;
import com.olafreset.domain.TeamInvite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    /** 自分に届いている招待（招待した人と習慣も一緒に読み込む）。 */
    @Query("""
            select i from TeamInvite i join fetch i.habit h join fetch h.owner
            where i.invitee = :user order by i.createdAt
            """)
    List<TeamInvite> findIncoming(AppUser user);

    long countByInvitee(AppUser invitee);

    /** チームへの、まだ承認されていない招待。 */
    @Query("select i from TeamInvite i join fetch i.invitee join fetch i.habit h where h.team = :team order by i.createdAt")
    List<TeamInvite> findByTeam(HabitTeam team);

    @Query("select count(i) > 0 from TeamInvite i where i.habit.team = :team and i.invitee = :invitee")
    boolean existsByTeamAndInvitee(HabitTeam team, AppUser invitee);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TeamInvite i where i.invitee = :invitee and i.habit in (select h from Habit h where h.team = :team)")
    void deleteByTeamAndInvitee(HabitTeam team, AppUser invitee);

    /** ユーザーを削除するとき：そのユーザーが受けた招待と、そのユーザーの習慣からの招待を消す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TeamInvite i where i.invitee = :user or i.habit in (select h from Habit h where h.owner = :user)")
    void deleteInvolving(AppUser user);

    /** フレンドを解除するとき：2人の間の招待を消す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from TeamInvite i
            where (i.invitee = :a and i.habit in (select h from Habit h where h.owner = :b))
               or (i.invitee = :b and i.habit in (select h from Habit h where h.owner = :a))
            """)
    void deleteBetween(AppUser a, AppUser b);
}
