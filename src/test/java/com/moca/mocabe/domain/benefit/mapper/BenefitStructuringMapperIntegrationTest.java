package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import com.moca.mocabe.domain.benefit.service.BenefitStructuringBatchService;
import com.moca.mocabe.domain.benefit.service.BenefitStructuringPersistenceService;
import com.moca.mocabe.domain.benefit.structuring.ParsedTarget;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Tag("integration")
@SpringJUnitConfig(BenefitStructuringMapperIntegrationTest.Config.class)
@DisplayName("혜택 자동 구조화 저장")
class BenefitStructuringMapperIntegrationTest {
  private static final String ISSUER = "81000000-0000-4000-8000-000000000001";
  private static final String CARD = "82000000-0000-4000-8000-000000000001";
  private static final String VERSION = "83000000-0000-4000-8000-000000000001";
  private static final String CATEGORY = "84000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "85000000-0000-4000-8000-000000000001";
  private static final String OFFER = "86000000-0000-4000-8000-000000000001";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private BenefitStructuringBatchService batchService;
  @Autowired private BenefitStructuringPersistenceService persistenceService;

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at) "
            + "VALUES (?,'S901','구조화카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        ISSUER);
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) "
            + "VALUES (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CARD,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions (content_version_id,card_id,content_sha256,name,"
            + "discontinued,first_seen_at,last_seen_at) VALUES (?,?,REPEAT('8',64),'구조화 카드',"
            + "FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION,
        CARD);
    jdbc.update(
        "INSERT INTO merchant_categories (merchant_category_id,category_code,category_name,"
            + "display_order,is_map_visible,created_at,updated_at) "
            + "VALUES (?,'CAFE','카페',991,TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CATEGORY);
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,"
            + "structuring_status,title,detail_text) VALUES (?,?,1,'benefit','PARSE_FAILED',"
            + "'카페 할인','카페 10% 할인 월 최대 1만원')",
        BENEFIT,
        VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers (offer_id,benefit_id,offer_name,position,reward_type,"
            + "value_type,calculation_mode,calculation_basis,stacking_mode,valuation_scope,"
            + "valuation_method) VALUES (?,?,'카페 할인',1,'other','other','other','other',"
            + "'standalone','non_monetary','not_valued')",
        OFFER,
        BENEFIT);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM benefit_limit_tiers WHERE limit_policy_id IN "
        + "(SELECT limit_policy_id FROM benefit_limit_policies WHERE offer_id=?)", OFFER);
    jdbc.update("DELETE FROM benefit_limit_policies WHERE offer_id=?", OFFER);
    jdbc.update("DELETE FROM benefit_rule_targets WHERE rule_id IN "
        + "(SELECT rule_id FROM benefit_rules WHERE offer_id=?)", OFFER);
    jdbc.update("DELETE FROM benefit_rules WHERE offer_id=?", OFFER);
    jdbc.update("DELETE FROM benefit_offers WHERE offer_id=?", OFFER);
    jdbc.update("DELETE FROM card_benefits WHERE benefit_id=?", BENEFIT);
    jdbc.update("DELETE FROM merchant_categories WHERE merchant_category_id=?", CATEGORY);
    jdbc.update("DELETE FROM card_content_versions WHERE content_version_id=?", VERSION);
    jdbc.update("DELETE FROM cards WHERE card_id=?", CARD);
    jdbc.update("DELETE FROM issuers WHERE issuer_id=?", ISSUER);
  }

  @Test
  @DisplayName("rule, category target, 월 한도와 구조화 상태를 원자적으로 저장한다")
  void persistsCompleteStructuredBenefit() {
    assertEquals(1, batchService.persistReadyCandidates());

    assertEquals(
        "STRUCTURED",
        value("SELECT structuring_status FROM card_benefits WHERE benefit_id=?", BENEFIT));
    assertEquals(1, count("SELECT COUNT(*) FROM benefit_rules WHERE offer_id=?"));
    assertEquals(1, count("SELECT COUNT(*) FROM benefit_rule_targets target "
        + "INNER JOIN benefit_rules rule_data ON rule_data.rule_id=target.rule_id "
        + "WHERE rule_data.offer_id=? AND target.merchant_category_id IS NOT NULL"));
    assertEquals(1, count("SELECT COUNT(*) FROM benefit_limit_policies WHERE offer_id=? "
        + "AND limit_period='monthly' AND limit_type='reward_amount' AND limit_unit='KRW'"));
    assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject(
        "SELECT tier.limit_value FROM benefit_limit_tiers tier "
            + "INNER JOIN benefit_limit_policies policy "
            + "ON policy.limit_policy_id=tier.limit_policy_id WHERE policy.offer_id=?",
        BigDecimal.class,
        OFFER)));
  }

  @Test
  @DisplayName("대상 FK를 찾지 못하면 offer 변경과 rule 생성을 모두 롤백한다")
  void rollsBackWhenTargetCannotBePersisted() {
    StructuredBenefitWrite write = new StructuredBenefitWrite(
        "87000000-0000-4000-8000-000000000001", OFFER, BENEFIT, "실패 룰",
        "discount", "percentage", BigDecimal.TEN, "percent", null, null, null,
        "{\"schemaVersion\":1}", "UNKNOWN", null, null);

    assertThrows(IllegalStateException.class, () -> persistenceService.persist(
        write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "UNKNOWN")));

    assertEquals("other", value("SELECT reward_type FROM benefit_offers WHERE offer_id=?", OFFER));
    assertEquals(0, count("SELECT COUNT(*) FROM benefit_rules WHERE offer_id=?"));
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class, OFFER);
  }

  private String value(String sql, String id) {
    return jdbc.queryForObject(sql, String.class, id);
  }

  @Configuration
  @EnableTransactionManagement
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = BenefitStructuringMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class Config {
    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    BenefitStructuringPersistenceService persistenceService(BenefitStructuringMapper mapper) {
      return new BenefitStructuringPersistenceService(mapper);
    }

    @Bean
    BenefitStructuringBatchService batchService(
        BenefitStructuringMapper mapper, BenefitStructuringPersistenceService persistenceService) {
      return new BenefitStructuringBatchService(mapper, persistenceService);
    }
  }
}
