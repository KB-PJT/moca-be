package com.moca.mocabe.domain.merchant.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
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

/** merchant_categories 조회 매퍼를 실제 MySQL로 검증한다. */
@Tag("integration")
@SpringJUnitConfig(MerchantCategoryMapperPersistenceIntegrationTest.MerchantCategoryTestConfig.class)
class MerchantCategoryMapperPersistenceIntegrationTest {

    private static final String CAFE_ID = "c0000000-0000-4000-8000-000000000001";
    private static final String MART_ID = "c0000000-0000-4000-8000-000000000003";
    private static final String SIMPLEPAY_ID = "c0000000-0000-4000-8000-000000000004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MerchantCategoryMapper merchantCategoryMapper;

    @BeforeEach
    void setUp() {
        deleteTestData();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    @DisplayName("is_map_visible=TRUE인 카테고리만 display_order 순으로 조회한다")
    void findsMapVisibleCategories() {
        insertCategory(MART_ID, "MART", "대형마트", 1, true);
        insertCategory(CAFE_ID, "CAFE", "카페", 2, true);
        insertCategory(SIMPLEPAY_ID, "SIMPLEPAY", "간편결제", 3, false);

        List<MerchantCategoryRow> rows = merchantCategoryMapper.findAllOrderedByDisplayOrder();
        List<MerchantCategoryRow> testRows = rows.stream()
                .filter(row -> List.of(MART_ID, CAFE_ID, SIMPLEPAY_ID).contains(row.merchantCategoryId()))
                .toList();

        assertEquals(2, testRows.size());
        assertEquals(MART_ID, testRows.get(0).merchantCategoryId());
        assertEquals("MART", testRows.get(0).categoryCode());
        assertEquals("대형마트", testRows.get(0).categoryName());
        assertEquals(1, testRows.get(0).displayOrder());
        assertEquals(CAFE_ID, testRows.get(1).merchantCategoryId());
        assertEquals("CAFE", testRows.get(1).categoryCode());
    }

    @Test
    @DisplayName("테스트 카테고리가 없으면 조회 결과에 포함하지 않는다")
    void excludesMissingTestCategories() {
        assertEquals(0, merchantCategoryMapper.findAllOrderedByDisplayOrder().stream()
                .filter(row -> List.of(MART_ID, CAFE_ID, SIMPLEPAY_ID).contains(row.merchantCategoryId()))
                .count());
    }

    @Test
    @DisplayName("활성 카카오 그룹코드를 priority 순으로 조회한다")
    void findsEnabledKakaoGroupCodes() {
        insertCategory(MART_ID, "MART", "대형마트", 1, true);
        insertKakaoCategoryMap(MART_ID, "PM9", 2, true);
        insertKakaoCategoryMap(MART_ID, "HP8", 1, true);
        insertKakaoCategoryMap(MART_ID, "MT1", 3, false);

        List<String> groupCodes = merchantCategoryMapper.findEnabledKakaoGroupCodes(MART_ID);

        assertEquals(List.of("HP8", "PM9"), groupCodes);
    }

    @Test
    @DisplayName("활성 그룹코드 매핑이 없으면 빈 목록을 반환한다(2안 대상)")
    void returnsEmptyGroupCodesWhenNotMapped() {
        insertCategory(CAFE_ID, "CAFE", "카페", 1, true);

        assertEquals(0, merchantCategoryMapper.findEnabledKakaoGroupCodes(CAFE_ID).size());
    }

    @Test
    @DisplayName("is_map_visible=TRUE인 카테고리는 존재하는 것으로 판단한다")
    void existsMapVisibleCategoryReturnsTrue() {
        insertCategory(CAFE_ID, "CAFE", "카페", 1, true);

        assertEquals(true, merchantCategoryMapper.existsMapVisibleCategory(CAFE_ID));
    }

    @Test
    @DisplayName("is_map_visible=FALSE이거나 존재하지 않는 카테고리는 존재하지 않는 것으로 판단한다")
    void existsMapVisibleCategoryReturnsFalse() {
        insertCategory(SIMPLEPAY_ID, "SIMPLEPAY", "간편결제", 1, false);

        assertEquals(false, merchantCategoryMapper.existsMapVisibleCategory(SIMPLEPAY_ID));
        assertEquals(false, merchantCategoryMapper.existsMapVisibleCategory("no-such-id"));
    }

    private void insertKakaoCategoryMap(String categoryId, String groupCode, int priority, boolean enabled) {
        jdbcTemplate.update("INSERT INTO kakao_category_maps "
                        + "(kakao_category_map_id, merchant_category_id, kakao_category_group_code, "
                        + "kakao_category_name_pattern, priority, enabled, created_at, updated_at) "
                        + "VALUES (UUID(), ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                categoryId, groupCode, groupCode, priority, enabled);
    }

    private void insertCategory(String id, String code, String name, int displayOrder, boolean mapVisible) {
        jdbcTemplate.update("INSERT INTO merchant_categories "
                        + "(merchant_category_id, category_code, category_name, display_order, is_map_visible, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                id, code, name, displayOrder, mapVisible);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM kakao_category_maps WHERE merchant_category_id IN (?, ?, ?)",
                CAFE_ID, MART_ID, SIMPLEPAY_ID);
        jdbcTemplate.update("DELETE FROM merchant_categories WHERE merchant_category_id IN (?, ?, ?)",
                CAFE_ID, MART_ID, SIMPLEPAY_ID);
    }

    @Configuration
    @Import(TestcontainersMySqlConfig.class)
    @MapperScan(basePackageClasses = MerchantCategoryMapper.class, sqlSessionFactoryRef = "testSqlSessionFactory")
    static class MerchantCategoryTestConfig {

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
