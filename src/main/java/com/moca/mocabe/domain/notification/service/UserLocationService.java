package com.moca.mocabe.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.notification.dto.UpdateLocationRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class UserLocationService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    public UserLocationService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis; this.objectMapper = objectMapper;
    }
    public void update(String userId, UpdateLocationRequest request) {
        try {
            redis.opsForValue().set(key(userId), objectMapper.writeValueAsString(Map.of(
                    "latitude", request.getLatitude(), "longitude", request.getLongitude(),
                    "updatedAt", Instant.now().toString())), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("사용자 위치 저장에 실패했습니다.", exception);
        }
    }
    public Optional<Location> find(String userId) {
        String raw = redis.opsForValue().get(key(userId));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            Map<?, ?> value = objectMapper.readValue(raw, Map.class);
            return Optional.of(new Location(((Number) value.get("latitude")).doubleValue(),
                    ((Number) value.get("longitude")).doubleValue()));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
    public record Location(double latitude, double longitude) { }
    private String key(String userId) {
        return "user:location:" + userId;
    }
}
