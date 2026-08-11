-- 카드번호가 필요한 카드사에서 connectedId 생성(1단계)과 보유카드 조회(2단계)를 별도 API로
-- 나누기 위해, 1단계에서 입력받은 카드번호/비밀번호를 2단계가 소비할 때까지 잠깐 보관한다.
-- 2단계(POST /card-links/{linkId}/cards/discover) 성공 시 즉시 NULL로 지운다.
ALTER TABLE codef_account_credentials
    ADD COLUMN pending_card_number_enc VARBINARY(512) NULL AFTER birth_date_enc,
    ADD COLUMN pending_card_password_enc VARBINARY(512) NULL AFTER pending_card_number_enc;
