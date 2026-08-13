package com.moca.mocabe.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.moca.mocabe.domain.notification.service.FcmService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class FirebaseConfig {
    @Bean
    @Lazy
    public FirebaseApp firebaseApp() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        return FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault()).build());
    }

    @Bean
    @Lazy
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    @Bean
    @Lazy
    public FcmService fcmService(ObjectProvider<FirebaseMessaging> messagingProvider) {
        return new FcmService(messagingProvider);
    }
}
