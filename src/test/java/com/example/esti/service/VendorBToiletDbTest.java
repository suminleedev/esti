package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 양변기 시트 <b>DB 적재</b> 검증(기존 ij 삭제 → 재기동 → DBeaver 수동확인을 대체).
 * 파싱 정확성은 {@code VendorBToiletSheetTest}, 여기선 적재/업서트/세트가=부속합 보존을 단언한다.
 */
class VendorBToiletDbTest extends AbstractVendorBSheetDbVerification {

    private static final String CAT = "양변기";

    @Test
    void 유실0_충돌0_재업로드_멱등() {
        assertNoLossNoCollision(CAT);
        assertReimportIdempotent();
    }

    @Test
    void MC921_도기_FV_스퍼드_3부속_세트가77200() {
        VendorProduct mc921 = dbSetProduct(CAT, "MC921");
        assertThat(dbSetPrice(CAT, mc921)).isEqualByComparingTo(new BigDecimal("77200"));
        assertThat(dbPartsOf(mc921)).extracting(VendorProduct::getProductName)
                .contains("F/V", "스퍼드");
        assertThat(dbPartsOf(mc921)).hasSize(3);
    }

    @Test
    void C752_투피스_세트가가_부속합과_일치() {
        VendorProduct c752 = dbSetProduct(CAT, "C752");
        BigDecimal setPrice = dbSetPrice(CAT, c752);
        assertThat(setPrice).isEqualByComparingTo(new BigDecimal("126500"));

        BigDecimal partSum = dbPartsOf(c752).stream()
                .map(this::dbPartPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(setPrice).as("計 = 부속 단가 합").isEqualByComparingTo(partSum);
    }

    @Test
    void 도자종류_중복품번_분기되어_양쪽_보존되고_도자명은_description() {
        VendorProduct o = dbSetProduct(CAT, "IC703Eo");
        VendorProduct g = dbSetProduct(CAT, "IC703Eg");
        assertThat(o.getDescription()).isEqualTo("성오도자");
        assertThat(g.getDescription()).isEqualTo("구륙도자");
        assertThat(dbSetProductsOf(CAT)).extracting(VendorProduct::getProductCode)
                .doesNotContain("IC703E");

        // 품번 괄호 설명 분리(req3)
        assertThat(dbSetProduct(CAT, "IC552EF").getDescription()).isEqualTo("길마위욕");
    }
}
