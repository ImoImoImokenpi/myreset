package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Cheer;
import com.olafreset.domain.Friendship;
import com.olafreset.domain.Friendship.Status;
import com.olafreset.repository.AppUserRepository;
import com.olafreset.repository.CheerRepository;
import com.olafreset.repository.FriendshipRepository;
import com.olafreset.service.HabitService.HabitRow;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** フレンドの申請・承認、フレンドの進捗、応援。 */
@Service
@Transactional(readOnly = true)
public class FriendService {

    /** 画面にそのまま表示できる理由付きの失敗。 */
    public static class FriendException extends RuntimeException {
        public FriendException(String message) {
            super(message);
        }
    }

    /**
     * フレンド一覧の1人分。達成数・連続日数・習慣は「フレンドに見せる」にした習慣だけで数える。
     * habits と達成数は、今日が実施日の習慣だけ。
     */
    public record FriendRow(AppUser user, List<HabitRow> habits, int done, int total, int bestStreak,
                            Level level, boolean cheered) {

        public int percent() {
            return total == 0 ? 0 : done * 100 / total;
        }
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final FriendshipRepository friendships;
    private final CheerRepository cheers;
    private final HabitService habits;

    public FriendService(AppUserRepository users, FriendshipRepository friendships, CheerRepository cheers,
                         HabitService habits) {
        this.users = users;
        this.friendships = friendships;
        this.cheers = cheers;
        this.habits = habits;
    }

    public List<AppUser> friends(AppUser me) {
        return friendships.findInvolving(me, Status.ACCEPTED).stream().map(f -> f.other(me)).toList();
    }

    public boolean areFriends(AppUser a, AppUser b) {
        return friendships.findBetween(a, b).filter(f -> f.getStatus() == Status.ACCEPTED).isPresent();
    }

    public List<Friendship> incoming(AppUser me) {
        return friendships.findIncoming(me);
    }

    public List<Friendship> outgoing(AppUser me) {
        return friendships.findOutgoing(me);
    }

    public long incomingCount(AppUser me) {
        return friendships.countByAddresseeAndStatus(me, Status.PENDING);
    }

    /**
     * ユーザー名でフレンド申請する。相手からすでに申請が来ていれば、その場でフレンドになる。
     * @return フレンドになったら true、申請しただけなら false
     */
    @Transactional
    public boolean request(AppUser me, String username) {
        AppUser target = users.findByUsernameIgnoreCase(username.strip())
                .orElseThrow(() -> new FriendException("「" + username.strip() + "」というユーザーは見つかりません"));
        return connect(me, target, false);
    }

    /** 招待リンクから来たとき。リンクを渡した本人が招待しているので、承認なしでフレンドになる。 */
    @Transactional
    public AppUser acceptInvite(AppUser me, String code) {
        AppUser inviter = users.findByInviteCode(code)
                .orElseThrow(() -> new FriendException("招待リンクが無効です"));
        connect(me, inviter, true);
        return inviter;
    }

    private boolean connect(AppUser me, AppUser target, boolean preApproved) {
        if (target.getId().equals(me.getId())) {
            throw new FriendException("自分自身とはフレンドになれません");
        }
        var existing = friendships.findBetween(me, target);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == Status.ACCEPTED) {
                throw new FriendException(target.getUsername() + " さんとはすでにフレンドです");
            }
            // 相手からの申請が来ている、または招待リンクから来たならフレンドにする
            if (f.getAddressee().getId().equals(me.getId()) || preApproved) {
                f.accept();
                return true;
            }
            throw new FriendException(target.getUsername() + " さんにはすでに申請しています");
        }
        friendships.save(new Friendship(me, target, preApproved ? Status.ACCEPTED : Status.PENDING));
        return preApproved;
    }

    /** 自分宛ての申請を承認する。 */
    @Transactional
    public AppUser accept(AppUser me, long friendshipId) {
        Friendship f = incomingRequest(me, friendshipId);
        f.accept();
        return f.getRequester();
    }

    /** 自分宛ての申請を断る、または自分が出した申請を取り消す。 */
    @Transactional
    public void dismiss(AppUser me, long friendshipId) {
        Friendship f = friendships.findById(friendshipId)
                .filter(x -> x.getStatus() == Status.PENDING)
                .filter(x -> x.getRequester().getId().equals(me.getId()) || x.getAddressee().getId().equals(me.getId()))
                .orElseThrow(NoSuchElementException::new);
        friendships.delete(f);
    }

    private Friendship incomingRequest(AppUser me, long friendshipId) {
        return friendships.findById(friendshipId)
                .filter(x -> x.getStatus() == Status.PENDING)
                .filter(x -> x.getAddressee().getId().equals(me.getId()))
                .orElseThrow(NoSuchElementException::new);
    }

    @Transactional
    public void remove(AppUser me, long friendUserId) {
        AppUser friend = users.findById(friendUserId).orElseThrow();
        Friendship f = friendships.findBetween(me, friend).orElseThrow();
        cheers.deleteBetween(me, friend);
        friendships.deleteById(f.getId());
    }

    /** フレンドの今日の進捗。達成率の高い順に並べる。 */
    public List<FriendRow> board(AppUser me, LocalDate today) {
        Set<Long> cheered = cheers.findSent(me, today).stream()
                .map(c -> c.getToUser().getId())
                .collect(Collectors.toSet());
        return friends(me).stream()
                .map(friend -> {
                    List<HabitRow> shared = habits.rows(friend, today).stream()
                            .filter(r -> r.habit().isShared())
                            .toList();
                    // 達成数は今日が実施日の習慣だけで数える
                    List<HabitRow> todays = shared.stream().filter(HabitRow::scheduledToday).toList();
                    int done = (int) todays.stream().filter(HabitRow::doneToday).count();
                    int best = shared.stream().mapToInt(HabitRow::streak).max().orElse(0);
                    return new FriendRow(friend, todays, done, todays.size(), best,
                            habits.level(friend), cheered.contains(friend.getId()));
                })
                .sorted(Comparator.comparingInt(FriendRow::percent).reversed()
                        .thenComparing(Comparator.comparingInt(FriendRow::done).reversed())
                        .thenComparing(r -> r.user().getUsername(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** フレンドを応援する。もう一度押すと取り消す。 */
    @Transactional
    public boolean toggleCheer(AppUser me, long friendUserId, LocalDate today) {
        AppUser friend = users.findById(friendUserId).orElseThrow();
        if (!areFriends(me, friend)) {
            throw new NoSuchElementException();
        }
        var existing = cheers.findByFromUserAndToUserAndDate(me, friend, today);
        if (existing.isPresent()) {
            cheers.delete(existing.get());
            return false;
        }
        cheers.save(new Cheer(me, friend, today));
        return true;
    }

    /** 今日もらった応援をくれた人。 */
    public List<AppUser> cheersReceived(AppUser me, LocalDate today) {
        return cheers.findReceived(me, today).stream().map(Cheer::getFromUser).toList();
    }

    /** 招待コード。まだなければ作る。 */
    @Transactional
    public String inviteCode(AppUser me) {
        AppUser managed = users.findById(me.getId()).orElseThrow();
        if (managed.getInviteCode() == null) {
            String code;
            do {
                code = randomCode();
            } while (users.findByInviteCode(code).isPresent());
            managed.setInviteCode(code);
        }
        return managed.getInviteCode();
    }

    /** 招待コードを作り直す（前のリンクは使えなくなる）。 */
    @Transactional
    public void resetInviteCode(AppUser me) {
        users.findById(me.getId()).orElseThrow().setInviteCode(null);
        users.flush();
        inviteCode(me);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
