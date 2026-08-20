-- 신한카드 Point Plan 체크 캐릭터형(짱구)의 원문 중 CODEF 승인과 내부 가맹점으로
-- 확정할 수 있는 편의점 적립 혜택을 계산 스키마에 연결한다.

INSERT INTO benefit_offers
    (offer_id, benefit_id, reward_program_id, offer_name, position, priority,
     exclusive_group_key, reward_type, value_type, value_unit, calculation_mode,
     calculation_basis, stacking_mode, reward_timing, valuation_scope,
     valuation_method, valid_from, valid_to, created_at, updated_at)
SELECT UUID(), benefit.benefit_id, NULL, '편의점 포인트 적립', 1, 100,
       'SHINHAN_POINT_PLAN_CHECK_CONVENIENCE', 'points', 'percentage', 'percent',
       'flat', 'transaction_amount', 'standalone', 'point_accrual', 'transaction',
       'direct', NULL, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_benefits benefit
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2890'
  AND benefit.position = 5
  AND benefit.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offers existing WHERE existing.benefit_id = benefit.benefit_id
  );

UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
SET offer.offer_name = '편의점 포인트 적립',
    offer.priority = 100,
    offer.exclusive_group_key = 'SHINHAN_POINT_PLAN_CHECK_CONVENIENCE',
    offer.reward_type = 'points',
    offer.value_type = 'percentage',
    offer.value_unit = 'percent',
    offer.calculation_mode = 'flat',
    offer.calculation_basis = 'transaction_amount',
    offer.stacking_mode = 'standalone',
    offer.reward_timing = 'point_accrual',
    offer.valuation_scope = 'transaction',
    offer.valuation_method = 'direct',
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE benefit.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND offer.position = 1;

INSERT INTO benefit_rules
    (rule_id, offer_id, position, priority, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, previous_spend_min_krw, rounding_type, rounding_unit,
     rule_schema_version, rule_support_status, rule_definition_json, created_at, updated_at)
SELECT UUID(), offer.offer_id, 1, 100, 'CU·GS25·세븐일레븐 5% 포인트 적립',
       'grant', 'standalone', 5, 'percent', 200000, 'floor', 1, 1, 'SUPPORTED',
       JSON_OBJECT(
           'schemaVersion', 1,
           'conditions', JSON_OBJECT(
               'all', JSON_ARRAY(
                   JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                               'value', '200000',
                               'rejectionReason', 'PERFORMANCE_NOT_MET')),
               'any', JSON_ARRAY(), 'none', JSON_ARRAY()),
           'reward', JSON_OBJECT('benefitType', 'POINT', 'rewardUnit', 'POINT',
                                 'calculation', 'RATE', 'rate', '0.05'),
           'limits', JSON_ARRAY(
               JSON_OBJECT('type', 'DAILY_USAGE_COUNT', 'value', '1'))),
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
WHERE offer.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND offer.position = 1
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules existing WHERE existing.offer_id = offer.offer_id
  );

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, 1, 'include', 'merchant_category',
       category.merchant_category_id, NULL, category.category_code, category.category_name,
       'CARD_BENEFIT_EXPLICIT', 'ISSUER_CATEGORY', 0.950,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN merchant_categories category
    ON category.category_code = 'CONVENIENCE_STORE'
WHERE offer.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets existing
      WHERE existing.rule_id = rule_data.rule_id
        AND existing.condition_group = 1
        AND existing.match_mode = 'include'
        AND existing.target_type = 'merchant_category'
        AND existing.merchant_category_id = category.merchant_category_id
  );

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit,
     shared_group_key, created_at, updated_at)
SELECT UUID(), offer.offer_id, '편의점 포인트 월 통합 적립 한도',
       'monthly', 'reward_amount', 'point', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
WHERE offer.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.limit_period = 'monthly'
        AND existing.limit_type = 'reward_amount'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, current_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, 1, 3000, 200000, NULL,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
WHERE offer.benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
  );

UPDATE card_benefits
SET structuring_status = 'STRUCTURED', structuring_note = NULL,
    updated_at = UTC_TIMESTAMP(6)
WHERE benefit_id = '4da2cd93-b8e1-585c-bae4-7118aef652f8';
