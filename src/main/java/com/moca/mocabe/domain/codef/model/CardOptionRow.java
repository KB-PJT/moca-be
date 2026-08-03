package com.moca.mocabe.domain.codef.model;

/** 검증 완료된 카드 옵션 그룹과 선택지를 한 행으로 조회하는 모델이다. */
public record CardOptionRow(
        String optionGroupId,
        String groupKey,
        String groupName,
        String optionChoiceId,
        String choiceKey,
        String choiceName
) {
}
