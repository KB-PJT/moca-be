package com.moca.mocabe.domain.support.mapper;

import com.moca.mocabe.domain.support.model.InquiryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupportInquiryMapper {

    int insertInquiry(@Param("inquiryId") String inquiryId,
                      @Param("userId") String userId,
                      @Param("inquiryType") String inquiryType,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("replyEmail") String replyEmail);

    InquiryRow findByInquiryId(@Param("inquiryId") String inquiryId, @Param("userId") String userId);
}
