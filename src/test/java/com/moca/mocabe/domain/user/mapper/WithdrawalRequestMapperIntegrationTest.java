package com.moca.mocabe.domain.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@SpringJUnitConfig(TestcontainersMySqlConfig.class)
class WithdrawalRequestMapperIntegrationTest {

    @Autowired
    private WithdrawalRequestMapper withdrawalRequestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update("DELETE FROM withdrawal_requests");
    }

    @AfterEach
    void tearDownDatabase() {
        jdbcTemplate.update("DELETE FROM withdrawal_requests");
    }

    @Test
    @DisplayName("탈퇴 사유는 사용자와 연결 없이 사유·확인 여부만 저장한다")
    void savesWithdrawalReasonWithoutUserLink() {
        assertEquals(1, withdrawalRequestMapper.insertWithdrawalRequest("privacy_concern", "설명", true));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT reason_code, reason_text, confirmed, created_at FROM withdrawal_requests");
        assertEquals("privacy_concern", row.get("reason_code"));
        assertEquals("설명", row.get("reason_text"));
        assertEquals(true, row.get("confirmed"));
        assertNotNull(row.get("created_at"));
    }

    @Test
    @DisplayName("사유 코드와 상세 설명은 선택값이라 비워도 저장된다")
    void savesWithdrawalReasonWithoutOptionalFields() {
        assertEquals(1, withdrawalRequestMapper.insertWithdrawalRequest(null, null, true));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT reason_code, reason_text, confirmed FROM withdrawal_requests");
        assertNull(row.get("reason_code"));
        assertNull(row.get("reason_text"));
        assertEquals(true, row.get("confirmed"));
    }
}
