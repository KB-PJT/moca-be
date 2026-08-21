-- V25가 이미 적용된 환경에서도 가족행사월 한도를 올바른 tier로 복구한다.
DELETE tier
FROM benefit_limit_tiers tier
INNER JOIN benefit_limit_policies policy ON policy.limit_policy_id = tier.limit_policy_id
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND policy.policy_name = '일상 생활비 가족행사월 한도';

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value,
     previous_spend_min_krw, created_at, updated_at)
SELECT UUID(), policy.limit_policy_id, tier.position, tier.limit_value,
       tier.minimum_spend, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN (
    SELECT 1 position, 10000 limit_value, 200000 minimum_spend
    UNION ALL SELECT 2, 15000, 500000
    UNION ALL SELECT 3, 20000, 800000
) tier ON 1 = 1
WHERE offer.offer_name = '일상 생활비 포인트 적립'
  AND policy.policy_name = '일상 생활비 가족행사월 한도';
