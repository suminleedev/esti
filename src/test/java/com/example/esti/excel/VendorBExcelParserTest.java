package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P3 검증: B사 파서가 시트 양식 패밀리별로 대표품목 + 부속 + 관계를 정확히 묶는지.
 * 샘플(docs/samples/...)은 git 추적 제외이므로, 파일이 없으면 테스트를 스킵한다.
 */
class VendorBExcelParserTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parseSample() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private Optional<VendorProductSet> findByMainCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst();
    }

    @Test
    void 양변기_슬롯세트는_計와_부속합이_일치하고_도기는_MAIN관계() {
        List<VendorProductSet> sets = parseSample();

        // MC921: 도기 35200 + F/V 35000 + 스퍼드 7000 = 計 77200
        VendorProductSet mc921 = findByMainCode(sets, "MC921")
                .orElseThrow(() -> new AssertionError("MC921 대표품목 미발견"));

        assertEquals(0, new BigDecimal("77200").compareTo(mc921.setPrice()), "計=부속합");
        assertEquals(3, mc921.parts().size(), "부속 3개(도기/F/V/스퍼드)");
        assertFalse(mc921.selectable());

        BigDecimal partSum = mc921.parts().stream()
                .map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, mc921.setPrice().compareTo(partSum), "부속 단가 합 == 세트가");

        // 도기 슬롯 = MAIN 관계
        assertTrue(mc921.parts().stream()
                .anyMatch(p -> p.productName().startsWith("도기")
                        && VendorParsedItem.RELATION_MAIN.equals(p.relationType())));
        // 나머지 슬롯은 슬롯 라벨이 relationType
        assertTrue(mc921.parts().stream()
                .anyMatch(p -> "F/V".equals(p.relationType())));
    }

    @Test
    void 세면기는_기본구성_도기원홀_반다리로_세트가_산정() {
        List<VendorProductSet> sets = parseSample();

        // IL451B: 도기(원홀) 65000 + 반다리 4000 = 69000 (도기 4"는 대체옵션, 세트가 제외)
        VendorProductSet il451 = findByMainCode(sets, "IL451B")
                .orElseThrow(() -> new AssertionError("IL451B 대표품목 미발견"));

        assertFalse(il451.selectable(), "세면기도 기본구성으로 세트가 산정");
        assertEquals(0, new BigDecimal("69000").compareTo(il451.setPrice()), "도기원홀+반다리=69000");

        // 기본 도기(원홀)=MAIN, 대체 도기(4")는 remark=대체옵션
        assertTrue(il451.parts().stream().anyMatch(p ->
                p.productName().contains("원홀") && VendorParsedItem.RELATION_MAIN.equals(p.relationType())));
        assertTrue(il451.parts().stream().anyMatch(p ->
                p.productName().contains("4") && "대체옵션".equals(p.remark())));

        // 부속 품번 = 대표품번_부속코드 복합코드(A사 방식)
        assertTrue(il451.parts().stream().allMatch(p -> p.productCode().startsWith("IL451B_")));
    }

    @Test
    void 갈라시아_4행형은_도기와_부속을_묶고_합계가_세트가() {
        List<VendorProductSet> sets = parseSample();

        // art6103: 도기 367000 + 부속 63000 = 430000
        VendorProductSet art = findByMainCode(sets, "art6103")
                .orElseThrow(() -> new AssertionError("art6103 대표품목 미발견"));

        assertEquals(0, new BigDecimal("430000").compareTo(art.setPrice()));
        assertEquals(2, art.parts().size());
        assertTrue(art.parts().stream()
                .anyMatch(p -> VendorParsedItem.RELATION_MAIN.equals(p.relationType())));
    }

    @Test
    void 악세사리_소계세트는_부속합이_세트가와_일치() {
        List<VendorProductSet> sets = parseSample();

        // AC8100 4품 세트 = 60000 = 20500+17500+11000+11000
        VendorProductSet ac = findByMainCode(sets, "AC8100")
                .orElseThrow(() -> new AssertionError("AC8100 세트 미발견"));

        assertEquals(0, new BigDecimal("60000").compareTo(ac.setPrice()));
        assertEquals(4, ac.parts().size(), "4품 부속");

        BigDecimal partSum = ac.parts().stream()
                .map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, ac.setPrice().compareTo(partSum));
    }

    @Test
    void 단일행형_비데는_부속없이_대리점가로_저장() {
        List<VendorProductSet> sets = parseSample();

        VendorProductSet bidet = findByMainCode(sets, "DSB-5420")
                .orElseThrow(() -> new AssertionError("DSB-5420 미발견"));

        assertTrue(bidet.parts().isEmpty(), "단일행은 부속 없음");
        assertEquals(0, new BigDecimal("120000").compareTo(bidet.setPrice()));
    }

    @Test
    void 악세사리_A빈_세트도_G_SET_기준으로_세트화_되고_단일품은_독립저장() {
        List<VendorProductSet> sets = parseSample();

        // AC8300G: A열이 비었지만 G="SET"인 진짜 5품 세트 (이전엔 윗 세트 부속으로 흡수됨)
        VendorProductSet ac8300 = findByMainCode(sets, "AC8300G")
                .orElseThrow(() -> new AssertionError("AC8300G 세트 미발견"));
        assertEquals(0, new BigDecimal("85000").compareTo(ac8300.setPrice()));
        assertEquals(5, ac8300.parts().size(), "AC8300G는 5품 세트");

        // 단일품 구간(AT0111S)은 부속 없는 독립 제품으로 저장
        VendorProductSet single = findByMainCode(sets, "AT0111S")
                .orElseThrow(() -> new AssertionError("AT0111S 단일품 미발견"));
        assertTrue(single.parts().isEmpty(), "단일품은 부속 없음");
        assertEquals(0, new BigDecimal("4800").compareTo(single.setPrice()));
    }

    @Test
    void 수전부속은_대분류_수전부속과_시트명basis로_적재되고_냉온블록은_합성세트() {
        List<VendorProductSet> sets = parseSample();

        // "U9111 (직각...)"은 품번패턴 → 코드=U9111(대문자 정규화, P9), 나머지 B열이 description
        VendorProductSet u9111 = findByMainCode(sets, "U9111")
                .orElseThrow(() -> new AssertionError("U9111 미발견"));
        assertEquals("(직각 노브형-i0110용)", u9111.main().description(), "첫 토큰 뺀 나머지 B");
        assertEquals("수전부속", u9111.categoryLarge(), "대분류=수전부속(§11 P5)");
        assertEquals("수전 부속(세트)", u9111.priceBasis(), "가격은 시트명 basis");

        // 냉/온+소계 블록(U9013c/h) → 합성 세트 U9013(main) + 부속 U9013_c/h (§11 P6)
        VendorProductSet u9013 = findByMainCode(sets, "U9013")
                .orElseThrow(() -> new AssertionError("U9013 합성 세트 미발견"));
        assertEquals(0, new BigDecimal("10000").compareTo(u9013.setPrice()), "세트가=소계");
        assertEquals(2, u9013.parts().size());
        assertTrue(u9013.parts().stream().anyMatch(p -> "U9013_c".equals(p.productCode())));

        // "1.5m"은 품번패턴 아님(숫자 시작) → 제품코드(43ds1500)로 대체, B 전체가 description (P8)
        VendorProductSet metal = findByMainCode(sets, "43ds1500")
                .orElseThrow(() -> new AssertionError("43ds1500(메탈호스 1.5m) 미발견"));
        assertEquals("1.5m", metal.main().description());
        assertTrue(metal.parts().isEmpty());
    }
}
