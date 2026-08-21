-- Point Plan 체크 캐릭터형(짱구) 일상 생활비 적립.
-- 승인 데이터는 오프라인 승인 기준이며, 포인트 사용액은 확인할 수 없으므로 승인금액 전체를 계산 기준으로 사용한다.

ALTER TABLE benefit_limit_policies
    ADD COLUMN applicable_months_json JSON NULL AFTER valid_to;

INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, priority, exclusive_group_key,
     reward_type, value_type, value_unit, calculation_mode, calculation_basis,
     stacking_mode, reward_timing, valuation_scope, valuation_method,
     created_at, updated_at)
SELECT UUID(), benefit.benefit_id, '일상 생활비 포인트 적립', 2, 90,
       'SHINHAN_POINT_PLAN_CHECK_DAILY_LIVING', 'points', 'percentage', 'percent',
       'flat', 'transaction_amount', 'standalone', 'point_accrual', 'transaction',
       'direct', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_benefits benefit
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2890'
  AND benefit.position = 1
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offers existing
      WHERE existing.benefit_id = benefit.benefit_id
        AND existing.position = 2
  );

INSERT INTO benefit_rules
    (rule_id, offer_id, position, priority, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, rule_schema_version, rule_support_status,
     rule_definition_json, created_at, updated_at)
SELECT UUID(), offer.offer_id, seed.position, seed.position, seed.rule_name,
       'grant', 'standalone', seed.rate * 100, 'percent', 1, 'SUPPORTED',
       JSON_OBJECT(
         'schemaVersion', 1,
         'conditions', JSON_OBJECT(
           'all', JSON_ARRAY(
             JSON_OBJECT('type', 'PAYMENT_AMOUNT', 'operator', 'GTE',
                         'value', seed.minimum_amount, 'rejectionReason', 'MIN_PAYMENT_NOT_MET'),
             JSON_OBJECT('type', 'PAYMENT_AMOUNT', 'operator', 'LT',
                         'value', seed.maximum_amount, 'rejectionReason', 'CATEGORY_NOT_MATCHED'),
             JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                         'value', '200000', 'rejectionReason', 'PERFORMANCE_NOT_MET')
           ),
           'any', JSON_ARRAY(),
           'none', JSON_ARRAY(
             JSON_OBJECT('type', 'MERCHANT_CATEGORY', 'operator', 'IN',
                         'values', JSON_ARRAY('CONVENIENCE_STORE'),
                         'rejectionReason', 'TARGET_NOT_MATCHED')
           )
         ),
         'reward', JSON_OBJECT('benefitType', 'POINT', 'rewardUnit', 'POINT',
                               'calculation', 'RATE', 'rate', seed.rate),
         'limits', JSON_ARRAY()
       ), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
JOIN (
    SELECT 1 position, '2만원 미만 0.2% 적립' rule_name, '0' minimum_amount,
           '20000' maximum_amount, '0.002' rate
    UNION ALL SELECT 2, '2만원 이상 10만원 미만 0.4%', '20000', '100000', '0.004'
    UNION ALL SELECT 3, '10만원 이상 50만원 미만 0.8%', '100000', '500000', '0.008'
    UNION ALL SELECT 4, '50만원 이상 1% 적립', '500000', '999999999999', '0.01'
) seed ON 1 = 1
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.position = seed.position
  );

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type, target_code,
     target_name, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, 1, 'include', 'all_merchants', 'ALL_MERCHANTS',
       '국내 오프라인 전체 가맹점', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets target
      WHERE target.rule_id = rule_data.rule_id
        AND target.target_type = 'all_merchants'
  );

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit,
     applicable_months_json, created_at, updated_at)
SELECT UUID(), offer.offer_id, policy.policy_name, 'monthly', 'reward_amount', 'point',
       policy.applicable_months_json, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
JOIN (
    SELECT '일상 생활비 일반월 한도' policy_name,
           JSON_ARRAY(1, 2, 3, 4, 6, 7, 8, 9, 10, 11) applicable_months_json
    UNION ALL
    SELECT '일상 생활비 가족행사월 한도', JSON_ARRAY(5, 12)
) policy ON 1 = 1
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.policy_name = policy.policy_name
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, tier.position, tier.limit_value,
       tier.minimum_spend, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
JOIN (
    SELECT 1 position, 5000 limit_value, 200000 minimum_spend
    UNION ALL SELECT 2, 10000, 500000
    UNION ALL SELECT 3, 15000, 800000
) tier ON 1 = 1
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = tier.position
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, tier.position, tier.limit_value,
       tier.minimum_spend, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
JOIN (
    SELECT 1 position, 10000 limit_value, 200000 minimum_spend
    UNION ALL SELECT 2, 15000, 500000
    UNION ALL SELECT 3, 20000, 800000
) tier ON 1 = 1
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND policy.policy_name = '일상 생활비 가족행사월 한도'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = tier.position
  );
