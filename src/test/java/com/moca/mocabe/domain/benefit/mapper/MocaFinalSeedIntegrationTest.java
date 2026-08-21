package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@DisplayName("최종 카드고릴라 seed")
class MocaFinalSeedIntegrationTest {

  @Test
  @DisplayName("첫 실행부터 모든 지원 룰의 target이 있고 재실행해도 일관된다")
  void seedsCurrentSchemaIdempotently() {
    try (MySQLContainer container =
        new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("moca")
            .withUsername("moca")
            .withPassword("moca")
            .withStartupTimeout(Duration.ofMinutes(3))) {
      container.start();
      DataSource dataSource = dataSource(container);
      Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

      ResourceDatabasePopulator populator =
          new ResourceDatabasePopulator(
              new ClassPathResource("db/seed/moca_final_seed.sql"));
      populator.execute(dataSource);

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertSupportedRulesHaveIncludeTarget(jdbc);
      assertZWorkMapBenefitTargets(jdbc);
      assertSolPlanAllMerchantTargets(jdbc);
      assertFlatMerchantTaxonomy(jdbc);
      assertRequiredMerchantMasters(jdbc);
      assertNhPointCardTargets(jdbc);
      assertExplicitMerchantTargetsAreNotBroadened(jdbc);
      assertStructuringStatuses(jdbc);
      assertJsonRuleDefinitions(jdbc);
      assertGeneratedSafeStructuring(jdbc);
      assertGeneratedGoldenCases(jdbc);
      assertMerchantAliases(jdbc);
      assertRootCauseAudit(jdbc);
      assertMerchantCategoryMappings(jdbc);
      assertBenefitTargetForeignKeys(jdbc);
      printStructuringAudit(jdbc);
      int firstRunTargetCount = count(jdbc, "SELECT COUNT(*) FROM benefit_rule_targets");

      populator.execute(dataSource);

      assertEquals(firstRunTargetCount, count(jdbc, "SELECT COUNT(*) FROM benefit_rule_targets"));
      assertEquals(203, count(jdbc, "SELECT COUNT(*) FROM cards WHERE gorilla_card_id IS NOT NULL"));
      assertEquals(
          0,
          count(
              jdbc,
              "SELECT COUNT(*) FROM benefit_rule_targets WHERE "
                  + "(target_type='merchant_category' AND merchant_category_id IS NULL) OR "
                  + "(target_type='merchant' AND merchant_id IS NULL)"));
      assertEquals(
          0,
          count(
              jdbc,
              "SELECT COUNT(*) FROM information_schema.tables "
                  + "WHERE table_schema=DATABASE() AND table_name IN "
                  + "('benefit_medical_scopes','merchant_category_closure')"));
      assertMapVisibilityFlags(jdbc);
      assertMerchantPhysicalLocationFlags(jdbc);
      assertRepresentativeRewardTargets(jdbc);
      assertFlatMerchantTaxonomy(jdbc);
      assertRequiredMerchantMasters(jdbc);
      assertNhPointCardTargets(jdbc);
      assertExplicitMerchantTargetsAreNotBroadened(jdbc);
      assertStructuringStatuses(jdbc);
      assertJsonRuleDefinitions(jdbc);
      assertGeneratedSafeStructuring(jdbc);
      assertGeneratedGoldenCases(jdbc);
      assertMerchantAliases(jdbc);
      assertRootCauseAudit(jdbc);
      assertMerchantCategoryMappings(jdbc);
      assertBenefitTargetForeignKeys(jdbc);
      assertV21PreservesExistingConditionGroup(jdbc, dataSource);

      String ruleId = jdbc.queryForObject("SELECT rule_id FROM benefit_rules LIMIT 1", String.class);
      String categoryId =
          jdbc.queryForObject(
              "SELECT merchant_category_id FROM merchant_categories LIMIT 1", String.class);
      String merchantId = jdbc.queryForObject("SELECT merchant_id FROM merchants LIMIT 1", String.class);
      assertThrows(
          org.springframework.dao.DataAccessException.class,
          () ->
              jdbc.update(
                  "INSERT INTO benefit_rule_targets "
                      + "(target_id, rule_id, condition_group, match_mode, target_type, "
                      + "merchant_category_id, merchant_id, target_code, created_at, updated_at) "
                      + "VALUES ('bad-target', ?, 99, 'include', 'merchant_category', ?, ?, "
                      + "'BAD', NOW(6), NOW(6))",
                  ruleId,
                  categoryId,
                  merchantId));
    }
  }

  private void printStructuringAudit(JdbcTemplate jdbc) {
    System.out.printf(
        "catalog audit: cards=%d benefits=%d offers=%d rules=%d targets=%d%n",
        count(jdbc, "SELECT COUNT(*) FROM cards WHERE gorilla_card_id IS NOT NULL"),
        count(jdbc, "SELECT COUNT(*) FROM card_benefits"),
        count(jdbc, "SELECT COUNT(*) FROM benefit_offers"),
        count(jdbc, "SELECT COUNT(*) FROM benefit_rules"),
        count(jdbc, "SELECT COUNT(*) FROM benefit_rule_targets"));
    jdbc.queryForList(
            "SELECT structuring_status, COUNT(*) count_value FROM card_benefits "
                + "GROUP BY structuring_status ORDER BY structuring_status")
        .forEach(
            row ->
                System.out.printf(
                    "status audit: %s=%s%n",
                    row.get("structuring_status"), row.get("count_value")));
  }

  private void assertGeneratedSafeStructuring(JdbcTemplate jdbc) {
    int generatedRuleCount =
        count(jdbc, "SELECT COUNT(*) FROM benefit_rules WHERE rule_name LIKE '%자동 구조화'");
    int recommendableCardCount =
        count(
            jdbc,
            "SELECT COUNT(DISTINCT card.card_id) FROM cards card "
                + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
                + "INNER JOIN card_benefits benefit "
                + "ON benefit.content_version_id=version.content_version_id "
                + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
                + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
                + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
                + "WHERE target.match_mode='include' AND rule_data.rule_effect='grant' "
                + "AND rule_data.reward_value IS NOT NULL "
                + "AND offer.reward_type IN ('discount','cashback','points','rebate')");
    assertTrue(generatedRuleCount >= 20, "안전 자동 구조화 룰이 충분히 생성되어야 한다");
    assertTrue(recommendableCardCount > 17, "추천 가능 카드 수가 기존 17장보다 증가해야 한다");
    System.out.printf(
        "benefit structuring audit: generatedRules=%d, recommendableCards=%d%n",
        generatedRuleCount,
        recommendableCardCount);
  }

  private void assertMerchantAliases(JdbcTemplate jdbc) {
    assertEquals(
        8,
        count(
            jdbc,
            "SELECT COUNT(*) FROM merchant_aliases WHERE normalized_alias_name IN "
                + "('GS25','지에스25','GS리테일GS25','스타벅스커피','스타벅스코리아',"
                + "'STARBUCKS','씨지브이','CJCGV')"));
    assertEquals(
        3,
        count(
            jdbc,
            "SELECT COUNT(DISTINCT merchant_id) FROM merchant_aliases "
                + "WHERE normalized_alias_name IN "
                + "('GS25','지에스25','GS리테일GS25','스타벅스커피','스타벅스코리아',"
                + "'STARBUCKS','씨지브이','CJCGV')"));
  }

  private void assertMerchantCategoryMappings(JdbcTemplate jdbc) {
    String expected = "SELECT COUNT(*) FROM merchants merchant "
        + "INNER JOIN merchant_categories category "
        + "ON category.merchant_category_id=merchant.merchant_category_id "
        + "WHERE (merchant.normalized_name IN ('무신사') "
        + "AND category.category_code <> 'ONLINE_SHOPPING') OR "
        + "(merchant.normalized_name IN ('CU','GS25','세븐일레븐','이마트24') "
        + "AND category.category_code <> 'CONVENIENCE_STORE') OR "
        + "(merchant.normalized_name IN ('스타벅스','투썸플레이스') "
        + "AND category.category_code <> 'CAFE') OR "
        + "(merchant.normalized_name IN ('CGV','롯데시네마','메가박스') "
        + "AND category.category_code <> 'MOVIE')";
    assertEquals(0, count(jdbc, expected));
    assertEquals(0, count(jdbc,
        "SELECT COUNT(*) FROM merchants WHERE merchant_category_id IS NULL"));
    assertEquals(0, count(jdbc,
        "SELECT COUNT(*) FROM (SELECT normalized_name FROM merchants "
            + "GROUP BY normalized_name HAVING COUNT(*) > 1) duplicate_merchants"));
    assertEquals(0, count(jdbc,
        "SELECT COUNT(*) FROM (SELECT normalized_alias_name FROM merchant_aliases "
            + "GROUP BY normalized_alias_name HAVING COUNT(DISTINCT merchant_id) > 1) "
            + "conflicting_aliases"));
  }

  private void assertBenefitTargetForeignKeys(JdbcTemplate jdbc) {
    assertEquals(0, count(jdbc,
        "SELECT COUNT(*) FROM benefit_rule_targets target "
            + "LEFT JOIN merchant_categories category "
            + "ON category.merchant_category_id=target.merchant_category_id "
            + "LEFT JOIN merchants merchant ON merchant.merchant_id=target.merchant_id "
            + "WHERE (target.target_type='merchant_category' AND "
            + "(target.merchant_category_id IS NULL OR category.merchant_category_id IS NULL "
            + "OR target.merchant_id IS NOT NULL OR target.target_code <> category.category_code)) OR "
            + "(target.target_type='merchant' AND "
            + "(target.merchant_id IS NULL OR merchant.merchant_id IS NULL "
            + "OR target.merchant_category_id IS NOT NULL "
            + "OR target.target_code <> merchant.normalized_name)) OR "
            + "(target.target_type='all_merchants' AND "
            + "(target.merchant_id IS NOT NULL OR target.merchant_category_id IS NOT NULL))"));
  }

  private void assertV21PreservesExistingConditionGroup(JdbcTemplate jdbc, DataSource dataSource) {
    Map<String, Object> source = jdbc.queryForMap(
        "SELECT target.target_id, target.rule_id, target.match_mode, target.target_type, "
            + "target.merchant_category_id, target.merchant_id, target.target_code, "
            + "target.target_name, target.target_source, target.target_authority, "
            + "target.minimum_place_confidence "
            + "FROM benefit_rule_targets target "
            + "INNER JOIN benefit_rules rule_data ON rule_data.rule_id=target.rule_id "
            + "INNER JOIN benefit_offers offer ON offer.offer_id=rule_data.offer_id "
            + "INNER JOIN card_benefits benefit ON benefit.benefit_id=offer.benefit_id "
            + "INNER JOIN card_content_versions version ON version.content_version_id=benefit.content_version_id "
            + "INNER JOIN cards card ON card.card_id=version.card_id "
            + "WHERE card.gorilla_card_id='2680' AND offer.offer_name='편의점 10% 청구 할인' "
            + "LIMIT 1");
    String targetId = "f0000000-0000-4000-8000-000000000021";
    jdbc.update(
        "INSERT INTO benefit_rule_targets (target_id, rule_id, condition_group, match_mode, "
            + "target_type, merchant_category_id, merchant_id, target_code, target_name, "
            + "target_source, target_authority, minimum_place_confidence, created_at, updated_at) "
            + "VALUES (?, ?, 77, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
        targetId, source.get("rule_id"), source.get("match_mode"), source.get("target_type"),
        source.get("merchant_category_id"), source.get("merchant_id"), source.get("target_code"),
        source.get("target_name"), source.get("target_source"), source.get("target_authority"),
        source.get("minimum_place_confidence"));

    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/migration/V21__backfill_appended_card_benefit_targets.sql"));
    populator.execute(dataSource);
    assertEquals(1, count(jdbc,
        "SELECT COUNT(*) FROM benefit_rule_targets WHERE target_id='" + targetId
            + "' AND condition_group=77"));
  }

  private void assertRootCauseAudit(JdbcTemplate jdbc) {
    String auditSql =
        "WITH evidence AS ("
            + "SELECT card.card_id, "
            + "MAX(EXISTS (SELECT 1 FROM benefit_offers offer "
            + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
            + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
            + "WHERE offer.benefit_id=benefit.benefit_id "
            + "AND target.match_mode='include' AND rule_data.rule_effect='grant' "
            + "AND rule_data.reward_value IS NOT NULL "
            + "AND offer.reward_type IN ('discount','cashback','points','rebate'))) ready, "
            + "MAX(CONCAT_WS(' ',benefit.title,benefit.summary,benefit.detail_text) REGEXP "
            + "'편의점|카페|커피|음식점|외식|마트|백화점|영화|병원|약국|학원|주유|"
            + "택시|버스|지하철|대중교통|베이커리|패스트푸드|테마파크|모든가맹점|"
            + "국내가맹점|국내외가맹점') in_scope "
            + "FROM cards card "
            + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
            + "LEFT JOIN card_benefits benefit ON benefit.content_version_id=version.content_version_id "
            + "WHERE card.gorilla_card_id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM card_content_versions newer "
            + "WHERE newer.card_id=version.card_id "
            + "AND (newer.last_seen_at>version.last_seen_at OR "
            + "(newer.last_seen_at=version.last_seen_at "
            + "AND newer.content_version_id>version.content_version_id))) "
            + "GROUP BY card.card_id) "
            + "SELECT COUNT(*) total_cards, SUM(ready=1) ready_cards, "
            + "SUM(ready=0 AND in_scope=0) out_of_scope_cards, "
            + "SUM(ready=0 AND in_scope=1) blocked_cards FROM evidence";
    jdbc.query(
        auditSql,
        result -> {
          int total = result.getInt("total_cards");
          int ready = result.getInt("ready_cards");
          int outOfScope = result.getInt("out_of_scope_cards");
          int blocked = result.getInt("blocked_cards");
          assertEquals(203, total);
          assertTrue(ready > 17, "추천 가능 카드가 기존 기준 17장을 넘어야 한다");
          assertEquals(total, ready + outOfScope + blocked);
          System.out.printf(
              "root cause audit: ready=%d, outOfScope=%d, blocked=%d%n",
              ready,
              outOfScope,
              blocked);
        });
  }

  private void assertGeneratedGoldenCases(JdbcTemplate jdbc) {
    String generatedBenefit =
        "FROM cards card "
            + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
            + "INNER JOIN card_benefits benefit "
            + "ON benefit.content_version_id=version.content_version_id "
            + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
            + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
            + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id ";
    assertEquals(
        1,
        count(
            jdbc,
            "SELECT COUNT(*) > 0 "
                + generatedBenefit
                + "WHERE card.gorilla_card_id='39' AND benefit.title='편의점' "
                + "AND rule_data.reward_value=5 AND rule_data.reward_unit='percent' "
                + "AND target.merchant_category_id=(SELECT merchant_category_id "
                + "FROM merchant_categories WHERE category_code='CONVENIENCE_STORE')"));
    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) "
                + generatedBenefit
                + "WHERE card.gorilla_card_id='2561' AND benefit.title='통신' "
                + "AND target.merchant_category_id=(SELECT merchant_category_id "
                + "FROM merchant_categories WHERE category_code='CONVENIENCE_STORE')"));
  }

  private void assertSupportedRulesHaveIncludeTarget(JdbcTemplate jdbc) {
    assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM benefit_rules rule "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule.offer_id "
        + "WHERE rule.rule_effect='grant' AND rule.reward_value IS NOT NULL "
        + "AND offer.reward_type IN ('discount','cashback','points','rebate') "
        + "AND rule.reward_unit IN ('percent','KRW','point','mile') "
        + "AND NOT EXISTS (SELECT 1 FROM benefit_rule_targets target "
        + "WHERE target.rule_id=rule.rule_id AND target.match_mode='include')"));
  }

  private void assertSolPlanAllMerchantTargets(JdbcTemplate jdbc) {
    assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM cards card "
        + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
        + "INNER JOIN card_benefits benefit "
        + "ON benefit.content_version_id=version.content_version_id "
        + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
        + "INNER JOIN benefit_rules rule ON rule.offer_id=offer.offer_id "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule.rule_id "
        + "WHERE card.gorilla_card_id='2899' "
        + "AND offer.offer_name='국내/외 전가맹점 기본 적립' "
        + "AND target.match_mode='include' AND target.target_type='all_merchants'"));
  }

  private void assertFlatMerchantTaxonomy(JdbcTemplate jdbc) {
    assertEquals(
        0,
        count(jdbc, "SELECT COUNT(*) FROM merchant_categories WHERE parent_id IS NOT NULL"));
    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) FROM (SELECT category_code FROM merchant_categories "
                + "GROUP BY category_code HAVING COUNT(*) > 1) duplicate_category"));
  }

  private void assertRequiredMerchantMasters(JdbcTemplate jdbc) {
    assertEquals(
        32,
        count(
            jdbc,
            "SELECT COUNT(DISTINCT normalized_name) FROM merchants WHERE normalized_name IN ("
                + "'CGV','롯데시네마','메가박스','롯데월드','에버랜드','서울랜드',"
                + "'파리바게뜨','뚜레쥬르','파리크라상','롯데백화점','신세계백화점',"
                + "'현대백화점','이마트','홈플러스','롯데마트','하나로마트','맥도날드',"
                + "'버거킹','롯데리아','KFC','올리브영','스타벅스','투썸플레이스','이디야',"
                + "'메가MGC커피','GS25','CU','세븐일레븐','이마트24','아웃백','VIPS','농협몰')"));
  }

  private void assertNhPointCardTargets(JdbcTemplate jdbc) {
    String nhPointRules =
        "FROM cards card "
            + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
            + "INNER JOIN card_benefits benefit "
            + "ON benefit.content_version_id=version.content_version_id "
            + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
            + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
            + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
            + "WHERE card.gorilla_card_id='360' ";
    assertEquals(
        1,
        count(
            jdbc,
            "SELECT COUNT(*) > 0 "
                + nhPointRules
                + "AND offer.offer_name='전 가맹점 기본적립 0.2%' "
                + "AND target.target_type='all_merchants'"));
    assertEquals(
        1,
        count(
            jdbc,
            "SELECT COUNT(*) > 0 "
                + nhPointRules
                + "AND offer.offer_name='생활 영역 추가적립 0.3%' "
                + "AND target.target_type='merchant' "
                + "AND target.merchant_id=(SELECT merchant_id FROM merchants "
                + "WHERE normalized_name='CU' LIMIT 1)"));
    assertEquals(
        1,
        count(
            jdbc,
            "SELECT COUNT(*) FROM card_benefits benefit "
                + "INNER JOIN card_content_versions version "
                + "ON version.content_version_id=benefit.content_version_id "
                + "INNER JOIN cards card ON card.card_id=version.card_id "
                + "WHERE card.gorilla_card_id='360' AND benefit.title='모든가맹점' "
                + "AND benefit.structuring_status='PARTIAL'"));
  }

  private void assertExplicitMerchantTargetsAreNotBroadened(JdbcTemplate jdbc) {
    String teenUpTargets =
        "FROM cards card "
            + "INNER JOIN card_content_versions version ON version.card_id=card.card_id "
            + "INNER JOIN card_benefits benefit "
            + "ON benefit.content_version_id=version.content_version_id "
            + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
            + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
            + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
            + "WHERE card.gorilla_card_id='2852' AND benefit.title='편의점' ";
    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) "
                + teenUpTargets
                + "AND target.target_type='merchant_category'"));
    assertEquals(
        2,
        count(
            jdbc,
            "SELECT COUNT(DISTINCT target.merchant_id) "
                + teenUpTargets
                + "AND target.target_type='merchant' "
                + "AND target.merchant_id IN (SELECT merchant_id FROM merchants "
                + "WHERE normalized_name IN ('CU','세븐일레븐'))"));
  }

  private void assertStructuringStatuses(JdbcTemplate jdbc) {
    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) FROM card_benefits WHERE structuring_status NOT IN ("
                + "'RAW','PARTIAL','STRUCTURED','UNSUPPORTED','PARSE_FAILED',"
                + "'NON_MONETARY','EXCLUDED')"));
    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) FROM card_benefits benefit "
                + "WHERE benefit.structuring_status='STRUCTURED' "
                + "AND NOT EXISTS (SELECT 1 FROM benefit_offers offer "
                + "INNER JOIN benefit_rules rule_data ON rule_data.offer_id=offer.offer_id "
                + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule_data.rule_id "
                + "WHERE offer.benefit_id=benefit.benefit_id "
                + "AND target.match_mode='include')"));
  }

  private void assertRepresentativeRewardTargets(JdbcTemplate jdbc) {
    assertEquals(1, count(jdbc, "SELECT COUNT(*) > 0 FROM benefit_rules rule "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule.offer_id "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule.rule_id "
        + "WHERE offer.reward_type='discount' AND (target.merchant_category_id IS NOT NULL "
        + "OR target.merchant_id IS NOT NULL OR target.target_type='all_merchants')"));
    assertEquals(1, count(jdbc, "SELECT COUNT(*) > 0 FROM benefit_rules rule "
        + "INNER JOIN benefit_offers offer ON offer.offer_id=rule.offer_id "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule.rule_id "
        + "WHERE offer.reward_type='cashback' AND (target.merchant_category_id IS NOT NULL "
        + "OR target.merchant_id IS NOT NULL OR target.target_type='all_merchants')"));
    assertEquals(1, count(jdbc, "SELECT COUNT(*) > 0 FROM benefit_rules rule "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule.rule_id "
        + "WHERE rule.reward_unit='point' AND (target.merchant_category_id IS NOT NULL "
        + "OR target.merchant_id IS NOT NULL OR target.target_type='all_merchants')"));
  }

  private void assertJsonRuleDefinitions(JdbcTemplate jdbc) {
    assertEquals(
        4,
        count(
            jdbc,
            "SELECT COUNT(*) FROM benefit_rules rule "
                + "INNER JOIN benefit_offers offer ON offer.offer_id=rule.offer_id "
                + "INNER JOIN card_benefits benefit ON benefit.benefit_id=offer.benefit_id "
                + "INNER JOIN card_content_versions version "
                + "ON version.content_version_id=benefit.content_version_id "
                + "INNER JOIN cards card ON card.card_id=version.card_id "
                + "WHERE card.gorilla_card_id='2680' "
                + "AND rule.rule_support_status='SUPPORTED' "
                + "AND rule.rule_schema_version=1 AND JSON_VALID(rule.rule_definition_json)"));
    assertEquals(
        2,
        count(
            jdbc,
            "SELECT COUNT(*) FROM benefit_rules rule "
                + "INNER JOIN benefit_offers offer ON offer.offer_id=rule.offer_id "
                + "INNER JOIN card_benefits benefit ON benefit.benefit_id=offer.benefit_id "
                + "INNER JOIN card_content_versions version "
                + "ON version.content_version_id=benefit.content_version_id "
                + "INNER JOIN cards card ON card.card_id=version.card_id "
                + "WHERE card.gorilla_card_id='2899' "
                + "AND offer.offer_name='국내/외 전가맹점 기본 적립' "
                + "AND rule.rule_support_status='PARTIAL' "
                + "AND rule.rule_schema_version=1 AND JSON_VALID(rule.rule_definition_json)"));
  }

  private void assertZWorkMapBenefitTargets(JdbcTemplate jdbc) {
    String zWorkBenefits = "FROM card_content_versions version "
        + "INNER JOIN card_benefits benefit ON benefit.content_version_id=version.content_version_id "
        + "INNER JOIN benefit_offers offer ON offer.benefit_id=benefit.benefit_id "
        + "INNER JOIN benefit_rules rule ON rule.offer_id=offer.offer_id "
        + "INNER JOIN benefit_rule_targets target ON target.rule_id=rule.rule_id "
        + "WHERE version.name='현대카드Z work Edition2' ";
    assertEquals(1, count(jdbc, "SELECT COUNT(*) > 0 " + zWorkBenefits
        + "AND benefit.title='편의점 10% 청구 할인' "
        + "AND target.target_type='merchant_category' "
        + "AND target.merchant_category_id=(SELECT merchant_category_id FROM merchant_categories "
        + "WHERE category_code='CONVENIENCE_STORE')"));
    assertEquals(1, count(jdbc, "SELECT COUNT(*) > 0 " + zWorkBenefits
        + "AND benefit.title='커피전문점 10% 청구 할인' "
        + "AND target.target_type='merchant_category' "
        + "AND target.merchant_category_id=(SELECT merchant_category_id FROM merchant_categories "
        + "WHERE category_code='CAFE')"));
    assertEquals(2, count(jdbc, "SELECT COUNT(*) " + zWorkBenefits
        + "AND benefit.title='도서 10% 청구 할인' AND target.target_type='merchant' "
        + "AND target.merchant_id IN (SELECT merchant_id FROM merchants "
        + "WHERE normalized_name IN ('교보문고','YES24'))"));
    assertEquals(7, count(jdbc, "SELECT COUNT(DISTINCT target.condition_group) " + zWorkBenefits
        + "AND benefit.title='온라인 쇼핑몰 10% 청구 할인' "
        + "AND target.target_type='merchant'"));
    assertEquals(2, count(jdbc, "SELECT COUNT(DISTINCT target.condition_group) " + zWorkBenefits
        + "AND benefit.title='도서 10% 청구 할인' AND target.target_type='merchant'"));
  }

  private DataSource dataSource(MySQLContainer container) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    dataSource.setUrl(container.getJdbcUrl());
    dataSource.setUsername(container.getUsername());
    dataSource.setPassword(container.getPassword());
    return dataSource;
  }

  private void assertMapVisibilityFlags(JdbcTemplate jdbc) {
    String visibleCategories =
        "'LARGE_MART','CONVENIENCE_STORE','PRESCHOOL','SCHOOL','ACADEMY',"
            + "'PARKING','FUEL','EV_CHARGING','PUBLIC_TRANSIT','BUS','SUBWAY','TAXI',"
            + "'CULTURE','PERFORMANCE_EXHIBITION','ACCOMMODATION','RESTAURANT','CAFE',"
            + "'HOSPITAL','GENERAL_HOSPITAL','CLINIC','DENTAL','ORIENTAL_MEDICINE',"
            + "'VETERINARY','NURSING_HOSPITAL','PUBLIC_HEALTH_CENTER','DERMATOLOGY',"
            + "'PLASTIC_SURGERY','POSTPARTUM_CARE_CENTER','PHARMACY','BEAUTY','DRUGSTORE',"
            + "'DEPARTMENT_STORE','MOVIE','BAKERY','THEME_PARK','GOLF','AUTO_MAINTENANCE'";
    String hiddenCategories =
        "'EDUCATION','TRANSPORTATION','LEISURE','MART','TRADITIONAL_MARKET','SHOPPING',"
            + "'DUTY_FREE','ONLINE_SHOPPING','PET','TRAVEL','OTT','BOOKS','MUSIC',"
            + "'AUTOMOTIVE','TELECOM','FAMILY_RESTAURANT','FAST_FOOD','DELIVERY',"
            + "'UTILITY_BILL','INSURANCE','AIRLINE','BANK','EXPRESS_BUS','TRAIN',"
            + "'HIGHWAY_TOLL','FITNESS','HOTEL','RENTAL_CAR','ONLINE_TRAVEL_AGENCY',"
            + "'GAME','AIRPORT'";

    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) FROM merchant_categories WHERE "
                + "(category_code IN ("
                + visibleCategories
                + ") AND is_map_visible=FALSE) OR "
                + "(category_code IN ("
                + hiddenCategories
                + ") AND is_map_visible=TRUE)"));
  }

  private void assertMerchantPhysicalLocationFlags(JdbcTemplate jdbc) {
    String physicalMerchants =
        "'GS25','CU','세븐일레븐','이마트24','스타벅스','투썸플레이스','커피빈',"
            + "'폴바셋','교보문고','SK에너지','GS칼텍스'";
    String nonPhysicalMerchants =
        "'네이버쇼핑','쿠팡','G마켓','옥션','11번가','SSG.COM','컬리','YES24',"
            + "'무신사','솔드아웃','29CM','땡겨요','배달의민족','요기요','쿠팡이츠',"
            + "'넷플릭스','유튜브 프리미엄','티빙','디즈니플러스',"
            + "'네이버플러스 멤버십','쿠팡 와우 멤버십'";

    assertEquals(
        0,
        count(
            jdbc,
            "SELECT COUNT(*) FROM merchants WHERE "
                + "(normalized_name IN ("
                + physicalMerchants
                + ") AND has_physical_location=FALSE) OR "
                + "(normalized_name IN ("
                + nonPhysicalMerchants
                + ") AND has_physical_location=TRUE)"));
  }

  private Integer count(JdbcTemplate jdbc, String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }
}
