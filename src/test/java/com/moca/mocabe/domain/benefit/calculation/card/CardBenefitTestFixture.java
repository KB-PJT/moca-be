package com.moca.mocabe.domain.benefit.calculation.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.benefit.calculation.BenefitCalculator;
import com.moca.mocabe.domain.benefit.calculation.PromotionBenefitCalculator;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationContext;
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;

/**
 * 카드고릴라 200개 카드의 카드별 명시적 테스트에서 공통으로 사용하는 생성 및 검증 도우미다.
 */
public final class CardBenefitTestFixture {

    private static final String RESOURCE_PATH = "/benefit/card-benefit-detail-cases-1206.json";
    private static final Map<String, JsonNode> SOURCE_DETAILS = readSourceDetails();

    public final BenefitCalculator calculator = new PromotionBenefitCalculator();

    /**
     * 구조화한 카드 혜택 값을 계산기가 사용하는 룰 모델로 변환한다.
     *
     * <p>금액은 부동소수점 오차를 피하기 위해 문자열로 받아 {@link BigDecimal}로 변환한다.
     */
    public BenefitRule rule(String ruleId, BenefitType benefitType, BenefitBasis benefitBasis,
            RewardUnit rewardUnit, String rewardRate, String rewardValue, String spendUnitAmount,
            String maximumBenefitBaseAmount, String minimumPaymentAmount, String requiredPreviousMonthSpend,
            String monthlyLimitValue, String usedMonthlyValue, BenefitPromotionCondition promotionCondition,
            String category, int dailyUsageLimit, int monthlyUsageLimit, boolean merchantEligibilityRequired,
            boolean paymentChannelEligibilityRequired) {
        Set<String> categories = category.isBlank() ? Set.of() : Set.of(category);
        return new BenefitRule(ruleId, benefitType, benefitBasis, rewardUnit, value(rewardRate), value(rewardValue),
                value(spendUnitAmount), value(maximumBenefitBaseAmount), value(minimumPaymentAmount),
                value(requiredPreviousMonthSpend), value(monthlyLimitValue), value(usedMonthlyValue),
                promotionCondition, categories, dailyUsageLimit, monthlyUsageLimit, merchantEligibilityRequired,
                paymentChannelEligibilityRequired);
    }

    /**
     * 횟수·가맹점·채널에 별도 경계조건이 없는 일반적인 결제 상황을 만든다.
     */
    public BenefitCalculationContext context(String paymentAmount, String usageQuantity,
            String previousMonthSpend, String approvedAt, String category) {
        return context(paymentAmount, usageQuantity, previousMonthSpend, approvedAt, category,
                false, 0, 0, true, true);
    }

    /**
     * 신규 발급 유예, 사용 횟수, 가맹점과 결제 채널 적격 여부까지 포함한 결제 상황을 만든다.
     */
    public BenefitCalculationContext context(String paymentAmount, String usageQuantity,
            String previousMonthSpend, String approvedAt, String category, boolean newMemberGracePeriod,
            int usedDailyCount, int usedMonthlyCount, boolean merchantEligible, boolean paymentChannelEligible) {
        return new BenefitCalculationContext(value(paymentAmount), value(usageQuantity), value(previousMonthSpend),
                LocalDateTime.parse(approvedAt), category, newMemberGracePeriod, usedDailyCount, usedMonthlyCount,
                merchantEligible, paymentChannelEligible);
    }

    /**
     * 동일한 혜택 룰에서 이번 달에 이미 사용한 혜택값만 바꿔 월 한도 경계를 검증한다.
     */
    public BenefitRule withUsedMonthlyValue(BenefitRule rule, String usedMonthlyValue) {
        return new BenefitRule(rule.ruleId(), rule.benefitType(), rule.benefitBasis(), rule.rewardUnit(),
                rule.rewardRate(), rule.rewardValue(), rule.spendUnitAmount(), rule.maximumBenefitBaseAmount(),
                rule.minimumPaymentAmount(), rule.requiredPreviousMonthSpend(), rule.monthlyLimitValue(),
                value(usedMonthlyValue), rule.promotionCondition(), rule.mocaCategories(), rule.dailyUsageLimit(),
                rule.monthlyUsageLimit(), rule.merchantEligibilityRequired(),
                rule.paymentChannelEligibilityRequired());
    }

    /**
     * 테스트에 적은 카드 ID와 혜택 순번이 원본 상세의 제목·지원 상태·분류 사유와 같은지 확인한다.
     */
    public void assertSourceDetail(String cardId, int benefitIndex, String expectedTitle,
            String expectedMode, String expectedClassificationReason) {
        JsonNode detail = SOURCE_DETAILS.get(sourceKey(cardId, benefitIndex));

        assertNotNull(detail, "카드 " + cardId + "의 benefitIndex=" + benefitIndex + " 상세를 찾을 수 없다.");
        assertEquals(expectedTitle, detail.path("benefitTitle").asText());
        assertEquals(expectedMode, detail.path("mode").asText());
        assertEquals(expectedClassificationReason, detail.path("classificationReason").asText());
        assertFalse(detail.path("sourceUrl").asText().isBlank());
    }

    /**
     * 혜택이 정상 적용됐고 미적용 사유가 없음을 확인한다.
     */
    public void assertApplied(BenefitCalculationResult result) {
        assertTrue(result.applicable());
        assertEquals(BenefitRejectionReason.NONE, result.rejectionReason());
    }

    /**
     * 정상 적용 여부와 월 한도 반영 전·후 혜택값, 계산 후 남은 월 한도를 함께 확인한다.
     */
    public void assertApplied(BenefitCalculationResult result, String rawRewardValue,
            String appliedRewardValue, String remainingLimitValue) {
        assertApplied(result);
        assertBigDecimalEquals(rawRewardValue, result.rawRewardValue());
        assertBigDecimalEquals(appliedRewardValue, result.appliedRewardValue());
        assertBigDecimalEquals(remainingLimitValue, result.remainingLimitValue());
    }

    /**
     * 혜택이 적용되지 않았고 계산기가 예상한 구체적인 미적용 사유를 반환했는지 확인한다.
     */
    public void assertRejected(BenefitCalculationResult result, BenefitRejectionReason rejectionReason) {
        assertFalse(result.applicable());
        assertEquals(rejectionReason, result.rejectionReason());
    }

    public void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, value(expected).compareTo(actual));
    }

    public void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    public BigDecimal value(String number) {
        return new BigDecimal(number);
    }

    private static Map<String, JsonNode> readSourceDetails() {
        try (InputStream input = CardBenefitTestFixture.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException(RESOURCE_PATH + " 테스트 fixture를 찾을 수 없다.");
            }
            JsonNode root = new ObjectMapper().readTree(input);
            Map<String, JsonNode> details = new HashMap<>();
            root.path("benefits").forEach(detail -> details.put(
                    sourceKey(detail.path("cardId").asText(), detail.path("benefitIndex").asInt()), detail));
            return Map.copyOf(details);
        } catch (IOException exception) {
            throw new IllegalStateException("카드 혜택 상세 fixture를 읽지 못했다.", exception);
        }
    }

    private static String sourceKey(String cardId, int benefitIndex) {
        return cardId + ":" + benefitIndex;
    }
}
