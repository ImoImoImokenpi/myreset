package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.UserService.CannotDeleteSelfException;
import com.olafreset.service.UserService.UsernameTakenException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserServiceTests {

    @Autowired
    UserService users;

    @Autowired
    HabitService habits;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void firstUserBecomesAdminAndOthersAreUsers() {
        assertThat(users.register("first", "password1").isAdmin()).isTrue();
        assertThat(users.register("second", "password2").isAdmin()).isFalse();
    }

    @Test
    void passwordIsStoredHashed() {
        AppUser user = users.register("hashed", "password1");
        assertThat(user.getPasswordHash()).isNotEqualTo("password1");
        assertThat(passwordEncoder.matches("password1", user.getPasswordHash())).isTrue();
    }

    @Test
    void usernameMustBeUniqueIgnoringCase() {
        users.register("Taro", "password1");
        assertThatThrownBy(() -> users.register("taro", "password2"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void firstUserClaimsDataCreatedBeforeUserManagement() {
        // ユーザー管理を入れる前のデータ（持ち主なし）を再現する
        jdbc.update("insert into habit (name, category, xp, active, created_at) values ('昔の習慣', 'BODY', 10, true, ?)",
                LocalDate.now());
        jdbc.update("insert into reflection (reflection_date, text) values (?, '昔のメモ')", LocalDate.now());

        AppUser first = users.register("first", "password1");
        AppUser second = users.register("second", "password2");

        assertThat(habits.activeHabits(first)).extracting(Habit::getName).containsExactly("昔の習慣");
        assertThat(habits.reflectionText(first, LocalDate.now())).isEqualTo("昔のメモ");
        assertThat(habits.activeHabits(second)).isEmpty();
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        AppUser user = users.register("changer", "password1");

        assertThat(users.changePassword(user, "wrong-password", "newpassword")).isFalse();
        assertThat(users.changePassword(user, "password1", "newpassword")).isTrue();
        assertThat(passwordEncoder.matches("newpassword", users.require("changer").getPasswordHash())).isTrue();
    }

    @Test
    void deletingUserRemovesTheirDataButAdminCannotDeleteSelf() {
        AppUser admin = users.register("admin", "password1");
        AppUser member = users.register("member", "password2");
        Habit habit = habits.create(member, "筋トレ", Category.BODY, 20);
        habits.toggle(member, habit.getId(), LocalDate.now());
        habits.saveReflection(member, LocalDate.now(), "メモ");

        assertThatThrownBy(() -> users.delete(admin, admin.getId()))
                .isInstanceOf(CannotDeleteSelfException.class);

        users.delete(admin, member.getId());

        assertThat(users.all()).extracting(AppUser::getUsername).containsExactly("admin");
        assertThat(jdbc.queryForObject("select count(*) from habit_log", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from habit", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reflection", Long.class)).isZero();
    }
}
