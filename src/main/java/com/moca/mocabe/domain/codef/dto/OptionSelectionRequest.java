package com.moca.mocabe.domain.codef.dto;

import javax.validation.constraints.NotNull;

/** 한 옵션 그룹에서 사용자가 고른 선택지다. */
public class OptionSelectionRequest {

    @NotNull(message = "optionGroupId는 필수입니다.")
    private String optionGroupId;

    @NotNull(message = "optionChoiceId는 필수입니다.")
    private String optionChoiceId;

    public String getOptionGroupId() {
        return optionGroupId;
    }

    public void setOptionGroupId(String optionGroupId) {
        this.optionGroupId = optionGroupId;
    }

    public String getOptionChoiceId() {
        return optionChoiceId;
    }

    public void setOptionChoiceId(String optionChoiceId) {
        this.optionChoiceId = optionChoiceId;
    }
}
