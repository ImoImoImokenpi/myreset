package com.olafreset.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 本番では DATABASE_URL（Neon の接続文字列をそのまま貼れる）からデータベースに接続する。 */
@Configuration
@Profile("prod")
class ProdDataSourceConfig {

    @Bean
    DataSource dataSource(@Value("${DATABASE_URL:}") String url,
                          @Value("${DATABASE_USERNAME:}") String username,
                          @Value("${DATABASE_PASSWORD:}") String password) {
        DatabaseUrl db = DatabaseUrl.parse(url, username, password);
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(db.jdbcUrl());
        ds.setUsername(db.username());
        ds.setPassword(db.password());
        // 無料枠のメモリと接続数に合わせて小さくする
        ds.setMaximumPoolSize(3);
        ds.setMinimumIdle(0);
        // Neon は使っていないと休止するので、起動直後の接続は待てるようにする
        ds.setConnectionTimeout(30_000);
        return ds;
    }
}
