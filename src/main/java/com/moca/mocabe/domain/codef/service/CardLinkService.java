package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsRequest;
import com.moca.mocabe.domain.codef.dto.ActivateCardLinkCardsResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkCardResponse;
import com.moca.mocabe.domain.codef.dto.CardLinkResponse;
import com.moca.mocabe.domain.codef.dto.CardOptionChoiceResponse;
import com.moca.mocabe.domain.codef.dto.CardOptionGroupResponse;
import com.moca.mocabe.domain.codef.dto.CardOptionSelectionRequest;
import com.moca.mocabe.domain.codef.dto.CreateCardLinkRequest;
import com.moca.mocabe.domain.codef.dto.OptionSelectionRequest;
import com.moca.mocabe.domain.codef.exception.CardLinkNotFoundException;
import com.moca.mocabe.domain.codef.exception.CodefAccountAlreadyLinkedException;
import com.moca.mocabe.domain.codef.exception.CodefCredentialRequiredException;
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
import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import com.moca.mocabe.domain.codef.model.CodefIssuerPolicy;
import com.moca.mocabe.domain.codef.model.CodefOwnedCard;
import com.moca.mocabe.domain.codef.model.LinkedCardInsert;
import com.moca.mocabe.domain.codef.model.LinkedCardRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/** Connected ID 생성·보유카드 적재(비활성)와 사용자 활성화·옵션 선택 유스케이스를 담당한다. */
public class CardLinkService {

    private static final String STATUS_PENDING = "PENDING_CARD_ACTIVATION";
    private static final String HASH_TYPE_CARD_NO = "CARD_NO";
    private static final String HASH_TYPE_ACCOUNT_ID = "ACCOUNT_ID";
    private static final String HASH_TYPE_CODEF_CARD = "CODEF_CARD";

    private final CodefClient codefClient;
    private final CodefCredentialMapper codefCredentialMapper;
    private final CodefCredentialStore codefCredentialStore;
    private final IssuerMapper issuerMapper;
    private final Encryptor encryptor;
    private final CredentialHasher credentialHasher;
    private final CardCatalogMatcher cardCatalogMatcher;
    private final CardCatalogMapper cardCatalogMapper;
    private final LinkedCardMapper linkedCardMapper;

    public CardLinkService(CodefClient codefClient, CodefCredentialMapper codefCredentialMapper,
                           CodefCredentialStore codefCredentialStore, IssuerMapper issuerMapper,
                           Encryptor encryptor, CredentialHasher credentialHasher,
                           CardCatalogMatcher cardCatalogMatcher, CardCatalogMapper cardCatalogMapper,
                           LinkedCardMapper linkedCardMapper) {
        this.codefClient = codefClient;
        this.codefCredentialMapper = codefCredentialMapper;
        this.codefCredentialStore = codefCredentialStore;
        this.issuerMapper = issuerMapper;
        this.encryptor = encryptor;
        this.credentialHasher = credentialHasher;
        this.cardCatalogMatcher = cardCatalogMatcher;
        this.cardCatalogMapper = cardCatalogMapper;
        this.linkedCardMapper = linkedCardMapper;
    }

    public CardLinkResponse createLink(String userId, CreateCardLinkRequest request) {
        // 카드사 정책 조회 → 필수값 검증 → 중복 연동 확인 → CODEF 연동 → 매칭 카드 적재(비활성) 순서로 진행한다.
        // API는 기관코드(institution_code)로 주고받고, 내부 저장·매칭은 정책에서 얻은 issuer_id를 쓴다.
        CodefIssuerPolicy policy =
                issuerMapper.findCodefPolicyByInstitutionCode(request.getInstitutionCode());
        if (policy == null) {
            throw new IssuerNotFoundException(request.getInstitutionCode());
        }
        validateRequiredCredentials(policy, request);
        // 카드번호(또는 로그인 ID)로 만든 식별 해시로 같은 계정의 중복 연동을 막는다.
        String credentialIdentityHash = createCredentialIdentityHash(policy, request);
        if (codefCredentialMapper.existsByUserIdAndIssuerIdAndIdentityHash(
                userId, policy.getIssuerId(), credentialIdentityHash)) {
            throw new CodefAccountAlreadyLinkedException();
        }

        CodefConnectionCommand command = new CodefConnectionCommand(
                policy.getInstitutionCode(), request.getId(), request.getPassword(),
                request.getCardNo(), request.getCardPassword(), request.getBirthDate());
        String connectedId = codefClient.createConnectedId(command);
        List<CodefOwnedCard> ownedCards = codefClient.getOwnedCards(connectedId, policy.getInstitutionCode());

        String linkId = UUID.randomUUID().toString();
        List<LinkedCardInsert> inserts = new ArrayList<>();
        List<CardLinkCardResponse> cards = new ArrayList<>();
        Set<String> cardKeyHashes = new HashSet<>();
        int displayOrder = linkedCardMapper.findNextDisplayOrder(userId);
        // issuerId는 루프 내내 동일하므로 카탈로그는 한 번만 조회해 재사용한다(카드 수만큼 반복 조회하지 않도록).
        List<CardCatalogEntry> catalog = cardCatalogMapper.findCardsByIssuerId(policy.getIssuerId());
        for (CodefOwnedCard ownedCard : ownedCards) {
            // 매칭 실패(matched == null)여도 응답엔 회색 카드로 노출하되, card_id NOT NULL이라 적재는 하지 않는다.
            CardCatalogEntry matched = cardCatalogMatcher.match(catalog, ownedCard.cardName());
            // 카드번호를 그대로 저장하지 않고 해시로 카드를 식별한다. 같은 카드가 중복 응답되면 한 건만 남긴다.
            String keySource = policy.getIssuerId() + ":" + ownedCard.cardNumber() + ":" + ownedCard.cardName();
            String cardKeyHash = credentialHasher.generate(HASH_TYPE_CODEF_CARD, keySource);
            if (!cardKeyHashes.add(cardKeyHash)) {
                continue;
            }
            String userCardId = null;
            if (matched != null) {
                userCardId = UUID.randomUUID().toString();
                inserts.add(new LinkedCardInsert(userCardId, linkId, userId, policy.getIssuerId(),
                        matched.cardId(), ownedCard.cardName(), blankToNull(ownedCard.cardNumber()),
                        cardKeyHash, displayOrder++));
            }
            cards.add(toResponse(userCardId, matched, ownedCard, policy));
        }

        codefCredentialStore.save(buildCredential(
                userId, request, policy.getIssuerId(), connectedId, linkId, credentialIdentityHash), inserts);
        return new CardLinkResponse(linkId, policy.getInstitutionCode(), STATUS_PENDING, cards);
    }

    @Transactional
    public ActivateCardLinkCardsResponse activateCards(String userId, String linkId,
                                                       ActivateCardLinkCardsRequest request) {
        // 본인 소유 연동만 잠근다. 신규 적재/원본 데이터 로딩은 하지 않고 활성 플래그·옵션만 수정한다.
        if (codefCredentialMapper.lockOwnedLink(linkId, userId) == null) {
            throw new CardLinkNotFoundException();
        }
        Map<String, String> cardIdByUserCard = linkedCardMapper.findByLinkIdAndUserId(linkId, userId).stream()
                .collect(Collectors.toMap(LinkedCardRow::userCardId, LinkedCardRow::cardId));

        // 활성화 대상은 반드시 이 연동에 적재된 카드여야 한다.
        Set<String> activeIds = new LinkedHashSet<>();
        for (String userCardId : request.getActiveUserCardIds()) {
            if (!cardIdByUserCard.containsKey(userCardId)) {
                throw new InvalidCardSelectionException("현재 연동에 속하지 않은 카드입니다.");
            }
            activeIds.add(userCardId);
        }

        // 옵션은 활성화하는 카드에 대해서만, 카드당 한 번만 받는다.
        Map<String, List<OptionSelectionRequest>> selectionsByCard = new HashMap<>();
        for (CardOptionSelectionRequest selection : optionSelections(request)) {
            if (!activeIds.contains(selection.getUserCardId())) {
                throw new InvalidCardSelectionException("활성화하지 않는 카드의 옵션은 보낼 수 없습니다.");
            }
            List<OptionSelectionRequest> options = selection.getOptionSelections() == null
                    ? List.of() : selection.getOptionSelections();
            if (selectionsByCard.put(selection.getUserCardId(), options) != null) {
                throw new InvalidCardSelectionException("같은 카드의 옵션을 중복 전송했습니다.");
            }
        }

        // 선택형 카드는 검증 완료(verified) 옵션을 그룹마다 하나씩 골랐는지 확인한다.
        for (String userCardId : activeIds) {
            List<CardOptionRow> options =
                    cardCatalogMapper.findVerifiedOptionsByCardId(cardIdByUserCard.get(userCardId));
            validateOptions(selectionsByCard.getOrDefault(userCardId, List.of()), options);
        }

        linkedCardMapper.activateCards(linkId, userId, new ArrayList<>(activeIds));
        for (String userCardId : activeIds) {
            String cardId = cardIdByUserCard.get(userCardId);
            for (OptionSelectionRequest selection : selectionsByCard.getOrDefault(userCardId, List.of())) {
                linkedCardMapper.upsertOptionSelection(
                        userCardId, selection.getOptionGroupId(), cardId, selection.getOptionChoiceId());
            }
        }
        return new ActivateCardLinkCardsResponse(linkId, new ArrayList<>(activeIds), activeIds.size());
    }

    private List<CardOptionSelectionRequest> optionSelections(ActivateCardLinkCardsRequest request) {
        return request.getOptionSelections() == null ? List.of() : request.getOptionSelections();
    }

    private CardLinkCardResponse toResponse(String userCardId, CardCatalogEntry matched,
                                            CodefOwnedCard ownedCard, CodefIssuerPolicy policy) {
        boolean matchedCard = matched != null;
        String cardName = matchedCard ? matched.cardName() : ownedCard.cardName();
        // 이미지: 카탈로그(카드고릴라) → CODEF resImageLink 순. 둘 다 없으면 null을 주고 기본 이미지는 프론트가 처리한다.
        String imageUrl = blankToNull(matchedCard ? matched.imageUrl() : ownedCard.imageUrl());
        List<CardOptionGroupResponse> options = matchedCard
                ? groupOptions(cardCatalogMapper.findVerifiedOptionsByCardId(matched.cardId())) : List.of();
        // 매칭 성공 카드는 카탈로그의 card_type가 정확하므로 그것을 쓴다. CODEF resCardType는 비어 오기도 한다.
        String cardType = normalizeCardType(matchedCard ? matched.cardType() : ownedCard.cardType());
        return new CardLinkCardResponse(
                userCardId, matchedCard ? matched.cardId() : null, cardName,
                blankToNull(ownedCard.cardNumber()), policy.getInstitutionCode(),
                policy.getIssuerName(), cardType, imageUrl, matchedCard, matchedCard, options);
    }

    private List<CardOptionGroupResponse> groupOptions(List<CardOptionRow> rows) {
        Map<String, OptionGroupBuilder> groups = new LinkedHashMap<>();
        for (CardOptionRow row : rows) {
            groups.computeIfAbsent(row.optionGroupId(), ignored ->
                    new OptionGroupBuilder(row.optionGroupId(), row.groupKey(), row.groupName()))
                    .choices.add(new CardOptionChoiceResponse(
                            row.optionChoiceId(), row.choiceKey(), row.choiceName()));
        }
        return groups.values().stream().map(OptionGroupBuilder::build).toList();
    }

    private void validateOptions(List<OptionSelectionRequest> selections, List<CardOptionRow> rows) {
        Map<String, Set<String>> allowed = new HashMap<>();
        for (CardOptionRow row : rows) {
            allowed.computeIfAbsent(row.optionGroupId(), ignored -> new HashSet<>()).add(row.optionChoiceId());
        }
        if (selections.size() != allowed.size()) {
            throw new InvalidCardSelectionException("모든 카드 옵션 그룹에서 하나씩 선택해야 합니다.");
        }
        Set<String> selectedGroups = new HashSet<>();
        for (OptionSelectionRequest selection : selections) {
            Set<String> choices = allowed.get(selection.getOptionGroupId());
            if (!selectedGroups.add(selection.getOptionGroupId())
                    || choices == null || !choices.contains(selection.getOptionChoiceId())) {
                throw new InvalidCardSelectionException("카드 옵션 선택이 올바르지 않습니다.");
            }
        }
    }

    private CodefAccountCredential buildCredential(String userId, CreateCardLinkRequest request,
                                                    String issuerId, String connectedId, String credentialId,
                                                    String credentialIdentityHash) {
        CodefAccountCredential credential = new CodefAccountCredential();
        credential.setCodefAccountCredentialId(credentialId);
        credential.setUserId(userId);
        credential.setIssuerId(issuerId);
        credential.setConnectedId(connectedId);
        credential.setAccountIdEnc(encryptor.encrypt(request.getId()));
        credential.setAccountPasswordEnc(encryptor.encrypt(request.getPassword()));
        credential.setCardNumberEnc(encryptor.encrypt(request.getCardNo()));
        credential.setCardPasswordEnc(encryptor.encrypt(request.getCardPassword()));
        credential.setBirthDateEnc(encryptor.encrypt(request.getBirthDate()));
        credential.setCredentialIdentityHash(credentialIdentityHash);
        credential.setStatus("active");
        return credential;
    }

    private void validateRequiredCredentials(CodefIssuerPolicy policy, CreateCardLinkRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        require(fields, "id", request.getId(), policy.isRequiresId(), "아이디는 필수입니다.");
        require(fields, "password", request.getPassword(), policy.isRequiresPassword(), "패스워드는 필수입니다.");
        require(fields, "cardNo", request.getCardNo(), policy.isRequiresCardNo(), "카드번호는 필수입니다.");
        require(fields, "cardPassword", request.getCardPassword(), policy.isRequiresCardPassword(),
                "카드 비밀번호는 필수입니다.");
        require(fields, "birthDate", request.getBirthDate(), policy.isRequiresBirthDate(), "생년월일은 필수입니다.");
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

    private String createCredentialIdentityHash(CodefIssuerPolicy policy, CreateCardLinkRequest request) {
        if (policy.isRequiresCardNo()) {
            String normalizedCardNo = request.getCardNo() == null
                    ? "" : request.getCardNo().replaceAll("[^0-9]", "");
            if (normalizedCardNo.isBlank()) {
                throw invalidIdentitySource("cardNo", "유효한 카드번호가 필요합니다.");
            }
            return credentialHasher.generate(HASH_TYPE_CARD_NO, normalizedCardNo);
        }
        String normalizedAccountId = request.getId() == null
                ? "" : request.getId().trim().toLowerCase(Locale.ROOT);
        if (normalizedAccountId.isBlank()) {
            throw invalidIdentitySource("id", "중복 연동 확인을 위한 아이디가 필요합니다.");
        }
        return credentialHasher.generate(HASH_TYPE_ACCOUNT_ID, normalizedAccountId);
    }

    private CodefCredentialRequiredException invalidIdentitySource(String field, String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, message);
        return new CodefCredentialRequiredException(fields);
    }

    private String normalizeCardType(String rawType) {
        // CODEF는 "체크/본인"·"신용" 등 한글로, 카탈로그는 "check"·"credit"로 저장한다. 둘 다 흡수한다.
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        if (type.contains("체크") || type.contains("check")) {
            return "CHECK";
        }
        if (type.contains("신용") || type.contains("credit")) {
            return "CREDIT";
        }
        return "UNKNOWN";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class OptionGroupBuilder {
        private final String id;
        private final String key;
        private final String name;
        private final List<CardOptionChoiceResponse> choices = new ArrayList<>();

        private OptionGroupBuilder(String id, String key, String name) {
            this.id = id;
            this.key = key;
            this.name = name;
        }

        private CardOptionGroupResponse build() {
            return new CardOptionGroupResponse(id, key, name, choices);
        }
    }
}
