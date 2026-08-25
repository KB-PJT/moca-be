-- 무신사·솔드아웃 청구 할인은 의류·패션 소비 영역으로 표시한다.
-- 혜택명(offer_name)은 원문을 유지하고 리포트 카테고리만 보정한다.
UPDATE benefit_offers offer
JOIN card_benefits benefit
  ON benefit.benefit_id = offer.benefit_id
JOIN card_content_versions content
  ON content.content_version_id = benefit.content_version_id
JOIN cards card
  ON card.card_id = content.card_id
SET offer.report_title = '패션·의류',
    offer.report_visible = TRUE,
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '733'
  AND offer.offer_name IN (
      '무신사/솔드아웃 5% 할인',
      '무신사 및 솔드아웃 5% 청구 할인'
  );
