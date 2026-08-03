ALTER TABLE issuers
    ADD COLUMN requires_id BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_card_no BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_card_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_birth_date BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE codef_account_credentials (
    codef_account_credential_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    issuer_id CHAR(36) NOT NULL,
    connected_id CHAR(36) NOT NULL,
    account_id_enc VARBINARY(512) NULL,
    account_password_enc VARBINARY(512) NULL,
    card_number_enc VARBINARY(512) NULL,
    card_password_enc VARBINARY(512) NULL,
    birth_date_enc VARBINARY(256) NULL,
    credential_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (codef_account_credential_id),
    CONSTRAINT fk_codef_account_credentials_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_codef_account_credentials_issuer
        FOREIGN KEY (issuer_id) REFERENCES issuers (issuer_id),
    CONSTRAINT uk_codef_account_credentials_user_issuer_fingerprint
        UNIQUE (user_id, issuer_id, credential_fingerprint),
    INDEX idx_codef_account_credentials_user (user_id),
    INDEX idx_codef_account_credentials_issuer (issuer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user_cards
    ADD CONSTRAINT fk_user_cards_codef_account_credential
        FOREIGN KEY (codef_account_credential_id)
            REFERENCES codef_account_credentials (codef_account_credential_id);
