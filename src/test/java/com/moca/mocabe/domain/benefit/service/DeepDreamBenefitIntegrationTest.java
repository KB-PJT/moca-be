package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@DisplayName("신한카드 Deep Dream 체크 월간 적립 E2E")
class DeepDreamBenefitIntegrationTest {
  private static final MySQLContainer CONTAINER =
      new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
          .withDatabaseName("moca").withUsername("moca").withPassword("moca")
          .withStartupTimeout(Duration.ofMinutes(3));
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUpDatabase() throws Exception {
    CONTAINER.start();
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName(CONTAINER.getDriverClassName());
    source.setUrl(CONTAINER.getJdbcUrl());
    source.setUsername(CONTAINER.getUsername());
    source.setPassword(CONTAINER.getPassword());
    dataSource = source;
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    new ResourceDatabasePopulator(new ClassPathResource("db/seed/moca_final_seed.sql"))
        .execute(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    sqlSessionFactory = createSqlSessionFactory();
  }

  @AfterAll
  static void stopDatabase() {
    CONTAINER.stop();
  }

  @Test
  @DisplayName("최다 영역 변경 시 월 전체를 재계산하고 추가 적립을 5천 포인트에서 제한한다")
  void recalculatesChangedTopAreaAndCapsMonthlyExtraReward() throws Exception {
    TestCard card = registerCard("deep-dream-e2e", "31000000-0000-4000-8000-000000000001");
    insertPerformance(card.userCardId(), 200_000);
    ApprovalInsert retail1 = insertApproval(card, "31100000-0000-4000-8000-000000000001",
        "CU", 100_000, 5);
    ApprovalInsert discount = insertApproval(card, "31100000-0000-4000-8000-000000000002",
        "이마트", 200_000, 6);

    calculatePeriod(card.userId(), 1, 10);
    assertReward(retail1.approvalId(), "600");
    assertReward(discount.approvalId(), "2000");

    ApprovalInsert retail2 = insertApproval(card, "31100000-0000-4000-8000-000000000003",
        "CU", 200_000, 12);
    calculatePeriod(card.userId(), 12, 13);
    assertReward(retail1.approvalId(), "1000");
    assertReward(discount.approvalId(), "1200");
    assertReward(retail2.approvalId(), "2000");

    ApprovalInsert limit = insertApproval(card, "31100000-0000-4000-8000-000000000004",
        "CU", 500_000, 14);
    calculatePeriod(card.userId(), 14, 15);
    assertReward(limit.approvalId(), "2800");
    assertEquals(0, new BigDecimal("5000").compareTo(jdbc.queryForObject(
        "SELECT SUM(reward_original_value-FLOOR(eligible_amount_krw*0.002)) "
            + "FROM user_benefit_usages WHERE user_card_id=?", BigDecimal.class,
        card.userCardId())));
  }

  @Test
  @DisplayName("같은 카드의 동시 승인은 중복 없이 직렬화되어 월 집계와 적립 한도를 유지한다")
  void serializesConcurrentApprovalsWithoutDuplicateSpend() throws Exception {
    TestCard card = registerCard("deep-dream-concurrency",
        "32000000-0000-4000-8000-000000000001");
    insertPerformance(card.userCardId(), 200_000);
    ApprovalInsert first = insertApproval(card, "32100000-0000-4000-8000-000000000001",
        "CU", 300_000, 5);
    ApprovalInsert second = insertApproval(card, "32100000-0000-4000-8000-000000000002",
        "이마트", 400_000, 6);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> one = executor.submit(() -> calculateApprovalAfter(start, first));
      Future<?> two = executor.submit(() -> calculateApprovalAfter(start, second));
      start.countDown();
      one.get();
      two.get();
    } finally {
      executor.shutdownNow();
    }

    assertEquals(2, jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_benefit_area_spend_events WHERE user_card_id=?",
        Integer.class, card.userCardId()));
    assertEquals(700_000, jdbc.queryForObject(
        "SELECT SUM(eligible_amount_krw) FROM user_benefit_area_monthly_spends "
            + "WHERE user_card_id=? AND usage_month='2026-08'", Integer.class,
        card.userCardId()));
    assertEquals(2, jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_benefit_usages WHERE user_card_id=?",
        Integer.class, card.userCardId()));
  }

  private void calculateApprovalAfter(CountDownLatch start, ApprovalInsert approval) {
    try {
      start.await();
      try (SqlSession session = sqlSession()) {
        new BenefitUsageCalculationService(session.getMapper(BenefitCalculationMapper.class))
            .calculateAndPersist(List.of(approval));
        session.commit();
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void calculatePeriod(String userId, int fromDay, int toDay) throws Exception {
    try (SqlSession session = sqlSession()) {
      new BenefitUsageCalculationService(session.getMapper(BenefitCalculationMapper.class))
          .calculateAndPersistForPeriod(userId, LocalDateTime.of(2026, 8, fromDay, 0, 0),
              LocalDateTime.of(2026, 8, toDay, 0, 0));
      session.commit();
    }
  }

  private TestCard registerCard(String subject, String userCardId) {
    String userId = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) "
        + "VALUES (?,?,'Deep Dream 테스트','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        userId, subject);
    var card = jdbc.queryForMap("SELECT card.card_id,card.issuer_id FROM cards card "
        + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
        + "WHERE version.name='신한카드 Deep Dream 체크' "
        + "ORDER BY version.last_seen_at DESC LIMIT 1");
    String credentialId = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO codef_account_credentials "
            + "(codef_account_credential_id,user_id,issuer_id,connected_id,"
            + "credential_identity_hash,status,created_at,updated_at) "
            + "VALUES (?,?,?,UUID(),SHA2(?,256),'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        credentialId, userId, card.get("issuer_id"), credentialId);
    jdbc.update("INSERT INTO user_cards (user_card_id,user_id,card_id,codef_account_credential_id,"
            + "card_name_from_codef,issuer_id,codef_card_key_hash,created_at,updated_at) "
            + "VALUES (?,?,?,?,?,?,SHA2(?,256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        userCardId, userId, card.get("card_id"), credentialId, "신한카드 Deep Dream 체크",
        card.get("issuer_id"), userCardId);
    return new TestCard(userId, userCardId);
  }

  private void insertPerformance(String userCardId, int previousSpend) {
    jdbc.update("INSERT INTO user_card_performance_snapshots "
            + "(performance_snapshot_id,user_card_id,performance_month,current_spend_amount,created_at,updated_at) "
            + "VALUES (UUID(),?,'2026-07',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
            + "(UUID(),?,'2026-08',800000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        userCardId, previousSpend, userCardId);
  }

  private ApprovalInsert insertApproval(
      TestCard card, String approvalId, String merchantName, int amount, int day) {
    String merchantId = jdbc.queryForObject(
        "SELECT merchant_id FROM merchants WHERE normalized_name=? LIMIT 1",
        String.class, merchantName);
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, day, 3, 0);
    jdbc.update("INSERT INTO card_payment_approvals "
            + "(approval_id,user_id,user_card_id,approval_number,approved_at,merchant_name,merchant_id,amount,"
            + "approval_status,source_payload,created_at) VALUES (?,?,?,?,?,?,?,?, 'approved','{}',UTC_TIMESTAMP(6))",
        approvalId, card.userId(), card.userCardId(), approvalId, approvedAt, merchantName,
        merchantId, amount);
    return new ApprovalInsert(approvalId, card.userId(), card.userCardId(), merchantId,
        approvalId, approvedAt, merchantName, amount, "{}");
  }

  private void assertReward(String approvalId, String expected) {
    BigDecimal actual = jdbc.queryForObject(
        "SELECT reward_original_value FROM user_benefit_usages WHERE approval_id=?",
        BigDecimal.class, approvalId);
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        () -> "approval=" + approvalId + ", expected=" + expected + ", actual=" + actual);
  }

  private static SqlSessionFactory createSqlSessionFactory() throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setMapperLocations(new PathMatchingResourcePatternResolver()
        .getResources("classpath*:mapper/benefit/*Mapper.xml"));
    return factory.getObject();
  }

  private SqlSession sqlSession() {
    return sqlSessionFactory.openSession(false);
  }

  private record TestCard(String userId, String userCardId) { }
}
