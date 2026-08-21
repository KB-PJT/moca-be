-- SOL Plan은 오프라인 승인으로 확정 가능한 일반 적립과 주유 특별 적립만 계산한다.
DELETE tier
FROM benefit_limit_tiers tier
INNER JOIN benefit_limit_policies policy ON policy.limit_policy_id = tier.limit_policy_id
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND policy.shared_group_key = 'SOL_PLAN_TOTAL_LIMIT';

DELETE tier
FROM benefit_limit_tiers tier
INNER JOIN benefit_limit_policies policy ON policy.limit_policy_id = tier.limit_policy_id
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)'
  AND policy.limit_type = 'eligible_spend';

DELETE policy
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)'
  AND policy.limit_type = 'eligible_spend';

UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'PARTIAL',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'conditions', JSON_OBJECT(
            'all', IF(
                rule_data.position = 1,
                JSON_ARRAY(
                    JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                                'value', '400000',
                                'rejectionReason', 'PERFORMANCE_NOT_MET'),
                    JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'LT',
                                'value', '1000000',
                                'rejectionReason', 'PERFORMANCE_NOT_MET')
                ),
                JSON_ARRAY(
                    JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                                'value', '1000000',
                                'rejectionReason', 'PERFORMANCE_NOT_MET')
                )
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'POINT', 'rewardUnit', 'POINT', 'calculation', 'RATE',
            'rate', IF(rule_data.position = 1, '0.025', '0.05')
        ),
        'limits', JSON_ARRAY()
    )
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)';

UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'INFORMATION_ONLY',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1, 'mode', 'INFORMATION_ONLY',
        'reason', 'ONLINE_PAYMENT_CHANNEL_NOT_VERIFIABLE')
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = 'OTT/멤버십 특별 적립';

DELETE target
FROM benefit_rule_targets target
INNER JOIN benefit_rules rule_data ON rule_data.rule_id = target.rule_id
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)';

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, target_data.condition_group, target_data.match_mode, 'merchant',
       NULL, merchant.merchant_id, target_data.target_code, target_data.target_code,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN (
    SELECT 1 condition_group, 'include' match_mode, 'SK에너지' target_code UNION ALL
    SELECT 2, 'include', 'GS칼텍스' UNION ALL
    SELECT 100, 'exclude', '쿠팡' UNION ALL
    SELECT 101, 'exclude', 'SSG.COM' UNION ALL
    SELECT 102, 'exclude', '무신사' UNION ALL
    SELECT 103, 'exclude', '29CM' UNION ALL
    SELECT 104, 'exclude', '땡겨요' UNION ALL
    SELECT 105, 'exclude', '배달의민족' UNION ALL
    SELECT 106, 'exclude', '요기요' UNION ALL
    SELECT 107, 'exclude', '쿠팡이츠'
) target_data
INNER JOIN merchants merchant ON merchant.normalized_name = target_data.target_code
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)';

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), basic_rule.rule_id, 100, 'exclude', 'merchant',
       NULL, special_target.merchant_id, special_target.target_code, special_target.target_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules basic_rule
INNER JOIN benefit_offers basic_offer ON basic_offer.offer_id = basic_rule.offer_id
INNER JOIN card_benefits basic_benefit ON basic_benefit.benefit_id = basic_offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = basic_benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN benefit_offers special_offer
    ON special_offer.benefit_id = basic_offer.benefit_id
   AND special_offer.offer_name = '특별 적립 (주유/쇼핑/배달)'
INNER JOIN benefit_rules special_rule ON special_rule.offer_id = special_offer.offer_id
INNER JOIN benefit_rule_targets special_target ON special_target.rule_id = special_rule.rule_id
WHERE card.gorilla_card_id = '2899'
  AND basic_offer.offer_name = '국내/외 전가맹점 기본 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets existing
      WHERE existing.rule_id = basic_rule.rule_id
        AND existing.match_mode = 'exclude'
        AND existing.target_type = 'merchant'
        AND existing.target_code = special_target.target_code
  )
GROUP BY basic_rule.rule_id, special_target.merchant_id,
         special_target.target_code, special_target.target_name;

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), basic_rule.rule_id, 101, 'exclude', 'merchant',
       NULL, ott_target.merchant_id, ott_target.target_code, ott_target.target_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules basic_rule
INNER JOIN benefit_offers basic_offer ON basic_offer.offer_id = basic_rule.offer_id
INNER JOIN card_benefits basic_benefit ON basic_benefit.benefit_id = basic_offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = basic_benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN card_benefits ott_benefit
    ON ott_benefit.content_version_id = version.content_version_id
   AND ott_benefit.position = 2
INNER JOIN benefit_offers ott_offer
    ON ott_offer.benefit_id = ott_benefit.benefit_id
   AND ott_offer.offer_name = 'OTT/멤버십 특별 적립'
INNER JOIN benefit_rules ott_rule ON ott_rule.offer_id = ott_offer.offer_id
INNER JOIN benefit_rule_targets ott_target ON ott_target.rule_id = ott_rule.rule_id
WHERE card.gorilla_card_id = '2899'
  AND basic_offer.offer_name = '국내/외 전가맹점 기본 적립'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets existing
      WHERE existing.rule_id = basic_rule.rule_id
        AND existing.match_mode = 'exclude'
        AND existing.target_type = 'merchant'
        AND existing.target_code = ott_target.target_code
  )
GROUP BY basic_rule.rule_id, ott_target.merchant_id,
         ott_target.target_code, ott_target.target_name;

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit,
     shared_group_key, created_at, updated_at)
SELECT UUID(), offer.offer_id, '쓸수록 SOLSOL 통합 적립 한도',
       'monthly', 'reward_amount', 'point', 'SOL_PLAN_TOTAL_LIMIT',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '특별 적립 (주유/쇼핑/배달)'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.shared_group_key = 'SOL_PLAN_TOTAL_LIMIT'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, 1, 50000, 400000,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2899'
  AND policy.shared_group_key = 'SOL_PLAN_TOTAL_LIMIT'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
  );
