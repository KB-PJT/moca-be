package com.moca.mocabe.domain.merchant.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.time.LocalDate;
import java.util.List;
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

  @org.springframework.beans.factory.annotation.Autowired
  private MerchantCardRecommendationMapper mapper;

  @Test
  @DisplayName("알 수 없는 가맹점의 배치 추천 조회는 오류 없이 빈 목록을 반환한다")
  void executesBatchQueriesAgainstMySql() {
    List<String> ids = List.of("unknown-merchant");

    assertTrue(mapper.findActiveMerchants(ids).isEmpty());
    assertTrue(mapper.findCategoryLineages(ids).isEmpty());
    assertTrue(mapper.findOwnedCardBenefitRulesForMerchants(
        "unknown-user", ids, LocalDate.of(2026, 8, 13), "2026-07").isEmpty());
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = MerchantCardRecommendationMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class MapperTestConfig { }
}
