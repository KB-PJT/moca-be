package com.moca.mocabe.global.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * 문자열의 Unicode 코드 포인트 개수가 지정한 값 이하인지 검증한다.
 * {@code javax.validation.constraints.Size}는 UTF-16 코드 단위(String.length()) 기준이라
 * non-BMP 문자(이모지 등)는 코드 포인트 1개가 2개로 계산되어 OpenAPI maxLength 계약과 어긋난다.
 */
@Documented
@Constraint(validatedBy = MaxCodePointsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxCodePoints {

    int value();

    String message() default "허용된 최대 길이를 초과했습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
