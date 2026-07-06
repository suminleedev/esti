package com.example.esti.dto;

import java.math.BigDecimal;

/**
 * 카탈로그 행(가격 라인) 인라인 수정 요청.
 * 브랜드(Vendor)는 여러 상품이 공유하므로 이 화면에서 수정하지 않는다.
 */
public record VendorCatalogUpdateRequest(
        String categoryLarge,
        String categorySmall,
        String productName,
        String mainItemCode,
        String remark,
        BigDecimal unitPrice,
        String description,
        String imageUrl
) {}
