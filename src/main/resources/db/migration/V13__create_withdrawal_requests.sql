-- 탈퇴 사유 통계용 테이블. 탈퇴 처리(users row 삭제)로 개인 식별이 불가능해지므로
-- user_id 등 사용자 연결 정보는 두지 않고 사유 집계 목적으로만 남긴다.
CREATE TABLE withdrawal_requests (
    withdrawal_request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reason_code VARCHAR(30) NULL,
    reason_text VARCHAR(500) NULL,
    confirmed BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (withdrawal_request_id),
    CONSTRAINT chk_withdrawal_requests_reason_code CHECK (reason_code IS NULL OR reason_code IN (
        'inconvenient', 'not_needed', 'incorrect_benefit', 'privacy_concern', 'low_usage', 'etc'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
