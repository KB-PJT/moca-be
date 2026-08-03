package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialFingerprintGenerator;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.IssuerMapper;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefIssuerPolicy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * CODEF Connected ID를 생성하고, 발급된 connectedId와 자격정보를 codef_account_credentials에 저장하는
 * 유스케이스를 담당한다.
 *
 * connectedId는 그대로, 카드사 자격정보는 AES로 양방향 암호화해 저장한다.
 */
public class CardLinkService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String FINGERPRINT_CARD_NO = "CARD_NO";
    private static final String FINGERPRINT_ACCOUNT_ID = "ACCOUNT_ID";

    private final CodefClient codefClient;
    private final CodefCredentialMapper codefCredentialMapper;
    private final CodefCredentialStore codefCredentialStore;
    private final IssuerMapper issuerMapper;
    private final Encryptor encryptor;
    private final CredentialFingerprintGenerator fingerprintGenerator;

    public CardLinkService(CodefClient codefClient, CodefCredentialMapper codefCredentialMapper,
                           CodefCredentialStore codefCredentialStore, IssuerMapper issuerMapper, Encryptor encryptor,
                           CredentialFingerprintGenerator fingerprintGenerator) {
        this.codefClient = codefClient;
        this.codefCredentialMapper = codefCredentialMapper;
        this.codefCredentialStore = codefCredentialStore;
        this.issuerMapper = issuerMapper;
        this.encryptor = encryptor;
        this.fingerprintGenerator = fingerprintGenerator;
    }

    public CardLinkResponse createLink(String userId, CreateCardLinkRequest request) {
        CodefIssuerPolicy policy = issuerMapper.findCodefPolicyByIssuerId(request.getIssuerId());
        if (policy == null) {
            throw new IssuerNotFoundException(request.getIssuerId());
        }
        validateRequiredCredentials(policy, request);
        String credentialFingerprint = createFingerprint(policy, request);
        if (codefCredentialMapper.existsByUserIdAndIssuerIdAndFingerprint(
                userId, request.getIssuerId(), credentialFingerprint)) {
            throw new CodefAccountAlreadyLinkedException();
        }

        CodefConnectionCommand command = new CodefConnectionCommand(
                policy.getInstitutionCode(), request.getId(), request.getPassword(),
                request.getCardNo(), request.getCardPassword(), request.getBirthDate());

        // CODEF에서 Connected ID 발급 (외부 호출)
        String connectedId = codefClient.createConnectedId(command);
        String credentialId = UUID.randomUUID().toString();

        // 외부 호출이 끝난 뒤 저장 구간만 별도 트랜잭션으로 실행한다.
        codefCredentialStore.save(
                buildCredential(userId, request, connectedId, credentialId, credentialFingerprint));

        return new CardLinkResponse(credentialId, request.getIssuerId(), STATUS_ACTIVE);
    }

    private CodefAccountCredential buildCredential(String userId, CreateCardLinkRequest request,
                                                   String connectedId, String credentialId,
                                                   String credentialFingerprint) {
        CodefAccountCredential credential = new CodefAccountCredential();
        credential.setCodefAccountCredentialId(credentialId);
        credential.setUserId(userId);
        credential.setIssuerId(request.getIssuerId());
        credential.setConnectedId(connectedId); // 이후 조회에 재사용하는 CODEF 토큰
        // 카드사 자격정보는 CODEF 재전송에 대비해 AES로 암호화 저장(원문 저장 금지)
        credential.setAccountIdEnc(encryptor.encrypt(request.getId()));
        credential.setAccountPasswordEnc(encryptor.encrypt(request.getPassword()));
        credential.setCardNumberEnc(encryptor.encrypt(request.getCardNo()));
        credential.setCardPasswordEnc(encryptor.encrypt(request.getCardPassword()));
        credential.setBirthDateEnc(encryptor.encrypt(request.getBirthDate()));
        credential.setCredentialFingerprint(credentialFingerprint);
        credential.setStatus(STATUS_ACTIVE);
        return credential;
    }

    private void validateRequiredCredentials(CodefIssuerPolicy policy, CreateCardLinkRequest request) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        require(fields, "id", request.getId(), policy.isRequiresId(),
                "아이디는 필수입니다.");
        require(fields, "password", request.getPassword(), policy.isRequiresPassword(),
                "패스워드는 필수입니다.");
        require(fields, "cardNo", request.getCardNo(), policy.isRequiresCardNo(),
                "카드번호는 필수입니다.");
        require(fields, "cardPassword", request.getCardPassword(), policy.isRequiresCardPassword(),
                "카드 비밀번호는 필수입니다.");
        require(fields, "birthDate", request.getBirthDate(), policy.isRequiresBirthDate(),
                "생년월일은 필수입니다.");
        if (!fields.isEmpty()) {
            throw new CodefCredentialRequiredException(fields);
        }
    }

    private void require(Map<String, String> fields, String field, String value,
                         boolean required, String message) {
        if (required && (value == null || value.isBlank())) {
            fields.put(field, message);
        }
    }

    private String createFingerprint(CodefIssuerPolicy policy, CreateCardLinkRequest request) {
        if (policy.isRequiresCardNo()) {
            String normalizedCardNo = normalizeCardNumber(request.getCardNo());
            if (normalizedCardNo.isBlank()) {
                throw invalidFingerprintSource("cardNo", "유효한 카드번호가 필요합니다.");
            }
            return fingerprintGenerator.generate(FINGERPRINT_CARD_NO, normalizedCardNo);
        }
        String normalizedAccountId = normalizeAccountId(request.getId());
        if (normalizedAccountId.isBlank()) {
            throw invalidFingerprintSource("id", "중복 연동 확인을 위한 아이디가 필요합니다.");
        }
        return fingerprintGenerator.generate(FINGERPRINT_ACCOUNT_ID, normalizedAccountId);
    }

    private String normalizeCardNumber(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replaceAll("[^0-9]", "");
    }

    private String normalizeAccountId(String accountId) {
        return accountId == null ? "" : accountId.trim().toLowerCase(Locale.ROOT);
    }

    private CodefCredentialRequiredException invalidFingerprintSource(String field, String message) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put(field, message);
        return new CodefCredentialRequiredException(fields);
    }
}
