package com.example.esti.service;

import com.example.esti.dto.VendorProductPartView;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import com.example.esti.excel.VendorParsedItem;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부속 구성 드릴다운({@code GET /api/vendor-catalog/{id}/parts})이 <b>부속 가격행에서는 열리지 않는지</b>
 * 확인한다 — {@code docs/analysis-a-set-parts.md} §4·§10-1.
 *
 * <p><b>왜 생겼나.</b> {@code VendorProductRelation}이 (제품 → 제품)이라 가격행의 역할을 구분하지 못한다.
 * A사 원본은 같은 품번을 어떤 세트에선 부속으로, 다른 자리에선 대표품목으로 적는다
 * ({@code 폽업}·{@code 양변기변좌}·{@code 플랙쉬플호스} 등 7건). 그러면 제품 하나에 관계가 붙고,
 * <b>부속으로 올라온 가격행을 눌러도 그 관계가 그대로 나왔다</b> — "부속을 눌렀는데 부속이 또 나온다".
 *
 * <p>이 테스트는 그 구조를 최소 형태로 재현한다. 실샘플과 무관하게 <b>항상 실행된다.</b>
 *
 * <pre>
 *   세면수전 ──ACCESSORY──▶ 폽업 ──ACCESSORY──▶ P트랩
 *                            ▲ 부속이면서 동시에 제 세트의 대표품목이다
 *
 *   가격행 ① priceType=SET  · 세면수전 → 부속 1건(폽업)이 나와야 한다
 *   가격행 ② priceType=PART · 폽업     → 빈 목록이어야 한다   ← 차단 대상
 *                                        (차단 전에는 P트랩이 나왔다)
 * </pre>
 *
 * <p><b>폽업이 관계의 target이기만 하면 이 테스트는 무력하다</b> — {@code findAllBySourceProduct}가
 * 어차피 비기 때문이다. 차단을 꺼도 통과해 버린다. 그래서 폽업에 <b>자신이 source인 관계</b>를
 * 하나 붙여 실제 조건을 만든다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:partsquerytest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class VendorCatalogPartsQueryTest {

    @Autowired private VendorCatalogQueryService queryService;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    private Long setPriceId;
    private Long partPriceId;

    @BeforeEach
    void setUp() {
        relationRepository.deleteAll();
        priceRepository.deleteAll();
        productRepository.deleteAll();
        vendorRepository.deleteAll();

        Vendor vendor = new Vendor();
        vendor.setVendorCode("A");
        vendor.setVendorName("테스트공급사");
        vendor = vendorRepository.save(vendor);

        VendorProduct main = product(vendor, "FA0000-TEST", "3H세면수전", VendorItemPrice.PRICE_TYPE_SET);
        // 폽업은 세트의 부속이면서, 부속 대분류에서 제 가격행도 갖는다 — A사 원본의 실제 형태다.
        VendorProduct part = product(vendor, "FJ0000-TEST", "폽업", VendorItemPrice.PRICE_TYPE_PART);
        VendorProduct grandChild = product(vendor, "FJ8302-TEST", "P트랩", VendorItemPrice.PRICE_TYPE_PART);

        relate(main, part);
        // 핵심 — 폽업 자신이 source인 관계. 이게 없으면 차단을 꺼도 테스트가 통과해 버린다.
        relate(part, grandChild);

        setPriceId = price(vendor, main, VendorItemPrice.PRICE_TYPE_SET, "세면수전", new BigDecimal("100"));
        partPriceId = price(vendor, part, VendorItemPrice.PRICE_TYPE_PART, null, new BigDecimal("10"));
        price(vendor, grandChild, VendorItemPrice.PRICE_TYPE_PART, null, new BigDecimal("5"));
    }

    @Test
    void 세트_가격행은_부속_구성을_보여준다() {
        Optional<List<VendorProductPartView>> parts = queryService.getParts(setPriceId);

        assertThat(parts).isPresent();
        assertThat(parts.get()).extracting(VendorProductPartView::productName)
                .containsExactly("폽업");
    }

    @Test
    void 부속_가격행은_구성을_펼치지_않는다() {
        Optional<List<VendorProductPartView>> parts = queryService.getParts(partPriceId);

        // 빈 목록이어야 한다. Optional.empty()(=404)가 아니다 — 행은 존재하고 구성만 없다.
        assertThat(parts).isPresent();
        assertThat(parts.get())
                .as("부속 가격행에는 구성이 없다. 차단 전에는 폽업이 대표품목인 세트의 부속(P트랩)이 나왔다")
                .isEmpty();
    }

    @Test
    void 없는_가격행은_빈_목록이_아니라_조회_실패다() {
        // "부속 없음"과 "조회 실패"의 구분이 이 차단으로 흐려지지 않아야 한다(컨트롤러가 404로 가른다).
        assertThat(queryService.getParts(-1L)).isEmpty();
    }

    // ====== fixture ======

    private void relate(VendorProduct source, VendorProduct target) {
        relationRepository.save(VendorProductRelation.builder()
                .sourceProduct(source)
                .targetProduct(target)
                .relationType(VendorParsedItem.RELATION_ACCESSORY)
                .quantity(1)
                .build());
    }

    private VendorProduct product(Vendor vendor, String code, String name, String itemType) {
        VendorProduct p = new VendorProduct();
        p.setVendor(vendor);
        p.setProductCode(code);
        p.setProductName(name);
        p.setItemType(itemType);
        return productRepository.save(p);
    }

    private Long price(Vendor vendor, VendorProduct product, String priceType,
                       String priceBasis, BigDecimal unitPrice) {
        VendorItemPrice vip = new VendorItemPrice();
        vip.setVendor(vendor);
        vip.setVendorProduct(product);
        vip.setProposalItemCode(product.getProductCode());
        vip.setVendorItemName(product.getProductName());
        vip.setUnitPrice(unitPrice);
        vip.setPriceType(priceType);
        vip.setPriceBasis(priceBasis);
        vip.setCurrency("KRW");
        return priceRepository.save(vip).getId();
    }
}
