package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.admin-username=owner")
@Transactional
class AdminUsernameTests {

    @Autowired
    UserService users;

    @Test
    void onlyConfiguredUsernameBecomesAdminEvenIfNotFirst() {
        assertThat(users.register("someone", "password1").isAdmin()).isFalse();
        assertThat(users.register("Owner", "password2").isAdmin()).isTrue();
    }
}
