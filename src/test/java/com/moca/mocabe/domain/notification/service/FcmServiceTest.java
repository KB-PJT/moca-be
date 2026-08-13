package com.moca.mocabe.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("FCM 발송 서비스")
class FcmServiceTest {
    @Test
    @DisplayName("notification과 data가 포함된 메시지를 발송한다")
    void sendsMessage() throws Exception {
        FirebaseMessaging messaging = org.mockito.Mockito.mock(FirebaseMessaging.class);
        when(messaging.send(any())).thenReturn("message-id");
        ObjectProvider<FirebaseMessaging> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(messaging);
        assertEquals("message-id", new FcmService(provider).send(
                "token", "title", "body", Map.of("type", "TEST")));
    }

    @Test
    @DisplayName("등록 해제 오류만 비활성화 대상으로 판정한다")
    void identifiesInvalidTokens() {
        FcmService service = new FcmService(org.mockito.Mockito.mock(ObjectProvider.class));
        FirebaseMessagingException unregistered = exception(MessagingErrorCode.UNREGISTERED);
        FirebaseMessagingException invalid = exception(MessagingErrorCode.INVALID_ARGUMENT);
        FirebaseMessagingException unavailable = exception(MessagingErrorCode.UNAVAILABLE);
        assertTrue(service.isInvalidToken(unregistered));
        assertFalse(service.isInvalidToken(invalid));
        assertFalse(service.isInvalidToken(unavailable));
    }

    private FirebaseMessagingException exception(MessagingErrorCode code) {
        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        return exception;
    }
}
