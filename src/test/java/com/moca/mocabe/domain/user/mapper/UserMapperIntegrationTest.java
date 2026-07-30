package com.moca.mocabe.domain.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@SpringJUnitConfig(TestcontainersMySqlConfig.class)
class UserMapperIntegrationTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update("DELETE FROM user_notification_settings");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, email, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_ID, "google-subject-for-test", "모카", "moca@example.com");
    }

    @Test
    @DisplayName("MySQL 컨테이너에서 사용자 프로필과 알림 설정을 저장하고 조회한다")
    void persistsUserProfileAndNotificationSettings() {
        UserProfile userProfile = userMapper.findProfileById(USER_ID);

        assertNotNull(userProfile);
        assertEquals("모카", userProfile.getNickname());

        NotificationSettings settings = new NotificationSettings();
        settings.setPerformanceClosingEnabled(true);
        settings.setNearbyBenefitEnabled(true);
        settings.setBenefitLimitEnabled(false);
        settings.setMarketingEnabled(false);
        userMapper.upsertNotificationSettings(USER_ID, settings);

        NotificationSettings savedSettings = userMapper.findNotificationSettingsByUserId(USER_ID);
        assertTrue(savedSettings.isPerformanceClosingEnabled());
        assertTrue(savedSettings.isNearbyBenefitEnabled());
        assertFalse(savedSettings.isBenefitLimitEnabled());

        userMapper.updateCardSortMode(USER_ID, "MANUAL");
        assertEquals("MANUAL", userMapper.findProfileById(USER_ID).getCardSortMode());
    }

    @Test
    @DisplayName("Flyway가 초기 스키마 마이그레이션 이력을 기록한다")
    void recordsFlywayMigrationHistory() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);

        assertEquals(1, migrationCount);
    }

    @Test
    @DisplayName("MySQL 외래 키 순서에 맞춰 알림 설정을 먼저 지우고 계정을 삭제한다")
    void permanentlyDeletesUserAndNotificationSettings() {
        userMapper.upsertNotificationSettings(USER_ID, new NotificationSettings());
        userMapper.deleteNotificationSettings(USER_ID);

        assertEquals(1, userMapper.deleteUser(USER_ID));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?",
                Integer.class, USER_ID));
    }

}
