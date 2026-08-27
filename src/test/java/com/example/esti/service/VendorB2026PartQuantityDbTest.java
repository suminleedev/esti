package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * §8 잔여 ② 회귀 잠금 — 부속 수량 축.
 *
 * <p>원본은 같은 부속이 2개 들어갈 때 <b>행을 두 번 적는다</b>
 * (`소변기, 수채` 시트 54·55행이 둘 다 {@code 수채가량 <CODE> 18000}).
 * 관계 유일키가 {@code (source, target, type)}이라 수량 축이 없으면 한 건으로 접히고,
 * 부속 합계가 세트가에 못 미친다(S132E −<PRICE> / L352E·L352E-2 각 −<PRICE>).
 *
 * <p>수량을 세면 세 세트 모두 <b>구성 합 = 세트가</b>가 정확히 성립한다. 그 등식을 여기서 잠근다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:b2026qtytest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images-qty"
})
class VendorB2026PartQuantityDbTest {

    private static final Path BOOK = Path.of("docs/samples/B사 단가표_2026최신.xlsx");

    @Autowired private CatalogImportAsyncService service;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    private void ensureLoaded() {
        requireSample(BOOK);
        if (productRepository.count() == 0) {
            service.importVendorCatalog("B", BOOK);
        }
    }

    private VendorProduct product(String code) {
        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();
        return productRepository.findByVendorAndProductCode(b, code)
                .orElseThrow(() -> new AssertionError("제품 미발견: " + code));
    }

    private List<VendorProductRelation> partsOf(String code) {
        return relationRepository.findAllBySourceProduct(product(code));
    }

    /** 부속 금액 합 = Σ(단가 × 수량). 세트가와 대조할 값이다. */
    private BigDecimal partsTotal(String code) {
        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();
        return partsOf(code).stream()
                .map(r -> priceRepository
                        .findFirstByVendorAndVendorProduct(b, r.getTargetProduct())
                        .map(VendorItemPrice::getUnitPrice)
                        .orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(r.getQuantity() == null ? 1 : r.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal setPrice(String code) {
        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();
        return priceRepository.findFirstByVendorAndVendorProduct(b, product(code))
                .map(VendorItemPrice::getUnitPrice)
                .orElseThrow(() -> new AssertionError("세트가 미발견: " + code));
    }

    @Test
    void 반복_행이_수량으로_잡힌다() {
        ensureLoaded();

        // 54·55행이 같은 부속 → 관계 1건 + 수량 2
        VendorProductRelation 수채가량 = partsOf("S132E").stream()
                .filter(r -> "수채가량".equals(r.getRelationType()))
                .findFirst().orElseThrow(() -> new AssertionError("수채가량 관계 미발견"));
        assertThat(수채가량.getQuantity()).as("수채가량 ×2").isEqualTo(2);

        VendorProductRelation 앵글밸브 = partsOf("L352E").stream()
                .filter(r -> "앵글밸브".equals(r.getRelationType()))
                .findFirst().orElseThrow(() -> new AssertionError("앵글밸브 관계 미발견"));
        assertThat(앵글밸브.getQuantity()).as("앵글밸브 ×2").isEqualTo(2);
    }

    @Test
    void 반복_행이_있어도_관계는_한_건만_생긴다() {
        ensureLoaded();

        // 접힘 자체는 유지된다 — 수량으로 표현할 뿐 관계 행이 늘지 않는다.
        assertThat(partsOf("S132E").stream().filter(r -> "수채가량".equals(r.getRelationType())).count())
                .as("수채가량 관계 행 수").isEqualTo(1);
    }

    @Test
    void 수량을_세면_구성합이_세트가와_정확히_일치한다() {
        ensureLoaded();

        assertThat(partsTotal("S132E")).as("S132E").isEqualByComparingTo(setPrice("S132E"));
        assertThat(partsTotal("L352E")).as("L352E").isEqualByComparingTo(setPrice("L352E"));
        assertThat(partsTotal("L352E-2")).as("L352E-2").isEqualByComparingTo(setPrice("L352E-2"));
    }

    @Test
    void 반복이_없는_세트는_수량이_1이다() {
        ensureLoaded();

        // S121E(수채 1구형)는 같은 시트에서 수채가량이 1행뿐이다(58행).
        assertThat(partsOf("S121E")).allSatisfy(r ->
                assertThat(r.getQuantity()).as(r.getRelationType()).isEqualTo(1));
    }

    @Test
    void 재적재해도_수량이_유지된다() {
        ensureLoaded();

        long relations1 = relationRepository.count();
        service.importVendorCatalog("B", BOOK);

        assertThat(relationRepository.count()).as("관계 행 수 불변").isEqualTo(relations1);
        assertThat(partsOf("S132E").stream()
                .filter(r -> "수채가량".equals(r.getRelationType()))
                .findFirst().orElseThrow().getQuantity()).isEqualTo(2);
    }
}
