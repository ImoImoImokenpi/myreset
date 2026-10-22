package com.olafreset.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ユーザー管理を入れる前の DB には「振り返りは1日1件（全体で）」という一意制約が残っている。
 * 今は「1ユーザー1日1件」なので、古い制約があれば起動時に外す。
 */
@Component
class LegacySchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySchemaMigration.class);

    private final JdbcTemplate jdbc;

    LegacySchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> names = jdbc.queryForList("""
                select tc.CONSTRAINT_NAME
                from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                where tc.TABLE_NAME = 'REFLECTION'
                  and tc.CONSTRAINT_TYPE = 'UNIQUE'
                  and (select count(*) from INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                       where k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME) = 1
                  and exists (select 1 from INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                              where k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                                and k.COLUMN_NAME = 'REFLECTION_DATE')
                """, String.class);
        for (String name : names) {
            jdbc.execute("alter table REFLECTION drop constraint \"" + name + "\"");
            log.info("古い一意制約 {} を削除しました", name);
        }
    }
}
