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
        String imageKey,              // 임베디드 이미지 식별키/임시경로 (없으면 null) — P5에서 채움
        boolean needsReview,          // 자동 그룹핑 실패(합계≠부속합산 등) → 검수 필요(D16)
        String priceBasis             // 대표품목 가격 분리 기준. 기본은 categoryLarge(하위호환).
                                      // 대분류≠가격기준인 시트(수전금구 3-시트: 대분류 "수전금구" 고정, basis=시트명)에만 별도 지정(§10 S3)
) {
    /**
     * 기존 9-인자 호출 호환 — {@code priceBasis = categoryLarge}(종전 동작 그대로).
     * 대분류를 그대로 가격 분리 기준으로 쓰는 모든 기존 파서가 이 생성자를 탄다.
     */
    public VendorProductSet(String vendorCode, String categoryLarge, String categorySmall,
                            VendorParsedItem main, List<VendorParsedItem> parts, BigDecimal setPrice,
                            boolean selectable, String imageKey, boolean needsReview) {
        this(vendorCode, categoryLarge, categorySmall, main, parts, setPrice,
                selectable, imageKey, needsReview, categoryLarge);
    }

    /**
     * imageKey에 원본 시트명을 실을 때 쓰는 구분자(Excel 시트명 금지 문자 "[").
     *
     * <p>임베디드 이미지 맵은 <b>시트명</b>으로 키가 잡히는데(ExcelImageExtractor), 대분류를 시트명에서
     * 분리 저장하는 시트(예: "비데, 기타" → 비데/기타)는 {@code categoryLarge}로 이미지를 찾지 못한다.
     * 이 경우 파서가 imageKey를 {@code "시트명" + SEP + 행번호}로 채워 시트명 기준으로 이미지를 찾게 한다.
     * 구분자가 없는 기존 키(순수 행 번호)는 종전대로 {@code categoryLarge}로 조회한다(하위호환).</p>
     */
    public static final String IMAGE_KEY_SHEET_SEP = "[";  // Excel 시트명에 금지된 문자라 충돌 불가
}
