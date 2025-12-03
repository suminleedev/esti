package com.example.esti.dto;

import java.math.BigDecimal;

public record VendorCatalogView(
        Long catalogId,
        String vendorCode,
        String vendorName,
        String categoryLarge,
        String categorySmall,
        String productName,      // 세트명 (ProductCatalog.name)
        String mainItemCode,     // 대표 신품번 (VendorItemPrice.mainItemCode)
        String oldItemCode,      // 구품번
        String vendorItemName,   // 공급사 기준 대표 품목명
        BigDecimal unitPrice     // 공급사 세트 단가
) {}
