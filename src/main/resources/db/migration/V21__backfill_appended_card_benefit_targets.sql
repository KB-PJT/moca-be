-- 기존 운영 데이터에서 가맹점 마스터보다 먼저 카드 target이 적재되어 누락된 행을 보정한다.
-- 신규 환경은 moca_final_seed.sql 마지막의 동일한 멱등 보정으로 첫 실행부터 일관성을 보장한다.

CREATE TEMPORARY TABLE seed_appended_card_targets (
    gorilla_card_id VARCHAR(50) NOT NULL,
    offer_name VARCHAR(255) NOT NULL,
    condition_group SMALLINT UNSIGNED NOT NULL,
    match_mode VARCHAR(10) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_code VARCHAR(100) NOT NULL,
    target_name VARCHAR(255) NOT NULL
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO seed_appended_card_targets
(gorilla_card_id, offer_name, condition_group, match_mode, target_type, target_code, target_name)
VALUES
    ('2680', '편의점 10% 청구 할인', 1, 'include', 'merchant_category', 'CONVENIENCE_STORE', '편의점'),
    ('2680', '커피전문점 10% 청구 할인', 1, 'include', 'merchant_category', 'CAFE', '커피전문점'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 1, 'include', 'merchant', '네이버쇼핑', '네이버쇼핑'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 2, 'include', 'merchant', '쿠팡', '쿠팡'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 3, 'include', 'merchant', 'G마켓', 'G마켓'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 4, 'include', 'merchant', '옥션', '옥션'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 5, 'include', 'merchant', '11번가', '11번가'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 6, 'include', 'merchant', 'SSG.COM', 'SSG.COM'),
    ('2680', '온라인 쇼핑 10% 청구 할인', 7, 'include', 'merchant', '컬리', '컬리'),
    ('2680', '도서 10% 청구 할인', 1, 'include', 'merchant', '교보문고', '교보문고'),
    ('2680', '도서 10% 청구 할인', 2, 'include', 'merchant', 'YES24', 'YES24'),
    ('733', '무신사/솔드아웃 5% 할인', 1, 'include', 'merchant', '무신사', '무신사'),
    ('733', '무신사/솔드아웃 5% 할인', 1, 'include', 'merchant', '솔드아웃', '솔드아웃'),
    ('733', '국내외 가맹점 1% 적립', 1, 'include', 'all_merchants', 'ALL', '전가맹점'),
    ('733', '국내외 가맹점 1% 적립', 2, 'exclude', 'merchant', '무신사', '무신사'),
    ('733', '국내외 가맹점 1% 적립', 2, 'exclude', 'merchant', '솔드아웃', '솔드아웃'),
    ('2899', '국내/외 전가맹점 기본 적립', 1, 'include', 'all_merchants', 'ALL', '전가맹점'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 1, 'include', 'merchant', 'SK에너지', 'SK에너지'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 1, 'include', 'merchant', 'GS칼텍스', 'GS칼텍스'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 2, 'include', 'merchant', '쿠팡', '쿠팡'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 2, 'include', 'merchant', 'SSG.COM', 'SSG.COM'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 2, 'include', 'merchant', '무신사', '무신사'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 2, 'include', 'merchant', '29CM', '29CM'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 3, 'include', 'merchant', '땡겨요', '땡겨요'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 3, 'include', 'merchant', '배달의민족', '배달의민족'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 3, 'include', 'merchant', '요기요', '요기요'),
    ('2899', '특별 적립 (주유/쇼핑/배달)', 3, 'include', 'merchant', '쿠팡이츠', '쿠팡이츠'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '넷플릭스', '넷플릭스'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '유튜브 프리미엄', '유튜브 프리미엄'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '티빙', '티빙'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '디즈니플러스', '디즈니플러스'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '네이버플러스 멤버십', '네이버플러스 멤버십'),
    ('2899', 'OTT/멤버십 특별 적립', 1, 'include', 'merchant', '쿠팡 와우 멤버십', '쿠팡 와우 멤버십');

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule.rule_id, seed.condition_group, seed.match_mode, seed.target_type,
       category.merchant_category_id, merchant.merchant_id, seed.target_code, seed.target_name,
       'CARD_BENEFIT_EXPLICIT',
       CASE seed.target_type
           WHEN 'merchant_category' THEN 'ISSUER_CATEGORY'
           WHEN 'merchant' THEN 'MERCHANT_EXACT'
           ELSE 'ALL_MERCHANTS'
       END,
       CASE seed.target_type WHEN 'all_merchants' THEN 0.000 ELSE 0.990 END,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM seed_appended_card_targets seed
INNER JOIN cards card ON card.gorilla_card_id = seed.gorilla_card_id
INNER JOIN card_content_versions version ON version.card_id = card.card_id
INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
INNER JOIN benefit_offers offer
    ON offer.benefit_id = benefit.benefit_id
   AND offer.offer_name = seed.offer_name
INNER JOIN benefit_rules rule ON rule.offer_id = offer.offer_id
LEFT JOIN merchant_categories category
    ON seed.target_type = 'merchant_category'
   AND category.category_code = seed.target_code
LEFT JOIN merchants merchant
    ON seed.target_type = 'merchant'
   AND (merchant.normalized_name = seed.target_code OR merchant.name = seed.target_code)
WHERE (seed.target_type = 'all_merchants'
       OR (seed.target_type = 'merchant_category' AND category.merchant_category_id IS NOT NULL)
       OR (seed.target_type = 'merchant' AND merchant.merchant_id IS NOT NULL))
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_rule_targets target
      WHERE target.rule_id = rule.rule_id
        AND target.condition_group = seed.condition_group
        AND target.match_mode = seed.match_mode
        AND target.target_type = seed.target_type
        AND target.target_code = seed.target_code
  );

DROP TEMPORARY TABLE seed_appended_card_targets;

DELETE target
FROM benefit_rule_targets target
INNER JOIN benefit_rules rule ON rule.rule_id = target.rule_id
INNER JOIN benefit_offers offer ON offer.offer_id = rule.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN merchant_categories category
    ON category.merchant_category_id = target.merchant_category_id
WHERE benefit.source_category_id = 52
  AND target.target_type = 'merchant_category'
  AND category.category_code = 'TRANSPORTATION';

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule.rule_id, 1, 'include', 'merchant_category',
       category.merchant_category_id, NULL, category.category_code, category.category_name,
       'CARD_GORILLA_CATEGORY', 'ISSUER_CATEGORY', 0.900,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_benefits benefit
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
INNER JOIN benefit_rules rule ON rule.offer_id = offer.offer_id
INNER JOIN merchant_categories category ON category.category_code = 'PUBLIC_TRANSIT'
WHERE benefit.source_category_id = 52
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_rule_targets target
      WHERE target.rule_id = rule.rule_id
        AND target.condition_group = 1
        AND target.match_mode = 'include'
        AND target.target_type = 'merchant_category'
        AND target.merchant_category_id = category.merchant_category_id
  );

DELETE duplicate_target
FROM benefit_rule_targets duplicate_target
INNER JOIN benefit_rule_targets keeper
    ON keeper.rule_id = duplicate_target.rule_id
   AND keeper.condition_group = duplicate_target.condition_group
   AND keeper.match_mode = duplicate_target.match_mode
   AND keeper.target_type = duplicate_target.target_type
   AND keeper.target_code = duplicate_target.target_code
   AND keeper.target_id < duplicate_target.target_id;
