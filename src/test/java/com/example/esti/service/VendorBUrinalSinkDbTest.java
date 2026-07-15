package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소변기·수채 시트 <b>DB 적재</b> 검증(기존 ij 삭제 → 재기동 → DBeaver 수동확인을 대체).
 * 파싱 정확성은 {@code VendorBUrinalSinkSheetTest}, 여기선 대분류 분리·부속/세트가 적재를 단언한다.
 */
class VendorBUrinalSinkDbTest extends AbstractVendorBSheetDbVerification {

    private static final String URINAL = "소변기";
    private static final String SINK = "수채";

    @Test
    void 유실0_충돌0_재업로드_멱등() {
        assertNoLossNoCollision(URINAL);
        assertNoLossNoCollision(SINK);
        assertReimportIdempotent();
    }

    @Test
    void req1_대분류가_소변기_수채로_분리저장되고_시트명통째는_없음() {
        // 두 대분류가 DB에 각각 존재
        assertThat(dbSetProductsOf(URINAL)).as("소변기 대표품목").isNotEmpty();
        assertThat(dbSetProductsOf(SINK)).as("수채 대표품목").isNotEmpty();

        // 시트명 통째("소변기, 수채")로 저장된 대표품목이 없어야 함(다른 시트의 콤마 대분류는 무관)
        List<VendorProduct> combined = productRepository.findAll().stream()
                .filter(p -> "SET".equals(p.getItemType()))
                .filter(p -> p.getCategoryLarge() != null
                        && p.getCategoryLarge().contains("소변기") && p.getCategoryLarge().contains(","))
                .toList();
        assertThat(combined).as("대분류에 시트명 통째(소변기, 수채) 잔존").isEmpty();
    }

    @Test
    void 소변기_IU302E_설명텍스트는_부속아니라_description컬럼에_저장() {
        VendorProduct iu302 = dbSetProduct(URINAL, "IU302E");
        assertThat(iu302.getDescription()).startsWith("후렌지/스프레다 포함"); // C-2로 P열 비고가 뒤에 병합될 수 있음
        assertThat(dbPartsOf(iu302)).extracting(VendorProduct::getProductName)
                .doesNotContain("스퍼드");
    }

    @Test
    void 소변기_U135_스퍼드_후렌지_세트가95500() {
        VendorProduct u135 = dbSetProduct(URINAL, "U135");
        assertThat(dbSetPrice(URINAL, u135)).isEqualByComparingTo(new BigDecimal("95500"));
        assertThat(dbPartsOf(u135)).extracting(VendorProduct::getProductName)
                .contains("스퍼드", "후렌지");
    }

    @Test
    void req2_수채_SS131_수채가랑_수채트랩_세트가122000_소변기슬롯_오염없음() {
        VendorProduct ss131 = dbSetProduct(SINK, "SS131");
        assertThat(dbSetPrice(SINK, ss131)).isEqualByComparingTo(new BigDecimal("122000"));

        assertThat(dbPartPrice(dbPart(ss131, "수채가랑"))).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(dbPartPrice(dbPart(ss131, "수채트랩"))).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(dbPartsOf(ss131)).extracting(VendorProduct::getProductName)
                .doesNotContain("스퍼드", "후렌지");

        // 計 = 부속 단가 합
        BigDecimal sum = dbPartsOf(ss131).stream()
                .map(this::dbPartPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dbSetPrice(SINK, ss131)).isEqualByComparingTo(sum);
    }
}
