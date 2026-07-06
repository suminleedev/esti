package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세면기 시트 <b>DB 적재</b> 검증(기존 ij 삭제 → 재기동 → DBeaver 수동확인을 대체).
 * 파싱 정확성은 {@code VendorBWashbasinSheetTest}, 여기선 적재/업서트/세트가 보존을 단언한다.
 */
class VendorBWashbasinDbTest extends AbstractVendorBSheetDbVerification {

    private static final String CAT = "세면기";

    @Test
    void 유실0_충돌0_재업로드_멱등() {
        assertNoLossNoCollision(CAT);
        assertReimportIdempotent();
    }

    @Test
    void IL451B_도기원홀반영_4는대체옵션으로_적재되되_세트가_미포함() {
        VendorProduct il451 = dbSetProduct(CAT, "IL451B");

        // 도기(원홀)+반다리 = 69000 (도기4"는 대체옵션이라 세트가에서 빠짐)
        assertThat(dbSetPrice(CAT, il451)).isEqualByComparingTo(new BigDecimal("69000"));

        // 대체옵션 도기(4")도 부속으로 적재되어야(유실 방지) + 비고=대체옵션
        VendorProduct four = dbPart(il451, "도기(4\")");
        assertThat(dbPartRemark(four)).isEqualTo("대체옵션");
        assertThat(dbPartsOf(il451)).extracting(VendorProduct::getProductName).contains("도기(원홀)");
    }

    @Test
    void L966_반다리채택_긴다리는대체옵션_필수부속포함_87200() {
        VendorProduct l966 = dbSetProduct(CAT, "L966");
        assertThat(dbSetPrice(CAT, l966)).isEqualByComparingTo(new BigDecimal("87200"));

        assertThat(dbPartRemark(dbPart(l966, "긴다리"))).isEqualTo("대체옵션");
        assertThat(dbPartRemark(dbPart(l966, "반다리"))).isNull();
        assertThat(dbPartRemark(dbPart(l966, "하프고리"))).isNull();
        assertThat(dbPartRemark(dbPart(l966, "앙카볼트"))).isNull();
    }

    @Test
    void 도자종류_중복품번_분기되어_양쪽_보존되고_도자명은_description() {
        VendorProduct hwa = dbSetProduct(CAT, "IL672Eh");
        VendorProduct cle = dbSetProduct(CAT, "IL672Ec");
        assertThat(hwa.getDescription()).isEqualTo("화려");
        assertThat(cle.getDescription()).isEqualTo("클레이탄");
        assertThat(dbSetPrice(CAT, hwa)).isEqualByComparingTo(new BigDecimal("61000"));
        assertThat(dbSetPrice(CAT, cle)).isEqualByComparingTo(new BigDecimal("61000"));

        // base 품번(IL672E)은 단독으로 남으면 안 됨(분기 실패 시 유실)
        assertThat(dbSetProductsOf(CAT)).extracting(VendorProduct::getProductCode)
                .doesNotContain("IL672E", "IL674E");
    }
}
