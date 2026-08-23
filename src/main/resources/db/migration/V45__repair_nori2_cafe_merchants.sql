-- 운영 DB에서 노리2 카페 대상 가맹점이 누락된 경우를 보정한다.
-- 기존 V44는 merchants가 이미 존재해야 target을 만들 수 있었으므로,
-- 가맹점 마스터를 먼저 보장한 뒤 최신 콘텐츠 버전의 target을 재연결한다.
INSERT INTO merchants
    (merchant_id, merchant_category_id, name, normalized_name, status,
     has_physical_location, created_at, updated_at)
SELECT UUID(), category.merchant_category_id, source.merchant_name,
       source.merchant_name, 'active', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '스타벅스' AS merchant_name
    UNION ALL SELECT '커피빈'
) source
INNER JOIN merchant_categories category ON category.category_code = 'CAFE'
WHERE NOT EXISTS (
    SELECT 1
    FROM merchants existing
    WHERE existing.normalized_name = source.merchant_name
);

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_id, target_code, target_name, target_source, target_authority,
     minimum_place_confidence)
SELECT UUID(), rule_data.rule_id, source.condition_group, 'include', 'merchant',
       merchant.merchant_id, merchant.normalized_name, merchant.name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
INNER JOIN (
    SELECT '스타벅스' AS merchant_name, 1 AS condition_group
    UNION ALL SELECT '커피빈', 2
) source ON 1 = 1
INNER JOIN merchants merchant ON merchant.normalized_name = source.merchant_name
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name = '스타벅스·커피빈 10% 할인'
  AND rule_data.position = 1
  AND NOT EXISTS (
      SELECT 1
      FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  )
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_rule_targets existing
      WHERE existing.rule_id = rule_data.rule_id
        AND existing.condition_group = source.condition_group
        AND existing.match_mode = 'include'
        AND existing.target_type = 'merchant'
        AND existing.merchant_id = merchant.merchant_id
  );

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
      SELECT 1
      FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );
