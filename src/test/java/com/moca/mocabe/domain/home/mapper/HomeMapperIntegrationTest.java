package com.moca.mocabe.domain.home.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 홈 집계 SQL이 실제 MySQL 8 스키마에서 실행되는지 검증한다. */
@Tag("integration")
@org.springframework.test.context.junit.jupiter.SpringJUnitConfig(
    HomeMapperIntegrationTest.HomeMapperTestConfig.class)
class HomeMapperIntegrationTest {

  private static final String UNKNOWN_USER_ID = "00000000-0000-4000-8000-000000000001";

  @org.springframework.beans.factory.annotation.Autowired private HomeMapper homeMapper;

  @Test
  @DisplayName("홈 카드와 최근 전체 내역 SQL은 데이터가 없는 사용자에게 빈 목록을 반환한다")
  void executesHomeQueriesAgainstMySql() {
    assertTrue(homeMapper.findHomeCards(UNKNOWN_USER_ID, "2026-07").isEmpty());
    assertTrue(
        homeMapper
            .findRecentHistory(
                UNKNOWN_USER_ID,
                LocalDateTime.of(2026, 6, 30, 15, 0),
                LocalDateTime.of(2026, 7, 31, 15, 0),
                5)
            .isEmpty());
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = HomeMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class HomeMapperTestConfig { }
}
