import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const SOURCE_PATH = path.join(
    ROOT,
    "src/test/resources/benefit/card-benefit-detail-cases-1206.json",
);
const OUTPUT_ROOT = path.join(
    ROOT,
    "src/test/java/com/moca/mocabe/domain/benefit/calculation/card/generated",
);

const CALCULABLE = "DIRECT_OFFLINE_CALCULABLE";
const data = JSON.parse(fs.readFileSync(SOURCE_PATH, "utf8"));
const cards = new Map();

for (const benefit of data.benefits) {
    const key = `${benefit.cardType}:${benefit.cardId}`;
    if (!cards.has(key)) {
        cards.set(key, []);
    }
    cards.get(key).push(benefit);
}

fs.rmSync(OUTPUT_ROOT, {recursive: true, force: true});

for (const benefits of cards.values()) {
    benefits.sort((left, right) => left.benefitIndex - right.benefitIndex);
    writeCardTest(benefits);
}

function writeCardTest(benefits) {
    const card = benefits[0];
    const kind = card.cardType === "CREDIT" ? "credit" : "check";
    const kindName = card.cardType === "CREDIT" ? "신용" : "체크";
    const rank = String(card.ranking).padStart(3, "0");
    const className = `${capitalize(kind)}Rank${rank}Card${card.cardId}BenefitTest`;
    const outputDirectory = path.join(OUTPUT_ROOT, kind);
    const outputPath = path.join(outputDirectory, `${className}.java`);
    const methods = [];
    const helpers = [];

    for (const benefit of benefits) {
        if (benefit.mode === CALCULABLE) {
            methods.push(...calculationMethods(benefit));
            helpers.push(ruleHelper(benefit));
        } else {
            methods.push(classificationMethod(benefit));
        }
    }

    const body = `${methods.join("\n\n")}

${helpers.join("\n\n")}`;
    const calculationImports = helpers.length > 0
        ? `
import com.moca.mocabe.domain.benefit.model.BenefitCalculationResult;
import com.moca.mocabe.domain.benefit.model.BenefitRule;
import com.moca.mocabe.domain.benefit.type.BenefitBasis;
import com.moca.mocabe.domain.benefit.type.BenefitPromotionCondition;
import com.moca.mocabe.domain.benefit.type.BenefitType;
import com.moca.mocabe.domain.benefit.type.RewardUnit;
`
        : "";
    const rejectionImport = body.includes("BenefitRejectionReason.")
        ? "import com.moca.mocabe.domain.benefit.type.BenefitRejectionReason;\n"
        : "";
    const categoryConstant = body.includes("NOT_MATCHED_CATEGORY")
        ? '    private static final String NOT_MATCHED_CATEGORY = "__NOT_MATCHED__";\n\n'
        : "";
    const source = `package com.moca.mocabe.domain.benefit.calculation.card.generated.${kind};

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.calculation.card.CardBenefitTestFixture;
${calculationImports}${rejectionImport}

@DisplayName("${escapeJava(`${kindName} ${card.ranking}위 ${card.cardName}`)}")
class ${className} {

${categoryConstant}\
    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

${body}
}
`;

    fs.mkdirSync(outputDirectory, {recursive: true});
    fs.writeFileSync(outputPath, source);
}

function classificationMethod(benefit) {
    const methodSuffix = benefitSuffix(benefit);
    const statusLabel = classificationStatusLabel(benefit.mode);
    const display = `${benefit.benefitTitle}: ${statusLabel} - ${shorten(benefit.classificationReason, 54)}`;

    return `    @Test
    @DisplayName("${escapeJava(display)}")
    void classifies${methodSuffix}() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "${escapeJava(benefit.cardId)}",
                /* 카드 내 혜택 순번 */ ${benefit.benefitIndex},
                /* benefit_title */ "${escapeJava(benefit.benefitTitle)}",
                /* 계산 지원 상태 */ "${escapeJava(benefit.mode)}",
                /* 분류 사유 */ "${escapeJava(benefit.classificationReason)}");
    }`;
}

function calculationMethods(benefit) {
    const methods = [];
    const suffix = benefitSuffix(benefit);
    const title = benefit.benefitTitle;
    const baseDisplay = `${title}: ${formulaLabel(benefit)} 정상 적용`;

    methods.push(`    @Test
    @DisplayName("${escapeJava(baseDisplay)}")
    void applies${suffix}() {
        // 테스트에 사용한 계산 규칙이 카드고릴라의 해당 혜택 상세에서 만들어졌는지 확인한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "${escapeJava(benefit.cardId)}",
                /* 카드 내 혜택 순번 */ ${benefit.benefitIndex},
                /* benefit_title */ "${escapeJava(title)}",
                /* 계산 지원 상태 */ "${escapeJava(benefit.mode)}",
                /* 분류 사유 */ "${escapeJava(benefit.classificationReason)}");

        // 카드 혜택 룰과 현재 결제 상황을 조합해 예상 혜택을 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit)});

        // 월 한도 반영 전 혜택, 실제 적용 혜택, 계산 후 남은 월 한도를 차례로 검증한다.
        fixture.assertApplied(
                result,
                /* 월 한도 반영 전 혜택 */ "${benefit.expectedRawRewardValue}",
                /* 실제 적용 혜택 */ "${benefit.expectedAppliedRewardValue}",
                /* 남은 월 한도 */ "${benefit.expectedRemainingLimitValue}");
    }`);

    if (benefit.category) {
        methods.push(rejectionMethod(
            benefit,
            "CategoryIsNotMatched",
            `${title}: 카테고리가 일치하지 않으면 적용하지 않는다`,
            contextCall(benefit, {category: "__NOT_MATCHED__"}),
            "CATEGORY_NOT_MATCHED",
        ));
    }

    if (positive(benefit.minimumPaymentAmount)) {
        methods.push(rejectionMethod(
            benefit,
            "PaymentIsOneWonBelowMinimum",
            `${title}: 최소 결제금액 ${won(benefit.minimumPaymentAmount)}보다 1원 적으면 적용하지 않는다`,
            contextCall(benefit, {
                paymentAmount: subtractOne(benefit.minimumPaymentAmount),
            }),
            "MIN_PAYMENT_NOT_MET",
        ));
        methods.push(applicableMethod(
            benefit,
            "PaymentEqualsMinimum",
            `${title}: 최소 결제금액 ${won(benefit.minimumPaymentAmount)}부터 적용한다`,
            contextCall(benefit, {paymentAmount: benefit.minimumPaymentAmount}),
        ));
    }

    if (positive(benefit.requiredPreviousMonthSpend)) {
        methods.push(rejectionMethod(
            benefit,
            "PerformanceIsOneWonBelowRequirement",
            `${title}: 전월 실적 ${won(benefit.requiredPreviousMonthSpend)}보다 1원 적으면 적용하지 않는다`,
            contextCall(benefit, {
                previousMonthSpend: subtractOne(benefit.requiredPreviousMonthSpend),
            }),
            "PERFORMANCE_NOT_MET",
        ));
        methods.push(applicableMethod(
            benefit,
            "PerformanceEqualsRequirement",
            `${title}: 전월 실적 ${won(benefit.requiredPreviousMonthSpend)}부터 적용한다`,
            contextCall(benefit, {
                previousMonthSpend: benefit.requiredPreviousMonthSpend,
            }),
        ));
    }

    if (benefit.newMemberGracePeriod && positive(benefit.requiredPreviousMonthSpend)) {
        methods.push(applicableMethod(
            benefit,
            "NewMemberGracePeriodIsActive",
            `${title}: 신규 발급 유예기간에는 전월 실적 0원도 적용한다`,
            contextCall(benefit, {
                previousMonthSpend: "0",
                newMemberGracePeriod: true,
            }),
        ));
    }

    if (
        benefit.rewardBasis === "RATE"
        && positive(benefit.maximumBenefitBaseAmount)
        && decimal(benefit.minimumPaymentAmount) <= decimal(benefit.maximumBenefitBaseAmount)
    ) {
        methods.push(maximumBaseMethod(benefit));
    }

    if (positive(benefit.monthlyLimitValue)) {
        methods.push(monthlyPartialMethod(benefit));
        methods.push(rejectionMethod(
            benefit,
            "MonthlyLimitIsExhausted",
            `${title}: 월 한도를 모두 사용했으면 적용하지 않는다`,
            contextCall(benefit),
            "MONTHLY_LIMIT_EXHAUSTED",
            benefit.monthlyLimitValue,
        ));
    }

    if (benefit.dailyUsageLimit > 0) {
        methods.push(applicableMethod(
            benefit,
            "DailyUsageIsLastAllowed",
            `${title}: 일 ${benefit.dailyUsageLimit}회 한도의 마지막 허용 거래는 적용한다`,
            contextCall(benefit, {usedDailyCount: benefit.dailyUsageLimit - 1}),
        ));
        methods.push(rejectionMethod(
            benefit,
            "DailyUsageLimitIsExhausted",
            `${title}: 일 ${benefit.dailyUsageLimit}회 사용 후에는 적용하지 않는다`,
            contextCall(benefit, {usedDailyCount: benefit.dailyUsageLimit}),
            "FREQUENCY_LIMIT_EXHAUSTED",
        ));
    }

    if (benefit.monthlyUsageLimit > 0) {
        methods.push(applicableMethod(
            benefit,
            "MonthlyUsageIsLastAllowed",
            `${title}: 월 ${benefit.monthlyUsageLimit}회 한도의 마지막 허용 거래는 적용한다`,
            contextCall(benefit, {usedMonthlyCount: benefit.monthlyUsageLimit - 1}),
        ));
        methods.push(rejectionMethod(
            benefit,
            "MonthlyUsageLimitIsExhausted",
            `${title}: 월 ${benefit.monthlyUsageLimit}회 사용 후에는 적용하지 않는다`,
            contextCall(benefit, {usedMonthlyCount: benefit.monthlyUsageLimit}),
            "FREQUENCY_LIMIT_EXHAUSTED",
        ));
    }

    if (benefit.merchantEligibilityRequired) {
        methods.push(rejectionMethod(
            benefit,
            "MerchantIsNotEligible",
            `${title}: 제외 가맹점에서는 적용하지 않는다`,
            contextCall(benefit, {merchantEligible: false}),
            "MERCHANT_NOT_ELIGIBLE",
        ));
    }

    if (benefit.paymentChannelEligibilityRequired) {
        methods.push(rejectionMethod(
            benefit,
            "PaymentChannelIsNotEligible",
            `${title}: 지정 결제 채널이 아니면 적용하지 않는다`,
            contextCall(benefit, {paymentChannelEligible: false}),
            "PAYMENT_CHANNEL_NOT_ELIGIBLE",
        ));
    }

    methods.push(...promotionMethods(benefit));
    methods.push(...basisMethods(benefit));
    return methods;
}

function rejectionMethod(benefit, scenarioSuffix, display, context, reason, usedMonthlyValue = null) {
    return `    @Test
    @DisplayName("${escapeJava(display)}")
    void rejects${benefitSuffix(benefit)}When${scenarioSuffix}() {
        // 미적용 경계값을 결제 상황에 넣어 계산기가 거절 사유를 구분하는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, usedMonthlyValue ?? benefit.usedMonthlyValue)},
                ${context});

        // 단순 미적용 여부뿐 아니라 사용자에게 안내할 구체적인 사유까지 검증한다.
        fixture.assertRejected(result, BenefitRejectionReason.${reason});
    }`;
}

function applicableMethod(benefit, scenarioSuffix, display, context) {
    return `    @Test
    @DisplayName("${escapeJava(display)}")
    void applies${benefitSuffix(benefit)}When${scenarioSuffix}() {
        // 적용 가능한 마지막 경계값 또는 최초 경계값을 결제 상황에 넣어 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${context});

        // 경계값에서도 혜택이 거절되지 않고 정상 적용되는지 확인한다.
        fixture.assertApplied(result);
    }`;
}

function maximumBaseMethod(benefit) {
    const atLimit = benefit.maximumBenefitBaseAmount;
    const overLimit = addOne(atLimit);
    return `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 1회 인정금액 ${won(atLimit)} 초과분은 혜택에서 제외한다`)}")
    void caps${benefitSuffix(benefit)}BenefitBaseAmount() {
        // 1회 인정금액과 정확히 같은 결제의 혜택을 계산한다.
        BenefitCalculationResult atLimit = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: atLimit})});

        // 인정금액보다 1원 큰 결제로 초과분이 계산 기준에서 제외되는지 확인한다.
        BenefitCalculationResult overLimit = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: overLimit})});

        // 두 결제의 혜택이 같으면 1회 인정금액 상한이 정상 적용된 것이다.
        fixture.assertApplied(atLimit);
        fixture.assertApplied(overLimit);
        fixture.assertBigDecimalEquals(atLimit.rawRewardValue(), overLimit.rawRewardValue());
    }`;
}

function monthlyPartialMethod(benefit) {
    const used = subtractOne(benefit.monthlyLimitValue);
    return `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 월 한도가 1원 남았으면 1원만 적용한다`)}")
    void appliesOnlyRemainingMonthlyLimitFor${benefitSuffix(benefit)}() {
        // 월 한도에서 이미 사용한 혜택을 빼고 1원만 남은 상태로 계산한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, used)},
                ${contextCall(benefit)});

        // 계산 혜택이 더 크더라도 남은 1원만 적용되고 월 한도는 0원이 되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("1", result.appliedRewardValue());
        fixture.assertBigDecimalEquals("0", result.remainingLimitValue());
    }`;
}

function promotionMethods(benefit) {
    const cases = [];
    if (benefit.promotionCondition === "NIGHT_TIME") {
        cases.push(["BeforeNightStart", "20시 59분 59초에는 Night 혜택을 적용하지 않는다",
            "2026-07-25T20:59:59", false]);
        cases.push(["AtNightStart", "21시 정각부터 Night 혜택을 적용한다",
            "2026-07-25T21:00:00", true]);
        cases.push(["BeforeNightEnd", "08시 59분 59초까지 Night 혜택을 적용한다",
            "2026-07-25T08:59:59", true]);
        cases.push(["AtNightEnd", "09시 정각부터 Night 혜택을 적용하지 않는다",
            "2026-07-25T09:00:00", false]);
    }
    if (benefit.promotionCondition === "WEEKEND") {
        cases.push(["BeforeWeekend", "금요일 23시 59분 59초에는 주말 혜택을 적용하지 않는다",
            "2026-07-24T23:59:59", false]);
        cases.push(["AtWeekendStart", "토요일 00시 정각부터 주말 혜택을 적용한다",
            "2026-07-25T00:00:00", true]);
        cases.push(["BeforeWeekendEnd", "일요일 23시 59분 59초까지 주말 혜택을 적용한다",
            "2026-07-26T23:59:59", true]);
        cases.push(["AtWeekendEnd", "월요일 00시 정각부터 주말 혜택을 적용하지 않는다",
            "2026-07-27T00:00:00", false]);
    }
    return cases.map(([suffix, label, approvedAt, applicable]) => {
        const context = contextCall(benefit, {approvedAt});
        if (applicable) {
            return applicableMethod(benefit, suffix, `${benefit.benefitTitle}: ${label}`, context);
        }
        return rejectionMethod(
            benefit,
            suffix,
            `${benefit.benefitTitle}: ${label}`,
            context,
            "CONDITION_NOT_MET",
        );
    });
}

function basisMethods(benefit) {
    if (benefit.rewardBasis === "RATE") {
        return [rateRoundingMethod(benefit)];
    }
    if (benefit.rewardBasis === "PER_SPEND_UNIT") {
        return perSpendUnitMethods(benefit);
    }
    if (benefit.rewardBasis === "PER_USAGE_UNIT") {
        return [perUsageUnitZeroMethod(benefit)];
    }
    if (
        benefit.rewardBasis === "FIXED"
        && benefit.benefitType === "DISCOUNT"
        && benefit.rewardUnit === "KRW"
        && decimal(benefit.minimumPaymentAmount) < decimal(benefit.rewardValue)
    ) {
        return [fixedDiscountCapMethod(benefit)];
    }
    return [];
}

function rateRoundingMethod(benefit) {
    const payment = String(Math.max(decimal(benefit.minimumPaymentAmount), 10001));
    const base = positive(benefit.maximumBenefitBaseAmount)
        ? String(Math.min(decimal(payment), decimal(benefit.maximumBenefitBaseAmount)))
        : payment;
    let expected = String(Math.floor(decimal(base) * decimal(benefit.rewardRate)));
    if (benefit.benefitType === "DISCOUNT" && benefit.rewardUnit === "KRW") {
        expected = String(Math.min(decimal(expected), decimal(payment)));
    }
    return `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 정률 혜택의 원 미만 금액을 절사한다`)}")
    void floors${benefitSuffix(benefit)}FractionalReward() {
        // 비율 계산 결과에 원 미만 소수가 생기는 결제금액으로 절사 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: payment})});

        // 카드 혜택은 원 미만 금액을 올림하지 않고 버린 결과와 같아야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("${expected}", result.rawRewardValue());
    }`;
}

function perSpendUnitMethods(benefit) {
    const minimum = decimal(benefit.minimumPaymentAmount);
    const unit = decimal(benefit.spendUnitAmount);
    const units = Math.max(Math.floor(minimum / unit) + 1, 1);
    const boundary = String(unit * units);
    const below = String(unit * units - 1);
    const belowExpected = String(Math.floor(
        Math.floor(decimal(below) / unit) * decimal(benefit.rewardValue),
    ));
    const exactExpected = String(Math.floor(units * decimal(benefit.rewardValue)));
    const suffix = benefitSuffix(benefit);
    return [
        `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 다음 결제 단위보다 1원 적은 금액은 추가 적립하지 않는다`)}")
    void excludes${suffix}AmountBelowNextSpendUnit() {
        // 다음 적립 단위에 1원 모자란 결제금액으로 불완전한 단위가 버려지는지 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: below})});

        // 완성된 결제 단위 수에 해당하는 혜택만 계산되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("${belowExpected}", result.rawRewardValue());
    }`,
        `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 다음 결제 단위 경계값부터 추가 혜택을 적용한다`)}")
    void applies${suffix}AtNextSpendUnit() {
        // 다음 적립 단위와 정확히 같은 결제금액으로 추가 혜택 시작 경계를 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: boundary})});

        // 결제 단위가 하나 늘어난 만큼 혜택도 증가해야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("${exactExpected}", result.rawRewardValue());
    }`,
    ];
}

function perUsageUnitZeroMethod(benefit) {
    return `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 사용량이 0이면 혜택도 0으로 계산한다`)}")
    void calculatesZero${benefitSuffix(benefit)}ForZeroUsage() {
        // 리터·횟수처럼 사용량을 기준으로 계산하는 혜택에 사용량 0을 입력한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {usageQuantity: "0"})});

        // 결제 승인 자체는 유효하지만 사용량이 없으므로 계산 혜택은 0이어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("0", result.rawRewardValue());
    }`;
}

function fixedDiscountCapMethod(benefit) {
    const payment = String(Math.max(decimal(benefit.minimumPaymentAmount), 1));
    return `    @Test
    @DisplayName("${escapeJava(`${benefit.benefitTitle}: 정액 할인은 결제금액을 초과하지 않는다`)}")
    void caps${benefitSuffix(benefit)}FixedDiscountAtPaymentAmount() {
        // 정액 할인액보다 작은 결제금액으로 결제금액 초과 할인 방지 정책을 확인한다.
        BenefitCalculationResult result = fixture.calculator.calculate(
                ${ruleCall(benefit, benefit.usedMonthlyValue)},
                ${contextCall(benefit, {paymentAmount: payment})});

        // 원화 할인은 실제 결제금액까지만 적용되어야 한다.
        fixture.assertApplied(result);
        fixture.assertBigDecimalEquals("${payment}", result.rawRewardValue());
    }`;
}

function ruleHelper(benefit) {
    const name = `benefit${String(benefit.benefitIndex).padStart(3, "0")}Rule`;
    return `    /**
     * benefit_title=${escapeJava(benefit.benefitTitle)}의 계산 조건을 만든다.
     *
     * @param usedMonthlyValue 이번 달에 이미 적용받은 혜택 누적값
     * @return 금액·실적·한도·횟수·적용 조건이 구조화된 혜택 룰
     */
    private BenefitRule ${name}(String usedMonthlyValue) {
        return fixture.rule(
                /* 룰 식별자 */ "card-${escapeJava(benefit.cardId)}-benefit-${benefit.benefitIndex}",
                /* 할인·캐시백·포인트·마일리지 구분 */ BenefitType.${benefit.benefitType},
                /* 정률·정액·결제 단위·사용량 단위 구분 */ BenefitBasis.${benefit.rewardBasis},
                /* 혜택 결과 단위 */ RewardUnit.${benefit.rewardUnit},
                /* 정률 계산 비율 */ "${benefit.rewardRate}",
                /* 정액 또는 단위당 혜택값 */ "${benefit.rewardValue}",
                /* 단위 적립 기준 결제금액 */ "${benefit.spendUnitAmount}",
                /* 1회 혜택 인정금액 상한 */ "${benefit.maximumBenefitBaseAmount}",
                /* 혜택 적용 최소 결제금액 */ "${benefit.minimumPaymentAmount}",
                /* 필요한 전월 실적 */ "${benefit.requiredPreviousMonthSpend}",
                /* 월 혜택 한도 */ "${benefit.monthlyLimitValue}",
                /* 이번 달에 이미 사용한 혜택 */ usedMonthlyValue,
                /* 시간·요일 조건 */ BenefitPromotionCondition.${benefit.promotionCondition},
                /* 적용 MOCA 카테고리 */ "${escapeJava(benefit.category)}",
                /* 일 사용 횟수 한도 */ ${benefit.dailyUsageLimit},
                /* 월 사용 횟수 한도 */ ${benefit.monthlyUsageLimit},
                /* 대상 가맹점 확인 필요 여부 */ ${benefit.merchantEligibilityRequired},
                /* 지정 결제 채널 확인 필요 여부 */ ${benefit.paymentChannelEligibilityRequired});
    }`;
}

function ruleCall(benefit, usedMonthlyValue) {
    const name = `benefit${String(benefit.benefitIndex).padStart(3, "0")}Rule`;
    return `${name}(/* 이번 달에 이미 사용한 혜택 */ "${usedMonthlyValue}")`;
}

function contextCall(benefit, overrides = {}) {
    const values = {
        paymentAmount: benefit.paymentAmount,
        usageQuantity: benefit.usageQuantity,
        previousMonthSpend: benefit.previousMonthSpend,
        approvedAt: benefit.approvedAt,
        category: benefit.category,
        newMemberGracePeriod: false,
        usedDailyCount: 0,
        usedMonthlyCount: 0,
        merchantEligible: true,
        paymentChannelEligible: true,
        ...overrides,
    };
    const category = values.category === "__NOT_MATCHED__"
        ? "NOT_MATCHED_CATEGORY"
        : `"${escapeJava(values.category)}"`;
    const simple = !values.newMemberGracePeriod
        && values.usedDailyCount === 0
        && values.usedMonthlyCount === 0
        && values.merchantEligible
        && values.paymentChannelEligible;
    if (simple) {
        return `fixture.context(
                        /* 결제금액 */ "${values.paymentAmount}",
                        /* 리터·횟수 등 사용량 */ "${values.usageQuantity}",
                        /* 전월 실적 */ "${values.previousMonthSpend}",
                        /* 카드 승인 시각 */ "${escapeJava(values.approvedAt)}",
                        /* MOCA 가맹점 카테고리 */ ${category})`;
    }
    return `fixture.context(
                        /* 결제금액 */ "${values.paymentAmount}",
                        /* 리터·횟수 등 사용량 */ "${values.usageQuantity}",
                        /* 전월 실적 */ "${values.previousMonthSpend}",
                        /* 카드 승인 시각 */ "${escapeJava(values.approvedAt)}",
                        /* MOCA 가맹점 카테고리 */ ${category},
                        /* 신규 발급 실적 유예 여부 */ ${values.newMemberGracePeriod},
                        /* 오늘 이미 사용한 횟수 */ ${values.usedDailyCount},
                        /* 이번 달 이미 사용한 횟수 */ ${values.usedMonthlyCount},
                        /* 대상 가맹점 여부 */ ${values.merchantEligible},
                        /* 지정 결제 채널 여부 */ ${values.paymentChannelEligible})`;
}

function formulaLabel(benefit) {
    if (benefit.rewardBasis === "RATE") {
        return `${decimal(benefit.rewardRate) * 100}% ${rewardTypeLabel(benefit)}`;
    }
    if (benefit.rewardBasis === "FIXED") {
        return `${benefit.rewardValue}${unitLabel(benefit.rewardUnit)} 정액 ${rewardTypeLabel(benefit)}`;
    }
    if (benefit.rewardBasis === "PER_SPEND_UNIT") {
        return `${won(benefit.spendUnitAmount)}당 ${benefit.rewardValue}${unitLabel(benefit.rewardUnit)} 적립`;
    }
    return `사용 단위당 ${benefit.rewardValue}${unitLabel(benefit.rewardUnit)} ${rewardTypeLabel(benefit)}`;
}

function rewardTypeLabel(benefit) {
    return {
        DISCOUNT: "할인",
        CASHBACK: "캐시백",
        POINT: "포인트",
        MILEAGE: "마일리지",
    }[benefit.benefitType] ?? "혜택";
}

function unitLabel(unit) {
    return {KRW: "원", POINT: "P", MILE: "마일"}[unit] ?? unit;
}

function classificationStatusLabel(mode) {
    return {
        DIRECT_OFFLINE_REVIEW_REQUIRED: "직접 카드 결제 계산 규칙 검토 필요",
        ONLINE_OR_INDIRECT_EXCLUDED: "온라인·간편결제 범위 제외",
        INFORMATION_ONLY: "정보성 혜택",
        NON_RULE_DETAIL: "계산 규칙이 아닌 상세 정보",
    }[mode] ?? mode;
}

function benefitSuffix(benefit) {
    return `Benefit${String(benefit.benefitIndex).padStart(3, "0")}`;
}

function positive(value) {
    return decimal(value) > 0;
}

function decimal(value) {
    return Number(value);
}

function subtractOne(value) {
    return String(decimal(value) - 1);
}

function addOne(value) {
    return String(decimal(value) + 1);
}

function won(value) {
    return `${Number(value).toLocaleString("ko-KR")}원`;
}

function shorten(value, maxLength) {
    const normalized = String(value).replace(/\s+/g, " ").trim();
    return normalized.length <= maxLength
        ? normalized
        : `${normalized.slice(0, maxLength - 1)}…`;
}

function escapeJava(value) {
    return String(value)
        .replaceAll("\\", "\\\\")
        .replaceAll("\"", "\\\"")
        .replaceAll("\r", "")
        .replaceAll("\n", "\\n");
}

function capitalize(value) {
    return value.charAt(0).toUpperCase() + value.slice(1);
}
