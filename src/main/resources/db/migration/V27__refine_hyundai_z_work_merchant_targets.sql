-- 현대카드Z work Edition2: 브랜드 대상과 오프라인 입점 매장 판정 보정
DELETE target
FROM benefit_rule_targets target
JOIN benefit_rules rule_data ON rule_data.rule_id = target.rule_id
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
WHERE card.gorilla_card_id = '2680'
  AND offer.offer_name IN ('편의점 10% 청구 할인', '커피전문점 10% 청구 할인')
  AND target.target_type = 'merchant_category';

INSERT INTO benefit_rule_targets
    (target_id, rule_id, condition_group, match_mode, target_type,
     merchant_category_id, merchant_id, target_code, target_name,
     target_source, target_authority, minimum_place_confidence, created_at, updated_at)
SELECT UUID(), rule_data.rule_id, brands.condition_group, 'include', 'merchant',
       NULL, merchant.merchant_id, brands.merchant_name, brands.merchant_name,
       'CARD_BENEFIT_EXPLICIT', 'MERCHANT_EXACT', 0.990, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM (
    SELECT '편의점 10% 청구 할인' offer_name, 1 condition_group, 'GS25' merchant_name
    UNION ALL SELECT '편의점 10% 청구 할인', 2, 'CU'
    UNION ALL SELECT '편의점 10% 청구 할인', 3, '세븐일레븐'
    UNION ALL SELECT '편의점 10% 청구 할인', 4, '이마트24'
    UNION ALL SELECT '커피전문점 10% 청구 할인', 1, '스타벅스'
    UNION ALL SELECT '커피전문점 10% 청구 할인', 2, '투썸플레이스'
    UNION ALL SELECT '커피전문점 10% 청구 할인', 3, '커피빈'
    UNION ALL SELECT '커피전문점 10% 청구 할인', 4, '폴바셋'
) brands
JOIN cards card ON card.gorilla_card_id = '2680'
JOIN card_content_versions version ON version.card_id = card.card_id
JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
    AND offer.offer_name = brands.offer_name
JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
JOIN merchants merchant ON merchant.normalized_name = brands.merchant_name
WHERE NOT EXISTS (
    SELECT 1 FROM benefit_rule_targets existing
    WHERE existing.rule_id = rule_data.rule_id
      AND existing.match_mode = 'include'
      AND existing.target_type = 'merchant'
      AND existing.target_code = brands.merchant_name
);
