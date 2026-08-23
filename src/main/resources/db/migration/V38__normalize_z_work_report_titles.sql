-- 이미 적용된 V37의 checksum을 변경하지 않고 현대카드Z work 리포트 제목만 카테고리로 정규화한다.
UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions content
    ON content.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = content.card_id
SET offer.report_title = CASE benefit.position
        WHEN 2 THEN '편의점'
        WHEN 3 THEN '커피전문점'
        WHEN 5 THEN '도서'
        ELSE offer.report_title
    END,
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '2680'
  AND offer.report_visible = TRUE
  AND benefit.position IN (2, 3, 5);
