ALTER TABLE benefit_rules
    ADD COLUMN rule_schema_version SMALLINT UNSIGNED NULL AFTER valid_to,
    ADD COLUMN rule_support_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY'
        AFTER rule_schema_version,
    ADD COLUMN rule_definition_json JSON NULL AFTER rule_support_status,
    ADD CONSTRAINT chk_benefit_rules_json_pair CHECK (
        (rule_schema_version IS NULL AND rule_definition_json IS NULL)
        OR (rule_schema_version IS NOT NULL AND rule_definition_json IS NOT NULL)
    ),
    ADD CONSTRAINT chk_benefit_rules_support_status CHECK (
        rule_support_status IN ('LEGACY', 'SUPPORTED', 'PARTIAL', 'INFORMATION_ONLY')
    ),
    ADD INDEX idx_benefit_rules_json_status
        (rule_support_status, rule_schema_version, offer_id, priority);

-- 관계형 target은 후보 가맹점 필터로 유지하고, CODEF 승인과 내부 원장만으로 확정 가능한
-- 실적·산식·횟수 조건부터 JSON evaluator로 전환한다.
UPDATE benefit_rules rule_data
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'SUPPORTED',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'conditions', JSON_OBJECT(
            'all', JSON_ARRAY(
                JSON_OBJECT(
                    'type', 'PREVIOUS_MONTH_SPEND',
                    'operator', 'GTE',
                    'value', CAST(rule_data.previous_spend_min_krw AS CHAR),
                    'rejectionReason', 'PERFORMANCE_NOT_MET'
                )
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'DISCOUNT',
            'rewardUnit', 'KRW',
            'calculation', 'RATE',
            'rate', CAST(rule_data.reward_value / 100 AS CHAR)
        ),
        'limits', JSON_ARRAY(
            JSON_OBJECT('type', 'DAILY_USAGE_COUNT', 'value', '1')
        )
    )
WHERE card.gorilla_card_id = '2680'
  AND offer.offer_name IN (
      '온라인 쇼핑 10% 청구 할인',
      '편의점 10% 청구 할인',
      '커피전문점 10% 청구 할인',
      '도서 10% 청구 할인'
  );

-- SOL Plan 기본 적립은 상위 실적 룰에서 하위 룰까지 함께 적용되지 않도록 구간을
-- 상호 배타적으로 정의한다. 해외 승인내역은 현재 수집 경계 밖이므로 PARTIAL로 표시한다.
UPDATE benefit_rules rule_data
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'PARTIAL',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'conditions', JSON_OBJECT(
            'all', JSON_ARRAY(
                JSON_OBJECT(
                    'type', 'PREVIOUS_MONTH_SPEND',
                    'operator', 'GTE',
                    'value', '400000',
                    'rejectionReason', 'PERFORMANCE_NOT_MET'
                ),
                JSON_OBJECT(
                    'type', 'PREVIOUS_MONTH_SPEND',
                    'operator', 'LT',
                    'value', '1000000',
                    'rejectionReason', 'PERFORMANCE_NOT_MET'
                )
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'POINT',
            'rewardUnit', 'POINT',
            'calculation', 'RATE',
            'rate', '0.01'
        ),
        'limits', JSON_ARRAY()
    )
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '국내/외 전가맹점 기본 적립'
  AND rule_data.position = 1;

UPDATE benefit_rules rule_data
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'PARTIAL',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'conditions', JSON_OBJECT(
            'all', JSON_ARRAY(
                JSON_OBJECT(
                    'type', 'PREVIOUS_MONTH_SPEND',
                    'operator', 'GTE',
                    'value', '1000000',
                    'rejectionReason', 'PERFORMANCE_NOT_MET'
                )
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'POINT',
            'rewardUnit', 'POINT',
            'calculation', 'RATE',
            'rate', '0.015'
        ),
        'limits', JSON_ARRAY()
    )
WHERE card.gorilla_card_id = '2899'
  AND offer.offer_name = '국내/외 전가맹점 기본 적립'
  AND rule_data.position = 2;
