package com.moca.mocabe.domain.benefit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistorySummaryRow;
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

/** 혜택 이력 SQL의 실제 MySQL 조인·소유권·정렬을 검증한다. */
@Tag("integration")
@SpringJUnitConfig(BenefitHistoryMapperIntegrationTest.Config.class)
class BenefitHistoryMapperIntegrationTest {

  private static final String USER = "10000000-0000-4000-8000-000000000001";
  private static final String OTHER_USER = "10000000-0000-4000-8000-000000000002";
  private static final String ISSUER = "20000000-0000-4000-8000-000000000001";
  private static final String CARD = "30000000-0000-4000-8000-000000000001";
  private static final String VERSION = "40000000-0000-4000-8000-000000000001";
  private static final String CREDENTIAL = "50000000-0000-4000-8000-000000000001";
  private static final String USER_CARD = "60000000-0000-4000-8000-000000000001";
  private static final String BENEFIT = "70000000-0000-4000-8000-000000000001";
  private static final String OFFER = "80000000-0000-4000-8000-000000000001";
  private static final String APPROVAL = "90000000-0000-4000-8000-000000000001";
  private static final String USAGE = "a0000000-0000-4000-8000-000000000001";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private BenefitHistoryMapper mapper;

  @BeforeEach
  void setUp() {
    clean();
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) VALUES"
            + " (?,?,'모카','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER,
        "history-user");
    jdbc.update(
        "INSERT INTO users (user_id,google_subject,nickname,user_type,created_at,updated_at) VALUES"
            + " (?,?,'다른 사용자','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        OTHER_USER,
        "history-other");
    jdbc.update(
        "INSERT INTO issuers (issuer_id,institution_code,issuer_name,created_at,updated_at) VALUES"
            + " (?,'H001','테스트카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        ISSUER);
    jdbc.update(
        "INSERT INTO cards (card_id,issuer_id,card_type,first_seen_at,last_seen_at) VALUES"
            + " (?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CARD,
        ISSUER);
    jdbc.update(
        "INSERT INTO card_content_versions"
            + " (content_version_id,card_id,content_sha256,name,discontinued,first_seen_at,last_seen_at)"
            + " VALUES (?,?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','테스트"
            + " 카드',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        VERSION,
        CARD);
    jdbc.update(
        "INSERT INTO codef_account_credentials"
            + " (codef_account_credential_id,user_id,issuer_id,connected_id,credential_identity_hash,"
            + "status,created_at,updated_at) VALUES"
            + " (?,?,?,'history-connection',?,'active',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        CREDENTIAL,
        USER,
        ISSUER,
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    jdbc.update(
        "INSERT INTO user_cards"
            + " (user_card_id,user_id,card_id,codef_account_credential_id,card_name_from_codef,"
            + "card_no,issuer_id,codef_card_key_hash,created_at,updated_at)"
            + " VALUES (?,?,?,?,?,?,?, ?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
        USER_CARD,
        USER,
        CARD,
        CREDENTIAL,
        "CODEF 카드",
        "1234********5678",
        ISSUER,
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    jdbc.update(
        "INSERT INTO card_benefits (benefit_id,content_version_id,position,record_type,title)"
            + " VALUES (?,?,1,'benefit','카페 10% 할인')",
        BENEFIT, VERSION);
    jdbc.update(
        "INSERT INTO benefit_offers"
            + " (offer_id,benefit_id,offer_name,position,reward_type,value_type,calculation_mode,"
            + "calculation_basis,stacking_mode,valuation_scope,valuation_method) VALUES (?,?, '카페"
            + " 할인',1,'discount','percentage','flat','transaction_amount','standalone','transaction','direct')",
        OFFER,
        BENEFIT);
    jdbc.update(
        "INSERT INTO card_payment_approvals"
            + " (approval_id,user_id,user_card_id,approved_at,merchant_name,amount,approval_status,"
            + "source_payload,created_at)"
            + " VALUES (?,?,?,'2026-07-17"
            + " 05:30:00','스타벅스',15000,'approved',JSON_OBJECT(),UTC_TIMESTAMP(6))",
        APPROVAL,
        USER,
        USER_CARD);
    jdbc.update(
        "INSERT INTO user_benefit_usages"
            + " (usage_id,user_card_id,offer_id,approval_id,usage_date,eligible_amount_krw,"
            + "reward_amount_krw,usage_status,approved_at,confirmed_at)"
            + " VALUES (?,?,?,?, '2026-07-17',15000,1500,'confirmed','2026-07-17"
            + " 05:30:00','2026-07-17 05:30:01')",
        USAGE,
        USER_CARD,
        OFFER,
        APPROVAL);
  }

  @AfterEach
  void tearDown() {
    clean();
  }

  @Test
  void readsHistoryAndPreventsAnotherUsersAccess() {
    LocalDateTime from = LocalDateTime.of(2026, 6, 30, 15, 0);
    LocalDateTime to = LocalDateTime.of(2026, 7, 31, 15, 0);
    List<BenefitHistoryRow> rows =
        mapper.findHistory(USER, from, to, null, "DISCOUNT", "LATEST", 0, 20);
    assertEquals(1, rows.size());
    assertEquals("스타벅스", rows.get(0).getMerchantName());
    assertEquals("테스트 카드 1234********5678", rows.get(0).getCardName());
    assertEquals(1500L, rows.get(0).getBenefitAmount());
    BenefitHistorySummaryRow summary = mapper.summarizeHistory(USER, from, to, USER_CARD);
    assertEquals(1500L, summary.totalBenefitAmount());
    assertEquals(1500L, summary.discountAmount());
    assertEquals(0L, summary.cashbackAmount());
    assertEquals(0L, summary.pointAmount());
    assertEquals(0L, summary.mileageAmount());
    assertEquals(1L, mapper.countHistory(USER, from, to, null, "DISCOUNT"));
    assertEquals(0L, mapper.countHistory(OTHER_USER, from, to, null, null));
    assertNull(mapper.findDetail(OTHER_USER, USAGE));
    BenefitHistoryDetailRow detail = mapper.findDetail(USER, USAGE);
    assertEquals("카페 할인", detail.getBenefitTitle());
    assertEquals(0L, detail.getMonthlyLimitAmount());
  }

  private void clean() {
    jdbc.update("DELETE FROM user_benefit_usages");
    jdbc.update("DELETE FROM card_payment_approvals");
    jdbc.update("DELETE FROM benefit_rules");
    jdbc.update("DELETE FROM benefit_offers");
    jdbc.update("DELETE FROM card_benefits");
    jdbc.update("DELETE FROM user_cards");
    jdbc.update("DELETE FROM codef_account_credentials");
    jdbc.update("DELETE FROM card_content_versions");
    jdbc.update("DELETE FROM cards");
    jdbc.update("DELETE FROM issuers");
    jdbc.update("DELETE FROM users");
  }

  @Configuration
  @Import(TestcontainersMySqlConfig.class)
  @org.mybatis.spring.annotation.MapperScan(
      basePackageClasses = BenefitHistoryMapper.class,
      sqlSessionFactoryRef = "testSqlSessionFactory")
  static class Config { }
}
