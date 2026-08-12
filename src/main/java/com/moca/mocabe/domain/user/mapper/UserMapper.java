package com.moca.mocabe.domain.user.mapper;

import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자 도메인의 영속성 접근만 담당한다. */
@Mapper
public interface UserMapper {

    UserProfile findProfileById(@Param("userId") String userId);

    UserProfile findProfileByGoogleSubject(@Param("googleSubject") String googleSubject);

    int insertGoogleUser(@Param("userId") String userId,
                         @Param("googleSubject") String googleSubject,
                         @Param("email") String email,
                         @Param("nickname") String nickname);

    int updateNickname(@Param("userId") String userId, @Param("nickname") String nickname);

    int updateCardSortMode(@Param("userId") String userId, @Param("cardSortMode") String cardSortMode);

    NotificationSettings findNotificationSettingsByUserId(@Param("userId") String userId);

    int upsertNotificationSettings(@Param("userId") String userId,
                                   @Param("settings") NotificationSettings settings);

    LocationSettings findLocationSettingsByUserId(@Param("userId") String userId);

    int updateLocationRecommendationEnabled(@Param("userId") String userId,
                                            @Param("enabled") boolean enabled);

    BenefitPreferenceType findBenefitPreferenceType(@Param("userId") String userId);

    int updateBenefitPreferenceType(@Param("userId") String userId,
                                    @Param("benefitPreferenceType") BenefitPreferenceType benefitPreferenceType);

    int deleteNotificationSettings(@Param("userId") String userId);

    int deleteUser(@Param("userId") String userId);
}
