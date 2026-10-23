package com.olafreset.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTests {

    @Test
    void acceptsNeonConnectionStringAsIs() {
        DatabaseUrl db = DatabaseUrl.parse(
                "postgresql://neondb_owner:p%40ss@ep-cool-1234.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require",
                "", "");
        assertThat(db.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-cool-1234.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(db.username()).isEqualTo("neondb_owner");
        assertThat(db.password()).isEqualTo("p@ss");
    }

    @Test
    void keepsPortAndAcceptsPostgresScheme() {
        DatabaseUrl db = DatabaseUrl.parse("postgres://u:p@localhost:5432/app", "", "");
        assertThat(db.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/app");
    }

    @Test
    void acceptsJdbcUrlWithSeparateCredentials() {
        DatabaseUrl db = DatabaseUrl.parse(" jdbc:postgresql://host/db?sslmode=require ", "user", "secret");
        assertThat(db).isEqualTo(new DatabaseUrl("jdbc:postgresql://host/db?sslmode=require", "user", "secret"));
    }

    @Test
    void explainsMissingOrInvalidUrl() {
        assertThatThrownBy(() -> DatabaseUrl.parse("", "", ""))
                .hasMessageContaining("DATABASE_URL が設定されていません");
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://x", "", ""))
                .hasMessageContaining("postgresql://");
    }
}
