package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * P1 검증: A사 파서가 합계행 기준으로 대표품목+부속을 정확히 묶는지.
 * 샘플(docs/samples/...)은 git 추적 제외이므로, 파일이 없으면 테스트를 스킵한다.
 */
class VendorAExcelParserTest {

    private static final Path SAMPLE = Path.of("docs/samples/A사 단가표_sample.xlsx");

    private final VendorAExcelParser parser = new VendorAExcelParser();

    private List<VendorProductSet> parseSample() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private Optional<VendorProductSet> findByMainCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst();
    }

    @Test
    void 스탠리_세트_합계행으로_대표품목과_부속2개_연결() {
        List<VendorProductSet> sets = parseSample();

        VendorProductSet stanley = findByMainCode(sets, "C338000B-6DAKIS60D")
                .orElseThrow(() -> new AssertionError("스탠리 대표품목 미발견"));

        assertEquals(0, new BigDecimal("374700").compareTo(stanley.setPrice()), "세트가=부속 합산");
        assertEquals(2, stanley.parts().size(), "부속 2개(시트커버, 플랜지)");
        assertFalse(stanley.needsReview(), "일치하므로 검수 불필요");
        assertEquals(VendorParsedItem.RELATION_MAIN, stanley.main().relationType());
        assertTrue(stanley.parts().stream()
                .allMatch(p -> VendorParsedItem.RELATION_ACCESSORY.equals(p.relationType())));
    }

    @Test
    void 유로젠_라운드_세트는_사이의_옵션행을_제외하고_묶임() {
        List<VendorProductSet> sets = parseSample();

        // 라운드 세트: 비데(라운드) + 양변기(라운드) + 앵글밸브 = 637800
        VendorProductSet round = findByMainCode(sets, "C837500E-6DAKMR05V")
                .orElseThrow(() -> new AssertionError("라운드 대표품목 미발견"));

        assertEquals(0, new BigDecimal("637800").compareTo(round.setPrice()));
        assertEquals(2, round.parts().size());
        assertFalse(round.needsReview());
    }

    @Test
    void 플랫_블록은_합계가_부분집합이라_검수플래그() {
        List<VendorProductSet> sets = parseSample();

        // 합계 769100이 연속/트레일링 합산과 안 맞는 블록 → needsReview=true 인 세트가 존재
        boolean hasFlaggedTotal = sets.stream()
                .anyMatch(s -> s.needsReview()
                        && s.setPrice() != null
                        && new BigDecimal("769100").compareTo(s.setPrice()) == 0);
        assertTrue(hasFlaggedTotal, "플랫 블록(769100)이 검수 플래그로 저장돼야 함");
    }

    @Test
    void 신품번_없는_행은_제품명에_표기되고_단가0() {
        List<VendorProductSet> sets = parseSample();

        boolean found = sets.stream()
                .map(VendorProductSet::main)
                .anyMatch(m -> m != null
                        && m.productCode() == null
                        && m.productName() != null
                        && m.productName().contains("(신품번 없음)")
                        && BigDecimal.ZERO.compareTo(m.unitPrice()) == 0);
        assertTrue(found, "신품번 없는 행(예: 비데 B타입)이 '(신품번 없음)'으로 저장돼야 함");
    }
}
