-- 노리2 체크카드의 온라인·자동이체 혜택은 안내 정보로만 보존하고 계산에서는 제외한다.
UPDATE benefit_rules rule_data
JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = 'INFORMATION_ONLY',
    rule_data.rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'mode', 'INFORMATION_ONLY',
        'reason', 'ONLINE_OR_AUTOPAY_CALCULATION_OUT_OF_SCOPE'
    )
WHERE card.gorilla_card_id = '2422'
  AND offer.offer_name IN (
      '구글플레이스토어·앱스토어 10% 할인',
      '인터파크 티켓 10% 할인',
      '넷플릭스·유튜브 프리미엄 1,000원 할인',
      '배달의민족·요기요 1,000원 할인',
      '통신요금 2,500원 할인'
  );
