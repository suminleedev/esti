package com.example.esti.excel;

import java.math.BigDecimal;

public record VendorExcelRow(
        String vendorCode,        // 'A' or 'B'
        String categoryLarge,     // 대분류
        String categorySmall,     // 소분류
        String productName,       // 제품명(세트명)
        String masterCodeHint,    // masterCode 후보 (초기엔 제안서 품번 등)
        String proposalItemCode,  // 제안서 품번
        String mainItemCode,      // 메인부속 품번
        String subItemCode,       // 보조품번/구품번 등
        String oldItemCode,       // 구품번(A사용)
        String vendorItemName,    // 공급사 기준 제품명
        String vendorSpec,        // 공급사 규격/특징
        String remark,            // 비고 전체
        BigDecimal unitPrice,     // 세트(합계) 단가
        String priceType          // 'SET' 고정
) {}

