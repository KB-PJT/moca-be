package com.moca.mocabe.domain.codef.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moca.mocabe.domain.codef.model.ExistingApprovalKey;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * card_payment_approvals 적재·조회 매퍼를 실제 MySQL로 검증한다.
 *
 * findExistingApprovalKeys는 적재된 행이 있어야 resultMap 생성자 매핑이 실제로 실행되므로,
 * 행이 없을 때는 드러나지 않던 매핑 오류(record 생성자 타입 불일치 등)를 여기서 잡는다.
 */
@Tag("integration")
@SpringJUnitConfig(CardApprovalPersistenceIntegrationTest.CardApprovalTestConfig.class)
class CardApprovalPersistenceIntegrationTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ISSUER_ID = "22222222-2222-4222-8222-222222222222";
    private static final String CARD_ID = "33333333-3333-4333-8333-333333333333";
    private static final String CREDENTIAL_ID = "44444444-4444-4444-8444-444444444444";
    private static final String USER_CARD_ID = "55555555-5555-4555-8555-555555555555";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CardApprovalMapper cardApprovalMapper;

    @BeforeEach
    void setUpDatabase() {
        deleteTestData();
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_ID, "approval-persistence-subject", "모카");
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
                "0000000000000000000000000000000000000000000000000000000000000000");
        jdbcTemplate.update("INSERT INTO user_cards "
                        + "(user_card_id, user_id, card_id, codef_account_credential_id, "
                        + "card_name_from_codef, card_no, issuer_id, codef_card_key_hash, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_CARD_ID, USER_ID, CARD_ID, CREDENTIAL_ID,
                "노리2 체크카드(KB Pay)_비교통", "943646******1069", ISSUER_ID,
                "1111111111111111111111111111111111111111111111111111111111111111");
    }

    @AfterEach
    void tearDownDatabase() {
        deleteTestData();
    }

    @Test
    @DisplayName("승인내역을 적재하고, 적재된 행을 중복 판정 키로 다시 읽어온다")
    void insertsAndReadsBackApprovalKeys() {
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 2, 5, 32, 0);
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                "30014285", approvedAt, "스타벅스 강남점", 1800,
                "{\"resApprovalNo\":\"30014285\",\"resMemberStoreName\":\"스타벅스 강남점\"}");

        List<ExistingApprovalKey> keys = cardApprovalMapper.findExistingApprovalKeys(
                USER_ID, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 3, 0, 0));

        assertEquals(1, keys.size());
        ExistingApprovalKey key = keys.get(0);
        assertEquals(USER_CARD_ID, key.userCardId());
        assertEquals("30014285", key.approvalNumber());
        assertEquals(approvedAt, key.approvedAt());
        assertEquals(1800, key.amount());
        assertEquals("스타벅스 강남점", key.merchantName());
    }

    @Test
    @DisplayName("같은 카드+승인번호를 다시 적재하면 UNIQUE 제약으로 DuplicateKeyException이 난다")
    void rejectsDuplicateCardApprovalNumber() {
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 2, 5, 32, 0);
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                "30014285", approvedAt, "스타벅스", 1800, "{}");

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID,
                        null, "30014285", approvedAt.plusHours(1), "스타벅스 다른지점", 2000, "{}"));
    }

    @Test
    @DisplayName("승인번호가 NULL이어도 같은 자연키(시각·금액·가맹점명)를 다시 적재하면 UNIQUE 제약으로 막힌다")
    void rejectsDuplicateFallbackKeyWhenApprovalNumberNull() {
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 4, 15, 0, 0);
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                null, approvedAt, "노포", 7000, "{}");

        // 두 동시 요청이 승인번호 없는 같은 승인건을 각각 신규로 판단해 INSERT를 시도하는 상황을 재현한다.
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID,
                        null, null, approvedAt, "노포", 7000, "{}"));
    }

    @Test
    @DisplayName("승인번호가 빈 문자열(NULL 아님)이어도 같은 자연키면 UNIQUE 제약으로 막힌다")
    void rejectsDuplicateFallbackKeyWhenApprovalNumberBlank() {
        // COALESCE만 쓰면 ''(NULL 아님)일 때 fallback으로 넘어가지 않아 두 건 모두 dedupe_key=''가 되어
        // 중복 판정이 무력화된다. NULLIF(approval_number, '')로 빈 문자열도 NULL과 동일하게 취급해야 잡힌다.
        LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 4, 15, 0, 0);
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                "", approvedAt, "노포", 7000, "{}");

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID,
                        null, "", approvedAt, "노포", 7000, "{}"));
    }

    @Test
    @DisplayName("승인번호가 빈 문자열이어도 자연키가 다른 승인건은 서로 다른 행으로 적재된다")
    void insertsDistinctRowsWithBlankApprovalNumberWhenNaturalKeyDiffers() {
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                "", LocalDateTime.of(2026, 8, 4, 15, 0, 0), "노포1", 1000, "{}");
        cardApprovalMapper.insertApproval(UUID.randomUUID().toString(), USER_ID, USER_CARD_ID, null,
                "", LocalDateTime.of(2026, 8, 4, 16, 0, 0), "노포2", 2000, "{}");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM card_payment_approvals WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID);
        assertEquals(2, count);
    }

    @Test
    @DisplayName("매칭 후보 보유카드를 카드사·카드명·마스킹 카드번호로 조회한다")
    void findsUserCardsForMatching() {
        List<UserCardMatchRow> cards = cardApprovalMapper.findUserCardsForMatching(USER_ID);

        assertEquals(1, cards.size());
        assertEquals(USER_CARD_ID, cards.get(0).userCardId());
        assertEquals(ISSUER_ID, cards.get(0).issuerId());
        assertEquals("노리2 체크카드(KB Pay)_비교통", cards.get(0).cardName());
        assertEquals("943646******1069", cards.get(0).cardNo());
    }

    @Test
    @DisplayName("비활성(is_active=FALSE) 카드는 매칭 후보에서 제외한다")
    void excludesInactiveUserCards() {
        jdbcTemplate.update("INSERT INTO user_cards "
                        + "(user_card_id, user_id, card_id, codef_account_credential_id, "
                        + "card_name_from_codef, card_no, issuer_id, is_active, codef_card_key_hash, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "66666666-6666-4666-8666-666666666666", USER_ID, CARD_ID, CREDENTIAL_ID,
                "KB 국민 일반", "111111******2222", ISSUER_ID,
                "2222222222222222222222222222222222222222222222222222222222222222");

        List<UserCardMatchRow> cards = cardApprovalMapper.findUserCardsForMatching(USER_ID);

        assertEquals(1, cards.size());
        assertEquals(USER_CARD_ID, cards.get(0).userCardId());
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM card_payment_approvals");
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
            basePackageClasses = CardApprovalMapper.class, sqlSessionFactoryRef = "testSqlSessionFactory")
    static class CardApprovalTestConfig {

        @org.springframework.context.annotation.Bean
        public org.springframework.transaction.PlatformTransactionManager transactionManager(
                DataSource dataSource) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        }
    }
}
