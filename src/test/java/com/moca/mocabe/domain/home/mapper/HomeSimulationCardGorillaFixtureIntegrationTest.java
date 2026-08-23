package com.moca.mocabe.domain.home.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.home.dto.HomeCardResponse;
import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.domain.report.mapper.ReportMapper;
import com.moca.mocabe.domain.report.model.MissedBenefitRow;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("홈 카드고릴라 시뮬레이션 fixture")
class HomeSimulationCardGorillaFixtureIntegrationTest {

    private static final String FIXTURE_PATH =
            "db/fixture/home-simulation-cardgorilla.sql";
    private static final String USER_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TAPTAP_USER_CARD_ID =
            "20000000-0000-0000-0000-000000000003";
    private static final String SELECT_UP_USER_CARD_ID =
            "20000000-0000-0000-0000-000000000004";
    private static final String LOCAL_USER_ID = "37411c29-5adc-4643-8ca5-8fa1c14abf1d";
    private static final String REWARD_VALUE_EXPRESSION =
            "CASE WHEN reward_original_unit = 'point' THEN reward_original_value "
                    + "ELSE reward_amount_krw END";

    private MySQLContainer container;
    private JdbcTemplate jdbcTemplate;
    private SqlSession sqlSession;
    private HomeMapper homeMapper;
    private ReportMapper reportMapper;
    private HomeQueryService homeQueryService;

    @BeforeAll
    void loadFixtureOnMigratedSchema() throws Exception {
        container =
                new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                        .withDatabaseName("moca_home_simulation_test")
                        .withUsername("moca")
                        .withPassword("moca")
                        .withStartupTimeout(Duration.ofMinutes(3));
        container.start();

        DataSource dataSource = dataSource(container);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        new ResourceDatabasePopulator(new ClassPathResource(FIXTURE_PATH)).execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSession = sqlSessionFactory.openSession();
        homeMapper = sqlSession.getMapper(HomeMapper.class);
        reportMapper = sqlSession.getMapper(ReportMapper.class);
        homeQueryService =
            new HomeQueryService(
                sqlSession.getMapper(UserMapper.class),
                homeMapper,
                sqlSession.getMapper(com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper.class));
    }

    @AfterAll
    void stopContainer() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("전체 migration을 적용한 MySQL 스키마에 fixture를 적재한다")
    void loadsFixtureAgainstCurrentSchema() throws Exception {
        Integer migrationCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                        Integer.class);
        Integer expectedMigrationCount =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/migration/V*.sql")
                        .length;
        Integer userCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, USER_ID);
        Integer cardCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_cards WHERE user_id = ?", Integer.class, USER_ID);

        // V21~V23(혜택 구조화)이 추가된 뒤에도 매번 하드코딩한 카운트를 갱신하지 않도록
        // classpath의 실제 V*.sql 파일 수와 비교한다.
        assertEquals(expectedMigrationCount, migrationCount);
        assertEquals(1, userCount);
        assertEquals(4, cardCount);
    }

    @Test
    @DisplayName("카드별 8월 사용액과 실제 혜택 및 실적을 집계한다")
    void aggregatesMonthlyCardSummaries() {
        Map<String, CardSummary> summaries = cardSummaries();

        assertCardSummary(summaries.get("2441"), "192300", "7640", "192300");
        assertCardSummary(summaries.get("13"), "197700", "10420", "197700");
        assertCardSummary(summaries.get("51"), "269900", "10000", "269900");
        assertCardSummary(summaries.get("2986"), "0", "0", "0");
    }

    @Test
    @DisplayName("홈 전체 8월 사용액과 실제 혜택 및 거래별 미적용 예상 혜택을 집계한다")
    void aggregatesHomeMonthlyTotals() {
        BigDecimal totalSpend =
                queryAmount(
                        "SELECT SUM(amount) FROM card_payment_approvals "
                                + "WHERE user_id = ? AND approved_at >= '2026-08-01' "
                                + "AND approved_at < '2026-09-01'",
                        USER_ID);
        BigDecimal receivedBenefit =
                queryAmount(
                        "SELECT SUM(" + REWARD_VALUE_EXPRESSION + ") "
                                + "FROM user_benefit_usages benefit_usage "
                                + "INNER JOIN user_cards card "
                                + "ON card.user_card_id = benefit_usage.user_card_id "
                                + "WHERE card.user_id = ? AND benefit_usage.usage_date >= '2026-08-01' "
                                + "AND benefit_usage.usage_date < '2026-09-01' "
                                + "AND benefit_usage.usage_status IN ('pending', 'confirmed')",
                        USER_ID);
        BigDecimal unappliedBenefit =
                queryAmount(
                        "SELECT SUM(outcome.missed_reward_value) "
                                + "FROM user_benefit_calculation_outcomes outcome "
                                + "INNER JOIN user_cards card ON card.user_card_id = outcome.user_card_id "
                                + "WHERE card.user_id = ? AND outcome.usage_date >= '2026-08-01' "
                                + "AND outcome.usage_date < '2026-09-01'",
                        USER_ID);

        assertAmount("659900", totalSpend);
        assertAmount("28060", receivedBenefit);
        assertAmount("15000", unappliedBenefit);
    }

    @Test
    @DisplayName("카드별 월 한도 합계에서 수령액을 차감하고 음수 잔여 혜택은 0으로 표시한다")
    void calculatesMaximumAndAvailableBenefitsFromAllMonthlyLimits() {
        HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-08", "MANUAL");
        Map<String, HomeCardResponse> cards = new LinkedHashMap<>();
        response.getCards().forEach(card -> cards.put(card.getCardName(), card));

        assertBenefitSummary(cards.get("KB국민 My WE:SH 카드"), 7_640, 0, 0);
        assertBenefitSummary(cards.get("신한카드 Mr.Life"), 10_420, 0, 0);
        assertBenefitSummary(cards.get("삼성카드 taptap O"), 10_000, 25_000, 15_000);
        assertBenefitSummary(cards.get("삼성 iD SELECT UP 카드"), 0, 510_000, 510_000);
    }

    @Test
    @DisplayName("선택한 taptap O 패키지 1의 미사용 커피·쇼핑 한도 1만 5천원을 놓친 혜택으로 집계한다")
    void aggregatesUnusedSelectedPackageLimitsAsMissedBenefit() {
        Integer selectionCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_card_option_selections "
                                + "WHERE user_card_id = ? AND option_choice_id = "
                                + "'46100000-0000-0000-0000-000000000001'",
                        Integer.class,
                        TAPTAP_USER_CARD_ID);

        assertEquals(1, selectionCount);
        Map<String, MissedBenefitRow> missedBenefits =
                reportMapper
                        .findMonthlyRemainingBenefits(USER_ID, TAPTAP_USER_CARD_ID, "2026-08")
                        .stream()
                        .filter(row -> row.limitAmount() > row.usedAmount())
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        MissedBenefitRow::title,
                                        row -> row,
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        assertEquals(2, missedBenefits.size());
        assertRemainingBenefit(
                missedBenefits.get("패키지 1 스타벅스 할인"), 0, 10_000);
        assertRemainingBenefit(
                missedBenefits.get("패키지 1 오픈마켓 할인"), 0, 5_000);
    }

    @Test
    @DisplayName("iD SELECT UP은 기본 여가와 선택한 의료 한도를 놓친 혜택으로 집계한다")
    void aggregatesIdSelectUpLeisureAndSelectedMedicalLimitsAsMissedBenefit() {
        Integer medicalSelectionCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_card_option_selections "
                                + "WHERE user_card_id = ? AND option_choice_id = "
                                + "'46100000-0000-0000-0000-000000000007'",
                        Integer.class,
                        SELECT_UP_USER_CARD_ID);

        List<MissedBenefitRow> missedBenefits =
                reportMapper.findMonthlyRemainingBenefits(
                        USER_ID, SELECT_UP_USER_CARD_ID, "2026-08");

        assertEquals(1, medicalSelectionCount);
        Map<String, MissedBenefitRow> benefitsByTitle =
                missedBenefits.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        MissedBenefitRow::title,
                                        row -> row,
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        assertEquals(2, benefitsByTitle.size());
        assertRemainingBenefit(benefitsByTitle.get("기본 여가 할인"), 0, 500_000);
        assertRemainingBenefit(benefitsByTitle.get("SELECT 의료 할인"), 0, 10_000);
        assertEquals(525_000L, homeMapper.sumMissedBenefitAmount(USER_ID, "2026-08"));
    }

    @Test
    @DisplayName("최근 전체 이용내역은 승인시각 내림차순으로 혜택과 함께 조회한다")
    void returnsRecentTransactionsInDescendingOrder() {
        List<RecentTransaction> transactions =
                jdbcTemplate.query(
                        "SELECT approval.approval_number, approval.merchant_name, approval.amount, "
                                + "COALESCE(SUM(benefit_usage.reward_amount_krw), 0) AS benefit_amount "
                                + "FROM card_payment_approvals approval "
                                + "LEFT JOIN user_benefit_usages benefit_usage "
                                + "ON benefit_usage.approval_id = approval.approval_id "
                                + "AND benefit_usage.usage_status IN ('pending', 'confirmed') "
                                + "WHERE approval.user_id = ? GROUP BY approval.approval_id, "
                                + "approval.approval_number, approval.approved_at, "
                                + "approval.merchant_name, approval.amount "
                                + "ORDER BY approval.approved_at DESC LIMIT 10",
                        (resultSet, rowNumber) ->
                                new RecentTransaction(
                                        resultSet.getString("approval_number"),
                                        resultSet.getString("merchant_name"),
                                        resultSet.getBigDecimal("amount"),
                                        resultSet.getBigDecimal("benefit_amount")),
                        USER_ID);

        assertEquals(10, transactions.size());
        assertEquals("SS-0810-01", transactions.get(0).approvalNumber());
        assertEquals("롯데시네마 부산본점", transactions.get(0).merchantName());
        assertAmount("3000", transactions.get(0).amount());
        assertAmount("0", transactions.get(0).benefitAmount());
        assertEquals("SH-0810-01", transactions.get(1).approvalNumber());
        assertAmount("3000", transactions.get(1).benefitAmount());
    }

    @Test
    @DisplayName("taptap O는 1만원 이상 결제에 5천원 정액 할인을 적용한다")
    void appliesFixedMovieDiscountAtAndAboveThreshold() {
        Map<String, TaptapOutcome> outcomes = taptapOutcomes();

        assertOutcome(outcomes.get("fixed_amount_large_payment"), "60000", "5000", "5000", "0", "NONE");
        assertOutcome(outcomes.get("min_amount_exact"), "10000", "5000", "5000", "0", "NONE");
    }

    @Test
    @DisplayName("taptap O는 1만원 미만 결제에 영화 할인을 적용하지 않는다")
    void rejectsMoviePaymentsBelowThreshold() {
        Map<String, TaptapOutcome> outcomes = taptapOutcomes();

        assertOutcome(
                outcomes.get("min_amount_below"),
                "9900",
                "0",
                "0",
                "0",
                "MIN_TRANSACTION_NOT_MET");
        assertOutcome(
                outcomes.get("very_small_payment"),
                "3000",
                "0",
                "0",
                "0",
                "MIN_TRANSACTION_NOT_MET");
    }

    @Test
    @DisplayName("taptap O는 일 1회와 월 2회 초과 결제를 거래별 미적용 예상 혜택으로 기록한다")
    void rejectsMoviePaymentsAfterUsageLimits() {
        Map<String, TaptapOutcome> outcomes = taptapOutcomes();

        assertOutcome(
                outcomes.get("daily_limit_exceeded"),
                "20000",
                "5000",
                "0",
                "5000",
                "DAILY_USAGE_LIMIT_EXCEEDED");
        assertOutcome(
                outcomes.get("monthly_limit_exceeded"),
                "15000",
                "5000",
                "0",
                "5000",
                "MONTHLY_USAGE_LIMIT_EXCEEDED");
        assertOutcome(
                outcomes.get("large_payment_after_monthly_limit"),
                "60000",
                "5000",
                "0",
                "5000",
                "MONTHLY_USAGE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("taptap O는 예매대행 결제를 대상 가맹점 혜택에서 제외한다")
    void rejectsIneligibleMovieMerchant() {
        TaptapOutcome outcome = taptapOutcomes().get("merchant_not_eligible");

        assertOutcome(outcome, "30000", "0", "0", "0", "MERCHANT_NOT_ELIGIBLE");
    }

    @Test
    @Order(Integer.MAX_VALUE)
    @DisplayName("fixture 카드와 승인내역을 기존 로컬 사용자에게 연결한다")
    void linksSimulationFixtureToExistingLocalUser() {
        jdbcTemplate.update(
                "INSERT INTO users (user_id, google_subject, nickname, email, user_type, "
                        + "location_recommendation_enabled, card_sort_mode, created_at, updated_at) "
                        + "VALUES (?, 'local-google-subject', '한뮤', 'hanmyu31@gmail.com', "
                        + "'user', FALSE, 'AUTO', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                LOCAL_USER_ID);

        new ResourceDatabasePopulator(
                        new ClassPathResource(
                                "db/fixture/home-simulation-link-local-user.sql"))
                .execute(jdbcTemplate.getDataSource());

        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_cards WHERE user_id = ?",
                        Integer.class,
                        LOCAL_USER_ID));
        assertEquals(
                26,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM card_payment_approvals WHERE user_id = ?",
                        Integer.class,
                        LOCAL_USER_ID));
    }

    private DataSource dataSource(MySQLContainer mysqlContainer) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysqlContainer.getDriverClassName());
        dataSource.setUrl(mysqlContainer.getJdbcUrl());
        dataSource.setUsername(mysqlContainer.getUsername());
        dataSource.setPassword(mysqlContainer.getPassword());
        return dataSource;
    }

    private Map<String, CardSummary> cardSummaries() {
        Map<String, CardSummary> summaries = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT card.gorilla_card_id, COALESCE(spend.total_spend, 0) AS monthly_spend, "
                        + "COALESCE(benefit.total_benefit, 0) AS monthly_benefit, "
                        + "COALESCE(performance.current_spend_amount, 0) AS performance_amount "
                        + "FROM user_cards user_card "
                        + "INNER JOIN cards card ON card.card_id = user_card.card_id "
                        + "LEFT JOIN (SELECT user_card_id, SUM(amount) AS total_spend "
                        + "FROM card_payment_approvals WHERE approved_at >= '2026-08-01' "
                        + "AND approved_at < '2026-09-01' GROUP BY user_card_id) spend "
                        + "ON spend.user_card_id = user_card.user_card_id "
                        + "LEFT JOIN (SELECT user_card_id, SUM(" + REWARD_VALUE_EXPRESSION
                        + ") AS total_benefit "
                        + "FROM user_benefit_usages WHERE usage_date >= '2026-08-01' "
                        + "AND usage_date < '2026-09-01' AND usage_status IN ('pending', 'confirmed') "
                        + "GROUP BY user_card_id) benefit "
                        + "ON benefit.user_card_id = user_card.user_card_id "
                        + "LEFT JOIN user_card_performance_snapshots performance "
                        + "ON performance.user_card_id = user_card.user_card_id "
                        + "AND performance.performance_month = '2026-08' "
                        + "WHERE user_card.user_id = ? ORDER BY user_card.display_order",
                (RowCallbackHandler)
                        resultSet ->
                                summaries.put(
                                        resultSet.getString("gorilla_card_id"),
                                        new CardSummary(
                                                resultSet.getBigDecimal("monthly_spend"),
                                                resultSet.getBigDecimal("monthly_benefit"),
                                                resultSet.getBigDecimal("performance_amount"))),
                USER_ID);
        return summaries;
    }

    private Map<String, TaptapOutcome> taptapOutcomes() {
        Map<String, TaptapOutcome> outcomes = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(approval.source_payload, '$.scenario')) AS scenario, "
                        + "approval.amount, outcome.expected_reward_value, outcome.applied_reward_value, "
                        + "outcome.missed_reward_value, outcome.rejection_reason "
                        + "FROM card_payment_approvals approval "
                        + "INNER JOIN user_benefit_calculation_outcomes outcome "
                        + "ON outcome.approval_id = approval.approval_id "
                        + "WHERE approval.user_card_id = ? ORDER BY approval.approved_at",
                (RowCallbackHandler)
                        resultSet ->
                                outcomes.put(
                                        resultSet.getString("scenario"),
                                        new TaptapOutcome(
                                                resultSet.getBigDecimal("amount"),
                                                resultSet.getBigDecimal("expected_reward_value"),
                                                resultSet.getBigDecimal("applied_reward_value"),
                                                resultSet.getBigDecimal("missed_reward_value"),
                                                resultSet.getString("rejection_reason"))),
                TAPTAP_USER_CARD_ID);
        return outcomes;
    }

    private BigDecimal queryAmount(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
    }

    private void assertCardSummary(
            CardSummary summary, String spend, String benefit, String performance) {
        assertAmount(spend, summary.monthlySpend());
        assertAmount(benefit, summary.monthlyBenefit());
        assertAmount(performance, summary.performanceAmount());
    }

    private void assertOutcome(
            TaptapOutcome outcome,
            String paymentAmount,
            String expectedReward,
            String appliedReward,
            String missedReward,
            String rejectionReason) {
        assertAmount(paymentAmount, outcome.paymentAmount());
        assertAmount(expectedReward, outcome.expectedReward());
        assertAmount(appliedReward, outcome.appliedReward());
        assertAmount(missedReward, outcome.missedReward());
        assertEquals(rejectionReason, outcome.rejectionReason());
    }

    private void assertBenefitSummary(
            HomeCardResponse card, long received, long maximum, long available) {
        assertEquals(received, card.getSummary().getReceivedBenefitAmount());
        assertEquals(maximum, card.getSummary().getMaximumMonthlyBenefitAmount());
        assertEquals(available, card.getSummary().getAvailableBenefitAmount());
    }

    private void assertRemainingBenefit(
            MissedBenefitRow row, long expectedUsedAmount, long expectedLimitAmount) {
        assertEquals(expectedUsedAmount, row.usedAmount());
        assertEquals(expectedLimitAmount, row.limitAmount());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private record CardSummary(
            BigDecimal monthlySpend, BigDecimal monthlyBenefit, BigDecimal performanceAmount) {
    }

    private record RecentTransaction(
            String approvalNumber,
            String merchantName,
            BigDecimal amount,
            BigDecimal benefitAmount) {
    }

    private record TaptapOutcome(
            BigDecimal paymentAmount,
            BigDecimal expectedReward,
            BigDecimal appliedReward,
            BigDecimal missedReward,
            String rejectionReason) {
    }
}
