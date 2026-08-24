package com.moca.mocabe.domain.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class HomeGreetingCacheTest {

  private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private HomeGreetingCache cache;

  @BeforeEach
  void setUp() {
    cache = new HomeGreetingCache(redisTemplate);
  }

  @Test
  @DisplayName("캐시에 값이 없으면 null을 반환한다")
  void returnsNullWhenCacheMiss() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("home:greeting:missed:" + USER_ID + ":2026-07")).thenReturn(null);

    assertNull(cache.get(USER_ID, "2026-07"));
  }

  @Test
  @DisplayName("캐시된 값을 그대로 복원한다")
  void roundTripsCachedValue() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("home:greeting:missed:" + USER_ID + ":2026-07")).thenReturn("8200");

    assertEquals(8_200L, cache.get(USER_ID, "2026-07"));
  }

  @Test
  @DisplayName("놓친 혜택 금액을 1시간 TTL로 저장한다")
  void putsValueWithOneHourTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    cache.put(USER_ID, "2026-07", 8_200L);

    verify(valueOperations)
        .set(eq("home:greeting:missed:" + USER_ID + ":2026-07"), eq("8200"), eq(Duration.ofHours(1)));
  }

  @Test
  @DisplayName("Redis 저장에 실패해도 예외 없이 넘어간다")
  void skipsWriteWhenRedisFails() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
        .when(valueOperations).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Duration.class));

    cache.put(USER_ID, "2026-07", 8_200L);
  }

  @Test
  @DisplayName("특정 월의 캐시만 비운다")
  void evictsSpecificMonth() {
    cache.evict(USER_ID, "2026-07");

    verify(redisTemplate).delete("home:greeting:missed:" + USER_ID + ":2026-07");
  }

  @Test
  @DisplayName("유저의 모든 월 캐시를 비운다")
  void evictsAllMonthsForUser() {
    java.util.Set<String> keys =
        java.util.Set.of(
            "home:greeting:missed:" + USER_ID + ":2026-07",
            "home:greeting:missed:" + USER_ID + ":2026-08");
    when(redisTemplate.keys("home:greeting:missed:" + USER_ID + ":*")).thenReturn(keys);

    cache.evictAll(USER_ID);

    verify(redisTemplate).delete(keys);
  }

  @Test
  @DisplayName("비울 캐시가 없으면 delete를 호출하지 않는다")
  void skipsDeleteWhenNoKeysMatch() {
    when(redisTemplate.keys("home:greeting:missed:" + USER_ID + ":*")).thenReturn(java.util.Set.of());

    cache.evictAll(USER_ID);

    verify(redisTemplate, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anySet());
  }
}
