-- 카드 카탈로그(카드고릴라 수집)와 CODEF 보유카드 매칭/선택 스키마.
-- cards는 식별 뼈대만 두고, 이름/이미지/연회비/이벤트 등 수집 콘텐츠는 card_content_versions에 버전으로 적재한다.

CREATE TABLE cards (
    card_id CHAR(36) NOT NULL,
    issuer_id CHAR(36) NOT NULL,
    card_type VARCHAR(10) NOT NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (card_id),
    CONSTRAINT fk_cards_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuers (issuer_id),
    CONSTRAINT chk_cards_card_type CHECK (card_type IN ('credit', 'check')),
    INDEX idx_cards_issuer (issuer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_content_versions (
    content_version_id CHAR(36) NOT NULL,
    card_id CHAR(36) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    annual_fee_summary VARCHAR(255) NULL,
    annual_fee_detail TEXT NULL,
    representative_spend INT NULL,
    discontinued BOOLEAN NOT NULL DEFAULT FALSE,
    main_benefits TEXT NULL,
    event_title VARCHAR(255) NULL,
    event_detail_text MEDIUMTEXT NULL,
    event_detail_html MEDIUMTEXT NULL,
    image_url VARCHAR(1000) NULL,
    source_url VARCHAR(500) NULL,
    source_payload JSON NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (content_version_id),
    CONSTRAINT fk_card_content_versions_card
        FOREIGN KEY (card_id) REFERENCES cards (card_id),
    CONSTRAINT uk_card_content_versions_card_sha UNIQUE (card_id, content_sha256),
    INDEX idx_card_content_versions_card_seen (card_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_annual_fee_options (
    fee_option_id CHAR(36) NOT NULL,
    content_version_id CHAR(36) NOT NULL,
    position SMALLINT NOT NULL,
    usage_region VARCHAR(20) NULL,
    brand VARCHAR(50) NULL,
    annual_fee_krw INT NULL,
    source_fragment VARCHAR(1000) NULL,
    parse_status VARCHAR(20) NOT NULL,
    parse_confidence DECIMAL(5,4) NULL,
    PRIMARY KEY (fee_option_id),
    CONSTRAINT fk_card_annual_fee_options_version
        FOREIGN KEY (content_version_id) REFERENCES card_content_versions (content_version_id),
    CONSTRAINT chk_card_annual_fee_options_parse_status
        CHECK (parse_status IN ('raw_only', 'auto_parsed', 'verified')),
    CONSTRAINT chk_card_annual_fee_options_parse_confidence
        CHECK (parse_confidence IS NULL OR (parse_confidence >= 0 AND parse_confidence <= 1)),
    INDEX idx_card_annual_fee_options_version (content_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 보유카드는 카탈로그 매칭에 성공한 카드만 적재하므로 card_id를 필수로 승격한다.
-- card_no: CODEF resCardNo(마스킹된 카드번호, 예 943646******1069)를 프론트 표시용으로 저장한다.
ALTER TABLE user_cards
    MODIFY COLUMN card_id CHAR(36) NOT NULL,
    ADD COLUMN card_no VARCHAR(20) NULL AFTER card_name_from_codef,
    ADD CONSTRAINT fk_user_cards_card
        FOREIGN KEY (card_id) REFERENCES cards (card_id);

-- credential_fingerprint를 중복 식별 해시 의미에 맞게 이름을 바꾼다.
ALTER TABLE codef_account_credentials
    RENAME COLUMN credential_fingerprint TO credential_identity_hash,
    RENAME INDEX uk_codef_account_credentials_user_issuer_fingerprint
        TO uk_codef_account_credentials_user_issuer_identity_hash,
    ADD CONSTRAINT chk_codef_account_credentials_status
        CHECK (status IN ('active', 'expired', 'revoked'));

CREATE TABLE card_option_groups (
    option_group_id CHAR(36) NOT NULL,
    card_id CHAR(36) NOT NULL,
    group_key VARCHAR(100) NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    source_fragment TEXT NULL,
    parse_status VARCHAR(20) NOT NULL,
    parse_confidence DECIMAL(5,4) NULL,
    PRIMARY KEY (option_group_id),
    CONSTRAINT fk_card_option_groups_card
        FOREIGN KEY (card_id) REFERENCES cards (card_id),
    CONSTRAINT uk_card_option_groups_card_key UNIQUE (card_id, group_key),
    CONSTRAINT chk_card_option_groups_parse_status
        CHECK (parse_status IN ('parsed', 'needs_review', 'verified')),
    CONSTRAINT chk_card_option_groups_parse_confidence
        CHECK (parse_confidence IS NULL OR (parse_confidence >= 0 AND parse_confidence <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_option_choices (
    option_choice_id CHAR(36) NOT NULL,
    option_group_id CHAR(36) NOT NULL,
    choice_key VARCHAR(100) NOT NULL,
    choice_name VARCHAR(255) NOT NULL,
    source_fragment TEXT NULL,
    parse_status VARCHAR(20) NOT NULL,
    parse_confidence DECIMAL(5,4) NULL,
    PRIMARY KEY (option_choice_id),
    CONSTRAINT fk_card_option_choices_group
        FOREIGN KEY (option_group_id) REFERENCES card_option_groups (option_group_id),
    CONSTRAINT uk_card_option_choices_group_key UNIQUE (option_group_id, choice_key),
    CONSTRAINT chk_card_option_choices_parse_status
        CHECK (parse_status IN ('parsed', 'needs_review', 'verified')),
    CONSTRAINT chk_card_option_choices_parse_confidence
        CHECK (parse_confidence IS NULL OR (parse_confidence >= 0 AND parse_confidence <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_card_option_selections (
    user_card_id CHAR(36) NOT NULL,
    option_group_id CHAR(36) NOT NULL,
    card_id CHAR(36) NOT NULL,
    option_choice_id CHAR(36) NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_card_id, option_group_id),
    CONSTRAINT fk_user_card_option_selections_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id),
    CONSTRAINT fk_user_card_option_selections_group
        FOREIGN KEY (option_group_id) REFERENCES card_option_groups (option_group_id),
    CONSTRAINT fk_user_card_option_selections_card
        FOREIGN KEY (card_id) REFERENCES cards (card_id),
    CONSTRAINT fk_user_card_option_selections_choice
        FOREIGN KEY (option_choice_id) REFERENCES card_option_choices (option_choice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
