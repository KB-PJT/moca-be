-- =====================================================================
-- MOCA HOME SIMULATION FIXTURE -> LOCAL USER LINK
-- Target schema: V1 ~ V9 / MySQL 8.x
--
-- 실행 순서
--  1) home-simulation-cardgorilla.sql 실행
--  2) 이 SQL 실행
--
-- 이 SQL은 fixture의 mock CODEF 연결, 보유 카드, 승인내역 소유자를
-- 아래 로컬 로그인 사용자로 이전한다. 사용자 프로필과 OAuth 정보는 수정하지 않는다.
-- 혜택 계산 결과, 혜택 사용 내역, 실적 스냅샷은 user_card_id를 참조하므로
-- 보유 카드 소유자 변경과 함께 해당 사용자의 홈 조회에 연결된다.
-- =====================================================================

SET NAMES utf8mb4;

SET @TARGET_USER_ID := '37411c29-5adc-4643-8ca5-8fa1c14abf1d';
SET @FIXTURE_USER_ID := '10000000-0000-0000-0000-000000000001';

-- 실행 전 확인: target_user_count=1, fixture_card_count=3이어야 한다.
SELECT
    (SELECT COUNT(*) FROM users WHERE user_id = @TARGET_USER_ID) AS target_user_count,
    (SELECT COUNT(*) FROM user_cards WHERE user_id = @FIXTURE_USER_ID) AS fixture_card_count,
    (SELECT COUNT(*) FROM card_payment_approvals WHERE user_id = @FIXTURE_USER_ID)
        AS fixture_approval_count;

START TRANSACTION;

-- fixture가 생성한 가짜 CODEF 자격 증명만 실제 사용자 소유로 변경한다.
UPDATE codef_account_credentials
SET user_id = @TARGET_USER_ID,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE user_id = @FIXTURE_USER_ID
  AND codef_account_credential_id IN (
      '12000000-0000-0000-0000-000000000001',
      '12000000-0000-0000-0000-000000000002',
      '12000000-0000-0000-0000-000000000003'
  );

-- 보유 카드 3장을 실제 로그인 사용자에게 연결한다.
UPDATE user_cards
SET user_id = @TARGET_USER_ID,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE user_id = @FIXTURE_USER_ID
  AND user_card_id IN (
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000002',
      '20000000-0000-0000-0000-000000000003'
  );

-- 승인내역의 비정규화된 user_id도 보유 카드 소유자와 동일하게 맞춘다.
UPDATE card_payment_approvals
SET user_id = @TARGET_USER_ID
WHERE user_id = @FIXTURE_USER_ID
  AND user_card_id IN (
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000002',
      '20000000-0000-0000-0000-000000000003'
  );

COMMIT;

-- =====================================================================
-- 연결 결과 검증
-- 기대값: codef_credential_count=3, card_count=3, approval_count=26
-- =====================================================================
SELECT
    (SELECT COUNT(*)
     FROM codef_account_credentials
     WHERE user_id = @TARGET_USER_ID
       AND codef_account_credential_id LIKE '12000000-0000-0000-0000-00000000000%')
        AS codef_credential_count,
    (SELECT COUNT(*)
     FROM user_cards
     WHERE user_id = @TARGET_USER_ID
       AND user_card_id LIKE '20000000-0000-0000-0000-00000000000%')
        AS card_count,
    (SELECT COUNT(*)
     FROM card_payment_approvals
     WHERE user_id = @TARGET_USER_ID
       AND user_card_id LIKE '20000000-0000-0000-0000-00000000000%')
        AS approval_count;

-- 카드별 홈 시뮬레이션 집계
SELECT
    user_card.user_card_id,
    content.name AS card_name,
    COALESCE(SUM(approval.amount), 0) AS august_spend,
    COALESCE(benefit.received_benefit, 0) AS august_received_benefit,
    COALESCE(performance.current_spend_amount, 0) AS august_performance
FROM user_cards user_card
INNER JOIN cards card ON card.card_id = user_card.card_id
INNER JOIN card_content_versions content ON content.card_id = card.card_id
LEFT JOIN card_payment_approvals approval
    ON approval.user_card_id = user_card.user_card_id
   AND approval.approved_at >= '2026-08-01'
   AND approval.approved_at < '2026-09-01'
LEFT JOIN (
    SELECT user_card_id, SUM(reward_amount_krw) AS received_benefit
    FROM user_benefit_usages
    WHERE usage_date >= '2026-08-01'
      AND usage_date < '2026-09-01'
      AND usage_status IN ('pending', 'confirmed')
    GROUP BY user_card_id
) benefit ON benefit.user_card_id = user_card.user_card_id
LEFT JOIN user_card_performance_snapshots performance
    ON performance.user_card_id = user_card.user_card_id
   AND performance.performance_month = '2026-08'
WHERE user_card.user_id = @TARGET_USER_ID
  AND user_card.user_card_id IN (
      '20000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000002',
      '20000000-0000-0000-0000-000000000003'
  )
GROUP BY
    user_card.user_card_id,
    content.name,
    benefit.received_benefit,
    performance.current_spend_amount,
    user_card.display_order
ORDER BY user_card.display_order;
