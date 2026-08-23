-- 운영 DB에 노리2 카페 offer/rule/target 자체가 없는 환경을 완전 보정한다.
UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET offer.report_visible = TRUE,
    offer.report_title = '카페',
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );

INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, reward_type, value_type,
     calculation_mode, calculation_basis, stacking_mode, valuation_scope,
     valuation_method, report_visible, report_title)
SELECT UUID(), benefit.benefit_id, '스타벅스·커피빈 10% 할인', 2,
       'discount', 'percentage', 'flat', 'transaction_amount', 'standalone',
       'transaction', 'direct', TRUE, '카페'
FROM cards card
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.title = '카페'
WHERE card.gorilla_card_id = '2422'
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offers existing
      WHERE existing.benefit_id = benefit.benefit_id
        AND existing.offer_name = '스타벅스·커피빈 10% 할인'
  );

INSERT INTO benefit_rules
    (rule_id, offer_id, position, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, previous_spend_min_krw, rounding_type,
     rounding_unit, rule_schema_version, rule_support_status, rule_definition_json)
SELECT UUID(), offer.offer_id, 1, '카페', 'grant', 'standalone',
       10, 'percent', NULL, 'floor', 1, 1, 'SUPPORTED',
       JSON_OBJECT(
           'schemaVersion', 1,
           'conditions', JSON_OBJECT(
               'all', JSON_ARRAY(), 'any', JSON_ARRAY(), 'none', JSON_ARRAY()),
           'reward', JSON_OBJECT(
               'benefitType', 'DISCOUNT', 'rewardUnit', 'KRW',
               'calculation', 'RATE', 'rate', '0.10'),
           'limits', JSON_ARRAY())
FROM benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.position = 1
  );

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence)
SELECT UUID(), rule_data.rule_id, merchant_seed.condition_group,
       'include', 'merchant', NULL, merchant.merchant_id,
       merchant.normalized_name, merchant.name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN (
    SELECT 1 AS condition_group, '스타벅스' AS merchant_name
    UNION ALL SELECT 2, '커피빈'
) merchant_seed ON 1 = 1
INNER JOIN merchants merchant ON merchant.normalized_name = merchant_seed.merchant_name
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND rule_data.position = 1
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets existing
      WHERE existing.rule_id = rule_data.rule_id
        AND existing.match_mode = 'include'
        AND existing.target_type = 'merchant'
        AND existing.merchant_id = merchant.merchant_id
  );

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type,
     limit_unit, shared_group_key)
SELECT UUID(), offer.offer_id, '노리2 카페 월 할인한도',
       'monthly', 'reward_amount', 'KRW', 'NORI2_CAFE_MONTHLY'
FROM benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.shared_group_key = 'NORI2_CAFE_MONTHLY'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, current_spend_min_krw)
SELECT UUID(), policy.limit_policy_id, 1, 3000, NULL, NULL
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE policy.shared_group_key = 'NORI2_CAFE_MONTHLY'
  AND card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = 1
  );
