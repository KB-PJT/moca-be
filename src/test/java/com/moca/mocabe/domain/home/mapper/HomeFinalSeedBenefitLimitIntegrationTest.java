package com.moca.mocabe.domain.home.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.home.model.HomeCardRow;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("최종 seed 카드 월 최대 혜택")
class HomeFinalSeedBenefitLimitIntegrationTest {

  private static final String USER_ID = "89000000-0000-4000-8000-000000000001";
  private static final String TARGET_CARD_IDS =
      "'360','2933','281','2422','2899','2890','733','2680'";

  private MySQLContainer container;
  private SqlSession sqlSession;
  private HomeMapper homeMapper;

  @BeforeAll
  void setUp() throws Exception {
    container =
        new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("moca")
            .withUsername("moca")
            .withPassword("moca")
            .withStartupTimeout(Duration.ofMinutes(3));
    container.start();

    DataSource dataSource = dataSource(container);
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    new ResourceDatabasePopulator(new ClassPathResource("db/seed/moca_final_seed.sql"))
        .execute(dataSource);
    seedUserCards(new JdbcTemplate(dataSource));

    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
    SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
    sqlSession = sqlSessionFactory.openSession();
    homeMapper = sqlSession.getMapper(HomeMapper.class);
  }

  @AfterAll
  void tearDown() {
    if (sqlSession != null) {
      sqlSession.close();
    }
    if (container != null) {
      container.stop();
    }
  }

  @Test
  @DisplayName("전월 60만원 기준 카드별 월 한도를 원화와 포인트 1대1로 합산한다")
  void sumsApplicableMonthlyLimitsInKrw() {
    Map<String, HomeCardRow> cards =
        homeMapper.findHomeCards(USER_ID, "2026-04").stream()
            .collect(Collectors.toMap(HomeCardRow::getCardName, Function.identity()));

    assertEquals(0, cards.get("올바른POINT체크카드").getMaximumMonthlyBenefitAmount());
    assertEquals(30_000, cards.get("신한카드 나라사랑카드 체크").getMaximumMonthlyBenefitAmount());
    assertEquals(15_000, cards.get("신한카드 Deep Dream 체크").getMaximumMonthlyBenefitAmount());
    assertEquals(43_000, cards.get("노리2 체크카드(KB Pay)").getMaximumMonthlyBenefitAmount());
    assertEquals(50_000, cards.get("신한카드 SOL Plan").getMaximumMonthlyBenefitAmount());
    assertEquals(
        13_000,
        cards.get("신한카드 Point Plan 체크 캐릭터형(짱구)")
            .getMaximumMonthlyBenefitAmount());
    assertEquals(30_000, cards.get("무신사 현대카드").getMaximumMonthlyBenefitAmount());
    assertEquals(30_000, cards.get("현대카드Z work Edition2").getMaximumMonthlyBenefitAmount());
  }

  @Test
  @DisplayName("Point Plan 가족행사월에는 5천 포인트 추가 한도를 적용한다")
  void selectsPointPlanFamilyMonthLimit() {
    HomeCardRow pointPlan =
        homeMapper.findHomeCards(USER_ID, "2026-05").stream()
            .filter(row -> row.getCardName().contains("Point Plan 체크 캐릭터형"))
            .findFirst()
            .orElseThrow();

    assertEquals(18_000, pointPlan.getMaximumMonthlyBenefitAmount());
  }

  private void seedUserCards(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO users (user_id, google_subject, nickname, email, user_type, "
            + "location_recommendation_enabled, card_sort_mode, created_at, updated_at) "
            + "VALUES (?, 'home-final-seed-limit', '한도검증', 'limit@moca.test', 'user', "
            + "FALSE, 'MANUAL', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
        USER_ID);
    jdbc.update(
        "INSERT INTO codef_account_credentials "
            + "(codef_account_credential_id, user_id, issuer_id, connected_id, "
            + "account_id_enc, account_password_enc, birth_date_enc, "
            + "credential_identity_hash, status, created_at, updated_at) "
            + "SELECT UUID(), ?, issuer.issuer_id, UUID(), X'01', X'02', X'03', "
            + "SHA2(CONCAT('home-limit-credential-', issuer.issuer_id), 256), "
            + "'active', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) "
            + "FROM issuers issuer WHERE issuer.issuer_id IN "
            + "(SELECT DISTINCT card.issuer_id FROM cards card "
            + "WHERE card.gorilla_card_id IN (" + TARGET_CARD_IDS + "))",
        USER_ID);
    jdbc.update(
        "INSERT INTO user_cards (user_card_id, user_id, card_id, "
            + "codef_account_credential_id, card_name_from_codef, issuer_id, "
            + "display_order, is_active, codef_card_key_hash, created_at, updated_at) "
            + "SELECT UUID(), ?, card.card_id, credential.codef_account_credential_id, "
            + "content.name, card.issuer_id, "
            + "ROW_NUMBER() OVER (ORDER BY card.gorilla_card_id), TRUE, "
            + "SHA2(CONCAT('home-limit-', card.gorilla_card_id), 256), "
            + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) "
            + "FROM cards card INNER JOIN card_content_versions content "
            + "ON content.card_id = card.card_id "
            + "INNER JOIN codef_account_credentials credential "
            + "ON credential.user_id = ? AND credential.issuer_id = card.issuer_id "
            + "WHERE card.gorilla_card_id IN (" + TARGET_CARD_IDS + ")",
        USER_ID,
        USER_ID);
    insertPerformanceSnapshots(jdbc, "2026-03");
    insertPerformanceSnapshots(jdbc, "2026-04");
  }

  private void insertPerformanceSnapshots(JdbcTemplate jdbc, String performanceMonth) {
    jdbc.update(
        "INSERT INTO user_card_performance_snapshots "
            + "(performance_snapshot_id, user_card_id, performance_month, "
            + "current_spend_amount, updated_at, created_at) "
            + "SELECT UUID(), user_card_id, ?, 600000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) "
            + "FROM user_cards WHERE user_id = ?",
        performanceMonth,
        USER_ID);
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
