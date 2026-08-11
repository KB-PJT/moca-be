package com.moca.mocabe.domain.support.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.support.model.InquiryRow;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
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
@SpringJUnitConfig(SupportInquiryMapperIntegrationTest.SupportTestConfig.class)
class SupportInquiryMapperIntegrationTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String OTHER_USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040507";
    private static final String INQUIRY_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";

    @Autowired
    private SupportInquiryMapper supportInquiryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        deleteTestData();
        insertUser(USER_ID, "google-subject-1");
        insertUser(OTHER_USER_ID, "google-subject-2");
    }

    @AfterEach
    void tearDownDatabase() {
        deleteTestData();
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM support_inquiries");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("MySQL 컨테이너에서 문의를 저장하고 본인 소유로 조회한다")
    void persistsAndFindsOwnInquiry() {
        int inserted = supportInquiryMapper.insertInquiry(INQUIRY_ID, USER_ID, "card_link",
                "카드 연동이 안 돼요", "계속 실패합니다.", "kakao_jimin@kakao.com");
        assertEquals(1, inserted);

        InquiryRow row = supportInquiryMapper.findByInquiryId(INQUIRY_ID, USER_ID);

        assertNotNull(row);
        assertEquals("card_link", row.getInquiryType());
        assertEquals("카드 연동이 안 돼요", row.getTitle());
        assertEquals("계속 실패합니다.", row.getContent());
        assertEquals("kakao_jimin@kakao.com", row.getReplyEmail());
        assertEquals("received", row.getStatus());
        assertNotNull(row.getCreatedAt());
        assertNull(row.getAnsweredAt());
    }

    @Test
    @DisplayName("다른 사용자가 등록한 문의는 조회되지 않는다")
    void doesNotFindInquiryOwnedByOtherUser() {
        supportInquiryMapper.insertInquiry(INQUIRY_ID, USER_ID, "card_link", "제목", "내용",
                "kakao_jimin@kakao.com");

        InquiryRow row = supportInquiryMapper.findByInquiryId(INQUIRY_ID, OTHER_USER_ID);

        assertNull(row);
    }

    private void insertUser(String userId, String googleSubject) {
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, email, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                userId, googleSubject, "모카", "moca@example.com");
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackages = "com.moca.mocabe.domain.support.mapper", sqlSessionFactoryRef = "testSqlSessionFactory")
    static class SupportTestConfig {
    }
}
