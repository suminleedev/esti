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

    private static final String CAT = "갈라시아";

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
    void req1_소분류는_갈라시아_대분류는_시트명유지() {
        VendorProduct art = dbSetProduct(CAT, "art6103");
        assertThat(art.getCategorySmall()).isEqualTo("갈라시아");
        assertThat(art.getCategoryLarge()).isEqualTo("갈라시아");
    }

    @Test
    void 세트가_도기부속합_430000() {
        VendorProduct art = dbSetProduct(CAT, "art6103");
        assertThat(dbSetPrice(CAT, art)).isEqualByComparingTo(new BigDecimal("430000"));
        assertThat(dbPartsOf(art)).hasSize(2);
    }
}
