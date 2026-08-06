package com.moca.mocabe.domain.codef.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** user_card_performance_snapshots upsert 매퍼를 실제 MySQL로 검증한다. */
@Tag("integration")
@org.springframework.test.context.junit.jupiter.SpringJUnitConfig(
        CardPerformanceMapperIntegrationTest.CardPerformanceTestConfig.class)
class CardPerformanceMapperIntegrationTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111112";
    private static final String ISSUER_ID = "22222222-2222-4222-8222-222222222223";
    private static final String CARD_ID = "33333333-3333-4333-8333-333333333334";
    private static final String CREDENTIAL_ID = "44444444-4444-4444-8444-444444444445";
    private static final String USER_CARD_ID = "55555555-5555-4555-8555-555555555556";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CardPerformanceMapper cardPerformanceMapper;

    @BeforeEach
    void setUpDatabase() {
        deleteTestData();
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_ID, "performance-persistence-subject", "모카");
        jdbcTemplate.update("INSERT INTO issuers "
                        + "(issuer_id, institution_code, issuer_name, created_at, updated_at) "
                        + "VALUES (?, '0301', 'KB카드', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                ISSUER_ID);
        jdbcTemplate.update("INSERT INTO cards "
                        + "(card_id, issuer_id, card_type, first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, 'check', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CARD_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO codef_account_credentials "
                        + "(codef_account_credential_id, user_id, issuer_id, connected_id, "
                        + "credential_identity_hash, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'cid', ?, 'active', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CREDENTIAL_ID, USER_ID, ISSUER_ID,
                "3333333333333333333333333333333333333333333333333333333333333333");
        jdbcTemplate.update("INSERT INTO user_cards "
                        + "(user_card_id, user_id, card_id, codef_account_credential_id, "
                        + "card_name_from_codef, card_no, issuer_id, codef_card_key_hash, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_CARD_ID, USER_ID, CARD_ID, CREDENTIAL_ID,
                "노리2 체크카드(KB Pay)_비교통", "943646******1069", ISSUER_ID,
                "4444444444444444444444444444444444444444444444444444444444444444");
    }

    @AfterEach
    void tearDownDatabase() {
        deleteTestData();
    }

    @Test
    @DisplayName("같은 카드·달에 처음 upsert하면 새 스냅샷 행이 생긴다")
    void insertsNewSnapshot() {
        cardPerformanceMapper.upsertPerformanceSnapshot(
                UUID.randomUUID().toString(), USER_CARD_ID, "2026-08", 300000);

        assertEquals(1, countSnapshots());
        assertEquals(300000, currentSpendAmount());
    }

    @Test
    @DisplayName("같은 카드·달에 다시 upsert하면 새 행을 만들지 않고 금액만 최신 값으로 덮어쓴다")
    void updatesExistingSnapshotOnConflict() {
        cardPerformanceMapper.upsertPerformanceSnapshot(
                UUID.randomUUID().toString(), USER_CARD_ID, "2026-08", 300000);

        cardPerformanceMapper.upsertPerformanceSnapshot(
                UUID.randomUUID().toString(), USER_CARD_ID, "2026-08", 450000);

        assertEquals(1, countSnapshots());
        assertEquals(450000, currentSpendAmount());
    }

    @Test
    @DisplayName("같은 카드라도 실적월이 다르면 서로 다른 행으로 적재된다")
    void insertsDistinctRowsForDifferentMonths() {
        cardPerformanceMapper.upsertPerformanceSnapshot(
                UUID.randomUUID().toString(), USER_CARD_ID, "2026-07", 100000);
        cardPerformanceMapper.upsertPerformanceSnapshot(
                UUID.randomUUID().toString(), USER_CARD_ID, "2026-08", 200000);

        assertEquals(2, countSnapshots());
    }

    private int countSnapshots() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_card_performance_snapshots WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID);
    }

    private int currentSpendAmount() {
        return jdbcTemplate.queryForObject(
                "SELECT current_spend_amount FROM user_card_performance_snapshots "
                        + "WHERE user_card_id = ? AND performance_month = '2026-08'",
                Integer.class, USER_CARD_ID);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM user_card_performance_snapshots");
        jdbcTemplate.update("DELETE FROM user_cards");
        jdbcTemplate.update("DELETE FROM codef_account_credentials");
        jdbcTemplate.update("DELETE FROM cards");
        jdbcTemplate.update("DELETE FROM issuers");
        jdbcTemplate.update("DELETE FROM user_notification_settings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @org.mybatis.spring.annotation.MapperScan(
            basePackageClasses = CardPerformanceMapper.class, sqlSessionFactoryRef = "testSqlSessionFactory")
    static class CardPerformanceTestConfig {

        @org.springframework.context.annotation.Bean
        public org.springframework.transaction.PlatformTransactionManager transactionManager(
                DataSource dataSource) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        }
    }
}
