-- 노리2 카페 혜택은 전월 실적과 무관하게 월 3,000원 한도를 적용한다.
-- 기존 배포 DB에는 seed가 재실행되지 않을 수 있으므로 후속 migration으로 보정한다.
INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type, limit_unit, shared_group_key)
SELECT UUID(), offer.offer_id, '노리2 카페 월 할인한도', 'monthly', 'reward_amount', 'KRW', 'NORI2_CAFE_MONTHLY'
FROM benefit_offers offer
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.shared_group_key = 'NORI2_CAFE_MONTHLY'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value, previous_spend_min_krw)
SELECT UUID(), policy.limit_policy_id, 1, 3000, NULL
FROM benefit_limit_policies policy
JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND policy.shared_group_key = 'NORI2_CAFE_MONTHLY'
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = 1
  );
