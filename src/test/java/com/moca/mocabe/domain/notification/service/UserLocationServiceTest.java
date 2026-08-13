package com.moca.mocabe.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.notification.dto.UpdateLocationRequest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("최근 사용자 위치 서비스")
class UserLocationServiceTest {
    @Test
    @DisplayName("위치를 JSON으로 30분간 저장하고 다시 조회한다")
    void storesAndFindsLocation() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("user:location:user")).thenReturn("{\"latitude\":35.1,\"longitude\":129.1}");
        UserLocationService service = new UserLocationService(redis, new ObjectMapper());
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(35.1);
        request.setLongitude(129.1);

        service.update("user", request);
        var location = service.find("user").orElseThrow();

        assertEquals(35.1, location.latitude());
        assertEquals(129.1, location.longitude());
        verify(values).set(eq("user:location:user"), any(), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("저장 위치가 없거나 손상됐으면 빈 결과를 반환한다")
    void returnsEmptyForMissingOrInvalidLocation() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UserLocationService service = new UserLocationService(redis, new ObjectMapper());
        assertTrue(service.find("user").isEmpty());
        when(values.get("user:location:user")).thenReturn("invalid");
        assertTrue(service.find("user").isEmpty());
    }

    @Test
    @DisplayName("위치 직렬화가 실패하면 저장 오류로 변환한다")
    void wrapsSerializationFailure() throws Exception {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("failure") { });
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(35.1);
        request.setLongitude(129.1);
        assertThrows(IllegalStateException.class,
                () -> new UserLocationService(redis, mapper).update("user", request));
    }
}
