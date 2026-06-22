package com.example.esti.excel;

import java.math.BigDecimal;

/**
 * 파싱된 단일 품목(대표품목 본품 또는 부속품 1건).
 *
 * <p>{@link VendorProductSet}의 구성 요소로 쓰인다. 대표품목/부속품 공통 표현.
 * 가격이 없는 행은 스킵하지 않고 {@code unitPrice=0} + {@code productName} 뒤에 사유를 표기한다(D8).
 */
public record VendorParsedItem(
        String productCode,    // 품번(코드). A사=신품번(F), B사=품번/부속 제품코드
        String productName,    // 표시명. A사=구성품명(D), B사=슬롯 라벨. 사유표기 포함 가능
        String oldItemCode,    // 구품번 (A사 E열)
        String subItemCode,    // KS품번 등 (B사)
        String relationType,   // 본품=MAIN, 부속=슬롯 라벨(도기/시트/앵글밸브…) 또는 ACCESSORY
        BigDecimal unitPrice,  // 개별 단가 (없으면 0)
        String remark          // 비고
) {
    public static final String RELATION_MAIN = "MAIN";
    public static final String RELATION_ACCESSORY = "ACCESSORY";
}
