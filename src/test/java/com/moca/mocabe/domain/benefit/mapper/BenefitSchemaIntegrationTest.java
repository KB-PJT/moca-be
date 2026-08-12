package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@SpringJUnitConfig(BenefitSchemaIntegrationTest.BenefitSchemaTestConfig.class)
class BenefitSchemaIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Flyway가 혜택 계산 테이블과 콘텐츠 버전 FK를 생성한다")
  void createsBenefitCalculationSchema() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = TRUE",
            Integer.class);
    Integer tableCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND"
                + " table_name IN ('reward_programs', 'reward_conversion_policies',"
                + " 'card_benefits', 'card_performance_tiers', 'benefit_offers', 'benefit_rules',"
                + " 'benefit_rule_targets', 'benefit_rule_schedules', 'benefit_limit_policies',"
                + " 'benefit_limit_tiers', 'benefit_offer_option_requirements', 'payment_methods',"
                + " 'payment_method_aliases', 'card_spend_rules', 'user_benefit_usages', "
                + "'user_benefit_calculation_outcomes')",
            Integer.class);
    Integer contentVersionForeignKeyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage "
                + "WHERE table_schema = DATABASE() AND table_name = 'card_benefits' "
                + "AND column_name = 'content_version_id' "
                + "AND referenced_table_name = 'card_content_versions'",
            Integer.class);
    Integer performanceTierForeignKeyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage "
                + "WHERE table_schema = DATABASE() AND table_name = 'card_performance_tiers' "
                + "AND column_name = 'content_version_id' "
                + "AND referenced_table_name = 'card_content_versions'",
            Integer.class);
    Integer legacyParseColumnCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name IN ('card_annual_fee_options', 'card_option_groups', "
                + "'card_option_choices') "
                + "AND column_name IN ('parse_status', 'parse_confidence', 'source_fragment')",
            Integer.class);

    assertEquals(1, migrationCount);
    assertEquals(16, tableCount);
    assertEquals(1, contentVersionForeignKeyCount);
    assertEquals(1, performanceTierForeignKeyCount);
    assertEquals(0, legacyParseColumnCount);
  }

  @Test
  @DisplayName("혜택 target FK와 Kakao 계산 정책 스키마를 생성한다")
  void createsStrictMerchantTargetAndKakaoResolutionSchema() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version IN ('15', '16', '17', '18') AND success = TRUE",
            Integer.class);
    Integer targetForeignKeys =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage "
                + "WHERE table_schema = DATABASE() AND table_name = 'benefit_rule_targets' "
                + "AND column_name IN ('merchant_category_id', 'merchant_id') "
                + "AND referenced_table_name IS NOT NULL",
            Integer.class);
    Integer kakaoRegistryRows =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kakao_category_group_registry", Integer.class);
    Integer medicalCategoryRows =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM merchant_categories WHERE category_code IN "
                + "('HOSPITAL', 'GENERAL_HOSPITAL', 'CLINIC', 'DENTAL', "
                + "'ORIENTAL_MEDICINE', 'VETERINARY', 'NURSING_HOSPITAL', "
                + "'PUBLIC_HEALTH_CENTER', 'DERMATOLOGY', 'PLASTIC_SURGERY', "
                + "'POSTPARTUM_CARE_CENTER', 'PHARMACY')",
            Integer.class);

    assertEquals(4, migrationCount);
    assertEquals(2, targetForeignKeys);
    assertEquals(18, kakaoRegistryRows);
    assertEquals(12, medicalCategoryRows);
  }

  @Test
  @DisplayName("unsupported Kakao 그룹의 ALLOW 저장을 거부한다")
  void rejectsUnsupportedKakaoPolicy() {
    jdbcTemplate.update(
        "INSERT INTO merchant_categories "
            + "(merchant_category_id, category_code, category_name, display_order, "
            + "is_map_visible, created_at, updated_at) "
            + "VALUES ('red-category', 'RED_CATEGORY', '레드팀', 999, FALSE, NOW(6), NOW(6))");
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO kakao_category_maps "
                    + "(kakao_category_map_id, merchant_category_id, kakao_category_group_code, "
                    + "kakao_category_name_pattern, match_method, confidence_score, "
                    + "benefit_match_policy, priority, enabled, created_at, updated_at) "
                    + "VALUES ('bad-kakao-map', 'red-category', 'AG2', '', 'GROUP_CODE', "
                    + "1.000, 'ALLOW', 1, TRUE, NOW(6), NOW(6))"));
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  static class BenefitSchemaTestConfig { }
}
