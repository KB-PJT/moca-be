package com.moca.mocabe.domain.user.service;

import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

/** 사용자 조회·생성·삭제에 필요한 도메인 규칙과 영속성 접근을 담당한다. */
public class UserDomainService {

    private final UserMapper userMapper;

    public UserDomainService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserProfile requireUser(String userId) {
        UserProfile userProfile = userMapper.findProfileById(userId);
        if (userProfile == null) {
            throw new UserNotFoundException();
        }
        return userProfile;
    }

    /** Google subject 유니크 제약을 최종 기준으로 동시 최초 로그인을 안전하게 처리한다. */
    public GoogleUserResult findOrCreateGoogleUser(String googleSubject, String email, String nickname) {
        UserProfile existingUser = userMapper.findProfileByGoogleSubject(googleSubject);
        if (existingUser != null) {
            return new GoogleUserResult(existingUser, false);
        }

        String userId = UUID.randomUUID().toString();
        try {
            userMapper.insertGoogleUser(userId, googleSubject, email, nickname);
            return new GoogleUserResult(requireUser(userId), true);
        } catch (DuplicateKeyException exception) {
            UserProfile concurrentlyCreatedUser = userMapper.findProfileByGoogleSubject(googleSubject);
            if (concurrentlyCreatedUser == null) {
                throw exception;
            }
            return new GoogleUserResult(concurrentlyCreatedUser, false);
        }
    }

    public void updateNickname(String userId, String nickname) {
        requireUser(userId);
        userMapper.updateNickname(userId, nickname);
    }

    public void updateCardSortMode(String userId, String cardSortMode) {
        requireUser(userId);
        userMapper.updateCardSortMode(userId, cardSortMode);
    }

    public void updateLocationRecommendationEnabled(String userId, boolean enabled) {
        requireUser(userId);
        userMapper.updateLocationRecommendationEnabled(userId, enabled);
    }

    public void saveNotificationSettings(
            String userId, com.moca.mocabe.domain.user.model.NotificationSettings settings) {
        requireUser(userId);
        userMapper.upsertNotificationSettings(userId, settings);
    }

    public com.moca.mocabe.domain.user.model.NotificationSettings findNotificationSettings(String userId) {
        requireUser(userId);
        return userMapper.findNotificationSettingsByUserId(userId);
    }

    public com.moca.mocabe.domain.user.model.LocationSettings findLocationSettings(String userId) {
        requireUser(userId);
        return userMapper.findLocationSettingsByUserId(userId);
    }

    public boolean deleteUser(String userId) {
        requireUser(userId);
        userMapper.deleteNotificationSettings(userId);
        return userMapper.deleteUser(userId) == 1;
    }

    /** Google 사용자 생성 결과와 최초 가입 여부를 함께 전달한다. */
    public static final class GoogleUserResult {

        private final UserProfile userProfile;
        private final boolean newMember;

        public GoogleUserResult(UserProfile userProfile, boolean newMember) {
            this.userProfile = userProfile;
            this.newMember = newMember;
        }

        public UserProfile getUserProfile() {
            return userProfile;
        }

        public boolean isNewMember() {
            return newMember;
        }
    }
}
