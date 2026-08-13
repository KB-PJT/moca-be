package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
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
  @DisplayName("현재 Flyway 스키마에 두 번 실행해도 FK target, 카드, 지도 노출 설정이 일관된다")
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
              new ClassPathResource("db/migration/moca_final_seed.sql"));
      populator.execute(dataSource);
      populator.execute(dataSource);

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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
