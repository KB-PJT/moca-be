CREATE TABLE issuers (
    issuer_id CHAR(36) NOT NULL,
    institution_code CHAR(10) NOT NULL,
    issuer_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (issuer_id),
    CONSTRAINT uk_issuers_institution_code UNIQUE (institution_code),
    CONSTRAINT uk_issuers_issuer_name UNIQUE (issuer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_cards (
    user_card_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    -- FIXME: 카드 마스터 테이블 마이그레이션 추가 후 card_id 외래키를 연결한다.
    card_id CHAR(36) NULL,
    -- FIXME: codef_connections 테이블 마이그레이션 추가 후 codef_connection_id 외래키를 연결한다.
    codef_connection_id CHAR(36) NOT NULL,
    card_name_from_codef VARCHAR(150) NOT NULL,
    issuer_id CHAR(36) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    codef_card_key_hash CHAR(64) NOT NULL,
    memo VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_card_id),
    CONSTRAINT uk_user_cards_user_codef_card_key_hash
        UNIQUE (user_id, codef_card_key_hash),
    CONSTRAINT fk_user_cards_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_cards_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuers (issuer_id),
    INDEX idx_user_cards_user_active_order (user_id, is_active, display_order, user_card_id),
    INDEX idx_user_cards_card_id (card_id),
    INDEX idx_user_cards_codef_connection_id (codef_connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
