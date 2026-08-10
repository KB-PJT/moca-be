-- 홈 카드 캐러셀 기본 정렬을 서버 추천(AUTO) 대신 사용자가 저장한 순서(MANUAL)로 바꾼다.
-- 신규 가입자에게만 적용되며, 기존 사용자가 이미 저장한 card_sort_mode 값은 그대로 둔다.
ALTER TABLE users
    ALTER COLUMN card_sort_mode SET DEFAULT 'MANUAL';
