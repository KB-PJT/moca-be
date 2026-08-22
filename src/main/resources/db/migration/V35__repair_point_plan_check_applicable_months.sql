-- V23 seed 적재 후 후속 migration을 적용하는 환경의 월별 Point Plan 한도를 복구한다.
UPDATE benefit_limit_policies
SET applicable_months_json = JSON_ARRAY(1, 2, 3, 4, 6, 7, 8, 9, 10, 11),
    updated_at = UTC_TIMESTAMP(6)
WHERE limit_policy_id = '28900000-0000-4000-8000-000000000201';

UPDATE benefit_limit_policies
SET applicable_months_json = JSON_ARRAY(5, 12),
    updated_at = UTC_TIMESTAMP(6)
WHERE limit_policy_id = '28900000-0000-4000-8000-000000000202';
