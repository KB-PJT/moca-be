-- 올바른POINT체크카드 기본 적립은 전월 실적 조건이 없다.
-- 기존 seed가 기본 적립에도 30만원 조건을 잘못 넣은 배포 DB를 보정한다.
UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET rule_data.previous_spend_min_krw = NULL,
    rule_data.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND offer.offer_name = '전 가맹점 기본적립 0.2%'
  AND rule_data.position = 1;
