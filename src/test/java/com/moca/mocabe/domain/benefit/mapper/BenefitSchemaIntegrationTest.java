package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;

@Tag("integration")
@SpringJUnitConfig(BenefitSchemaIntegrationTest.BenefitSchemaTestConfig.class)
class BenefitSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway가 혜택 계산 테이블과 콘텐츠 버전 FK를 생성한다")
    void createsBenefitCalculationSchema() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = TRUE",
                Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN ("
                        + "'reward_programs', 'reward_conversion_policies', 'card_benefits', 'card_performance_tiers', "
                        + "'benefit_offers', 'benefit_rules', 'benefit_rule_targets', "
                        + "'benefit_rule_schedules', 'benefit_limit_policies', 'benefit_limit_tiers', "
                        + "'benefit_offer_option_requirements', 'payment_methods', "
                        + "'payment_method_aliases', 'card_spend_rules', 'user_benefit_usages', "
                        + "'user_benefit_calculation_outcomes')",
                Integer.class);
        Integer contentVersionForeignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'card_benefits' "
                        + "AND column_name = 'content_version_id' "
                        + "AND referenced_table_name = 'card_content_versions'",
                Integer.class);
        Integer performanceTierForeignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'card_performance_tiers' "
                        + "AND column_name = 'content_version_id' "
                        + "AND referenced_table_name = 'card_content_versions'",
                Integer.class);
        Integer legacyParseColumnCount = jdbcTemplate.queryForObject(
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

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    static class BenefitSchemaTestConfig {
    }
}
