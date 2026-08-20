package com.example.esti.dto;

import java.math.BigDecimal;

/**
 * 대표품목(세트)을 구성하는 부속 1건 (B-2 드릴다운 응답).
 *
 * <p>{@link VendorCatalogView}에 끼워 넣지 않고 별도 record로 둔다 — 목록 응답은 페이지당 수십 행이라
 * 부속까지 실으면 무거워지고, 부속은 사용자가 행을 펼친 시점에만 필요하다.
 *
 * @param unitPrice 부속 단가. 공유 부속은 코드당 1건(priceBasis=null, D13)이라 그 값을 그대로 쓴다.
 */
public record VendorProductPartView(
        Long vendorProductId,
        String productCode,
        String productName,
        String relationType,   // 슬롯 라벨(도기/시트/앵글밸브…) 또는 ACCESSORY
        BigDecimal unitPrice,
        String imageUrl
) {}
