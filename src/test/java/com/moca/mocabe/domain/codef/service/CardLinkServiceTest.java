package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardLinkServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String ISSUER_ID = "00000000-0000-4000-8000-000000000301";

    @Mock
    private CodefClient codefClient;
    @Mock
    private CodefCredentialMapper codefCredentialMapper;
    @Mock
    private CodefCredentialStore codefCredentialStore;
    @Mock
    private IssuerMapper issuerMapper;
    @Mock
    private Encryptor encryptor;
    @Mock
    private CredentialFingerprintGenerator fingerprintGenerator;

    private CardLinkService cardLinkService;

    @BeforeEach
    void setUp() {
        cardLinkService = new CardLinkService(
                codefClient, codefCredentialMapper, codefCredentialStore,
                issuerMapper, encryptor, fingerprintGenerator);
    }

    @Test
    @DisplayName("issuerId로 기관코드를 조회해 연동하고 connectedId·암호화 자격정보를 저장한다")
    void createsLinkAndStoresCredential() {
        CreateCardLinkRequest request = request();
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(cardPolicy());
        when(fingerprintGenerator.generate("CARD_NO", "1234567890123456")).thenReturn("fingerprint-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request);

        assertNotNull(response.getLinkId());
        assertEquals(ISSUER_ID, response.getIssuerId());
        assertEquals("ACTIVE", response.getStatus());

        ArgumentCaptor<CodefAccountCredential> credentialCaptor =
                ArgumentCaptor.forClass(CodefAccountCredential.class);
        verify(codefCredentialStore).save(credentialCaptor.capture());
        CodefAccountCredential credential = credentialCaptor.getValue();
        assertEquals(response.getLinkId(), credential.getCodefAccountCredentialId());
        assertEquals(USER_ID, credential.getUserId());
        assertEquals(ISSUER_ID, credential.getIssuerId());
        assertEquals("cid-1", credential.getConnectedId());
        assertEquals("fingerprint-1", credential.getCredentialFingerprint());
        assertEquals("ACTIVE", credential.getStatus());
        assertNotNull(credential.getAccountPasswordEnc());
        assertNotNull(credential.getCardPasswordEnc());
    }

    @Test
    @DisplayName("등록되지 않은 발급사면 예외를 던지고 CODEF를 호출하지 않는다")
    void throwsWhenIssuerNotFound() {
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(null);

        IssuerNotFoundException exception = assertThrows(
                IssuerNotFoundException.class, () -> cardLinkService.createLink(USER_ID, request()));

        assertEquals("등록되지 않은 발급사입니다: " + ISSUER_ID, exception.getMessage());
        verifyNoInteractions(codefClient, codefCredentialMapper, codefCredentialStore, encryptor);
    }

    @Test
    @DisplayName("카드사 정책의 필수 자격정보가 누락되면 필드 오류를 반환한다")
    void throwsWhenRequiredCredentialsMissing() {
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(cardPolicy());
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setIssuerId(ISSUER_ID);

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class, () -> cardLinkService.createLink(USER_ID, request));

        assertEquals(5, exception.getFields().size());
        assertEquals("아이디는 필수입니다.", exception.getFields().get("id"));
        assertEquals("카드번호는 필수입니다.", exception.getFields().get("cardNo"));
        verifyNoInteractions(codefClient, codefCredentialMapper, codefCredentialStore,
                encryptor, fingerprintGenerator);
    }

    @Test
    @DisplayName("카드번호를 받지 않는 카드사는 정규화한 로그인 ID로 중복을 확인한다")
    void createsAccountIdFingerprint() {
        CodefIssuerPolicy policy = accountPolicy();
        CreateCardLinkRequest request = request();
        request.setId(" Tester ");
        request.setCardNo(null);
        request.setCardPassword(null);
        request.setBirthDate(null);
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(policy);
        when(fingerprintGenerator.generate("ACCOUNT_ID", "tester")).thenReturn("account-fingerprint");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-2");
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1});

        cardLinkService.createLink(USER_ID, request);

        verify(codefCredentialMapper).existsByUserIdAndIssuerIdAndFingerprint(
                USER_ID, ISSUER_ID, "account-fingerprint");
    }

    @Test
    @DisplayName("이미 같은 카드가 연동돼 있으면 CODEF를 호출하지 않는다")
    void rejectsExistingCredential() {
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(cardPolicy());
        when(fingerprintGenerator.generate("CARD_NO", "1234567890123456")).thenReturn("duplicate");
        when(codefCredentialMapper.existsByUserIdAndIssuerIdAndFingerprint(USER_ID, ISSUER_ID, "duplicate"))
                .thenReturn(true);

        assertThrows(CodefAccountAlreadyLinkedException.class,
                () -> cardLinkService.createLink(USER_ID, request()));

        verifyNoInteractions(codefClient, encryptor);
    }

    @Test
    @DisplayName("카드번호 정규화 결과가 비어 있으면 검증 오류를 반환한다")
    void rejectsEmptyNormalizedCardNumber() {
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(cardPolicy());
        CreateCardLinkRequest request = request();
        request.setCardNo("----");

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class,
                () -> cardLinkService.createLink(USER_ID, request));

        assertEquals("유효한 카드번호가 필요합니다.", exception.getFields().get("cardNo"));
        verifyNoInteractions(codefClient, codefCredentialStore, fingerprintGenerator);
    }

    @Test
    @DisplayName("정책상 ID와 카드번호가 선택이어도 fingerprint 원천 부재는 명시적으로 거부한다")
    void rejectsMissingFingerprintSourceSeparatelyFromPolicy() {
        CodefIssuerPolicy policy = basePolicy();
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setIssuerId(ISSUER_ID);
        when(issuerMapper.findCodefPolicyByIssuerId(ISSUER_ID)).thenReturn(policy);

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class,
                () -> cardLinkService.createLink(USER_ID, request));

        assertEquals("중복 연동 확인을 위한 아이디가 필요합니다.", exception.getFields().get("id"));
        verifyNoInteractions(codefClient, codefCredentialMapper, codefCredentialStore, fingerprintGenerator);
    }

    private CreateCardLinkRequest request() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setIssuerId(ISSUER_ID);
        request.setId("tester");
        request.setPassword("secret-pw");
        request.setCardNo("1234567890123456");
        request.setCardPassword("1234");
        request.setBirthDate("900101");
        return request;
    }

    private CodefIssuerPolicy cardPolicy() {
        CodefIssuerPolicy policy = basePolicy();
        policy.setRequiresId(true);
        policy.setRequiresPassword(true);
        policy.setRequiresCardNo(true);
        policy.setRequiresCardPassword(true);
        policy.setRequiresBirthDate(true);
        return policy;
    }

    private CodefIssuerPolicy accountPolicy() {
        CodefIssuerPolicy policy = basePolicy();
        policy.setRequiresId(true);
        policy.setRequiresPassword(true);
        return policy;
    }

    private CodefIssuerPolicy basePolicy() {
        CodefIssuerPolicy policy = new CodefIssuerPolicy();
        policy.setIssuerId(ISSUER_ID);
        policy.setInstitutionCode("0301");
        return policy;
    }
}
