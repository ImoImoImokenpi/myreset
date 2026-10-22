package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olafreset.service.UserService.RegistrationClosedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.registration-open=false")
@Transactional
class RegistrationClosedTests {

    @Autowired
    UserService users;

    @Test
    void registrationIsRejected() {
        assertThat(users.isRegistrationOpen()).isFalse();
        assertThatThrownBy(() -> users.register("someone", "password1"))
                .isInstanceOf(RegistrationClosedException.class);
    }
}
