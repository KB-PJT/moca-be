-- 운영 DB에서 누락된 노리2 체크카드(KB Pay) 오프라인 혜택 구조를 보정한다.
CREATE TEMPORARY TABLE nori2_offline_benefit_repair (
    benefit_title VARCHAR(100) NOT NULL,
    offer_name VARCHAR(150) NOT NULL,
    reward_value DECIMAL(12,4) NOT NULL,
    reward_unit VARCHAR(20) NOT NULL,
    previous_spend_min_krw DECIMAL(12,2) NOT NULL,
    transaction_min_krw DECIMAL(12,2) NULL,
    monthly_limit_krw DECIMAL(12,2) NOT NULL,
    monthly_usage_count INT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_code VARCHAR(100) NOT NULL,
    target_name VARCHAR(150) NOT NULL,
    condition_group INT NOT NULL
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO nori2_offline_benefit_repair VALUES
('드럭스토어', '올리브영·미용실 5% 할인', 5, 'percent', 200000, NULL, 2000, NULL,
 'merchant', '올리브영', '올리브영', 1),
('드럭스토어', '올리브영·미용실 5% 할인', 5, 'percent', 200000, NULL, 2000, NULL,
 'merchant_category', 'BEAUTY', '미용실', 2),
('편의점', 'GS25·CU 5% 할인', 5, 'percent', 200000, NULL, 2000, NULL,
 'merchant', 'GS25', 'GS25', 1),
('편의점', 'GS25·CU 5% 할인', 5, 'percent', 200000, NULL, 2000, NULL,
 'merchant', 'CU', 'CU', 2),
('영화', 'CGV 4,000원 할인', 4000, 'KRW', 200000, 10000, 8000, 2,
 'merchant', 'CGV', 'CGV', 1),
('테마파크', '에버랜드·롯데월드 15,000원 할인', 15000, 'KRW', 200000, 30000, 15000, 1,
 'merchant', '에버랜드', '에버랜드', 1),
('테마파크', '에버랜드·롯데월드 15,000원 할인', 15000, 'KRW', 200000, 30000, 15000, 1,
 'merchant', '롯데월드', '롯데월드', 2);

INSERT INTO merchants
    (merchant_id, merchant_category_id, name, normalized_name, status,
     has_physical_location, created_at, updated_at)
SELECT UUID(), category.merchant_category_id, source.merchant_name,
       source.merchant_name, 'active', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '올리브영' AS merchant_name, 'DRUGSTORE' AS category_code
    UNION ALL SELECT 'CGV', 'MOVIE'
    UNION ALL SELECT '에버랜드', 'THEME_PARK'
    UNION ALL SELECT '롯데월드', 'THEME_PARK'
    UNION ALL SELECT 'GS25', 'CONVENIENCE_STORE'
    UNION ALL SELECT 'CU', 'CONVENIENCE_STORE'
) source
INNER JOIN merchant_categories category
    ON category.category_code COLLATE utf8mb4_0900_ai_ci
       = source.category_code COLLATE utf8mb4_0900_ai_ci
WHERE NOT EXISTS (
    SELECT 1 FROM merchants existing
    WHERE existing.normalized_name COLLATE utf8mb4_0900_ai_ci
          = source.merchant_name COLLATE utf8mb4_0900_ai_ci
);

INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, reward_type, value_type,
     calculation_mode, calculation_basis, stacking_mode, valuation_scope,
     valuation_method, report_visible, report_title)
SELECT UUID(), benefit.benefit_id, repair.offer_name, 2, 'discount',
       CASE WHEN repair.reward_unit = 'KRW' THEN 'fixed_amount' ELSE 'percentage' END,
       CASE WHEN repair.reward_unit = 'KRW' THEN 'single_tier' ELSE 'flat' END,
       'transaction_amount', 'standalone', 'transaction', 'direct', TRUE,
       benefit.title
FROM (SELECT DISTINCT benefit_title, offer_name, reward_unit
      FROM nori2_offline_benefit_repair) repair
INNER JOIN cards card ON card.gorilla_card_id = '2422'
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit
    ON benefit.content_version_id = version.content_version_id
   AND benefit.title COLLATE utf8mb4_0900_ai_ci
       = repair.benefit_title COLLATE utf8mb4_0900_ai_ci
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_offers existing
    WHERE existing.benefit_id = benefit.benefit_id
      AND existing.offer_name COLLATE utf8mb4_0900_ai_ci
          = repair.offer_name COLLATE utf8mb4_0900_ai_ci
);

INSERT INTO benefit_rules
    (rule_id, offer_id, position, rule_effect, stacking_mode, reward_value,
     reward_unit, previous_spend_min_krw, transaction_min_krw,
     rule_schema_version, rule_support_status, rule_definition_json)
SELECT UUID(), offer.offer_id, 1, 'grant', 'standalone', repair.reward_value,
       repair.reward_unit, repair.previous_spend_min_krw,
       repair.transaction_min_krw, 1, 'SUPPORTED',
       JSON_OBJECT(
           'schemaVersion', 1,
           'conditions', JSON_OBJECT('all', JSON_ARRAY(), 'any', JSON_ARRAY(), 'none', JSON_ARRAY()),
           'reward', JSON_OBJECT(
               'benefitType', 'DISCOUNT',
               'rewardUnit', 'KRW',
               'calculation', CASE WHEN repair.reward_unit = 'KRW' THEN 'FIXED' ELSE 'RATE' END,
               'rate', CASE WHEN repair.reward_unit = 'KRW' THEN '0' ELSE CAST(repair.reward_value / 100 AS CHAR) END,
               'value', CASE WHEN repair.reward_unit = 'KRW' THEN CAST(repair.reward_value AS CHAR) ELSE '0' END),
           'limits', CASE WHEN repair.monthly_usage_count IS NULL THEN JSON_ARRAY()
                    ELSE JSON_ARRAY(JSON_OBJECT('type', 'MONTHLY_USAGE_COUNT',
                                                'value', CAST(repair.monthly_usage_count AS CHAR))) END)
FROM (
    SELECT DISTINCT benefit_title, offer_name, reward_value, reward_unit,
           previous_spend_min_krw, transaction_min_krw, monthly_usage_count
    FROM nori2_offline_benefit_repair
) repair
INNER JOIN benefit_offers offer
    ON offer.offer_name COLLATE utf8mb4_0900_ai_ci
       = repair.offer_name COLLATE utf8mb4_0900_ai_ci
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND benefit.title COLLATE utf8mb4_0900_ai_ci
      = repair.benefit_title COLLATE utf8mb4_0900_ai_ci
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.position = 1
  );

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence)
SELECT UUID(), rule_data.rule_id, repair.condition_group, 'include', repair.target_type,
       category.merchant_category_id, merchant.merchant_id, repair.target_code,
       repair.target_name, 'CARD_BENEFIT_EXPLICIT',
       CASE WHEN repair.target_type = 'merchant' THEN 'MERCHANT_EXACT' ELSE 'ISSUER_CATEGORY' END,
       0.990
FROM nori2_offline_benefit_repair repair
INNER JOIN benefit_offers offer
    ON offer.offer_name COLLATE utf8mb4_0900_ai_ci
       = repair.offer_name COLLATE utf8mb4_0900_ai_ci
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN card_benefits benefit
    ON benefit.benefit_id = offer.benefit_id
   AND benefit.title COLLATE utf8mb4_0900_ai_ci
       = repair.benefit_title COLLATE utf8mb4_0900_ai_ci
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
LEFT JOIN merchant_categories category
    ON repair.target_type = 'merchant_category'
   AND category.category_code COLLATE utf8mb4_0900_ai_ci
       = repair.target_code COLLATE utf8mb4_0900_ai_ci
LEFT JOIN merchants merchant
    ON repair.target_type = 'merchant'
   AND merchant.normalized_name COLLATE utf8mb4_0900_ai_ci
       = repair.target_code COLLATE utf8mb4_0900_ai_ci
   AND merchant.status = 'active'
WHERE card.gorilla_card_id = '2422'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets existing
      WHERE existing.rule_id = rule_data.rule_id
        AND existing.condition_group = repair.condition_group
        AND existing.target_type COLLATE utf8mb4_0900_ai_ci
            = repair.target_type COLLATE utf8mb4_0900_ai_ci
        AND existing.target_code COLLATE utf8mb4_0900_ai_ci
            = repair.target_code COLLATE utf8mb4_0900_ai_ci
  )
  AND ((repair.target_type = 'merchant' AND merchant.merchant_id IS NOT NULL)
       OR (repair.target_type = 'merchant_category' AND category.merchant_category_id IS NOT NULL));

INSERT INTO benefit_limit_policies
    (limit_policy_id, offer_id, policy_name, limit_period, limit_type,
     limit_unit, shared_group_key)
SELECT UUID(), offer.offer_id, '노리2 월간 통합할인한도', 'monthly',
       'reward_amount', 'KRW', 'NORI2_MONTHLY_TOTAL'
FROM benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name IN ('올리브영·미용실 5% 할인', 'GS25·CU 5% 할인',
                           'CGV 4,000원 할인', '에버랜드·롯데월드 15,000원 할인')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_policies existing
      WHERE existing.offer_id = offer.offer_id
        AND existing.shared_group_key = 'NORI2_MONTHLY_TOTAL'
  );

INSERT INTO benefit_limit_tiers
    (limit_tier_id, limit_policy_id, position, limit_value, previous_spend_min_krw)
SELECT UUID(), policy.limit_policy_id, tier.position, tier.limit_value,
       tier.previous_spend_min_krw
FROM benefit_limit_policies policy
INNER JOIN benefit_offers offer ON offer.offer_id = policy.offer_id
INNER JOIN (
    SELECT 1 AS position, 20000 AS limit_value, 200000 AS previous_spend_min_krw
    UNION ALL SELECT 2, 30000, 400000
    UNION ALL SELECT 3, 40000, 600000
    UNION ALL SELECT 4, 50000, 800000
) tier ON 1 = 1
WHERE policy.shared_group_key = 'NORI2_MONTHLY_TOTAL'
  AND offer.offer_name IN ('올리브영·미용실 5% 할인', 'GS25·CU 5% 할인',
                           'CGV 4,000원 할인', '에버랜드·롯데월드 15,000원 할인')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_limit_tiers existing
      WHERE existing.limit_policy_id = policy.limit_policy_id
        AND existing.position = tier.position
  );

DROP TEMPORARY TABLE nori2_offline_benefit_repair;
