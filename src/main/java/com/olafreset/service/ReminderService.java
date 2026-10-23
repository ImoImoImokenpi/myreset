package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Habit;
import com.olafreset.domain.PushSubscription;
import com.olafreset.repository.AppUserRepository;
import com.olafreset.repository.PushSubscriptionRepository;
import com.olafreset.service.WebPush.Result;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** リマインダー通知：設定した時刻に、今日の未達成の習慣があれば通知する。 */
@Service
@Transactional(readOnly = true)
public class ReminderService {

    /** 通知に表示する内容。 */
    public record Message(String title, String body, String url) {
    }

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final AppUserRepository users;
    private final PushSubscriptionRepository subscriptions;
    private final HabitService habits;
    private final WebPush webPush;

    public ReminderService(AppUserRepository users, PushSubscriptionRepository subscriptions, HabitService habits,
                           WebPush webPush) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.habits = habits;
        this.webPush = webPush;
    }

    public String publicKey() {
        return webPush.publicKey();
    }

    /** この端末で通知を受け取る。別のユーザーが同じ端末で登録していたら付け替える。 */
    @Transactional
    public void subscribe(AppUser user, String endpoint) {
        AppUser managed = users.findById(user.getId()).orElseThrow();
        subscriptions.findByEndpoint(endpoint).ifPresentOrElse(
                s -> s.setUser(managed),
                () -> subscriptions.save(new PushSubscription(managed, endpoint)));
    }

    @Transactional
    public void unsubscribe(AppUser user, String endpoint) {
        subscriptions.findByEndpoint(endpoint)
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptions::delete);
    }

    public long deviceCount(AppUser user) {
        return subscriptions.countByUser(user);
    }

    /** 通知の時刻を設定する。null なら通知しない。 */
    @Transactional
    public void setTime(AppUser user, LocalTime time, LocalDateTime now) {
        AppUser managed = users.findById(user.getId()).orElseThrow();
        managed.setReminderTime(time);
        // 今日の時刻をもう過ぎているなら、今日は送らない（設定した瞬間に通知が来ないように）
        boolean alreadyPassed = time != null && !time.isAfter(now.toLocalTime());
        managed.setLastRemindedOn(alreadyPassed ? now.toLocalDate() : null);
    }

    /** 通知に表示する内容（sw.js が取得する）。 */
    public Message message(AppUser user, LocalDate today) {
        List<Habit> left = habits.unfinishedToday(user, today);
        if (left.isEmpty()) {
            return new Message("MyReset", "今日の習慣はすべて達成しました。おつかれさま！", "/");
        }
        String names = left.stream().limit(3).map(Habit::getName).reduce((a, b) -> a + "、" + b).orElse("");
        String more = left.size() > 3 ? " ほか" + (left.size() - 3) + "件" : "";
        return new Message("まだの習慣が" + left.size() + "件あります", names + more, "/");
    }

    /** 時刻になったユーザーに通知する。毎分と、外部の cron から呼ばれる。 */
    @Transactional
    public int sendDue(LocalDateTime now) {
        int sent = 0;
        for (AppUser user : users.findDueForReminder(now.toLocalTime(), now.toLocalDate())) {
            user.setLastRemindedOn(now.toLocalDate());
            if (!habits.unfinishedToday(user, now.toLocalDate()).isEmpty()) {
                sent += push(user);
            }
        }
        if (sent > 0) {
            log.info("リマインダーを {} 件送りました", sent);
        }
        return sent;
    }

    /** 設定画面の「テスト通知」。 */
    @Transactional
    public int sendTest(AppUser user) {
        return push(users.findById(user.getId()).orElseThrow());
    }

    private int push(AppUser user) {
        int sent = 0;
        for (PushSubscription s : subscriptions.findByUser(user)) {
            Result result = webPush.send(s.getEndpoint());
            if (result == Result.SENT) {
                sent++;
            } else if (result == Result.GONE) {
                subscriptions.delete(s);
            }
        }
        return sent;
    }
}
