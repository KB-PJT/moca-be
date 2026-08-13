package com.moca.mocabe.domain.notification.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.notification.model.PerformanceDeadlineCandidate;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@DisplayName("알림 Mapper MySQL 통합")
@SpringJUnitConfig(NotificationMapperIntegrationTest.NotificationMapperTestConfig.class)
class NotificationMapperIntegrationTest {
    private static final String USER_ID = "91000000-0000-4000-8000-000000000001";
    private static final String ISSUER_ID = "91000000-0000-4000-8000-000000000002";
    private static final String CARD_ID = "91000000-0000-4000-8000-000000000003";
    private static final String CONTENT_ID = "91000000-0000-4000-8000-000000000004";
    private static final String CREDENTIAL_ID = "91000000-0000-4000-8000-000000000005";
    private static final String USER_CARD_ID = "91000000-0000-4000-8000-000000000006";
    private static final String BENEFIT_ID = "91000000-0000-4000-8000-000000000007";
    private static final String OFFER_ID = "91000000-0000-4000-8000-000000000008";
    private static final String RULE_ID = "91000000-0000-4000-8000-000000000009";
    private static final String SNAPSHOT_ID = "91000000-0000-4000-8000-000000000010";
    private static final String DEVICE_ONE = "91000000-0000-4000-8000-000000000011";
    private static final String DEVICE_TWO = "91000000-0000-4000-8000-000000000012";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private DeviceMapper deviceMapper;

    @BeforeEach
    void setUp() {
        deleteTestData();
        insertPerformanceFixture();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("확인된 이번 달 실적이 기준보다 부족한 활성 카드만 조회한다")
    void findsUnderperformingActiveCard() {
        List<PerformanceDeadlineCandidate> candidates =
                notificationMapper.findPerformanceDeadlineCandidates("2026-08");

        assertEquals(1, candidates.size());
        assertEquals(USER_CARD_ID, candidates.get(0).userCardId());
        assertEquals(0, new BigDecimal("120000").compareTo(candidates.get(0).currentSpendAmount()));
        assertEquals(0, new BigDecimal("300000").compareTo(candidates.get(0).requiredSpendAmount()));
    }

    @Test
    @DisplayName("실적 마감 알림 설정이 꺼진 사용자는 제외한다")
    void excludesUserWithPerformanceNotificationDisabled() {
        jdbcTemplate.update("UPDATE user_notification_settings SET performance_closing_enabled=FALSE "
                + "WHERE user_id=?", USER_ID);

        assertTrue(notificationMapper.findPerformanceDeadlineCandidates("2026-08").isEmpty());
    }

    @Test
    @DisplayName("실적을 충족했거나 카드가 비활성이면 제외한다")
    void excludesSatisfiedOrInactiveCard() {
        jdbcTemplate.update("UPDATE user_card_performance_snapshots SET current_spend_amount=300000 "
                + "WHERE performance_snapshot_id=?", SNAPSHOT_ID);
        assertTrue(notificationMapper.findPerformanceDeadlineCandidates("2026-08").isEmpty());

        jdbcTemplate.update("UPDATE user_card_performance_snapshots SET current_spend_amount=120000 "
                + "WHERE performance_snapshot_id=?", SNAPSHOT_ID);
        jdbcTemplate.update("UPDATE user_cards SET is_active=FALSE WHERE user_card_id=?", USER_CARD_ID);
        assertTrue(notificationMapper.findPerformanceDeadlineCandidates("2026-08").isEmpty());
    }

    @Test
    @DisplayName("실적 스냅샷이 없으면 0원으로 추정하지 않고 알림 대상에서 제외한다")
    void excludesCardWithoutPerformanceSnapshot() {
        jdbcTemplate.update("DELETE FROM user_card_performance_snapshots WHERE performance_snapshot_id=?",
                SNAPSHOT_ID);

        assertTrue(notificationMapper.findPerformanceDeadlineCandidates("2026-08").isEmpty());
    }

    @Test
    @DisplayName("주변 혜택 설정이 켜진 사용자의 활성 기기만 조회한다")
    void findsOnlyActiveNearbyBenefitDevice() {
        insertDevice(DEVICE_ONE, "active-token", true);
        insertDevice(DEVICE_TWO, "inactive-token", false);

        assertEquals(List.of(DEVICE_ONE), deviceMapper.findActiveNearbyBenefitDevices().stream()
                .map(device -> device.userDeviceId()).toList());

        jdbcTemplate.update("UPDATE user_notification_settings SET nearby_benefit_enabled=FALSE WHERE user_id=?",
                USER_ID);
        assertTrue(deviceMapper.findActiveNearbyBenefitDevices().isEmpty());
    }

    @Test
    @DisplayName("기기별 선점 키가 다르면 모두 발송할 수 있고 같은 키는 한 번만 선점한다")
    void claimsAndStoresHistoryPerDevice() {
        insertDevice(DEVICE_ONE, "first-token", true);
        insertDevice(DEVICE_TWO, "second-token", true);

        assertEquals(1, claim("history-one", "delivery-one", DEVICE_ONE));
        assertEquals(0, claim("history-duplicate", "delivery-one", DEVICE_ONE));
        assertEquals(1, claim("history-two", "delivery-two", DEVICE_TWO));
        notificationMapper.updateHistory("history-one", "SENT", "message-id", null);
        notificationMapper.updateHistory("history-two", "FAILED", null, "UNAVAILABLE");

        assertTrue(notificationMapper.existsSent(USER_ID, DEVICE_ONE, "PERFORMANCE_DEADLINE", USER_CARD_ID,
                "2026-08-28", null));
        assertFalse(notificationMapper.existsSent(USER_ID, DEVICE_TWO, "PERFORMANCE_DEADLINE", USER_CARD_ID,
                "2026-08-28", null));
        assertEquals("message-id", jdbcTemplate.queryForObject(
                "SELECT fcm_message_id FROM notification_history WHERE notification_history_id='history-one'",
                String.class));
        assertEquals("UNAVAILABLE", jdbcTemplate.queryForObject(
                "SELECT error_message FROM notification_history WHERE notification_history_id='history-two'",
                String.class));
    }

    @Test
    @DisplayName("알림 중복 조회 인덱스는 사용자 다음에 기기를 구분한다")
    void indexesNotificationDeduplicationByDevice() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema=DATABASE() AND table_name='notification_history' "
                        + "AND index_name='idx_notification_history_dedup' ORDER BY seq_in_index",
                String.class);

        assertEquals(List.of("user_id", "user_device_id", "notification_type", "reference_id",
                "notification_date", "time_slot", "status"), columns);
    }

    private int claim(String historyId, String deliveryKey, String deviceId) {
        return notificationMapper.claimPending(historyId, deliveryKey, USER_ID, deviceId,
                "PERFORMANCE_DEADLINE", USER_CARD_ID, null, "2026-08-28", "title", "body", "PENDING");
    }

    private void insertDevice(String deviceId, String token, boolean active) {
        jdbcTemplate.update("INSERT INTO user_devices(user_device_id,user_id,fcm_token,device_type,is_active,"
                        + "last_token_updated_at,created_at,updated_at) VALUES(?,?,?,'WEB',?,UTC_TIMESTAMP(6),"
                        + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                deviceId, USER_ID, token, active);
    }

    private void insertPerformanceFixture() {
        jdbcTemplate.update("INSERT INTO users(user_id,google_subject,nickname,user_type,created_at,updated_at) "
                + "VALUES(?,'notification-subject','모카','user',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", USER_ID);
        jdbcTemplate.update("INSERT INTO user_notification_settings(user_id,performance_closing_enabled,"
                + "nearby_benefit_enabled,created_at,updated_at) VALUES(?,TRUE,TRUE,UTC_TIMESTAMP(6),"
                + "UTC_TIMESTAMP(6))", USER_ID);
        jdbcTemplate.update("INSERT INTO issuers(issuer_id,institution_code,issuer_name,created_at,updated_at) "
                + "VALUES(?,'N901','알림테스트카드',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ISSUER_ID);
        jdbcTemplate.update("INSERT INTO cards(card_id,issuer_id,card_type,first_seen_at,last_seen_at) "
                + "VALUES(?,?,'credit',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", CARD_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO card_content_versions(content_version_id,card_id,content_sha256,name,"
                + "discontinued,first_seen_at,last_seen_at) VALUES(?,?,REPEAT('a',64),'알림 카드',FALSE,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", CONTENT_ID, CARD_ID);
        jdbcTemplate.update("INSERT INTO codef_account_credentials(codef_account_credential_id,user_id,issuer_id,"
                + "connected_id,credential_identity_hash,status,created_at,updated_at) VALUES(?,?,?,"
                + "'91000000-0000-4000-8000-000000000099',REPEAT('b',64),'active',UTC_TIMESTAMP(6),"
                + "UTC_TIMESTAMP(6))", CREDENTIAL_ID, USER_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO user_cards(user_card_id,user_id,card_id,codef_account_credential_id,"
                + "card_name_from_codef,issuer_id,codef_card_key_hash,is_active,created_at,updated_at) "
                + "VALUES(?,?,?,?, '알림 카드',?,REPEAT('c',64),TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                USER_CARD_ID, USER_ID, CARD_ID, CREDENTIAL_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO user_card_performance_snapshots(performance_snapshot_id,user_card_id,"
                + "performance_month,current_spend_amount,updated_at,created_at) VALUES(?,?,'2026-08',120000,"
                + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", SNAPSHOT_ID, USER_CARD_ID);
        jdbcTemplate.update("INSERT INTO card_benefits(benefit_id,content_version_id,position,record_type,title) "
                + "VALUES(?,?,1,'benefit','기본 할인')", BENEFIT_ID, CONTENT_ID);
        jdbcTemplate.update("INSERT INTO benefit_offers(offer_id,benefit_id,offer_name,position,reward_type,"
                + "value_type,calculation_mode,calculation_basis,stacking_mode,reward_timing,valuation_scope,"
                + "valuation_method) VALUES(?,?,'기본 할인',1,'discount','percentage','flat',"
                + "'transaction_amount','standalone','statement','transaction','direct')", OFFER_ID, BENEFIT_ID);
        jdbcTemplate.update("INSERT INTO benefit_rules(rule_id,offer_id,position,rule_effect,stacking_mode,"
                + "reward_value,reward_unit,previous_spend_min_krw) VALUES(?,?,1,'grant','standalone',10,"
                + "'percent',300000)", RULE_ID, OFFER_ID);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM notification_history WHERE user_id=?", USER_ID);
        jdbcTemplate.update("DELETE FROM user_devices WHERE user_id=?", USER_ID);
        jdbcTemplate.update("DELETE FROM benefit_rules WHERE rule_id=?", RULE_ID);
        jdbcTemplate.update("DELETE FROM benefit_offers WHERE offer_id=?", OFFER_ID);
        jdbcTemplate.update("DELETE FROM card_benefits WHERE benefit_id=?", BENEFIT_ID);
        jdbcTemplate.update("DELETE FROM user_card_performance_snapshots WHERE user_card_id=?", USER_CARD_ID);
        jdbcTemplate.update("DELETE FROM user_cards WHERE user_card_id=?", USER_CARD_ID);
        jdbcTemplate.update("DELETE FROM codef_account_credentials WHERE codef_account_credential_id=?",
                CREDENTIAL_ID);
        jdbcTemplate.update("DELETE FROM card_content_versions WHERE content_version_id=?", CONTENT_ID);
        jdbcTemplate.update("DELETE FROM cards WHERE card_id=?", CARD_ID);
        jdbcTemplate.update("DELETE FROM issuers WHERE issuer_id=?", ISSUER_ID);
        jdbcTemplate.update("DELETE FROM user_notification_settings WHERE user_id=?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id=?", USER_ID);
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackages = "com.moca.mocabe.domain.notification.mapper",
            sqlSessionFactoryRef = "testSqlSessionFactory")
    static class NotificationMapperTestConfig {
    }
}
