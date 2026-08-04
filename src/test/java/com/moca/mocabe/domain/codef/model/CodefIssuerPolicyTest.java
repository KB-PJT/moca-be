package com.moca.mocabe.domain.codef.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefIssuerPolicyTest {

    @Test
    @DisplayName("카드사 CODEF 정책 값을 보관한다")
    void storesPolicyValues() {
        CodefIssuerPolicy policy = new CodefIssuerPolicy();
        policy.setIssuerId("issuer-1");
        policy.setInstitutionCode("0301");
        policy.setRequiresId(true);
        policy.setRequiresPassword(true);
        policy.setRequiresCardNo(true);
        policy.setRequiresCardPassword(true);
        policy.setRequiresBirthDate(true);

        assertEquals("issuer-1", policy.getIssuerId());
        assertEquals("0301", policy.getInstitutionCode());
        assertTrue(policy.isRequiresId());
        assertTrue(policy.isRequiresPassword());
        assertTrue(policy.isRequiresCardNo());
        assertTrue(policy.isRequiresCardPassword());
        assertTrue(policy.isRequiresBirthDate());
    }
}
