package com.moca.mocabe.domain.home.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.home.model.HomeCardRow;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 홈 카드 조회 결과를 Redis에 짧게 캐싱해 반복 조회의 DB 부하를 줄인다. */
public class HomeCardsCache {

  private static final Logger LOG = LoggerFactory.getLogger(HomeCardsCache.class);
  // 카드 목록·순서·혜택 금액이 바뀌는 지점(재정렬·연동·연동해제·CODEF 동기화·혜택 재계산)마다
  // evict/evictAll을 호출해 즉시 무효화하므로, TTL은 그 무효화를 놓쳤을 때의 안전망 역할이다.
  private static final Duration TTL = Duration.ofHours(1);
  private static final TypeReference<List<HomeCardRow>> ROWS_TYPE = new TypeReference<>() { };
  private static final String KEY_PREFIX = "home:cards:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public HomeCardsCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public List<HomeCardRow> get(String userId, String yearMonth) {
    String json = redisTemplate.opsForValue().get(key(userId, yearMonth));
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, ROWS_TYPE);
    } catch (JsonProcessingException e) {
      LOG.warn("홈 카드 캐시 역직렬화 실패, DB 조회로 대체합니다.", e);
      return null;
    }
  }

  public void put(String userId, String yearMonth, List<HomeCardRow> rows) {
    try {
      String json = objectMapper.writeValueAsString(rows);
      redisTemplate.opsForValue().set(key(userId, yearMonth), json, TTL);
    } catch (JsonProcessingException e) {
      LOG.warn("홈 카드 캐시 저장 실패, 캐시 없이 계속 진행합니다.", e);
    }
  }

  /** 특정 월의 혜택 금액 재계산처럼, 재계산 대상 월을 정확히 아는 경우에 쓴다. */
  public void evict(String userId, String yearMonth) {
    redisTemplate.delete(key(userId, yearMonth));
  }

  /**
   * 카드 재정렬·별칭 변경·연동·연동 해제처럼 카드 목록 자체가 바뀌는 경우에 쓴다.
   * display_order·alias는 조회 월과 무관하게 모든 캐시된 월에 반영되므로, 그 유저의 홈 카드
   * 캐시를 월 구분 없이 전부 비운다.
   */
  public void evictAll(String userId) {
    Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private String key(String userId, String yearMonth) {
    return KEY_PREFIX + userId + ":" + yearMonth;
  }
}
