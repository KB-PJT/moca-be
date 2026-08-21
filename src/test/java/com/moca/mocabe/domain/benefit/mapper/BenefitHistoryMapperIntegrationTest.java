package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistorySummaryRow;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 혜택 이력 SQL의 실제 MySQL 조인·소유권·정렬을 검증한다. */
@Tag("integration")
@SpringJUnitConfig(BenefitHistoryMapperIntegrationTest.Config.class)
class BenefitHistoryMapperIntegrationTest {

  private static final String USER = "10000000-0000-4000-8000-000000000001";
  private static final String OTHER_USER = "10000000-0000-4000-8000-000000000002";
  private static final String ISSUER = "20000000-0000-4000-8000-000000000001";
  private static final String CARD = "30000000-0000-4000-8000-000000000001";
  private static final String VERSION = "40000000-0000-4000-8000-000000000001";
  private static final String CREDENTIAL = "50000000-0000-4000-8000-000000000001";
  private static final String USER_CARD = "60000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "70000000-0000-4000-8000-000000000001";
  private static final String OFFER = "80000000-0000-4000-8000-000000000001";
  private static final String RULE = "81000000-0000-4000-8000-000000000001";
  private static final String APPROVAL = "90000000-0000-4000-8000-000000000001";
  private static final String MISSED_APPROVAL = "90000000-0000-4000-8000-000000000002";
  private static final String GENERAL_APPROVAL = "90000000-0000-4000-8000-000000000003";
  private static final String LIMIT_APPROVAL = "90000000-0000-4000-8000-000000000004";
  private static final String USAGE = "a0000000-0000-4000-8000-000000000001";
  private static final String OUTCOME = "b0000000-0000-4000-8000-000000000001";
  private static final String LIMIT_OUTCOME = "b0000000-0000-4000-8000-000000000002";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private BenefitHistoryMapper mapper;

  @BeforeEach
  void setUp() {
    clean();
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) VALUES"
            + " (?,?,'모카','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER,
        "history-user");
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) VALUES"
            + " (?,?,'다른 사용자','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        OTHER_USER,
        "history-other");
    jdbc.update(
        "INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at) VALUES"
            + " (?,'H001','테스트카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        ISSUER);
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) VALUES"
            + " (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CARD,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions"
            + " (content_version_id,card_id,content_sha256,name,discontinued,first_seen_at,last_seen_at)"
            + " VALUES (?,?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','테스트"
            + " 카드',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION,
        CARD);
    jdbc.update(
        "INSERT INTO codef_account_credentials"
            + " (codef_account_credential_id,user_id,issuer_id,connected_id,credential_identity_hash,"
            + "status,created_at,updated_at) VALUES"
            + " (?,?,?,'history-connection',?,'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CREDENTIAL,
        USER,
        ISSUER,
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    jdbc.update(
        "INSERT INTO user_cards"
            + " (user_card_id,user_id,card_id,codef_account_credential_id,card_name_from_codef,"
            + "card_no,issuer_id,codef_card_key_hash,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,?,?, ?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD,
        USER,
        CARD,
        CREDENTIAL,
        "CODEF 카드",
        "1234********5678",
        ISSUER,
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title)"
            + " VALUES (?,?,1,'benefit','카페 10% 할인')",
        BENEFIT, VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers"
            + " (offer_id,benefit_id,offer_name,position,reward_type,value_type,calculation_mode,"
            + "calculation_basis,stacking_mode,valuation_scope,valuation_method) VALUES (?,?, '카페"
            + " 할인',1,'discount','percentage','flat','transaction_amount','standalone','transaction','direct')",
        OFFER,
        BENEFIT);
    jdbc.update(
        "INSERT INTO benefit_rules"
            + " (rule_id,offer_id,position,rule_effect,stacking_mode,reward_value,reward_unit,"
            + "previous_spend_min_krw) VALUES (?,?,1,'grant','standalone',10,'percent',300000)",
        RULE,
        OFFER);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at)"
            + " VALUES (?,?,?,'2026-07-17"
            + " 05:30:00','스타벅스',15000,'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        APPROVAL,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at) VALUES (?,?,?,'2026-07-18 05:30:00','이마트',20000,"
            + "'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        MISSED_APPROVAL,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at) VALUES (?,?,?,'2026-07-19 05:30:00','서점',12000,"
            + "'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        GENERAL_APPROVAL,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at) VALUES (?,?,?,'2026-07-20 05:30:00','영화관',18000,"
            + "'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        LIMIT_APPROVAL,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO user_card_performance_snapshots"
            + " (performance_snapshot_id,user_card_id,performance_month,current_spend_amount,"
            + "created_at,updated_at) VALUES (UUID(),?,'2026-06',120000,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD);
    jdbc.update(
        "INSERT INTO user_benefit_calculation_outcomes"
            + " (outcome_id,user_card_id,approval_id,offer_id,rule_id,usage_date,reward_unit,"
            + "expected_reward_value,applied_reward_value,missed_reward_value,outcome_status,"
            + "rejection_reason) VALUES (?,?,?,?,?,'2026-07-18','KRW',2000,0,2000,"
            + "'not_applied','PERFORMANCE_NOT_MET')",
        OUTCOME,
        USER_CARD,
        MISSED_APPROVAL,
        OFFER,
        RULE);
    jdbc.update(
        "INSERT INTO user_benefit_calculation_outcomes"
            + " (outcome_id,user_card_id,approval_id,offer_id,rule_id,usage_date,reward_unit,"
            + "expected_reward_value,applied_reward_value,missed_reward_value,outcome_status,"
            + "rejection_reason) VALUES (?,?,?,?,?,'2026-07-20','KRW',1800,500,1300,"
            + "'partially_applied','MONTHLY_LIMIT_EXHAUSTED')",
        LIMIT_OUTCOME,
        USER_CARD,
        LIMIT_APPROVAL,
        OFFER,
        RULE);
    jdbc.update(
        "INSERT INTO user_benefit_usages"
            + " (usage_id,user_card_id,offer_id,approval_id,usage_date,eligible_amount_krw,"
            + "reward_amount_krw,usage_status,approved_at,confirmed_at)"
            + " VALUES (?,?,?,?, '2026-07-17',15000,1500,'confirmed','2026-07-17"
            + " 05:30:00','2026-07-17 05:30:01')",
        USAGE,
        USER_CARD,
        OFFER,
        APPROVAL);
  }

  @AfterEach
  void tearDown() {
    clean();
  }

  @Test
  void readsHistoryAndPreventsAnotherUsersAccess() {
    LocalDateTime from = LocalDateTime.of(2026, 6, 30, 15, 0);
    LocalDateTime to = LocalDateTime.of(2026, 7, 31, 15, 0);
    List<BenefitHistoryRow> rows =
        mapper.findHistory(USER, from, to, null, "DISCOUNT");
    assertEquals(3, rows.size());
    BenefitHistoryRow performanceOutcome = find(rows, OUTCOME);
    assertEquals("NOT_APPLIED", performanceOutcome.getCalculationStatus());
    assertEquals(2000L, performanceOutcome.getMissedBenefitAmount());
    assertEquals(300000L, performanceOutcome.getRequiredPreviousSpendAmount());
    assertEquals(120000L, performanceOutcome.getPreviousMonthSpendAmount());
    assertEquals("이마트", performanceOutcome.getMerchantName());
    BenefitHistoryRow limitOutcome = find(rows, LIMIT_OUTCOME);
    assertEquals("PARTIALLY_APPLIED", limitOutcome.getCalculationStatus());
    assertEquals(500L, limitOutcome.getBenefitAmount());
    assertEquals("KRW", limitOutcome.getBenefitUnit());
    assertEquals(1300L, limitOutcome.getMissedBenefitAmount());
    assertEquals("MONTHLY_LIMIT_EXHAUSTED", limitOutcome.getRejectionReason());
    assertEquals("테스트 카드 1234********5678", find(rows, USAGE).getCardName());
    assertEquals(1500L, find(rows, USAGE).getBenefitAmount());
    assertEquals("KRW", find(rows, USAGE).getBenefitUnit());
    BenefitHistorySummaryRow summary = mapper.summarizeHistory(USER, from, to, USER_CARD);
    assertEquals(1500L, summary.totalBenefitAmount());
    assertEquals(1500L, summary.discountAmount());
    assertEquals(0L, summary.cashbackAmount());
    assertEquals(0L, summary.pointAmount());
    assertEquals(0L, summary.mileageAmount());
    List<BenefitHistoryRow> allRows =
        mapper.findHistory(USER, from, to, null, null);
    assertEquals(4, allRows.size());
    BenefitHistoryRow general = find(allRows, GENERAL_APPROVAL);
    assertEquals("NOT_CALCULATED", general.getCalculationStatus());
    assertNull(general.getBenefitType());
    assertNull(general.getBenefitUnit());
    assertNull(general.getBenefitTitle());
    assertEquals(0L, mapper.findHistory(OTHER_USER, from, to, null, null).size());
    assertNull(mapper.findDetail(OTHER_USER, USAGE));
    BenefitHistoryDetailRow detail = mapper.findDetail(USER, USAGE);
    assertEquals("카페 할인", detail.getBenefitTitle());
    assertEquals(0L, detail.getMonthlyLimitAmount());
    BenefitHistoryDetailRow missed = mapper.findDetail(USER, OUTCOME);
    assertEquals("PERFORMANCE_NOT_MET", missed.getRejectionReason());
    assertEquals(2000L, missed.getMissedBenefitAmount());
    BenefitHistoryDetailRow generalDetail = mapper.findDetail(USER, GENERAL_APPROVAL);
    assertEquals("NOT_CALCULATED", generalDetail.getCalculationStatus());
    assertNull(generalDetail.getBenefitTitle());
  }

  @Test
  void returnsPointValueAndUnitForAppliedPointBenefit() {
    String pointBenefit = "70000000-0000-4000-8000-000000000002";
    String pointOffer = "80000000-0000-4000-8000-000000000002";
    String pointApproval = "90000000-0000-4000-8000-000000000005";
    String pointUsage = "a0000000-0000-4000-8000-000000000002";
    String pointLimit = "82000000-0000-4000-8000-000000000002";
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title)"
            + " VALUES (?,?,2,'benefit','커피 포인트 적립')",
        pointBenefit,
        VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers"
            + " (offer_id,benefit_id,offer_name,position,reward_type,value_type,calculation_mode,"
            + "calculation_basis,stacking_mode,valuation_scope,valuation_method)"
            + " VALUES (?,?,'커피 포인트 적립',1,'points','percentage','flat',"
            + "'transaction_amount','standalone','transaction','direct')",
        pointOffer,
        pointBenefit);
    jdbc.update(
        "INSERT INTO benefit_limit_policies"
            + " (limit_policy_id,offer_id,policy_name,limit_period,limit_type,limit_unit)"
            + " VALUES (?,?,'포인트 월 한도','monthly','reward_amount','point')",
        pointLimit,
        pointOffer);
    jdbc.update(
        "INSERT INTO benefit_limit_tiers"
            + " (limit_tier_id,limit_policy_id,position,limit_value) VALUES (UUID(),?,1,5000)",
        pointLimit);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at) VALUES (?,?,?,'2026-07-21 05:30:00','컴포즈커피',3600,"
            + "'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        pointApproval,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO user_benefit_usages"
            + " (usage_id,user_card_id,offer_id,limit_policy_id,approval_id,usage_date,eligible_amount_krw,"
            + "reward_amount_krw,reward_original_value,reward_original_unit,usage_status,approved_at,"
            + "confirmed_at) VALUES (?,?,?,?,?, '2026-07-21',3600,0,7,'point','confirmed',"
            + "'2026-07-21 05:30:00','2026-07-21 05:30:01')",
        pointUsage,
        USER_CARD,
        pointOffer,
        pointLimit,
        pointApproval);

    List<BenefitHistoryRow> rows =
        mapper.findHistory(
            USER,
            LocalDateTime.of(2026, 6, 30, 15, 0),
            LocalDateTime.of(2026, 7, 31, 15, 0),
            USER_CARD,
            "POINT");

    assertEquals(1, rows.size());
    assertEquals(7L, rows.get(0).getBenefitAmount());
    assertEquals("POINT", rows.get(0).getBenefitUnit());
    assertEquals("POINT", rows.get(0).getBenefitType());
    BenefitHistorySummaryRow summary =
        mapper.summarizeHistory(
            USER,
            LocalDateTime.of(2026, 6, 30, 15, 0),
            LocalDateTime.of(2026, 7, 31, 15, 0),
            USER_CARD);
    assertEquals(1507L, summary.totalBenefitAmount());
    assertEquals(7L, summary.pointAmount());
    BenefitHistoryDetailRow detail = mapper.findDetail(USER, pointUsage);
    assertEquals(7L, detail.getMonthlyUsedAmount());
    assertEquals(5000L, detail.getMonthlyLimitAmount());
  }

  private BenefitHistoryRow find(List<BenefitHistoryRow> rows, String historyId) {
    return rows.stream()
        .filter(row -> historyId.equals(row.getBenefitHistoryId()))
        .findFirst()
        .orElseThrow();
  }

  private void clean() {
    jdbc.update("DELETE FROM user_benefit_calculation_outcomes");
    jdbc.update("DELETE FROM user_benefit_usages");
    jdbc.update("DELETE FROM user_card_performance_snapshots");
    jdbc.update("DELETE FROM card_payment_approvals");
    jdbc.update("DELETE FROM benefit_limit_tiers");
    jdbc.update("DELETE FROM benefit_limit_policies");
    jdbc.update("DELETE FROM benefit_rules");
    jdbc.update("DELETE FROM benefit_offers");
    jdbc.update("DELETE FROM card_benefits");
    jdbc.update("DELETE FROM user_cards");
    jdbc.update("DELETE FROM codef_account_credentials");
    jdbc.update("DELETE FROM card_content_versions");
    jdbc.update("DELETE FROM cards");
    jdbc.update("DELETE FROM issuers");
    jdbc.update("DELETE FROM users");
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = BenefitHistoryMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class Config { }
}
