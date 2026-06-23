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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P4 검증: B사 샘플을 실제 DB(인메모리 Derby)에 적재 → 대표품목/부속/관계/가격이 들어가고,
 * 재업로드 시 코드 기준 upsert로 멱등(중복 행 없음)인지 확인한다.
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:p4test;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
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
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);

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

        // 3) 재업로드 → 멱등(행 수 불변)
        int sets2 = service.importVendorCatalog("B", SAMPLE);
        assertThat(sets2).isEqualTo(sets1);
        assertThat(productRepository.count()).as("재업로드 후 제품 수 불변").isEqualTo(products1);
        assertThat(priceRepository.count()).as("재업로드 후 가격 수 불변").isEqualTo(prices1);
        assertThat(relationRepository.count()).as("재업로드 후 관계 수 불변").isEqualTo(relations1);
    }
}
