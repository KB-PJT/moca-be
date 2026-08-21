-- 카드별 혜택 영역과 월간 사용액을 일반화한다.
-- DREAM 외에도 월간 최다 영역을 사용하는 다른 상품에서 재사용할 수 있다.
CREATE TABLE IF NOT EXISTS benefit_area_groups (
    area_group_id CHAR(36) NOT NULL,
    group_key VARCHAR(80) NOT NULL,
    group_name VARCHAR(150) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (area_group_id),
    CONSTRAINT uk_benefit_area_groups_key UNIQUE (group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS benefit_areas (
    area_id CHAR(36) NOT NULL,
    area_group_id CHAR(36) NOT NULL,
    area_key VARCHAR(80) NOT NULL,
    area_name VARCHAR(150) NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (area_id),
    CONSTRAINT fk_benefit_areas_group FOREIGN KEY (area_group_id) REFERENCES benefit_area_groups(area_group_id),
    CONSTRAINT uk_benefit_areas_key UNIQUE (area_group_id, area_key),
    CONSTRAINT uk_benefit_areas_order UNIQUE (area_group_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS benefit_area_targets (
    area_target_id CHAR(36) NOT NULL,
    area_id CHAR(36) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    merchant_category_id CHAR(36) NULL,
    merchant_id CHAR(36) NULL,
    target_code VARCHAR(100) NOT NULL,
    match_mode VARCHAR(10) NOT NULL DEFAULT 'include',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (area_target_id),
    CONSTRAINT fk_benefit_area_targets_area FOREIGN KEY (area_id) REFERENCES benefit_areas(area_id),
    CONSTRAINT fk_benefit_area_targets_category FOREIGN KEY (merchant_category_id) REFERENCES merchant_categories(merchant_category_id),
    CONSTRAINT fk_benefit_area_targets_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id),
    CONSTRAINT uk_benefit_area_targets UNIQUE (area_id, target_type, target_code, match_mode),
    CONSTRAINT chk_benefit_area_targets_mode CHECK (match_mode IN ('include', 'exclude')),
    CONSTRAINT chk_benefit_area_targets_type CHECK (target_type IN ('merchant', 'merchant_category', 'all_merchants'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_benefit_area_monthly_spends (
    monthly_spend_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    area_group_id CHAR(36) NOT NULL,
    area_id CHAR(36) NOT NULL,
    usage_month CHAR(7) NOT NULL,
    eligible_amount_krw DECIMAL(18,2) NOT NULL DEFAULT 0,
    transaction_count INT UNSIGNED NOT NULL DEFAULT 0,
    selected_rank SMALLINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (monthly_spend_id),
    CONSTRAINT fk_user_area_spend_card FOREIGN KEY (user_card_id) REFERENCES user_cards(user_card_id),
    CONSTRAINT fk_user_area_spend_group FOREIGN KEY (area_group_id) REFERENCES benefit_area_groups(area_group_id),
    CONSTRAINT fk_user_area_spend_area FOREIGN KEY (area_id) REFERENCES benefit_areas(area_id),
    CONSTRAINT uk_user_area_spend_period UNIQUE (user_card_id, area_group_id, area_id, usage_month),
    CONSTRAINT chk_user_area_spend_month CHECK (usage_month REGEXP '^[0-9]{4}-[0-9]{2}$'),
    CONSTRAINT chk_user_area_spend_amount CHECK (eligible_amount_krw >= 0),
    INDEX idx_user_area_spend_rank (user_card_id, area_group_id, usage_month, eligible_amount_krw)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
