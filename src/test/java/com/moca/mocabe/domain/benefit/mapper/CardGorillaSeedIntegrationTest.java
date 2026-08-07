package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@DisplayName("카드고릴라 seed SQL")
class CardGorillaSeedIntegrationTest {

  private static final String SEED_PATH = "db/seed/card_gorilla_without_summary_benefits.sql";
  private static final String EXISTING_ISSUER_ID = "01980d6a-5c0c-7aaf-9b85-010203040301";
  private static final String EXISTING_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040302";
  private static final String EXISTING_CONTENT_ID = "01980d6a-5c0c-7aaf-9b85-010203040303";

  @Test
  @DisplayName("MySQL 8에 카드 원문과 보수적으로 구조화한 계산 룰을 중복 없이 적재한다")
  void seedsCardGorillaCatalogIdempotently() {
    try (MySQLContainer container =
        new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("moca_seed_test")
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
      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      insertExistingMrLife(jdbcTemplate);
      ResourceDatabasePopulator populator =
          new ResourceDatabasePopulator(new ClassPathResource(SEED_PATH));

      populator.execute(dataSource);
      populator.execute(dataSource);

      assertEquals(
          200, count(jdbcTemplate, "SELECT COUNT(*) FROM cards WHERE gorilla_card_id IS NOT NULL"));
      assertEquals(201, count(jdbcTemplate, "SELECT COUNT(*) FROM card_content_versions"));
      assertEquals(409, count(jdbcTemplate, "SELECT COUNT(*) FROM card_annual_fee_options"));
      assertEquals(215, count(jdbcTemplate, "SELECT COUNT(*) FROM card_performance_tiers"));
      assertEquals(1198, count(jdbcTemplate, "SELECT COUNT(*) FROM card_benefits"));
      assertEquals(1006, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_offers"));
      assertEquals(21, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_rules"));
      assertEquals(21, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_rule_targets"));
      assertEquals(4, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_limit_policies"));
      assertEquals(4, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_limit_tiers"));
      assertEquals(1, count(jdbcTemplate, "SELECT COUNT(*) FROM benefit_rule_schedules"));
      assertEquals(
          EXISTING_CARD_ID,
          jdbcTemplate.queryForObject(
              "SELECT card_id FROM cards WHERE gorilla_card_id = '13'", String.class));
      assertEquals(
          "신한카드 Mr.Life",
          jdbcTemplate.queryForObject(
              "SELECT cv.name FROM cards c "
                  + "INNER JOIN card_content_versions cv ON cv.card_id = c.card_id "
                  + "WHERE c.gorilla_card_id = '13' "
                  + "ORDER BY cv.last_seen_at DESC LIMIT 1",
              String.class));
      assertEquals(
          3,
          count(
              jdbcTemplate,
              "SELECT COUNT(*) FROM card_performance_tiers tier INNER JOIN card_content_versions cv"
                  + " ON cv.content_version_id = tier.content_version_id INNER JOIN cards c ON"
                  + " c.card_id = cv.card_id WHERE c.gorilla_card_id = '13'"));
      assertEquals(
          499999,
          jdbcTemplate.queryForObject(
              "SELECT maximum_spend_krw FROM card_performance_tiers tier INNER JOIN"
                  + " card_content_versions cv ON cv.content_version_id = tier.content_version_id"
                  + " INNER JOIN cards c ON c.card_id = cv.card_id WHERE c.gorilla_card_id = '13'"
                  + " AND tier.tier_number = 1",
              Integer.class));
    }
  }

  private DataSource dataSource(MySQLContainer container) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(container.getDriverClassName());
    dataSource.setUrl(container.getJdbcUrl());
    dataSource.setUsername(container.getUsername());
    dataSource.setPassword(container.getPassword());
    return dataSource;
  }

  private Integer count(JdbcTemplate jdbcTemplate, String sql) {
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  private void insertExistingMrLife(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        "INSERT INTO issuers "
            + "(issuer_id, institution_code, issuer_name, created_at, updated_at) "
            + "VALUES (?, '0306', '신한카드', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
        EXISTING_ISSUER_ID);
    jdbcTemplate.update(
        "INSERT INTO cards "
            + "(card_id, issuer_id, card_type, first_seen_at, last_seen_at) "
            + "VALUES (?, ?, 'credit', '2025-01-01 00:00:00', '2025-01-01 00:00:00')",
        EXISTING_CARD_ID,
        EXISTING_ISSUER_ID);
    jdbcTemplate.update(
        "INSERT INTO card_content_versions "
            + "(content_version_id, card_id, content_sha256, name, source_url, "
            + "first_seen_at, last_seen_at) VALUES (?, ?, ?, '과거 Mr.Life', "
            + "'https://www.card-gorilla.com/card/detail/13', "
            + "'2025-01-01 00:00:00', '2025-01-01 00:00:00')",
        EXISTING_CONTENT_ID,
        EXISTING_CARD_ID,
        "0000000000000000000000000000000000000000000000000000000000000000");
  }
}
