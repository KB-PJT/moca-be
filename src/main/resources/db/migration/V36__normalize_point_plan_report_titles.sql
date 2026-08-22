-- Point Plan 체크 캐릭터형의 월 통합 한도 리포트는 결제 구간이 아닌 혜택 영역으로 표시한다.
UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions content ON content.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = content.card_id
SET rule_data.rule_name = CASE offer.exclusive_group_key
        WHEN 'SHINHAN_POINT_PLAN_CHECK_DAILY_LIVING' THEN '일상 생활비'
        WHEN 'SHINHAN_POINT_PLAN_CHECK_CONVENIENCE' THEN '편의점'
        ELSE rule_data.rule_name
    END,
    rule_data.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = '2890'
  AND offer.exclusive_group_key IN (
      'SHINHAN_POINT_PLAN_CHECK_DAILY_LIVING',
      'SHINHAN_POINT_PLAN_CHECK_CONVENIENCE'
  );
