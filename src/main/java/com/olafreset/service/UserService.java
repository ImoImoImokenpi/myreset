package com.olafreset.service;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.AppUser.Role;
import com.olafreset.repository.AppUserRepository;
import com.olafreset.repository.CheerRepository;
import com.olafreset.repository.FriendshipRepository;
import com.olafreset.repository.HabitLogRepository;
import com.olafreset.repository.HabitRepository;
import com.olafreset.repository.ReflectionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    /** 登録済みのユーザー名を使おうとしたとき。 */
    public static class UsernameTakenException extends RuntimeException {
    }

    /** 新規登録を締め切っているとき。 */
    public static class RegistrationClosedException extends RuntimeException {
    }

    /** 管理者が自分自身を削除しようとしたとき。 */
    public static class CannotDeleteSelfException extends RuntimeException {
    }

    private final AppUserRepository users;
    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final ReflectionRepository reflections;
    private final FriendshipRepository friendships;
    private final CheerRepository cheers;
    private final PasswordEncoder passwordEncoder;
    private final boolean registrationOpen;
    private final String adminUsername;

    public UserService(AppUserRepository users, HabitRepository habits, HabitLogRepository logs,
                       ReflectionRepository reflections, FriendshipRepository friendships,
                       CheerRepository cheers, PasswordEncoder passwordEncoder,
                       @Value("${app.registration-open:true}") boolean registrationOpen,
                       @Value("${app.admin-username:}") String adminUsername) {
        this.users = users;
        this.habits = habits;
        this.logs = logs;
        this.reflections = reflections;
        this.friendships = friendships;
        this.cheers = cheers;
        this.passwordEncoder = passwordEncoder;
        this.registrationOpen = registrationOpen;
        this.adminUsername = adminUsername.strip();
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    public AppUser require(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow();
    }

    public boolean isTaken(String username) {
        return users.existsByUsernameIgnoreCase(username.strip());
    }

    public List<AppUser> all() {
        return users.findAllByOrderByIdAsc();
    }

    public long habitCount(AppUser user) {
        return habits.countByOwnerAndActiveTrue(user);
    }

    /**
     * ユーザーを登録する。app.admin-username が設定されていればそのユーザー名の人だけが、
     * 未設定なら最初の1人が管理者になる。最初の1人はユーザー管理を入れる前に記録したデータを引き継ぐ。
     */
    @Transactional
    public AppUser register(String username, String rawPassword) {
        if (!registrationOpen) {
            throw new RegistrationClosedException();
        }
        String name = username.strip();
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new UsernameTakenException();
        }
        boolean first = users.count() == 0;
        boolean admin = adminUsername.isEmpty() ? first : adminUsername.equalsIgnoreCase(name);
        AppUser user = users.save(new AppUser(name, passwordEncoder.encode(rawPassword),
                admin ? Role.ADMIN : Role.USER));
        if (first) {
            habits.claimOrphans(user);
            reflections.claimOrphans(user);
        }
        return user;
    }

    @Transactional
    public boolean changePassword(AppUser user, String currentPassword, String newPassword) {
        AppUser managed = users.findById(user.getId()).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, managed.getPasswordHash())) {
            return false;
        }
        managed.setPasswordHash(passwordEncoder.encode(newPassword));
        return true;
    }

    @Transactional
    public void changeThemeColor(AppUser user, String color) {
        users.findById(user.getId()).orElseThrow().setThemeColor(color.toLowerCase());
    }

    /** ユーザーと、そのユーザーの習慣・記録・振り返り・フレンド関係・応援をすべて削除する。 */
    @Transactional
    public void delete(AppUser admin, long userId) {
        if (admin.getId() == userId) {
            throw new CannotDeleteSelfException();
        }
        AppUser target = users.findById(userId).orElseThrow(NoSuchElementException::new);
        cheers.deleteInvolving(target);
        friendships.deleteInvolving(target);
        logs.deleteByOwner(target);
        habits.deleteByOwner(target);
        reflections.deleteByOwner(target);
        users.deleteById(target.getId());
    }
}
