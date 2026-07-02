package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import com.example.esti.excel.VendorProductSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수전금구 3-시트 <b>DB 적재</b> 검증(§10). 파싱은 {@code VendorBFaucetSheetTest}.
 *
 * <p>같은 본품이 일반·국산 시트에 등장 → 본품 1행(대분류=수전금구)으로 upsert되고 가격만 price_basis로 분리.
 * 부속 출처는 부속 categorySmall(국산). 커밋 샘플은 일반·국산만 있음(OEM 없음).</p>
 */
class VendorBFaucetDbTest extends AbstractVendorBSheetDbVerification {

    private static final String LARGE = "수전금구";
    private static final String KOR = "수전금구(국산 부속 기준)";

    @Test
    void 공유본품은_한_행이고_대분류_수전금구로_통합() {
        VendorProduct g0110 = dbSetProduct(LARGE, "G-0110");
        assertThat(g0110.getCategoryLarge()).isEqualTo("수전금구");
        assertThat(g0110.getCategorySmall()).isEqualTo("G-01"); // 본품 소분류=시리즈(안정, 국산 적재로 오염 안 됨)

        // 대분류=수전금구 DB 본품 수 = 파서가 만든 '서로 다른 품번' 수(일반+국산 합집합, upsert 병합)
        Set<String> parsedCodes = parsedSetsOf(LARGE).stream()
                .map(s -> s.main().productCode()).collect(Collectors.toSet());
        List<VendorProduct> dbSets = dbSetProductsOf(LARGE);
        assertThat(dbSets.stream().map(VendorProduct::getProductCode).collect(Collectors.toSet()))
                .as("모든 파서 본품 품번이 DB에 존재(유실 0)").containsAll(parsedCodes);
        assertThat(dbSets).as("DB 본품 수 = 서로 다른 품번 수(충돌 0)").hasSize(parsedCodes.size());
    }

    @Test
    void 같은_본품이_price_basis별로_가격_분리적재() {
        VendorProduct g0110 = dbSetProduct(LARGE, "G-0110");
        // 일반(대리점가) 31000 / 국산(소계 세트가) 54400 — 한 품번, 두 가격행
        assertThat(dbSetPriceByBasis(g0110, "수전금구")).isEqualByComparingTo(new BigDecimal("31000"));
        assertThat(dbSetPriceByBasis(g0110, KOR)).isEqualByComparingTo(new BigDecimal("54400"));
    }

    @Test
    void 국산_부속은_categorySmall_국산으로_적재() {
        VendorProduct g0110 = dbSetProduct(LARGE, "G-0110");
        VendorProduct popup = dbPart(g0110, "폽업");
        assertThat(popup.getProductCode()).isEqualTo("G-0110_U9110150");
        assertThat(popup.getCategorySmall()).isEqualTo("국산");
        assertThat(dbPartPrice(popup)).isEqualByComparingTo(new BigDecimal("8800"));
    }

    @Test
    void 재업로드_멱등() {
        assertReimportIdempotent();
    }
}
