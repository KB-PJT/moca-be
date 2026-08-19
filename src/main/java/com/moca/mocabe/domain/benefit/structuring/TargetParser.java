package com.moca.mocabe.domain.benefit.structuring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 일반 업종·전 가맹점 표현만 category/all target으로 확정한다. 명시 브랜드는 resolver로 넘긴다. */
public class TargetParser {
  private static final Map<String, String> CATEGORIES = categories();
  private static final List<String> EXPLICIT_MERCHANTS = List.of(
      "세븐일레븐", "파리바게뜨", "투썸플레이스", "롯데시네마", "롯데백화점", "신세계백화점",
      "현대백화점", "메가MGC커피", "메가박스", "스타벅스", "커피빈", "폴바셋", "이마트24",
      "하나로마트", "홈플러스", "롯데마트", "에버랜드", "서울랜드", "롯데월드", "파리크라상",
      "뚜레쥬르", "맥도날드", "버거킹", "롯데리아", "올리브영", "교보문고",
      "CGV", "YES24", "GS25", "KFC", "VIPS", "아웃백", "이마트", "CU");
  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public Optional<ParsedTarget> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    if (text.contains("모든 가맹점") || text.contains("전 가맹점") || text.contains("국내 가맹점")) {
      return Optional.of(new ParsedTarget(ParsedTarget.Type.ALL_MERCHANTS, "ALL"));
    }
    Optional<String> explicitMerchant = EXPLICIT_MERCHANTS.stream()
        .filter(text::contains)
        .findFirst();
    if (explicitMerchant.isPresent()) {
      return Optional.of(new ParsedTarget(ParsedTarget.Type.MERCHANT, explicitMerchant.orElseThrow()));
    }
    for (Map.Entry<String, String> category : CATEGORIES.entrySet()) {
      if (text.contains(category.getKey())) {
        return Optional.of(new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, category.getValue()));
      }
    }
    return Optional.empty();
  }

  private static Map<String, String> categories() {
    Map<String, String> categories = new LinkedHashMap<>();
    categories.put("편의점", "CONVENIENCE_STORE");
    categories.put("커피전문점", "CAFE");
    categories.put("영화관", "MOVIE");
    categories.put("도서", "BOOKS");
    categories.put("서점", "BOOKS");
    categories.put("대형마트", "LARGE_MART");
    categories.put("백화점", "DEPARTMENT_STORE");
    categories.put("면세점", "DUTY_FREE");
    categories.put("베이커리", "BAKERY");
    categories.put("패스트푸드", "FAST_FOOD");
    categories.put("테마파크", "THEME_PARK");
    categories.put("골프", "GOLF");
    categories.put("주유소", "FUEL");
    categories.put("택시", "TAXI");
    categories.put("지하철", "SUBWAY");
    categories.put("버스", "BUS");
    categories.put("렌터카", "RENTAL_CAR");
    categories.put("호텔", "HOTEL");
    categories.put("공연", "PERFORMANCE_EXHIBITION");
    categories.put("전시", "PERFORMANCE_EXHIBITION");
    categories.put("카페", "CAFE");
    categories.put("커피", "CAFE");
    categories.put("음식점", "RESTAURANT");
    categories.put("병원", "HOSPITAL");
    categories.put("약국", "PHARMACY");
    categories.put("학원", "ACADEMY");
    return java.util.Collections.unmodifiableMap(categories);
  }
}
