package com.moca.mocabe.domain.notification.mapper;

import com.moca.mocabe.domain.notification.model.PerformanceDeadlineCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NotificationMapper {
    List<PerformanceDeadlineCandidate> findPerformanceDeadlineCandidates(
            @Param("performanceMonth") String performanceMonth);
    boolean existsSent(@Param("userId") String userId, @Param("deviceId") String deviceId,
                       @Param("type") String type,
                       @Param("referenceId") String referenceId, @Param("date") String date,
                       @Param("timeSlot") String timeSlot);
    int claimPending(@Param("historyId") String historyId, @Param("deliveryKey") String deliveryKey,
                     @Param("userId") String userId,
                      @Param("deviceId") String deviceId, @Param("type") String type,
                      @Param("referenceId") String referenceId, @Param("timeSlot") String timeSlot,
                      @Param("date") String date, @Param("title") String title, @Param("body") String body,
                      @Param("status") String status);
    int updateHistory(@Param("historyId") String historyId, @Param("status") String status,
                      @Param("messageId") String messageId, @Param("errorMessage") String errorMessage);
    int deleteHistoryByUserId(@Param("userId") String userId);
}
