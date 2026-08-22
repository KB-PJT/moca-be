-- V23 seed 적재 후 후속 migration을 적용하는 환경의 월별 Point Plan 한도를 복구한다.
UPDATE benefit_limit_policies
SET applicable_months_json = JSON_ARRAY(1, 2, 3, 4, 6, 7, 8, 9, 10, 11),
    updated_at = UTC_TIMESTAMP(6)
WHERE policy_name = '일상 생활비 일반월 한도'
  AND offer_id IN (
      SELECT offer_id
      FROM benefit_offers
      WHERE offer_name = '일상 생활비 포인트 적립');

UPDATE benefit_limit_policies
SET applicable_months_json = JSON_ARRAY(5, 12),
    updated_at = UTC_TIMESTAMP(6)
WHERE policy_name = '일상 생활비 가족행사월 한도'
  AND offer_id IN (
      SELECT offer_id
      FROM benefit_offers
      WHERE offer_name = '일상 생활비 포인트 적립');
