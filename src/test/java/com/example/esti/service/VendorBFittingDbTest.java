package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 수전부속 3-시트 <b>DB 적재</b> 검증(§11). 파싱은 {@code VendorBFittingSheetTest}.
 *
 * <p>합본 샘플(수전 부속(세트) 포함) 위에 시트별 픽스처(분계표·세트·단가표)를 추가 적재해
 * ①분계표 본품이 §10 수전금구 본품과 1행으로 병합되고 가격만 basis=분계표로 추가되는지,
 * ②세트·단가표 공통 품번이 1행 + 가격 2행(시트명 basis)으로 병합되는지 검증한다.</p>
 *
 * <p>분계표가 수전금구 대분류에 신규 본품(G-0721 등)을 추가하므로, 수전금구 카운트를 단언하는
 * {@code VendorBFaucetDbTest}와 DB를 공유하지 않도록 <b>별도 인메모리 DB 컨텍스트</b>를 쓴다.</p>
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:fittingdbtest;create=true"
})
class VendorBFittingDbTest extends AbstractVendorBSheetDbVerification {

    private static final Path FIXTURE = Path.of("docs/samples/B사 test (수전부속).xlsx");
    private static final String SET_BASIS = "수전 부속(세트)";
    private static final String PRICE_BASIS = "부속 단가표";

    /** 합본 샘플(부모 ensureLoaded) 위에 수전부속 픽스처를 1회 추가 적재(분계표-전용 품번으로 감지). */
    private void ensureFittingLoaded() {
        assumeTrue(Files.exists(FIXTURE), "픽스처 엑셀이 없어 스킵: " + FIXTURE);
        boolean loaded = productRepository.findAll().stream()
                .anyMatch(p -> "G-0721".equals(p.getProductCode()));
        if (!loaded) {
            service.importVendorCatalog("B", FIXTURE);
        }
    }

    @Test
    void 분계표본품은_수전금구본품과_1행으로_병합되고_가격은_분계표basis() {
        ensureFittingLoaded();
        // "G 0130" → G-0130 정규화 → 대분류=수전금구 1행(§10과 병합), 분계표 대리점가는 별도 basis(P1)
        VendorProduct g0130 = dbSetProduct("수전금구", "G-0130");
        assertThat(g0130.getCategorySmall()).isEqualTo("G-01");
        assertThat(dbSetPriceByBasis(g0130, "분계표")).isEqualByComparingTo(new BigDecimal("49800"));

        // 부속 = {품번}_{전산코드}, 출처 categorySmall=분계(P2)
        VendorProduct body = dbPart(g0130, "몸체");
        assertThat(body.getProductCode()).isEqualTo("G-0130_46dsg0130n");
        assertThat(body.getCategorySmall()).isEqualTo("분계");
        assertThat(dbPartPrice(body)).isEqualByComparingTo(new BigDecimal("27800"));
    }

    @Test
    void 공통품번은_제품1행에_시트명basis_가격2행() {
        ensureFittingLoaded();
        // U9111이 세트(9500)·단가표(9500) 모두 등장 → upsert 1행 + 가격 2행(P5)
        VendorProduct u9111 = dbSetProduct("수전부속", "U9111");
        assertThat(dbSetPriceByBasis(u9111, SET_BASIS)).isEqualByComparingTo(new BigDecimal("9500"));
        assertThat(dbSetPriceByBasis(u9111, PRICE_BASIS)).isEqualByComparingTo(new BigDecimal("9500"));

        // 표현이 다른 가격도 유실 없이 분리: U9310 세트=건+행거 합 5000 / 단가표=건만 4500
        VendorProduct u9310 = dbSetProduct("수전부속", "U9310");
        assertThat(dbSetPriceByBasis(u9310, SET_BASIS)).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(dbSetPriceByBasis(u9310, PRICE_BASIS)).isEqualByComparingTo(new BigDecimal("4500"));
        // 행거는 품번 재사용 → 전산코드 폴백 단품(P8)
        assertThat(dbSetPriceByBasis(dbSetProduct("수전부속", "43u0630"), PRICE_BASIS))
                .isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void 합성세트는_부속2건과_소계세트가로_적재() {
        ensureFittingLoaded();
        // U9013c/h + 소계 → 합성 세트 U9013 + 부속 U9013_c/h(P6). 단가표 단품 U9013C와 공존
        VendorProduct u9013 = dbSetProduct("수전부속", "U9013");
        assertThat(dbSetPriceByBasis(u9013, SET_BASIS)).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(dbPartsOf(u9013)).hasSize(2);
        assertThat(dbPartsOf(u9013).stream().map(VendorProduct::getProductCode))
                .containsExactlyInAnyOrder("U9013_c", "U9013_h");
        assertThat(dbSetPriceByBasis(dbSetProduct("수전부속", "U9013C"), PRICE_BASIS))
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void 픽스처_재업로드_멱등() {
        ensureFittingLoaded();
        long products = productRepository.count();
        long prices = priceRepository.count();
        long relations = relationRepository.count();

        service.importVendorCatalog("B", FIXTURE);

        assertThat(productRepository.count()).as("재업로드 후 제품 수 불변").isEqualTo(products);
        assertThat(priceRepository.count()).as("재업로드 후 가격 수 불변").isEqualTo(prices);
        assertThat(relationRepository.count()).as("재업로드 후 관계 수 불변").isEqualTo(relations);
    }
}
