package com.moca.mocabe.domain.merchant.service;

import java.text.Normalizer;
import java.util.Locale;

/** 가맹점명을 merchants/merchant_aliases의 정규화 컬럼과 완전일치 비교하기 위한 정규화 규칙이다. */
public class MerchantNameNormalizer {

    /** 대소문자·공백·특수문자 차이를 없앤 비교용 문자열로 정규화한다. */
    public String normalize(String merchantName) {
        if (merchantName == null) {
            return "";
        }
        return Normalizer.normalize(merchantName, Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
