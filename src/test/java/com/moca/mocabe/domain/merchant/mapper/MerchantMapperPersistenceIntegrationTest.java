package com.moca.mocabe.domain.merchant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

/**
 * 가맹점 접두사 매칭 SQL을 실제 MySQL로 검증한다.
 *
 * 저장 가맹점명이 승인 가맹점명의 접두사인지(LIKE CONCAT), 가장 긴 접두사 우선인지(CHAR_LENGTH),
 * merchants가 alias보다 우선인지, 활성 가맹점만 매칭하는지 등 SQL에 담긴 규칙을 확인한다.
 */
@Tag("integration")
@SpringJUnitConfig(MerchantMapperPersistenceIntegrationTest.MerchantTestConfig.class)
class MerchantMapperPersistenceIntegrationTest {

    private static final String CATEGORY_ID = "c0000000-0000-4000-8000-000000000001";
    private static final String MEGA_ID = "10000000-0000-4000-8000-000000000001";
    private static final String MEGA_COFFEE_ID = "10000000-0000-4000-8000-000000000002";
    private static final String INACTIVE_ID = "10000000-0000-4000-8000-000000000003";
    private static final String ALIAS_MERCHANT_ID = "10000000-0000-4000-8000-000000000004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MerchantMapper merchantMapper;

    @BeforeEach
    void setUp() {
        deleteTestData();
        jdbcTemplate.update("INSERT INTO merchant_categories "
                        + "(merchant_category_id, category_name, display_order, created_at, updated_at) "
                        + "VALUES (?, '미분류', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                CATEGORY_ID);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("저장명이 승인명의 접두사인 활성 가맹점 중 가장 긴 것을 반환한다")
    void matchesLongestActivePrefix() {
        insertMerchant(MEGA_ID, "메가", "메가", "active");
        insertMerchant(MEGA_COFFEE_ID, "메가커피", "메가커피", "active");

        assertEquals(MEGA_COFFEE_ID,
                merchantMapper.findMerchantIdByNamePrefix("메가커피어린이대공원역점"));
    }

    @Test
    @DisplayName("접두사가 아니라 꼬리에 들어있는 가맹점명은 매칭하지 않는다")
    void doesNotMatchWhenNameIsInTail() {
        // 승인명 "메가커피어린이대공원역점"의 꼬리에 '어린이대공원'이 있어도 접두사가 아니므로 탈락한다.
        insertMerchant(MEGA_ID, "어린이대공원", "어린이대공원", "active");

        assertNull(merchantMapper.findMerchantIdByNamePrefix("메가커피어린이대공원역점"));
    }

    @Test
    @DisplayName("비활성 가맹점은 접두사가 더 길어도 매칭하지 않는다")
    void ignoresInactiveMerchant() {
        insertMerchant(MEGA_ID, "메가", "메가", "active");
        insertMerchant(INACTIVE_ID, "메가커피", "메가커피", "inactive");

        assertEquals(MEGA_ID, merchantMapper.findMerchantIdByNamePrefix("메가커피강남"));
    }

    @Test
    @DisplayName("2글자 짧은 브랜드도 접두사로 매칭된다")
    void matchesShortBrandPrefix() {
        insertMerchant(MEGA_ID, "CU", "CU", "active");

        assertEquals(MEGA_ID, merchantMapper.findMerchantIdByNamePrefix("CU강남역점"));
    }

    @Test
    @DisplayName("일치하는 활성 가맹점이 없으면 null을 반환한다")
    void returnsNullWhenNoPrefixMatch() {
        insertMerchant(MEGA_ID, "스타벅스", "스타벅스", "active");

        assertNull(merchantMapper.findMerchantIdByNamePrefix("메가커피강남"));
    }

    @Test
    @DisplayName("별칭도 접두사·가장 긴 것 규칙으로 매칭하고 활성 가맹점만 대상이다")
    void matchesLongestActiveAliasPrefix() {
        insertMerchant(ALIAS_MERCHANT_ID, "메가커피", "메가커피", "active");
        insertAlias("a0000000-0000-4000-8000-000000000001", ALIAS_MERCHANT_ID, "메가엠지씨MGC커피");
        insertAlias("a0000000-0000-4000-8000-000000000002", ALIAS_MERCHANT_ID, "메가엠지씨");

        assertEquals(ALIAS_MERCHANT_ID,
                merchantMapper.findMerchantIdByAliasPrefix("메가엠지씨MGC커피역삼점"));
    }

    @Test
    @DisplayName("별칭이 없으면 null을 반환한다")
    void returnsNullWhenNoAliasMatch() {
        insertMerchant(ALIAS_MERCHANT_ID, "메가커피", "메가커피", "active");

        assertNull(merchantMapper.findMerchantIdByAliasPrefix("스타벅스역삼"));
    }

    private void insertMerchant(String merchantId, String name, String normalizedName, String status) {
        jdbcTemplate.update("INSERT INTO merchants "
                        + "(merchant_id, merchant_category_id, name, normalized_name, status, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                merchantId, CATEGORY_ID, name, normalizedName, status);
    }

    private void insertAlias(String aliasId, String merchantId, String normalizedAlias) {
        jdbcTemplate.update("INSERT INTO merchant_aliases "
                        + "(merchant_alias_id, merchant_id, alias_name, normalized_alias_name, "
                        + "source_type, created_at) "
                        + "VALUES (?, ?, ?, ?, 'manual', UTC_TIMESTAMP(6))",
                aliasId, merchantId, normalizedAlias, normalizedAlias);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM merchant_aliases");
        jdbcTemplate.update("DELETE FROM card_payment_approvals");
        jdbcTemplate.update("DELETE FROM merchants");
        jdbcTemplate.update("DELETE FROM merchant_categories");
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackageClasses = MerchantMapper.class, sqlSessionFactoryRef = "testSqlSessionFactory")
    static class MerchantTestConfig {

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
