package com.moca.mocabe.domain.merchant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.merchant.model.MerchantBenefitTierRow;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 단건·배치 카드 추천 SQL이 실제 MySQL 8에서 실행되는지 검증한다. */
@Tag("integration")
@org.springframework.test.context.junit.jupiter.SpringJUnitConfig(
    MerchantCardRecommendationMapperIntegrationTest.MapperTestConfig.class)
class MerchantCardRecommendationMapperIntegrationTest {
  private static final String USER = "d1000000-0000-4000-8000-000000000001";
  private static final String ISSUER = "d2000000-0000-4000-8000-000000000001";
  private static final String CARD = "d3000000-0000-4000-8000-000000000001";
  private static final String VERSION = "d4000000-0000-4000-8000-000000000001";
  private static final String CREDENTIAL = "d5000000-0000-4000-8000-000000000001";
  private static final String USER_CARD = "d6000000-0000-4000-8000-000000000001";
  private static final String CATEGORY = "d7000000-0000-4000-8000-000000000001";
  private static final String MERCHANT = "d8000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "d9000000-0000-4000-8000-000000000001";
  private static final String OFFER = "da000000-0000-4000-8000-000000000001";
  private static final String RULE = "db000000-0000-4000-8000-000000000001";
  private static final String TARGET = "dc000000-0000-4000-8000-000000000001";
  private static final String LIMIT_POLICY = "dd000000-0000-4000-8000-000000000001";
  private static final String LIMIT_TIER = "de000000-0000-4000-8000-000000000001";

  @org.springframework.beans.factory.annotation.Autowired
  private MerchantCardRecommendationMapper mapper;
  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.update("INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at)"
        + " VALUES (?,?,'추천 사용자','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", USER, "recommend-user");
    jdbc.update("INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at)"
        + " VALUES (?,'R001','추천카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ISSUER);
    jdbc.update("INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at)"
        + " VALUES (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", CARD, ISSUER);
    jdbc.update("INSERT INTO card_content_versions (content_version_id,card_id,content_sha256,name,"
        + "discontinued,first_seen_at,last_seen_at) VALUES (?,?,'dddddddddddddddddddddddddddddddd"
        + "dddddddddddddddddddddddddddddddd','추천 카드',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION, CARD);
    jdbc.update("INSERT INTO codef_account_credentials (codef_account_credential_id,user_id,issuer_id,"
        + "connected_id,credential_identity_hash,status,created_at,updated_at) VALUES (?,?,?,'recommend',"
        + "?,'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", CREDENTIAL, USER, ISSUER,
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
    jdbc.update("INSERT INTO user_cards (user_card_id,user_id,card_id,codef_account_credential_id,"
        + "card_name_from_codef,issuer_id,codef_card_key_hash,created_at,updated_at) VALUES (?,?,?,?,"
        + "'CODEF 추천 카드',?, ?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", USER_CARD, USER, CARD,
        CREDENTIAL, ISSUER, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    jdbc.update("INSERT INTO merchant_categories (merchant_category_id,category_code,category_name,"
        + "display_order,created_at,updated_at) VALUES (?,'MART','마트',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CATEGORY);
    jdbc.update("INSERT INTO merchants (merchant_id,merchant_category_id,name,normalized_name,status,"
        + "created_at,updated_at) VALUES (?,?,'테스트마트','테스트마트','active',UTC_TIMESTAMP(6),"
        + "UTC_TIMESTAMP(6))", MERCHANT, CATEGORY);
    jdbc.update("INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title)"
        + " VALUES (?,?,1,'benefit','마트 적립')", BENEFIT, VERSION);
    jdbc.update("INSERT INTO benefit_offers (offer_id,benefit_id,offer_name,position,reward_type,value_type,"
        + "calculation_mode,calculation_basis,stacking_mode,valuation_scope,valuation_method) VALUES (?,?,"
        + "'마트 적립',1,'points','percentage','flat','transaction_amount','standalone','transaction','direct')",
        OFFER, BENEFIT);
    jdbc.update("INSERT INTO benefit_rules (rule_id,offer_id,position,rule_effect,stacking_mode,reward_value,"
        + "reward_unit) VALUES (?,?,1,'grant','standalone',5,'percent')", RULE, OFFER);
    jdbc.update("INSERT INTO benefit_rule_targets (target_id,rule_id,condition_group,match_mode,target_type,"
        + "merchant_category_id,target_code,target_name) VALUES (?,?,1,'include','merchant_category',?,"
        + "'MART','마트')", TARGET, RULE, CATEGORY);
    jdbc.update("UPDATE benefit_offers SET reference_value_krw = 2 WHERE offer_id = ?", OFFER);
    jdbc.update("INSERT INTO benefit_limit_policies (limit_policy_id,offer_id,policy_name,limit_period,"
        + "limit_type,limit_unit) VALUES (?,?,'마트 포인트 월 한도','monthly','reward_amount','point')",
        LIMIT_POLICY, OFFER);
    jdbc.update("INSERT INTO benefit_limit_tiers (limit_tier_id,limit_policy_id,position,limit_value,"
        + "previous_spend_min_krw) VALUES (?,?,1,1000,NULL)", LIMIT_TIER, LIMIT_POLICY);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM benefit_limit_tiers WHERE limit_tier_id = ?", LIMIT_TIER);
    jdbc.update("DELETE FROM benefit_limit_policies WHERE limit_policy_id = ?", LIMIT_POLICY);
    jdbc.update("DELETE FROM benefit_rule_targets WHERE target_id = ?", TARGET);
    jdbc.update("DELETE FROM benefit_rules WHERE rule_id = ?", RULE);
    jdbc.update("DELETE FROM benefit_offers WHERE offer_id = ?", OFFER);
    jdbc.update("DELETE FROM card_benefits WHERE benefit_id = ?", BENEFIT);
    jdbc.update("DELETE FROM merchants WHERE merchant_id = ?", MERCHANT);
    jdbc.update("DELETE FROM merchant_categories WHERE merchant_category_id = ?", CATEGORY);
    jdbc.update("DELETE FROM user_cards WHERE user_card_id = ?", USER_CARD);
    jdbc.update("DELETE FROM codef_account_credentials WHERE codef_account_credential_id = ?", CREDENTIAL);
    jdbc.update("DELETE FROM card_content_versions WHERE content_version_id = ?", VERSION);
    jdbc.update("DELETE FROM cards WHERE card_id = ?", CARD);
    jdbc.update("DELETE FROM issuers WHERE issuer_id = ?", ISSUER);
    jdbc.update("DELETE FROM users WHERE user_id = ?", USER);
  }

  @Test
  @DisplayName("알 수 없는 가맹점의 배치 추천 조회는 오류 없이 빈 목록을 반환한다")
  void executesBatchQueriesAgainstMySql() {
    List<String> ids = List.of("unknown-merchant");

    assertTrue(mapper.findActiveMerchants(ids).isEmpty());
    assertTrue(mapper.findCategoryLineages(ids).isEmpty());
    assertTrue(mapper.findOwnedCardBenefitRulesForMerchants(
        "unknown-user", ids, LocalDate.of(2026, 8, 13), "2026-07").isEmpty());
  }

  @Test
  @DisplayName("보유 카드 혜택 룰을 record 생성자로 매핑한다")
  void mapsOwnedCardBenefitRuleRow() {
    List<MerchantCardBenefitRuleRow> rows = mapper.findOwnedCardBenefitRules(
        USER, MERCHANT, CATEGORY, "테스트마트", LocalDate.of(2026, 8, 14), "2026-07");

    assertEquals(1, rows.size());
    MerchantCardBenefitRuleRow row = rows.get(0);
    assertEquals(USER_CARD, row.userCardId());
    assertEquals(OFFER, row.offerId());
    assertEquals("마트 적립", row.offerName());
    assertEquals(1, row.benefitTierPosition());
    assertEquals("standalone", row.ruleStackingMode());
    assertEquals("standalone", row.offerStackingMode());
    assertFalse(row.hasSchedule());
    assertFalse(row.hasOptionRequirement());

    List<MerchantCardBenefitRuleRow> batchRows = mapper.findOwnedCardBenefitRulesForMerchants(
        USER, List.of(MERCHANT), LocalDate.of(2026, 8, 14), "2026-07");
    assertEquals(1, batchRows.size());
    assertEquals(RULE, batchRows.get(0).ruleId());
    assertEquals(OFFER, batchRows.get(0).offerId());
  }

  @Test
  @DisplayName("포인트 단위 월 한도를 offer의 환산율로 KRW로 변환해 반환한다")
  void convertsPointLimitToKrw() {
    List<MerchantCardBenefitRuleRow> rows = mapper.findOwnedCardBenefitRules(
        USER, MERCHANT, CATEGORY, "테스트마트", LocalDate.of(2026, 8, 14), "2026-07");

    assertEquals(1, rows.size());
    assertEquals(0, new BigDecimal("2000").compareTo(rows.get(0).monthlyLimitKrw()));

    List<MerchantBenefitTierRow> tiers = mapper.findBenefitTiersForOffers(
        List.of(OFFER), LocalDate.of(2026, 8, 14));

    assertEquals(1, tiers.size());
    assertEquals(0, new BigDecimal("2000").compareTo(tiers.get(0).monthlyLimitKrw()));
    assertNull(tiers.get(0).requiredPreviousSpendKrw());
  }

  @Test
  @DisplayName("이번 달에 적용되지 않는 월별 한도 정책은 제외한다")
  void excludesLimitPolicyNotApplicableToUsageMonth() {
    String decemberOnlyPolicy = "df000000-0000-4000-8000-000000000001";
    String decemberOnlyTier = "e0000000-0000-4000-8000-000000000001";
    jdbc.update("INSERT INTO benefit_limit_policies (limit_policy_id,offer_id,policy_name,limit_period,"
        + "limit_type,limit_unit,applicable_months_json) VALUES (?,?,'12월 전용 한도','monthly',"
        + "'reward_amount','point',JSON_ARRAY(12))", decemberOnlyPolicy, OFFER);
    jdbc.update("INSERT INTO benefit_limit_tiers (limit_tier_id,limit_policy_id,position,limit_value,"
        + "previous_spend_min_krw) VALUES (?,?,1,9999,NULL)", decemberOnlyTier, decemberOnlyPolicy);

    try {
      List<MerchantCardBenefitRuleRow> rows = mapper.findOwnedCardBenefitRules(
          USER, MERCHANT, CATEGORY, "테스트마트", LocalDate.of(2026, 8, 14), "2026-07");
      assertEquals(0, new BigDecimal("2000").compareTo(rows.get(0).monthlyLimitKrw()));

      List<MerchantBenefitTierRow> tiers = mapper.findBenefitTiersForOffers(
          List.of(OFFER), LocalDate.of(2026, 8, 14));
      assertEquals(1, tiers.size());
      assertEquals(0, new BigDecimal("2000").compareTo(tiers.get(0).monthlyLimitKrw()));
    } finally {
      jdbc.update("DELETE FROM benefit_limit_tiers WHERE limit_tier_id = ?", decemberOnlyTier);
      jdbc.update("DELETE FROM benefit_limit_policies WHERE limit_policy_id = ?", decemberOnlyPolicy);
    }
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = MerchantCardRecommendationMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class MapperTestConfig { }
}
