package com.moca.mocabe.domain.codef.dto;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/** 활성화할 카드 한 장(userCardId)과 그 카드의 옵션 선택이다. */
public class CardOptionSelectionRequest {

    @NotBlank(message = "userCardId는 필수입니다.")
    private String userCardId;

    // 옵션 없는 카드는 생략 가능(null 허용). 옵션 필수 여부는 서버가 카드별 옵션 그룹으로 검증한다.
    @Valid
    private List<OptionSelectionRequest> optionSelections;

    public String getUserCardId() {
        return userCardId;
    }

    public void setUserCardId(String userCardId) {
        this.userCardId = userCardId;
    }

    public List<OptionSelectionRequest> getOptionSelections() {
        return optionSelections;
    }

    public void setOptionSelections(List<OptionSelectionRequest> optionSelections) {
        this.optionSelections = optionSelections;
    }
}
