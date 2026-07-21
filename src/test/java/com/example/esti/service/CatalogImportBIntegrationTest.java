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

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * P4 검증: B사 샘플을 실제 DB(인메모리 Derby)에 적재 → 대표품목/부속/관계/가격이 들어가고,
 * 재업로드 시 코드 기준 upsert로 멱등(중복 행 없음)인지 확인한다.
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:p4test;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // 테스트는 임시 디렉터리에 이미지 저장(실제 ./uploads 오염 방지)
        "app.crawler.image-dir=target/test-product-images"
})
class CatalogImportBIntegrationTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    @Autowired private CatalogImportAsyncService service;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    @Test
    void B사_적재_후_관계_가격_저장되고_재업로드는_멱등() {
        requireSample(SAMPLE);

        // 1) 최초 적재
        int sets1 = service.importVendorCatalog("B", SAMPLE);
        assertThat(sets1).isGreaterThan(0);

        long products1 = productRepository.count();
        long prices1 = priceRepository.count();
        long relations1 = relationRepository.count();
        assertThat(products1).as("대표품목+부속 적재").isGreaterThan(0);
        assertThat(prices1).as("가격 적재").isGreaterThan(0);
        assertThat(relations1).as("구성 관계 적재").isGreaterThan(0);

        // 2) 스폿 체크 — 양변기 MC921 세트(도기+F/V+스퍼드 = 計 77200)
        Vendor b = vendorRepository.findByVendorCode("B").orElseThrow();
        VendorProduct mc921 = productRepository.findByVendorAndProductCode(b, "MC921")
                .orElseThrow(() -> new AssertionError("MC921 대표품목 미적재"));
        assertThat(mc921.getItemType()).isEqualTo("SET");
        assertThat(relationRepository.findAllBySourceProduct(mc921))
                .as("MC921 부속 관계 3건").hasSize(3);
        assertThat(priceRepository.findByVendorAndVendorProductAndProposalItemCode(b, mc921, "MC921"))
                .get()
                .satisfies(p -> assertThat(p.getUnitPrice()).isEqualByComparingTo(new BigDecimal("77200")));
        // 임베디드 이미지 연결(D15)
        assertThat(mc921.getImageUrl()).as("MC921 이미지 연결")
                .isNotNull().startsWith("/uploads/product-images/");

        // 코드 충돌 분리(P6): G-0110이 수전금구(수입,31000)·국산부속(소계,54400) 두 가격으로 분리 보존
        VendorProduct g0110 = productRepository.findByVendorAndProductCode(b, "G-0110")
                .orElseThrow(() -> new AssertionError("G-0110 미적재"));
        assertThat(priceRepository
                .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(b, g0110, "G-0110", "수전금구"))
                .as("수입 부속 기준 대리점가").get()
                .satisfies(p -> assertThat(p.getUnitPrice()).isEqualByComparingTo(new BigDecimal("31000")));
        assertThat(priceRepository
                .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(b, g0110, "G-0110", "수전금구(국산 부속 기준)"))
                .as("국산 부속 기준 소계").get()
                .satisfies(p -> assertThat(p.getUnitPrice()).isEqualByComparingTo(new BigDecimal("54400")));

        // 3) 재업로드 → 멱등(행 수 불변)
        int sets2 = service.importVendorCatalog("B", SAMPLE);
        assertThat(sets2).isEqualTo(sets1);
        assertThat(productRepository.count()).as("재업로드 후 제품 수 불변").isEqualTo(products1);
        assertThat(priceRepository.count()).as("재업로드 후 가격 수 불변").isEqualTo(prices1);
        assertThat(relationRepository.count()).as("재업로드 후 관계 수 불변").isEqualTo(relations1);
    }
}
