package com.moca.mocabe.domain.home.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@EnabledIf(value = "isSeedScriptAvailable", disabledReason = "로컬 생성 seed SQL이 없어 검증을 건너뜁니다.")
@DisplayName("카드고릴라 seed 기반 홈 시뮬레이션 fixture")
class HomeSimulationWithCardGorillaSeedIntegrationTest {

    private static final String SEED_PATH = "db/seed/card_gorilla_without_summary_benefits.sql";

    static boolean isSeedScriptAvailable() {
        return new ClassPathResource(SEED_PATH).exists();
    }

    @Test
    @DisplayName("기존 카드사·카드·콘텐츠를 재사용해 중복키 충돌 없이 적재한다")
    void loadsSimulationWithoutConflictingWithCardGorillaSeed() {
        try (MySQLContainer container =
                new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                        .withDatabaseName("moca_seed_simulation_test")
                        .withUsername("moca")
                        .withPassword("moca")
                        .withStartupTimeout(Duration.ofMinutes(3))) {
            container.start();
            DataSource dataSource = dataSource(container);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            new ResourceDatabasePopulator(
                            new ClassPathResource(SEED_PATH))
                    .execute(dataSource);
            new ResourceDatabasePopulator(
                            new ClassPathResource(
                                    "db/fixture/home-simulation-cardgorilla.sql"))
                    .execute(dataSource);

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            assertEquals(
                    4,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM cards "
                                    + "WHERE gorilla_card_id IN ('13', '51', '2441', '2986')",
                            Integer.class));
            assertEquals(
                    4,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM user_cards "
                                    + "WHERE user_id = '10000000-0000-0000-0000-000000000001'",
                            Integer.class));
        }
    }

    private DataSource dataSource(MySQLContainer mysqlContainer) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysqlContainer.getDriverClassName());
        dataSource.setUrl(mysqlContainer.getJdbcUrl());
        dataSource.setUsername(mysqlContainer.getUsername());
        dataSource.setPassword(mysqlContainer.getPassword());
        return dataSource;
    }
}
