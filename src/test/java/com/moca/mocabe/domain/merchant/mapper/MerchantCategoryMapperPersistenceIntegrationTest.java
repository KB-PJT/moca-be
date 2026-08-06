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
    @DisplayName("kakao_category_maps에 활성 매핑이 있는 카테고리만 display_order 순으로 조회한다")
    void findsMappedCategories() {
        insertCategory(MART_ID, "MART", "대형마트", 1);
        insertCategory(CAFE_ID, "CAFE", "카페", 2);
        insertCategory(SIMPLEPAY_ID, "SIMPLEPAY", "간편결제", 3);
        insertKakaoCategoryMap(MART_ID, "MT1");
        insertKakaoCategoryMap(CAFE_ID, "CE7");
        // SIMPLEPAY_ID는 매핑을 넣지 않는다 = 지도 대상 아님

        List<MerchantCategoryRow> rows = merchantCategoryMapper.findAllOrderedByDisplayOrder();

        assertEquals(2, rows.size());
        assertEquals(MART_ID, rows.get(0).merchantCategoryId());
        assertEquals("MART", rows.get(0).categoryCode());
        assertEquals("대형마트", rows.get(0).categoryName());
        assertEquals(1, rows.get(0).displayOrder());
        assertEquals(CAFE_ID, rows.get(1).merchantCategoryId());
        assertEquals("CAFE", rows.get(1).categoryCode());
    }

    @Test
    @DisplayName("카테고리가 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoCategories() {
        assertEquals(0, merchantCategoryMapper.findAllOrderedByDisplayOrder().size());
    }

    @Test
    @DisplayName("kakao_category_maps 매핑이 비활성(enabled=false)이면 제외한다")
    void excludesCategoriesWithDisabledMapping() {
        insertCategory(MART_ID, "MART", "대형마트", 1);
        jdbcTemplate.update("INSERT INTO kakao_category_maps "
                        + "(kakao_category_map_id, merchant_category_id, kakao_category_group_code, "
                        + "kakao_category_name_pattern, priority, enabled, created_at, updated_at) "
                        + "VALUES (?, ?, 'MT1', '대형마트', 1, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "k0000000-0000-4000-8000-000000000001", MART_ID);

        assertEquals(0, merchantCategoryMapper.findAllOrderedByDisplayOrder().size());
    }

    private void insertCategory(String id, String code, String name, int displayOrder) {
        jdbcTemplate.update("INSERT INTO merchant_categories "
                        + "(merchant_category_id, category_code, category_name, display_order, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                id, code, name, displayOrder);
    }

    private void insertKakaoCategoryMap(String categoryId, String groupCode) {
        jdbcTemplate.update("INSERT INTO kakao_category_maps "
                        + "(kakao_category_map_id, merchant_category_id, kakao_category_group_code, "
                        + "kakao_category_name_pattern, priority, enabled, created_at, updated_at) "
                        + "VALUES (UUID(), ?, ?, ?, 1, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                categoryId, groupCode, groupCode);
    }

    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM kakao_category_maps");
        jdbcTemplate.update("DELETE FROM merchant_aliases");
        jdbcTemplate.update("DELETE FROM card_payment_approvals");
        jdbcTemplate.update("DELETE FROM merchants");
        jdbcTemplate.update("DELETE FROM merchant_categories");
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
