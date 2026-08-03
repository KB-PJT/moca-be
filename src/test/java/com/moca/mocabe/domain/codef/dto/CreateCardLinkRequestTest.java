package com.moca.mocabe.domain.codef.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateCardLinkRequestTest {

    @Test
    @DisplayName("문자열 표현에서 카드사 자격정보를 마스킹한다")
    void masksSensitiveCredentialsInToString() {
        CreateCardLinkRequest request = new CreateCardLinkRequest();
        request.setInstitutionCode("0301");
        request.setId("login-id");
        request.setPassword("password");
        request.setCardNo("1234567890123456");
        request.setCardPassword("12");
        request.setBirthDate("19900101");

        String result = request.toString();

        assertEquals("CreateCardLinkRequest{institutionCode=0301, id=[MASKED], "
                + "password=[MASKED], cardNo=[MASKED], cardPassword=[MASKED], "
                + "birthDate=[MASKED]}", result);
        assertFalse(result.contains("login-id"));
        assertFalse(result.contains("1234567890123456"));
    }
}
