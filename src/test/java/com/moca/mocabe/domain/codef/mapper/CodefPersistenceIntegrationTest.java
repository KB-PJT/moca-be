package com.moca.mocabe.domain.codef.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsRequest;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CardOptionSelectionRequest;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.dto.OptionSelectionRequest;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.infra.AesGcmEncryptor;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialHasher;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.domain.codef.service.CardCatalogMatcher;
import com.moca.mocabe.domain.codef.service.CardNameNormalizer;
import com.moca.mocabe.domain.codef.service.CodefCredentialStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("integration")
@SpringJUnitConfig(CodefPersistenceIntegrationTest.CodefPersistenceTestConfig.class)
class CodefPersistenceIntegrationTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private static final String CONNECTED_ID = "01980d6a-5c0c-7aaf-9b85-010203040521";
    private static final String CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040601";
    private static final String CONTENT_VERSION_ID = "01980d6a-5c0c-7aaf-9b85-010203040611";
    private static final String OPTION_GROUP_ID = "01980d6a-5c0c-7aaf-9b85-010203040603";
    private static final String OPTION_CHOICE_ID = "01980d6a-5c0c-7aaf-9b85-010203040604";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CodefClient codefClient;

    @Autowired
    private CardLinkService cardLinkService;

    @Autowired
    private CodefCredentialStore codefCredentialStore;

    @Autowired
    private Encryptor encryptor;

    @Autowired
    private CodefCredentialMapper codefCredentialMapper;

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
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return CONNECTED_ID;
        });
        when(codefClient.getOwnedCards(CONNECTED_ID, "0301")).thenReturn(List.of());
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
        assertEquals("active", findString("status", response.getLinkId()));
        assertEquals(64, findString("credential_identity_hash", response.getLinkId()).length());
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

    @Test
    @DisplayName("매칭 카드는 비활성으로 적재되고, 활성화 요청 시 옵션과 함께 활성화된다")
    void activatesMatchedCardWithOption() {
        jdbcTemplate.update("INSERT INTO cards "
                        + "(card_id, issuer_id, card_type, first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, 'check', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CARD_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO card_content_versions "
                        + "(content_version_id, card_id, content_sha256, name, image_url, "
                        + "first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CONTENT_VERSION_ID, CARD_ID,
                "0000000000000000000000000000000000000000000000000000000000000000",
                "노리2 체크카드(KB Pay)", "https://gorilla/card.png");
        jdbcTemplate.update("INSERT INTO card_option_groups "
                        + "(option_group_id, card_id, group_key, group_name, parse_status, parse_confidence) "
                        + "VALUES (?, ?, 'main', '혜택 팩', 'verified', 0.9900)",
                OPTION_GROUP_ID, CARD_ID);
        jdbcTemplate.update("INSERT INTO card_option_choices "
                        + "(option_choice_id, option_group_id, choice_key, choice_name, "
                        + "parse_status, parse_confidence) VALUES (?, ?, 'a', 'A팩', 'verified', 0.9900)",
                OPTION_CHOICE_ID, OPTION_GROUP_ID);
        when(codefClient.getOwnedCards(CONNECTED_ID, "0301")).thenReturn(List.of(
                new CodefOwnedCard("노리2 체크카드(KB Pay)_비교통", "1234****5678", "체크/본인", "")));

        CardLinkResponse link = cardLinkService.createLink(USER_ID, request());
        String userCardId = link.cards().get(0).userCardId();
        assertNotNull(userCardId);
        // 적재 직후에는 비활성 상태여야 한다.
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT is_active FROM user_cards WHERE user_card_id = ?", Boolean.class, userCardId));

        ActivateCardLinkCardsRequest activateRequest = new ActivateCardLinkCardsRequest();
        activateRequest.setActiveUserCardIds(List.of(userCardId));
        CardOptionSelectionRequest cardOption = new CardOptionSelectionRequest();
        cardOption.setUserCardId(userCardId);
        OptionSelectionRequest option = new OptionSelectionRequest();
        option.setOptionGroupId(OPTION_GROUP_ID);
        option.setOptionChoiceId(OPTION_CHOICE_ID);
        cardOption.setOptionSelections(List.of(option));
        activateRequest.setOptionSelections(List.of(cardOption));

        cardLinkService.activateCards(USER_ID, link.linkId(), activateRequest);

        assertEquals(CARD_ID, jdbcTemplate.queryForObject(
                "SELECT card_id FROM user_cards WHERE user_id = ?", String.class, USER_ID));
        assertEquals("1234****5678", jdbcTemplate.queryForObject(
                "SELECT card_no FROM user_cards WHERE user_card_id = ?", String.class, userCardId));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT is_active FROM user_cards WHERE user_card_id = ?", Boolean.class, userCardId));
        assertEquals(OPTION_CHOICE_ID, jdbcTemplate.queryForObject(
                "SELECT option_choice_id FROM user_card_option_selections", String.class));
    }

    @Test
    @DisplayName("승인내역 조회용으로 활성 연동만 기관코드·birth_date_enc와 함께 조회한다")
    void findsActiveConnectionsWithInstitutionCode() {
        insertCredential("01980d6a-5c0c-7aaf-9b85-0102030405a1", CONNECTED_ID, "active",
                "1000000000000000000000000000000000000000000000000000000000000001",
                encryptor.encrypt("900101"));
        insertCredential("01980d6a-5c0c-7aaf-9b85-0102030405a2", "revoked-connected-id", "revoked",
                "1000000000000000000000000000000000000000000000000000000000000002",
                encryptor.encrypt("880220"));

        List<CodefConnection> connections =
                codefCredentialMapper.findActiveConnectionsByUserId(USER_ID);

        assertEquals(1, connections.size());
        assertEquals(CONNECTED_ID, connections.get(0).connectedId());
        assertEquals("0301", connections.get(0).institutionCode());
        assertEquals(ISSUER_ID, connections.get(0).issuerId());
        assertEquals("KB카드", connections.get(0).issuerName());
        assertEquals(null, connections.get(0).performanceLookbackMonths());
        assertEquals("900101", encryptor.decrypt(connections.get(0).birthDateEnc()));
        assertEquals(null, connections.get(0).cardNumberEnc());
        assertEquals(null, connections.get(0).cardPasswordEnc());
    }

    @Test
    @DisplayName("실적조회 인증에 필요한 카드번호·카드비밀번호가 저장돼 있으면 함께 조회한다")
    void findsActiveConnectionsWithCardCredentials() {
        jdbcTemplate.update("INSERT INTO codef_account_credentials "
                        + "(codef_account_credential_id, user_id, issuer_id, connected_id, "
                        + "birth_date_enc, card_number_enc, card_password_enc, credential_identity_hash, status, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-0102030405c1", USER_ID, ISSUER_ID, "cid-with-card",
                encryptor.encrypt("900101"), encryptor.encrypt("1234567890123456"), encryptor.encrypt("12"),
                "4000000000000000000000000000000000000000000000000000000000000001");

        List<CodefConnection> connections = codefCredentialMapper.findActiveConnectionsByUserId(USER_ID);

        assertEquals(1, connections.size());
        assertEquals("1234567890123456", encryptor.decrypt(connections.get(0).cardNumberEnc()));
        assertEquals("12", encryptor.decrypt(connections.get(0).cardPasswordEnc()));
    }

    @Test
    @DisplayName("동시 재조회로 같은 카드를 두 요청이 함께 적재하면 하나의 user_card_id로 수렴한다")
    void concurrentSaveCardConvergesToSingleUserCardId() throws Exception {
        String linkId = "01980d6a-5c0c-7aaf-9b85-0102030405b1";
        insertCredential(linkId, CONNECTED_ID, "active",
                "3000000000000000000000000000000000000000000000000000000000000001",
                encryptor.encrypt("900101"));
        jdbcTemplate.update("INSERT INTO cards "
                        + "(card_id, issuer_id, card_type, first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, 'credit', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CARD_ID, ISSUER_ID);
        String cardKeyHash = "concurrent-resync-card-key-hash";
        LinkedCardInsert first = new LinkedCardInsert(UUID.randomUUID().toString(), linkId, USER_ID, ISSUER_ID,
                CARD_ID, "동시성 테스트 카드", "1111****2222", cardKeyHash, 0);
        LinkedCardInsert second = new LinkedCardInsert(UUID.randomUUID().toString(), linkId, USER_ID, ISSUER_ID,
                CARD_ID, "동시성 테스트 카드", "1111****2222", cardKeyHash, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<String> saveFirst = () -> {
                ready.countDown();
                start.await();
                return codefCredentialStore.saveCard(first);
            };
            Callable<String> saveSecond = () -> {
                ready.countDown();
                start.await();
                return codefCredentialStore.saveCard(second);
            };
            Future<String> resultOne = executor.submit(saveFirst);
            Future<String> resultTwo = executor.submit(saveSecond);
            ready.await();
            start.countDown();

            String userCardIdOne = resultOne.get(10, TimeUnit.SECONDS);
            String userCardIdTwo = resultTwo.get(10, TimeUnit.SECONDS);

            assertEquals(userCardIdOne, userCardIdTwo);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_cards WHERE codef_card_key_hash = ?", Integer.class, cardKeyHash));
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertCredential(String credentialId, String connectedId, String status,
                                  String identityHash, byte[] birthDateEnc) {
        jdbcTemplate.update("INSERT INTO codef_account_credentials "
                        + "(codef_account_credential_id, user_id, issuer_id, connected_id, "
                        + "birth_date_enc, credential_identity_hash, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                credentialId, USER_ID, ISSUER_ID, connectedId, birthDateEnc, identityHash, status);
    }

    private CreateCardLinkRequest request() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode("0301");
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
        jdbcTemplate.update("DELETE FROM user_card_option_selections");
        jdbcTemplate.update("DELETE FROM user_cards");
        jdbcTemplate.update("DELETE FROM codef_account_credentials");
        jdbcTemplate.update("DELETE FROM card_option_choices");
        jdbcTemplate.update("DELETE FROM card_option_groups");
        jdbcTemplate.update("DELETE FROM card_annual_fee_options");
        jdbcTemplate.update("DELETE FROM card_content_versions");
        jdbcTemplate.update("DELETE FROM cards");
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
        public CredentialHasher credentialHasher() {
            return new CredentialHasher(new byte[32]);
        }

        @Bean
        public CardLinkService cardLinkService(
                CodefClient codefClient,
                CodefCredentialMapper codefCredentialMapper,
                CodefCredentialStore codefCredentialStore,
                IssuerMapper issuerMapper,
                Encryptor encryptor,
                CredentialHasher credentialHasher,
                CardCatalogMatcher cardCatalogMatcher,
                CardCatalogMapper cardCatalogMapper,
                LinkedCardMapper linkedCardMapper
        ) {
            return new CardLinkService(
                    codefClient, codefCredentialMapper, codefCredentialStore,
                    issuerMapper, encryptor, credentialHasher,
                    cardCatalogMatcher, cardCatalogMapper, linkedCardMapper);
        }

        @Bean
        public CodefCredentialStore codefCredentialStore(CodefCredentialMapper codefCredentialMapper,
                                                          LinkedCardMapper linkedCardMapper) {
            return new CodefCredentialStore(codefCredentialMapper, linkedCardMapper);
        }

        @Bean
        public CardNameNormalizer cardNameNormalizer() {
            return new CardNameNormalizer();
        }

        @Bean
        public CardCatalogMatcher cardCatalogMatcher(CardNameNormalizer normalizer) {
            return new CardCatalogMatcher(normalizer);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
