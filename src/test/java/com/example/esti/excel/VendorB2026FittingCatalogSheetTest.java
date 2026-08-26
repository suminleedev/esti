package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T5 검증 — 최신본(2026) 부속류 시트.
 *
 * <p>구본 '수전 부속(세트)'와 레이아웃은 같지만 <b>소계행이 하나도 없다</b> → 세트를 만들지 않는다(D-B5).
 * 식별자는 품번이 아니라 전산코드다 — 구·신 코드가 병존해 같은 품번이 서로 다른 전산코드를 갖는다.
 */
class VendorB2026FittingCatalogSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (부속류).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("품목 미발견: " + code));
    }

    @Test
    void 소계가_없으므로_세트를_만들지_않는다() {
        List<VendorProductSet> sets = parse();
        assertTrue(sets.stream().allMatch(s -> s.parts().isEmpty()), "전부 단품 카탈로그");
        assertTrue(sets.stream().allMatch(s -> "수전부속".equals(s.categoryLarge())));
    }

    @Test
    void 구버전과_신규_코드가_같은_품번이어도_따로_적재된다() {
        // 'U9013c 냉수' 한 품번에 전산코드가 둘이다 — 품번을 식별자로 쓰면 한 제품으로 병합된다.
        List<VendorProductSet> sets = parse();

        assertEquals(new BigDecimal("10000"), byCode(sets, "43u9013c").setPrice());
        assertEquals(new BigDecimal("10500"), byCode(sets, "43dbu9013c").setPrice());
        assertEquals("구버전", byCode(sets, "43u9013c").main().description());
        assertEquals("신규", byCode(sets, "43dbu9013c").main().description());
    }

    @Test
    void 여러_그룹에_다시_나오는_전산코드는_처음_것만_남는다() {
        // 43ds1500(메탈호스 1.5m)은 발코니수전·청소용수전 구성으로 6번 등장한다. 단가는 전부 같다.
        List<VendorProductSet> sets = parse();

        assertEquals(1, sets.stream()
                .filter(s -> "43ds1500".equals(s.main().productCode())).count());
        assertEquals("메탈호스", byCode(sets, "43ds1500").categorySmall(),
                "뒤에 나오는 '발코니수전' 그룹이 처음 그룹을 덮으면 안 된다");
    }

    @Test
    void 니쁠_부표는_다른_컬럼_배치로_읽고_규격은_specs로_간다() {
        // 141행 아래 부표는 B=품목 C=제품코드 D=단가 E=규격 — 본표(F=단가)와 배치가 다르다.
        List<VendorProductSet> sets = parse();

        assertEquals(8, sets.stream().filter(s -> "니쁠".equals(s.categorySmall())).count());
        VendorProductSet n = byCode(sets, "43u94p65");
        assertEquals(new BigDecimal("3000"), n.setPrice(), "D열을 단가로 읽어야 한다");
        assertEquals("65mm", n.main().specs(), "규격은 specs (R7 ③)");
    }

    @Test
    void 비고는_내용별로_갈라지고_매입처는_버린다() {
        List<VendorProductSet> sets = parse();

        assertEquals("재고 소진 후 단종", byCode(sets, "43u9113").main().remark());
        assertEquals("3기능", byCode(sets, "43u9310n").main().description());
        // H=한양(매입처)은 저장하지 않는다 (R7 ④).
        VendorProductSet hanyang = byCode(sets, "43u0520cr");
        assertNull(hanyang.main().description());
        assertNull(hanyang.main().remark());
    }

    @Test
    void 대문자_전산코드도_소문자로_모은다() {
        // 57행 C열은 43U9113 — 같은 코드가 대소문자만 다르게 들어오는 오타를 흡수한다(구본 P9).
        assertNotNull(byCode(parse(), "43u9113"));
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(124, sets.size(), "본표 116 + 니쁠 8 (중복 전산코드 12건 제외)");
        assertTrue(sets.stream().allMatch(s -> "부속류".equals(s.sheetName())));
        assertEquals(sets.size(),
                sets.stream().map(s -> s.main().productCode()).distinct().count(),
                "전산코드는 시트 안에서 유일해야 한다");
    }
}
