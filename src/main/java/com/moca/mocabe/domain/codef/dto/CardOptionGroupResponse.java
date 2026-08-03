package com.moca.mocabe.domain.codef.dto;

import java.util.List;

/** 검증 완료되어 사용자가 반드시 하나를 골라야 하는 카드 옵션 그룹이다. */
public record CardOptionGroupResponse(
        String optionGroupId,
        String groupKey,
        String groupName,
        List<CardOptionChoiceResponse> choices
) {
}
