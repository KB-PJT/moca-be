-- =====================================================================
-- MOCA HOME SIMULATION FIXTURE
-- Target schema: V1 ~ V9
-- MySQL 8.x
--
-- 목적
--  1) Mock 사용자 1명
--  2) 카드고릴라 기반 실제 카드 4장 등록
--     - KB국민 My WE:SH
--     - 신한카드 Mr.Life
--     - 삼성카드 taptap O
--     - 삼성 iD SELECT UP 카드
--  3) 2026-08 홈 화면용 승인내역
--  4) 카드별 이번 달 사용액 / 실제 혜택 합산
--  5) 최근 전체 내역
--  6) taptap O 영화 혜택 엣지케이스
--     - 9,900원 최소금액 미달
--     - 10,000원 경계값
--     - 60,000원 결제에도 정액 5,000원
--     - 일 1회 초과
--     - 월 2회 초과
--     - 비대상 가맹점
--     - 전월 실적 충족
--
-- 주의
--  * 카드고릴라 주요 혜택을 바탕으로 만든 테스트 fixture다.
--  * My WE:SH / Mr.Life의 일부 세부 한도·시간 조건은 테스트 목적상 단순화했다.
--  * taptap O 영화 시나리오는
--      CGV·롯데시네마 10,000원 이상 -> 5,000원 할인
--      일 1회 / 월 2회 / 연 12회
--    조건을 모델링한다.
--  * V5의 card_payment_approvals는 취소/부분취소를 저장하지 않으므로
--    취소 시나리오는 이 fixture에서 제외한다.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 공통 시각
-- ---------------------------------------------------------------------
SET @NOW := '2026-08-10 18:00:00.000000';
SET @SIMULATION_USER_ID := COALESCE(
    @SIMULATION_USER_ID,
    '10000000-0000-0000-0000-000000000001'
);
SET @SIMULATION_GOOGLE_SUBJECT := COALESCE(
    @SIMULATION_GOOGLE_SUBJECT,
    'mock-google-subject-minji-001'
);
SET @SIMULATION_NICKNAME := COALESCE(@SIMULATION_NICKNAME, '김민지');
SET @SIMULATION_EMAIL := COALESCE(@SIMULATION_EMAIL, 'minji.mock@moca.test');

-- ---------------------------------------------------------------------
-- 1. 사용자
-- ---------------------------------------------------------------------
INSERT INTO users (
    user_id, google_subject, nickname, email, user_type,
    location_recommendation_enabled, card_sort_mode,
    created_at, updated_at
) SELECT
    @SIMULATION_USER_ID,
    @SIMULATION_GOOGLE_SUBJECT,
    @SIMULATION_NICKNAME,
    @SIMULATION_EMAIL,
    'user',
    TRUE,
    'AUTO',
    @NOW, @NOW
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE user_id = @SIMULATION_USER_ID
);

INSERT INTO user_notification_settings (
    user_id,
    performance_closing_enabled,
    nearby_benefit_enabled,
    benefit_limit_enabled,
    marketing_enabled,
    created_at, updated_at
) SELECT
    @SIMULATION_USER_ID,
    TRUE, TRUE, TRUE, FALSE,
    @NOW, @NOW
WHERE NOT EXISTS (
    SELECT 1
    FROM user_notification_settings
    WHERE user_id = @SIMULATION_USER_ID
);

-- ---------------------------------------------------------------------
-- 2. 카드사
-- ---------------------------------------------------------------------
INSERT INTO issuers (
    issuer_id, institution_code, issuer_name,
    created_at, updated_at,
    requires_id, requires_password, requires_card_no,
    requires_card_password, requires_birth_date,
    performance_lookback_months
) SELECT
    '11000000-0000-0000-0000-000000000001',
    'KBMOCK0001', 'KB국민카드',
    @NOW, @NOW,
    TRUE, TRUE, FALSE, FALSE, TRUE,
    12
WHERE NOT EXISTS (SELECT 1 FROM issuers WHERE issuer_name = 'KB국민카드');

INSERT INTO issuers (
    issuer_id, institution_code, issuer_name,
    created_at, updated_at,
    requires_id, requires_password, requires_card_no,
    requires_card_password, requires_birth_date,
    performance_lookback_months
) SELECT
    '11000000-0000-0000-0000-000000000002',
    'SHMOCK0001', '신한카드',
    @NOW, @NOW,
    TRUE, TRUE, FALSE, FALSE, TRUE,
    0
WHERE NOT EXISTS (SELECT 1 FROM issuers WHERE issuer_name = '신한카드');

INSERT INTO issuers (
    issuer_id, institution_code, issuer_name,
    created_at, updated_at,
    requires_id, requires_password, requires_card_no,
    requires_card_password, requires_birth_date,
    performance_lookback_months
) SELECT
    '11000000-0000-0000-0000-000000000003',
    'SSMOCK0001', '삼성카드',
    @NOW, @NOW,
    TRUE, TRUE, FALSE, FALSE, TRUE,
    NULL
WHERE NOT EXISTS (SELECT 1 FROM issuers WHERE issuer_name = '삼성카드');

SET @KB_ISSUER_ID := (SELECT issuer_id FROM issuers WHERE issuer_name = 'KB국민카드');
SET @SH_ISSUER_ID := (SELECT issuer_id FROM issuers WHERE issuer_name = '신한카드');
SET @SS_ISSUER_ID := (SELECT issuer_id FROM issuers WHERE issuer_name = '삼성카드');

-- ---------------------------------------------------------------------
-- 3. CODEF 연결 mock
-- ---------------------------------------------------------------------
INSERT INTO codef_account_credentials (
    codef_account_credential_id,
    user_id,
    issuer_id,
    connected_id,
    account_id_enc,
    account_password_enc,
    birth_date_enc,
    credential_identity_hash,
    status,
    last_used_at,
    created_at,
    updated_at
) VALUES
(
    '12000000-0000-0000-0000-000000000001',
    @SIMULATION_USER_ID,
    @KB_ISSUER_ID,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    X'01', X'02', X'03',
    REPEAT('a', 64),
    'active', @NOW, @NOW, @NOW
),
(
    '12000000-0000-0000-0000-000000000002',
    @SIMULATION_USER_ID,
    @SH_ISSUER_ID,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    X'01', X'02', X'03',
    REPEAT('b', 64),
    'active', @NOW, @NOW, @NOW
),
(
    '12000000-0000-0000-0000-000000000003',
    @SIMULATION_USER_ID,
    @SS_ISSUER_ID,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
    X'01', X'02', X'03',
    REPEAT('c', 64),
    'active', @NOW, @NOW, @NOW
);

-- ---------------------------------------------------------------------
-- 4. 카드 카탈로그
-- gorilla_card_id:
--   My WE:SH = 2441
--   Mr.Life   = 13
--   taptap O  = 51
--   iD SELECT UP = 2986
-- ---------------------------------------------------------------------
INSERT INTO cards (
    card_id, gorilla_card_id, issuer_id, card_type,
    first_seen_at, last_seen_at
) SELECT
    '13000000-0000-0000-0000-000000000001',
    '2441',
    @KB_ISSUER_ID,
    'credit',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM cards WHERE gorilla_card_id = '2441');

INSERT INTO cards (
    card_id, gorilla_card_id, issuer_id, card_type,
    first_seen_at, last_seen_at
) SELECT
    '13000000-0000-0000-0000-000000000002',
    '13',
    @SH_ISSUER_ID,
    'credit',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM cards WHERE gorilla_card_id = '13');

INSERT INTO cards (
    card_id, gorilla_card_id, issuer_id, card_type,
    first_seen_at, last_seen_at
) SELECT
    '13000000-0000-0000-0000-000000000003',
    '51',
    @SS_ISSUER_ID,
    'credit',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM cards WHERE gorilla_card_id = '51');

INSERT INTO cards (
    card_id, gorilla_card_id, issuer_id, card_type,
    first_seen_at, last_seen_at
) SELECT
    '13000000-0000-0000-0000-000000000004',
    '2986',
    @SS_ISSUER_ID,
    'credit',
    '2026-04-21 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM cards WHERE gorilla_card_id = '2986');

SET @KB_CARD_ID := (SELECT card_id FROM cards WHERE gorilla_card_id = '2441');
SET @SH_CARD_ID := (SELECT card_id FROM cards WHERE gorilla_card_id = '13');
SET @SS_CARD_ID := (SELECT card_id FROM cards WHERE gorilla_card_id = '51');
SET @SELECT_UP_CARD_ID := (SELECT card_id FROM cards WHERE gorilla_card_id = '2986');

INSERT INTO card_content_versions (
    content_version_id, card_id, content_sha256,
    name, annual_fee_summary, annual_fee_detail,
    representative_spend, discontinued,
    main_benefits,
    event_title, event_detail_text, event_detail_html,
    image_url, source_url,
    first_seen_at, last_seen_at
) SELECT
    '14000000-0000-0000-0000-000000000001',
    @KB_CARD_ID,
    REPEAT('1', 64),
    'KB국민 My WE:SH 카드',
    NULL, NULL,
    400000, FALSE,
    '음식점·편의점 등 생활영역 할인 / 선택 서비스팩',
    NULL, NULL, NULL,
    NULL,
    'https://www.card-gorilla.com/card/detail/2441',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM card_content_versions WHERE card_id = @KB_CARD_ID);

INSERT INTO card_content_versions (
    content_version_id, card_id, content_sha256,
    name, annual_fee_summary, annual_fee_detail,
    representative_spend, discontinued,
    main_benefits,
    event_title, event_detail_text, event_detail_html,
    image_url, source_url,
    first_seen_at, last_seen_at
) SELECT
    '14000000-0000-0000-0000-000000000002',
    @SH_CARD_ID,
    REPEAT('2', 64),
    '신한카드 Mr.Life',
    NULL, NULL,
    300000, FALSE,
    '편의점·병원/약국·세탁소 등 10% 할인',
    NULL, NULL, NULL,
    NULL,
    'https://www.card-gorilla.com/card/detail/13',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM card_content_versions WHERE card_id = @SH_CARD_ID);

INSERT INTO card_content_versions (
    content_version_id, card_id, content_sha256,
    name, annual_fee_summary, annual_fee_detail,
    representative_spend, discontinued,
    main_benefits,
    event_title, event_detail_text, event_detail_html,
    image_url, source_url,
    first_seen_at, last_seen_at
) SELECT
    '14000000-0000-0000-0000-000000000003',
    @SS_CARD_ID,
    REPEAT('3', 64),
    '삼성카드 taptap O',
    NULL, NULL,
    300000, FALSE,
    '스타벅스·교통·영화 등 생활 할인',
    NULL, NULL, NULL,
    NULL,
    'https://www.card-gorilla.com/card/detail/51',
    '2026-01-01 00:00:00.000000', @NOW
WHERE NOT EXISTS (SELECT 1 FROM card_content_versions WHERE card_id = @SS_CARD_ID);

INSERT INTO card_content_versions (
    content_version_id, card_id, content_sha256,
    name, annual_fee_summary, annual_fee_detail,
    representative_spend, discontinued,
    main_benefits,
    event_title, event_detail_text, event_detail_html,
    image_url, source_url,
    first_seen_at, last_seen_at
) SELECT
    '14000000-0000-0000-0000-000000000004',
    @SELECT_UP_CARD_ID,
    REPEAT('4', 64),
    '삼성 iD SELECT UP 카드',
    NULL, NULL,
    500000, FALSE,
    '의료 20% 또는 생활 영역 10% 중 매월 택 1',
    NULL, NULL, NULL,
    NULL,
    'https://www.card-gorilla.com/card/detail/2986',
    '2026-04-21 00:00:00.000000', @NOW
WHERE NOT EXISTS (
    SELECT 1 FROM card_content_versions WHERE card_id = @SELECT_UP_CARD_ID
);

SET @KB_CONTENT_VERSION_ID := (
    SELECT content_version_id FROM card_content_versions
    WHERE card_id = @KB_CARD_ID ORDER BY last_seen_at DESC, content_version_id DESC LIMIT 1
);
SET @SH_CONTENT_VERSION_ID := (
    SELECT content_version_id FROM card_content_versions
    WHERE card_id = @SH_CARD_ID ORDER BY last_seen_at DESC, content_version_id DESC LIMIT 1
);
SET @SS_CONTENT_VERSION_ID := (
    SELECT content_version_id FROM card_content_versions
    WHERE card_id = @SS_CARD_ID ORDER BY last_seen_at DESC, content_version_id DESC LIMIT 1
);
SET @SELECT_UP_CONTENT_VERSION_ID := (
    SELECT content_version_id FROM card_content_versions
    WHERE card_id = @SELECT_UP_CARD_ID
    ORDER BY last_seen_at DESC, content_version_id DESC LIMIT 1
);

-- ---------------------------------------------------------------------
-- 5. 전월실적 구간
-- ---------------------------------------------------------------------
INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000001',
    @KB_CONTENT_VERSION_ID,
    1, 400000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers WHERE content_version_id = @KB_CONTENT_VERSION_ID
);

INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000002',
    @SH_CONTENT_VERSION_ID,
    1, 300000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers WHERE content_version_id = @SH_CONTENT_VERSION_ID
);

INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000003',
    @SS_CONTENT_VERSION_ID,
    1, 300000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers WHERE content_version_id = @SS_CONTENT_VERSION_ID
);

INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000004',
    @SELECT_UP_CONTENT_VERSION_ID,
    1, 500000, 999999
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers
    WHERE content_version_id = @SELECT_UP_CONTENT_VERSION_ID
);

INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000005',
    @SELECT_UP_CONTENT_VERSION_ID,
    2, 1000000, 1499999
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers
    WHERE content_version_id = @SELECT_UP_CONTENT_VERSION_ID AND tier_number = 2
);

INSERT INTO card_performance_tiers (
    performance_tier_id, content_version_id, tier_number,
    minimum_spend_krw, maximum_spend_krw
) SELECT
    '15000000-0000-0000-0000-000000000006',
    @SELECT_UP_CONTENT_VERSION_ID,
    3, 1500000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM card_performance_tiers
    WHERE content_version_id = @SELECT_UP_CONTENT_VERSION_ID AND tier_number = 3
);

-- ---------------------------------------------------------------------
-- 6. 사용자 보유 카드 4장
-- ---------------------------------------------------------------------
INSERT INTO user_cards (
    user_card_id, user_id, card_id,
    codef_account_credential_id,
    card_name_from_codef, card_no,
    card_number_enc, card_password_enc,
    issuer_id,
    display_order, is_active,
    codef_card_key_hash,
    memo,
    created_at, updated_at
) VALUES
(
    '20000000-0000-0000-0000-000000000001',
    @SIMULATION_USER_ID,
    @KB_CARD_ID,
    '12000000-0000-0000-0000-000000000001',
    'KB국민 My WE:SH 카드',
    '123456******1001',
    NULL, NULL,
    @KB_ISSUER_ID,
    1, TRUE,
    REPEAT('d', 64),
    '생활비/편의점 위주',
    @NOW, @NOW
),
(
    '20000000-0000-0000-0000-000000000002',
    @SIMULATION_USER_ID,
    @SH_CARD_ID,
    '12000000-0000-0000-0000-000000000002',
    '신한카드 Mr.Life',
    '234567******2002',
    NULL, NULL,
    @SH_ISSUER_ID,
    2, TRUE,
    REPEAT('e', 64),
    '병원/약국/편의점 생활비',
    @NOW, @NOW
),
(
    '20000000-0000-0000-0000-000000000003',
    @SIMULATION_USER_ID,
    @SS_CARD_ID,
    '12000000-0000-0000-0000-000000000003',
    '삼성카드 taptap O',
    '345678******3003',
    NULL, NULL,
    @SS_ISSUER_ID,
    3, TRUE,
    REPEAT('f', 64),
    '영화/카페/교통',
    @NOW, @NOW
),
(
    '20000000-0000-0000-0000-000000000004',
    @SIMULATION_USER_ID,
    @SELECT_UP_CARD_ID,
    '12000000-0000-0000-0000-000000000003',
    '삼성 iD SELECT UP 카드',
    '456789******4004',
    NULL, NULL,
    @SS_ISSUER_ID,
    4, TRUE,
    REPEAT('9', 64),
    '의료 선택형 혜택',
    @NOW, @NOW
);

-- ---------------------------------------------------------------------
-- 6-1. taptap O 라이프스타일 패키지 선택
-- 원문은 패키지 1~6 중 하나를 매월 선택하며, fixture 사용자는 패키지 1을 선택한다.
-- ---------------------------------------------------------------------
INSERT INTO card_option_groups (
    option_group_id, card_id, group_key, group_name,
    selection_required, created_at, updated_at
) VALUES (
    '46000000-0000-0000-0000-000000000001',
    @SS_CARD_ID,
    'TAPTAP_O_LIFESTYLE_PACKAGE',
    '라이프스타일 패키지',
    TRUE, @NOW, @NOW
);

INSERT INTO card_option_choices (
    option_choice_id, option_group_id, choice_key, choice_name,
    created_at, updated_at
) VALUES
('46100000-0000-0000-0000-000000000001','46000000-0000-0000-0000-000000000001','PACKAGE_1','패키지 1',@NOW,@NOW),
('46100000-0000-0000-0000-000000000002','46000000-0000-0000-0000-000000000001','PACKAGE_2','패키지 2',@NOW,@NOW),
('46100000-0000-0000-0000-000000000003','46000000-0000-0000-0000-000000000001','PACKAGE_3','패키지 3',@NOW,@NOW),
('46100000-0000-0000-0000-000000000004','46000000-0000-0000-0000-000000000001','PACKAGE_4','패키지 4',@NOW,@NOW),
('46100000-0000-0000-0000-000000000005','46000000-0000-0000-0000-000000000001','PACKAGE_5','패키지 5',@NOW,@NOW),
('46100000-0000-0000-0000-000000000006','46000000-0000-0000-0000-000000000001','PACKAGE_6','패키지 6',@NOW,@NOW);

INSERT INTO user_card_option_selections (
    user_card_id, option_group_id, card_id, option_choice_id,
    selected_at, created_at, updated_at
) VALUES (
    '20000000-0000-0000-0000-000000000003',
    '46000000-0000-0000-0000-000000000001',
    @SS_CARD_ID,
    '46100000-0000-0000-0000-000000000001',
    @NOW, @NOW, @NOW
);

-- ---------------------------------------------------------------------
-- 6-2. 삼성 iD SELECT UP SELECT 서비스 선택
-- 의료 20%와 생활 영역 10% 중 하나를 매월 선택하며, fixture 사용자는 의료를 선택한다.
-- ---------------------------------------------------------------------
INSERT INTO card_option_groups (
    option_group_id, card_id, group_key, group_name,
    selection_required, created_at, updated_at
) VALUES (
    '46000000-0000-0000-0000-000000000002',
    @SELECT_UP_CARD_ID,
    'ID_SELECT_UP_SELECT_SERVICE',
    'SELECT 서비스',
    TRUE, @NOW, @NOW
);

INSERT INTO card_option_choices (
    option_choice_id, option_group_id, choice_key, choice_name,
    created_at, updated_at
) VALUES
('46100000-0000-0000-0000-000000000007','46000000-0000-0000-0000-000000000002','MEDICAL_20','의료 20% 할인',@NOW,@NOW),
('46100000-0000-0000-0000-000000000008','46000000-0000-0000-0000-000000000002','LIVING_10','생활 영역 10% 할인',@NOW,@NOW);

INSERT INTO user_card_option_selections (
    user_card_id, option_group_id, card_id, option_choice_id,
    selected_at, created_at, updated_at
) VALUES (
    '20000000-0000-0000-0000-000000000004',
    '46000000-0000-0000-0000-000000000002',
    @SELECT_UP_CARD_ID,
    '46100000-0000-0000-0000-000000000007',
    @NOW, @NOW, @NOW
);

-- ---------------------------------------------------------------------
-- 7. 실적 스냅샷
-- 7월 = 8월 혜택 판정용 전월실적
-- 8월 = 홈에서 현재 실적 진행률 표시용
-- ---------------------------------------------------------------------
INSERT INTO user_card_performance_snapshots (
    performance_snapshot_id, user_card_id,
    performance_month, current_spend_amount,
    updated_at, created_at
) VALUES
-- My WE:SH
(
    '21000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '2026-07', 470000, @NOW, @NOW
),
(
    '21000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    '2026-08', 192300, @NOW, @NOW
),
-- Mr.Life
(
    '21000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000002',
    '2026-07', 380000, @NOW, @NOW
),
(
    '21000000-0000-0000-0000-000000000004',
    '20000000-0000-0000-0000-000000000002',
    '2026-08', 197700, @NOW, @NOW
),
-- taptap O
(
    '21000000-0000-0000-0000-000000000005',
    '20000000-0000-0000-0000-000000000003',
    '2026-07', 350000, @NOW, @NOW
),
(
    '21000000-0000-0000-0000-000000000006',
    '20000000-0000-0000-0000-000000000003',
    '2026-08', 269900, @NOW, @NOW
),
-- 삼성 iD SELECT UP: 전월 100만원 경계로 월 10,000원 한도 구간
(
    '21000000-0000-0000-0000-000000000007',
    '20000000-0000-0000-0000-000000000004',
    '2026-07', 1000000, @NOW, @NOW
),
(
    '21000000-0000-0000-0000-000000000008',
    '20000000-0000-0000-0000-000000000004',
    '2026-08', 0, @NOW, @NOW
);

-- ---------------------------------------------------------------------
-- 8. 가맹점 카테고리
-- ---------------------------------------------------------------------
INSERT INTO merchant_categories (
    merchant_category_id, parent_id,
    category_code, category_name,
    display_order,
    created_at, updated_at,
    is_map_visible
) VALUES
('30000000-0000-0000-0000-000000000001', NULL, 'SIM_CAFE',    '카페',      1, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000002', NULL, 'SIM_CONVENIENCE', '편의점',   2, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000003', NULL, 'SIM_FOOD',        '음식점',   3, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000004', NULL, 'SIM_PHARMACY',    '약국',     4, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000005', NULL, 'SIM_HOSPITAL',    '병원',     5, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000006', NULL, 'SIM_LAUNDRY',     '세탁소',   6, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000007', NULL, 'SIM_CINEMA',      '영화관',   7, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000008', NULL, 'SIM_TRANSPORT',   '대중교통', 8, @NOW, @NOW, FALSE),
('30000000-0000-0000-0000-000000000009', NULL, 'SIM_MART',        '대형마트',  9, @NOW, @NOW, TRUE),
('30000000-0000-0000-0000-000000000010', NULL, 'SIM_OPEN_MARKET', '오픈마켓', 10, @NOW, @NOW, FALSE);

-- ---------------------------------------------------------------------
-- 9. 가맹점
-- V8의 has_physical_location으로 실제 매장 존재 여부를 구분.
-- ---------------------------------------------------------------------
INSERT INTO merchants (
    merchant_id, merchant_category_id,
    name, normalized_name, status,
    created_at, updated_at,
    has_physical_location
) VALUES
('31000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000002','GS25 서면점','gs25서면점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000003','본전돼지국밥','본전돼지국밥','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000001','스타벅스 서면점','스타벅스서면점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000002','CU 전포점','cu전포점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000003','개미집 서면점','개미집서면점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000009','이마트 문현점','이마트문현점','active',@NOW,@NOW,TRUE),

('31000000-0000-0000-0000-000000000011','30000000-0000-0000-0000-000000000002','GS25 부산역점','gs25부산역점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000012','30000000-0000-0000-0000-000000000004','서면약국','서면약국','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000005','서면내과','서면내과','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000014','30000000-0000-0000-0000-000000000006','크린토피아 전포점','크린토피아전포점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000015','30000000-0000-0000-0000-000000000003','홍콩반점 서면점','홍콩반점서면점','active',@NOW,@NOW,TRUE),

('31000000-0000-0000-0000-000000000021','30000000-0000-0000-0000-000000000007','CGV 서면','cgv서면','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000022','30000000-0000-0000-0000-000000000007','롯데시네마 부산본점','롯데시네마부산본점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000023','30000000-0000-0000-0000-000000000007','무비티켓 예매대행','무비티켓예매대행','active',@NOW,@NOW,FALSE),
('31000000-0000-0000-0000-000000000024','30000000-0000-0000-0000-000000000001','스타벅스 부산역점','스타벅스부산역점','active',@NOW,@NOW,TRUE),
('31000000-0000-0000-0000-000000000025','30000000-0000-0000-0000-000000000008','부산교통공사','부산교통공사','active',@NOW,@NOW,FALSE);

-- ---------------------------------------------------------------------
-- 10. 혜택 정의
-- ---------------------------------------------------------------------

-- 10-1. My WE:SH - 테스트용 생활영역 10%
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000001',
    @KB_CONTENT_VERSION_ID,
    30001, 'benefit',
    '음식점·편의점 할인',
    '테스트 fixture: 대상 생활영역 10%',
    NULL, NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    NULL,
    '생활영역 10% 할인',
    1, 100, NULL,
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'standalone',
    'statement', 'transaction', 'direct',
    NULL, NULL,
    '2026-01-01', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000001',
    '41000000-0000-0000-0000-000000000001',
    1, 100,
    '전월 40만원 이상 대상영역 10%',
    'grant', 'standalone',
    10, 'percent',
    NULL, NULL,
    400000, NULL,
    NULL, NULL,
    'floor', 1,
    '2026-01-01', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES
('43000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',1,'include','merchant_category','SIM_CONVENIENCE','편의점'),
('43000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000001',1,'include','merchant_category','SIM_FOOD','음식점');

-- 10-2. Mr.Life - 테스트용 TIME 생활영역 10%
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000002',
    @SH_CONTENT_VERSION_ID,
    30001, 'benefit',
    'TIME 할인',
    '편의점·병원/약국·세탁소 10%',
    NULL, NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000002',
    '40000000-0000-0000-0000-000000000002',
    NULL,
    'TIME 10% 할인',
    1, 100, NULL,
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'standalone',
    'statement', 'transaction', 'direct',
    NULL, NULL,
    '2026-01-01', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000002',
    '41000000-0000-0000-0000-000000000002',
    1, 100,
    '전월 30만원 이상 TIME 10%',
    'grant', 'standalone',
    10, 'percent',
    NULL, NULL,
    300000, NULL,
    NULL, NULL,
    'floor', 1,
    '2026-01-01', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES
('43000000-0000-0000-0000-000000000011','42000000-0000-0000-0000-000000000002',1,'include','merchant_category','SIM_CONVENIENCE','편의점'),
('43000000-0000-0000-0000-000000000012','42000000-0000-0000-0000-000000000002',1,'include','merchant_category','SIM_PHARMACY','약국'),
('43000000-0000-0000-0000-000000000013','42000000-0000-0000-0000-000000000002',1,'include','merchant_category','SIM_HOSPITAL','병원'),
('43000000-0000-0000-0000-000000000014','42000000-0000-0000-0000-000000000002',1,'include','merchant_category','SIM_LAUNDRY','세탁소');

-- 10-3. taptap O - 영화 5,000원 정액 할인
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000003',
    @SS_CONTENT_VERSION_ID,
    30001, 'benefit',
    'CGV·롯데시네마 영화 할인',
    '1만원 이상 결제 시 5,000원 할인',
    '전월실적 30만원 이상 / 일 1회 / 월 2회 / 연 12회 테스트 모델',
    NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000003',
    '40000000-0000-0000-0000-000000000003',
    NULL,
    '영화 5,000원 할인',
    1, 100, 'TAPTAP_O_MOVIE',
    'discount', 'fixed_amount', 'KRW',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    5000, 'KRW',
    '2026-01-01', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000003',
    '41000000-0000-0000-0000-000000000003',
    1, 100,
    '전월 30만원 + 1만원 이상 결제 시 5천원',
    'grant', 'not_stackable',
    5000, 'KRW',
    NULL, NULL,
    300000, NULL,
    10000, NULL,
    'none', 1,
    '2026-01-01', NULL
);

-- CGV, 롯데시네마만 포함
INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES
('43000000-0000-0000-0000-000000000021','42000000-0000-0000-0000-000000000003',1,'include','merchant','CGV','CGV'),
('43000000-0000-0000-0000-000000000022','42000000-0000-0000-0000-000000000003',1,'include','merchant','LOTTE_CINEMA','롯데시네마');

-- 일 1회 / 월 2회 / 연 12회
INSERT INTO benefit_limit_policies (
    limit_policy_id, offer_id, policy_name,
    limit_period, limit_type, limit_unit,
    shared_group_key, valid_from, valid_to
) VALUES
(
    '44000000-0000-0000-0000-000000000001',
    '41000000-0000-0000-0000-000000000003',
    '영화 일 1회',
    'daily', 'usage_count', 'count',
    'TAPTAP_O_MOVIE', '2026-01-01', NULL
),
(
    '44000000-0000-0000-0000-000000000002',
    '41000000-0000-0000-0000-000000000003',
    '영화 월 2회',
    'monthly', 'usage_count', 'count',
    'TAPTAP_O_MOVIE', '2026-01-01', NULL
),
(
    '44000000-0000-0000-0000-000000000003',
    '41000000-0000-0000-0000-000000000003',
    '영화 연 12회',
    'yearly', 'usage_count', 'count',
    'TAPTAP_O_MOVIE', '2026-01-01', NULL
);

INSERT INTO benefit_limit_tiers (
    limit_tier_id, limit_policy_id, position,
    limit_value, previous_spend_min_krw, current_spend_min_krw
) VALUES
('45000000-0000-0000-0000-000000000001','44000000-0000-0000-0000-000000000001',1,1,300000,NULL),
('45000000-0000-0000-0000-000000000002','44000000-0000-0000-0000-000000000002',1,2,300000,NULL),
('45000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000003',1,12,300000,NULL);

-- 10-4. taptap O 패키지 1 - 스타벅스 50% 할인(월 10,000원)
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000004',
    @SS_CONTENT_VERSION_ID,
    30002, 'benefit',
    '패키지 1 스타벅스 할인',
    '라이프스타일 패키지 1 선택 시 스타벅스 50% 할인',
    '전월 30만원 이상 / 커피 할인 월 한도 10,000원', NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000004',
    NULL,
    '패키지 1 스타벅스 50% 할인',
    1, 100, 'TAPTAP_O_PACKAGE_1_COFFEE',
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    NULL, NULL,
    '2026-01-01', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000004',
    '41000000-0000-0000-0000-000000000004',
    1, 100,
    '패키지 1 전월 30만원 이상 스타벅스 50%',
    'grant', 'not_stackable',
    50, 'percent',
    NULL, NULL,
    300000, NULL,
    NULL, NULL,
    'floor', 1,
    '2026-01-01', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES (
    '43000000-0000-0000-0000-000000000031',
    '42000000-0000-0000-0000-000000000004',
    1, 'include', 'merchant', 'STARBUCKS', '스타벅스'
);

INSERT INTO benefit_limit_policies (
    limit_policy_id, offer_id, policy_name,
    limit_period, limit_type, limit_unit,
    shared_group_key, valid_from, valid_to
) VALUES (
    '44000000-0000-0000-0000-000000000004',
    '41000000-0000-0000-0000-000000000004',
    '패키지 1 커피 할인 월 한도',
    'monthly', 'reward_amount', 'KRW',
    'TAPTAP_O_PACKAGE_1_COFFEE', '2026-01-01', NULL
);

INSERT INTO benefit_limit_tiers (
    limit_tier_id, limit_policy_id, position,
    limit_value, previous_spend_min_krw, current_spend_min_krw
) VALUES (
    '45000000-0000-0000-0000-000000000004',
    '44000000-0000-0000-0000-000000000004',
    1, 10000, 300000, NULL
);

-- 10-5. taptap O 패키지 1 - 오픈마켓 7% 할인(월 5,000원)
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000005',
    @SS_CONTENT_VERSION_ID,
    30003, 'benefit',
    '패키지 1 오픈마켓 할인',
    '라이프스타일 패키지 1 선택 시 G마켓·옥션·11번가 7% 할인',
    '전월 30만원 이상 / 쇼핑 할인 월 한도 5,000원', NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000005',
    '40000000-0000-0000-0000-000000000005',
    NULL,
    '패키지 1 오픈마켓 7% 할인',
    1, 100, 'TAPTAP_O_PACKAGE_1_SHOPPING',
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    NULL, NULL,
    '2026-01-01', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000005',
    '41000000-0000-0000-0000-000000000005',
    1, 100,
    '패키지 1 전월 30만원 이상 오픈마켓 7%',
    'grant', 'not_stackable',
    7, 'percent',
    NULL, NULL,
    300000, NULL,
    NULL, NULL,
    'floor', 1,
    '2026-01-01', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES (
    '43000000-0000-0000-0000-000000000032',
    '42000000-0000-0000-0000-000000000005',
    1, 'include', 'merchant_category', 'SIM_OPEN_MARKET', '오픈마켓'
);

INSERT INTO benefit_limit_policies (
    limit_policy_id, offer_id, policy_name,
    limit_period, limit_type, limit_unit,
    shared_group_key, valid_from, valid_to
) VALUES (
    '44000000-0000-0000-0000-000000000005',
    '41000000-0000-0000-0000-000000000005',
    '패키지 1 쇼핑 할인 월 한도',
    'monthly', 'reward_amount', 'KRW',
    'TAPTAP_O_PACKAGE_1_SHOPPING', '2026-01-01', NULL
);

INSERT INTO benefit_limit_tiers (
    limit_tier_id, limit_policy_id, position,
    limit_value, previous_spend_min_krw, current_spend_min_krw
) VALUES (
    '45000000-0000-0000-0000-000000000005',
    '44000000-0000-0000-0000-000000000005',
    1, 5000, 300000, NULL
);

INSERT INTO benefit_offer_option_requirements (
    requirement_id, offer_id, option_group_id, option_choice_id,
    created_at, updated_at
) VALUES
(
    '46200000-0000-0000-0000-000000000001',
    '41000000-0000-0000-0000-000000000004',
    '46000000-0000-0000-0000-000000000001',
    '46100000-0000-0000-0000-000000000001',
    @NOW, @NOW
),
(
    '46200000-0000-0000-0000-000000000002',
    '41000000-0000-0000-0000-000000000005',
    '46000000-0000-0000-0000-000000000001',
    '46100000-0000-0000-0000-000000000001',
    @NOW, @NOW
);

-- 10-6. 삼성 iD SELECT UP - 의료 20% 또는 생활 영역 10% 중 택 1
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES
(
    '40000000-0000-0000-0000-000000000006',
    @SELECT_UP_CONTENT_VERSION_ID,
    30001, 'benefit',
    'SELECT 의료 할인',
    'SELECT 서비스에서 의료 선택 시 병원·약국 20% 할인',
    '전월 50만원/100만원/150만원 이상 시 통합 월 7천원/1만원/1만5천원', NULL
),
(
    '40000000-0000-0000-0000-000000000007',
    @SELECT_UP_CONTENT_VERSION_ID,
    30002, 'benefit',
    'SELECT 생활 영역 할인',
    'SELECT 서비스에서 생활 선택 시 보험·주유·이동통신 10% 할인',
    '전월 50만원/100만원/150만원 이상 시 통합 월 7천원/1만원/1만5천원', NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES
(
    '41000000-0000-0000-0000-000000000006',
    '40000000-0000-0000-0000-000000000006', NULL,
    'SELECT 의료 20% 할인',
    1, 100, 'ID_SELECT_UP_SELECT_SERVICE',
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    NULL, NULL, '2026-04-21', NULL
),
(
    '41000000-0000-0000-0000-000000000007',
    '40000000-0000-0000-0000-000000000007', NULL,
    'SELECT 생활 영역 10% 할인',
    1, 100, 'ID_SELECT_UP_SELECT_SERVICE',
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    NULL, NULL, '2026-04-21', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES
(
    '42000000-0000-0000-0000-000000000006',
    '41000000-0000-0000-0000-000000000006',
    1, 100, 'SELECT 의료 20%', 'grant', 'not_stackable',
    20, 'percent', NULL, NULL, 500000, NULL, NULL, NULL,
    'floor', 1, '2026-04-21', NULL
),
(
    '42000000-0000-0000-0000-000000000007',
    '41000000-0000-0000-0000-000000000007',
    1, 100, 'SELECT 생활 영역 10%', 'grant', 'not_stackable',
    10, 'percent', NULL, NULL, 500000, NULL, NULL, NULL,
    'floor', 1, '2026-04-21', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES
('43000000-0000-0000-0000-000000000041','42000000-0000-0000-0000-000000000006',1,'include','merchant_category','SIM_HOSPITAL','병원'),
('43000000-0000-0000-0000-000000000042','42000000-0000-0000-0000-000000000006',1,'include','merchant_category','SIM_PHARMACY','약국'),
('43000000-0000-0000-0000-000000000043','42000000-0000-0000-0000-000000000007',1,'include','merchant_category','SIM_LIVING','보험·주유·이동통신');

INSERT INTO benefit_limit_policies (
    limit_policy_id, offer_id, policy_name,
    limit_period, limit_type, limit_unit,
    shared_group_key, valid_from, valid_to
) VALUES
('44000000-0000-0000-0000-000000000006','41000000-0000-0000-0000-000000000006','SELECT 의료 통합 월 할인한도','monthly','reward_amount','KRW','ID_SELECT_UP_SELECT_SERVICE','2026-04-21',NULL),
('44000000-0000-0000-0000-000000000007','41000000-0000-0000-0000-000000000007','SELECT 생활 통합 월 할인한도','monthly','reward_amount','KRW','ID_SELECT_UP_SELECT_SERVICE','2026-04-21',NULL);

INSERT INTO benefit_limit_tiers (
    limit_tier_id, limit_policy_id, position,
    limit_value, previous_spend_min_krw, current_spend_min_krw
) VALUES
('45000000-0000-0000-0000-000000000006','44000000-0000-0000-0000-000000000006',1,7000,500000,NULL),
('45000000-0000-0000-0000-000000000007','44000000-0000-0000-0000-000000000006',2,10000,1000000,NULL),
('45000000-0000-0000-0000-000000000008','44000000-0000-0000-0000-000000000006',3,15000,1500000,NULL),
('45000000-0000-0000-0000-000000000009','44000000-0000-0000-0000-000000000007',1,7000,500000,NULL),
('45000000-0000-0000-0000-000000000010','44000000-0000-0000-0000-000000000007',2,10000,1000000,NULL),
('45000000-0000-0000-0000-000000000011','44000000-0000-0000-0000-000000000007',3,15000,1500000,NULL);

INSERT INTO benefit_offer_option_requirements (
    requirement_id, offer_id, option_group_id, option_choice_id,
    created_at, updated_at
) VALUES
('46200000-0000-0000-0000-000000000003','41000000-0000-0000-0000-000000000006','46000000-0000-0000-0000-000000000002','46100000-0000-0000-0000-000000000007',@NOW,@NOW),
('46200000-0000-0000-0000-000000000004','41000000-0000-0000-0000-000000000007','46000000-0000-0000-0000-000000000002','46100000-0000-0000-0000-000000000008',@NOW,@NOW);

-- 10-7. 삼성 iD SELECT UP 기본 서비스 - 여가 2% 할인(통합 월 500,000원)
INSERT INTO card_benefits (
    benefit_id, content_version_id, position, record_type,
    title, summary, detail_text, detail_html
) VALUES (
    '40000000-0000-0000-0000-000000000008',
    @SELECT_UP_CONTENT_VERSION_ID,
    30003, 'benefit',
    '기본 여가 할인',
    '골프·항공·면세점·철도·공연 2% 할인',
    '전월 이용금액 조건 없음 / 여가 영역 통합 월 할인한도 500,000원', NULL
);

INSERT INTO benefit_offers (
    offer_id, benefit_id, reward_program_id,
    offer_name, position, priority, exclusive_group_key,
    reward_type, value_type, value_unit,
    calculation_mode, calculation_basis, stacking_mode,
    reward_timing, valuation_scope, valuation_method,
    reference_value_krw, reference_value_unit,
    valid_from, valid_to
) VALUES (
    '41000000-0000-0000-0000-000000000008',
    '40000000-0000-0000-0000-000000000008', NULL,
    '기본 여가 2% 할인',
    1, 50, 'ID_SELECT_UP_LEISURE',
    'discount', 'percentage', 'percent',
    'flat', 'transaction_amount', 'not_stackable',
    'statement', 'transaction', 'direct',
    NULL, NULL, '2026-04-21', NULL
);

INSERT INTO benefit_rules (
    rule_id, offer_id, position, priority,
    rule_name, rule_effect, stacking_mode,
    reward_value, reward_unit,
    reward_basis_amount, reward_basis_unit,
    previous_spend_min_krw, current_spend_min_krw,
    transaction_min_krw, transaction_max_krw,
    rounding_type, rounding_unit,
    valid_from, valid_to
) VALUES (
    '42000000-0000-0000-0000-000000000008',
    '41000000-0000-0000-0000-000000000008',
    1, 50, '기본 여가 2%', 'grant', 'not_stackable',
    2, 'percent', NULL, NULL, NULL, NULL, NULL, NULL,
    'floor', 1, '2026-04-21', NULL
);

INSERT INTO benefit_rule_targets (
    target_id, rule_id, condition_group, match_mode,
    target_type, target_code, target_name
) VALUES
('43000000-0000-0000-0000-000000000044','42000000-0000-0000-0000-000000000008',1,'include','merchant_category','SIM_GOLF','골프'),
('43000000-0000-0000-0000-000000000045','42000000-0000-0000-0000-000000000008',1,'include','merchant_category','SIM_AIRLINE','항공'),
('43000000-0000-0000-0000-000000000046','42000000-0000-0000-0000-000000000008',1,'include','merchant_category','SIM_DUTY_FREE','면세점'),
('43000000-0000-0000-0000-000000000047','42000000-0000-0000-0000-000000000008',1,'include','merchant_category','SIM_RAIL','철도'),
('43000000-0000-0000-0000-000000000048','42000000-0000-0000-0000-000000000008',1,'include','merchant_category','SIM_PERFORMANCE','공연');

INSERT INTO benefit_limit_policies (
    limit_policy_id, offer_id, policy_name,
    limit_period, limit_type, limit_unit,
    shared_group_key, valid_from, valid_to
) VALUES (
    '44000000-0000-0000-0000-000000000008',
    '41000000-0000-0000-0000-000000000008',
    '기본 여가 통합 월 할인한도',
    'monthly', 'reward_amount', 'KRW',
    'ID_SELECT_UP_LEISURE', '2026-04-21', NULL
);

INSERT INTO benefit_limit_tiers (
    limit_tier_id, limit_policy_id, position,
    limit_value, previous_spend_min_krw, current_spend_min_krw
) VALUES (
    '45000000-0000-0000-0000-000000000012',
    '44000000-0000-0000-0000-000000000008',
    1, 500000, NULL, NULL
);

-- ---------------------------------------------------------------------
-- 11. 카드 승인내역
-- ---------------------------------------------------------------------

-- 11-1 My WE:SH: 총 192,300원
INSERT INTO card_payment_approvals (
    approval_id, user_id, user_card_id, merchant_id,
    approval_number, approved_at, merchant_name,
    amount, approval_status, source_payload, created_at
) VALUES
('50000000-0000-0000-0000-000000000001',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000001','KB-0801-01','2026-08-01 08:13:00','GS25 서면점',6800,'approved',JSON_OBJECT('scenario','eligible_convenience'),'2026-08-01 08:13:01'),
('50000000-0000-0000-0000-000000000002',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000002','KB-0801-02','2026-08-01 12:32:00','본전돼지국밥',11000,'approved',JSON_OBJECT('scenario','eligible_food'),'2026-08-01 12:32:01'),
('50000000-0000-0000-0000-000000000003',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000003','KB-0802-01','2026-08-02 15:17:00','스타벅스 서면점',6500,'approved',JSON_OBJECT('scenario','non_target_cafe'),'2026-08-02 15:17:01'),
('50000000-0000-0000-0000-000000000004',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000005','KB-0803-01','2026-08-03 19:42:00','개미집 서면점',32000,'approved',JSON_OBJECT('scenario','eligible_food'),'2026-08-03 19:42:01'),
('50000000-0000-0000-0000-000000000005',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000004','KB-0804-01','2026-08-04 08:21:00','CU 전포점',5900,'approved',JSON_OBJECT('scenario','eligible_convenience'),'2026-08-04 08:21:01'),
('50000000-0000-0000-0000-000000000006',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000002','KB-0806-01','2026-08-06 12:14:00','본전돼지국밥',10900,'approved',JSON_OBJECT('scenario','eligible_food'),'2026-08-06 12:14:01'),
('50000000-0000-0000-0000-000000000007',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000006','KB-0809-01','2026-08-09 18:43:00','이마트 문현점',109400,'approved',JSON_OBJECT('scenario','non_target_mart'),'2026-08-09 18:43:01'),
('50000000-0000-0000-0000-000000000008',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000001','31000000-0000-0000-0000-000000000005','KB-0810-01','2026-08-10 13:10:00','개미집 서면점',9800,'approved',JSON_OBJECT('scenario','eligible_food'),'2026-08-10 13:10:01');

-- 11-2 Mr.Life: 총 197,700원
INSERT INTO card_payment_approvals (
    approval_id, user_id, user_card_id, merchant_id,
    approval_number, approved_at, merchant_name,
    amount, approval_status, source_payload, created_at
) VALUES
('50000000-0000-0000-0000-000000000011',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000011','SH-0801-01','2026-08-01 18:20:00','GS25 부산역점',12400,'approved',JSON_OBJECT('scenario','time_convenience'),'2026-08-01 18:20:01'),
('50000000-0000-0000-0000-000000000012',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000012','SH-0802-01','2026-08-02 10:32:00','서면약국',18000,'approved',JSON_OBJECT('scenario','time_pharmacy'),'2026-08-02 10:32:01'),
('50000000-0000-0000-0000-000000000013',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000013','SH-0804-01','2026-08-04 11:10:00','서면내과',21000,'approved',JSON_OBJECT('scenario','time_hospital'),'2026-08-04 11:10:01'),
('50000000-0000-0000-0000-000000000014',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000014','SH-0805-01','2026-08-05 17:54:00','크린토피아 전포점',15000,'approved',JSON_OBJECT('scenario','time_laundry'),'2026-08-05 17:54:01'),
('50000000-0000-0000-0000-000000000015',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000015','SH-0806-01','2026-08-06 12:21:00','홍콩반점 서면점',9500,'approved',JSON_OBJECT('scenario','non_time_target_food'),'2026-08-06 12:21:01'),
('50000000-0000-0000-0000-000000000016',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000011','SH-0808-01','2026-08-08 16:18:00','GS25 부산역점',7800,'approved',JSON_OBJECT('scenario','time_convenience'),'2026-08-08 16:18:01'),
('50000000-0000-0000-0000-000000000017',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000006','SH-0809-01','2026-08-09 15:00:00','이마트 문현점',84000,'approved',JSON_OBJECT('scenario','not_in_fixture_benefit'),'2026-08-09 15:00:01'),
('50000000-0000-0000-0000-000000000018',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000002','31000000-0000-0000-0000-000000000012','SH-0810-01','2026-08-10 17:20:00','서면약국',30000,'approved',JSON_OBJECT('scenario','time_pharmacy'),'2026-08-10 17:20:01');

-- 11-3 taptap O: 총 269,900원
-- 핵심: 60,000원 결제라도 혜택은 5,000원 고정.
INSERT INTO card_payment_approvals (
    approval_id, user_id, user_card_id, merchant_id,
    approval_number, approved_at, merchant_name,
    amount, approval_status, source_payload, created_at
) VALUES
-- 최소금액 직전: 9,900 -> 0
('50000000-0000-0000-0000-000000000021',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000021','SS-0801-01','2026-08-01 13:00:00','CGV 서면',9900,'approved',
 JSON_OBJECT('scenario','min_amount_below','channel','offline','paymentType','lump_sum'),
 '2026-08-01 13:00:01'),

-- 큰 금액: 60,000 -> 5,000 (정액)
('50000000-0000-0000-0000-000000000022',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000021','SS-0802-01','2026-08-02 14:00:00','CGV 서면',60000,'approved',
 JSON_OBJECT('scenario','fixed_amount_large_payment','channel','offline','paymentType','lump_sum'),
 '2026-08-02 14:00:01'),

-- 정확한 경계값: 10,000 -> 5,000
('50000000-0000-0000-0000-000000000023',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000022','SS-0803-01','2026-08-03 14:00:00','롯데시네마 부산본점',10000,'approved',
 JSON_OBJECT('scenario','min_amount_exact','channel','offline','paymentType','lump_sum'),
 '2026-08-03 14:00:01'),

-- 같은 날 두 번째 영화 결제: 일 1회 초과 -> 0
('50000000-0000-0000-0000-000000000024',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000021','SS-0803-02','2026-08-03 20:30:00','CGV 서면',20000,'approved',
 JSON_OBJECT('scenario','daily_limit_exceeded','channel','offline','paymentType','lump_sum'),
 '2026-08-03 20:30:01'),

-- 8월 정상혜택 2회를 이미 사용했으므로 월 2회 초과 -> 0
('50000000-0000-0000-0000-000000000025',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000022','SS-0804-01','2026-08-04 19:00:00','롯데시네마 부산본점',15000,'approved',
 JSON_OBJECT('scenario','monthly_limit_exceeded','channel','offline','paymentType','lump_sum'),
 '2026-08-04 19:00:01'),

-- 예매 대행: 비대상 가맹점 -> 0
('50000000-0000-0000-0000-000000000026',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000023','SS-0805-01','2026-08-05 21:00:00','무비티켓 예매대행',30000,'approved',
 JSON_OBJECT('scenario','merchant_not_eligible','channel','online_agency','paymentType','lump_sum'),
 '2026-08-05 21:00:01'),

-- 다른 혜택 영역(이번 fixture에선 영화 계산과 분리)
('50000000-0000-0000-0000-000000000027',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000024','SS-0807-01','2026-08-07 09:20:00','스타벅스 부산역점',12000,'approved',
 JSON_OBJECT('scenario','different_benefit_area','channel','offline','paymentType','lump_sum'),
 '2026-08-07 09:20:01'),

('50000000-0000-0000-0000-000000000028',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000025','SS-0808-01','2026-08-08 08:00:00','부산교통공사',50000,'approved',
 JSON_OBJECT('scenario','different_benefit_area','channel','transport','paymentType','lump_sum'),
 '2026-08-08 08:00:01'),

-- 월 한도 소진 뒤 60,000원 재결제 -> 여전히 0
('50000000-0000-0000-0000-000000000029',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000021','SS-0809-01','2026-08-09 18:00:00','CGV 서면',60000,'approved',
 JSON_OBJECT('scenario','large_payment_after_monthly_limit','channel','offline','paymentType','lump_sum'),
 '2026-08-09 18:00:01'),

-- 홈 최근내역 최상단: 최소금액 미달
('50000000-0000-0000-0000-000000000030',@SIMULATION_USER_ID,'20000000-0000-0000-0000-000000000003','31000000-0000-0000-0000-000000000022','SS-0810-01','2026-08-10 18:32:00','롯데시네마 부산본점',3000,'approved',
 JSON_OBJECT('scenario','very_small_payment','channel','offline','paymentType','lump_sum'),
 '2026-08-10 18:32:01');

-- ---------------------------------------------------------------------
-- 12. 계산 결과: My WE:SH
-- ---------------------------------------------------------------------
INSERT INTO user_benefit_calculation_outcomes (
    outcome_id, user_card_id, approval_id,
    offer_id, rule_id, limit_policy_id,
    usage_date, reward_unit,
    expected_reward_value, applied_reward_value, missed_reward_value,
    outcome_status, rejection_reason,
    calculated_at
) VALUES
('60000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-01','KRW',680,680,0,'applied','NONE','2026-08-01 08:13:02'),
('60000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-01','KRW',1100,1100,0,'applied','NONE','2026-08-01 12:32:02'),
('60000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000004','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-03','KRW',3200,3200,0,'applied','NONE','2026-08-03 19:42:02'),
('60000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000005','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-04','KRW',590,590,0,'applied','NONE','2026-08-04 08:21:02'),
('60000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000006','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-06','KRW',1090,1090,0,'applied','NONE','2026-08-06 12:14:02'),
('60000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000008','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'2026-08-10','KRW',980,980,0,'applied','NONE','2026-08-10 13:10:02');

INSERT INTO user_benefit_usages (
    usage_id, user_card_id, offer_id, rule_id,
    limit_policy_id, approval_id, usage_date,
    eligible_amount_krw, reward_amount_krw,
    reward_original_value, reward_original_unit,
    usage_count, usage_status,
    approved_at, confirmed_at
) VALUES
('61000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000001','2026-08-01',6800,680,680,'KRW',1,'confirmed','2026-08-01 08:13:00','2026-08-01 08:13:02'),
('61000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000002','2026-08-01',11000,1100,1100,'KRW',1,'confirmed','2026-08-01 12:32:00','2026-08-01 12:32:02'),
('61000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000004','2026-08-03',32000,3200,3200,'KRW',1,'confirmed','2026-08-03 19:42:00','2026-08-03 19:42:02'),
('61000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000005','2026-08-04',5900,590,590,'KRW',1,'confirmed','2026-08-04 08:21:00','2026-08-04 08:21:02'),
('61000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000006','2026-08-06',10900,1090,1090,'KRW',1,'confirmed','2026-08-06 12:14:00','2026-08-06 12:14:02'),
('61000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001','42000000-0000-0000-0000-000000000001',NULL,'50000000-0000-0000-0000-000000000008','2026-08-10',9800,980,980,'KRW',1,'confirmed','2026-08-10 13:10:00','2026-08-10 13:10:02');

-- ---------------------------------------------------------------------
-- 13. 계산 결과: Mr.Life
-- ---------------------------------------------------------------------
INSERT INTO user_benefit_calculation_outcomes (
    outcome_id, user_card_id, approval_id,
    offer_id, rule_id, limit_policy_id,
    usage_date, reward_unit,
    expected_reward_value, applied_reward_value, missed_reward_value,
    outcome_status, rejection_reason,
    calculated_at
) VALUES
('60000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000011','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-01','KRW',1240,1240,0,'applied','NONE','2026-08-01 18:20:02'),
('60000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000012','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-02','KRW',1800,1800,0,'applied','NONE','2026-08-02 10:32:02'),
('60000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000013','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-04','KRW',2100,2100,0,'applied','NONE','2026-08-04 11:10:02'),
('60000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000014','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-05','KRW',1500,1500,0,'applied','NONE','2026-08-05 17:54:02'),
('60000000-0000-0000-0000-000000000016','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000016','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-08','KRW',780,780,0,'applied','NONE','2026-08-08 16:18:02'),
('60000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000018','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'2026-08-10','KRW',3000,3000,0,'applied','NONE','2026-08-10 17:20:02');

INSERT INTO user_benefit_usages (
    usage_id, user_card_id, offer_id, rule_id,
    limit_policy_id, approval_id, usage_date,
    eligible_amount_krw, reward_amount_krw,
    reward_original_value, reward_original_unit,
    usage_count, usage_status,
    approved_at, confirmed_at
) VALUES
('61000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000011','2026-08-01',12400,1240,1240,'KRW',1,'confirmed','2026-08-01 18:20:00','2026-08-01 18:20:02'),
('61000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000012','2026-08-02',18000,1800,1800,'KRW',1,'confirmed','2026-08-02 10:32:00','2026-08-02 10:32:02'),
('61000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000013','2026-08-04',21000,2100,2100,'KRW',1,'confirmed','2026-08-04 11:10:00','2026-08-04 11:10:02'),
('61000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000014','2026-08-05',15000,1500,1500,'KRW',1,'confirmed','2026-08-05 17:54:00','2026-08-05 17:54:02'),
('61000000-0000-0000-0000-000000000016','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000016','2026-08-08',7800,780,780,'KRW',1,'confirmed','2026-08-08 16:18:00','2026-08-08 16:18:02'),
('61000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000002','41000000-0000-0000-0000-000000000002','42000000-0000-0000-0000-000000000002',NULL,'50000000-0000-0000-0000-000000000018','2026-08-10',30000,3000,3000,'KRW',1,'confirmed','2026-08-10 17:20:00','2026-08-10 17:20:02');

-- ---------------------------------------------------------------------
-- 14. 계산 결과: taptap O 영화
-- ---------------------------------------------------------------------
INSERT INTO user_benefit_calculation_outcomes (
    outcome_id, user_card_id, approval_id,
    offer_id, rule_id, limit_policy_id,
    usage_date, reward_unit,
    expected_reward_value, applied_reward_value, missed_reward_value,
    outcome_status, rejection_reason,
    calculated_at
) VALUES
-- 9,900원: 최소금액 미달
('60000000-0000-0000-0000-000000000021','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000021','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003',NULL,'2026-08-01','KRW',0,0,0,'not_applied','MIN_TRANSACTION_NOT_MET','2026-08-01 13:00:02'),

-- 60,000원: 5,000원 정액
('60000000-0000-0000-0000-000000000022','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000022','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000002','2026-08-02','KRW',5000,5000,0,'applied','NONE','2026-08-02 14:00:02'),

-- 정확히 10,000원: 5,000원 정액
('60000000-0000-0000-0000-000000000023','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000023','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000002','2026-08-03','KRW',5000,5000,0,'applied','NONE','2026-08-03 14:00:02'),

-- 같은 날 2번째: 혜택 자체는 5천원이지만 일 1회 한도로 전부 놓침
('60000000-0000-0000-0000-000000000024','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000024','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000001','2026-08-03','KRW',5000,0,5000,'not_applied','DAILY_USAGE_LIMIT_EXCEEDED','2026-08-03 20:30:02'),

-- 월 정상 사용 2회 소진
('60000000-0000-0000-0000-000000000025','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000025','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000002','2026-08-04','KRW',5000,0,5000,'not_applied','MONTHLY_USAGE_LIMIT_EXCEEDED','2026-08-04 19:00:02'),

-- 예매대행: 아예 대상 가맹점이 아님
('60000000-0000-0000-0000-000000000026','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000026','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003',NULL,'2026-08-05','KRW',0,0,0,'not_applied','MERCHANT_NOT_ELIGIBLE','2026-08-05 21:00:02'),

-- 월 한도 소진 뒤 60,000원 재결제
('60000000-0000-0000-0000-000000000029','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000029','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003','44000000-0000-0000-0000-000000000002','2026-08-09','KRW',5000,0,5000,'not_applied','MONTHLY_USAGE_LIMIT_EXCEEDED','2026-08-09 18:00:02'),

-- 3,000원: 최소금액 미달
('60000000-0000-0000-0000-000000000030','20000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000030','41000000-0000-0000-0000-000000000003','42000000-0000-0000-0000-000000000003',NULL,'2026-08-10','KRW',0,0,0,'not_applied','MIN_TRANSACTION_NOT_MET','2026-08-10 18:32:02');

-- 실제 적용된 영화 혜택은 2건만 usage에 적재
INSERT INTO user_benefit_usages (
    usage_id, user_card_id, offer_id, rule_id,
    limit_policy_id, approval_id, usage_date,
    eligible_amount_krw, reward_amount_krw,
    reward_original_value, reward_original_unit,
    usage_count, usage_status,
    approved_at, confirmed_at
) VALUES
(
    '61000000-0000-0000-0000-000000000022',
    '20000000-0000-0000-0000-000000000003',
    '41000000-0000-0000-0000-000000000003',
    '42000000-0000-0000-0000-000000000003',
    '44000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000022',
    '2026-08-02',
    60000, 5000, 5000, 'KRW',
    1, 'confirmed',
    '2026-08-02 14:00:00',
    '2026-08-02 14:00:02'
),
(
    '61000000-0000-0000-0000-000000000023',
    '20000000-0000-0000-0000-000000000003',
    '41000000-0000-0000-0000-000000000003',
    '42000000-0000-0000-0000-000000000003',
    '44000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000023',
    '2026-08-03',
    10000, 5000, 5000, 'KRW',
    1, 'confirmed',
    '2026-08-03 14:00:00',
    '2026-08-03 14:00:02'
);

-- =====================================================================
-- 검증 Query 1: 홈 카드별 사용금액 + 실제 받은 혜택
-- =====================================================================
SELECT
    uc.user_card_id,
    ccv.name AS card_name,
    COALESCE(p.total_spend, 0) AS monthly_spend,
    COALESCE(b.total_benefit, 0) AS monthly_benefit,
    COALESCE(s.current_spend_amount, 0) AS performance_amount
FROM user_cards uc
JOIN cards c
  ON c.card_id = uc.card_id
JOIN card_content_versions ccv
  ON ccv.card_id = c.card_id
LEFT JOIN (
    SELECT user_card_id, SUM(amount) AS total_spend
    FROM card_payment_approvals
    WHERE approved_at >= '2026-08-01'
      AND approved_at <  '2026-09-01'
    GROUP BY user_card_id
) p ON p.user_card_id = uc.user_card_id
LEFT JOIN (
    SELECT user_card_id, SUM(reward_amount_krw) AS total_benefit
    FROM user_benefit_usages
    WHERE usage_date >= '2026-08-01'
      AND usage_date <  '2026-09-01'
      AND usage_status IN ('pending', 'confirmed')
    GROUP BY user_card_id
) b ON b.user_card_id = uc.user_card_id
LEFT JOIN user_card_performance_snapshots s
  ON s.user_card_id = uc.user_card_id
 AND s.performance_month = '2026-08'
WHERE uc.user_id = @SIMULATION_USER_ID
ORDER BY uc.display_order;

-- =====================================================================
-- 검증 Query 2: 홈 전체 합계
-- =====================================================================
SELECT
    SUM(cpa.amount) AS total_spend
FROM card_payment_approvals cpa
WHERE cpa.user_id = @SIMULATION_USER_ID
  AND cpa.approved_at >= '2026-08-01'
  AND cpa.approved_at <  '2026-09-01';

SELECT
    SUM(ubu.reward_amount_krw) AS total_received_benefit
FROM user_benefit_usages ubu
JOIN user_cards uc
  ON uc.user_card_id = ubu.user_card_id
WHERE uc.user_id = @SIMULATION_USER_ID
  AND ubu.usage_date >= '2026-08-01'
  AND ubu.usage_date <  '2026-09-01'
  AND ubu.usage_status IN ('pending', 'confirmed');

SELECT
    SUM(ubo.missed_reward_value) AS total_missed_benefit
FROM user_benefit_calculation_outcomes ubo
JOIN user_cards uc
  ON uc.user_card_id = ubo.user_card_id
WHERE uc.user_id = @SIMULATION_USER_ID
  AND ubo.usage_date >= '2026-08-01'
  AND ubo.usage_date <  '2026-09-01';

-- =====================================================================
-- 검증 Query 3: 최근 전체 내역 + 실제 혜택
-- =====================================================================
SELECT
    cpa.approval_id,
    cpa.approved_at,
    cpa.merchant_name,
    cpa.amount,
    uc.card_name_from_codef AS card_name,
    COALESCE(SUM(ubu.reward_amount_krw), 0) AS benefit_amount
FROM card_payment_approvals cpa
JOIN user_cards uc
  ON uc.user_card_id = cpa.user_card_id
LEFT JOIN user_benefit_usages ubu
  ON ubu.approval_id = cpa.approval_id
 AND ubu.usage_status IN ('pending', 'confirmed')
WHERE cpa.user_id = @SIMULATION_USER_ID
GROUP BY
    cpa.approval_id, cpa.approved_at,
    cpa.merchant_name, cpa.amount,
    uc.card_name_from_codef
ORDER BY cpa.approved_at DESC
LIMIT 10;

-- =====================================================================
-- 검증 Query 4: taptap O 영화 엣지케이스만 보기
-- =====================================================================
SELECT
    cpa.approved_at,
    cpa.merchant_name,
    cpa.amount,
    JSON_UNQUOTE(JSON_EXTRACT(cpa.source_payload, '$.scenario')) AS scenario,
    ubo.expected_reward_value,
    ubo.applied_reward_value,
    ubo.missed_reward_value,
    ubo.outcome_status,
    ubo.rejection_reason
FROM card_payment_approvals cpa
JOIN user_benefit_calculation_outcomes ubo
  ON ubo.approval_id = cpa.approval_id
WHERE cpa.user_card_id = '20000000-0000-0000-0000-000000000003'
ORDER BY cpa.approved_at;
