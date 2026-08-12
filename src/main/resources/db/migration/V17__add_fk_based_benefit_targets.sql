-- target_code/name은 수집 원문 snapshot으로 보존하고 계산에는 FK를 사용한다.
ALTER TABLE benefit_rule_targets
    ADD COLUMN merchant_category_id CHAR(36) NULL AFTER target_type,
    ADD COLUMN merchant_id CHAR(36) NULL AFTER merchant_category_id,
    ADD COLUMN target_source VARCHAR(32) NOT NULL DEFAULT 'LEGACY' AFTER target_name,
    ADD COLUMN target_authority VARCHAR(24) NOT NULL DEFAULT 'ISSUER_CATEGORY' AFTER target_source,
    ADD COLUMN minimum_place_confidence DECIMAL(4,3) NOT NULL DEFAULT 0.800 AFTER target_authority,
    ADD CONSTRAINT fk_benefit_rule_targets_category
        FOREIGN KEY (merchant_category_id) REFERENCES merchant_categories (merchant_category_id),
    ADD CONSTRAINT fk_benefit_rule_targets_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id),
    ADD CONSTRAINT chk_benefit_rule_targets_source CHECK (target_source IN (
        'CARD_GORILLA_CATEGORY', 'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXPLICIT',
        'MEDICAL_SCOPE', 'ALL_MERCHANTS', 'LEGACY')),
    ADD CONSTRAINT chk_benefit_rule_targets_authority CHECK (target_authority IN (
        'ISSUER_CATEGORY', 'MERCHANT_EXACT', 'ALL_MERCHANTS')),
    ADD CONSTRAINT chk_benefit_rule_targets_min_confidence
        CHECK (minimum_place_confidence BETWEEN 0.000 AND 1.000),
    ADD INDEX idx_brt_category_fk (merchant_category_id, rule_id, match_mode),
    ADD INDEX idx_brt_merchant_fk (merchant_id, rule_id, match_mode);

-- 명시적 동일 코드만 backfill한다. title/detail_html LIKE 추론은 하지 않는다.
UPDATE benefit_rule_targets target
JOIN merchant_categories category
  ON UPPER(TRIM(category.category_code)) = UPPER(TRIM(target.target_code))
SET target.merchant_category_id = category.merchant_category_id
WHERE target.target_type = 'merchant_category';

UPDATE benefit_rule_targets target
JOIN merchants merchant ON merchant.merchant_id = target.target_code
SET target.merchant_id = merchant.merchant_id
WHERE target.target_type = 'merchant';

UPDATE benefit_rule_targets
SET target_authority = CASE target_type
        WHEN 'merchant' THEN 'MERCHANT_EXACT'
        WHEN 'all_merchants' THEN 'ALL_MERCHANTS'
        ELSE 'ISSUER_CATEGORY'
    END,
    target_source = CASE target_type
        WHEN 'merchant' THEN 'MERCHANT_EXPLICIT'
        WHEN 'all_merchants' THEN 'ALL_MERCHANTS'
        ELSE target_source
    END;

-- 기존 channel/payment_method 등의 target은 양쪽 FK가 NULL인 상태로 계속 허용한다.
-- merchant 관련 3종만 정확한 FK 모양을 강제하며, 미해결 legacy 행이 있으면 migration을 실패시킨다.
ALTER TABLE benefit_rule_targets
    ADD CONSTRAINT chk_benefit_rule_target_fk_shape CHECK (
        (target_type = 'merchant_category' AND merchant_category_id IS NOT NULL AND merchant_id IS NULL)
        OR (target_type = 'merchant' AND merchant_id IS NOT NULL AND merchant_category_id IS NULL)
        OR (target_type = 'all_merchants' AND merchant_category_id IS NULL AND merchant_id IS NULL)
        OR (target_type NOT IN ('merchant_category', 'merchant', 'all_merchants')
            AND merchant_category_id IS NULL AND merchant_id IS NULL)
    );
