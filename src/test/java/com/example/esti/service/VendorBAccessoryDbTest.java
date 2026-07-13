package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 악세사리 단가표 <b>DB 적재</b> 검증(§12). 파싱은 {@code VendorBAccessorySheetTest}.
 *
 * <p>합본 샘플에 악세사리 단가표 시트가 포함돼 있어 별도 픽스처 없이 공유 컨텍스트로 검증한다.
 * 대분류=악세사리(C-1), 가격은 시트명 basis. U접두 필터는 {품번}-{전산코드}라 수전부속과 무충돌.</p>
 */
class VendorBAccessoryDbTest extends AbstractVendorBSheetDbVerification {

    private static final String LARGE = "악세사리";
    private static final String BASIS = "악세사리 단가표";

    @Test
    void 유실_충돌_없음() {
        assertNoLossNoCollision(LARGE);
    }

    @Test
    void 세트는_대표_부속_관계와_세트가로_적재() {
        VendorProduct ac8100 = dbSetProduct(LARGE, "AC8100");
        assertThat(dbSetPriceByBasis(ac8100, BASIS)).isEqualByComparingTo(new BigDecimal("60000"));
        assertThat(dbPartsOf(ac8100)).hasSize(4);
        VendorProduct towel = dbPart(ac8100, "수건걸이");
        assertThat(towel.getProductCode()).isEqualTo("AC8100_AC8101");
        assertThat(dbPartPrice(towel)).isEqualByComparingTo(new BigDecimal("20500"));
    }

    @Test
    void U접두_필터는_결합코드로_적재되어_맨품번_미생성() {
        // {품번}-{전산코드} 결합(A1) — 수전부속 U9120(자동폽업)과 별개 행 보장
        VendorProduct filter = dbSetProduct(LARGE, "U9120-43u0120");
        assertThat(dbSetPriceByBasis(filter, BASIS)).isEqualByComparingTo(new BigDecimal("6500"));
        assertThat(dbSetProductsOf(LARGE).stream()
                .anyMatch(p -> "U9120".equals(p.getProductCode()))).isFalse();
    }

    @Test
    void 재업로드_멱등() {
        assertReimportIdempotent();
    }
}
