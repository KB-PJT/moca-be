package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import com.moca.mocabe.domain.codef.dto.SubmitCardCredentialsRequest;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResponse;
import com.moca.mocabe.domain.codef.dto.SyncOwnedCardsResult;
import com.moca.mocabe.domain.codef.exception.CardCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.CardNumberMismatchException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefConnectionNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
import com.moca.mocabe.domain.codef.exception.CodefUnavailableException;
import com.moca.mocabe.domain.codef.exception.InvalidCardSelectionException;
import com.moca.mocabe.domain.codef.exception.IssuerNotFoundException;
import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialHasher;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CardCatalogMapper;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.IssuerMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.model.ActiveCardCredential;
import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import com.moca.mocabe.domain.codef.model.CardCredentialIssue;
import com.moca.mocabe.domain.codef.model.CardCredentialSubmissionTarget;
import com.moca.mocabe.domain.codef.model.CardOptionRow;
import com.moca.mocabe.domain.codef.model.CodefAccountCredential;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefIssuerPolicy;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import com.moca.mocabe.domain.codef.model.LinkedCardKeyRow;
import com.moca.mocabe.domain.codef.model.LinkedCardRow;
import com.moca.mocabe.domain.codef.model.PendingCardDiscoveryTarget;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

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
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private com.moca.mocabe.domain.home.service.HomeCardsCache homeCardsCache;

    private CardLinkService cardLinkService;

    @BeforeEach
    void setUp() {
        cardLinkService = new CardLinkService(
                codefClient, codefCredentialMapper, codefCredentialStore,
                issuerMapper, encryptor, credentialHasher,
                cardCatalogMatcher, cardCatalogMapper, linkedCardMapper, transactionManager);
        // 재조회 경로는 TransactionTemplate으로 lockOwnedLink를 감싸므로, 트랜잭션 매니저가 항상
        // 유효한 트랜잭션을 돌려주는 상황을 기본으로 깔아둔다(실제 커밋/롤백 여부는 검증 대상이 아님).
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        // 대부분의 테스트는 재조회 중복 방지 조회 결과에 관심이 없으므로 기본값(빈 목록)을 lenient로 깔아둔다.
        lenient().when(linkedCardMapper.findLinkedCardKeysByLinkId(anyString(), anyString()))
                .thenReturn(List.of());
        // 기본은 동시성 충돌 없이 요청한 그대로 적재되는 상황을 가정한다(경합 시나리오는 각 테스트에서 재정의).
        lenient().when(codefCredentialStore.saveCard(any(LinkedCardInsert.class)))
                .thenAnswer(invocation -> invocation.<LinkedCardInsert>getArgument(0).userCardId());
    }

    @Test
    @DisplayName("카드번호가 필요한 카드사도 방금 입력받은 카드번호로 즉시 보유카드를 조회하고, "
            + "계정 생성 카드가 실제로 저장되면 그 자리에서 pending을 지운다")
    void createsLinkAndPersistsCreatorCardImmediatelyForCardNoIssuer() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        // cardPolicy()의 institutionCode(0301)는 KB카드라 카드 비밀번호가 앞 2자리로 잘려 CODEF에 전달된다.
        // 계정 생성 카드번호(1234567890123456)와 마스킹이 일치하는 보유카드를 CODEF가 돌려주고, 카탈로그에도 매칭된다.
        when(codefClient.getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12"))
                .thenReturn(List.of(new CodefOwnedCard("정식 카드명", "123456******3456", "체크/본인", "")));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("new-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "check", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("정식 카드명"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertNotNull(response.getLinkId());
        assertEquals(INSTITUTION_CODE, response.getInstitutionCode());
        assertEquals("PENDING_CARD_ACTIVATION", response.getStatus());
        assertEquals(1, response.cards().size());
        assertTrue(response.cards().get(0).matched());

        ArgumentCaptor<CodefAccountCredential> credentialCaptor =
                ArgumentCaptor.forClass(CodefAccountCredential.class);
        verify(codefCredentialStore).saveCredential(credentialCaptor.capture());
        verify(codefCredentialStore).saveCard(any());
        verify(codefClient).getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12");
        verify(codefCredentialMapper).clearPendingCardCredentials(response.getLinkId(), USER_ID);
        CodefAccountCredential credential = credentialCaptor.getValue();
        assertEquals(response.getLinkId(), credential.getCodefAccountCredentialId());
        assertEquals("hash-1", credential.getCredentialIdentityHash());
        assertEquals("active", credential.getStatus());
        assertNotNull(credential.getAccountPasswordEnc());
        assertNotNull(credential.getPendingCardNumberEnc());
        assertNotNull(credential.getPendingCardPasswordEnc());
    }

    @Test
    @DisplayName("카드번호가 필요한 카드사에서 즉시 조회는 성공했지만 계정 생성 카드가 카탈로그 매칭 "
            + "실패 등으로 저장되지 않으면 pending을 지우지 않는다(재조회가 이어서 재시도)")
    void keepsPendingWhenImmediateFetchSucceedsButCreatorCardIsNotPersisted() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12"))
                .thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertTrue(response.cards().isEmpty());
        verify(codefCredentialStore, never()).saveCard(any());
        verify(codefClient).getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12");
        verify(codefCredentialMapper, never()).clearPendingCardCredentials(any(), any());
    }

    @Test
    @DisplayName("카드번호가 필요한 카드사에서 즉시 보유카드 조회가 실패해도 connectedId·pending은 "
            + "그대로 유지해 POST /card-links/cards/sync로 재시도할 수 있다")
    void keepsPendingWhenImmediateOwnedCardFetchFailsForCardNoIssuer() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12"))
                .thenThrow(new CodefUnavailableException("upstream timeout"));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertNotNull(response.getLinkId());
        assertEquals("PENDING_CARD_ACTIVATION", response.getStatus());
        assertTrue(response.cards().isEmpty());
        verify(codefCredentialStore).saveCredential(any(CodefAccountCredential.class));
        verify(codefCredentialStore, never()).saveCard(any());
        verify(codefCredentialMapper, never()).clearPendingCardCredentials(any(), any());
        // 짧게 대기 후 한 번만 자동 재시도한다(최초 시도 + 재시도 1회 = 총 2회).
        verify(codefClient, times(2)).getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12");
    }

    @Test
    @DisplayName("즉시 보유카드 조회가 첫 시도에 실패해도 짧게 대기 후 재시도가 성공하면 그 결과를 쓴다")
    void retriesOnceAndSucceedsForCardNoIssuer() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        // 첫 시도는 실패하고, 재시도(두 번째 호출)에서 계정 생성 카드번호와 마스킹이 일치하는 보유카드를 돌려준다.
        when(codefClient.getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12"))
                .thenThrow(new CodefUnavailableException("upstream timeout"))
                .thenReturn(List.of(new CodefOwnedCard("정식 카드명", "123456******3456", "체크/본인", "")));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("new-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "check", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("정식 카드명"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

        assertEquals(1, response.cards().size());
        assertTrue(response.cards().get(0).matched());
        verify(codefClient, times(2)).getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12");
        verify(codefCredentialStore).saveCard(any());
        // 재시도로 CODEF 호출이 성공해 계정 생성 카드가 실제로 저장됐으니 pending을 지운다.
        verify(codefCredentialMapper).clearPendingCardCredentials(response.getLinkId(), USER_ID);
    }

    @Test
    @DisplayName("재시도 대기 중 스레드가 인터럽트되어도 예외를 삼키고 재시도를 계속한다")
    void continuesRetryWhenSleepIsInterrupted() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(cardPolicy());
        when(credentialHasher.generate("CARD_NO", "1234567890123456")).thenReturn("hash-1");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12"))
                .thenThrow(new CodefUnavailableException("upstream timeout"));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});
        // 대기(Thread.sleep) 전에 인터럽트를 걸어두면 실제로 기다리지 않고 바로 InterruptedException이
        // 발생해, 재시도 대기 중 인터럽트되는 경로를 실제로 대기하지 않고도 검증할 수 있다.
        Thread.currentThread().interrupt();
        try {
            CardLinkResponse response = cardLinkService.createLink(USER_ID, request());

            assertTrue(response.cards().isEmpty());
            verify(codefClient, times(2)).getOwnedCards("cid-1", "0301", "900101", "1234567890123456", "12");
        } finally {
            // 이 테스트가 세팅한 인터럽트 상태가 이후 다른 테스트로 새지 않도록 지운다.
            Thread.interrupted();
        }
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
        when(codefClient.getOwnedCards("cid-2", "0301", null, null, null)).thenReturn(List.of());
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
        when(codefClient.getOwnedCards("cid-3", "0301", null, null, null)).thenReturn(List.of(
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

        ArgumentCaptor<LinkedCardInsert> insertCaptor = ArgumentCaptor.forClass(LinkedCardInsert.class);
        verify(codefCredentialStore).saveCredential(any(CodefAccountCredential.class));
        verify(codefCredentialStore).saveCard(insertCaptor.capture());
        LinkedCardInsert insert = insertCaptor.getValue();
        assertEquals("card-1", insert.cardId());
        assertEquals("1111****2222", insert.cardNo());
        assertEquals(first.userCardId(), insert.userCardId());
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
        when(codefClient.getOwnedCards("cid-4", "0301", null, null, null)).thenReturn(List.of(duplicate, duplicate));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("same-key");
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, accountRequest());

        assertEquals(1, response.cards().size());
    }

    @Test
    @DisplayName("카드번호가 필요 없는 카드사는 보유카드 조회가 실패해도 connectedId·자격정보 저장은 "
            + "유지하고 연동 생성은 성공한다")
    void keepsCredentialWhenOwnedCardFetchFails() {
        when(issuerMapper.findCodefPolicyByInstitutionCode(INSTITUTION_CODE)).thenReturn(accountPolicy());
        when(credentialHasher.generate("ACCOUNT_ID", "tester")).thenReturn("cred-hash");
        when(codefClient.createConnectedId(any(CodefConnectionCommand.class))).thenReturn("cid-1");
        when(codefClient.getOwnedCards("cid-1", "0301", null, null, null))
                .thenThrow(new CodefUnavailableException("upstream timeout"));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});

        CardLinkResponse response = cardLinkService.createLink(USER_ID, accountRequest());

        assertNotNull(response.getLinkId());
        assertEquals("PENDING_CARD_ACTIVATION", response.getStatus());
        assertTrue(response.cards().isEmpty());
        verify(codefCredentialStore).saveCredential(any(CodefAccountCredential.class));
        verify(codefCredentialStore, never()).saveCard(any());
        verifyNoInteractions(cardCatalogMapper);
    }

    @Test
    @DisplayName("재조회 시, 아직 크리덴셜을 가진 카드가 없으면 pending을 claim해서 그 카드번호로 "
            + "조회하고, 카드가 실제로 저장되면 pending을 복구하지 않는다")
    void syncClaimsPendingCredentialsWhenNoCardHasCredentialsYetAndKeepsItClearedWhenCardIsStored() {
        String linkId = "link-kb";
        byte[] pendingCardNoEnc = {7, 7};
        byte[] pendingCardPasswordEnc = {6, 6};
        CodefConnection kbConnection = new CodefConnection(
                linkId, "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], true, true);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        PendingCardDiscoveryTarget target = new PendingCardDiscoveryTarget(
                linkId, "cid-kb", "0301", new byte[0], true, pendingCardNoEnc, pendingCardPasswordEnc);
        when(codefCredentialMapper.findPendingDiscoveryTarget(linkId, USER_ID)).thenReturn(target);
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(encryptor.decrypt(kbConnection.birthDateEnc())).thenReturn("900101");
        when(encryptor.decrypt(pendingCardNoEnc)).thenReturn("1234567890123456");
        when(encryptor.decrypt(pendingCardPasswordEnc)).thenReturn("1234");
        // 계정 생성 카드번호와 마스킹이 일치하는 보유카드 한 장을 CODEF가 돌려주고, 카탈로그에도 매칭된다.
        when(codefClient.getOwnedCards("cid-kb", "0301", "900101", "1234567890123456", "1234"))
                .thenReturn(List.of(new CodefOwnedCard("노리2 체크카드", "123456******3456", "체크/본인", "")));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("new-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("노리2 체크카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {9});

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).success());
        assertEquals(1, response.results().get(0).cards().size());
        // pending을 우선 claim해서 썼으니, 이미 크리덴셜 있는 카드를 찾는 폴백 조회는 필요 없다.
        verify(linkedCardMapper, never()).findAnyCardCredentialByLinkId(any());
        verify(codefCredentialMapper).clearPendingCardCredentials(linkId, USER_ID);
        verify(codefCredentialStore, never()).restorePendingCardCredentials(any(), any(), any(), any());
    }

    @Test
    @DisplayName("재조회 시 pending을 claim했는데, 이전 시도(sync가 다른 카드번호로 먼저 적재한 경우 등)에서 "
            + "크리덴셜 없이 이미 적재된 계정 생성 카드가 있으면 크리덴셜을 채워 넣는다")
    void syncBackfillsCredentialsForAlreadyPersistedCreatorCardWhenClaimingPending() {
        String linkId = "link-kb";
        byte[] pendingCardNoEnc = {7, 7};
        byte[] pendingCardPasswordEnc = {6, 6};
        CodefConnection kbConnection = new CodefConnection(
                linkId, "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], true, true);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        PendingCardDiscoveryTarget target = new PendingCardDiscoveryTarget(
                linkId, "cid-kb", "0301", new byte[0], true, pendingCardNoEnc, pendingCardPasswordEnc);
        when(codefCredentialMapper.findPendingDiscoveryTarget(linkId, USER_ID)).thenReturn(target);
        CodefIssuerPolicy policy = cardPolicy();
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(policy);
        when(encryptor.decrypt(kbConnection.birthDateEnc())).thenReturn("900101");
        when(encryptor.decrypt(pendingCardNoEnc)).thenReturn("1234567890123456");
        when(encryptor.decrypt(pendingCardPasswordEnc)).thenReturn("1234");
        // 계정 생성 카드번호와 마스킹이 일치하는 보유카드 한 장을 CODEF가 돌려준다.
        when(codefClient.getOwnedCards("cid-kb", "0301", "900101", "1234567890123456", "1234"))
                .thenReturn(List.of(new CodefOwnedCard("노리2 체크카드", "123456******3456", "체크/본인", "")));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("existing-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("노리2 체크카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        // 이 카드는 이미 크리덴셜 없이 적재돼 있다(예: sync가 다른 카드번호로 먼저 조회하며 적재).
        when(linkedCardMapper.findLinkedCardKeysByLinkId(linkId, USER_ID))
                .thenReturn(List.of(new LinkedCardKeyRow("existing-uc-1", "existing-key", false)));
        when(encryptor.encrypt(anyString())).thenReturn(new byte[] {9});

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertTrue(response.results().get(0).success());
        assertEquals(1, response.results().get(0).cards().size());
        assertEquals("existing-uc-1", response.results().get(0).cards().get(0).userCardId());
        verify(linkedCardMapper).updateCardCredentials(
                eq("existing-uc-1"), eq(USER_ID), any(byte[].class), any(byte[].class));
        verify(codefCredentialStore, never()).saveCard(any());
        verify(codefCredentialMapper).clearPendingCardCredentials(linkId, USER_ID);
        verify(codefCredentialStore, never()).restorePendingCardCredentials(any(), any(), any(), any());
    }

    @Test
    @DisplayName("재조회 시 pending으로 조회했지만 계정 생성 카드가 저장되지 않으면(카탈로그 매칭 "
            + "실패 등) pending을 그대로 복구하고, 이 연동은 여전히 success=true다")
    void syncRestoresPendingCredentialsWhenNoCardStoresTheCreatorCardNumber() {
        String linkId = "link-kb";
        byte[] pendingCardNoEnc = {7, 7};
        byte[] pendingCardPasswordEnc = {6, 6};
        CodefConnection kbConnection = new CodefConnection(
                linkId, "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], true, true);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        PendingCardDiscoveryTarget target = new PendingCardDiscoveryTarget(
                linkId, "cid-kb", "0301", new byte[0], true, pendingCardNoEnc, pendingCardPasswordEnc);
        when(codefCredentialMapper.findPendingDiscoveryTarget(linkId, USER_ID)).thenReturn(target);
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(encryptor.decrypt(kbConnection.birthDateEnc())).thenReturn("900101");
        when(encryptor.decrypt(pendingCardNoEnc)).thenReturn("1234567890123456");
        when(encryptor.decrypt(pendingCardPasswordEnc)).thenReturn("1234");
        when(codefClient.getOwnedCards("cid-kb", "0301", "900101", "1234567890123456", "1234"))
                .thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).success());
        assertTrue(response.results().get(0).cards().isEmpty());
        verify(codefCredentialMapper).clearPendingCardCredentials(linkId, USER_ID);
        // claim이 읽어둔 pending 값 그대로(재암호화하지 않고) 복구한다.
        verify(codefCredentialStore).restorePendingCardCredentials(
                linkId, USER_ID, pendingCardNoEnc, pendingCardPasswordEnc);
    }

    @Test
    @DisplayName("재조회 도중 pending으로 CODEF 호출이 실패하면 pending을 복구하고 이 연동만 "
            + "success=false로 응답한다")
    void syncRestoresPendingCredentialsWhenCodefCallFails() {
        String linkId = "link-kb";
        byte[] pendingCardNoEnc = {7, 7};
        byte[] pendingCardPasswordEnc = {6, 6};
        CodefConnection kbConnection = new CodefConnection(
                linkId, "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], true, true);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        PendingCardDiscoveryTarget target = new PendingCardDiscoveryTarget(
                linkId, "cid-kb", "0301", new byte[0], true, pendingCardNoEnc, pendingCardPasswordEnc);
        when(codefCredentialMapper.findPendingDiscoveryTarget(linkId, USER_ID)).thenReturn(target);
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(encryptor.decrypt(kbConnection.birthDateEnc())).thenReturn("900101");
        when(encryptor.decrypt(pendingCardNoEnc)).thenReturn("1234567890123456");
        when(encryptor.decrypt(pendingCardPasswordEnc)).thenReturn("1234");
        when(codefClient.getOwnedCards("cid-kb", "0301", "900101", "1234567890123456", "1234"))
                .thenThrow(new CodefUnavailableException("upstream timeout"));

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        assertFalse(response.results().get(0).success());
        verify(codefCredentialMapper).clearPendingCardCredentials(linkId, USER_ID);
        verify(codefCredentialStore).restorePendingCardCredentials(
                linkId, USER_ID, pendingCardNoEnc, pendingCardPasswordEnc);
    }

    @Test
    @DisplayName("institutionCode 없이 재조회하면 모든 활성 연동을 순회해 결과를 모은다")
    void syncsAllActiveConnectionsWhenInstitutionCodeOmitted() {
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        CodefConnection shinhanConnection =
                new CodefConnection(
                        "link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", "신한카드", null, new byte[0],
                        false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        CodefIssuerPolicy shinhanPolicy = basePolicy();
        shinhanPolicy.setIssuerId("issuer-shinhan");
        shinhanPolicy.setInstitutionCode("0302");
        when(issuerMapper.findCodefPolicyByInstitutionCode("0302")).thenReturn(shinhanPolicy);
        // 매칭되는 카드가 하나도 없어도(카탈로그가 비어 있어도) 실패가 아니라 빈 배열로 성공 처리돼야 한다.
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null)).thenReturn(List.of());
        when(codefClient.getOwnedCards("cid-shinhan", "0302", null, null, null)).thenReturn(List.of());

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
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        CodefConnection shinhanConnection =
                new CodefConnection(
                        "link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", "신한카드", null, new byte[0],
                        false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null)).thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, "0301");

        assertEquals(1, response.results().size());
        assertEquals("link-kb", response.results().get(0).linkId());
        verify(codefClient, never()).getOwnedCards("cid-shinhan", "0302", null, null, null);
    }

    @Test
    @DisplayName("카드번호가 필요한 카드사는 이미 저장된 카드번호로 CODEF 재조회를 요청한다")
    void syncsWithStoredCardCredentialsWhenIssuerRequiresCardNo() {
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], true, true);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        byte[] cardNumberEnc = {9, 9};
        byte[] cardPasswordEnc = {8, 8};
        when(linkedCardMapper.findAnyCardCredentialByLinkId("link-kb"))
                .thenReturn(new ActiveCardCredential("uc-existing", cardNumberEnc, cardPasswordEnc));
        when(encryptor.decrypt(kbConnection.birthDateEnc())).thenReturn(null);
        when(encryptor.decrypt(cardNumberEnc)).thenReturn("1234567890123456");
        when(encryptor.decrypt(cardPasswordEnc)).thenReturn("1234");
        when(codefClient.getOwnedCards("cid-kb", "0301", null, "1234567890123456", "1234"))
                .thenReturn(List.of());

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).success());
        verify(codefClient).getOwnedCards("cid-kb", "0301", null, "1234567890123456", "1234");
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
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        CodefConnection shinhanConnection =
                new CodefConnection(
                        "link-shinhan", "cid-shinhan", "0302", "issuer-shinhan", "신한카드", null, new byte[0],
                        false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID))
                .thenReturn(List.of(kbConnection, shinhanConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        CodefIssuerPolicy shinhanPolicy = basePolicy();
        shinhanPolicy.setIssuerId("issuer-shinhan");
        shinhanPolicy.setInstitutionCode("0302");
        when(issuerMapper.findCodefPolicyByInstitutionCode("0302")).thenReturn(shinhanPolicy);
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null))
                .thenThrow(new CodefUnavailableException("timeout", new java.io.IOException("connection reset")));
        when(codefClient.getOwnedCards("cid-shinhan", "0302", null, null, null)).thenReturn(List.of());

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
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        CodefIssuerPolicy policy = cardPolicy();
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(policy);
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null)).thenReturn(List.of(
                new CodefOwnedCard("매칭 카드", "1111****2222", "신용", null)));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("existing-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("매칭 카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(linkedCardMapper.findLinkedCardKeysByLinkId("link-kb", USER_ID))
                .thenReturn(List.of(new LinkedCardKeyRow("existing-uc-1", "existing-key", false)));

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        CardLinkCardResponse card = response.results().get(0).cards().get(0);
        assertEquals("existing-uc-1", card.userCardId());
        assertTrue(card.matched());
        verify(codefCredentialStore, never()).saveCard(any());
    }

    @Test
    @DisplayName("이미 활성화된 카드는 재조회 응답에서 제외한다")
    void excludesAlreadyActiveCardFromResyncResponse() {
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        CodefIssuerPolicy policy = cardPolicy();
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(policy);
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null)).thenReturn(List.of(
                new CodefOwnedCard("매칭 카드", "1111****2222", "신용", null)));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("active-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("매칭 카드"))).thenReturn(matched);
        when(linkedCardMapper.findLinkedCardKeysByLinkId("link-kb", USER_ID))
                .thenReturn(List.of(new LinkedCardKeyRow("active-uc-1", "active-key", true)));

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        assertEquals(1, response.results().size());
        assertTrue(response.results().get(0).success());
        assertTrue(response.results().get(0).cards().isEmpty());
        verify(codefCredentialStore, never()).saveCard(any());
    }

    @Test
    @DisplayName("동시 재조회로 다른 요청이 먼저 적재했다면 새로 만든 ID 대신 그 요청의 userCardId를 응답에 반영한다")
    void usesWinningUserCardIdWhenConcurrentResyncRacesOnSameCard() {
        CodefConnection kbConnection = new CodefConnection(
                "link-kb", "cid-kb", "0301", ISSUER_ID, "KB카드", null, new byte[0], false, false);
        when(codefCredentialMapper.findActiveConnectionsByUserId(USER_ID)).thenReturn(List.of(kbConnection));
        when(issuerMapper.findCodefPolicyByInstitutionCode("0301")).thenReturn(cardPolicy());
        when(codefClient.getOwnedCards("cid-kb", "0301", null, null, null)).thenReturn(List.of(
                new CodefOwnedCard("매칭 카드", "1111****2222", "신용", null)));
        when(credentialHasher.generate(eq("CODEF_CARD"), anyString())).thenReturn("race-key");
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardsByIssuerId(ISSUER_ID)).thenReturn(List.of(matched));
        when(cardCatalogMatcher.match(any(), eq("매칭 카드"))).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        // 사전 조회 시점(findLinkedCardKeysByLinkId)엔 아직 없었지만, 실제 INSERT 시점엔 동시 요청이
        // 먼저 커밋해 UNIQUE 충돌이 나고, saveCard는 그 요청의 기존 userCardId를 돌려준다.
        when(codefCredentialStore.saveCard(any(LinkedCardInsert.class))).thenReturn("uc-from-other-request");

        SyncOwnedCardsResponse response = cardLinkService.syncOwnedCards(USER_ID, null);

        CardLinkCardResponse card = response.results().get(0).cards().get(0);
        assertEquals("uc-from-other-request", card.userCardId());
        assertTrue(card.matched());
    }

    @Test
    @DisplayName("선택한 카드를 활성화하고 옵션은 upsert한다")
    void activatesSelectedCardsAndUpsertsOptions() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                linkedCardRow("uc-1", "card-1"), linkedCardRow("uc-2", "card-2")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-2")).thenReturn(List.of());
        ActivateCardLinkCardsRequest request = activateRequest(List.of("uc-1", "uc-2"),
                optionFor("uc-1", selection("group-1", "choice-1")));

        ActivateCardLinkCardsResponse response = cardLinkService.activateCards(USER_ID, linkId, request);

        assertEquals(2, response.activatedCount());
        assertEquals(List.of("uc-1", "uc-2"), response.activatedUserCardIds());
        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1", "uc-2"), 0);
        verify(linkedCardMapper).upsertOptionSelection("uc-1", "group-1", "card-1", "choice-1");
        verify(linkedCardMapper, never()).upsertOptionSelection(eq("uc-2"), any(), any(), any());
    }

    @Test
    @DisplayName("카드를 활성화하면 홈 카드 캐시를 비운다")
    void activatingCardsEvictsHomeCardsCache() {
        String linkId = "link-1";
        CardLinkService cachedService = new CardLinkService(
                codefClient, codefCredentialMapper, codefCredentialStore,
                issuerMapper, encryptor, credentialHasher,
                cardCatalogMatcher, cardCatalogMapper, linkedCardMapper, transactionManager, homeCardsCache);
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                linkedCardRow("uc-1", "card-1")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());

        cachedService.activateCards(USER_ID, linkId, activateRequest(List.of("uc-1")));

        verify(homeCardsCache).evictAll(USER_ID);
    }

    @Test
    @DisplayName("이미 활성 카드가 있는 상태에서 재활성화하면 그 뒤 순서부터 새로 매긴다")
    void reactivatesCardsAfterCurrentMaxDisplayOrder() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                linkedCardRow("uc-1", "card-1"), linkedCardRow("uc-2", "card-2")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-2")).thenReturn(List.of());
        // 이미 활성 카드 3장(0~2번)이 있어, 새로 활성화하는 카드는 예전 display_order를 버리고 3번부터 받아야 한다.
        when(linkedCardMapper.findNextDisplayOrder(USER_ID)).thenReturn(3);
        ActivateCardLinkCardsRequest request = activateRequest(List.of("uc-1", "uc-2"));

        cardLinkService.activateCards(USER_ID, linkId, request);

        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1", "uc-2"), 3);
    }

    @Test
    @DisplayName("옵션 없는 카드는 옵션 선택 없이 활성화만 한다")
    void activatesOptionlessCardWithoutSelections() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID))
                .thenReturn(List.of(linkedCardRow("uc-1", "card-1")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());

        ActivateCardLinkCardsResponse response = cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1")));

        assertEquals(1, response.activatedCount());
        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1"), 0);
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
                .thenReturn(List.of(linkedCardRow("uc-1", "card-1")));

        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-2"))));
    }

    @Test
    @DisplayName("활성화하지 않는 카드의 옵션이나 같은 카드 옵션 중복 전송은 거부한다")
    void rejectsMisdirectedOrDuplicateOptions() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                linkedCardRow("uc-1", "card-1"), linkedCardRow("uc-2", "card-2")));

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
                .thenReturn(List.of(linkedCardRow("uc-1", "card-1")));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩")));

        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"))));
        assertThrows(InvalidCardSelectionException.class, () -> cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1"),
                        optionFor("uc-1", selection("wrong-group", "choice-1")))));
    }

    @Test
    @DisplayName("카드사가 카드번호를 요구하는데 카드에 저장된 카드번호가 없으면 활성화를 거부한다")
    void rejectsActivationWhenCardNumberMissing() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(
                List.of(new LinkedCardRow("uc-1", "card-1", true, true, false, false)));

        CardCredentialRequiredException exception = assertThrows(CardCredentialRequiredException.class,
                () -> cardLinkService.activateCards(USER_ID, linkId, activateRequest(List.of("uc-1"))));

        assertEquals(1, exception.getIssues().size());
        CardCredentialIssue issue = exception.getIssues().get(0);
        assertEquals("uc-1", issue.userCardId());
        assertEquals("카드번호가 필요합니다.", issue.fields().get("cardNo"));
        assertEquals("카드 비밀번호가 필요합니다.", issue.fields().get("cardPassword"));
        verify(linkedCardMapper, never()).activateCards(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("여러 카드를 한 번에 활성화할 때 카드정보가 부족한 카드가 여럿이면 전부 모아서 알려준다")
    void rejectsActivationWithAllCardsMissingCredentials() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(List.of(
                new LinkedCardRow("uc-1", "card-1", true, true, false, false),
                new LinkedCardRow("uc-2", "card-2", true, false, false, true),
                new LinkedCardRow("uc-3", "card-3", true, true, true, true)));

        CardCredentialRequiredException exception = assertThrows(CardCredentialRequiredException.class,
                () -> cardLinkService.activateCards(
                        USER_ID, linkId, activateRequest(List.of("uc-1", "uc-2", "uc-3"))));

        // uc-3은 카드번호·비밀번호가 모두 있으므로 문제 목록에 포함되지 않는다.
        assertEquals(2, exception.getIssues().size());
        CardCredentialIssue first = exception.getIssues().get(0);
        assertEquals("uc-1", first.userCardId());
        assertEquals("카드번호가 필요합니다.", first.fields().get("cardNo"));
        assertEquals("카드 비밀번호가 필요합니다.", first.fields().get("cardPassword"));
        CardCredentialIssue second = exception.getIssues().get(1);
        assertEquals("uc-2", second.userCardId());
        assertEquals("카드번호가 필요합니다.", second.fields().get("cardNo"));
        assertNull(second.fields().get("cardPassword"));
        verify(linkedCardMapper, never()).activateCards(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("요청에 같은 카드 ID가 중복돼 있으면 문제 카드 목록에도 한 번만 담긴다")
    void deduplicatesCredentialIssuesForRepeatedUserCardId() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(
                List.of(new LinkedCardRow("uc-1", "card-1", true, true, false, false)));

        CardCredentialRequiredException exception = assertThrows(CardCredentialRequiredException.class,
                () -> cardLinkService.activateCards(
                        USER_ID, linkId, activateRequest(List.of("uc-1", "uc-1"))));

        assertEquals(1, exception.getIssues().size());
        assertEquals("uc-1", exception.getIssues().get(0).userCardId());
    }

    @Test
    @DisplayName("카드번호가 이미 저장돼 있으면 카드사가 요구하더라도 정상 활성화된다")
    void activatesWhenCardNumberAlreadyStored() {
        String linkId = "link-1";
        when(codefCredentialMapper.lockOwnedLink(linkId, USER_ID)).thenReturn(linkId);
        when(linkedCardMapper.findByLinkIdAndUserId(linkId, USER_ID)).thenReturn(
                List.of(new LinkedCardRow("uc-1", "card-1", true, true, true, true)));
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());

        ActivateCardLinkCardsResponse response = cardLinkService.activateCards(
                USER_ID, linkId, activateRequest(List.of("uc-1")));

        assertEquals(1, response.activatedCount());
        verify(linkedCardMapper).activateCards(linkId, USER_ID, List.of("uc-1"), 0);
    }

    @Test
    @DisplayName("카드번호/비밀번호 입력 후 CODEF 보유카드 조회가 성공하면 암호화 저장하고, "
            + "옵션 그룹을 포함한 카드 정보를 응답한다(활성화는 별도 요청으로 한다)")
    void submitsCardCredentialsAndReturnsCardWithOptions() {
        String userCardId = "uc-1";
        CardCredentialSubmissionTarget target = new CardCredentialSubmissionTarget(
                userCardId, "link-1", "cid-1", "0301", new byte[0], true, true,
                "card-1", "9999****6666", "KB국민카드");
        when(linkedCardMapper.findCardForCredentialSubmission(userCardId, USER_ID)).thenReturn(target);
        when(encryptor.decrypt(target.birthDateEnc())).thenReturn("900101");
        when(encryptor.encrypt("9999888877776666")).thenReturn(new byte[] {9});
        // KB카드(0301)는 카드소지확인에 카드 비밀번호 앞 2자리만 쓰므로 "5678" 입력은 "56"으로 잘려 CODEF에 전달·저장된다.
        when(encryptor.encrypt("56")).thenReturn(new byte[] {5});
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardById("card-1")).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of(
                new CardOptionRow("group-1", "main", "혜택 팩", "choice-1", "a", "A팩")));
        SubmitCardCredentialsRequest request = new SubmitCardCredentialsRequest();
        request.setCardNo("9999888877776666");
        request.setCardPassword("5678");

        CardLinkCardResponse response = cardLinkService.submitCardCredentials(USER_ID, userCardId, request);

        assertEquals(userCardId, response.userCardId());
        assertEquals("card-1", response.cardId());
        assertEquals("정식 카드명", response.cardName());
        assertEquals("CREDIT", response.cardType());
        assertTrue(response.matched());
        assertEquals(1, response.optionGroups().size());
        assertEquals(1, response.optionGroups().get(0).choices().size());
        verify(codefClient).getOwnedCards("cid-1", "0301", "900101", "9999888877776666", "56");
        verify(linkedCardMapper).updateCardCredentials(
                userCardId, USER_ID, new byte[] {9}, new byte[] {5});
        // 옵션 검증 없이 바로 활성화하지 않는다 — 활성화는 activateCards(옵션 선택 포함)로만 한다.
        verify(linkedCardMapper, never()).activateCards(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("KB카드가 아니면 카드 비밀번호를 자르지 않고 그대로 CODEF에 전달·저장한다")
    void doesNotTruncateCardPasswordForNonKbIssuer() {
        String userCardId = "uc-1";
        CardCredentialSubmissionTarget target = new CardCredentialSubmissionTarget(
                userCardId, "link-1", "cid-1", "0302", new byte[0], true, true,
                "card-1", "9999****6666", "현대카드");
        when(linkedCardMapper.findCardForCredentialSubmission(userCardId, USER_ID)).thenReturn(target);
        when(encryptor.decrypt(target.birthDateEnc())).thenReturn("900101");
        when(encryptor.encrypt("9999888877776666")).thenReturn(new byte[] {9});
        when(encryptor.encrypt("5678")).thenReturn(new byte[] {5});
        CardCatalogEntry matched = new CardCatalogEntry(
                "card-1", ISSUER_ID, "정식 카드명", "credit", "https://gorilla/card.png");
        when(cardCatalogMapper.findCardById("card-1")).thenReturn(matched);
        when(cardCatalogMapper.findVerifiedOptionsByCardId("card-1")).thenReturn(List.of());
        SubmitCardCredentialsRequest request = new SubmitCardCredentialsRequest();
        request.setCardNo("9999888877776666");
        request.setCardPassword("5678");

        cardLinkService.submitCardCredentials(USER_ID, userCardId, request);

        verify(codefClient).getOwnedCards("cid-1", "0302", "900101", "9999888877776666", "5678");
        verify(linkedCardMapper).updateCardCredentials(
                userCardId, USER_ID, new byte[] {9}, new byte[] {5});
    }

    @Test
    @DisplayName("카드 비밀번호가 필요한데 입력하지 않으면 카드정보 추가 입력을 거부한다")
    void rejectsCredentialSubmissionWhenCardPasswordMissing() {
        CardCredentialSubmissionTarget target = new CardCredentialSubmissionTarget(
                "uc-1", "link-1", "cid-1", "0301", new byte[0], true, true,
                "card-1", "9999****6666", "KB국민카드");
        when(linkedCardMapper.findCardForCredentialSubmission("uc-1", USER_ID)).thenReturn(target);
        SubmitCardCredentialsRequest request = new SubmitCardCredentialsRequest();
        request.setCardNo("9999888877776666");

        CardCredentialRequiredException exception = assertThrows(CardCredentialRequiredException.class,
                () -> cardLinkService.submitCardCredentials(USER_ID, "uc-1", request));

        assertEquals(1, exception.getIssues().size());
        assertEquals("uc-1", exception.getIssues().get(0).userCardId());
        assertEquals("카드 비밀번호는 필수입니다.", exception.getIssues().get(0).fields().get("cardPassword"));
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("본인 소유 카드가 아니면 카드정보 추가 입력을 거부한다")
    void rejectsCredentialSubmissionForUnknownCard() {
        when(linkedCardMapper.findCardForCredentialSubmission("uc-1", USER_ID)).thenReturn(null);

        assertThrows(UserCardNotFoundException.class, () -> cardLinkService.submitCardCredentials(
                USER_ID, "uc-1", new SubmitCardCredentialsRequest()));
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("카드번호가 필요하지 않은 카드사면 카드정보 추가 입력을 거부한다")
    void rejectsCredentialSubmissionWhenCardNoNotRequired() {
        CardCredentialSubmissionTarget target = new CardCredentialSubmissionTarget(
                "uc-1", "link-1", "cid-1", "0301", new byte[0], false, false,
                "card-1", "9999****6666", "KB국민카드");
        when(linkedCardMapper.findCardForCredentialSubmission("uc-1", USER_ID)).thenReturn(target);
        SubmitCardCredentialsRequest request = new SubmitCardCredentialsRequest();
        request.setCardNo("9999888877776666");

        assertThrows(InvalidCardSelectionException.class,
                () -> cardLinkService.submitCardCredentials(USER_ID, "uc-1", request));
        verifyNoInteractions(codefClient);
    }

    @Test
    @DisplayName("입력한 카드번호가 저장된 마스킹 카드번호와 다르면 CODEF를 호출하지 않고 바로 거부한다")
    void rejectsCredentialSubmissionWhenCardNumberDoesNotMatchMaskedCardNo() {
        CardCredentialSubmissionTarget target = new CardCredentialSubmissionTarget(
                "uc-1", "link-1", "cid-1", "0301", new byte[0], true, true,
                "card-1", "9999****6666", "KB국민카드");
        when(linkedCardMapper.findCardForCredentialSubmission("uc-1", USER_ID)).thenReturn(target);
        SubmitCardCredentialsRequest request = new SubmitCardCredentialsRequest();
        // 앞자리(9999)는 맞지만 뒷자리가 저장된 마스킹 카드번호(6666)와 다른, 다른 카드의 번호다.
        request.setCardNo("9999888877770000");
        request.setCardPassword("5678");

        assertThrows(CardNumberMismatchException.class,
                () -> cardLinkService.submitCardCredentials(USER_ID, "uc-1", request));
        verifyNoInteractions(codefClient);
        verify(linkedCardMapper, never()).updateCardCredentials(any(), any(), any(), any());
    }

    private LinkedCardRow linkedCardRow(String userCardId, String cardId) {
        return new LinkedCardRow(userCardId, cardId, false, false, false, false);
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
