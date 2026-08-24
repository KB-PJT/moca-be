-- 올바른POINT체크카드에서 현재 계산 가능한 기본·생활 영역 룰을 계산 대상으로 승격한다.
-- 온라인·해외 등 판별할 수 없는 별도 룰은 이 migration의 대상이 아니다.
UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer
    ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit
    ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version
    ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card
    ON card.card_id = version.card_id
SET rule_data.rule_support_status = 'SUPPORTED',
    rule_data.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '360'
  AND benefit.title = '모든가맹점'
  AND offer.offer_name IN (
      '전 가맹점 기본적립 0.2%',
      '생활 영역 추가적립 0.3%'
  )
  AND rule_data.rule_support_status <> 'SUPPORTED'
  AND NOT EXISTS (
      SELECT 1
      FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );
