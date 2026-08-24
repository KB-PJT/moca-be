package com.moca.mocabe.domain.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.home.model.HomeCardRow;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class HomeCardsCacheTest {

  private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private HomeCardsCache cache;

  @BeforeEach
  void setUp() {
    cache = new HomeCardsCache(redisTemplate, new ObjectMapper());
  }

  @Test
  @DisplayName("캐시에 값이 없으면 null을 반환한다")
  void returnsNullWhenCacheMiss() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("home:cards:" + USER_ID + ":2026-07")).thenReturn(null);

    assertNull(cache.get(USER_ID, "2026-07"));
  }

  @Test
  @DisplayName("저장된 카드 목록을 그대로 복원한다")
  void roundTripsCachedRows() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    HomeCardRow row = new HomeCardRow();
    row.setUserCardId("card-1");
    row.setCardName("신한 Mr.Life");
    when(valueOperations.get("home:cards:" + USER_ID + ":2026-07"))
        .thenReturn("[{\"userCardId\":\"card-1\",\"cardName\":\"신한 Mr.Life\"}]");

    List<HomeCardRow> rows = cache.get(USER_ID, "2026-07");

    assertEquals(1, rows.size());
    assertEquals("card-1", rows.get(0).getUserCardId());
  }

  @Test
  @DisplayName("역직렬화에 실패하면 캐시 미스로 처리한다")
  void treatsMalformedJsonAsCacheMiss() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("home:cards:" + USER_ID + ":2026-07")).thenReturn("not-json");

    assertNull(cache.get(USER_ID, "2026-07"));
  }

  @Test
  @DisplayName("카드 목록을 1시간 TTL로 저장한다")
  void putsRowsWithOneHourTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    HomeCardRow row = new HomeCardRow();
    row.setUserCardId("card-1");

    cache.put(USER_ID, "2026-07", List.of(row));

    verify(valueOperations)
        .set(eq("home:cards:" + USER_ID + ":2026-07"), anyString(), eq(Duration.ofHours(1)));
  }

  @Test
  @DisplayName("빈 목록도 캐시에 저장한다")
  void putsEmptyRows() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    cache.put(USER_ID, "2026-07", List.of());

    verify(valueOperations).set(anyString(), eq("[]"), any(Duration.class));
  }

  @Test
  @DisplayName("직렬화에 실패해도 예외 없이 캐시 저장을 건너뛴다")
  void skipsWriteWhenSerializationFails() {
    ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
    try {
      when(failingMapper.writeValueAsString(any()))
          .thenThrow(new JsonProcessingException("직렬화 실패") { });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    HomeCardsCache failingCache = new HomeCardsCache(redisTemplate, failingMapper);

    failingCache.put(USER_ID, "2026-07", List.of());

    org.mockito.Mockito.verifyNoInteractions(redisTemplate);
  }

  @Test
  @DisplayName("특정 월의 캐시만 비운다")
  void evictsSpecificMonth() {
    cache.evict(USER_ID, "2026-07");

    verify(redisTemplate).delete("home:cards:" + USER_ID + ":2026-07");
  }

  @Test
  @DisplayName("유저의 모든 월 캐시를 비운다")
  void evictsAllMonthsForUser() {
    java.util.Set<String> keys =
        java.util.Set.of("home:cards:" + USER_ID + ":2026-07", "home:cards:" + USER_ID + ":2026-08");
    when(redisTemplate.keys("home:cards:" + USER_ID + ":*")).thenReturn(keys);

    cache.evictAll(USER_ID);

    verify(redisTemplate).delete(keys);
  }

  @Test
  @DisplayName("비울 캐시가 없으면 delete를 호출하지 않는다")
  void skipsDeleteWhenNoKeysMatch() {
    when(redisTemplate.keys("home:cards:" + USER_ID + ":*")).thenReturn(java.util.Set.of());

    cache.evictAll(USER_ID);

    verify(redisTemplate, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anySet());
  }
}
