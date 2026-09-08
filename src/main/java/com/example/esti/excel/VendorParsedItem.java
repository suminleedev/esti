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
        String remark,         // 비고
        String description,    // 원본 품번/부가 설명 보존 (수전부속처럼 코드를 제품코드로 대체할 때 원본 B열 등)
        String categorySmall,  // 이 품목 전용 소분류(있으면 세트의 categorySmall 대신 사용). null이면 세트값(§10 S4: 수전금구 부속 출처 국산/OEM)
        String specs,          // 규격(15파이/70mm 등). 비고 분류 정책(C-2)에서 규격성 비고가 여기로 온다
        String unit            // 단위(ea/SET/조). 원본에 단위 컬럼이 있는 시트만 채운다. null이면 VendorProduct 기본값(SET)
) {
    public static final String RELATION_MAIN = "MAIN";
    public static final String RELATION_ACCESSORY = "ACCESSORY";

    /**
     * <b>선택 옵션</b> — 세트에 기본 포함되지 않고 «대신 고를 수 있는» 부속.
     *
     * <p>B사 도기 시트의 N~P 서브테이블이 이것이다(탱크뚜껑·F/V·감지기…). 원본이 기본 구성을
     * 왼쪽에, 선택지를 오른쪽에 나눠 적었고 <b>세트가(計)에는 왼쪽만 들어간다.</b>
     *
     * <p>그래서 {@code ACCESSORY}와 갈라야 한다 — 합계 대조에 넣으면 «計 ≠ 부속합»이 되어
     * 멀쩡한 세트가 전부 오류로 뜬다. 세트 정체성({@code setHash})에도 넣지 않는다:
     * 고를 수 있는 것이 달라졌다고 다른 세트가 되는 것은 아니다.
     */
    public static final String RELATION_OPTION = "OPTION";

    /** 기존 10-인자 호출 호환 (unit 없음). 단위 컬럼이 없는 시트는 이쪽을 그대로 쓴다. */
    public VendorParsedItem(String productCode, String productName, String oldItemCode,
                            String subItemCode, String relationType, BigDecimal unitPrice,
                            String remark, String description, String categorySmall, String specs) {
        this(productCode, productName, oldItemCode, subItemCode, relationType, unitPrice,
                remark, description, categorySmall, specs, null);
    }

    /** 기존 7-인자 호출 호환 (description·categorySmall·specs 없음). */
    public VendorParsedItem(String productCode, String productName, String oldItemCode,
                            String subItemCode, String relationType, BigDecimal unitPrice, String remark) {
        this(productCode, productName, oldItemCode, subItemCode, relationType, unitPrice, remark, null, null, null, null);
    }

    /** 기존 8-인자 호출 호환 (categorySmall·specs 없음). */
    public VendorParsedItem(String productCode, String productName, String oldItemCode,
                            String subItemCode, String relationType, BigDecimal unitPrice,
                            String remark, String description) {
        this(productCode, productName, oldItemCode, subItemCode, relationType, unitPrice, remark, description, null, null, null);
    }

    /** 기존 9-인자 호출 호환 (specs 없음). */
    public VendorParsedItem(String productCode, String productName, String oldItemCode,
                            String subItemCode, String relationType, BigDecimal unitPrice,
                            String remark, String description, String categorySmall) {
        this(productCode, productName, oldItemCode, subItemCode, relationType, unitPrice, remark, description, categorySmall, null, null);
    }
}
