-- 지도 추천 taxonomy는 category -> merchant의 2단계만 사용한다.
-- 기존 계층이 제공하던 상위 category 매칭 의미는 target/map을 자식 category로 펼쳐 보존한다.

ALTER TABLE card_benefits
    ADD COLUMN structuring_status VARCHAR(20) NOT NULL DEFAULT 'RAW' AFTER record_type,
    ADD COLUMN structuring_note VARCHAR(500) NULL AFTER structuring_status,
    ADD CONSTRAINT chk_card_benefits_structuring_status CHECK (structuring_status IN (
        'RAW', 'PARTIAL', 'STRUCTURED', 'UNSUPPORTED', 'PARSE_FAILED',
        'NON_MONETARY', 'EXCLUDED'
    )),
    ADD INDEX idx_card_benefits_structuring_status (structuring_status);

CREATE TEMPORARY TABLE category_hierarchy_snapshot AS
SELECT child.merchant_category_id AS child_category_id,
       child.category_code AS child_category_code,
       child.category_name AS child_category_name,
       parent.merchant_category_id AS parent_category_id
FROM merchant_categories child
INNER JOIN merchant_categories parent
    ON parent.merchant_category_id = child.parent_id;

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), target.rule_id, target.condition_group, target.match_mode, 'merchant_category',
       hierarchy.child_category_id, NULL,
       hierarchy.child_category_code, hierarchy.child_category_name,
       target.target_source, target.target_authority, target.minimum_place_confidence,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rule_targets target
INNER JOIN category_hierarchy_snapshot hierarchy
    ON hierarchy.parent_category_id = target.merchant_category_id
WHERE target.target_type = 'merchant_category'
  AND NOT EXISTS (
      SELECT 1
      FROM benefit_rule_targets existing_target
      WHERE existing_target.rule_id = target.rule_id
        AND existing_target.condition_group = target.condition_group
        AND existing_target.match_mode = target.match_mode
        AND existing_target.target_type = 'merchant_category'
        AND existing_target.merchant_category_id = hierarchy.child_category_id
  );

INSERT INTO card_gorilla_category_maps
    (card_gorilla_category_map_id, source_category_id, merchant_category_id,
     priority, enabled, created_at, updated_at)
SELECT UUID(), source_map.source_category_id, hierarchy.child_category_id,
       source_map.priority, source_map.enabled, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_gorilla_category_maps source_map
INNER JOIN category_hierarchy_snapshot hierarchy
    ON hierarchy.parent_category_id = source_map.merchant_category_id
WHERE NOT EXISTS (
    SELECT 1
    FROM card_gorilla_category_maps existing_map
    WHERE existing_map.source_category_id = source_map.source_category_id
      AND existing_map.merchant_category_id = hierarchy.child_category_id
);

UPDATE merchant_categories
SET parent_id = NULL,
    updated_at = UTC_TIMESTAMP(6)
WHERE parent_id IS NOT NULL;

DROP TEMPORARY TABLE category_hierarchy_snapshot;

-- 카드 약관에 명시적으로 등장하는 오프라인 브랜드 master만 보강한다.
CREATE TEMPORARY TABLE benefit_merchant_master_seed (
    category_code VARCHAR(50) NOT NULL,
    merchant_name VARCHAR(150) NOT NULL,
    has_physical_location BOOLEAN NOT NULL
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO benefit_merchant_master_seed
    (category_code, merchant_name, has_physical_location)
VALUES
    ('MOVIE', 'CGV', TRUE),
    ('MOVIE', '롯데시네마', TRUE),
    ('MOVIE', '메가박스', TRUE),
    ('THEME_PARK', '롯데월드', TRUE),
    ('THEME_PARK', '에버랜드', TRUE),
    ('THEME_PARK', '서울랜드', TRUE),
    ('BAKERY', '파리바게뜨', TRUE),
    ('BAKERY', '뚜레쥬르', TRUE),
    ('BAKERY', '파리크라상', TRUE),
    ('DEPARTMENT_STORE', '롯데백화점', TRUE),
    ('DEPARTMENT_STORE', '신세계백화점', TRUE),
    ('DEPARTMENT_STORE', '현대백화점', TRUE),
    ('LARGE_MART', '이마트', TRUE),
    ('LARGE_MART', '홈플러스', TRUE),
    ('LARGE_MART', '롯데마트', TRUE),
    ('LARGE_MART', '하나로마트', TRUE),
    ('FAST_FOOD', '맥도날드', TRUE),
    ('FAST_FOOD', '버거킹', TRUE),
    ('FAST_FOOD', '롯데리아', TRUE),
    ('FAST_FOOD', 'KFC', TRUE),
    ('DRUGSTORE', '올리브영', TRUE),
    ('CAFE', '스타벅스', TRUE),
    ('CAFE', '투썸플레이스', TRUE),
    ('CAFE', '이디야', TRUE),
    ('CAFE', '메가MGC커피', TRUE),
    ('CONVENIENCE_STORE', 'GS25', TRUE),
    ('CONVENIENCE_STORE', 'CU', TRUE),
    ('CONVENIENCE_STORE', '세븐일레븐', TRUE),
    ('CONVENIENCE_STORE', '이마트24', TRUE),
    ('RESTAURANT', '아웃백', TRUE),
    ('RESTAURANT', 'VIPS', TRUE),
    ('ONLINE_SHOPPING', '농협몰', FALSE);

INSERT INTO merchants
    (merchant_id, merchant_category_id, name, normalized_name, status,
     has_physical_location, created_at, updated_at)
SELECT UUID(), category.merchant_category_id,
       seed.merchant_name, seed.merchant_name, 'active', seed.has_physical_location,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_merchant_master_seed seed
INNER JOIN merchant_categories category ON category.category_code = seed.category_code
WHERE NOT EXISTS (
    SELECT 1
    FROM merchants merchant
    WHERE merchant.normalized_name = seed.merchant_name
);

DROP TEMPORARY TABLE benefit_merchant_master_seed;

-- KB 틴업 체크카드는 "CU, 세븐일레븐" 명시 혜택이므로 편의점 전체 category로 확장하지 않는다.
DELETE target
FROM benefit_rule_targets target
INNER JOIN benefit_rules rule_data ON rule_data.rule_id = target.rule_id
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2852'
  AND benefit.title = '편의점'
  AND target.target_type = 'merchant_category';

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, seed.condition_group, 'include', 'merchant',
       NULL, merchant.merchant_id, seed.merchant_name, seed.merchant_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
CROSS JOIN (
    SELECT 1 condition_group, 'CU' merchant_name
    UNION ALL SELECT 2, '세븐일레븐'
) seed
INNER JOIN merchants merchant ON merchant.normalized_name = seed.merchant_name
WHERE card.gorilla_card_id = '2852'
  AND benefit.title = '편의점'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets target
      WHERE target.rule_id = rule_data.rule_id
        AND target.match_mode = 'include'
        AND target.target_type = 'merchant'
        AND target.merchant_id = merchant.merchant_id
  );

-- 올바른POINT체크카드의 확정 가능한 부분만 분리 구조화한다.
-- 하나로고객 우대율과 해외/면세점 판정은 현재 입력으로 확정할 수 없어 PARTIAL로 남긴다.
INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, priority, reward_type, value_type,
     calculation_mode, calculation_basis, stacking_mode, reward_timing,
     valuation_scope, valuation_method, created_at, updated_at)
SELECT UUID(), benefit.benefit_id, '전 가맹점 기본적립 0.2%', 2, 100,
       'points', 'percentage', 'flat', 'transaction_amount', 'additive',
       'point_accrual', 'transaction', 'direct', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_benefits benefit
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offers offer
      WHERE offer.benefit_id = benefit.benefit_id AND offer.position = 2
  );

INSERT INTO benefit_offers
    (offer_id, benefit_id, offer_name, position, priority, reward_type, value_type,
     calculation_mode, calculation_basis, stacking_mode, reward_timing,
     valuation_scope, valuation_method, created_at, updated_at)
SELECT UUID(), benefit.benefit_id, '생활 영역 추가적립 0.3%', 3, 200,
       'points', 'percentage', 'flat', 'transaction_amount', 'additive',
       'point_accrual', 'transaction', 'direct', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM card_benefits benefit
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_offers offer
      WHERE offer.benefit_id = benefit.benefit_id AND offer.position = 3
  );

INSERT INTO benefit_rules
    (rule_id, offer_id, position, priority, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, rounding_type, rounding_unit, created_at, updated_at)
SELECT UUID(), offer.offer_id, 1, 100, '조건 없는 전 가맹점 기본적립',
       'grant', 'additive', 0.2, 'percent', 'floor', 1,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
WHERE offer.offer_name = '전 가맹점 기본적립 0.2%'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules rule_data
      WHERE rule_data.offer_id = offer.offer_id AND rule_data.position = 1
  );

INSERT INTO benefit_rules
    (rule_id, offer_id, position, priority, rule_name, rule_effect, stacking_mode,
     reward_value, reward_unit, previous_spend_min_krw,
     rounding_type, rounding_unit, created_at, updated_at)
SELECT UUID(), offer.offer_id, 1, 200, '전월 30만원 이상 생활 영역 추가적립',
       'grant', 'additive', 0.3, 'percent', 300000,
       'floor', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_offers offer
WHERE offer.offer_name = '생활 영역 추가적립 0.3%'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rules rule_data
      WHERE rule_data.offer_id = offer.offer_id AND rule_data.position = 1
  );

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, 1, 'include', 'all_merchants',
       NULL, NULL, 'ALL', '전 가맹점',
       'ALL_MERCHANTS', 'ALL_MERCHANTS', 0.000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
WHERE offer.offer_name = '전 가맹점 기본적립 0.2%'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets target
      WHERE target.rule_id = rule_data.rule_id
        AND target.match_mode = 'include'
        AND target.target_type = 'all_merchants'
  );

CREATE TEMPORARY TABLE nh_point_extra_merchant_seed (
    condition_group SMALLINT UNSIGNED NOT NULL,
    merchant_name VARCHAR(150) NOT NULL
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

INSERT INTO nh_point_extra_merchant_seed (condition_group, merchant_name)
VALUES
    (1, '하나로마트'),
    (2, '농협몰'),
    (3, 'GS25'),
    (4, 'CU'),
    (5, '올리브영'),
    (6, 'CGV'),
    (7, '스타벅스'),
    (8, '파리바게뜨');

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, seed.condition_group, 'include', 'merchant',
       NULL, merchant.merchant_id, seed.merchant_name, seed.merchant_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
CROSS JOIN nh_point_extra_merchant_seed seed
INNER JOIN merchants merchant ON merchant.normalized_name = seed.merchant_name
WHERE offer.offer_name = '생활 영역 추가적립 0.3%'
  AND NOT EXISTS (
      SELECT 1 FROM benefit_rule_targets target
      WHERE target.rule_id = rule_data.rule_id
        AND target.condition_group = seed.condition_group
        AND target.match_mode = 'include'
        AND target.target_type = 'merchant'
        AND target.merchant_id = merchant.merchant_id
  );

DROP TEMPORARY TABLE nh_point_extra_merchant_seed;

-- 구조화 상태는 reward_type과 별도로 관리한다.
UPDATE card_benefits
SET structuring_status = 'EXCLUDED',
    structuring_note = '혜택 레코드가 아니므로 추천 구조화 대상에서 제외'
WHERE record_type <> 'benefit';

UPDATE card_benefits benefit
INNER JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
SET benefit.structuring_status = 'UNSUPPORTED',
    benefit.structuring_note = CONCAT('현재 지도 추천 미지원 reward_type: ', offer.reward_type)
WHERE benefit.record_type = 'benefit'
  AND offer.reward_type IN (
      'fee_waiver', 'voucher', 'free_service', 'installment', 'insurance'
  );

UPDATE card_benefits benefit
SET benefit.structuring_status = 'STRUCTURED',
    benefit.structuring_note = NULL
WHERE EXISTS (
    SELECT 1
    FROM benefit_offers offer
    INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
    INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
    WHERE offer.benefit_id = benefit.benefit_id
      AND target.match_mode = 'include'
      AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
      AND rule_data.rule_effect = 'grant'
      AND rule_data.reward_value IS NOT NULL
      AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
      AND target.target_type IN ('all_merchants', 'merchant_category', 'merchant')
);

UPDATE card_benefits benefit
SET benefit.structuring_status = 'PARSE_FAILED',
    benefit.structuring_note = '금전성 문구는 있으나 안전한 offer/rule/target 구조화가 완료되지 않음'
WHERE benefit.record_type = 'benefit'
  AND benefit.structuring_status = 'RAW'
  AND CONCAT_WS(' ', benefit.title, benefit.summary) REGEXP
      '할인|적립|캐시백|포인트|마일리지|청구';

UPDATE card_benefits benefit
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET benefit.structuring_status = 'PARTIAL',
    benefit.structuring_note = '기본 0.2%와 생활 영역 추가 0.3%만 구조화; 하나로고객·해외 조건은 미지원'
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점';
