package com.example.esti.excel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 파싱된 "제품 구성 묶음" — 대표품목 1건 + 부속품 N건.
 *
 * <p>파서의 새 출력 단위(기존 평면 {@code VendorExcelRow} 대체). 대표품목↔부속품 구성 관계를
 * 그대로 표현하여 저장 단계에서 {@code VendorProduct} + {@code VendorProductRelation} +
 * {@code VendorItemPrice}로 풀어낸다.
 *
 * <ul>
 *   <li>일반 세트(A사, B사 양변기/소변기/갈라시아): {@code setPrice} = 합계행/計 또는 부속 단가 합산(D1)</li>
 *   <li>선택형 세트(B사 세면기, D10): {@code selectable=true}, {@code setPrice=null}(미산정)</li>
 *   <li>단일 제품(B사 비데/수전금구/악세사리): {@code parts} 비어있고 {@code setPrice}=본품 단가</li>
 * </ul>
 */
public record VendorProductSet(
        String vendorCode,            // 'A' or 'B'
        String categoryLarge,         // 대분류
        String categorySmall,         // 소분류
        VendorParsedItem main,        // 대표품목(본품, MAIN)
        List<VendorParsedItem> parts, // 부속품 목록 (없으면 빈 리스트)
        BigDecimal setPrice,          // 세트 합계가. 선택형이면 null
        boolean selectable,           // 선택형 세트(합계 미산정) 여부
        String imageKey,              // 임베디드 이미지 식별키(대표품목의 0-based 행번호, 없으면 null) — P5에서 채움
        boolean needsReview,          // 자동 그룹핑 실패(합계≠부속합산 등) → 검수 필요(D16)
        String priceBasis,            // 대표품목 가격 분리 기준. 기본은 categoryLarge(하위호환).
                                      // 대분류≠가격기준인 시트(수전금구 3-시트: 대분류 "수전금구" 고정, basis=시트명)에만 별도 지정(§10 S3)
        String sheetName              // 원본 시트명. 임베디드 이미지 매칭 키(ExcelImageExtractor 맵이 시트명 키).
                                      // 대분류를 시트명에서 분리·정제하는 시트(비데/기타·갈라시아·수전금구·악세사리 등)도
                                      // 이미지가 안 끊기도록 categoryLarge와 독립적으로 시트명을 보존한다(§13 sheetName 분리).
) {
    /**
     * 기존 9-인자 호출 호환 — {@code priceBasis = sheetName = categoryLarge}(종전 동작 그대로).
     * 대분류를 그대로 시트명·가격 분리 기준으로 쓰는(대분류==시트명) 시트가 이 생성자를 탄다.
     */
    public VendorProductSet(String vendorCode, String categoryLarge, String categorySmall,
                            VendorParsedItem main, List<VendorParsedItem> parts, BigDecimal setPrice,
                            boolean selectable, String imageKey, boolean needsReview) {
        this(vendorCode, categoryLarge, categorySmall, main, parts, setPrice,
                selectable, imageKey, needsReview, categoryLarge, categoryLarge);
    }

    /**
     * 10-인자 호출 호환 — 대분류를 시트명에서 분리·정제한 시트용({@code categoryLarge != 시트명}).
     * 이 시트들은 {@code priceBasis}에 시트명을 넘기므로 {@code sheetName = priceBasis}가 된다
     * (가격 분리 기준과 이미지 매칭 키가 모두 시트명이라 일치).
     */
    public VendorProductSet(String vendorCode, String categoryLarge, String categorySmall,
                            VendorParsedItem main, List<VendorParsedItem> parts, BigDecimal setPrice,
                            boolean selectable, String imageKey, boolean needsReview, String priceBasis) {
        this(vendorCode, categoryLarge, categorySmall, main, parts, setPrice,
                selectable, imageKey, needsReview, priceBasis, priceBasis);
    }
}
