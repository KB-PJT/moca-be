package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsRequest;
import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkCardResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CardOptionSelectionRequest;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.dto.OptionSelectionRequest;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResponse;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResult;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefConnectionNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
import com.moca.mocabe.domain.codef.exception.InvalidCardSelectionException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialHasher;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CardCatalogMapper;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.IssuerMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import com.moca.mocabe.domain.codef.model.CardOptionRow;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefIssuerPolicy;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import com.moca.mocabe.domain.codef.model.LinkedCardKeyRow;
import com.moca.mocabe.domain.codef.model.LinkedCardRow;
import java.util.List;
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
    private static final String INSTITUTION_CODE = "0301";

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
    private CredentialHasher credentialHasher;
    @Mock
    private CardCatalogMatcher cardCatalogMatcher;
    @Mock
    private CardCatalogMapper cardCatalogMapper;
    @Mock
    private LinkedCardMapper linkedCardMapper;

    private CardLinkService cardLinkService;

    @BeforeEach
    void setUp() {
        cardLinkService = new CardLinkService(
                codefClient, codefCredentialMapper, codefCredentialStore,
                issuerMapper, encryptor, credentialHasher,
                cardCatalogMatcher, cardCatalogMapper, linkedCardMapper);
        // 대부분의 테스트는 재조회 중복 방지 조회 결과에 관심이 없으므로 기본값(빈 목록)을 lenient로 깔아둔다.
        lenient().when(linkedCardMapper.findLinkedCardKeysByLinkId(anyString(), anyString()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("issuerId로 기관코드를 조회해 연동하고 connectedId·암호화 자격정보를 저장한다")
    void createsLinkAndStoresCredential() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301")).thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertNotNull(response.getLinkId());
        assertEquals(INSTITUTION_CODE, response.getInstitutionCode());
        assertEquals("PENDING_CARD_ACTIVATION", response.getStatus());

        ArgumentCaptor<CodefAccountCredential> credentialCaptor =
                ArgumentCaptor.forClass(CodefAccountCredential.class);
        verify(codefCredentialStore).saveCredential(credentialCaptor.capture());
        verify(codefCredentialStore).saveCards(List.of());
        CodefAccountCredential credential = credentialCaptor.getValue();
        assertEquals(response.getLinkId(), credential.getCodefAccountCredentialId());
        assertEquals("hash-1", credential.getCredentialIdentityHash());
        assertEquals("active", credential.getStatus());
        assertNotNull(credential.getCardPasswordEnc());
    }

    @Test
    @DisplayName("등록되지 않은 발급사면 예외를 던지고 CODEF를 호출하지 않는다")
    void throwsWhenIssuerNotFound() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(null);

        IssuerNotFoundException exception = assertThrows(
                IssuerNotFoundException.class, () -> cardLinkService.createLink(USER_ID, request()));

        assertEquals("등록되지 않은 발급사입니다: " + INSTITUTION_CODE, exception.getMessage());
        verifyNoInteractions(codefClient, codefCredentialMapper, codefCredentialStore, encryptor);
    }

    @Test
    @DisplayName("카드사 정책의 필수 자격정보가 누락되면 필드 오류를 반환한다")
    void throwsWhenRequiredCredentialsMissing() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode(INSTITUTION_CODE);

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class, () -> cardLinkService.createLink(USER_ID, request));

        assertEquals(5, exception.getFields().size());
        assertEquals("아이디는 필수입니다.", exception.getFields().get("id"));
        verifyNoInteractions(codefClient, codefCredentialStore, credentialHasher);
    }

    @Test
    @DisplayName("카드번호를 받지 않는 카드사는 정규화한 로그인 ID로 중복을 확인한다")
    void checksDuplicationWithNormalizedAccountId() {
        CreateCardLinkRequest request = request();
        request.setId(" Tester ");
        request.setCardNo(null);
        request.setCardPassword(null);
        request.setBirthDate(null);
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(accountPolicy());
        when(credentialHasher.generate("ACCOUNT_ID", "tester")).thenReturn("account-hash");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-2");
        when(codefClient.getOwnedCards("cid-2", "0301")).thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1});

        cardLinkService.createLink(USER_ID, request);

        verify(codefCredentialMapper).existsByUserIdAndIssuerIdAndIdentityHash(
                USER_ID, ISSUER_ID, "account-hash");
    }

    @Test
    @DisplayName("이미 같은 카드가 연동돼 있으면 CODEF를 호출하지 않는다")
    void rejectsExistingCredential() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("duplicate");
        when(codefCredentialMapper.existsByUserIdAndIssuerIdAndIdentityHash(USER_ID, ISSUER_ID, "duplicate"))
                .thenReturn(true);

        assertThrows(CodefAccountAlreadyLinkedException.class,
                () -> cardLinkService.createLink(USER_ID, request()));

        verifyNoInteractions(codefClient, encryptor);
    }

    @Test
    @DisplayName("카드번호 정규화 결과가 비어 있으면 검증 오류를 반환한다")
    void rejectsEmptyNormalizedCardNumber() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        CreateCardLinkRequest request = request();
        request.setCardNo("----");

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class,
                () -> cardLinkService.createLink(USER_ID, request));

        assertEquals("유효한 카드번호가 필요합니다.", exception.getFields().get("cardNo"));
        verifyNoInteractions(codefClient, codefCredentialStore, credentialHasher);
    }

    @Test
    @DisplayName("식별 원천(ID) 부재는 정책과 별개로 명시적으로 거부한다")
    void rejectsMissingIdentitySource() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode(INSTITUTION_CODE);
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(basePolicy());

        CodefCredentialRequiredException exception = assertThrows(
                CodefCredentialRequiredException.class,
                () -> cardLinkService.createLink(USER_ID, request));

        assertEquals("중복 연동 확인을 위한 아이디가 필요합니다.", exception.getFields().get("id"));
        verifyNoInteractions(codefClient, codefCredentialStore, credentialHasher);
    }

    @Test
    @DisplayName("매칭 카드는 비활성으로 적재하고, 미매칭 카드는 회색 fallback으로 응답만 한다")
    void persistsMatchedAndReturnsUnmatched() {
        CodefIssuerPolicy policy = accountPolicy();
        policy.setIssuerName("KB국민카드");
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(policy);
        when(credentialHasher.generate("ACCOUNT_ID", "tester")).thenReturn("cred-hash");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-3");
        when(codefClient.getOwnedCards("cid-3", "0301")).thenReturn(List.of(
                new CodefOwnedCard("매칭 카드", "1111****2222", "", "https://codef/ignored.png"),
                new CodefOwnedCard("미매칭 체크", "3333****4444", "체크/본인", "https://codef/check.png"),
                new CodefOwnedCard("이미지 없음", "5555****6666", "기타", " ")));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString()))
                .thenReturn("k1", "k2", "k3");
        // 매칭 카드의 CODEF resCardType는 빈 값이지만, 카탈로그가 credit이므로 응답은 CREDIT이어야 한다.
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("매칭 카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩"),
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-2", "b", "B팩")));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, accountRequest());

        assertEquals(3, response.cards().size());
        CardLinkCardResponse first = response.cards().get(0);
        assertNotNull(first.userCardId());
        assertTrue(first.matched());
        assertEquals("정식 카드명", first.cardName());
        assertEquals("1111****2222", first.cardNo());
        assertEquals("https://gorilla/card.png", first.cardImageUrl());
        assertEquals("CREDIT", first.cardType());
        assertEquals(2, first.optionGroups().get(0).choices().size());
        CardLinkCardResponse second = response.cards().get(1);
        assertNull(second.userCardId());
        assertFalse(second.matched());
        assertEquals("https://codef/check.png", second.cardImageUrl());
        assertEquals("CHECK", second.cardType());
        assertNull(response.cards().get(2).cardImageUrl());
        assertEquals("UNKNOWN", response.cards().get(2).cardType());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkedCardInsert>> insertsCaptor = ArgumentCaptor.forClass(List.class);
        verify(codefCredentialStore).saveCredential(any(CodefAccountCredential.class));
        verify(codefCredentialStore).saveCards(insertsCaptor.capture());
        List<LinkedCardInsert> inserts = insertsCaptor.getValue();
        assertEquals(1, inserts.size());
        assertEquals("card-1", inserts.get(0).cardId());
        assertEquals("1111****2222", inserts.get(0).cardNo());
        assertEquals(first.userCardId(), inserts.get(0).userCardId());
        // 보유카드 3장을 순회해도 카탈로그 조회는 한 번만 일어나야 한다(N+1 방지).
        verify(cardCatalogMapper, times(1)).findCardsByIssuerId(ISSUER_ID);
    }

    @Test
    @DisplayName("같은 CODEF 카드 키가 반복되면 응답·적재 모두 한 건만 남긴다")
    void removesDuplicatedOwnedCards() {
        CodefIssuerPolicy policy = accountPolicy();
        policy.setIssuerName("KB국민카드");
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(policy);
        when(credentialHasher.generate("ACCOUNT_ID", "tester")).thenReturn("cred-hash");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-4");
        CodefOwnedCard duplicate = new CodefOwnedCard("동일 카드", "1111****2222", "신용", null);
        when(codefClient.getOwnedCards("cid-4", "0301")).thenReturn(List.of(duplicate, duplicate));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("same-key");
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, accountRequest());

        assertEquals(1, response.cards().size());
    }

    @Test
    @DisplayName("보유카드 조회가 실패해도 connectedId·자격정보 저장은 유지하고 연동 생성은 성공한다")
    void keepsCredentialWhenOwnedCardFetchFails() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301"))
                .thenThrow(new CodefUnavailableException("upstream timeout"));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertNotNull(response.getLinkId());
        assertEquals("PENDING_CARD_ACTIVATION", response.getStatus());
        assertTrue(response.cards().isEmpty());
        verify(codefCredentialStore).saveCredential(any(CodefAccountCredential.class));
        verify(codefCredentialStore, never()).saveCards(any());
        verifyNoInteractions(cardCatalogMapper);
    }

    @Test
    @DisplayName("institutionCode 없이 재조회하면 모든 활성 연동을 순회해 결과를 모은다")
    void syncsAllActiveConnectionsWhenInstitutionCodeOmitted() {
        CodefConnection kbConnection = new CodefConnection("link-kb", "cid-kb", "0301", ISSUER_ID, new byte[0]);
        CodefConnection shinhanConnection =
                new CodefConnection("link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", new byte[0]);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        CodefIssuerPolicy shinhanPolicy = basePolicy();
        shinhanPolicy.setIssuerId("issuer-shinhan");
        shinhanPolicy.setInstitutionCode("0302");
        when(issuerMapper.findCodefPolicyByInstitutionCode("0302")).thenReturn(shinhanPolicy);
        // 매칭되는 카드가 하나도 없어도(카탈로그가 비어 있어도) 실패가 아니라 빈 배열로 성공 처리돼야 한다.
        when(codefClient.getOwnedCards("cid-kb", "0301")).thenReturn(List.of());
        when(codefClient.getOwnedCards("cid-shinhan", "0302")).thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(2, response.results().size());
        for (SyncOwnedCardsResult result : response.results()) {
            assertTrue(result.success());
            assertTrue(result.cards().isEmpty());
        }
    }

    @Test
    @DisplayName("institutionCode를 주면 그 카드사 연동만 재조회한다")
    void syncsOnlyMatchingInstitutionCode() {
        CodefConnection kbConnection = new CodefConnection("link-kb", "cid-kb", "0301", ISSUER_ID, new byte[0]);
        CodefConnection shinhanConnection =
                new CodefConnection("link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", new byte[0]);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(codefClient.getOwnedCards("cid-kb", "0301")).thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, "0301");

        assertEquals(1, response.results().size());
        assertEquals("link-kb", response.results().get(0).linkId());
        verify(codefClient, never()).getOwnedCards("cid-shinhan", "0302");
    }

    @Test
    @DisplayName("지정한 기관코드로 연동된 활성 계정이 없으면 예외를 던진다")
    void rejectsSyncWhenInstitutionCodeHasNoConnection() {
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of());

        assertThrows(CodefConnectionNotFoundException.class,
                () -> cardLinkService.syncOwnedCards(USER_ID, "0301"));
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("한 연동의 재조회 실패는 다른 연동 결과에 영향을 주지 않는다")
    void isolatesFailureOfOneConnectionDuringSync() {
        CodefConnection kbConnection = new CodefConnection("link-kb", "cid-kb", "0301", ISSUER_ID, new byte[0]);
        CodefConnection shinhanConnection =
                new CodefConnection("link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", new byte[0]);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        CodefIssuerPolicy shinhanPolicy = basePolicy();
        shinhanPolicy.setIssuerId("issuer-shinhan");
        shinhanPolicy.setInstitutionCode("0302");
        when(issuerMapper.findCodefPolicyByInstitutionCode("0302")).thenReturn(shinhanPolicy);
        when(codefClient.getOwnedCards("cid-kb", "0301"))
                .thenThrow(new CodefUnavailableException("timeout", new java.io.IOException("connection reset")));
        when(codefClient.getOwnedCards("cid-shinhan", "0302")).thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(2, response.results().size());
        SyncOwnedCardsResult kbResult = response.results().stream()
                .filter(result -> "link-kb".equals(result.linkId())).findFirst().orElseThrow();
        SyncOwnedCardsResult shinhanResult = response.results().stream()
                .filter(result -> "link-shinhan".equals(result.linkId())).findFirst().orElseThrow();
        assertFalse(kbResult.success());
        assertTrue(kbResult.cards().isEmpty());
        assertTrue(shinhanResult.success());
    }

    @Test
    @DisplayName("이미 적재된 카드는 재조회해도 다시 적재하지 않고 기존 userCardId를 재사용한다")
    void reusesExistingUserCardIdOnResync() {
        CodefConnection kbConnection = new CodefConnection("link-kb", "cid-kb", "0301", ISSUER_ID, new byte[0]);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        CodefIssuerPolicy policy = cardPolicy();
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(policy);
        when(codefClient.getOwnedCards("cid-kb", "0301")).thenReturn(List.of(
                new CodefOwnedCard("매칭 카드", "1111****2222", "신용", null)));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("existing-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("매칭 카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(linkedCardMapper.findLinkedCardKeysByLinkId("link-kb", USER_ID))
                .thenReturn(List.of(new LinkedCardKeyRow("existing-uc-1", "existing-key")));

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        CardLinkCardResponse card = response.results().get(0).cards().get(0);
        assertEquals("existing-uc-1", card.userCardId());
        assertTrue(card.matched());
        verify(codefCredentialStore).saveCards(List.of());
    }

    @Test
    @DisplayName("선택한 카드를 활성화하고 옵션은 upsert한다")
    void activatesSelectedCardsAndUpsertsOptions() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                new LinkedCardRow("uc-1", "card-1"), new LinkedCardRow("uc-2", "card-2")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-2")).thenReturn(List.of());
        ActivateCardLinkCardsRequest request = activateRequest(List.of("uc-1", "uc-2"),
                optionFor("uc-1", selection("group-1", "choice-1")));

        ActivateCardLinkCardsResponse response = cardLinkService.activateCards(USER_ID, linkId, request);

        assertEquals(2, response.activatedCount());
        assertEquals(List.of("uc-1", "uc-2"), response.activatedUserCardIds());
        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1", "uc-2"));
        verify(linkedCardMapper).upsertOptionSelection("uc-1", "group-1", "card-1", "choice-1");
        verify(linkedCardMapper, never()).upsertOptionSelection(eq("uc-2"), any(), any(), any());
    }

    @Test
    @DisplayName("옵션 없는 카드는 옵션 선택 없이 활성화만 한다")
    void activatesOptionlessCardWithoutSelections() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID))
                .thenReturn(List.of(new LinkedCardRow("uc-1", "card-1")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());

        ActivateCardLinkCardsResponse response = cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1")));

        assertEquals(1, response.activatedCount());
        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1"));
        verify(linkedCardMapper, never()).upsertOptionSelection(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("본인 소유 연동이 아니면 활성화를 거부한다")
    void rejectsUnknownLink() {
        when(codefCredentialMapper.lockOwnedLink("missing", USER_ID)).thenReturn(null);

        assertThrows(CardLinkNotFoundException.class, () -> cardLinkService.activateCards(
                USER_ID, "missing", activateRequest(List.of("uc-1"))));
    }

    @Test
    @DisplayName("이 연동에 속하지 않은 카드를 활성화하려 하면 거부한다")
    void rejectsForeignCardActivation() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID))
                .thenReturn(List.of(new LinkedCardRow("uc-1", "card-1")));

        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-2"))));
    }

    @Test
    @DisplayName("활성화하지 않는 카드의 옵션이나 같은 카드 옵션 중복 전송은 거부한다")
    void rejectsMisdirectedOrDuplicateOptions() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                new LinkedCardRow("uc-1", "card-1"), new LinkedCardRow("uc-2", "card-2")));

        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"),
                        optionFor("uc-2", selection("group-1", "choice-1")))));
        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"),
                        optionFor("uc-1", selection("group-1", "choice-1")),
                        optionFor("uc-1", selection("group-2", "choice-2")))));
    }

    @Test
    @DisplayName("옵션 필수 카드에서 선택 누락·잘못된 선택은 거부한다")
    void rejectsInvalidOptions() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID))
                .thenReturn(List.of(new LinkedCardRow("uc-1", "card-1")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩")));

        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"))));
        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"),
                        optionFor("uc-1", selection("wrong-group", "choice-1")))));
    }

    private ActivateCardLinkCardsRequest activateRequest(List<String> activeIds,
                                                         CardOptionSelectionRequest... options) {
        ActivateCardLinkCardsRequest request = new ActivateCardLinkCardsRequest();
        request.setActiveUserCardIds(activeIds);
        if (options.length > 0) {
            request.setOptionSelections(List.of(options));
        }
        return request;
    }

    private CardOptionSelectionRequest optionFor(String userCardId, OptionSelectionRequest... selections) {
        CardOptionSelectionRequest option = new CardOptionSelectionRequest();
        option.setUserCardId(userCardId);
        option.setOptionSelections(List.of(selections));
        return option;
    }

    private OptionSelectionRequest selection(String groupId, String choiceId) {
        OptionSelectionRequest selection = new OptionSelectionRequest();
        selection.setOptionGroupId(groupId);
        selection.setOptionChoiceId(choiceId);
        return selection;
    }

    private CreateCardLinkRequest accountRequest() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode(INSTITUTION_CODE);
        request.setId("tester");
        request.setPassword("secret-pw");
        return request;
    }

    private CreateCardLinkRequest request() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode(INSTITUTION_CODE);
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
