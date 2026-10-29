package com.mynotionai.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Flyway 마이그레이션 통합 테스트")
class FlywayMigrationTest {

    private static final String INITIAL_SCHEMA_VERSION = "1";

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("V1 초기 스키마가 성공적으로 적용된다")
    void appliesInitialSchemaSuccessfully() {
        assertThat(flyway.info().applied()).singleElement().satisfies(migration -> {
            assertThat(migration.getVersion().getVersion()).isEqualTo(INITIAL_SCHEMA_VERSION);
            assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
        });
        assertThat(flyway.info().pending()).isEmpty();
    }
}
