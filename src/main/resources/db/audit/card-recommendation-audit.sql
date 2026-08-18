-- MySQL 8 / MOCA 카드 추천 구조화 감사 SQL

-- [Merchant Category]
SELECT COUNT(*) AS total_category_count FROM merchant_categories;

SELECT COUNT(*) AS parent_category_count
FROM merchant_categories
WHERE parent_id IS NOT NULL;

SELECT category_code, COUNT(*) AS duplicate_count
FROM merchant_categories
GROUP BY category_code
HAVING COUNT(*) > 1;

SELECT category_name, COUNT(*) AS duplicate_count
FROM merchant_categories
GROUP BY category_name
HAVING COUNT(*) > 1;

-- [Merchant]
SELECT COUNT(*) AS total_merchant_count FROM merchants;

SELECT category.category_code,
       category.category_name,
       COUNT(merchant.merchant_id) AS merchant_count
FROM merchant_categories category
LEFT JOIN merchants merchant
    ON merchant.merchant_category_id = category.merchant_category_id
GROUP BY category.merchant_category_id, category.category_code, category.category_name
ORDER BY merchant_count, category.category_code;

SELECT COUNT(*) AS category_without_merchant_count
FROM merchant_categories category
WHERE NOT EXISTS (
    SELECT 1 FROM merchants merchant
    WHERE merchant.merchant_category_id = category.merchant_category_id
);

-- [Benefit Structuring]
SELECT COUNT(*) AS total_benefit_count FROM card_benefits;
SELECT COUNT(*) AS total_offer_count FROM benefit_offers;
SELECT COUNT(*) AS total_rule_count FROM benefit_rules;
SELECT COUNT(*) AS total_target_count FROM benefit_rule_targets;

SELECT structuring_status, COUNT(*) AS benefit_count
FROM card_benefits
GROUP BY structuring_status
ORDER BY structuring_status;

SELECT reward_type, COUNT(*) AS offer_count
FROM benefit_offers
GROUP BY reward_type
ORDER BY reward_type;

SELECT COUNT(*) AS offer_without_rule_count
FROM benefit_offers offer
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_rules rule_data
    WHERE rule_data.offer_id = offer.offer_id
);

SELECT COUNT(*) AS rule_without_include_target_count
FROM benefit_rules rule_data
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_rule_targets target
    WHERE target.rule_id = rule_data.rule_id
      AND target.match_mode = 'include'
);

SELECT COUNT(DISTINCT card.card_id) AS recommendable_card_count
FROM cards card
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
WHERE target.match_mode = 'include'
  AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
  AND rule_data.rule_effect = 'grant'
  AND rule_data.reward_value IS NOT NULL
  AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
  AND target.target_type IN ('all_merchants', 'merchant_category', 'merchant')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_schedules schedule
      WHERE schedule.rule_id = rule_data.rule_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offer_option_requirements requirement
      WHERE requirement.offer_id = offer.offer_id
  );

SELECT COUNT(DISTINCT offer.offer_id) AS recommendable_structured_offer_count
FROM benefit_offers offer
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
WHERE target.match_mode = 'include'
  AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
  AND rule_data.rule_effect = 'grant'
  AND rule_data.reward_value IS NOT NULL
  AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
  AND target.target_type IN ('all_merchants', 'merchant_category', 'merchant')
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_schedules schedule
      WHERE schedule.rule_id = rule_data.rule_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offer_option_requirements requirement
      WHERE requirement.offer_id = offer.offer_id
  );

-- [Target Integrity / Distribution]
SELECT target_type, COUNT(*) AS target_count
FROM benefit_rule_targets
GROUP BY target_type
ORDER BY target_type;

SELECT COUNT(*) AS merchant_target_without_fk_count
FROM benefit_rule_targets
WHERE target_type = 'merchant' AND merchant_id IS NULL;

SELECT COUNT(*) AS category_target_without_fk_count
FROM benefit_rule_targets
WHERE target_type = 'merchant_category' AND merchant_category_id IS NULL;

-- 브랜드가 명시된 원문을 category 전체로 과도하게 확장했을 가능성이 있는 행.
SELECT card.gorilla_card_id,
       version.name AS card_name,
       benefit.title,
       target.target_code,
       target.target_name
FROM cards card
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
WHERE target.match_mode = 'include'
  AND target.target_type = 'merchant_category'
  AND CONCAT_WS(' ', benefit.title, benefit.summary) REGEXP
      'GS25|CU|세븐일레븐|이마트24|스타벅스|투썸플레이스|이디야|CGV|롯데시네마|메가박스|올리브영'
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_rule_targets merchant_target
      WHERE merchant_target.rule_id = rule_data.rule_id
        AND merchant_target.match_mode = 'include'
        AND merchant_target.target_type = 'merchant'
  )
ORDER BY version.name, benefit.position;

-- 대표 회귀: 두 문제 카드가 CU에서 사용할 target을 갖는지 확인한다.
SELECT card.gorilla_card_id,
       version.name AS card_name,
       offer.offer_name,
       rule_data.reward_value,
       rule_data.reward_unit,
       target.target_type,
       COALESCE(merchant.normalized_name, category.category_code, target.target_code) AS target_value
FROM cards card
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
LEFT JOIN merchants merchant ON merchant.merchant_id = target.merchant_id
LEFT JOIN merchant_categories category
    ON category.merchant_category_id = target.merchant_category_id
WHERE card.gorilla_card_id IN ('2680', '360')
  AND target.match_mode = 'include'
  AND (
      target.target_type = 'all_merchants'
      OR merchant.normalized_name = 'CU'
      OR category.category_code = 'CONVENIENCE_STORE'
  )
ORDER BY card.gorilla_card_id, offer.position, target.condition_group;

-- [Issuer Coverage]
WITH recommendable_cards AS (
    SELECT DISTINCT version.card_id
    FROM card_content_versions version
    INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
    INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
    INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
    WHERE target.match_mode = 'include'
      AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
      AND rule_data.rule_effect = 'grant'
      AND rule_data.reward_value IS NOT NULL
      AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
)
SELECT issuer.issuer_name,
       COUNT(DISTINCT card.card_id) AS total_cards,
       COUNT(DISTINCT recommendable.card_id) AS recommendable_cards,
       COUNT(DISTINCT card.card_id) - COUNT(DISTINCT recommendable.card_id)
           AS unavailable_cards
FROM issuers issuer
INNER JOIN cards card ON card.issuer_id = issuer.issuer_id
LEFT JOIN recommendable_cards recommendable ON recommendable.card_id = card.card_id
WHERE card.gorilla_card_id IS NOT NULL
GROUP BY issuer.issuer_id, issuer.issuer_name
ORDER BY issuer.issuer_name;

-- [Unavailable Reason]
-- 카드마다 설명 가능한 최우선 미추천 사유 하나를 부여한다.
WITH recommendable_cards AS (
    SELECT DISTINCT version.card_id
    FROM card_content_versions version
    INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
    INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
    INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
    WHERE target.match_mode = 'include'
      AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
      AND rule_data.rule_effect = 'grant'
      AND rule_data.reward_value IS NOT NULL
      AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
), unavailable AS (
    SELECT card.card_id,
           card.gorilla_card_id,
           version.name AS card_name,
           CASE
               WHEN SUM(benefit.structuring_status = 'PARSE_FAILED') > 0 THEN 'PARSE_FAILED'
               WHEN SUM(benefit.structuring_status = 'PARTIAL') > 0 THEN 'PARTIAL'
               WHEN SUM(benefit.structuring_status = 'UNSUPPORTED') > 0
                   THEN 'UNSUPPORTED_CONDITION'
               WHEN SUM(benefit.record_type = 'benefit'
                        AND benefit.structuring_status = 'RAW') > 0 THEN 'REWARD_NOT_PARSED'
               WHEN SUM(benefit.record_type = 'benefit') = 0 THEN 'NO_MONETARY_BENEFIT'
               ELSE 'NO_SUPPORTED_OFFLINE_BENEFIT'
           END AS unavailable_reason
    FROM cards card
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
    LEFT JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    LEFT JOIN recommendable_cards recommendable ON recommendable.card_id = card.card_id
    WHERE card.gorilla_card_id IS NOT NULL AND recommendable.card_id IS NULL
    GROUP BY card.card_id, card.gorilla_card_id, version.name
)
SELECT unavailable_reason, COUNT(DISTINCT card_id) AS card_count
FROM unavailable
GROUP BY unavailable_reason
ORDER BY card_count DESC, unavailable_reason;

-- 남아 있는 실패 유형의 대표 원문을 다음 parser 작업 입력으로 사용한다.
WITH ranked_failure AS (
    SELECT version.name AS card_name,
           benefit.title,
           benefit.detail_text,
           benefit.structuring_status,
           benefit.structuring_note,
           ROW_NUMBER() OVER (
               PARTITION BY benefit.structuring_status
               ORDER BY version.name, benefit.position
           ) AS row_number_in_status
    FROM card_benefits benefit
    INNER JOIN card_content_versions version
        ON version.content_version_id = benefit.content_version_id
    WHERE benefit.structuring_status IN ('PARSE_FAILED', 'PARTIAL', 'UNSUPPORTED')
)
SELECT structuring_status, card_name, title, detail_text, structuring_note
FROM ranked_failure
WHERE row_number_in_status <= 10
ORDER BY structuring_status, row_number_in_status;
