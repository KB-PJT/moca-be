package com.moca.mocabe.domain.home.service;

import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 홈 인사 문구의 놓친 혜택 합계(0 이상, null 없음)를 Redis에 짧게 캐싱해 반복 조회의 DB 부하를 줄인다. */
public class HomeGreetingCache {

  private static final Logger LOG = LoggerFactory.getLogger(HomeGreetingCache.class);
  // 혜택 계산 결과가 바뀌는 지점(CODEF 동기화·재계산 API)마다 evict/evictAll을 호출해 즉시
  // 무효화하므로, TTL은 그 무효화를 놓쳤을 때의 안전망 역할이다.
  private static final Duration TTL = Duration.ofHours(1);
  private static final String KEY_PREFIX = "home:greeting:missed:";

  private final StringRedisTemplate redisTemplate;

  public HomeGreetingCache(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** 캐시에 없으면 null을 반환한다. */
  public Long get(String userId, String yearMonth) {
    String cached = redisTemplate.opsForValue().get(key(userId, yearMonth));
    return cached == null ? null : Long.valueOf(cached);
  }

  public void put(String userId, String yearMonth, long missedBenefitAmount) {
    try {
      redisTemplate.opsForValue().set(key(userId, yearMonth), Long.toString(missedBenefitAmount), TTL);
    } catch (RuntimeException e) {
      LOG.warn("홈 인사 캐시 저장 실패, 캐시 없이 계속 진행합니다.", e);
    }
  }

  /** 해당 월의 혜택 계산 결과가 바뀌었을 때(예: 재계산 API) 즉시 호출한다. */
  public void evict(String userId, String yearMonth) {
    redisTemplate.delete(key(userId, yearMonth));
  }

  /** CODEF 동기화처럼 영향받는 월이 여러 개일 수 있을 때, 그 유저의 캐시를 월 구분 없이 전부 비운다. */
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
