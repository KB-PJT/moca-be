package com.moca.mocabe.domain.card.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.util.List;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Tag("integration")
@SpringJUnitConfig(UserCardSchemaIntegrationTest.CardTestConfig.class)
class UserCardSchemaIntegrationTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040531";
    private static final String ANOTHER_USER_CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040532";
    private static final String CODEF_ACCOUNT_CREDENTIAL_ID = "01980d6a-5c0c-7aaf-9b85-010203040521";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";
    private static final String CARD_ID = "01980d6a-5c0c-7aaf-9b85-010203040601";
    private static final String CONTENT_VERSION_ID = "01980d6a-5c0c-7aaf-9b85-010203040611";
    private static final String CARD_KEY_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ANOTHER_CARD_KEY_HASH =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserCardMapper userCardMapper;

    @Autowired
    private CardQueryService cardQueryService;

    @BeforeEach
    void setUpDatabase() {
        deleteTestData();
        jdbcTemplate.update("INSERT INTO users "
                        + "(user_id, google_subject, nickname, user_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'user', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                USER_ID, "card-schema-google-subject", "모카");
        jdbcTemplate.update("INSERT INTO issuers "
                        + "(issuer_id, institution_code, issuer_name, "
                        + "requires_id, requires_password, requires_card_no, requires_card_password, "
                        + "requires_birth_date, created_at, updated_at) "
                        + "VALUES (?, ?, ?, TRUE, TRUE, TRUE, TRUE, TRUE, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                ISSUER_ID, "0301", "KB카드");
        jdbcTemplate.update("INSERT INTO cards "
                        + "(card_id, issuer_id, card_type, first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, 'check', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CARD_ID, ISSUER_ID);
        jdbcTemplate.update("INSERT INTO card_content_versions "
                        + "(content_version_id, card_id, content_sha256, name, image_url, "
                        + "first_seen_at, last_seen_at) "
                        + "VALUES (?, ?, ?, ?, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CONTENT_VERSION_ID, CARD_ID,
                "0000000000000000000000000000000000000000000000000000000000000000",
                "노리2 체크카드(KB Pay)");
        jdbcTemplate.update("INSERT INTO codef_account_credentials "
                        + "(codef_account_credential_id, user_id, issuer_id, connected_id, "
                        + "credential_identity_hash, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'active', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CODEF_ACCOUNT_CREDENTIAL_ID, USER_ID, ISSUER_ID,
                "01980d6a-5c0c-7aaf-9b85-010203040522", CARD_KEY_HASH);
    }

    @AfterEach
    void tearDownDatabase() {
        deleteTestData();
    }

    @Test
    @DisplayName("Flyway 테이블에서 활성·비활성 카드와 issuer 정보를 조회한다")
    void findsActiveAndInactiveCardsFromMigratedSchema() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, true, 1);
        insertUserCard(ANOTHER_USER_CARD_ID, ANOTHER_CARD_KEY_HASH, false, 2);

        List<UserCardListRow> activeCards = userCardMapper.findActiveByUserId(USER_ID);
        List<UserCardListRow> inactiveCards = userCardMapper.findInactiveByUserId(USER_ID);

        assertEquals(1, activeCards.size());
        assertEquals(USER_CARD_ID, activeCards.get(0).getUserCardId());
        assertEquals(ISSUER_ID, activeCards.get(0).getIssuerId());
        assertEquals("KB카드", activeCards.get(0).getIssuerName());
        assertNull(activeCards.get(0).getCardImageUrl());
        assertEquals(1, inactiveCards.size());
        assertEquals(ANOTHER_USER_CARD_ID, inactiveCards.get(0).getUserCardId());
        assertEquals("0301", jdbcTemplate.queryForObject(
                "SELECT institution_code FROM issuers WHERE issuer_id = ?",
                String.class, ISSUER_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = TRUE",
                Integer.class));
    }

    @Test
    @DisplayName("비활성 카드만 있어도 보유 카드가 존재하는 것으로 판단한다")
    void existsByUserIdIsTrueEvenWhenOnlyInactiveCardExists() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, false, 1);

        assertTrue(userCardMapper.existsByUserId(USER_ID));
    }

    @Test
    @DisplayName("보유 카드가 없으면 존재하지 않는 것으로 판단한다")
    void existsByUserIdIsFalseWhenNoCardExists() {
        assertFalse(userCardMapper.existsByUserId(USER_ID));
    }

    @Test
    @DisplayName("같은 사용자의 CODEF 카드 식별값 hash 중복 등록을 막는다")
    void rejectsDuplicatedCardKeyHashForSameUser() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, true, 1);

        assertThrows(DuplicateKeyException.class,
                () -> insertUserCard(ANOTHER_USER_CARD_ID, CARD_KEY_HASH, true, 2));
    }

    @Test
    @DisplayName("카드 연결 해제 시 다섯 자식 테이블을 모두 지우고 user_cards를 삭제한다")
    void disconnectsCardByDeletingChildRowsBeforeUserCard() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, true, 1);
        String optionGroupId = "01980d6a-5c0c-7aaf-9b85-010203040701";
        String optionChoiceId = "01980d6a-5c0c-7aaf-9b85-010203040702";
        String approvalId = "01980d6a-5c0c-7aaf-9b85-010203040703";
        String benefitId = "01980d6a-5c0c-7aaf-9b85-010203040705";
        String offerId = "01980d6a-5c0c-7aaf-9b85-010203040706";
        String ruleId = "01980d6a-5c0c-7aaf-9b85-010203040707";
        jdbcTemplate.update("INSERT INTO card_option_groups "
                        + "(option_group_id, card_id, group_key, group_name, "
                        + "selection_required, created_at, updated_at) "
                        + "VALUES (?, ?, 'brand', '브랜드', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                optionGroupId, CARD_ID);
        jdbcTemplate.update("INSERT INTO card_option_choices "
                        + "(option_choice_id, option_group_id, choice_key, choice_name, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 'visa', 'VISA', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                optionChoiceId, optionGroupId);
        jdbcTemplate.update("INSERT INTO user_card_option_selections "
                        + "(user_card_id, option_group_id, card_id, option_choice_id, selected_at) "
                        + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))",
                USER_CARD_ID, optionGroupId, CARD_ID, optionChoiceId);
        jdbcTemplate.update("INSERT INTO user_card_performance_snapshots "
                        + "(performance_snapshot_id, user_card_id, performance_month, current_spend_amount, "
                        + "updated_at, created_at) "
                        + "VALUES (?, ?, '2026-08', 100000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040704", USER_CARD_ID);
        jdbcTemplate.update("INSERT INTO card_payment_approvals "
                        + "(approval_id, user_id, user_card_id, approval_number, approved_at, "
                        + "merchant_name, amount, approval_status, source_payload, created_at) "
                        + "VALUES (?, ?, ?, 'A-1', UTC_TIMESTAMP(6), '테스트가맹점', 10000, 'approved', "
                        + "JSON_OBJECT(), UTC_TIMESTAMP(6))",
                approvalId, USER_ID, USER_CARD_ID);
        jdbcTemplate.update("INSERT INTO card_benefits "
                        + "(benefit_id, content_version_id, position, record_type, title, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 1, 'benefit', '카페 10% 할인', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                benefitId, CONTENT_VERSION_ID);
        jdbcTemplate.update("INSERT INTO benefit_offers "
                        + "(offer_id, benefit_id, offer_name, position, reward_type, value_type, "
                        + "calculation_mode, calculation_basis, stacking_mode, valuation_scope, "
                        + "valuation_method, created_at, updated_at) "
                        + "VALUES (?, ?, '카페 할인', 1, 'cashback', 'percentage', 'flat', "
                        + "'transaction_amount', 'standalone', 'transaction', 'direct', "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                offerId, benefitId);
        jdbcTemplate.update("INSERT INTO benefit_rules "
                        + "(rule_id, offer_id, position, rule_effect, stacking_mode, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 1, 'grant', 'standalone', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                ruleId, offerId);
        jdbcTemplate.update("INSERT INTO user_benefit_usages "
                        + "(usage_id, user_card_id, offer_id, rule_id, approval_id, usage_date, "
                        + "eligible_amount_krw, reward_amount_krw, usage_status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE(), 10000, 1000, 'confirmed', "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040708", USER_CARD_ID, offerId, ruleId, approvalId);
        jdbcTemplate.update("INSERT INTO user_benefit_calculation_outcomes "
                        + "(outcome_id, user_card_id, approval_id, offer_id, rule_id, usage_date, "
                        + "reward_unit, expected_reward_value, applied_reward_value, missed_reward_value, "
                        + "outcome_status, rejection_reason, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE(), 'KRW', 1000, 1000, 0, "
                        + "'applied', 'none', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040709", USER_CARD_ID, approvalId, offerId, ruleId);

        cardQueryService.disconnectCard(USER_ID, USER_CARD_ID);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_benefit_calculation_outcomes WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_benefit_usages WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_card_option_selections WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_card_performance_snapshots WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM card_payment_approvals WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_cards WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));

        jdbcTemplate.update("DELETE FROM benefit_rules WHERE rule_id = ?", ruleId);
        jdbcTemplate.update("DELETE FROM benefit_offers WHERE offer_id = ?", offerId);
        jdbcTemplate.update("DELETE FROM card_benefits WHERE benefit_id = ?", benefitId);
    }

    @Test
    @DisplayName("회원 탈퇴 시 사용자의 모든 보유 카드와 자식 테이블을 FK 위반 없이 지운다")
    void deletesAllUserCardsAndChildRowsByUserIdWithoutForeignKeyViolation() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, true, 1);
        insertUserCard(ANOTHER_USER_CARD_ID, ANOTHER_CARD_KEY_HASH, false, 2);
        String optionGroupId = "01980d6a-5c0c-7aaf-9b85-010203040801";
        String optionChoiceId = "01980d6a-5c0c-7aaf-9b85-010203040802";
        String approvalId = "01980d6a-5c0c-7aaf-9b85-010203040703";
        String benefitId = "01980d6a-5c0c-7aaf-9b85-010203040805";
        String offerId = "01980d6a-5c0c-7aaf-9b85-010203040806";
        String ruleId = "01980d6a-5c0c-7aaf-9b85-010203040807";
        jdbcTemplate.update("INSERT INTO card_option_groups "
                        + "(option_group_id, card_id, group_key, group_name, "
                        + "selection_required, created_at, updated_at) "
                        + "VALUES (?, ?, 'brand', '브랜드', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                optionGroupId, CARD_ID);
        jdbcTemplate.update("INSERT INTO card_option_choices "
                        + "(option_choice_id, option_group_id, choice_key, choice_name, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 'visa', 'VISA', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                optionChoiceId, optionGroupId);
        jdbcTemplate.update("INSERT INTO user_card_option_selections "
                        + "(user_card_id, option_group_id, card_id, option_choice_id, selected_at) "
                        + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))",
                USER_CARD_ID, optionGroupId, CARD_ID, optionChoiceId);
        jdbcTemplate.update("INSERT INTO user_card_performance_snapshots "
                        + "(performance_snapshot_id, user_card_id, performance_month, current_spend_amount, "
                        + "updated_at, created_at) "
                        + "VALUES (?, ?, '2026-08', 100000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040704", USER_CARD_ID);
        jdbcTemplate.update("INSERT INTO card_payment_approvals "
                        + "(approval_id, user_id, user_card_id, approval_number, approved_at, "
                        + "merchant_name, amount, approval_status, source_payload, created_at) "
                        + "VALUES (?, ?, ?, 'A-1', UTC_TIMESTAMP(6), '테스트가맹점', 10000, 'approved', "
                        + "JSON_OBJECT(), UTC_TIMESTAMP(6))",
                approvalId, USER_ID, USER_CARD_ID);
        jdbcTemplate.update("INSERT INTO card_benefits "
                        + "(benefit_id, content_version_id, position, record_type, title, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 1, 'benefit', '카페 10% 할인', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                benefitId, CONTENT_VERSION_ID);
        jdbcTemplate.update("INSERT INTO benefit_offers "
                        + "(offer_id, benefit_id, offer_name, position, reward_type, value_type, "
                        + "calculation_mode, calculation_basis, stacking_mode, valuation_scope, "
                        + "valuation_method, created_at, updated_at) "
                        + "VALUES (?, ?, '카페 할인', 1, 'cashback', 'percentage', 'flat', "
                        + "'transaction_amount', 'standalone', 'transaction', 'direct', "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                offerId, benefitId);
        jdbcTemplate.update("INSERT INTO benefit_rules "
                        + "(rule_id, offer_id, position, rule_effect, stacking_mode, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 1, 'grant', 'standalone', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                ruleId, offerId);
        jdbcTemplate.update("INSERT INTO user_benefit_usages "
                        + "(usage_id, user_card_id, offer_id, rule_id, approval_id, usage_date, "
                        + "eligible_amount_krw, reward_amount_krw, usage_status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE(), 10000, 1000, 'confirmed', "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040808", USER_CARD_ID, offerId, ruleId, approvalId);
        jdbcTemplate.update("INSERT INTO user_benefit_calculation_outcomes "
                        + "(outcome_id, user_card_id, approval_id, offer_id, rule_id, usage_date, "
                        + "reward_unit, expected_reward_value, applied_reward_value, missed_reward_value, "
                        + "outcome_status, rejection_reason, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE(), 'KRW', 1000, 1000, 0, "
                        + "'applied', 'none', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "01980d6a-5c0c-7aaf-9b85-010203040809", USER_CARD_ID, approvalId, offerId, ruleId);

        cardQueryService.deleteAllByUserId(USER_ID);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_benefit_calculation_outcomes WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_benefit_usages WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_card_option_selections WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_card_performance_snapshots WHERE user_card_id = ?",
                Integer.class, USER_CARD_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM card_payment_approvals WHERE user_id = ?",
                Integer.class, USER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_cards WHERE user_id = ?",
                Integer.class, USER_ID));
        // codef_account_credentials는 user_cards가 참조하고 있었으므로, 위 삭제가 순서대로
        // 이뤄졌다면 이 시점에는 이미 참조가 끊겨 있어 FK 위반 없이 지울 수 있어야 한다.
        assertEquals(1, jdbcTemplate.update(
                "DELETE FROM codef_account_credentials WHERE user_id = ?", USER_ID));

        jdbcTemplate.update("DELETE FROM benefit_rules WHERE rule_id = ?", ruleId);
        jdbcTemplate.update("DELETE FROM benefit_offers WHERE offer_id = ?", offerId);
        jdbcTemplate.update("DELETE FROM card_benefits WHERE benefit_id = ?", benefitId);
    }

    @Test
    @DisplayName("존재하지 않는 보유 카드를 연결 해제하려 하면 예외를 던진다")
    void disconnectingMissingUserCardThrows() {
        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.disconnectCard(USER_ID, USER_CARD_ID));
    }

    @Test
    @DisplayName("보유 카드를 비활성화하면 is_active가 false로 바뀌고 목록 조회에서 비활성 카드로 잡힌다")
    void deactivatesUserCard() {
        insertUserCard(USER_CARD_ID, CARD_KEY_HASH, true, 1);

        cardQueryService.deactivateCard(USER_ID, USER_CARD_ID);

        assertFalse(jdbcTemplate.queryForObject(
                "SELECT is_active FROM user_cards WHERE user_card_id = ?",
                Boolean.class, USER_CARD_ID));
        assertTrue(userCardMapper.findActiveByUserId(USER_ID).isEmpty());
        assertEquals(1, userCardMapper.findInactiveByUserId(USER_ID).size());
    }

    @Test
    @DisplayName("존재하지 않는 보유 카드를 비활성화하려 하면 예외를 던진다")
    void deactivatingMissingUserCardThrows() {
        assertThrows(UserCardNotFoundException.class,
                () -> cardQueryService.deactivateCard(USER_ID, USER_CARD_ID));
    }

    private void insertUserCard(String userCardId, String cardKeyHash, boolean active, int displayOrder) {
        jdbcTemplate.update("INSERT INTO user_cards "
                        + "(user_card_id, user_id, card_id, codef_account_credential_id, "
                        + "card_name_from_codef, issuer_id, "
                        + "display_order, is_active, codef_card_key_hash, memo, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                userCardId, USER_ID, CARD_ID, CODEF_ACCOUNT_CREDENTIAL_ID, "KB My WE:SH",
                ISSUER_ID, displayOrder, active, cardKeyHash);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM user_benefit_calculation_outcomes");
        jdbcTemplate.update("DELETE FROM user_benefit_usages");
        jdbcTemplate.update("DELETE FROM benefit_rules");
        jdbcTemplate.update("DELETE FROM benefit_offers");
        jdbcTemplate.update("DELETE FROM card_benefits");
        jdbcTemplate.update("DELETE FROM card_payment_approvals");
        jdbcTemplate.update("DELETE FROM user_card_performance_snapshots");
        jdbcTemplate.update("DELETE FROM user_card_option_selections");
        jdbcTemplate.update("DELETE FROM card_option_choices");
        jdbcTemplate.update("DELETE FROM card_option_groups");
        jdbcTemplate.update("DELETE FROM user_cards");
        jdbcTemplate.update("DELETE FROM codef_account_credentials");
        jdbcTemplate.update("DELETE FROM card_content_versions");
        jdbcTemplate.update("DELETE FROM cards");
        jdbcTemplate.update("DELETE FROM issuers");
        jdbcTemplate.update("DELETE FROM user_notification_settings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackages = "com.moca.mocabe.domain.card.mapper", sqlSessionFactoryRef = "testSqlSessionFactory")
    static class CardTestConfig {

        @Bean
        CardQueryService cardQueryService(UserCardMapper userCardMapper, CardBenefitMapper cardBenefitMapper) {
            return new CardQueryService(userCardMapper, cardBenefitMapper);
        }
    }
}
