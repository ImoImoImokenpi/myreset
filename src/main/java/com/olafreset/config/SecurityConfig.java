package com.olafreset.config;

import com.olafreset.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService,
                                            @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/icons/**",
                                "/favicon.ico", "/manifest*.json", "/sw.js", "/cron/**", "/error").permitAll()
                        .requestMatchers("/admin/**", "/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        // 招待リンクなどからログインに来た場合は元のページへ戻す
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .userDetailsService(userService)
                        .tokenValiditySeconds(60 * 60 * 24 * 30))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout"))
                // H2 コンソールは独自のフォームと iframe を使うため、その部分だけ緩める
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
