package com.moca.mocabe.domain.user.dto;

import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import javax.validation.constraints.NotNull;

public class BenefitPreferenceRequest {
    @NotNull
    private BenefitPreferenceType benefitPreferenceType;

    public BenefitPreferenceType getBenefitPreferenceType() {
        return benefitPreferenceType;
    }

    public void setBenefitPreferenceType(BenefitPreferenceType benefitPreferenceType) {
        this.benefitPreferenceType = benefitPreferenceType;
    }
}
