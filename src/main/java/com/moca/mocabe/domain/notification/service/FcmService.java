package com.moca.mocabe.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.Map;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.beans.factory.ObjectProvider;

public class FcmService {
    private final ObjectProvider<FirebaseMessaging> messagingProvider;
    public FcmService(ObjectProvider<FirebaseMessaging> messagingProvider) {
        this.messagingProvider = messagingProvider;
    }
    public String send(String token, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException {
        Message message = Message.builder().setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data).build();
        return messagingProvider.getObject().send(message);
    }

    /** 등록 해제·유효하지 않은 토큰에만 재사용 중지를 적용한다. */
    public boolean isInvalidToken(FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
