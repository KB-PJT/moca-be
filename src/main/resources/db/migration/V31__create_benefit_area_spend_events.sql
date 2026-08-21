CREATE TABLE IF NOT EXISTS user_benefit_area_spend_events (
    event_id CHAR(36) NOT NULL,
    approval_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    area_id CHAR(36) NOT NULL,
    usage_month CHAR(7) NOT NULL,
    amount_krw DECIMAL(18,2) NOT NULL,
    transaction_count INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id),
    CONSTRAINT fk_area_spend_event_approval FOREIGN KEY (approval_id) REFERENCES card_payment_approvals(approval_id),
    CONSTRAINT fk_area_spend_event_card FOREIGN KEY (user_card_id) REFERENCES user_cards(user_card_id),
    CONSTRAINT fk_area_spend_event_area FOREIGN KEY (area_id) REFERENCES benefit_areas(area_id),
    CONSTRAINT uk_area_spend_event_approval_area UNIQUE (approval_id, area_id),
    CONSTRAINT chk_area_spend_event_amount CHECK (amount_krw >= 0),
    INDEX idx_area_spend_event_period (user_card_id, usage_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
