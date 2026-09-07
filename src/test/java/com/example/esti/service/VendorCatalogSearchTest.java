package com.example.esti.service;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카탈로그 화면 검색 (F-015).
 *
 * <p>QA(S2)의 «검색 — 품번, 제품명, 없는 값» 항목은 <b>기능이 없어 실행 불가</b>로 닫혔었다.
 * 2,247건을 10건씩 넘기면 225페이지라, 원하는 항목을 지목할 방법이 페이지 넘김뿐이었다.
 *
 * <p><b>서버에서 찾는다.</b> 화면이 서버 페이징이라 클라이언트에서 거르면 지금 보이는 한 페이지만
 * 뒤지게 된다 — 이 테스트가 지키려는 것이 그 점이다. 페이지 크기를 1로 두고도 뒤쪽 페이지에 있는
 * 항목이 걸리는지 본다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:catalogSearchTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class VendorCatalogSearchTest {

    @Autowired private VendorCatalogQueryService service;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;

    private static final Pageable FIRST = PageRequest.of(0, 50);

    @BeforeEach
    void seed() {
        priceRepository.deleteAll();
        productRepository.deleteAll();
        vendorRepository.deleteAll();

        Vendor a = vendor("A", "A사");
        Vendor b = vendor("B", "B사");

        // (공급사, 품번, 제품명, 대분류, 소분류, 규격, 비고, 구품번)
        row(a, "AC8100", "원피스양변기", "양변기", "원피스양변기", null, null, "OLD-1");
        row(a, "IL672", "반다리세면기", "세면기", "반다리세면기", "500mm", "단종", null);
        row(a, "FA1701", "1H세면수전", "수전", "세면수전", null, null, null);
        row(b, "G-0130", "벽붙이주방수전", "주방수전", "벽붙이주방수전", "50%할인", "규격 A_B", null);
    }

    // ====== 무엇으로 찾히나 ======

    @Test
    void 품번으로_찾는다() {
        assertThat(codes(search("A", "AC8100"))).containsExactly("AC8100");
    }

    @Test
    void 구품번으로도_찾는다() {
        assertThat(codes(search("A", "OLD-1"))).containsExactly("AC8100");
    }

    @Test
    void 제품명으로_찾는다() {
        assertThat(codes(search("A", "반다리"))).containsExactly("IL672");
    }

    @Test
    void 분류로_찾는다() {
        // 대분류 '수전'이 걸린다 — 제품명에 '수전'이 없어도 분류가 맞으면 나와야 한다
        assertThat(codes(search("A", "수전"))).containsExactly("FA1701");
    }

    @Test
    void 규격과_비고로도_찾는다() {
        assertThat(codes(search("A", "500mm"))).containsExactly("IL672");
        assertThat(codes(search("A", "단종"))).containsExactly("IL672");
    }

    @Test
    void 대소문자를_가리지_않는다() {
        assertThat(codes(search("A", "ac8100"))).containsExactly("AC8100");
        assertThat(codes(search("A", "Il672"))).containsExactly("IL672");
    }

    // ====== 어떻게 동작하나 ======

    @Test
    void 없는_값은_빈_결과다() {
        Page<VendorCatalogView> page = search("A", "없는품번XYZ");

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void 검색어가_비면_전건을_돌려준다() {
        assertThat(search("A", null).getTotalElements()).isEqualTo(3);
        assertThat(search("A", "   ").getTotalElements()).isEqualTo(3);
    }

    @Test
    void 공급사_범위를_넘지_않는다() {
        // '수전'은 A사 1건 · B사 1건이지만, 공급사를 지정하면 그 안에서만 찾는다
        assertThat(codes(search("A", "수전"))).containsExactly("FA1701");
        assertThat(codes(search("B", "수전"))).containsExactly("G-0130");
        assertThat(codes(service.getVendorCatalogPageAll("수전", FIRST)))
                .containsExactlyInAnyOrder("FA1701", "G-0130");
    }

    @Test
    void 페이지에_안_보이는_항목도_찾힌다() {
        // 페이지 크기를 1로 줄여도 결과가 나와야 한다.
        // 클라이언트에서 걸렀다면 «현재 페이지 1건» 안에서만 찾아 못 찾는다 — 그게 F-015의 본질이다.
        Page<VendorCatalogView> page =
                service.getVendorCatalogPage("A", "반다리", PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(codes(page)).containsExactly("IL672");
    }

    @Test
    void 와일드카드는_글자로_취급한다() {
        // '%'를 패턴으로 흘리면 전건이 걸린다. 사용자가 친 '%'는 찾으려는 글자다.
        assertThat(codes(service.getVendorCatalogPageAll("%", FIRST)))
                .as("'%'는 그 글자가 든 행만").containsExactly("G-0130");
        assertThat(codes(service.getVendorCatalogPageAll("50%", FIRST)))
                .containsExactly("G-0130");

        // '_'는 아무 글자나 무는 패턴이다. 그대로 두면 'A_B'가 'AxB'에도 걸린다.
        assertThat(codes(service.getVendorCatalogPageAll("A_B", FIRST)))
                .as("'_'도 글자로").containsExactly("G-0130");
        assertThat(service.getVendorCatalogPageAll("A1B", FIRST).getTotalElements())
                .as("'_'가 패턴이었다면 여기서 걸렸을 것").isZero();
    }

    // ====== 도우미 ======

    private Page<VendorCatalogView> search(String vendorCode, String keyword) {
        return service.getVendorCatalogPage(vendorCode, keyword, FIRST);
    }

    private List<String> codes(Page<VendorCatalogView> page) {
        return page.getContent().stream().map(VendorCatalogView::mainItemCode).toList();
    }

    private Vendor vendor(String code, String name) {
        Vendor v = new Vendor();
        v.setVendorCode(code);
        v.setVendorName(name);
        return vendorRepository.save(v);
    }

    private void row(Vendor vendor, String code, String name, String large, String small,
                     String specs, String remark, String oldCode) {
        VendorProduct p = new VendorProduct();
        p.setVendor(vendor);
        p.setProductCode(code);
        p.setProductName(name);
        p.setCategoryLarge(large);
        p.setCategorySmall(small);
        p.setSpecs(specs);
        p.setItemType("SET");
        productRepository.save(p);

        VendorItemPrice vip = new VendorItemPrice();
        vip.setVendor(vendor);
        vip.setVendorProduct(p);
        vip.setMainItemCode(code);
        vip.setProposalItemCode(code);
        vip.setOldItemCode(oldCode);
        vip.setVendorItemName(name);
        vip.setRemark(remark);
        vip.setUnitPrice(BigDecimal.ONE);
        vip.setPriceType("SET");
        vip.setPriceBasis(large);
        vip.setCurrency("KRW");
        priceRepository.save(vip);
    }
}
