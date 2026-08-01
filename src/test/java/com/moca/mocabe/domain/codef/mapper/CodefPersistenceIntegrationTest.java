package com.moca.mocabe.domain.codef.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.infra.AesGcmEncryptor;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialFingerprintGenerator;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Tag("integration")
@SpringJUnitConfig(CodefPersistenceIntegrationTest.CodefPersistenceTestConfig.class)
class CodefPersistenceIntegrationTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private static final String CONNECTED_ID = "01980d6a-5c0c-7aaf-9b85-010203040521";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CodefClient codefClient;

    @Autowired
    private CardLinkService cardLinkService;

    @Autowired
    private Encryptor encryptor;

    @BeforeEach
    void setUpDatabase() {
        deleteTestData();
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_ID, "codef-persistence-google-subject", "모카");
        jdbcTemplate.update("INSERT INTO issuers "
                        + "(issuer_id, institution_code, issuer_name, "
                        + "requires_id, requires_password, requires_card_no, requires_card_password, "
                        + "requires_birth_date, created_at, updated_at) "
                        + "VALUES (?, ?, ?, TRUE, TRUE, TRUE, TRUE, TRUE, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                ISSUER_ID, "0301", "KB카드");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn(CONNECTED_ID);
    }

    @AfterEach
    void tearDownDatabase() {
        deleteTestData();
    }

    @Test
    @DisplayName("Connected ID와 암호화 자격정보를 실제 MySQL에 저장한다")
    void storesConnectedIdAndEncryptedCredentials() {
        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertEquals(USER_ID, findString("user_id", response.getLinkId()));
        assertEquals(ISSUER_ID, findString("issuer_id", response.getLinkId()));
        assertEquals(CONNECTED_ID, findString("connected_id", response.getLinkId()));
        assertEquals("ACTIVE", findString("status", response.getLinkId()));
        assertEquals(64, findString("credential_fingerprint", response.getLinkId()).length());
        assertEquals("tester", decrypt("account_id_enc", response.getLinkId()));
        assertEquals("secret-pw", decrypt("account_password_enc", response.getLinkId()));
        assertEquals("1234567890123456", decrypt("card_number_enc", response.getLinkId()));
        assertEquals("1234", decrypt("card_password_enc", response.getLinkId()));
        assertEquals("900101", decrypt("birth_date_enc", response.getLinkId()));
    }

    @Test
    @DisplayName("동일 사용자의 같은 카드번호 중복 연동을 차단한다")
    void rejectsDuplicatedCredential() {
        cardLinkService.createLink(USER_ID, request());

        assertThrows(CodefAccountAlreadyLinkedException.class,
                () -> cardLinkService.createLink(USER_ID, request()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM codef_account_credentials", Integer.class));
    }

    private CreateCardLinkRequest request() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setIssuerId(ISSUER_ID);
        request.setId("tester");
        request.setPassword("secret-pw");
        request.setCardNo("1234567890123456");
        request.setCardPassword("1234");
        request.setBirthDate("900101");
        return request;
    }

    private String findString(String column, String credentialId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM codef_account_credentials "
                        + "WHERE codef_account_credential_id = ?",
                String.class, credentialId);
    }

    private String decrypt(String column, String credentialId) {
        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM codef_account_credentials "
                        + "WHERE codef_account_credential_id = ?",
                byte[].class, credentialId);
        return encryptor.decrypt(ciphertext);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM user_cards");
        jdbcTemplate.update("DELETE FROM codef_account_credentials");
        jdbcTemplate.update("DELETE FROM issuers");
        jdbcTemplate.update("DELETE FROM user_notification_settings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Configuration
    @EnableTransactionManagement
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackageClasses = CodefCredentialMapper.class,
            sqlSessionFactoryRef = "testSqlSessionFactory")
    static class CodefPersistenceTestConfig {

        @Bean
        public CodefClient codefClient() {
            return org.mockito.Mockito.mock(CodefClient.class);
        }

        @Bean
        public Encryptor encryptor() {
            return new AesGcmEncryptor(new byte[32]);
        }

        @Bean
        public CredentialFingerprintGenerator credentialFingerprintGenerator() {
            return new CredentialFingerprintGenerator(new byte[32]);
        }

        @Bean
        public CardLinkService cardLinkService(
                CodefClient codefClient,
                CodefCredentialMapper codefCredentialMapper,
                IssuerMapper issuerMapper,
                Encryptor encryptor,
                CredentialFingerprintGenerator fingerprintGenerator
        ) {
            return new CardLinkService(
                    codefClient, codefCredentialMapper, issuerMapper, encryptor, fingerprintGenerator);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
