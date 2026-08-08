package com.moca.mocabe.domain.codef.mapper;

import com.moca.mocabe.domain.codef.model.CardCatalogEntry;
import com.moca.mocabe.domain.codef.model.CardOptionRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 카드 마스터 이름과 검증 완료된 선택 옵션 조회를 담당한다. */
@Mapper
public interface CardCatalogMapper {

  List<CardCatalogEntry> findCardsByIssuerId(@Param("issuerId") String issuerId);

  CardCatalogEntry findCardById(@Param("cardId") String cardId);

  List<CardOptionRow> findVerifiedOptionsByCardId(@Param("cardId") String cardId);
}
