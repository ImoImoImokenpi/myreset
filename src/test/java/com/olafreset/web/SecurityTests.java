package com.olafreset.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.service.FriendService;
import com.olafreset.service.HabitService;
import com.olafreset.service.UserService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserService users;

    @Autowired
    HabitService habits;

    @Autowired
    FriendService friends;

    AppUser admin;
    AppUser member;

    @BeforeEach
    void setUp() {
        admin = users.register("admin", "password1");
        member = users.register("member", "password2");
    }

    @Test
    void pagesRequireLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/habits")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void loginWithCorrectPasswordOnly() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "member").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("username", "member").param("password", "password2"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void registerLogsInImmediately() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/register").session(session).with(csrf())
                        .param("username", "newbie")
                        .param("password", "password3")
                        .param("passwordConfirm", "password3"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("newbie")));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("username", "Admin")
                        .param("password", "short")
                        .param("passwordConfirm", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("このユーザー名はすでに使われています")))
                .andExpect(content().string(Matchers.containsString("パスワードは8〜72文字にしてください")))
                .andExpect(content().string(Matchers.containsString("確認用パスワードが一致しません")));
    }

    @Test
    void postsWithoutCsrfTokenAreRejected() throws Exception {
        Habit habit = habits.create(member, "読書", Category.SKILL, 10);
        mvc.perform(post("/habits/{id}/toggle", habit.getId()).with(user("member")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotToggleOtherUsersHabit() throws Exception {
        Habit adminsHabit = habits.create(admin, "瞑想", Category.MIND, 10);
        mvc.perform(post("/habits/{id}/toggle", adminsHabit.getId()).with(user("member")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(habits.totalXp(admin)).isZero();
    }

    @Test
    void inviteLinkSurvivesLoginAndMakesFriends() throws Exception {
        String code = friends.inviteCode(admin);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/invite/{code}", code).session(session))
                .andExpect(redirectedUrl("/login"));
        String back = mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "member").param("password", "password2"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(back).contains("/invite/" + code);

        mvc.perform(get(back).session(session)).andExpect(redirectedUrl("/friends"));
        assertThat(friends.areFriends(admin, member)).isTrue();
    }

    @Test
    void renameValidatesAndProtectsOtherUsersHabits() throws Exception {
        Habit habit = habits.create(member, "読書", Category.SKILL, 10);

        mvc.perform(post("/habits/{id}/rename", habit.getId()).with(user("member")).with(csrf())
                        .param("name", "   "))
                .andExpect(redirectedUrl("/habits"));
        assertThat(habits.activeHabits(member).getFirst().getName()).isEqualTo("読書");

        mvc.perform(post("/habits/{id}/rename", habit.getId()).with(user("admin")).with(csrf())
                        .param("name", "乗っ取り"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/habits/{id}/rename", habit.getId()).with(user("member")).with(csrf())
                        .param("name", "読書30分"))
                .andExpect(redirectedUrl("/habits"));
        assertThat(habits.activeHabits(member).getFirst().getName()).isEqualTo("読書30分");
    }

    @Test
    void cannotCheerNonFriend() throws Exception {
        mvc.perform(post("/friends/{id}/cheer", admin.getId()).with(user("member")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminPagesAreForAdminsOnly() throws Exception {
        mvc.perform(get("/admin/users").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("member")));
        mvc.perform(post("/admin/users/{id}/delete", admin.getId()).with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
