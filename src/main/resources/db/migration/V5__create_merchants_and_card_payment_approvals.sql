-- 가맹점(카테고리/별칭/카카오 매핑) 스키마와 카드 결제 승인내역 적재 스키마.
-- 승인내역은 CODEF 승인내역 조회(approval-list)로 수집하며, 가맹점 매칭은 이름 완전일치 조회만 수행한다.
-- merchant_locations / merchant_location_match_histories(지도 기반)는 해당 기능이 필요해질 때 함께 추가한다.

CREATE TABLE merchant_categories (
    merchant_category_id CHAR(36) NOT NULL,
    parent_id CHAR(36) NULL,
    category_code VARCHAR(50) NULL,
    category_name VARCHAR(100) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (merchant_category_id),
    CONSTRAINT uk_merchant_categories_code UNIQUE (category_code),
    CONSTRAINT fk_merchant_categories_parent
        FOREIGN KEY (parent_id) REFERENCES merchant_categories (merchant_category_id),
    INDEX idx_merchant_categories_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchants (
    merchant_id CHAR(36) NOT NULL,
    merchant_category_id CHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (merchant_id),
    CONSTRAINT fk_merchants_category
        FOREIGN KEY (merchant_category_id) REFERENCES merchant_categories (merchant_category_id),
    CONSTRAINT chk_merchants_status CHECK (status IN ('active', 'inactive', 'merged')),
    INDEX idx_merchants_normalized_name (normalized_name),
    INDEX idx_merchants_category (merchant_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchant_aliases (
    merchant_alias_id CHAR(36) NOT NULL,
    merchant_id CHAR(36) NOT NULL,
    alias_name VARCHAR(150) NOT NULL,
    normalized_alias_name VARCHAR(150) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (merchant_alias_id),
    CONSTRAINT uk_merchant_aliases_normalized UNIQUE (normalized_alias_name),
    CONSTRAINT fk_merchant_aliases_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id),
    CONSTRAINT chk_merchant_aliases_source_type
        CHECK (source_type IN ('kakao', 'codef', 'benefit_text', 'manual')),
    INDEX idx_merchant_aliases_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kakao_category_maps (
    kakao_category_map_id CHAR(36) NOT NULL,
    merchant_category_id CHAR(36) NOT NULL,
    kakao_category_group_code VARCHAR(20) NULL,
    kakao_category_name_pattern VARCHAR(255) NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (kakao_category_map_id),
    CONSTRAINT fk_kakao_category_maps_category
        FOREIGN KEY (merchant_category_id) REFERENCES merchant_categories (merchant_category_id),
    INDEX idx_kakao_category_maps_lookup (enabled, priority, kakao_category_group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 카드 결제 승인내역. 취소/부분취소/거절 및 해외결제는 적재 대상이 아니므로 approval_status는 'approved' 고정이다.
-- merchant_id는 가맹점명 완전일치 매칭에 성공한 경우에만 채워지며, 실패 시 NULL로 둔다.
-- approval_number가 NULL이면 (user_card_id, approval_number) UNIQUE는 MySQL에서 NULL을 서로 다른 값으로 취급해
-- 중복을 막지 못한다. 그래서 승인번호가 있으면 그 값, 없으면 (승인시각·금액·가맹점명) 조합을 담는
-- non-null 생성 컬럼(dedupe_key)을 두고 여기에 UNIQUE 제약을 걸어, 동시 요청이 같은 승인건을 동시에
-- 조회해도 DB가 원자적으로 중복 INSERT를 막게 한다(애플리케이션의 사전 조회는 최적화일 뿐 유일한 방어선이 아니다).
CREATE TABLE card_payment_approvals (
    approval_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    merchant_id CHAR(36) NULL,
    approval_number VARCHAR(100) NULL,
    approved_at DATETIME(6) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    approval_status VARCHAR(20) NOT NULL,
    source_payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    dedupe_key VARCHAR(320) AS (
        COALESCE(approval_number, CONCAT('N|', approved_at, '|', amount, '|', merchant_name))
    ) STORED NOT NULL,
    PRIMARY KEY (approval_id),
    CONSTRAINT uk_card_payment_approvals_card_dedupe
        UNIQUE (user_card_id, dedupe_key),
    CONSTRAINT fk_card_payment_approvals_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_card_payment_approvals_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_card_payment_approvals_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id),
    CONSTRAINT chk_card_payment_approvals_status CHECK (approval_status IN ('approved')),
    INDEX idx_card_payment_approvals_user_card_approved_at (user_card_id, approved_at),
    INDEX idx_card_payment_approvals_user_approved_at (user_id, approved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- card_content_versions.source_payload(V4에서 추가)는 애플리케이션 코드에서 한 번도 쓰이지 않아 제거한다.
ALTER TABLE card_content_versions
    DROP COLUMN source_payload;
