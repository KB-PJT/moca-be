package com.moca.mocabe.domain.merchant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import com.moca.mocabe.global.config.TestcontainersMySqlConfig;
import java.util.List;
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
 * 가맹점 후보 조회 매퍼를 실제 MySQL로 검증한다.
 *
 * 접두사 판정·최장일치·동률 해소 같은 매칭 규칙은 MerchantLookup(도메인 계층)에서 검증하고,
 * 여기서는 활성 가맹점/별칭 후보를 빠짐없이·정확한 컬럼으로 가져오는지만 확인한다.
 */
@Tag("integration")
@SpringJUnitConfig(MerchantMapperPersistenceIntegrationTest.MerchantTestConfig.class)
class MerchantMapperPersistenceIntegrationTest {

    private static final String CATEGORY_ID = "c0000000-0000-4000-8000-000000000001";
    private static final String OTHER_CATEGORY_ID = "c0000000-0000-4000-8000-000000000002";
    private static final String MEGA_ID = "10000000-0000-4000-8000-000000000001";
    private static final String MEGA_COFFEE_ID = "10000000-0000-4000-8000-000000000002";
    private static final String INACTIVE_ID = "10000000-0000-4000-8000-000000000003";
    private static final String OTHER_CATEGORY_MERCHANT_ID = "10000000-0000-4000-8000-000000000004";

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
        jdbcTemplate.update("INSERT INTO merchant_categories "
                        + "(merchant_category_id, category_name, display_order, created_at, updated_at) "
                        + "VALUES (?, '기타', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                OTHER_CATEGORY_ID);
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("활성 가맹점의 (merchant_id, normalized_name) 후보를 모두 반환한다")
    void findsActiveMerchantNameCandidates() {
        insertMerchant(MEGA_ID, "메가", "메가", "active");
        insertMerchant(MEGA_COFFEE_ID, "메가커피", "메가커피", "active");
        insertMerchant(INACTIVE_ID, "폐업가맹점", "폐업가맹점", "inactive");

        List<MerchantNameCandidate> candidates = merchantMapper.findActiveMerchantNameCandidates();

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(
                c -> MEGA_ID.equals(c.merchantId()) && "메가".equals(c.normalizedName())));
        assertTrue(candidates.stream().anyMatch(
                c -> MEGA_COFFEE_ID.equals(c.merchantId()) && "메가커피".equals(c.normalizedName())));
    }

    @Test
    @DisplayName("비활성 가맹점은 후보에서 제외한다")
    void excludesInactiveMerchantsFromNameCandidates() {
        insertMerchant(INACTIVE_ID, "폐업가맹점", "폐업가맹점", "inactive");

        assertEquals(0, merchantMapper.findActiveMerchantNameCandidates().size());
    }

    @Test
    @DisplayName("활성 가맹점의 별칭 (merchant_id, normalized_alias_name) 후보를 반환한다")
    void findsActiveMerchantAliasCandidates() {
        insertMerchant(MEGA_ID, "메가커피", "메가커피", "active");
        insertAlias("a0000000-0000-4000-8000-000000000001", MEGA_ID, "메가엠지씨MGC커피");

        List<MerchantNameCandidate> candidates = merchantMapper.findActiveMerchantAliasCandidates();

        assertEquals(1, candidates.size());
        assertEquals(MEGA_ID, candidates.get(0).merchantId());
        assertEquals("메가엠지씨MGC커피", candidates.get(0).normalizedName());
    }

    @Test
    @DisplayName("비활성 가맹점에 속한 별칭은 후보에서 제외한다")
    void excludesAliasesOfInactiveMerchants() {
        insertMerchant(INACTIVE_ID, "폐업가맹점", "폐업가맹점", "inactive");
        insertAlias("a0000000-0000-4000-8000-000000000002", INACTIVE_ID, "폐업가맹점별칭");

        assertEquals(0, merchantMapper.findActiveMerchantAliasCandidates().size());
    }

    @Test
    @DisplayName("카테고리에 속한 활성 가맹점을 이름 순으로 반환한다")
    void findsActiveMerchantsByCategoryId() {
        insertMerchant(MEGA_COFFEE_ID, "메가커피", "메가커피", "active");
        insertMerchant(MEGA_ID, "매머드커피", "매머드커피", "active");
        insertMerchant(INACTIVE_ID, "폐업가맹점", "폐업가맹점", "inactive");
        insertMerchant(OTHER_CATEGORY_MERCHANT_ID, OTHER_CATEGORY_ID, "이마트", "이마트", "active");

        List<MerchantListRow> rows = merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID);

        assertEquals(2, rows.size());
        assertEquals("매머드커피", rows.get(0).name());
        assertEquals(MEGA_ID, rows.get(0).merchantId());
        assertEquals("메가커피", rows.get(1).name());
    }

    @Test
    @DisplayName("해당 카테고리에 활성 가맹점이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoActiveMerchantsInCategory() {
        insertMerchant(INACTIVE_ID, "폐업가맹점", "폐업가맹점", "inactive");

        assertEquals(0, merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID).size());
    }

    private void insertMerchant(String merchantId, String name, String normalizedName, String status) {
        insertMerchant(merchantId, CATEGORY_ID, name, normalizedName, status);
    }

    private void insertMerchant(String merchantId, String categoryId, String name, String normalizedName,
                                String status) {
        jdbcTemplate.update("INSERT INTO merchants "
                        + "(merchant_id, merchant_category_id, name, normalized_name, status, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                merchantId, categoryId, name, normalizedName, status);
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
