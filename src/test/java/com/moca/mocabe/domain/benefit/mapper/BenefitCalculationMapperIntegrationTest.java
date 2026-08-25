package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.benefit.model.BenefitUsageCounts;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@SpringJUnitConfig(BenefitCalculationMapperIntegrationTest.Config.class)
class BenefitCalculationMapperIntegrationTest {
  private static final String USER = "91000000-0000-4000-8000-000000000001";
  private static final String ISSUER = "92000000-0000-4000-8000-000000000001";
  private static final String CARD = "93000000-0000-4000-8000-000000000001";
  private static final String VERSION = "94000000-0000-4000-8000-000000000001";
  private static final String CREDENTIAL = "95000000-0000-4000-8000-000000000001";
  private static final String USER_CARD = "96000000-0000-4000-8000-000000000001";
  private static final String CATEGORY = "97000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "98000000-0000-4000-8000-000000000001";
  private static final String OFFER = "99000000-0000-4000-8000-000000000001";
  private static final String RULE = "9a000000-0000-4000-8000-000000000001";
  private static final String TARGET = "9b000000-0000-4000-8000-000000000001";

  @Autowired private BenefitCalculationMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) "
            + "VALUES (?,?,'계산 사용자','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER,
        "calculation-user");
    jdbc.update(
        "INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at) "
            + "VALUES (?,'C901','계산카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        ISSUER);
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) "
            + "VALUES (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CARD,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions (content_version_id,card_id,content_sha256,name,"
            + "discontinued,first_seen_at,last_seen_at) VALUES (?,?,'99999999999999999999999999999999"
            + "99999999999999999999999999999999','계산 카드',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION,
        CARD);
    jdbc.update(
        "INSERT INTO codef_account_credentials (codef_account_credential_id,user_id,issuer_id,"
            + "connected_id,credential_identity_hash,status,created_at,updated_at) VALUES (?,?,?,"
            + "'calculation',?,'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CREDENTIAL,
        USER,
        ISSUER,
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
    jdbc.update(
        "INSERT INTO user_cards (user_card_id,user_id,card_id,codef_account_credential_id,"
            + "card_name_from_codef,issuer_id,codef_card_key_hash,created_at,updated_at) VALUES "
            + "(?,?,?,?,'계산 카드',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD,
        USER,
        CARD,
        CREDENTIAL,
        ISSUER,
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb1");
    jdbc.update(
        "INSERT INTO merchant_categories (merchant_category_id,category_code,category_name,"
            + "display_order,created_at,updated_at) VALUES (?,'CALC_CAFE','계산 카페',901,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CATEGORY);
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title) "
            + "VALUES (?,?,1,'benefit','계산 카페 할인')",
        BENEFIT,
        VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers (offer_id,benefit_id,offer_name,position,reward_type,value_type,"
            + "calculation_mode,calculation_basis,stacking_mode,valuation_scope,valuation_method) "
            + "VALUES (?,?,'계산 카페 할인',1,'discount','percentage','flat','transaction_amount',"
            + "'standalone','transaction','direct')",
        OFFER,
        BENEFIT);
    jdbc.update(
        "INSERT INTO benefit_rules (rule_id,offer_id,position,rule_effect,stacking_mode,reward_value,"
            + "reward_unit,previous_spend_min_krw,transaction_max_krw,rule_schema_version,"
            + "rule_support_status,rule_definition_json) VALUES (?,?,1,'grant','additive',10,"
            + "'percent',300000,50000,1,'SUPPORTED',CAST(? AS JSON))",
        RULE,
        OFFER,
        jsonRule());
    jdbc.update(
        "INSERT INTO benefit_rule_targets (target_id,rule_id,condition_group,match_mode,target_type,"
            + "merchant_category_id,target_code,target_name) VALUES (?,?,1,'include',"
            + "'merchant_category',?,'CALC_CAFE','계산 카페')",
        TARGET,
        RULE,
        CATEGORY);
    insertUsage("9c000000-0000-4000-8000-000000000001", "2026-08-14", "confirmed", 1);
    insertUsage("9c000000-0000-4000-8000-000000000002", "2026-08-13", "confirmed", 2);
    insertUsage("9c000000-0000-4000-8000-000000000003", "2026-08-14", "reversed", 5);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM user_benefit_usages WHERE user_card_id=?", USER_CARD);
    jdbc.update("DELETE FROM benefit_rule_targets WHERE target_id=?", TARGET);
    jdbc.update("DELETE FROM benefit_rules WHERE rule_id=?", RULE);
    jdbc.update("DELETE FROM benefit_offers WHERE offer_id=?", OFFER);
    jdbc.update("DELETE FROM card_benefits WHERE benefit_id=?", BENEFIT);
    jdbc.update("DELETE FROM merchant_categories WHERE merchant_category_id=?", CATEGORY);
    jdbc.update("DELETE FROM user_cards WHERE user_card_id=?", USER_CARD);
    jdbc.update("DELETE FROM codef_account_credentials WHERE codef_account_credential_id=?", CREDENTIAL);
    jdbc.update("DELETE FROM card_content_versions WHERE content_version_id=?", VERSION);
    jdbc.update("DELETE FROM cards WHERE card_id=?", CARD);
    jdbc.update("DELETE FROM issuers WHERE issuer_id=?", ISSUER);
    jdbc.update("DELETE FROM users WHERE user_id=?", USER);
  }

  @Test
  @DisplayName("JSON 룰과 거래 상한을 record 생성자로 매핑한다")
  void mapsJsonRuleDefinition() throws Exception {
    List<SimpleBenefitRuleRow> rows =
        mapper.findSimpleRulesForUserCard(USER_CARD, LocalDate.of(2026, 8, 14));

    assertEquals(1, rows.size());
    SimpleBenefitRuleRow row = rows.get(0);
    assertEquals(RULE, row.ruleId());
    assertEquals("SUPPORTED", row.ruleSupportStatus());
    assertEquals(0, new java.math.BigDecimal("50000").compareTo(row.transactionMaxKrw()));
    assertEquals(1, new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(row.ruleDefinitionJson()).get("schemaVersion").asInt());
  }

  @Test
  @DisplayName("확정 사용 원장에서 일·월 횟수만 합산한다")
  void sumsConfirmedDailyAndMonthlyUsageCounts() {
    BenefitUsageCounts counts =
        mapper.findConfirmedUsageCounts(
            USER_CARD,
            OFFER,
            LocalDate.of(2026, 8, 14),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 9, 1));

    assertEquals(1, counts.dailyCount());
    assertEquals(3, counts.monthlyCount());
  }

  private void insertUsage(String usageId, String usageDate, String status, int count) {
    jdbc.update(
        "INSERT INTO user_benefit_usages (usage_id,user_card_id,offer_id,rule_id,usage_date,"
            + "eligible_amount_krw,reward_amount_krw,usage_count,usage_status,created_at,updated_at) "
            + "VALUES (?,?,?,?,?,10000,1000,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        usageId,
        USER_CARD,
        OFFER,
        RULE,
        usageDate,
        count,
        status);
  }

  private String jsonRule() {
    return """
        {
          "schemaVersion":1,
          "conditions":{"all":[],"any":[],"none":[]},
          "reward":{
            "benefitType":"DISCOUNT",
            "rewardUnit":"KRW",
            "calculation":"RATE",
            "rate":"0.1"
          },
          "limits":[]
        }
        """;
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = BenefitCalculationMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class Config { }
}
