package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.domain.HabitLog;
import com.olafreset.domain.HabitTeam;
import com.olafreset.domain.TeamInvite;
import com.olafreset.repository.AppUserRepository;
import com.olafreset.repository.HabitLogRepository;
import com.olafreset.repository.HabitRepository;
import com.olafreset.repository.HabitTeamRepository;
import com.olafreset.repository.TeamInviteRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * フレンドと一緒に取り組む習慣（チーム）。招待→承認で参加し、
 * その日に参加者全員が達成するとクリアになってボーナス XP がもらえる（判定は {@link HabitService}）。
 */
@Service
@Transactional(readOnly = true)
public class TeamService {

    /** 画面にそのまま表示できる理由付きの失敗。 */
    public static class TeamException extends RuntimeException {
        public TeamException(String message) {
            super(message);
        }
    }

    /** チームの1人分の、今日の状況。 */
    public record Member(AppUser user, boolean me, boolean doneToday) {
    }

    /**
     * フレンドページに出すチーム1つ分。habit は自分の習慣。
     * invitable は招待できるフレンド（まだ参加も招待もされていない人）。
     */
    public record TeamRow(Habit habit, List<Member> members, List<TeamInvite> invited, List<AppUser> invitable,
                          boolean scheduledToday, boolean cleared, int streak) {

        public int done() {
            return (int) members.stream().filter(Member::doneToday).count();
        }

        public int bonus() {
            return HabitLog.teamBonus(habit.getXp());
        }
    }

    /** 1人分の、今日達成したかどうか（画面の自動更新用）。 */
    public record MemberStatus(long userId, boolean done) {
    }

    /**
     * チームの習慣の今日の状況。「今日」の画面と、画面の自動更新（/api/teams/status）に使う。
     * total は参加人数、streak はみんなでクリアした連続日数。
     */
    public record TeamStatus(int done, int total, boolean cleared, int bonus, boolean scheduledToday, int streak,
                             List<MemberStatus> members) {
    }

    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final HabitTeamRepository teams;
    private final TeamInviteRepository invites;
    private final AppUserRepository users;
    private final HabitService habitService;
    private final FriendService friends;

    public TeamService(HabitRepository habits, HabitLogRepository logs, HabitTeamRepository teams,
                       TeamInviteRepository invites, AppUserRepository users, HabitService habitService,
                       FriendService friends) {
        this.habits = habits;
        this.logs = logs;
        this.teams = teams;
        this.invites = invites;
        this.users = users;
        this.habitService = habitService;
        this.friends = friends;
    }

    /** 自分の習慣に、フレンドを招待する。1人でやっていた習慣なら、ここでチームになる。 */
    @Transactional
    public void invite(AppUser me, long habitId, long friendUserId) {
        Habit habit = habits.findByIdAndOwner(habitId, me).filter(Habit::isActive).orElseThrow();
        AppUser friend = users.findById(friendUserId).orElseThrow();
        if (!friends.areFriends(me, friend)) {
            throw new NoSuchElementException();
        }
        if (habit.getTeam() == null) {
            habit.setTeam(teams.save(new HabitTeam()));
        } else {
            HabitTeam team = habit.getTeam();
            if (habits.findActiveByTeam(team).stream().anyMatch(h -> h.getOwner().getId().equals(friend.getId()))) {
                throw new TeamException(friend.getUsername() + " さんはすでに参加しています");
            }
            if (invites.existsByTeamAndInvitee(team, friend)) {
                throw new TeamException(friend.getUsername() + " さんはすでに招待されています");
            }
        }
        invites.save(new TeamInvite(habit, friend));
    }

    public List<TeamInvite> incoming(AppUser me) {
        return invites.findIncoming(me);
    }

    public long incomingCount(AppUser me) {
        return invites.countByInvitee(me);
    }

    /** 招待を承認して参加する。招待した人の習慣と同じ設定の習慣が、自分の一覧に増える。 */
    @Transactional(noRollbackFor = TeamException.class)
    public Habit accept(AppUser me, long inviteId, LocalDate today) {
        TeamInvite invite = invites.findById(inviteId)
                .filter(i -> i.getInvitee().getId().equals(me.getId()))
                .orElseThrow(NoSuchElementException::new);
        Habit source = invite.getHabit();
        HabitTeam team = source.getTeam();
        if (!source.isActive()) {
            invites.delete(invite);
            throw new TeamException("この習慣はもう終了しています");
        }
        List<Habit> members = habits.findActiveByTeam(team);
        Habit mine = members.stream()
                .filter(h -> h.getOwner().getId().equals(me.getId()))
                .findFirst()
                .orElseGet(() -> habits.save(Habit.joinTeam(source, me)));
        // 同じチームへの、他の人からの招待もまとめて片付ける
        invites.deleteByTeamAndInvitee(team, me);
        // 参加した人も「全員」に入るので、今日のクリアを判定し直す
        habitService.updateTeamClear(team, today);
        return mine;
    }

    /** 招待を断る（招待された人）、または取り消す（招待した人）。 */
    @Transactional
    public void dismiss(AppUser me, long inviteId) {
        TeamInvite invite = invites.findById(inviteId)
                .filter(i -> i.getInvitee().getId().equals(me.getId())
                        || i.getInviter().getId().equals(me.getId()))
                .orElseThrow(NoSuchElementException::new);
        invites.delete(invite);
    }

    /** 自分が参加しているチームの、今日の状況。 */
    public List<TeamRow> teams(AppUser me, LocalDate today) {
        List<AppUser> myFriends = friends.friends(me);
        return habitService.activeHabits(me).stream()
                .filter(Habit::isTeamHabit)
                .map(habit -> {
                    HabitTeam team = habit.getTeam();
                    List<Habit> memberHabits = habits.findActiveByTeam(team);
                    Set<Long> doneIds = completedHabitIds(team, today);
                    List<Member> members = memberHabits.stream()
                            .map(h -> new Member(h.getOwner(), h.getOwner().getId().equals(me.getId()),
                                    doneIds.contains(h.getId())))
                            .toList();
                    List<TeamInvite> invited = invites.findByTeam(team);
                    Set<Long> taken = members.stream().map(m -> m.user().getId()).collect(Collectors.toSet());
                    invited.forEach(i -> taken.add(i.getInvitee().getId()));
                    List<AppUser> invitable = myFriends.stream().filter(f -> !taken.contains(f.getId())).toList();
                    Set<LocalDate> clearedDates = clearedDates(habit);
                    return new TeamRow(habit, members, invited, invitable, habit.isScheduledOn(today),
                            clearedDates.contains(today), Streaks.current(habit, clearedDates, today));
                })
                .toList();
    }

    /** 自分のチームの習慣ごとの、今日の状況（キーは自分の習慣の ID）。 */
    public Map<Long, TeamStatus> statusByHabit(AppUser me, LocalDate today) {
        Map<Long, TeamStatus> result = new HashMap<>();
        for (Habit habit : habitService.activeHabits(me)) {
            if (!habit.isTeamHabit()) {
                continue;
            }
            List<Habit> memberHabits = habits.findActiveByTeam(habit.getTeam());
            Set<Long> doneIds = completedHabitIds(habit.getTeam(), today);
            List<MemberStatus> members = memberHabits.stream()
                    .map(h -> new MemberStatus(h.getOwner().getId(), doneIds.contains(h.getId())))
                    .toList();
            int done = (int) members.stream().filter(MemberStatus::done).count();
            Set<LocalDate> clearedDates = clearedDates(habit);
            result.put(habit.getId(), new TeamStatus(done, memberHabits.size(), clearedDates.contains(today),
                    HabitLog.teamBonus(habit.getXp()), habit.isScheduledOn(today),
                    Streaks.current(habit, clearedDates, today), members));
        }
        return result;
    }

    private Set<Long> completedHabitIds(HabitTeam team, LocalDate date) {
        return logs.findByTeamAndDate(team, date).stream()
                .filter(HabitLog::isCompleted)
                .map(l -> l.getHabit().getId())
                .collect(Collectors.toSet());
    }

    private Set<LocalDate> clearedDates(Habit habit) {
        return logs.findByHabitAndBonusXpGreaterThan(habit, 0).stream()
                .map(HabitLog::getDate)
                .collect(Collectors.toSet());
    }
}
