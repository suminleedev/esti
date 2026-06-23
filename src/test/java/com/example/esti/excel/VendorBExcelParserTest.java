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
    void 세면기는_計없는_선택형_세트로_저장() {
        List<VendorProductSet> sets = parseSample();

        VendorProductSet il451 = findByMainCode(sets, "IL451B")
                .orElseThrow(() -> new AssertionError("IL451B 대표품목 미발견"));

        assertTrue(il451.selectable(), "세면기는 선택형");
        assertNull(il451.setPrice(), "선택형은 세트가 미산정");
        assertTrue(il451.main().productName().contains("부속 선택형"));
        assertFalse(il451.parts().isEmpty(), "슬롯 부속은 존재");
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
}
