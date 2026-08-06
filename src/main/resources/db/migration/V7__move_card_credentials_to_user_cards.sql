-- 카드번호/비밀번호가 필요한 카드사는 카드마다 다른 카드번호를 가지므로, 계정(연동) 단위였던
-- 카드번호/비밀번호 저장 위치를 카드(user_cards) 단위로 옮긴다.

ALTER TABLE codef_account_credentials
    DROP COLUMN card_number_enc,
    DROP COLUMN card_password_enc;

-- card_no(마스킹된 표시용 카드번호)는 유지하고, 실제 카드번호/비밀번호는 암호화해 별도 컬럼에 저장한다.
ALTER TABLE user_cards
    ADD COLUMN card_number_enc VARBINARY(512) NULL AFTER card_no,
    ADD COLUMN card_password_enc VARBINARY(512) NULL AFTER card_number_enc;
