package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.LinkedCardRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 연동으로 적재된 보유카드(user_cards)와 옵션 선택 영속성 접근을 담당한다. */
@Mapper
public interface LinkedCardMapper {

    int findNextDisplayOrder(@Param("userId") String userId);

    void insertLinkedCard(@Param("userCardId") String userCardId,
                          @Param("linkId") String linkId,
                          @Param("userId") String userId,
                          @Param("issuerId") String issuerId,
                          @Param("cardId") String cardId,
                          @Param("cardNameFromCodef") String cardNameFromCodef,
                          @Param("cardNo") String cardNo,
                          @Param("codefCardKeyHash") String codefCardKeyHash,
                          @Param("displayOrder") int displayOrder);

    List<LinkedCardRow> findByLinkIdAndUserId(@Param("linkId") String linkId,
                                              @Param("userId") String userId);

    int activateCards(@Param("linkId") String linkId,
                      @Param("userId") String userId,
                      @Param("userCardIds") List<String> userCardIds);

    void upsertOptionSelection(@Param("userCardId") String userCardId,
                               @Param("optionGroupId") String optionGroupId,
                               @Param("cardId") String cardId,
                               @Param("optionChoiceId") String optionChoiceId);
}
