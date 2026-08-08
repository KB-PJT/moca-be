-- 카드고릴라 원문을 관리자가 구조화한 뒤 국내 결제 혜택을 계산하기 위한 스키마.
-- 기존 테이블의 UUID가 CHAR(36)이므로 일부 테이블만 BINARY(16)을 사용해 변환 비용과
-- 조인 불일치를 만들지 않도록 같은 저장 형식을 유지한다.
-- 카드 기본 정보와 수집 콘텐츠가 분리되어 있으므로 혜택 원문은 card_content_versions에 귀속한다.

-- 관리자 확정 데이터만 적재하므로 AI 파싱·검증 상태와 파싱 원문 조각을 최종 스키마에서 제거한다.
ALTER TABLE card_annual_fee_options
    DROP CHECK chk_card_annual_fee_options_parse_status,
    DROP CHECK chk_card_annual_fee_options_parse_confidence,
    DROP COLUMN source_fragment,
    DROP COLUMN parse_status,
    DROP COLUMN parse_confidence,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uk_card_annual_fee_options_version_position UNIQUE (content_version_id, position);

-- 내부 UUID와 별도로 카드고릴라 원본 ID를 보존해 seed 재실행과 수집 버전 연결에 사용한다.
ALTER TABLE cards
    ADD COLUMN gorilla_card_id VARCHAR(50) NULL AFTER card_id,
    ADD CONSTRAINT uk_cards_gorilla_card_id UNIQUE (gorilla_card_id);

CREATE TABLE reward_programs (
    reward_program_id CHAR(36) NOT NULL,
    issuer_id CHAR(36) NOT NULL,
    program_name VARCHAR(100) NOT NULL,
    reward_unit VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (reward_program_id),
    CONSTRAINT fk_reward_programs_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuers (issuer_id),
    CONSTRAINT uk_reward_programs_issuer_name UNIQUE (issuer_id, program_name),
    CONSTRAINT chk_reward_programs_unit CHECK (reward_unit IN ('point', 'mile'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reward_conversion_policies (
    conversion_policy_id CHAR(36) NOT NULL,
    reward_program_id CHAR(36) NOT NULL,
    krw_per_unit DECIMAL(18,6) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (conversion_policy_id),
    CONSTRAINT fk_reward_conversions_program
        FOREIGN KEY (reward_program_id) REFERENCES reward_programs (reward_program_id),
    CONSTRAINT uk_reward_conversions_program_from UNIQUE (reward_program_id, valid_from),
    CONSTRAINT chk_reward_conversions_value CHECK (krw_per_unit > 0),
    CONSTRAINT chk_reward_conversions_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    INDEX idx_reward_conversions_lookup (reward_program_id, valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_benefits (
    benefit_id CHAR(36) NOT NULL,
    content_version_id CHAR(36) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    record_type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NULL,
    detail_text MEDIUMTEXT NULL,
    detail_html MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (benefit_id),
    CONSTRAINT fk_card_benefits_content_version
        FOREIGN KEY (content_version_id) REFERENCES card_content_versions (content_version_id),
    CONSTRAINT uk_card_benefits_version_position UNIQUE (content_version_id, position),
    CONSTRAINT chk_card_benefits_record_type
        CHECK (record_type IN ('benefit', 'notice', 'exclusion', 'other')),
    INDEX idx_card_benefits_version (content_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 카드 콘텐츠 원문에 명시된 전월 실적 구간이다. 카드 자체가 아니라 수집 시점의
-- 콘텐츠 버전에 귀속시켜, 카드사가 구간을 변경해도 과거 규칙을 보존한다.
-- minimum/maximum 모두 원화이며 maximum은 포함 범위다. 마지막 구간은 상한이 없다.
CREATE TABLE card_performance_tiers (
    performance_tier_id CHAR(36) NOT NULL,
    content_version_id CHAR(36) NOT NULL,
    tier_number SMALLINT UNSIGNED NOT NULL,
    minimum_spend_krw DECIMAL(18,2) NOT NULL,
    maximum_spend_krw DECIMAL(18,2) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (performance_tier_id),
    CONSTRAINT fk_card_performance_tiers_content_version
        FOREIGN KEY (content_version_id) REFERENCES card_content_versions (content_version_id),
    CONSTRAINT uk_card_performance_tiers_version_number UNIQUE (content_version_id, tier_number),
    CONSTRAINT uk_card_performance_tiers_version_minimum UNIQUE (content_version_id, minimum_spend_krw),
    CONSTRAINT chk_card_performance_tiers_number CHECK (tier_number > 0),
    CONSTRAINT chk_card_performance_tiers_range CHECK (
        minimum_spend_krw >= 0
        AND (maximum_spend_krw IS NULL OR maximum_spend_krw >= minimum_spend_krw)
    ),
    INDEX idx_card_performance_tiers_lookup (content_version_id, minimum_spend_krw)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_offers (
    offer_id CHAR(36) NOT NULL,
    benefit_id CHAR(36) NOT NULL,
    reward_program_id CHAR(36) NULL,
    offer_name VARCHAR(255) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    exclusive_group_key VARCHAR(100) NULL,
    reward_type VARCHAR(30) NOT NULL,
    value_type VARCHAR(30) NOT NULL,
    value_unit VARCHAR(20) NULL,
    calculation_mode VARCHAR(30) NOT NULL,
    calculation_basis VARCHAR(30) NOT NULL,
    stacking_mode VARCHAR(20) NOT NULL,
    reward_timing VARCHAR(30) NULL,
    valuation_scope VARCHAR(20) NOT NULL,
    valuation_method VARCHAR(30) NOT NULL,
    reference_value_krw DECIMAL(18,2) NULL,
    reference_value_unit VARCHAR(20) NULL,
    valid_from DATE NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (offer_id),
    CONSTRAINT fk_benefit_offers_benefit
        FOREIGN KEY (benefit_id) REFERENCES card_benefits (benefit_id),
    CONSTRAINT fk_benefit_offers_reward_program
        FOREIGN KEY (reward_program_id) REFERENCES reward_programs (reward_program_id),
    CONSTRAINT uk_benefit_offers_benefit_position UNIQUE (benefit_id, position),
    CONSTRAINT chk_benefit_offers_reward_type CHECK (reward_type IN (
        'discount', 'cashback', 'points', 'fee_waiver', 'voucher', 'free_service',
        'installment', 'insurance', 'rebate', 'other'
    )),
    CONSTRAINT chk_benefit_offers_value_type CHECK (value_type IN (
        'percentage', 'fixed_amount', 'unit_per_amount', 'free', 'months', 'count',
        'variable', 'not_applicable', 'other'
    )),
    CONSTRAINT chk_benefit_offers_calc_mode CHECK (calculation_mode IN (
        'flat', 'single_tier', 'progressive_tier', 'additive', 'highest_only',
        'variable', 'not_applicable', 'other'
    )),
    CONSTRAINT chk_benefit_offers_calc_basis CHECK (calculation_basis IN (
        'transaction_amount', 'eligible_amount', 'billing_amount', 'base_amount',
        'liter', 'usage_count', 'annual_fee', 'none', 'other'
    )),
    CONSTRAINT chk_benefit_offers_stacking CHECK (stacking_mode IN (
        'standalone', 'additive', 'replace', 'highest_only', 'not_stackable'
    )),
    CONSTRAINT chk_benefit_offers_reward_timing CHECK (reward_timing IS NULL OR reward_timing IN (
        'instant', 'statement', 'cashback', 'point_accrual', 'voucher_issue', 'service_usage'
    )),
    CONSTRAINT chk_benefit_offers_valuation_scope CHECK (valuation_scope IN (
        'transaction', 'annual', 'usage', 'non_monetary'
    )),
    CONSTRAINT chk_benefit_offers_valuation_method CHECK (valuation_method IN (
        'direct', 'fixed_conversion', 'user_usage', 'not_valued'
    )),
    CONSTRAINT chk_benefit_offers_reference_value
        CHECK (reference_value_krw IS NULL OR reference_value_krw >= 0),
    CONSTRAINT chk_benefit_offers_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    INDEX idx_benefit_offers_benefit (benefit_id),
    INDEX idx_benefit_offers_exclusive_group (exclusive_group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_rules (
    rule_id CHAR(36) NOT NULL,
    offer_id CHAR(36) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    rule_name VARCHAR(255) NULL,
    rule_effect VARCHAR(20) NOT NULL,
    stacking_mode VARCHAR(20) NOT NULL,
    reward_value DECIMAL(18,4) NULL,
    reward_unit VARCHAR(20) NULL,
    reward_basis_amount DECIMAL(18,4) NULL,
    reward_basis_unit VARCHAR(20) NULL,
    previous_spend_min_krw DECIMAL(18,2) NULL,
    current_spend_min_krw DECIMAL(18,2) NULL,
    transaction_min_krw DECIMAL(18,2) NULL,
    transaction_max_krw DECIMAL(18,2) NULL,
    rounding_type VARCHAR(20) NULL,
    rounding_unit INT UNSIGNED NULL,
    valid_from DATE NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (rule_id),
    CONSTRAINT fk_benefit_rules_offer
        FOREIGN KEY (offer_id) REFERENCES benefit_offers (offer_id),
    CONSTRAINT uk_benefit_rules_offer_position UNIQUE (offer_id, position),
    CONSTRAINT chk_benefit_rules_effect CHECK (rule_effect IN ('grant', 'bonus', 'exclude', 'replace')),
    CONSTRAINT chk_benefit_rules_stacking CHECK (stacking_mode IN (
        'standalone', 'additive', 'replace', 'highest_only', 'not_stackable'
    )),
    CONSTRAINT chk_benefit_rules_reward_unit
        CHECK (reward_unit IS NULL OR reward_unit IN ('percent', 'KRW', 'point', 'mile')),
    CONSTRAINT chk_benefit_rules_basis_unit
        CHECK (reward_basis_unit IS NULL OR reward_basis_unit IN ('KRW', 'liter', 'usage_count')),
    CONSTRAINT chk_benefit_rules_rounding
        CHECK (rounding_type IS NULL OR rounding_type IN ('floor', 'round', 'ceil', 'none')),
    CONSTRAINT chk_benefit_rules_non_negative CHECK (
        (reward_value IS NULL OR reward_value >= 0)
        AND (reward_basis_amount IS NULL OR reward_basis_amount > 0)
        AND (previous_spend_min_krw IS NULL OR previous_spend_min_krw >= 0)
        AND (current_spend_min_krw IS NULL OR current_spend_min_krw >= 0)
        AND (transaction_min_krw IS NULL OR transaction_min_krw >= 0)
        AND (transaction_max_krw IS NULL OR transaction_max_krw >= 0)
    ),
    CONSTRAINT chk_benefit_rules_transaction_range CHECK (
        transaction_min_krw IS NULL OR transaction_max_krw IS NULL
        OR transaction_max_krw >= transaction_min_krw
    ),
    CONSTRAINT chk_benefit_rules_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    INDEX idx_benefit_rules_tier_lookup (offer_id, previous_spend_min_krw, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_rule_targets (
    target_id CHAR(36) NOT NULL,
    rule_id CHAR(36) NOT NULL,
    condition_group SMALLINT UNSIGNED NOT NULL,
    match_mode VARCHAR(10) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_code VARCHAR(100) NOT NULL,
    target_name VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (target_id),
    CONSTRAINT fk_benefit_rule_targets_rule
        FOREIGN KEY (rule_id) REFERENCES benefit_rules (rule_id),
    CONSTRAINT uk_benefit_rule_targets_condition
        UNIQUE (rule_id, condition_group, match_mode, target_type, target_code),
    CONSTRAINT chk_benefit_rule_targets_group CHECK (condition_group > 0),
    CONSTRAINT chk_benefit_rule_targets_mode CHECK (match_mode IN ('include', 'exclude')),
    CONSTRAINT chk_benefit_rule_targets_type CHECK (target_type IN (
        'all_merchants', 'merchant_category', 'merchant', 'channel', 'payment_method',
        'entry_method', 'merchant_attribute', 'transaction_type', 'product', 'other'
    )),
    INDEX idx_benefit_rule_targets_rule_group (rule_id, condition_group, match_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_rule_schedules (
    schedule_id CHAR(36) NOT NULL,
    rule_id CHAR(36) NOT NULL,
    months_json JSON NULL,
    days_of_month_json JSON NULL,
    days_of_week_json JSON NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (schedule_id),
    CONSTRAINT fk_benefit_rule_schedules_rule
        FOREIGN KEY (rule_id) REFERENCES benefit_rules (rule_id),
    CONSTRAINT chk_benefit_rule_schedules_time_pair CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time <> end_time)
    ),
    INDEX idx_benefit_rule_schedules_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_limit_policies (
    limit_policy_id CHAR(36) NOT NULL,
    offer_id CHAR(36) NOT NULL,
    policy_name VARCHAR(255) NULL,
    limit_period VARCHAR(30) NOT NULL,
    limit_type VARCHAR(30) NOT NULL,
    limit_unit VARCHAR(20) NOT NULL,
    shared_group_key VARCHAR(100) NULL,
    valid_from DATE NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (limit_policy_id),
    CONSTRAINT fk_benefit_limit_policies_offer
        FOREIGN KEY (offer_id) REFERENCES benefit_offers (offer_id),
    CONSTRAINT chk_benefit_limit_period CHECK (limit_period IN (
        'per_transaction', 'daily', 'weekly', 'monthly', 'yearly', 'card_lifetime',
        'promotion_period', 'other'
    )),
    CONSTRAINT chk_benefit_limit_type CHECK (limit_type IN (
        'reward_amount', 'eligible_spend', 'usage_count', 'points', 'other'
    )),
    CONSTRAINT chk_benefit_limit_unit CHECK (limit_unit IN ('KRW', 'point', 'mile', 'count', 'other')),
    CONSTRAINT chk_benefit_limit_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    INDEX idx_benefit_limit_offer (offer_id),
    INDEX idx_benefit_limit_shared_group (shared_group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE benefit_limit_tiers (
    limit_tier_id CHAR(36) NOT NULL,
    limit_policy_id CHAR(36) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    limit_value DECIMAL(18,4) NOT NULL,
    previous_spend_min_krw DECIMAL(18,2) NULL,
    current_spend_min_krw DECIMAL(18,2) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (limit_tier_id),
    CONSTRAINT fk_benefit_limit_tiers_policy
        FOREIGN KEY (limit_policy_id) REFERENCES benefit_limit_policies (limit_policy_id),
    CONSTRAINT uk_benefit_limit_tiers_position UNIQUE (limit_policy_id, position),
    CONSTRAINT uk_benefit_limit_tiers_threshold
        UNIQUE (limit_policy_id, previous_spend_min_krw, current_spend_min_krw),
    CONSTRAINT chk_benefit_limit_tiers_value CHECK (limit_value >= 0),
    CONSTRAINT chk_benefit_limit_tiers_spend CHECK (
        (previous_spend_min_krw IS NULL OR previous_spend_min_krw >= 0)
        AND (current_spend_min_krw IS NULL OR current_spend_min_krw >= 0)
    ),
    INDEX idx_benefit_limit_tiers_lookup
        (limit_policy_id, previous_spend_min_krw, current_spend_min_krw)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- option_choice_id가 실제로 option_group_id에 속하는지 복합 FK로 보장한다.
ALTER TABLE card_option_choices
    DROP CHECK chk_card_option_choices_parse_status,
    DROP CHECK chk_card_option_choices_parse_confidence,
    DROP COLUMN source_fragment,
    DROP COLUMN parse_status,
    DROP COLUMN parse_confidence,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uk_card_option_choices_group_id UNIQUE (option_group_id, option_choice_id);

ALTER TABLE card_option_groups
    DROP CHECK chk_card_option_groups_parse_status,
    DROP CHECK chk_card_option_groups_parse_confidence,
    DROP COLUMN source_fragment,
    DROP COLUMN parse_status,
    DROP COLUMN parse_confidence,
    ADD COLUMN selection_required BOOLEAN NOT NULL DEFAULT FALSE AFTER group_name,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uk_card_option_groups_card_id UNIQUE (card_id, option_group_id);

ALTER TABLE user_cards
    ADD CONSTRAINT uk_user_cards_user_card_product UNIQUE (user_card_id, card_id);

ALTER TABLE user_card_option_selections
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT fk_user_option_selections_user_card_product
        FOREIGN KEY (user_card_id, card_id) REFERENCES user_cards (user_card_id, card_id),
    ADD CONSTRAINT fk_user_option_selections_card_group
        FOREIGN KEY (card_id, option_group_id) REFERENCES card_option_groups (card_id, option_group_id),
    ADD CONSTRAINT fk_user_option_selections_group_choice
        FOREIGN KEY (option_group_id, option_choice_id)
        REFERENCES card_option_choices (option_group_id, option_choice_id);

CREATE TABLE benefit_offer_option_requirements (
    requirement_id CHAR(36) NOT NULL,
    offer_id CHAR(36) NOT NULL,
    option_group_id CHAR(36) NOT NULL,
    option_choice_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (requirement_id),
    CONSTRAINT fk_benefit_option_requirements_offer
        FOREIGN KEY (offer_id) REFERENCES benefit_offers (offer_id),
    CONSTRAINT fk_benefit_option_requirements_choice
        FOREIGN KEY (option_group_id, option_choice_id)
        REFERENCES card_option_choices (option_group_id, option_choice_id),
    CONSTRAINT uk_benefit_option_requirements
        UNIQUE (offer_id, option_group_id, option_choice_id),
    INDEX idx_benefit_option_requirements_choice (option_group_id, option_choice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_methods (
    payment_method_id CHAR(36) NOT NULL,
    payment_method_code VARCHAR(100) NOT NULL,
    payment_method_name VARCHAR(255) NOT NULL,
    method_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (payment_method_id),
    CONSTRAINT uk_payment_methods_code UNIQUE (payment_method_code),
    CONSTRAINT chk_payment_methods_type CHECK (method_type IN (
        'physical_card', 'easy_pay', 'qr', 'nfc', 'mst', 'auto_payment',
        'recurring_payment', 'early_payment', 'other'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 동일 브랜드라도 공식 앱과 오프라인 POS를 구분할 수 있도록 별칭에 결제 문맥을 보존한다.
ALTER TABLE merchant_aliases
    ADD COLUMN match_type VARCHAR(20) NOT NULL DEFAULT 'prefix' AFTER normalized_alias_name,
    ADD COLUMN channel VARCHAR(20) NULL AFTER source_type,
    ADD COLUMN entry_method VARCHAR(30) NULL AFTER channel,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT chk_merchant_aliases_match_type
        CHECK (match_type IN ('exact', 'prefix', 'contains', 'regex'));

CREATE TABLE payment_method_aliases (
    payment_method_alias_id CHAR(36) NOT NULL,
    payment_method_id CHAR(36) NOT NULL,
    alias_name VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (payment_method_alias_id),
    CONSTRAINT fk_payment_method_aliases_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (payment_method_id),
    CONSTRAINT uk_payment_method_aliases_source_normalized UNIQUE (source_type, normalized_alias),
    INDEX idx_payment_method_aliases_method (payment_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_spend_rules (
    spend_rule_id CHAR(36) NOT NULL,
    card_id CHAR(36) NOT NULL,
    condition_group SMALLINT UNSIGNED NOT NULL,
    match_mode VARCHAR(10) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_code VARCHAR(100) NOT NULL,
    reference_month_offset SMALLINT NOT NULL DEFAULT -1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (spend_rule_id),
    CONSTRAINT fk_card_spend_rules_card
        FOREIGN KEY (card_id) REFERENCES cards (card_id),
    CONSTRAINT uk_card_spend_rules_condition
        UNIQUE (card_id, condition_group, match_mode, target_type, target_code, reference_month_offset),
    CONSTRAINT chk_card_spend_rules_group CHECK (condition_group > 0),
    CONSTRAINT chk_card_spend_rules_mode CHECK (match_mode IN ('include', 'exclude')),
    CONSTRAINT chk_card_spend_rules_month_offset CHECK (reference_month_offset BETWEEN -24 AND 0),
    INDEX idx_card_spend_rules_card_group (card_id, condition_group, match_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_benefit_usages (
    usage_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    offer_id CHAR(36) NOT NULL,
    rule_id CHAR(36) NULL,
    limit_policy_id CHAR(36) NULL,
    approval_id CHAR(36) NULL,
    usage_date DATE NOT NULL,
    eligible_amount_krw DECIMAL(18,2) NOT NULL,
    reward_amount_krw DECIMAL(18,2) NOT NULL,
    reward_original_value DECIMAL(18,4) NULL,
    reward_original_unit VARCHAR(20) NULL,
    usage_count INT UNSIGNED NOT NULL DEFAULT 1,
    usage_status VARCHAR(20) NOT NULL,
    approved_at DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    reversed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    usage_identity_key VARCHAR(160) AS (
        CASE
            WHEN approval_id IS NULL THEN NULL
            ELSE CONCAT(
                approval_id, '|', offer_id, '|',
                COALESCE(rule_id, 'NO_RULE'), '|', COALESCE(limit_policy_id, 'NO_POLICY')
            )
        END
    ) STORED,
    PRIMARY KEY (usage_id),
    CONSTRAINT fk_user_benefit_usages_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_user_benefit_usages_offer
        FOREIGN KEY (offer_id) REFERENCES benefit_offers (offer_id),
    CONSTRAINT fk_user_benefit_usages_rule
        FOREIGN KEY (rule_id) REFERENCES benefit_rules (rule_id),
    CONSTRAINT fk_user_benefit_usages_limit_policy
        FOREIGN KEY (limit_policy_id) REFERENCES benefit_limit_policies (limit_policy_id),
    CONSTRAINT fk_user_benefit_usages_approval
        FOREIGN KEY (approval_id) REFERENCES card_payment_approvals (approval_id),
    CONSTRAINT uk_user_benefit_usages_identity UNIQUE (user_card_id, usage_identity_key),
    CONSTRAINT chk_user_benefit_usages_amounts CHECK (
        eligible_amount_krw >= 0 AND reward_amount_krw >= 0
        AND (reward_original_value IS NULL OR reward_original_value >= 0)
    ),
    CONSTRAINT chk_user_benefit_usages_unit
        CHECK (reward_original_unit IS NULL OR reward_original_unit IN ('KRW', 'point', 'mile')),
    CONSTRAINT chk_user_benefit_usages_status
        CHECK (usage_status IN ('pending', 'confirmed', 'cancelled', 'reversed')),
    INDEX idx_user_benefit_usages_period (user_card_id, usage_date, usage_status),
    INDEX idx_user_benefit_usages_offer_period (offer_id, usage_date, usage_status),
    INDEX idx_user_benefit_usages_shared_limit (limit_policy_id, usage_date, usage_status),
    INDEX idx_user_benefit_usages_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 계산 결과를 모두 남긴다. user_benefit_usages는 실제로 적용된 혜택만 나타내므로,
-- 월 한도 소진으로 일부/전부 적용되지 못한 금액은 이 원장에서만 정확히 집계할 수 있다.
CREATE TABLE user_benefit_calculation_outcomes (
    outcome_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    approval_id CHAR(36) NOT NULL,
    offer_id CHAR(36) NOT NULL,
    rule_id CHAR(36) NOT NULL,
    limit_policy_id CHAR(36) NULL,
    usage_date DATE NOT NULL,
    reward_unit VARCHAR(20) NOT NULL,
    expected_reward_value DECIMAL(18,4) NOT NULL,
    applied_reward_value DECIMAL(18,4) NOT NULL,
    missed_reward_value DECIMAL(18,4) NOT NULL,
    outcome_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(50) NOT NULL,
    calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (outcome_id),
    CONSTRAINT fk_user_benefit_outcomes_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_user_benefit_outcomes_approval
        FOREIGN KEY (approval_id) REFERENCES card_payment_approvals (approval_id),
    CONSTRAINT fk_user_benefit_outcomes_offer
        FOREIGN KEY (offer_id) REFERENCES benefit_offers (offer_id),
    CONSTRAINT fk_user_benefit_outcomes_rule
        FOREIGN KEY (rule_id) REFERENCES benefit_rules (rule_id),
    CONSTRAINT fk_user_benefit_outcomes_limit_policy
        FOREIGN KEY (limit_policy_id) REFERENCES benefit_limit_policies (limit_policy_id),
    CONSTRAINT uk_user_benefit_outcomes_identity
        UNIQUE (user_card_id, approval_id, offer_id, rule_id),
    CONSTRAINT chk_user_benefit_outcomes_values CHECK (
        expected_reward_value >= 0 AND applied_reward_value >= 0 AND missed_reward_value >= 0
        AND expected_reward_value = applied_reward_value + missed_reward_value
    ),
    CONSTRAINT chk_user_benefit_outcomes_unit
        CHECK (reward_unit IN ('KRW', 'point', 'mile')),
    CONSTRAINT chk_user_benefit_outcomes_status
        CHECK (outcome_status IN ('applied', 'partially_applied', 'not_applied')),
    INDEX idx_user_benefit_outcomes_month (user_card_id, usage_date, outcome_status),
    INDEX idx_user_benefit_outcomes_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
