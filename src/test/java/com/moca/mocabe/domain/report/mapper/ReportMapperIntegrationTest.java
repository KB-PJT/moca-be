package com.moca.mocabe.domain.report.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.report.model.BenefitTypeAmountRow;
import com.moca.mocabe.domain.report.model.MissedBenefitRow;
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

/** 혜택·카테고리·실적 리포트 조인이 실제 MySQL에서 반환 타입까지 정상 매핑되는지 검증한다. */
@Tag("integration")
@SpringJUnitConfig(ReportMapperIntegrationTest.Config.class)
class ReportMapperIntegrationTest {
  private static final String USER = "11000000-0000-4000-8000-000000000001";
  private static final String ISSUER = "22000000-0000-4000-8000-000000000001";
  private static final String CARD = "33000000-0000-4000-8000-000000000001";
  private static final String VERSION = "44000000-0000-4000-8000-000000000001";
  private static final String CREDENTIAL = "55000000-0000-4000-8000-000000000001";
  private static final String USER_CARD = "66000000-0000-4000-8000-000000000001";
  private static final String CATEGORY = "77000000-0000-4000-8000-000000000001";
  private static final String MERCHANT = "88000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "99000000-0000-4000-8000-000000000001";
  private static final String OFFER = "aa000000-0000-4000-8000-000000000001";
  private static final String RULE = "bb000000-0000-4000-8000-000000000001";
  private static final String POLICY = "cc000000-0000-4000-8000-000000000001";
  private static final String APPROVAL = "dd000000-0000-4000-8000-000000000001";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReportMapper mapper;

  @BeforeEach
  void setUp() {
    clean();
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) VALUES"
            + " (?,?,'리포트','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER,
        "report-user");
    jdbc.update(
        "INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at) VALUES"
            + " (?,'R001','리포트카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        ISSUER);
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) VALUES"
            + " (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CARD,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions"
            + " (content_version_id,card_id,content_sha256,name,representative_spend,discontinued,"
            + "first_seen_at,last_seen_at)"
            + " VALUES (?,?,'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd','리포트"
            + " 카드',500000,FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION,
        CARD);
    jdbc.update(
        "INSERT INTO card_performance_tiers"
            + " (performance_tier_id,content_version_id,tier_number,minimum_spend_krw,maximum_spend_krw)"
            + " VALUES ('45000000-0000-4000-8000-000000000001',?,1,300000,499999),"
            + " ('45000000-0000-4000-8000-000000000002',?,2,500000,NULL)",
        VERSION,
        VERSION);
    jdbc.update(
        "INSERT INTO codef_account_credentials"
            + " (codef_account_credential_id,user_id,issuer_id,connected_id,credential_identity_hash,"
            + "status,created_at,updated_at) VALUES"
            + " (?,?,?,'report-connection',?,'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CREDENTIAL,
        USER,
        ISSUER,
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
    jdbc.update(
        "INSERT INTO user_cards"
            + " (user_card_id,user_id,card_id,codef_account_credential_id,card_name_from_codef,"
            + "issuer_id,codef_card_key_hash,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD,
        USER,
        CARD,
        CREDENTIAL,
        "CODEF 리포트 카드",
        ISSUER,
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    jdbc.update(
        "INSERT INTO merchant_categories"
            + " (merchant_category_id,category_code,category_name,created_at,updated_at) VALUES"
            + " (?,'CAFE','카페',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CATEGORY);
    jdbc.update(
        "INSERT INTO merchants"
            + " (merchant_id,merchant_category_id,name,normalized_name,status,created_at,updated_at)"
            + " VALUES (?,?, '스타벅스','스타벅스','active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        MERCHANT,
        CATEGORY);
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title)"
            + " VALUES (?,?,1,'benefit','카페 할인')",
        BENEFIT,
        VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers"
            + " (offer_id,benefit_id,offer_name,position,reward_type,value_type,calculation_mode,"
            + "calculation_basis,stacking_mode,valuation_scope,valuation_method) VALUES (?,?, '카페"
            + " 할인',1,'discount','percentage','flat','transaction_amount','standalone','transaction','direct')",
        OFFER,
        BENEFIT);
    jdbc.update(
        "INSERT INTO benefit_rules"
            + " (rule_id,offer_id,position,rule_effect,stacking_mode,reward_value,reward_unit)"
            + " VALUES (?,?,1,'grant','standalone',10,'percent')",
        RULE,
        OFFER);
    jdbc.update(
        "INSERT INTO benefit_limit_policies"
            + " (limit_policy_id,offer_id,policy_name,limit_period,limit_type,limit_unit) VALUES"
            + " (?,?, '월 한도','monthly','reward_amount','KRW')",
        POLICY,
        OFFER);
    jdbc.update(
        "INSERT INTO benefit_limit_tiers (limit_tier_id,limit_policy_id,position,limit_value)"
            + " VALUES ('ee000000-0000-4000-8000-000000000001',?,?,5000)",
        POLICY,
        1);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,merchant_id,approved_at,merchant_name,amount,"
            + "approval_status,source_payload,created_at)"
            + " VALUES (?,?,?,?, '2026-07-17"
            + " 05:30:00','스타벅스',15000,'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        APPROVAL,
        USER,
        USER_CARD,
        MERCHANT);
    jdbc.update(
        "INSERT INTO user_benefit_usages"
            + " (usage_id,user_card_id,offer_id,rule_id,limit_policy_id,approval_id,usage_date,"
            + "eligible_amount_krw,reward_amount_krw,usage_status,approved_at,confirmed_at) VALUES"
            + " ('ff000000-0000-4000-8000-000000000001',?,?,?,?,?,'2026-07-17',15000,1500,'confirmed','2026-07-17"
            + " 05:30:00','2026-07-17 05:30:01')",
        USER_CARD,
        OFFER,
        RULE,
        POLICY,
        APPROVAL);
    jdbc.update(
        "INSERT INTO user_benefit_calculation_outcomes"
            + " (outcome_id,user_card_id,approval_id,offer_id,rule_id,limit_policy_id,usage_date,"
            + "reward_unit,expected_reward_value,applied_reward_value,missed_reward_value,"
            + "outcome_status,rejection_reason)"
            + " VALUES"
            + " ('fe000000-0000-4000-8000-000000000001',?,?,?,?,?,'2026-07-17','KRW',5000,"
            + "1500,3500,'partially_applied','NONE')",
        USER_CARD,
        APPROVAL,
        OFFER,
        RULE,
        POLICY);
    jdbc.update(
        "INSERT INTO user_card_performance_snapshots"
            + " (performance_snapshot_id,user_card_id,performance_month,current_spend_amount,created_at,updated_at)"
            + " VALUES"
            + " ('ab000000-0000-4000-8000-000000000001',?,'2026-07',382000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD);
  }

  @AfterEach
  void tearDown() {
    clean();
  }

  @Test
  void aggregatesConfirmedBenefitsCategoriesLimitsAndPerformance() {
    LocalDateTime from = LocalDateTime.of(2026, 6, 30, 15, 0),
        to = LocalDateTime.of(2026, 7, 31, 15, 0);
    List<BenefitTypeAmountRow> types = mapper.findBenefitAmountsByType(USER, from, to);
    assertEquals(1, types.size());
    assertEquals("DISCOUNT", types.get(0).benefitType());
    assertEquals(1500L, types.get(0).amount());
    assertEquals(
        "CAFE", mapper.findBenefitAmountsByCategory(USER, from, to, 3).get(0).categoryCode());
    assertEquals(
        3500L,
        mapper.findMonthlyRemainingBenefits(USER, USER_CARD, "2026-07").get(0).limitAmount()
            - mapper.findMonthlyRemainingBenefits(USER, USER_CARD, "2026-07").get(0).usedAmount());
    assertEquals(
        382000L, mapper.findPerformanceCard(USER, USER_CARD, "2026-07").currentPerformanceAmount());
    assertEquals(1, mapper.findPerformanceCard(USER, USER_CARD, "2026-07").currentTier());
    assertEquals(2, mapper.findPerformanceCard(USER, USER_CARD, "2026-07").nextTier());
    assertEquals(
        500000L, mapper.findPerformanceCard(USER, USER_CARD, "2026-07").currentTierTargetAmount());
    assertEquals(1, mapper.findPerformanceCards(USER, "2026-07").size());
    assertEquals(2, mapper.findPerformanceTiers(USER).size());
  }

  @Test
  void returnsUnusedMonthlyLimitWithoutCalculationOutcomesOrUsages() {
    jdbc.update("DELETE FROM user_benefit_calculation_outcomes WHERE user_card_id = ?", USER_CARD);
    jdbc.update("DELETE FROM user_benefit_usages WHERE user_card_id = ?", USER_CARD);

    MissedBenefitRow row =
        mapper.findMonthlyRemainingBenefits(USER, USER_CARD, "2026-07").get(0);

    assertEquals(0L, row.usedAmount());
    assertEquals(5_000L, row.limitAmount());
  }

  @Test
  void fallsBackToRepresentativeSpendWhenCardHasNoPerformanceTiers() {
    String cardWithoutTier = "33000000-0000-4000-8000-000000000002";
    String versionWithoutTier = "44000000-0000-4000-8000-000000000002";
    String userCardWithoutTier = "66000000-0000-4000-8000-000000000002";
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) VALUES"
            + " (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        cardWithoutTier,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions"
            + " (content_version_id,card_id,content_sha256,name,representative_spend,discontinued,"
            + "first_seen_at,last_seen_at)"
            + " VALUES (?,?,'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc','구간"
            + " 없는 카드',400000,FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        versionWithoutTier,
        cardWithoutTier);
    jdbc.update(
        "INSERT INTO user_cards"
            + " (user_card_id,user_id,card_id,codef_account_credential_id,card_name_from_codef,"
            + "issuer_id,codef_card_key_hash,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        userCardWithoutTier,
        USER,
        cardWithoutTier,
        CREDENTIAL,
        "CODEF 구간 없는 카드",
        ISSUER,
        "abababababababababababababababababababababababababababababababab");
    jdbc.update(
        "INSERT INTO user_card_performance_snapshots"
            + " (performance_snapshot_id,user_card_id,performance_month,current_spend_amount,created_at,updated_at)"
            + " VALUES"
            + " ('ab000000-0000-4000-8000-000000000002',?,'2026-07',100000,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        userCardWithoutTier);

    var row = mapper.findPerformanceCard(USER, userCardWithoutTier, "2026-07");

    assertEquals(0, row.currentTier());
    assertEquals(null, row.nextTier());
    assertEquals(400000L, row.currentTierTargetAmount());
  }

  private void clean() {
    jdbc.update("DELETE FROM user_benefit_calculation_outcomes");
    jdbc.update("DELETE FROM user_benefit_usages");
    jdbc.update("DELETE FROM card_payment_approvals");
    jdbc.update("DELETE FROM user_card_performance_snapshots");
    jdbc.update("DELETE FROM benefit_limit_tiers");
    jdbc.update("DELETE FROM benefit_limit_policies");
    jdbc.update("DELETE FROM benefit_rules");
    jdbc.update("DELETE FROM benefit_offers");
    jdbc.update("DELETE FROM card_benefits");
    jdbc.update("DELETE FROM merchants WHERE merchant_id = ?", MERCHANT);
    jdbc.update("DELETE FROM merchant_categories WHERE merchant_category_id = ?", CATEGORY);
    jdbc.update("DELETE FROM user_cards");
    jdbc.update("DELETE FROM codef_account_credentials");
    jdbc.update("DELETE FROM card_performance_tiers");
    jdbc.update("DELETE FROM card_content_versions");
    jdbc.update("DELETE FROM cards");
    jdbc.update("DELETE FROM issuers");
    jdbc.update("DELETE FROM users");
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = ReportMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class Config { }
}
