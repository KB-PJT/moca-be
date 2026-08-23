-- 운영 DB에 노리2 카페 월 한도와 리포트 카테고리가 함께 없을 수 있어 보정한다.
UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET offer.report_title = '카페',
    offer.report_visible = TRUE,
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인';

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit, shared_group_key)
SELECT UUID(), offer.offer_id, '노리2 카페 월 할인한도', 'monthly', 'reward_amount', 'KRW', 'NORI2_CAFE_MONTHLY'
FROM benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.shared_group_key = 'NORI2_CAFE_MONTHLY'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value, previous_spend_min_krw)
SELECT UUID(), policy.limit_policy_id, 1, 3000, NULL
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
WHERE policy.shared_group_key = 'NORI2_CAFE_MONTHLY'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = 1
  );
