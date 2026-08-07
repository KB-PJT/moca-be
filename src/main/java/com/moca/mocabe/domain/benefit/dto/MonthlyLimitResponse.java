package com.moca.mocabe.domain.benefit.dto;
public class MonthlyLimitResponse { private final long usedAmount; private final long limitAmount; private final long remainingAmount;
    public MonthlyLimitResponse(long used,long limit,long remaining){usedAmount=used;limitAmount=limit;remainingAmount=remaining;}
    public long getUsedAmount(){return usedAmount;} public long getLimitAmount(){return limitAmount;} public long getRemainingAmount(){return remainingAmount;} }
