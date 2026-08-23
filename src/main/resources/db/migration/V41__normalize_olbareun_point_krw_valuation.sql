-- NH포인트는 리포트 금액 집계에서 1포인트를 1원으로 환산한다.
UPDATE benefit_offers offer
INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET offer.reward_type = 'points',
    offer.valuation_scope = 'transaction',
    offer.valuation_method = 'direct',
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND offer.offer_name IN ('전 가맹점 기본적립 0.2%', '생활 영역 추가적립 0.3%');
