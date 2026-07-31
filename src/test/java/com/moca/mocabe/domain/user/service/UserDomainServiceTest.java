package com.moca.mocabe.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private static final String GOOGLE_SUBJECT = "google-subject";

    @Mock
    private UserMapper userMapper;

    private UserDomainService userDomainService;

    @BeforeEach
    void setUp() {
        userDomainService = new UserDomainService(userMapper);
    }

    @Test
    @DisplayName("기존 Google 사용자는 새 계정을 만들지 않고 반환한다")
    void returnsExistingGoogleUser() {
        UserProfile user = user();
        when(userMapper.findProfileByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(user);

        UserDomainService.GoogleUserResult result = userDomainService.findOrCreateGoogleUser(
                GOOGLE_SUBJECT, "moca@example.com", "모카");

        assertFalse(result.isNewMember());
        assertEquals(USER_ID, result.getUserProfile().getUserId());
    }

    @Test
    @DisplayName("최초 Google 사용자는 UUID로 생성한 뒤 조회한다")
    void createsGoogleUser() {
        UserProfile user = user();
        when(userMapper.findProfileByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(null);
        when(userMapper.findProfileById(anyString())).thenReturn(user);

        UserDomainService.GoogleUserResult result = userDomainService.findOrCreateGoogleUser(
                GOOGLE_SUBJECT, "moca@example.com", "모카");

        assertTrue(result.isNewMember());
        verify(userMapper).insertGoogleUser(anyString(), eq(GOOGLE_SUBJECT), eq("moca@example.com"), eq("모카"));
    }

    @Test
    @DisplayName("동시 최초 로그인 중복 키는 이미 생성된 Google 사용자를 다시 조회한다")
    void rereadsGoogleUserAfterDuplicateKey() {
        UserProfile user = user();
        when(userMapper.findProfileByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(null, user);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(userMapper).insertGoogleUser(anyString(), anyString(), anyString(), anyString());

        UserDomainService.GoogleUserResult result = userDomainService.findOrCreateGoogleUser(
                GOOGLE_SUBJECT, "moca@example.com", "모카");

        assertFalse(result.isNewMember());
        assertEquals(USER_ID, result.getUserProfile().getUserId());
    }

    @Test
    @DisplayName("중복 키 이후에도 사용자를 찾지 못하면 저장소 오류를 그대로 전달한다")
    void propagatesDuplicateKeyWhenUserCannotBeReread() {
        when(userMapper.findProfileByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(null, (UserProfile) null);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(userMapper).insertGoogleUser(anyString(), anyString(), anyString(), anyString());

        assertThrows(DuplicateKeyException.class, () -> userDomainService.findOrCreateGoogleUser(
                GOOGLE_SUBJECT, "moca@example.com", "모카"));
    }

    @Test
    @DisplayName("사용자 조회·설정 저장·삭제를 Mapper로 위임한다")
    void delegatesUserOperations() {
        UserProfile user = user();
        NotificationSettings notificationSettings = new NotificationSettings();
        LocationSettings locationSettings = new LocationSettings();
        when(userMapper.findProfileById(USER_ID)).thenReturn(user);
        when(userMapper.findNotificationSettingsByUserId(USER_ID)).thenReturn(notificationSettings);
        when(userMapper.findLocationSettingsByUserId(USER_ID)).thenReturn(locationSettings);
        when(userMapper.deleteUser(USER_ID)).thenReturn(1);

        assertEquals(user, userDomainService.requireUser(USER_ID));
        userDomainService.updateNickname(USER_ID, "새 모카");
        userDomainService.updateCardSortMode(USER_ID, "MANUAL");
        userDomainService.updateLocationRecommendationEnabled(USER_ID, true);
        userDomainService.saveNotificationSettings(USER_ID, notificationSettings);
        assertEquals(notificationSettings, userDomainService.findNotificationSettings(USER_ID));
        assertEquals(locationSettings, userDomainService.findLocationSettings(USER_ID));
        assertTrue(userDomainService.deleteUser(USER_ID));

        verify(userMapper).updateNickname(USER_ID, "새 모카");
        verify(userMapper).updateCardSortMode(USER_ID, "MANUAL");
        verify(userMapper).updateLocationRecommendationEnabled(USER_ID, true);
        verify(userMapper).upsertNotificationSettings(USER_ID, notificationSettings);
        verify(userMapper).deleteNotificationSettings(USER_ID);
    }

    @Test
    @DisplayName("없는 사용자는 공통 사용자 없음 예외로 처리하고 삭제 경합도 롤백시킨다")
    void rejectsMissingUserAndDeleteFailure() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(null);
        assertThrows(UserNotFoundException.class, () -> userDomainService.requireUser(USER_ID));

        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        when(userMapper.deleteUser(USER_ID)).thenReturn(0);
        assertThrows(UserNotFoundException.class, () -> userDomainService.deleteUser(USER_ID));
    }

    private UserProfile user() {
        UserProfile user = new UserProfile();
        user.setUserId(USER_ID);
        user.setNickname("모카");
        user.setEmail("moca@example.com");
        user.setUserType("user");
        return user;
    }
}
