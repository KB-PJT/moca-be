package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CardCredentialSubmissionTarget;
import com.moca.mocabe.domain.codef.model.LinkedCardKeyRow;
import com.moca.mocabe.domain.codef.model.LinkedCardRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 연동으로 적재된 보유카드(user_cards)와 옵션 선택 영속성 접근을 담당한다. */
@Mapper
public interface LinkedCardMapper {

    int findNextDisplayOrder(@Param("userId") String userId);

    /** 보유카드 재조회 시 이미 적재된 카드를 재판별해 중복 INSERT를 피하는 데 쓰인다. */
    List<LinkedCardKeyRow> findLinkedCardKeysByLinkId(@Param("linkId") String linkId,
                                                       @Param("userId") String userId);

    /**
     * (user_id, codef_card_key_hash) UNIQUE 충돌 시, 동시 재조회로 다른 요청이 먼저 적재한
     * 카드의 user_card_id를 찾는 데 쓰인다.
     */
    String findUserCardIdByUserIdAndCardKeyHash(@Param("userId") String userId,
                                                 @Param("codefCardKeyHash") String codefCardKeyHash);

    void insertLinkedCard(@Param("userCardId") String userCardId,
                          @Param("linkId") String linkId,
                          @Param("userId") String userId,
                          @Param("issuerId") String issuerId,
                          @Param("cardId") String cardId,
                          @Param("cardNameFromCodef") String cardNameFromCodef,
                          @Param("cardNo") String cardNo,
                          @Param("codefCardKeyHash") String codefCardKeyHash,
                          @Param("displayOrder") int displayOrder,
                          @Param("cardNumberEnc") byte[] cardNumberEnc,
                          @Param("cardPasswordEnc") byte[] cardPasswordEnc,
                          @Param("isActive") boolean isActive);

    List<LinkedCardRow> findByLinkIdAndUserId(@Param("linkId") String linkId,
                                              @Param("userId") String userId);

    int activateCards(@Param("linkId") String linkId,
                      @Param("userId") String userId,
                      @Param("userCardIds") List<String> userCardIds);

    void upsertOptionSelection(@Param("userCardId") String userCardId,
                               @Param("optionGroupId") String optionGroupId,
                               @Param("cardId") String cardId,
                               @Param("optionChoiceId") String optionChoiceId);

    /** 카드정보 추가 입력(PATCH /card-links/cards/{userCardId}/credentials) 전, 대상 카드·연동·정책을 조회한다. */
    CardCredentialSubmissionTarget findCardForCredentialSubmission(@Param("userCardId") String userCardId,
                                                                    @Param("userId") String userId);

    int updateCardCredentials(@Param("userCardId") String userCardId,
                              @Param("userId") String userId,
                              @Param("cardNumberEnc") byte[] cardNumberEnc,
                              @Param("cardPasswordEnc") byte[] cardPasswordEnc);
}
