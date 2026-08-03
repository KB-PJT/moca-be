package com.moca.mocabe.domain.codef.dto;

/** 카드 옵션 선택지다. */
public record CardOptionChoiceResponse(
        String optionChoiceId,
        String choiceKey,
        String choiceName
) {
}
