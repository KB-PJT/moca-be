import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../..');
const fixturePath = path.join(
  root,
  'src/test/resources/benefit/card-benefit-detail-cases-1206.json',
);
const finalSeedPath = path.join(root, 'src/main/resources/db/seed/moca_final_seed.sql');
const sectionStart = '-- BEGIN GENERATED SAFE BENEFIT STRUCTURING';
const sectionEnd = '-- END GENERATED SAFE BENEFIT STRUCTURING';
const insertionAnchor = '-- =================================================================================\n-- 추가 카드 3종';

// merchant_categories.category_code(moca_final_seed.sql INSERT INTO merchant_categories)와
// 정확히 일치해야 한다. 코드가 일치하지 않으면 target 행이 생성되지 않고 rule만 남는
// 무효 구조화가 생기므로 실제 seed 코드와 다시 대조한 뒤에만 추가한다.
const categoryRules = [
  {type: 'merchant_category', code: 'CONVENIENCE_STORE', source: 'CONVENIENCE_STORE', title: /^편의점$/},
  // 카드고릴라 category는 "대중교통"과 "택시"를 TAXI_MOBILITY 하나로 묶어 제공하고,
  // merchant_categories에도 TRANSPORTATION 한 코드만 존재한다. 기존 PUBLIC_TRANSIT/TAXI
  // 코드는 실제 merchant_categories에 없어 target이 생성되지 않는 무효 구조화였다.
  {type: 'merchant_category', code: 'TRANSPORTATION', source: 'TAXI_MOBILITY', title: /^(대중교통|교통|택시)$/},
  {type: 'merchant_category', code: 'FUEL', source: 'FUEL_CAR', title: /^(주유|주유소)$/},
  {type: 'merchant_category', code: 'MOVIE', source: 'MOVIE_CULTURE', title: /^(영화|영화관)$/},
  {type: 'merchant_category', code: 'CAFE', source: 'FOOD_DINING', title: /^(카페|커피|카페\/디저트)$/},
  {type: 'merchant_category', code: 'FAST_FOOD', source: 'FOOD_DINING', title: /^패스트푸드$/},
  {type: 'merchant_category', code: 'FAMILY_RESTAURANT', source: 'FOOD_DINING', title: /^패밀리레스토랑$/},
  {type: 'merchant_category', code: 'RESTAURANT', source: 'FOOD_DINING', title: /^(음식점|외식)$/},
  // THEME_PARK 코드는 seed에 없다. 실제 seed는 테마파크를 LEISURE(레저)로 분류한다.
  {type: 'merchant_category', code: 'LEISURE', source: null, title: /^테마파크$/},
  {type: 'merchant_category', code: 'DEPARTMENT_STORE', source: null, title: /^백화점$/},
  // ACADEMY 코드는 seed에 없다. 실제 seed는 학원을 EDUCATION(교육)으로 분류한다.
  {type: 'merchant_category', code: 'EDUCATION', source: null, title: /^학원$/},
  // AUTO_MAINTENANCE 코드는 seed에 없다. 실제 seed는 자동차 정비를 AUTOMOTIVE로 분류한다.
  {type: 'merchant_category', code: 'AUTOMOTIVE', source: null, title: /^(자동차|정비)$/},
  {type: 'all_merchants', code: 'ALL', source: '', title: /^(모든가맹점|국내가맹점|국내외가맹점|어디서나 캐시백|무실적)$/},
];

// V23__flatten_categories_and_audit_benefit_structuring.sql의 benefit_merchant_master_seed와
// 정확히 일치하는 canonical merchant만 사용한다. 새 merchant는 이 생성기에서 만들지 않는다.
// pattern은 benefitTitle + benefitDescription(짧은 요약 문구)에서만 찾는다. detailText 전체를
// 검색하면 "○○ 제외", "○○ 매장 한정" 같은 유의사항 문장의 브랜드명까지 target으로 잘못
// 채택할 위험이 있어 안전 범위에서 제외한다.
const merchantDictionary = [
  {canonical: 'CGV', pattern: /CGV|씨지브이/},
  {canonical: '롯데시네마', pattern: /롯데시네마/},
  {canonical: '메가박스', pattern: /메가박스/},
  {canonical: '롯데월드', pattern: /롯데월드/},
  {canonical: '에버랜드', pattern: /에버랜드/},
  {canonical: '서울랜드', pattern: /서울랜드/},
  {canonical: '파리바게뜨', pattern: /파리바게뜨/},
  {canonical: '뚜레쥬르', pattern: /뚜레쥬르/},
  {canonical: '파리크라상', pattern: /파리크라상/},
  {canonical: '롯데백화점', pattern: /롯데백화점/},
  {canonical: '신세계백화점', pattern: /신세계백화점/},
  {canonical: '현대백화점', pattern: /현대백화점/},
  {canonical: '이마트24', pattern: /이마트24/},
  {canonical: '이마트', pattern: /이마트(?!24)/},
  {canonical: '홈플러스', pattern: /홈플러스/},
  {canonical: '롯데마트', pattern: /롯데마트/},
  {canonical: '하나로마트', pattern: /하나로마트/},
  {canonical: '맥도날드', pattern: /맥도날드/},
  {canonical: '버거킹', pattern: /버거킹/},
  {canonical: '롯데리아', pattern: /롯데리아/},
  {canonical: 'KFC', pattern: /KFC/},
  {canonical: '올리브영', pattern: /올리브영/},
  {canonical: '스타벅스', pattern: /스타벅스|STARBUCKS/i},
  {canonical: '투썸플레이스', pattern: /투썸플레이스/},
  {canonical: '이디야', pattern: /이디야/},
  {canonical: '메가MGC커피', pattern: /메가MGC커피|메가커피/},
  {canonical: 'GS25', pattern: /GS ?25|지에스 ?25/},
  {canonical: 'CU', pattern: /(^|[^A-Za-z])CU(?![A-Za-z])|씨유/},
  {canonical: '세븐일레븐', pattern: /세븐일레븐/},
  {canonical: '아웃백', pattern: /아웃백/},
  {canonical: 'VIPS', pattern: /VIPS|빕스/},
  {canonical: '농협몰', pattern: /농협몰/},
];

// LEGACY(비 JSON) 계산 경로는 BenefitUsageCalculationService#toRule에서 dailyUsageLimit,
// monthlyUsageLimit, merchantEligibilityRequired, paymentChannelEligibilityRequired를 항상
// 0/false로 고정하고, findSimpleRulesForUserCard 쿼리도 monthly reward_amount 한도가 아닌
// benefit_limit_policies가 하나라도 있으면 해당 rule 자체를 후보에서 제외한다. 따라서 이
// 생성기가 만드는 관계형(LEGACY) rule에서 안전하게 표현할 수 있는 것은:
//   - transaction_max_krw (거래 인정금액 상한)
//   - monthly / reward_amount 단일 한도 (benefit_limit_policies + benefit_limit_tiers)
// 뿐이다. 일·월 "횟수" 한도(DAILY_USAGE_LIMIT/MONTHLY_USAGE_LIMIT)는 JSON Rule DSL
// (rule_definition_json, rule_support_status=SUPPORTED)로만 표현 가능하며, 이번 변경은
// 빌드 검증(Maven Central·Docker 접근 불가로 ./gradlew test/integrationTest 실행 불가) 없이
// JSON을 생성하는 위험을 피하기 위해 이번 라운드에서는 다루지 않는다. PERFORMANCE_TIER는
// fixture가 tier 사다리 전체가 아니라 대표값 1개만 제공하므로, 이를 단일 monthly 한도로
// 저장하면 다른 실적 구간 사용자에게 잘못된 한도를 보여줄 수 있어 계속 보류한다.
const unsafeConditions = new Set([
  'CAPTURE_ORDER',
  'DAILY_USAGE_LIMIT',
  'EXCLUSIONS',
  'MONTHLY_USAGE_LIMIT',
  'PERFORMANCE_TIER',
  'WEEKEND',
]);

const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
const rejected = new Map();

function reject(benefit, reason) {
  const aggregate = rejected.get(reason) ?? {benefits: 0, cards: new Set()};
  aggregate.benefits += 1;
  aggregate.cards.add(benefit.cardId);
  rejected.set(reason, aggregate);
  return false;
}

function targetFor(benefit) {
  return categoryRules.find(
    (rule) => (rule.source === null || rule.source === benefit.category)
      && rule.title.test(benefit.benefitTitle.trim()),
  );
}

function merchantTargetsFor(benefit) {
  const text = `${benefit.benefitTitle} ${benefit.benefitDescription}`;
  const seen = new Set();
  const matches = [];
  for (const entry of merchantDictionary) {
    if (entry.pattern.test(text) && !seen.has(entry.canonical)) {
      seen.add(entry.canonical);
      matches.push(entry.canonical);
    }
  }
  return matches;
}

function supported(benefit) {
  if (benefit.mode !== 'DIRECT_OFFLINE_CALCULABLE') return reject(benefit, benefit.mode);
  let resolvedMerchants = null;
  if (benefit.merchantEligibilityRequired) {
    resolvedMerchants = merchantTargetsFor(benefit);
    if (resolvedMerchants.length === 0) return reject(benefit, 'MERCHANT_NOT_MAPPED');
  }
  if (benefit.paymentChannelEligibilityRequired) {
    return reject(benefit, 'PAYMENT_CHANNEL_UNSUPPORTED');
  }
  const category = resolvedMerchants ? null : targetFor(benefit);
  if (!resolvedMerchants && !category) return reject(benefit, 'TARGET_NOT_MAPPED');
  if (benefit.rewardBasis !== 'RATE' && benefit.rewardBasis !== 'FIXED') {
    return reject(benefit, 'REWARD_BASIS_UNSUPPORTED');
  }
  if (benefit.rewardBasis === 'RATE'
      && !(Number(benefit.rewardRate) > 0 && Number(benefit.rewardRate) <= 1)) {
    return reject(benefit, 'REWARD_NOT_PARSED');
  }
  if (benefit.rewardBasis === 'FIXED' && !(Number(benefit.rewardValue) > 0)) {
    return reject(benefit, 'REWARD_NOT_PARSED');
  }
  const unsafe = (benefit.recognizedConditions ?? []).find((item) => unsafeConditions.has(item));
  if (unsafe) return reject(benefit, `${unsafe}_UNSUPPORTED`);
  // benefit_rules.chk_benefit_rules_transaction_range는 transaction_max_krw가 transaction_min_krw
  // 이상이어야 한다고 강제한다. "거래 적격 최소금액"과 "혜택 인정금액 상한"은 서로 다른 개념이라
  // 실제로는 상한이 최소금액보다 작을 수 있으므로(예: 1만원 이상 결제해야 하지만 혜택은 2천원까지만
  // 인정), 이 조합은 안전하게 표현할 수 없어 명시적으로 막는다.
  const minPayment = Number(benefit.minimumPaymentAmount) || 0;
  const maxBase = Number(benefit.maximumBenefitBaseAmount) || 0;
  if (minPayment > 0 && maxBase > 0 && maxBase < minPayment) {
    return reject(benefit, 'TRANSACTION_CAP_BELOW_MINIMUM_UNSUPPORTED');
  }
  // PERFORMANCE_TIER 혜택은 위에서 이미 걸러지므로, 여기서 살아남은 monthlyLimitValue는
  // 실적 구간과 무관한 단일 월 한도로 간주할 수 있다.
  return {category, merchants: resolvedMerchants};
}

function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function nullableNumber(value) {
  return Number(value) > 0 ? String(Number(value)) : 'NULL';
}

function rewardType(type) {
  return {DISCOUNT: 'discount', CASHBACK: 'cashback', POINT: 'points', MILEAGE: 'points'}[type];
}

// RATE 계산은 항상 "비율"이므로 percent로 저장한다. FIXED 계산은 실제 지급 단위를 그대로
// reward_unit/value_unit에 반영한다(원화 할인·캐시백은 KRW, 포인트는 point, 마일리지는 mile).
function rewardUnitFor(benefit) {
  if (benefit.rewardBasis === 'RATE') return 'percent';
  if (benefit.benefitType === 'MILEAGE') return 'mile';
  if (benefit.benefitType === 'POINT') return 'point';
  return 'KRW';
}

function rewardValueFor(benefit) {
  // 부동소수점 곱셈은 0.07 * 100 = 7.000000000000001 같은 잔여 오차를 만들 수 있으므로
  // DECIMAL(18,4) 컬럼에 맞춰 소수 4자리로 반올림한다.
  const raw = benefit.rewardBasis === 'RATE'
    ? Number(benefit.rewardRate) * 100
    : Number(benefit.rewardValue);
  return Math.round(raw * 10000) / 10000;
}

const evaluated = fixture.benefits.map((benefit) => ({benefit, result: supported(benefit)}));
const accepted = evaluated.filter((row) => row.result);

const values = accepted.map(({benefit, result}) => `    (${[
  sqlString(benefit.cardId),
  benefit.benefitIndex,
  sqlString(benefit.benefitTitle),
  sqlString(rewardType(benefit.benefitType)),
  rewardValueFor(benefit),
  sqlString(rewardUnitFor(benefit)),
  benefit.rewardBasis === 'RATE' ? sqlString('percentage') : sqlString('fixed_amount'),
  nullableNumber(benefit.requiredPreviousMonthSpend),
  nullableNumber(benefit.minimumPaymentAmount),
  nullableNumber(benefit.maximumBenefitBaseAmount),
  nullableNumber(benefit.monthlyLimitValue),
  sqlString(result.category ? result.category.type : 'merchant'),
  result.category ? sqlString(result.category.code) : 'NULL',
].join(', ')})`);

const merchantSeedRows = [];
for (const {benefit, result} of accepted) {
  if (!result.merchants) continue;
  result.merchants.forEach((canonical, index) => {
    merchantSeedRows.push(`    (${sqlString(benefit.cardId)}, ${benefit.benefitIndex}, `
      + `${index + 1}, ${sqlString(canonical)})`);
  });
}

const sql = `-- Generated by tools/benefit-structuring/generate-safe-structuring.mjs.
-- detailText 기반 Golden fixture 중 교차 검증을 통과한 안전 구조화 후보만 포함한다.
-- 직접 수정하지 말고 생성기를 다시 실행한다.

CREATE TEMPORARY TABLE safe_benefit_structuring_seed (
    gorilla_card_id VARCHAR(50) NOT NULL,
    benefit_position SMALLINT UNSIGNED NOT NULL,
    benefit_title VARCHAR(255) NOT NULL,
    reward_type VARCHAR(30) NOT NULL,
    reward_value DECIMAL(18,4) NOT NULL,
    reward_unit VARCHAR(20) NOT NULL,
    value_type VARCHAR(30) NOT NULL,
    previous_spend_min_krw DECIMAL(18,2) NULL,
    transaction_min_krw DECIMAL(18,2) NULL,
    transaction_max_krw DECIMAL(18,2) NULL,
    monthly_limit_value DECIMAL(18,4) NULL,
    target_type VARCHAR(30) NOT NULL,
    category_code VARCHAR(50) NULL,
    PRIMARY KEY (gorilla_card_id, benefit_position)
) ENGINE=InnoDB;

INSERT INTO safe_benefit_structuring_seed
    (gorilla_card_id, benefit_position, benefit_title, reward_type, reward_value,
     reward_unit, value_type, previous_spend_min_krw, transaction_min_krw, transaction_max_krw,
     monthly_limit_value, target_type, category_code)
VALUES
${values.join(',\n')};

CREATE TEMPORARY TABLE safe_benefit_structuring_merchant_seed (
    gorilla_card_id VARCHAR(50) NOT NULL,
    benefit_position SMALLINT UNSIGNED NOT NULL,
    condition_group SMALLINT UNSIGNED NOT NULL,
    merchant_name VARCHAR(150) NOT NULL,
    PRIMARY KEY (gorilla_card_id, benefit_position, condition_group)
) ENGINE=InnoDB;
${merchantSeedRows.length > 0 ? `
INSERT INTO safe_benefit_structuring_merchant_seed
    (gorilla_card_id, benefit_position, condition_group, merchant_name)
VALUES
${merchantSeedRows.join(',\n')};
` : ''}
UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN safe_benefit_structuring_seed seed
    ON seed.gorilla_card_id = card.gorilla_card_id
   AND seed.benefit_position = benefit.position
SET offer.offer_name = seed.benefit_title,
    offer.reward_type = seed.reward_type,
    offer.value_type = seed.value_type,
    offer.value_unit = seed.reward_unit,
    offer.calculation_mode = 'flat',
    offer.calculation_basis = 'transaction_amount',
    offer.stacking_mode = 'standalone',
    offer.reward_timing = CASE seed.reward_type
        WHEN 'discount' THEN 'statement'
        WHEN 'cashback' THEN 'cashback'
        ELSE 'point_accrual' END,
    offer.valuation_scope = 'transaction',
    offer.valuation_method = 'direct',
    offer.updated_at = CURRENT_TIMESTAMP(6)
WHERE offer.position = 1
  AND offer.reward_type = 'other'
  AND NOT EXISTS (SELECT 1 FROM benefit_rules rule_data WHERE rule_data.offer_id = offer.offer_id);

INSERT INTO benefit_rules
    (rule_id, offer_id, position, priority, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, previous_spend_min_krw, transaction_min_krw, transaction_max_krw,
     rounding_type, rounding_unit, created_at, updated_at)
SELECT UUID(), offer.offer_id, 1, 100, CONCAT(seed.benefit_title, ' 자동 구조화'),
       'grant', 'standalone', seed.reward_value, seed.reward_unit,
       seed.previous_spend_min_krw, seed.transaction_min_krw, seed.transaction_max_krw,
       'floor', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM safe_benefit_structuring_seed seed
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.position = seed.benefit_position
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id AND offer.position = 1
WHERE offer.reward_type = seed.reward_type
  AND NOT EXISTS (SELECT 1 FROM benefit_rules rule_data WHERE rule_data.offer_id = offer.offer_id);

-- category/all_merchants target: 기존 방식과 동일하게 seed.target_type이 merchant가 아닐 때만 채운다.
INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, 1, 'include', seed.target_type,
       category.merchant_category_id, NULL, seed.category_code,
       CASE seed.target_type WHEN 'all_merchants' THEN '전 가맹점' ELSE category.category_name END,
       CASE seed.target_type WHEN 'all_merchants' THEN 'ALL_MERCHANTS'
            ELSE 'CARD_GORILLA_CATEGORY' END,
       CASE seed.target_type WHEN 'all_merchants' THEN 'ALL_MERCHANTS'
            ELSE 'ISSUER_CATEGORY' END,
       CASE seed.target_type WHEN 'all_merchants' THEN 0.000 ELSE 0.950 END,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM safe_benefit_structuring_seed seed
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.position = seed.benefit_position
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id AND offer.position = 1
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
LEFT JOIN merchant_categories category
    ON seed.target_type = 'merchant_category' AND category.category_code = seed.category_code
WHERE seed.target_type <> 'merchant'
  AND NOT EXISTS (
    SELECT 1 FROM benefit_rule_targets target
    WHERE target.rule_id = rule_data.rule_id
      AND target.match_mode = 'include'
)
  AND (seed.target_type = 'all_merchants' OR category.merchant_category_id IS NOT NULL);

-- 명시 브랜드 target: 카드 원문에서 인식한 canonical merchant마다 별도 condition_group으로
-- OR 후보를 만든다(같은 group에 두 merchant를 넣으면 승인 1건이 동시에 두 merchant_id를
-- 가질 수 없어 항상 불일치하므로 반드시 그룹을 분리한다).
INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, merchant_seed.condition_group, 'include', 'merchant',
       NULL, merchant.merchant_id, merchant_seed.merchant_name, merchant_seed.merchant_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM safe_benefit_structuring_merchant_seed merchant_seed
INNER JOIN safe_benefit_structuring_seed seed
    ON seed.gorilla_card_id = merchant_seed.gorilla_card_id
   AND seed.benefit_position = merchant_seed.benefit_position
   AND seed.target_type = 'merchant'
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.position = seed.benefit_position
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id AND offer.position = 1
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN merchants merchant ON merchant.normalized_name = merchant_seed.merchant_name
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_rule_targets target
    WHERE target.rule_id = rule_data.rule_id
      AND target.condition_group = merchant_seed.condition_group
      AND target.match_mode = 'include'
      AND target.target_type = 'merchant'
);

-- 월 보상금액 한도(PERFORMANCE_TIER가 아닌 단일 한도만): findSimpleRulesForUserCard가
-- LEGACY rule에서 유일하게 허용하는 benefit_limit_policies 모양(monthly/reward_amount)이다.
INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit,
     created_at, updated_at)
SELECT UUID(), offer.offer_id, CONCAT(seed.benefit_title, ' 월 한도'), 'monthly', 'reward_amount',
       seed.reward_unit, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM safe_benefit_structuring_seed seed
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.position = seed.benefit_position
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id AND offer.position = 1
WHERE seed.monthly_limit_value IS NOT NULL
  AND seed.reward_unit IN ('KRW', 'point', 'mile')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies policy
      WHERE policy.offer_id = offer.offer_id AND policy.limit_period = 'monthly'
        AND policy.limit_type = 'reward_amount'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, 1, seed.monthly_limit_value,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM safe_benefit_structuring_seed seed
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.position = seed.benefit_position
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id AND offer.position = 1
INNER JOIN benefit_limit_policies policy
    ON policy.offer_id = offer.offer_id AND policy.limit_period = 'monthly'
   AND policy.limit_type = 'reward_amount'
WHERE seed.monthly_limit_value IS NOT NULL
  AND seed.reward_unit IN ('KRW', 'point', 'mile')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers tier WHERE tier.limit_policy_id = policy.limit_policy_id
  );

UPDATE card_benefits benefit
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN safe_benefit_structuring_seed seed
    ON seed.gorilla_card_id = card.gorilla_card_id
   AND seed.benefit_position = benefit.position
SET benefit.structuring_status = 'STRUCTURED',
    benefit.structuring_note = NULL,
    benefit.updated_at = CURRENT_TIMESTAMP(6)
WHERE EXISTS (
    SELECT 1 FROM benefit_offers offer
    INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
    INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
    WHERE offer.benefit_id = benefit.benefit_id AND target.match_mode = 'include'
);

DROP TEMPORARY TABLE safe_benefit_structuring_seed;
DROP TEMPORARY TABLE safe_benefit_structuring_merchant_seed;
`;

const finalSeed = fs.readFileSync(finalSeedPath, 'utf8');
const generatedSection = `${sectionStart}\n${sql}\n${sectionEnd}`;
const startIndex = finalSeed.indexOf(sectionStart);
let nextFinalSeed;
if (startIndex >= 0) {
  const endIndex = finalSeed.indexOf(sectionEnd, startIndex);
  if (endIndex < 0) throw new Error('generated section end marker not found');
  nextFinalSeed = finalSeed.slice(0, startIndex) + generatedSection
    + finalSeed.slice(endIndex + sectionEnd.length);
} else {
  const anchorIndex = finalSeed.indexOf(insertionAnchor);
  if (anchorIndex < 0) throw new Error('moca_final_seed.sql insertion anchor not found');
  nextFinalSeed = `${finalSeed.slice(0, anchorIndex)}${generatedSection}\n\n`
    + finalSeed.slice(anchorIndex);
}
fs.writeFileSync(finalSeedPath, nextFinalSeed);

console.log(JSON.stringify({
  source: fixture.benefits.length,
  structured: accepted.length,
  cards: new Set(accepted.map(({benefit}) => benefit.cardId)).size,
  merchantTargetedBenefits: accepted.filter(({result}) => result.merchants).length,
  fixedBasisBenefits: accepted.filter(({benefit}) => benefit.rewardBasis === 'FIXED').length,
  transactionCapBenefits: accepted.filter(({benefit}) => Number(benefit.maximumBenefitBaseAmount) > 0).length,
  monthlyLimitBenefits: accepted.filter(({benefit}) => Number(benefit.monthlyLimitValue) > 0).length,
  rejected: Object.fromEntries(
    [...rejected.entries()]
      .sort((a, b) => b[1].cards.size - a[1].cards.size)
      .map(([reason, aggregate]) => [reason, {
        benefits: aggregate.benefits,
        cards: aggregate.cards.size,
      }]),
  ),
}, null, 2));
