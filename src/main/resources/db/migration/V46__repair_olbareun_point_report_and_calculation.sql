-- 올바른POINT체크카드의 기본·추가 적립을 승인 계산과 혜택 리포트에 노출한다.
-- V23은 두 rule을 additive로 저장했지만, 승인 계산 후보는 standalone rule을 조회한다.
UPDATE benefit_offers offer
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET offer.report_visible = TRUE,
    offer.report_title = CASE
        WHEN offer.offer_name = '전 가맹점 기본적립 0.2%' THEN '모든 가맹점'
        ELSE '생활 영역'
    END,
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND offer.offer_name IN ('전 가맹점 기본적립 0.2%', '생활 영역 추가적립 0.3%')
  AND NOT (
      offer.report_visible <=> TRUE
      AND offer.report_title <=> CASE
          WHEN offer.offer_name = '전 가맹점 기본적립 0.2%' THEN '모든 가맹점'
          ELSE '생활 영역'
      END
  )
  AND NOT EXISTS (
      SELECT 1
      FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );

UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET rule_data.stacking_mode = 'standalone',
    rule_data.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND offer.offer_name IN ('전 가맹점 기본적립 0.2%', '생활 영역 추가적립 0.3%')
  AND NOT (rule_data.stacking_mode <=> 'standalone')
  AND NOT EXISTS (
      SELECT 1
      FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );
