ALTER TABLE users
    ADD COLUMN benefit_preference_type VARCHAR(30) NOT NULL DEFAULT 'IMMEDIATE_SAVINGS',
    ADD CONSTRAINT chk_users_benefit_preference_type CHECK (
        benefit_preference_type IN ('IMMEDIATE_SAVINGS', 'POINT_USAGE', 'TRAVEL_MILEAGE', 'MAXIMUM_BENEFIT')
    );
