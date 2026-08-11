CREATE TABLE support_inquiries (
    inquiry_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    inquiry_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    reply_email VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'received',
    created_at DATETIME(6) NOT NULL,
    answered_at DATETIME(6) NULL,
    PRIMARY KEY (inquiry_id),
    CONSTRAINT fk_support_inquiries_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT chk_support_inquiries_inquiry_type CHECK (inquiry_type IN (
        'card_link', 'performance_benefit', 'map_merchant', 'account_login', 'bug', 'etc')),
    CONSTRAINT chk_support_inquiries_status CHECK (status IN ('received', 'answered', 'closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
