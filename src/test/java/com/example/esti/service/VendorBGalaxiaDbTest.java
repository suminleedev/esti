package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 갈라시아 시트 <b>DB 적재</b> 검증. 파싱 정확성은 {@code VendorBGalaxiaSheetTest},
 * 여기선 표시 보강(소분류=갈라시아·productName 접두)과 적재/세트가를 단언한다.
 */
class VendorBGalaxiaDbTest extends AbstractVendorBSheetDbVerification {

    /** 대분류=정제값(D46). 조회/유실검증은 이 값으로. */
    private static final String CAT = "세면기(갈라시아)";
    /** 가격 분리 기준=원본 시트명(§13 sheetName 분리 후에도 priceBasis는 종전과 동일). */
    private static final String BASIS = "갈라시아";

    @Test
    void 유실0_충돌0_재업로드_멱등() {
        assertNoLossNoCollision(CAT);
        assertReimportIdempotent();
    }

    @Test
    void req2_메인_productName은_갈라시아세면기_접두_품번은유지() {
        VendorProduct art = dbSetProduct(CAT, "art6103");
        assertThat(art.getProductCode()).isEqualTo("art6103");
        assertThat(art.getProductName()).isEqualTo("갈라시아 세면기 art6103");
    }

    @Test
    void req1_소분류는_갈라시아_대분류는_세면기갈라시아() {
        VendorProduct art = dbSetProduct(CAT, "art6103");
        assertThat(art.getCategorySmall()).isEqualTo("갈라시아");
        assertThat(art.getCategoryLarge()).isEqualTo("세면기(갈라시아)");
    }

    @Test
    void 세트가_도기부속합_430000() {
        VendorProduct art = dbSetProduct(CAT, "art6103");
        assertThat(dbSetPriceByBasis(art, BASIS)).isEqualByComparingTo(new BigDecimal("430000"));
        assertThat(dbPartsOf(art)).hasSize(2);
    }
}
