ALTER TABLE notification_history
    DROP INDEX idx_notification_history_dedup,
    ADD INDEX idx_notification_history_dedup (
        user_id,
        user_device_id,
        notification_type,
        reference_id,
        notification_date,
        time_slot,
        status
    );
