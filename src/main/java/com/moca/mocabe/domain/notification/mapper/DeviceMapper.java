package com.moca.mocabe.domain.notification.mapper;

import com.moca.mocabe.domain.notification.model.UserDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceMapper {
    UserDevice findByToken(@Param("fcmToken") String fcmToken);
    int insert(@Param("deviceId") String deviceId, @Param("userId") String userId,
               @Param("fcmToken") String fcmToken, @Param("deviceType") String deviceType);
    int activate(@Param("deviceId") String deviceId, @Param("userId") String userId,
                 @Param("deviceType") String deviceType);
    int deactivate(@Param("deviceId") String deviceId, @Param("userId") String userId);
    List<UserDevice> findActiveByUserId(@Param("userId") String userId);
    List<UserDevice> findActiveNearbyBenefitDevices();
}
