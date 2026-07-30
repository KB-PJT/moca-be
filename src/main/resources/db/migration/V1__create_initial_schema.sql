CREATE TABLE users (
    user_id CHAR(36) NOT NULL,
    google_subject VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(255) NULL,
    user_type VARCHAR(20) NOT NULL DEFAULT 'user',
    location_recommendation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    card_sort_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_google_subject UNIQUE (google_subject),
    CONSTRAINT chk_users_user_type CHECK (user_type IN ('user', 'admin')),
    CONSTRAINT chk_users_card_sort_mode CHECK (card_sort_mode IN ('AUTO', 'MANUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_notification_settings (
    user_id CHAR(36) NOT NULL,
    performance_closing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    nearby_benefit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    benefit_limit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_notification_settings_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
