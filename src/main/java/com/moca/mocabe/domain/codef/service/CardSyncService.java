package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.card.dto.SyncMyCardsResponse;
import com.moca.mocabe.domain.codef.exception.InvalidSyncPeriodException;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import com.moca.mocabe.domain.codef.model.CodefApproval;
import com.moca.mocabe.domain.codef.model.CodefConnection;
import com.moca.mocabe.domain.codef.model.ExistingApprovalKey;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import com.moca.mocabe.domain.merchant.service.MerchantCandidateSnapshot;
import com.moca.mocabe.domain.merchant.service.MerchantLookup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * POST /me/cards/sync에서 CODEF 승인내역을 조회해 새 건만 card_payment_approvals에 적재한다.
 *
 * 취소·부분취소·거절 및 해외결제는 제외하고, 정상 국내 승인건만 적재한다.
 * 카드 매칭은 {@link ApprovalCardMatcher}, 가맹점 매칭은 {@link MerchantLookup}에 위임하며,
 * 이미 적재된 건은 (카드+승인번호) 또는 (카드+시각+금액+가맹점명)으로 중복을 걸러낸다.
 */
public class CardSyncService {

    private static final Logger LOGGER = Logger.getLogger(CardSyncService.class.getName());
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter CODEF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CodefClient codefClient;
    private final CodefCredentialMapper codefCredentialMapper;
    private final CardApprovalMapper cardApprovalMapper;
    private final ApprovalCardMatcher approvalCardMatcher;
    private final MerchantLookup merchantLookup;
    private final ApprovalIngestStore approvalIngestStore;
    private final Encryptor encryptor;

    public CardSyncService(CodefClient codefClient, CodefCredentialMapper codefCredentialMapper,
                           CardApprovalMapper cardApprovalMapper, ApprovalCardMatcher approvalCardMatcher,
                           MerchantLookup merchantLookup, ApprovalIngestStore approvalIngestStore,
                           Encryptor encryptor) {
        this.codefClient = codefClient;
        this.codefCredentialMapper = codefCredentialMapper;
        this.cardApprovalMapper = cardApprovalMapper;
        this.approvalCardMatcher = approvalCardMatcher;
        this.merchantLookup = merchantLookup;
        this.approvalIngestStore = approvalIngestStore;
        this.encryptor = encryptor;
    }

    /** startDate/endDate는 KST 기준이며, null이면 이번 달 1일~오늘로 기본값을 채운다. */
    public SyncMyCardsResponse sync(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(KST);
        LocalDate from = startDate != null ? startDate : today.withDayOfMonth(1);
        LocalDate to = endDate != null ? endDate : today;
        if (from.isAfter(to)) {
            throw new InvalidSyncPeriodException("조회 시작일이 종료일보다 늦을 수 없습니다.");
        }

        List<UserCardMatchRow> userCards = cardApprovalMapper.findUserCardsForMatching(userId);
        List<CodefConnection> connections = codefCredentialMapper.findActiveConnectionsByUserId(userId);

        int insertedCount = 0;
        if (!userCards.isEmpty() && !connections.isEmpty()) {
            insertedCount = ingest(userId, userCards, connections, from, to);
        }
        return new SyncMyCardsResponse(userCards.size(), insertedCount, formatSyncedAt());
    }

    private int ingest(String userId, List<UserCardMatchRow> userCards, List<CodefConnection> connections,
                       LocalDate from, LocalDate to) {
        LocalDateTime fromUtc = from.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to.plusDays(1).atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        Set<String> seenKeys = new HashSet<>();
        for (ExistingApprovalKey key : cardApprovalMapper.findExistingApprovalKeys(userId, fromUtc, toUtc)) {
            seenKeys.add(dedupeKey(key.userCardId(), key.approvalNumber(),
                    key.approvedAt(), key.amount(), key.merchantName()));
        }

        String startStr = from.format(CODEF_DATE);
        String endStr = to.format(CODEF_DATE);
        // 카드 매칭 실패 원인을 진단할 수 있도록 매칭 후보(보유카드)의 이름·카드번호를 남긴다(FINE).
        for (UserCardMatchRow card : userCards) {
            LOGGER.fine("동기화 대상 보유카드: name='" + card.cardName() + "' cardNo='" + card.cardNo() + "'");
        }
        // 가맹점 후보를 승인건마다 다시 조회하면 비용이 승인건 수만큼 반복되므로 이 회차 시작 시 한 번만 읽는다.
        MerchantCandidateSnapshot merchantCandidates = merchantLookup.loadCandidates();
        IngestStats stats = new IngestStats();
        List<ApprovalInsert> inserts = new ArrayList<>();
        for (CodefConnection connection : connections) {
            String birthDate = encryptor.decrypt(connection.birthDateEnc());
            List<CodefApproval> approvals = codefClient.getApprovals(
                    connection.connectedId(), connection.institutionCode(), birthDate, startStr, endStr);
            for (CodefApproval approval : approvals) {
                ApprovalInsert insert = toInsert(userId, userCards, approval, connection.issuerId(),
                        merchantCandidates, seenKeys, stats);
                if (insert != null) {
                    inserts.add(insert);
                }
            }
        }
        int inserted = approvalIngestStore.insertAll(inserts);
        // 승인내역이 왜 적재되지 않는지 진단할 수 있도록 드랍 사유별 집계를 한 줄로 남긴다.
        LOGGER.info(String.format(
                "승인내역 동기화 결과 period=%s~%s fetched=%d filtered=%d unmatched=%d invalid=%d duplicate=%d inserted=%d",
                startStr, endStr, stats.fetched, stats.filtered, stats.unmatched, stats.invalid,
                stats.duplicate, inserted));
        return inserted;
    }

    private ApprovalInsert toInsert(String userId, List<UserCardMatchRow> userCards,
                                    CodefApproval approval, String issuerId,
                                    MerchantCandidateSnapshot merchantCandidates,
                                    Set<String> seenKeys, IngestStats stats) {
        stats.fetched++;
        // 취소/부분취소/거절·해외결제는 적재하지 않는다.
        if (!approval.isNormalApproval() || !approval.isDomestic()) {
            stats.filtered++;
            return null;
        }
        String userCardId = approvalCardMatcher.match(userCards, approval, issuerId);
        if (userCardId == null) {
            // 보유카드와 매칭되지 않는 승인건은 적재하지 않고 미매칭으로 남긴다.
            stats.unmatched++;
            LOGGER.fine("미매칭 승인: resCardName='" + approval.cardName()
                    + "' resCardNo='" + approval.cardNo() + "'");
            return null;
        }
        LocalDateTime approvedAt = toApprovedAtUtc(approval.usedDate(), approval.usedTime());
        Integer amount = parseAmount(approval.usedAmount());
        if (approvedAt == null || amount == null) {
            stats.invalid++;
            return null;
        }
        String dedupeKey = dedupeKey(
                userCardId, approval.approvalNo(), approvedAt, amount, approval.memberStoreName());
        if (!seenKeys.add(dedupeKey)) {
            stats.duplicate++;
            return null;
        }
        String merchantId = merchantCandidates.resolveMerchantId(approval.memberStoreName());
        return new ApprovalInsert(UUID.randomUUID().toString(), userId, userCardId, merchantId,
                approval.approvalNo(), approvedAt, approval.memberStoreName(), amount, approval.sourcePayload());
    }

    /** 승인내역 적재 과정에서 드랍된 사유를 집계해 진단 로그로 남기기 위한 카운터다. */
    private static final class IngestStats {
        private int fetched;
        private int filtered;
        private int unmatched;
        private int invalid;
        private int duplicate;
    }

    /** 승인번호가 있으면 (카드+승인번호), 없으면 (카드+시각+금액+가맹점명)으로 중복 판정 키를 만든다. */
    private String dedupeKey(String userCardId, String approvalNumber,
                             LocalDateTime approvedAt, int amount, String merchantName) {
        if (approvalNumber != null && !approvalNumber.isBlank()) {
            return userCardId + "A" + approvalNumber;
        }
        return userCardId + "B" + approvedAt + "" + amount + "" + merchantName;
    }

    private LocalDateTime toApprovedAtUtc(String usedDate, String usedTime) {
        if (usedDate == null || usedDate.length() != 8) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(usedDate, CODEF_DATE);
            LocalTime time = parseTime(usedTime);
            return date.atTime(time).atZone(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (RuntimeException exception) {
            LOGGER.fine("승인 시각 파싱에 실패해 건너뜁니다. usedDate=" + usedDate);
            return null;
        }
    }

    private LocalTime parseTime(String usedTime) {
        if (usedTime == null || usedTime.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        String padded = (usedTime + "000000").substring(0, 6);
        int hour = Integer.parseInt(padded.substring(0, 2));
        int minute = Integer.parseInt(padded.substring(2, 4));
        int second = Integer.parseInt(padded.substring(4, 6));
        return LocalTime.of(hour, minute, second);
    }

    private Integer parseAmount(String usedAmount) {
        if (usedAmount == null) {
            return null;
        }
        String digits = usedAmount.replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || "-".equals(digits)) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatSyncedAt() {
        return OffsetDateTime.now(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
