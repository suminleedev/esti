package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * §8 잔여 ⑥ 회귀 잠금 — 최신본(2026) 부속류 시트의 단위(D열) 저장.
 *
 * <p>종전에는 {@code VendorParsedItem}에 단위 자리가 없어 모든 제품이 {@code VendorProduct}의
 * 기본값 {@code SET}으로 적재됐다. 부속은 대부분 {@code ea}라 실제와 달랐다.
 *
 * <p>D열은 깨끗하지 않다 — 142행부터 붙는 니쁠 부표는 <b>D가 단가</b>다. 그 행들의 단위를
 * 그대로 읽으면 단위 자리에 금액(3000·1800…)이 들어간다. 이 테스트가 그 회귀를 막는다.
 */
class VendorB2026FittingUnitTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (부속류).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private VendorParsedItem byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .map(VendorProductSet::main)
                .orElseThrow(() -> new AssertionError("품목 미발견: " + code));
    }

    @Test
    void 본표_단위가_그대로_저장된다() {
        List<VendorProductSet> sets = parse();

        assertEquals("EA", byCode(sets, "<CODE>").unit(), "5행 ea");
        assertEquals("EA", byCode(sets, "<CODE>").unit(), "50행 ea");
        assertEquals("SET", byCode(sets, "<CODE>").unit(), "55행 SET");
        assertEquals("조", byCode(sets, "<CODE>").unit(), "122행 조");
    }

    @Test
    void 대소문자가_섞여도_한_표기로_모인다() {
        // 89행만 'EA' 대문자다. 정규화하지 않으면 같은 단위가 두 값으로 갈린다.
        List<VendorProductSet> sets = parse();

        assertEquals("EA", byCode(sets, "<CODE>").unit(), "89행 EA");
        assertEquals(byCode(sets, "<CODE>").unit(), byCode(sets, "<CODE>").unit());
    }

    @Test
    void 니쁠_부표는_D가_단가라_단위를_읽지_않는다() {
        // 143행 D=3000(단가). 이 값이 단위로 새면 단위 자리에 금액이 들어간다.
        List<VendorProductSet> sets = parse();

        assertNull(byCode(sets, "<CODE>").unit(), "니쁠 부표는 단위 미상 → null");
    }

    @Test
    void 저장되는_단위는_알려진_토큰뿐이다() {
        List<VendorProductSet> sets = parse();

        List<String> unknown = sets.stream()
                .map(VendorProductSet::main)
                .filter(m -> m != null && m.unit() != null)
                .map(VendorParsedItem::unit)
                .filter(u -> !List.of("EA", "SET", "조").contains(u))
                .distinct()
                .toList();

        assertTrue(unknown.isEmpty(), "정규화되지 않은 단위: " + unknown);
    }
}
