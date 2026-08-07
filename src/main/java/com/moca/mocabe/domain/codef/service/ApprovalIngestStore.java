package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.model.ApprovalInsert;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** 신규 승인내역 적재의 트랜잭션 경계를 담당한다. */
public class ApprovalIngestStore {

    private final CardApprovalMapper cardApprovalMapper;

    public ApprovalIngestStore(CardApprovalMapper cardApprovalMapper) {
        this.cardApprovalMapper = cardApprovalMapper;
    }

    /** 중복이 제거된 신규 승인내역만 적재하고 실제 적재 건수를 반환한다. */
    @Transactional
    public int insertAll(List<ApprovalInsert> approvals) {
        return insertAllReturningInserted(approvals).size();
    }

    /** 실제 DB에 새로 저장된 승인만 반환해 재동기화 시 혜택을 중복 계산하지 않게 한다. */
    @Transactional
    public List<ApprovalInsert> insertAllReturningInserted(List<ApprovalInsert> approvals) {
        java.util.ArrayList<ApprovalInsert> inserted = new java.util.ArrayList<>();
        for (ApprovalInsert approval : approvals) {
            try {
                cardApprovalMapper.insertApproval(approval.approvalId(), approval.userId(),
                        approval.userCardId(), approval.merchantId(), approval.approvalNumber(),
                        approval.approvedAt(), approval.merchantName(), approval.amount(),
                        approval.sourcePayload());
                inserted.add(approval);
            } catch (DuplicateKeyException exception) {
                // (user_card_id, approval_number) UNIQUE 위반 = 동시 요청 등으로 이미 적재됨. 건너뛴다.
            }
        }
        return inserted;
    }
}
