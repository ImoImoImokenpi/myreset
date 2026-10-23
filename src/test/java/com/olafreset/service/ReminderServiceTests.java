package com.olafreset.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olafreset.domain.AppUser;
import com.olafreset.domain.Category;
import com.olafreset.domain.Habit;
import com.olafreset.repository.AppUserRepository;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReminderServiceTests {

    @Autowired
    ReminderService reminders;

    @Autowired
    HabitService habits;

    @Autowired
    UserService users;

    @Autowired
    AppUserRepository userRepository;

    HttpServer pushServer;
    List<String> authorizations = new CopyOnWriteArrayList<>();
    String endpoint;

    @BeforeEach
    void startFakePushService() throws Exception {
        pushServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushServer.createContext("/push/", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = exchange.getRequestURI().getPath().endsWith("/gone") ? 410 : 201;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        pushServer.start();
        endpoint = "http://127.0.0.1:" + pushServer.getAddress().getPort() + "/push/device1";
    }

    @AfterEach
    void stop() {
        pushServer.stop(0);
    }

    @Test
    void messageListsUnfinishedHabits() {
        AppUser user = users.register("remind", "password1");
        LocalDate today = LocalDate.now();
        assertThat(reminders.message(user, today).body()).contains("すべて達成");

        Habit a = habits.create(user, "読書", Category.SKILL, 10);
        habits.create(user, "筋トレ", Category.BODY, 10);
        assertThat(reminders.message(user, today).title()).isEqualTo("まだの習慣が2件あります");

        habits.toggle(user, a.getId(), today);
        assertThat(reminders.message(user, today).body()).isEqualTo("筋トレ");
    }

    @Test
    void sendsOnceAfterReminderTimeOnlyWhenSomethingIsLeft() throws Exception {
        AppUser user = users.register("remind", "password1");
        Habit habit = habits.create(user, "読書", Category.SKILL, 10);
        reminders.subscribe(user, endpoint);
        LocalDateTime evening = LocalDate.now().atTime(21, 0);
        reminders.setTime(user, LocalTime.of(21, 0), evening.minusHours(1));

        assertThat(reminders.sendDue(evening.minusMinutes(1))).isZero();
        assertThat(reminders.sendDue(evening)).isEqualTo(1);
        // 同じ日には2回送らない
        assertThat(reminders.sendDue(evening.plusMinutes(5))).isZero();
        assertThat(authorizations).hasSize(1);
        assertValidVapid(authorizations.getFirst());

        // 次の日、すでに全部達成していれば送らない
        LocalDateTime tomorrow = evening.plusDays(1);
        habits.toggle(user, habit.getId(), tomorrow.toLocalDate());
        assertThat(reminders.sendDue(tomorrow)).isZero();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastRemindedOn())
                .isEqualTo(tomorrow.toLocalDate());
    }

    @Test
    void settingTimeThatAlreadyPassedSkipsToday() {
        AppUser user = users.register("remind", "password1");
        habits.create(user, "読書", Category.SKILL, 10);
        reminders.subscribe(user, endpoint);
        LocalDateTime now = LocalDate.now().atTime(22, 0);

        reminders.setTime(user, LocalTime.of(21, 0), now);

        assertThat(reminders.sendDue(now)).isZero();
    }

    @Test
    void goneSubscriptionsAreRemoved() {
        AppUser user = users.register("remind", "password1");
        reminders.subscribe(user, endpoint.replace("device1", "gone"));
        assertThat(reminders.deviceCount(user)).isEqualTo(1);

        assertThat(reminders.sendTest(user)).isZero();

        assertThat(reminders.deviceCount(user)).isZero();
    }

    @Test
    void subscriptionMovesToTheUserWhoRegisteredItLast() {
        AppUser a = users.register("first", "password1");
        AppUser b = users.register("second", "password2");
        reminders.subscribe(a, endpoint);
        reminders.subscribe(b, endpoint);
        assertThat(reminders.deviceCount(a)).isZero();
        assertThat(reminders.deviceCount(b)).isEqualTo(1);
    }

    /** Authorization ヘッダーの JWT が、公開鍵で検証できることを確かめる。 */
    private void assertValidVapid(String header) throws Exception {
        assertThat(header).startsWith("vapid t=");
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String key = header.substring(header.indexOf(", k=") + 4);
        assertThat(key).isEqualTo(reminders.publicKey());

        byte[] raw = Base64.getUrlDecoder().decode(key);
        assertThat(raw).hasSize(65);
        assertThat(raw[0]).isEqualTo((byte) 0x04);
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(point, params.getParameterSpec(ECParameterSpec.class)));

        String[] parts = token.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"http://127.0.0.1:" + pushServer.getAddress().getPort() + "\"");
    }
}
