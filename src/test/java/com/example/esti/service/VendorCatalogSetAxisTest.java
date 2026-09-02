package com.example.esti.service;

import com.example.esti.dto.VendorProductPartView;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.excel.ExcelImageExtractor;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorParsedItem;
import com.example.esti.excel.VendorProductSet;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 세트 축(G-1, 부속 구성 해시) 검증 — {@code docs/plan-a-set-parts.md} Task 4.
 *
 * <p><b>고치기 전 상태.</b> 관계와 대표품목 가격행이 <b>품번 단위</b>로 접혔다. 같은 품번이 여러 세트의
 * 대표품목이면 그 세트들의 부속이 한 제품에 전부 누적돼 <b>택1 부속이 동시에 보이고</b>, 세트가는
 * 마지막 것 하나만 남았다. A사 실측 — 22종에서 원본 48세트가 22행으로 접히고 세트가 24개가 덮였다.
 *
 * <p>여기서는 그 구조를 최소 형태로 재현한다. 파서를 스텁으로 갈아끼우므로 엑셀 샘플이 필요 없고
 * <b>항상 실행된다.</b>
 *
 * <pre>
 *   같은 대표품목 M ─┬─ 세트①(부속 A) 세트가 100
 *                    └─ 세트②(부속 B) 세트가 200      ← 실제로는 택1
 *
 *   기대: 가격행 2개(100·200) / ①을 펼치면 A만, ②를 펼치면 B만
 *   차단 전: 가격행 1개(200만 남음) / 어느 쪽을 펼쳐도 A·B가 함께
 * </pre>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:setaxistest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class VendorCatalogSetAxisTest {

    private static final Path DUMMY = Path.of("target", "set-axis-dummy.xlsx");
    private static final String MAIN_CODE = "MAIN-1";

    @MockitoBean private VendorExcelParserFactory parserFactory;
    @MockitoBean private ExcelImageExtractor imageExtractor;

    @Autowired private VendorCatalogImporter importer;
    @Autowired private VendorCatalogQueryService queryService;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    @BeforeEach
    void reset() {
        relationRepository.deleteAll();
        priceRepository.deleteAll();
        productRepository.deleteAll();
        vendorRepository.deleteAll();
        given(imageExtractor.extract(any())).willReturn(Map.of());
    }

    // ====== 세트 해시 자체 ======

    @Test
    void 부속_구성이_다르면_세트_해시가_다르다() {
        assertThat(set("100", part("P-A", "긴다리")).setHash())
                .isNotEqualTo(set("100", part("P-B", "반다리")).setHash());
    }

    @Test
    void 부속_순서가_달라도_같은_세트다() {
        // 엑셀 행 순서가 바뀌었다고 다른 세트가 되면 재적재마다 정체성이 흔들린다.
        assertThat(set("100", part("P-A", "긴다리"), part("P-B", "앙카")).setHash())
                .isEqualTo(set("100", part("P-B", "앙카"), part("P-A", "긴다리")).setHash());
    }

    @Test
    void 세트가가_달라도_구성이_같으면_같은_세트다() {
        // 구성이 같은데 세트가만 다른 경우는 A사 실측에서 0건이었다(접어도 손실 없음).
        assertThat(set("100", part("P-A", "긴다리")).setHash())
                .isEqualTo(set("999", part("P-A", "긴다리")).setHash());
    }

    // ====== 적재 ======

    @Test
    void 같은_대표품목의_서로_다른_세트가_각자의_가격행을_갖는다() {
        importTwoSets();

        Vendor vendor = vendorRepository.findByVendorCode("A").orElseThrow();
        VendorProduct main = productRepository.findByVendorAndProductCode(vendor, MAIN_CODE).orElseThrow();

        List<VendorItemPrice> setRows = priceRepository
                .findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
                        vendor, main, VendorItemPrice.PRICE_TYPE_SET, "세면기");

        assertThat(setRows)
                .as("세트 축 전에는 1행으로 접혀 세트가가 하나만 남았다")
                .hasSize(2);
        assertThat(setRows).extracting(r -> r.getUnitPrice().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("100", "200");
        assertThat(setRows).allSatisfy(r -> assertThat(r.getSetHash()).isNotBlank());
    }

    @Test
    void 세트를_펼치면_그_세트의_부속만_나온다() {
        importTwoSets();

        Vendor vendor = vendorRepository.findByVendorCode("A").orElseThrow();
        VendorProduct main = productRepository.findByVendorAndProductCode(vendor, MAIN_CODE).orElseThrow();
        List<VendorItemPrice> setRows = priceRepository
                .findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
                        vendor, main, VendorItemPrice.PRICE_TYPE_SET, "세면기");

        for (VendorItemPrice row : setRows) {
            List<VendorProductPartView> parts = queryService.getParts(row.getId()).orElseThrow();
            assertThat(parts)
                    .as("세트 축 전에는 두 세트의 부속이 함께 나왔다(택1이 동시 노출)")
                    .hasSize(1);
            String expected = "100".equals(row.getUnitPrice().stripTrailingZeros().toPlainString())
                    ? "긴다리" : "반다리";
            assertThat(parts.get(0).productName()).isEqualTo(expected);
        }
    }

    @Test
    void 재적재해도_행이_늘지_않는다() {
        importTwoSets();
        long pricesAfterFirst = priceRepository.count();
        long relationsAfterFirst = relationRepository.count();

        importTwoSets(); // 같은 파일을 한 번 더

        assertThat(priceRepository.count()).as("가격행 멱등").isEqualTo(pricesAfterFirst);
        assertThat(relationRepository.count()).as("관계 멱등").isEqualTo(relationsAfterFirst);
    }

    @Test
    void 세트_구성이_바뀌면_낡은_관계가_남지_않는다() {
        // 임포터에 delete가 없어 낡은 부속 연결이 쌓이던 문제(Task 3 흡수).
        given(parserFactory.getParser("A")).willReturn(parserReturning(List.of(
                set("100", part("P-A", "긴다리")))));
        importer.importVendorCatalog("A", DUMMY, null);

        given(parserFactory.getParser("A")).willReturn(parserReturning(List.of(
                set("100", part("P-C", "새다리")))));   // 구성이 바뀐 재적재
        importer.importVendorCatalog("A", DUMMY, null);

        Vendor vendor = vendorRepository.findByVendorCode("A").orElseThrow();
        VendorProduct main = productRepository.findByVendorAndProductCode(vendor, MAIN_CODE).orElseThrow();

        assertThat(relationRepository.findAllBySourceProduct(main))
                .as("낡은 구성(긴다리)의 관계가 남으면 안 된다")
                .hasSize(1);
        assertThat(priceRepository.findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
                vendor, main, VendorItemPrice.PRICE_TYPE_SET, "세면기"))
                .as("낡은 세트의 가격행도 함께 걷힌다")
                .hasSize(1);
    }

    @Test
    void 세트가까지_같으면_구성_요약만이_행을_가른다() {
        // 목록에서 가장 어려운 경우 — A사 상업용 소변기(센서 배터리/전기식)가 이 모양이다.
        // 품번·소분류·세트가가 전부 같고 부속만 다르다. 요약이 없으면 화면에서 구별할 수단이 없다.
        given(parserFactory.getParser("A")).willReturn(parserReturning(List.of(
                set("100", part("S-BAT", "내장형센서(배터리)")),
                set("100", part("S-ELE", "내장형센서(전기식)")))));
        importer.importVendorCatalog("A", DUMMY, null);

        Vendor vendor = vendorRepository.findByVendorCode("A").orElseThrow();
        VendorProduct main = productRepository.findByVendorAndProductCode(vendor, MAIN_CODE).orElseThrow();

        List<VendorItemPrice> rows = priceRepository
                .findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
                        vendor, main, VendorItemPrice.PRICE_TYPE_SET, "세면기");

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(VendorItemPrice::getSetSummary)
                .as("세트가가 같으니 요약이 유일한 구분 수단이다")
                .containsExactlyInAnyOrder("내장형센서(배터리)", "내장형센서(전기식)");
    }

    @Test
    void 구성_요약은_부속이_많으면_접는다() {
        VendorProductSet many = set("100",
                part("P1", "가"), part("P2", "나"), part("P3", "다"), part("P4", "라"), part("P5", "마"));
        assertThat(many.partsSummary()).isEqualTo("가 · 나 · 다 외 2건");
    }

    @Test
    void 부속이_없으면_구성_요약이_없다() {
        assertThat(set("100").partsSummary()).isNull();
    }

    @Test
    void 구성_요약은_엑셀_순서를_따른다() {
        // 정체성(setHash)은 순서에 흔들리면 안 되지만, 표시는 드릴다운과 같은 순서여야 한다.
        assertThat(set("100", part("P-B", "나중"), part("P-A", "먼저")).partsSummary())
                .isEqualTo("나중 · 먼저");
    }

    // ====== 헬퍼 ======

    private void importTwoSets() {
        given(parserFactory.getParser("A")).willReturn(parserReturning(List.of(
                set("100", part("P-A", "긴다리")),
                set("200", part("P-B", "반다리")))));
        importer.importVendorCatalog("A", DUMMY, null);
    }

    private static VendorParsedItem part(String code, String name) {
        return new VendorParsedItem(code, name, null, null,
                VendorParsedItem.RELATION_ACCESSORY, new BigDecimal("10"), null);
    }

    private static VendorProductSet set(String setPrice, VendorParsedItem... parts) {
        VendorParsedItem main = new VendorParsedItem(MAIN_CODE, "세면기", null, null,
                VendorParsedItem.RELATION_MAIN, new BigDecimal("50"), null);
        return new VendorProductSet("A", "세면기", "반다리세면기", main, List.of(parts),
                new BigDecimal(setPrice), false, null, false);
    }

    private static VendorExcelParser parserReturning(List<VendorProductSet> sets) {
        return new VendorExcelParser() {
            @Override public String getVendorCode() { return "A"; }
            @Override public List<VendorProductSet> parseSets(Path path) { return sets; }
        };
    }
}
