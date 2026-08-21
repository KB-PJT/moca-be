-- 신한카드 나라사랑카드 체크: 승인 데이터로 판별 가능한 Life 서비스만 자동 계산한다.
INSERT INTO merchants
    (merchant_id, merchant_category_id, name, normalized_name, status,
     has_physical_location, created_at, updated_at)
SELECT UUID(), category.merchant_category_id, source.name, source.name, 'active',
       source.physical, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'TAXI' category_code, '카카오T' name, FALSE physical UNION ALL
    SELECT 'CAFE', '컴포즈커피', TRUE UNION ALL
    SELECT 'CAFE', '매머드커피', TRUE UNION ALL
    SELECT 'CAFE', '빽다방', TRUE UNION ALL
    SELECT 'THEME_PARK', '캐리비안베이', TRUE
) source
INNER JOIN merchant_categories category ON category.category_code = source.category_code
WHERE NOT EXISTS (
    SELECT 1 FROM merchants existing WHERE existing.normalized_name = source.name
);

DELETE tier
FROM benefit_limit_tiers tier
INNER JOIN benefit_limit_policies policy ON policy.limit_policy_id = tier.limit_policy_id
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
WHERE offer.benefit_id IN (
    'd2f751e7-87db-5c87-80da-3566a3f87686',
    'a199918c-17f4-5bd5-a366-2c8c4171f946');

DELETE policy
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
WHERE offer.benefit_id IN (
    'd2f751e7-87db-5c87-80da-3566a3f87686',
    'a199918c-17f4-5bd5-a366-2c8c4171f946');

DELETE target
FROM benefit_rule_targets target
INNER JOIN benefit_rules rule_data ON rule_data.rule_id = target.rule_id
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
WHERE offer.benefit_id IN (
    'd2f751e7-87db-5c87-80da-3566a3f87686',
    'a199918c-17f4-5bd5-a366-2c8c4171f946');

DELETE rule_data
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
WHERE offer.benefit_id IN (
    'd2f751e7-87db-5c87-80da-3566a3f87686',
    'a199918c-17f4-5bd5-a366-2c8c4171f946');

DELETE FROM benefit_offers
WHERE benefit_id IN (
    'd2f751e7-87db-5c87-80da-3566a3f87686',
    'a199918c-17f4-5bd5-a366-2c8c4171f946');

INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, reward_type, value_type,
     calculation_mode, calculation_basis, stacking_mode, valuation_scope,
     valuation_method, created_at, updated_at)
SELECT source.offer_id, source.benefit_id, source.offer_name, source.position,
       source.reward_type, source.value_type, source.calculation_mode,
       'transaction_amount', 'standalone', 'transaction', source.valuation_method,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '29330000-0000-4000-8000-000000000001' offer_id,
           'd2f751e7-87db-5c87-80da-3566a3f87686' benefit_id,
           '대중교통 20% 캐시백' offer_name, 1 position,
           'cashback' reward_type, 'percentage' value_type,
           'flat' calculation_mode, 'direct' valuation_method UNION ALL
    SELECT '29330000-0000-4000-8000-000000000002',
           'd2f751e7-87db-5c87-80da-3566a3f87686',
           '광역교통 10% 캐시백', 2, 'cashback', 'percentage', 'flat', 'direct' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000003',
           'd2f751e7-87db-5c87-80da-3566a3f87686',
           '카카오T 택시 10% 캐시백', 3, 'cashback', 'percentage', 'flat', 'direct' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000004',
           'd2f751e7-87db-5c87-80da-3566a3f87686',
           '편의점 20% 캐시백', 4, 'cashback', 'percentage', 'flat', 'direct' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000005',
           'd2f751e7-87db-5c87-80da-3566a3f87686',
           '주요 커피 브랜드 5% 캐시백', 5, 'cashback', 'percentage', 'flat', 'direct' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000006',
           'a199918c-17f4-5bd5-a366-2c8c4171f946',
           'CGV 관람권 6천원 정액 제공', 1, 'other', 'other', 'other', 'not_valued' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000007',
           'a199918c-17f4-5bd5-a366-2c8c4171f946',
           '테마파크 최대 50% 할인', 2, 'discount', 'percentage', 'flat', 'not_valued' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000008',
           'a199918c-17f4-5bd5-a366-2c8c4171f946',
           'CU 행사상품 10% 즉시할인', 3, 'discount', 'percentage', 'flat', 'not_valued'
) source
WHERE EXISTS (SELECT 1 FROM card_benefits benefit WHERE benefit.benefit_id = source.benefit_id)
  AND NOT EXISTS (SELECT 1 FROM benefit_offers existing WHERE existing.offer_id = source.offer_id);

INSERT INTO benefit_rules
    (rule_id, offer_id, position, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, previous_spend_min_krw,
     rule_schema_version, rule_support_status, rule_definition_json,
     created_at, updated_at)
SELECT source.rule_id, source.offer_id, 1, source.offer_name, 'grant', 'standalone',
       source.rate * 100, 'percent', 100000, 1, 'SUPPORTED',
       JSON_OBJECT(
           'schemaVersion', 1,
           'conditions', JSON_OBJECT(
               'all', JSON_ARRAY(JSON_OBJECT(
                   'type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                   'value', '100000', 'rejectionReason', 'PERFORMANCE_NOT_MET')),
               'any', JSON_ARRAY(), 'none', JSON_ARRAY()),
           'reward', JSON_OBJECT(
               'benefitType', 'CASHBACK', 'rewardUnit', 'KRW',
               'calculation', 'RATE', 'rate', CAST(source.rate AS CHAR)),
           'limits', CASE source.offer_id
               WHEN '29330000-0000-4000-8000-000000000004' THEN JSON_ARRAY(
                   JSON_OBJECT('type', 'TRANSACTION_BENEFIT_BASE', 'value', '5000'),
                   JSON_OBJECT('type', 'DAILY_USAGE_COUNT', 'value', '1'),
                   JSON_OBJECT('type', 'MONTHLY_USAGE_COUNT', 'value', '5'))
               WHEN '29330000-0000-4000-8000-000000000005' THEN JSON_ARRAY(
                   JSON_OBJECT('type', 'TRANSACTION_BENEFIT_BASE', 'value', '40000'),
                   JSON_OBJECT('type', 'DAILY_USAGE_COUNT', 'value', '1'),
                   JSON_OBJECT('type', 'MONTHLY_USAGE_COUNT', 'value', '3'))
               ELSE JSON_ARRAY()
           END),
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '29330000-0000-4000-8000-000000000101' rule_id,
           '29330000-0000-4000-8000-000000000001' offer_id,
           '대중교통 20% 캐시백' offer_name, 0.20 rate UNION ALL
    SELECT '29330000-0000-4000-8000-000000000102',
           '29330000-0000-4000-8000-000000000002', '광역교통 10% 캐시백', 0.10 UNION ALL
    SELECT '29330000-0000-4000-8000-000000000103',
           '29330000-0000-4000-8000-000000000003', '카카오T 택시 10% 캐시백', 0.10 UNION ALL
    SELECT '29330000-0000-4000-8000-000000000104',
           '29330000-0000-4000-8000-000000000004', '편의점 20% 캐시백', 0.20 UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105',
           '29330000-0000-4000-8000-000000000005', '주요 커피 브랜드 5% 캐시백', 0.05
) source
WHERE EXISTS (SELECT 1 FROM benefit_offers offer WHERE offer.offer_id = source.offer_id)
  AND NOT EXISTS (SELECT 1 FROM benefit_rules existing WHERE existing.rule_id = source.rule_id);

INSERT INTO benefit_rules
    (rule_id, offer_id, position, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, rule_schema_version, rule_support_status,
     rule_definition_json, created_at, updated_at)
SELECT source.rule_id, source.offer_id, 1, source.offer_name, 'grant', 'standalone',
       source.reward_value, source.reward_unit, 1, 'INFORMATION_ONLY',
       JSON_OBJECT('schemaVersion', 1, 'mode', 'INFORMATION_ONLY',
                   'reason', source.reason), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '29330000-0000-4000-8000-000000000106' rule_id,
           '29330000-0000-4000-8000-000000000006' offer_id,
           'CGV 관람권 6천원 정액 제공' offer_name, NULL reward_value,
           NULL reward_unit, 'TICKET_PRICE_AND_QUANTITY_NOT_VERIFIABLE' reason UNION ALL
    SELECT '29330000-0000-4000-8000-000000000107',
           '29330000-0000-4000-8000-000000000007',
           '테마파크 최대 50% 할인', 50, 'percent',
           'ANNUAL_COUNT_AND_TICKET_HOLDER_NOT_VERIFIABLE' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000108',
           '29330000-0000-4000-8000-000000000008',
           'CU 행사상품 10% 즉시할인', 10, 'percent',
           'EVENT_PRODUCT_NOT_VERIFIABLE'
) source
WHERE EXISTS (SELECT 1 FROM benefit_offers offer WHERE offer.offer_id = source.offer_id)
  AND NOT EXISTS (SELECT 1 FROM benefit_rules existing WHERE existing.rule_id = source.rule_id);

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), source.rule_id, source.condition_group, 'include', source.target_type,
       category.merchant_category_id, merchant.merchant_id, source.target_code, source.target_code,
       'CARD_BENEFIT_EXPLICIT',
       CASE source.target_type WHEN 'merchant' THEN 'MERCHANT_EXACT' ELSE 'ISSUER_CATEGORY' END,
       0.990, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '29330000-0000-4000-8000-000000000101' rule_id, 1 condition_group,
           'merchant_category' target_type, 'BUS' target_code UNION ALL
    SELECT '29330000-0000-4000-8000-000000000101', 2, 'merchant_category', 'SUBWAY' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000102', 1, 'merchant_category', 'EXPRESS_BUS' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000102', 2, 'merchant_category', 'TRAIN' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000103', 1, 'merchant', '카카오T' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000104', 1, 'merchant', 'GS25' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000104', 2, 'merchant', 'CU' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 1, 'merchant', '스타벅스' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 2, 'merchant', '이디야' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 3, 'merchant', '메가MGC커피' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 4, 'merchant', '폴바셋' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 5, 'merchant', '컴포즈커피' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 6, 'merchant', '매머드커피' UNION ALL
    SELECT '29330000-0000-4000-8000-000000000105', 7, 'merchant', '빽다방'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000106', 1, 'merchant', 'CGV'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000107', 1, 'merchant', '에버랜드'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000107', 2, 'merchant', '롯데월드'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000107', 3, 'merchant', '서울랜드'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000107', 4, 'merchant', '캐리비안베이'
    UNION ALL SELECT '29330000-0000-4000-8000-000000000108', 1, 'merchant', 'CU'
) source
LEFT JOIN merchant_categories category
    ON source.target_type = 'merchant_category' AND category.category_code = source.target_code
LEFT JOIN merchants merchant
    ON source.target_type = 'merchant' AND merchant.normalized_name = source.target_code
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_rule_targets existing
    WHERE existing.rule_id = source.rule_id
      AND existing.condition_group = source.condition_group
      AND existing.match_mode = 'include'
      AND existing.target_type = source.target_type
      AND existing.target_code = source.target_code
)
  AND EXISTS (SELECT 1 FROM benefit_rules rule_data WHERE rule_data.rule_id = source.rule_id)
  AND (category.merchant_category_id IS NOT NULL OR merchant.merchant_id IS NOT NULL);

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit,
     shared_group_key, created_at, updated_at)
SELECT CONCAT('29330000-0000-4000-8000-0000000002', LPAD(source.position, 2, '0')),
       source.offer_id, 'Life 서비스 통합 캐시백 한도',
       'monthly', 'reward_amount', 'KRW', 'SHINHAN_NARASARANG_LIFE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 1 position, '29330000-0000-4000-8000-000000000001' offer_id UNION ALL
    SELECT 2, '29330000-0000-4000-8000-000000000002' UNION ALL
    SELECT 3, '29330000-0000-4000-8000-000000000003' UNION ALL
    SELECT 4, '29330000-0000-4000-8000-000000000004' UNION ALL
    SELECT 5, '29330000-0000-4000-8000-000000000005'
) source
WHERE EXISTS (SELECT 1 FROM benefit_offers offer WHERE offer.offer_id = source.offer_id)
  AND NOT EXISTS (SELECT 1 FROM benefit_limit_policies existing
                  WHERE existing.offer_id = source.offer_id
                    AND existing.shared_group_key = 'SHINHAN_NARASARANG_LIFE');

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, tier_data.position, tier_data.limit_value,
       tier_data.minimum_spend, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN (
    SELECT 1 position, 5000 limit_value, 100000 minimum_spend UNION ALL
    SELECT 2, 20000, 200000 UNION ALL
    SELECT 3, 30000, 500000 UNION ALL
    SELECT 4, 40000, 700000 UNION ALL
    SELECT 5, 50000, 1000000
) tier_data
WHERE offer.offer_id IN (
    '29330000-0000-4000-8000-000000000001',
    '29330000-0000-4000-8000-000000000002',
    '29330000-0000-4000-8000-000000000003',
    '29330000-0000-4000-8000-000000000004',
    '29330000-0000-4000-8000-000000000005')
  AND policy.shared_group_key = 'SHINHAN_NARASARANG_LIFE'
  AND NOT EXISTS (SELECT 1 FROM benefit_limit_tiers existing
                  WHERE existing.limit_policy_id = policy.limit_policy_id
                    AND existing.position = tier_data.position);
