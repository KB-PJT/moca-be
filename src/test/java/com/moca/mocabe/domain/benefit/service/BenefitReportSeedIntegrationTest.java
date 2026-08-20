package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.mapper.BenefitCalculationMapper;
import com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper;
import com.moca.mocabe.domain.benefit.model.MonthlyBenefitLimit;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.flywaydb.core.Flyway;
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
@DisplayName("실제 seed 카드 기반 혜택 계산·리포트 E2E")
class BenefitReportSeedIntegrationTest {
  private static final String USER_ID = "20000000-0000-4000-8000-000000000099";
  private static final List<String> CARD_NAMES = List.of(
      "현대카드Z work Edition2",
      "올바른POINT체크카드",
      "신한카드 SOL Plan",
      "무신사 현대카드",
      "신한카드 Point Plan 체크 캐릭터형(짱구)");

  @Test
  @DisplayName("실제 seed의 다섯 카드를 등록하고 8월 승인 혜택을 계산해 리포트로 조회한다")
  void calculatesSeedCardBenefitsUsingRealMapperAndTierBoundaries() throws Exception {
    try (MySQLContainer container = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
        .withDatabaseName("moca")
        .withUsername("moca")
        .withPassword("moca")
        .withStartupTimeout(Duration.ofMinutes(3))) {
      container.start();
      DataSource dataSource = dataSource(container);
      Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
          .target("23").load().migrate();
      new ResourceDatabasePopulator(new ClassPathResource("db/migration/moca_final_seed.sql"))
          .execute(dataSource);

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      removePointPlanCheckStructure(jdbc);
      insertPointPlanSiblingStructure(jdbc);
      Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
      supplementSolPlanCurrentSpendTiers(jdbc);
      insertUser(jdbc);
      List<SeedCard> cards = registerSeedCards(jdbc);
      assertEquals(CARD_NAMES, cards.stream().map(SeedCard::name).toList());
      insertPreviousMonthData(jdbc, cards);
      List<ApprovalInsert> augustApprovals = insertAugustApprovals(jdbc, cards);

      try (SqlSession session = sqlSession(dataSource)) {
        BenefitCalculationMapper calculationMapper =
            session.getMapper(BenefitCalculationMapper.class);
        BenefitHistoryMapper historyMapper = session.getMapper(BenefitHistoryMapper.class);
        assertSolPlanTierBoundaries(jdbc, calculationMapper);
        for (SeedCard card : cards.subList(0, 4)) {
          assertFalse(calculationMapper.findSimpleRulesForUserCard(card.userCardId(),
              java.time.LocalDate.of(2026, 8, 10)).isEmpty());
        }
        assertPointPlanCheckStructure(jdbc, cards.get(4), calculationMapper);

        new BenefitUsageCalculationService(calculationMapper).calculateAndPersist(augustApprovals);
        new BenefitUsageCalculationService(calculationMapper)
            .calculateAndPersist(insertAdditionalPointPlanBrandApprovals(jdbc, cards.get(4)));
        ApprovalInsert performanceNotMet = insertPointPlanPerformanceNotMetApproval(jdbc, cards.get(4));
        new BenefitUsageCalculationService(calculationMapper)
            .calculateAndPersist(List.of(performanceNotMet));
        session.commit();

        assertTrue(count(jdbc, "SELECT COUNT(*) FROM user_benefit_usages WHERE user_card_id IN ("
            + placeholders(cards.size()) + ")", userCardIds(cards).toArray()) > 0);
        assertTrue(count(jdbc, "SELECT COUNT(*) FROM user_benefit_calculation_outcomes "
            + "WHERE user_card_id IN (" + placeholders(cards.size()) + ")",
            userCardIds(cards).toArray()) >= cards.size() - 1);

        BenefitHistoryResponse report =
            new BenefitHistoryQueryService(historyMapper)
                .getHistory(USER_ID, "2026-08", null, null, "LATEST", 1, 100);
        assertEquals(cards.size(), report.getData().stream()
            .map(item -> item.getUserCardId()).distinct().count());
        assertTrue(report.getData().stream()
            .noneMatch(item -> item.getApprovedAt().startsWith("2026-07")));
        assertTrue(report.getData().stream()
            .anyMatch(item -> "APPLIED".equals(item.getCalculationStatus())));
        assertTrue(report.getData().stream()
            .anyMatch(item -> "NOT_APPLIED".equals(item.getCalculationStatus())));
        assertPointPlanCheckCalculationAndReport(jdbc, report, cards.get(4));
      }
    }
  }

  private void removePointPlanCheckStructure(JdbcTemplate jdbc) {
    String benefitId = "4da2cd93-b8e1-585c-bae4-7118aef652f8";
    jdbc.update("DELETE tier FROM benefit_limit_tiers tier "
        + "INNER JOIN benefit_limit_policies policy "
        + "ON policy.limit_policy_id=tier.limit_policy_id "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=policy.offer_id "
        + "WHERE offer.benefit_id=?", benefitId);
    jdbc.update("DELETE policy FROM benefit_limit_policies policy "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=policy.offer_id "
        + "WHERE offer.benefit_id=?", benefitId);
    jdbc.update("DELETE target FROM benefit_rule_targets target "
        + "INNER JOIN benefit_rules rule_data ON rule_data.rule_id=target.rule_id "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule_data.offer_id "
        + "WHERE offer.benefit_id=?", benefitId);
    jdbc.update("DELETE rule_data FROM benefit_rules rule_data "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule_data.offer_id "
        + "WHERE offer.benefit_id=?", benefitId);
    jdbc.update("DELETE FROM benefit_offers WHERE benefit_id=?", benefitId);
    jdbc.update("UPDATE card_benefits SET structuring_status='PARSE_FAILED' WHERE benefit_id=?",
        benefitId);
  }

  private void insertPointPlanSiblingStructure(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO benefit_offers (offer_id,benefit_id,offer_name,position,priority,"
            + "reward_type,value_type,calculation_mode,calculation_basis,stacking_mode,"
            + "valuation_scope,valuation_method,created_at,updated_at) "
            + "VALUES ('24000000-0000-4000-8000-000000000001',"
            + "'4da2cd93-b8e1-585c-bae4-7118aef652f8','sibling offer',2,0,'other',"
            + "'other','other','other','standalone','transaction','not_valued',"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
    jdbc.update("INSERT INTO benefit_rules (rule_id,offer_id,position,priority,rule_name,"
            + "rule_effect,stacking_mode,reward_value,reward_unit,created_at,updated_at) "
            + "VALUES ('24000000-0000-4000-8000-000000000002',"
            + "'24000000-0000-4000-8000-000000000001',1,0,'sibling rule','grant',"
            + "'standalone',1,'point',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
    jdbc.update("INSERT INTO benefit_limit_policies (limit_policy_id,offer_id,policy_name,"
            + "limit_period,limit_type,limit_unit,created_at,updated_at) "
            + "VALUES ('24000000-0000-4000-8000-000000000003',"
            + "'24000000-0000-4000-8000-000000000001','sibling policy','monthly',"
            + "'reward_amount','point',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
  }

  private void assertPointPlanCheckStructure(
      JdbcTemplate jdbc, SeedCard card, BenefitCalculationMapper calculationMapper) {
    assertEquals("02222c8f-d28a-5edc-969c-faa153fd7806",
        jdbc.queryForObject("SELECT content_version_id FROM card_content_versions "
            + "WHERE card_id=? ORDER BY last_seen_at DESC LIMIT 1", String.class, card.cardId()));
    assertEquals(5, count(jdbc, "SELECT COUNT(*) FROM card_benefits benefit "
        + "INNER JOIN card_content_versions version "
        + "ON version.content_version_id=benefit.content_version_id "
        + "WHERE version.card_id=? AND benefit.record_type='benefit'", card.cardId()));
    assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM benefit_offers offer "
        + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
        + "WHERE offer.benefit_id='4da2cd93-b8e1-585c-bae4-7118aef652f8' "
        + "AND offer.position=1 AND rule_data.position=1 "
        + "AND target.target_type='merchant' "
        + "AND target.target_code IN ('CU','GS25','세븐일레븐')"));
    assertEquals(List.of("CU", "GS25", "세븐일레븐"), jdbc.queryForList(
        "SELECT target.target_code FROM benefit_rule_targets target "
            + "INNER JOIN benefit_rules rule_data ON rule_data.rule_id=target.rule_id "
            + "INNER JOIN benefit_offers offer ON offer.offer_id=rule_data.offer_id "
            + "WHERE offer.benefit_id='4da2cd93-b8e1-585c-bae4-7118aef652f8' "
            + "AND offer.position=1 AND rule_data.position=1 "
            + "AND target.target_type='merchant' ORDER BY target.condition_group",
        String.class));
    assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM benefit_rule_targets target "
        + "INNER JOIN benefit_rules rule_data ON rule_data.rule_id=target.rule_id "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule_data.offer_id "
        + "WHERE offer.benefit_id='4da2cd93-b8e1-585c-bae4-7118aef652f8' "
        + "AND offer.position=1 AND rule_data.position=1 "
        + "AND target.target_type='merchant_category' "
        + "AND target.target_code='CONVENIENCE_STORE'"));
    assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM benefit_rule_targets "
        + "WHERE rule_id='24000000-0000-4000-8000-000000000002'"));
    assertEquals("sibling policy", jdbc.queryForObject(
        "SELECT policy_name FROM benefit_limit_policies "
            + "WHERE limit_policy_id='24000000-0000-4000-8000-000000000003'", String.class));
    assertFalse(calculationMapper.findSimpleRulesForUserCard(
        card.userCardId(), java.time.LocalDate.of(2026, 8, 10)).isEmpty());
  }

  private ApprovalInsert insertPointPlanPerformanceNotMetApproval(
      JdbcTemplate jdbc, SeedCard card) {
    jdbc.update("UPDATE user_card_performance_snapshots SET current_spend_amount=199999 "
        + "WHERE user_card_id=? AND performance_month='2026-07'", card.userCardId());
    String approvalId = "23000000-0000-4000-8000-000000000099";
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 20, 3, 0);
    String merchantId = merchantId(jdbc, "CU");
    insertApproval(jdbc, approvalId, card, approvedAt, 20_000, merchantId);
    return new ApprovalInsert(approvalId, USER_ID, card.userCardId(), null,
        "AUG-POINT-NOT-MET", approvedAt, "CU", 20_000, "{}");
  }

  private List<ApprovalInsert> insertAdditionalPointPlanBrandApprovals(
      JdbcTemplate jdbc, SeedCard card) {
    List<ApprovalInsert> approvals = new ArrayList<>();
    List<String> merchants = List.of("GS25", "세븐일레븐", "이마트24");
    for (int index = 0; index < merchants.size(); index++) {
      String merchantName = merchants.get(index);
      String approvalId = String.format("25000000-0000-4000-8000-%012d", index + 1);
      LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 11 + index, 3, 0);
      insertApproval(jdbc, approvalId, card, approvedAt, 20_000,
          merchantId(jdbc, merchantName));
      approvals.add(new ApprovalInsert(approvalId, USER_ID, card.userCardId(), null,
          "AUG-POINT-" + index, approvedAt, merchantName, 20_000, "{}"));
    }
    return approvals;
  }

  private void assertPointPlanCheckCalculationAndReport(
      JdbcTemplate jdbc, BenefitHistoryResponse report, SeedCard card) {
    assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM user_benefit_usages usage_data "
        + "WHERE usage_data.user_card_id=? AND usage_data.reward_original_unit='point' "
        + "AND usage_data.reward_original_value=1000", card.userCardId()));
    assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM user_benefit_usages usage_data "
        + "INNER JOIN card_payment_approvals approval "
        + "ON approval.approval_id=usage_data.approval_id "
        + "INNER JOIN merchants merchant ON merchant.merchant_id=approval.merchant_id "
        + "WHERE usage_data.user_card_id=? AND merchant.normalized_name='이마트24'",
        card.userCardId()));
    assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM user_benefit_calculation_outcomes outcome "
        + "WHERE outcome.user_card_id=? AND outcome.outcome_status='not_applied' "
        + "AND outcome.rejection_reason='PERFORMANCE_NOT_MET' "
        + "AND outcome.expected_reward_value=1000 "
        + "AND outcome.missed_reward_value=1000", card.userCardId()));
    assertTrue(report.getData().stream()
        .filter(item -> card.userCardId().equals(item.getUserCardId()))
        .noneMatch(item -> "NOT_CALCULATED".equals(item.getCalculationStatus())));
    assertTrue(report.getData().stream()
        .anyMatch(item -> card.userCardId().equals(item.getUserCardId())
            && "APPLIED".equals(item.getCalculationStatus())
            && "POINT".equals(item.getBenefitType())));
    assertTrue(report.getData().stream()
        .anyMatch(item -> card.userCardId().equals(item.getUserCardId())
            && "NOT_APPLIED".equals(item.getCalculationStatus())
            && "PERFORMANCE_NOT_MET".equals(item.getRejectionReason())));
  }

  private void supplementSolPlanCurrentSpendTiers(JdbcTemplate jdbc) {
    String policyId = solPlanLimitPolicyId(jdbc);
    jdbc.update("UPDATE benefit_limit_tiers SET current_spend_min_krw=0 "
        + "WHERE limit_policy_id=? AND position=1", policyId);
    jdbc.update("INSERT INTO benefit_limit_tiers (limit_tier_id,limit_policy_id,position,"
            + "limit_value,previous_spend_min_krw,current_spend_min_krw,created_at,updated_at) "
            + "VALUES (UUID(),?,2,60000,400000,300000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),"
            + "(UUID(),?,3,70000,400000,500000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        policyId, policyId);
  }

  private void assertSolPlanTierBoundaries(
      JdbcTemplate jdbc, BenefitCalculationMapper calculationMapper) {
    String offerId = jdbc.queryForObject(
        "SELECT offer.offer_id FROM benefit_offers offer "
            + "INNER JOIN card_benefits benefit ON benefit.benefit_id=offer.benefit_id "
            + "INNER JOIN card_content_versions version "
            + "ON version.content_version_id=benefit.content_version_id "
            + "WHERE version.name='신한카드 SOL Plan' "
            + "AND offer.offer_name='국내/외 전가맹점 기본 적립' LIMIT 1",
        String.class);
    var candidates = calculationMapper.findMonthlyRewardLimitCandidates(
        offerId, java.time.LocalDate.of(2026, 8, 10), "point");
    BenefitLimitTierSelector selector = new BenefitLimitTierSelector();

    MonthlyBenefitLimit below =
        selector.select(candidates, money(400_000), money(299_999)).limit();
    MonthlyBenefitLimit middle =
        selector.select(candidates, money(400_000), money(300_000)).limit();
    MonthlyBenefitLimit high =
        selector.select(candidates, money(400_000), money(500_000)).limit();
    assertEquals(0, below.limitValue().compareTo(money(50_000)));
    assertEquals(0, middle.limitValue().compareTo(money(60_000)));
    assertEquals(0, high.limitValue().compareTo(money(70_000)));
  }

  private String solPlanLimitPolicyId(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        "SELECT policy.limit_policy_id FROM benefit_limit_policies policy "
            + "INNER JOIN benefit_offers offer ON offer.offer_id=policy.offer_id "
            + "INNER JOIN card_benefits benefit ON benefit.benefit_id=offer.benefit_id "
            + "INNER JOIN card_content_versions version "
            + "ON version.content_version_id=benefit.content_version_id "
            + "WHERE version.name='신한카드 SOL Plan' "
            + "AND policy.policy_name='쓸수록 SOLSOL 통합 적립 한도' LIMIT 1",
        String.class);
  }

  private java.math.BigDecimal money(int value) {
    return java.math.BigDecimal.valueOf(value);
  }

  private List<SeedCard> registerSeedCards(JdbcTemplate jdbc) {
    Map<String, String> credentialByIssuer = new LinkedHashMap<>();
    List<SeedCard> cards = new ArrayList<>();
    for (int index = 0; index < CARD_NAMES.size(); index++) {
      String name = CARD_NAMES.get(index);
      Map<String, Object> row = jdbc.queryForMap(
          "SELECT card.card_id, card.issuer_id FROM cards card "
              + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
              + "WHERE version.name=? ORDER BY version.last_seen_at DESC LIMIT 1", name);
      String issuerId = row.get("issuer_id").toString();
      String credentialId = credentialByIssuer.computeIfAbsent(issuerId,
          ignored -> insertCredential(jdbc, issuerId));
      String userCardId = String.format("21000000-0000-4000-8000-%012d", index + 1);
      jdbc.update("INSERT INTO user_cards (user_card_id,user_id,card_id,"
              + "codef_account_credential_id,card_name_from_codef,issuer_id,codef_card_key_hash,"
              + "created_at,updated_at) VALUES (?,?,?,?,?,?,SHA2(?,256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
          userCardId, USER_ID, row.get("card_id"), credentialId, name, issuerId, userCardId);
      cards.add(new SeedCard(name, row.get("card_id").toString(), userCardId));
    }
    return cards;
  }

  private String insertCredential(JdbcTemplate jdbc, String issuerId) {
    String id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO codef_account_credentials (codef_account_credential_id,user_id,issuer_id,"
            + "connected_id,credential_identity_hash,status,created_at,updated_at) "
            + "VALUES (?,?,?,UUID(),SHA2(?,256),'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        id, USER_ID, issuerId, id);
    return id;
  }

  private void insertPreviousMonthData(JdbcTemplate jdbc, List<SeedCard> cards) {
    for (int index = 0; index < cards.size(); index++) {
      SeedCard card = cards.get(index);
      int spend = index == 1 ? 0 : 3_000_000;
      jdbc.update("INSERT INTO user_card_performance_snapshots "
              + "(performance_snapshot_id,user_card_id,performance_month,current_spend_amount,"
              + "created_at,updated_at) VALUES (UUID(),?,'2026-07',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
          card.userCardId(), spend);
      jdbc.update("INSERT INTO user_card_performance_snapshots "
              + "(performance_snapshot_id,user_card_id,performance_month,current_spend_amount,"
              + "created_at,updated_at) VALUES (UUID(),?,'2026-08',600000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
          card.userCardId());
      insertApproval(jdbc, "july-" + index, card, LocalDateTime.of(2026, 7, 10, 3, 0), 100_000);
    }
  }

  private List<ApprovalInsert> insertAugustApprovals(JdbcTemplate jdbc, List<SeedCard> cards) {
    List<ApprovalInsert> approvals = new ArrayList<>();
    for (int index = 0; index < cards.size(); index++) {
      SeedCard card = cards.get(index);
      String approvalId = String.format("23000000-0000-4000-8000-%012d", index + 1);
      LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 5 + index, 3, 0);
      int amount = index == 4 ? 20_000 : 100_000;
      String merchantId = index == 4 ? merchantId(jdbc, "CU") : null;
      insertApproval(jdbc, approvalId, card, approvedAt, amount, merchantId);
      approvals.add(new ApprovalInsert(approvalId, USER_ID, card.userCardId(), null,
          "AUG-" + index, approvedAt, index == 4 ? "CU" : "일반 가맹점", amount, "{}"));
    }
    return approvals;
  }

  private void insertApproval(
      JdbcTemplate jdbc, String approvalId, SeedCard card, LocalDateTime approvedAt, int amount) {
    insertApproval(jdbc, approvalId, card, approvedAt, amount, null);
  }

  private void insertApproval(
      JdbcTemplate jdbc, String approvalId, SeedCard card, LocalDateTime approvedAt,
      int amount, String merchantId) {
    String merchantName = merchantId == null ? "일반 가맹점" : jdbc.queryForObject(
        "SELECT name FROM merchants WHERE merchant_id=?", String.class, merchantId);
    jdbc.update("INSERT INTO card_payment_approvals "
            + "(approval_id,user_id,user_card_id,approval_number,approved_at,merchant_name,merchant_id,amount,"
            + "approval_status,source_payload,created_at) "
            + "VALUES (?,?,?,?,?,?,?,?,'approved','{}',UTC_TIMESTAMP(6))",
        approvalId, USER_ID, card.userCardId(), approvalId, approvedAt,
        merchantName, merchantId, amount);
  }

  private String merchantId(JdbcTemplate jdbc, String merchantName) {
    return jdbc.queryForObject(
        "SELECT merchant_id FROM merchants WHERE normalized_name=? LIMIT 1",
        String.class, merchantName);
  }

  private SqlSession sqlSession(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setMapperLocations(new PathMatchingResourcePatternResolver()
        .getResources("classpath*:mapper/benefit/*Mapper.xml"));
    return factory.getObject().openSession(false);
  }

  private void insertUser(JdbcTemplate jdbc) {
    jdbc.update("INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) "
            + "VALUES (?,'benefit-seed-e2e','혜택 seed 사용자','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_ID);
  }

  private DataSource dataSource(MySQLContainer container) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(container.getDriverClassName());
    dataSource.setUrl(container.getJdbcUrl());
    dataSource.setUsername(container.getUsername());
    dataSource.setPassword(container.getPassword());
    return dataSource;
  }

  private int count(JdbcTemplate jdbc, String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private String placeholders(int size) {
    return String.join(",", java.util.Collections.nCopies(size, "?"));
  }

  private List<String> userCardIds(List<SeedCard> cards) {
    return cards.stream().map(SeedCard::userCardId).toList();
  }

  private record SeedCard(String name, String cardId, String userCardId) {
  }
}
