-- 공식 안내는 온라인 채널이 아니라 무신사·솔드아웃 가맹점 승인을 할인 기준으로 둔다.
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
                JSON_OBJECT('type', 'PREVIOUS_MONTH_SPEND', 'operator', 'GTE',
                            'value', '300000', 'rejectionReason', 'PERFORMANCE_NOT_MET')
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'DISCOUNT', 'rewardUnit', 'KRW',
            'calculation', 'RATE', 'rate', '0.05'
        ),
        'limits', JSON_ARRAY()
    )
WHERE card.gorilla_card_id = '733'
  AND offer.offer_name = '무신사/솔드아웃 5% 할인';

UPDATE benefit_rule_targets target
JOIN benefit_rules rule_data ON rule_data.rule_id = target.rule_id
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
SET target.condition_group = CASE target.target_code
        WHEN '무신사' THEN 1
        WHEN '솔드아웃' THEN 2
        ELSE target.condition_group
    END,
    target.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '733'
  AND offer.offer_name = '무신사/솔드아웃 5% 할인'
  AND target.match_mode = 'include'
  AND target.target_code IN ('무신사', '솔드아웃');
