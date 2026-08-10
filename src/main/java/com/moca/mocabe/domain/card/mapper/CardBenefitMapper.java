package com.moca.mocabe.domain.card.mapper;

import com.moca.mocabe.domain.card.model.CardBenefitRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 카드 콘텐츠 버전의 혜택·유의사항 영속성 접근을 담당한다. */
@Mapper
public interface CardBenefitMapper {

    List<CardBenefitRow> findByContentVersionId(@Param("contentVersionId") String contentVersionId);
}
