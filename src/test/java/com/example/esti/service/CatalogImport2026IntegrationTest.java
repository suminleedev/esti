package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
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

import static com.example.esti.support.TestSamples.requireSample;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T9 — 최신본(2026, 14시트)을 실제 DB(인메모리 Derby)에 적재하고 재업로드 멱등을 확인한다(R8).
 *
 * <p>시트별 테스트는 파싱까지만 본다. 여기서는 적재 단계에서만 드러나는 것을 본다 —
 * 코드가 겹쳐 제품이 병합되는지, 관계가 중복으로 쌓이는지, 재적재가 행 수를 늘리는지.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:b2026test;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images-2026"
})
class CatalogImport2026IntegrationTest {

    private static final Path BOOK = Path.of("docs/samples/B사 단가표_2026최신.xlsx");

    @Autowired private CatalogImportAsyncService service;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    @Test
    void 최신본_적재_후_재업로드가_멱등이다() {
        requireSample(BOOK);

        int sets1 = service.importVendorCatalog("B", BOOK);
        assertThat(sets1).as("파싱된 세트/제품 수").isEqualTo(763); // §8 잔여 ⑦로 바스 중복 4건 제외

        long products1 = productRepository.count();
        long prices1 = priceRepository.count();
        long relations1 = relationRepository.count();
        assertThat(products1).isGreaterThan(0);
        assertThat(prices1).isGreaterThan(0);
        assertThat(relations1).isGreaterThan(0);

        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();

        // ── 스폿 체크: 시트마다 대표 1건씩 ──────────────────────────────
        // 양변기(T1) — 세로 나열형 세트
        VendorProduct toilet = productRepository.findByVendorAndProductCode(b, "IC552EF").orElseThrow();
        assertThat(relationRepository.findAllBySourceProduct(toilet))
                .as("도기+부속 4건").hasSize(5);

        // 세면기(T2) — 택일 항목까지 구성으로 보존
        assertThat(productRepository.findByVendorAndProductCode(b, "IL610")).isPresent();
        // 소변기·수채(T3) — 대분류가 시트명과 다르다
        assertThat(productRepository.findByVendorAndProductCode(b, "S131E").orElseThrow()
                .getCategoryLarge()).isEqualTo("수채");
        // 액세사리(T4) — 품수 경계로 옷걸이를 세트에서 뺐다
        VendorProduct accSet = productRepository.findByVendorAndProductCode(b, "AC8300G").orElseThrow();
        assertThat(relationRepository.findAllBySourceProduct(accSet)).hasSize(4);
        assertThat(productRepository.findByVendorAndProductCode(b, "AC8305G")).isPresent();
        // 부속류(T5) — 구·신 전산코드가 따로 남는다
        assertThat(productRepository.findByVendorAndProductCode(b, "<CODE>")).isPresent();
        assertThat(productRepository.findByVendorAndProductCode(b, "<CODE>")).isPresent();
        // 수전금구(T6) — 전산코드를 보조 코드로 보존
        assertThat(productRepository.findByVendorAndProductCode(b, "G-0110")).isPresent();
        // 바스(T8) — 신규 카테고리
        assertThat(productRepository.findByVendorAndProductCode(b, "<CODE>").orElseThrow()
                .getCategoryLarge()).isEqualTo("바스");

        // 사라진 시트의 잔재가 새로 들어오지 않는다
        assertThat(productRepository.findByVendorAndProductCode(b, "MC921"))
                .as("구본 양변기 품번은 최신본에 없다").isEmpty();

        // ── 재업로드 멱등(R8) ─────────────────────────────────────────
        int sets2 = service.importVendorCatalog("B", BOOK);
        assertThat(sets2).isEqualTo(sets1);
        assertThat(productRepository.count()).as("제품 행 수 불변").isEqualTo(products1);
        assertThat(priceRepository.count()).as("가격 행 수 불변").isEqualTo(prices1);
        assertThat(relationRepository.count()).as("관계 행 수 불변").isEqualTo(relations1);
    }

    @Test
    void 세트가와_부속_단가가_모두_들어간다() {
        requireSample(BOOK);
        service.importVendorCatalog("B", BOOK);

        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();
        VendorProduct toilet = productRepository.findByVendorAndProductCode(b, "IC552EF").orElseThrow();

        BigDecimal setPrice = priceRepository.findFirstByVendorAndVendorProduct(b, toilet)
                .orElseThrow().getUnitPrice();
        assertThat(setPrice).isEqualByComparingTo("<PRICE>");

        BigDecimal partSum = relationRepository.findAllBySourceProduct(toilet).stream()
                .map(rel -> priceRepository.findFirstByVendorAndVendorProduct(b, rel.getTargetProduct())
                        .map(p -> p.getUnitPrice()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(partSum).as("計 = 구성 단가 합").isEqualByComparingTo(setPrice);
    }
}
