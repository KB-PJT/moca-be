-- 현대카드Z work Edition2에서 승인 데이터로 판별할 수 없는 혜택은 리포트에서 제외한다.
ALTER TABLE benefit_offers
    ADD COLUMN report_visible BOOLEAN NOT NULL DEFAULT TRUE AFTER valuation_method,
    ADD COLUMN report_title VARCHAR(255) NULL AFTER report_visible;

UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions content
    ON content.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = content.card_id
SET offer.report_visible = CASE
        WHEN benefit.position IN (1, 4) THEN FALSE
        ELSE TRUE
    END,
    offer.report_title = CASE benefit.position
        WHEN 2 THEN '편의점 청구 할인'
        WHEN 3 THEN '커피전문점 청구 할인'
        WHEN 5 THEN '도서 청구 할인'
        ELSE NULL
    END,
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '2680';
