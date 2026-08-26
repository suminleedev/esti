package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T6 검증 — 최신본(2026) 수전금구류 시트.
 *
 * <p>구본 파서도 형태상 읽기는 하지만 두 군데가 어긋난다 —
 * 시트명을 {@code 수전금구}로 고정해 이미지가 전부 끊기고, 전산코드를 버려 T7 조인 키가 사라진다.
 */
class VendorB2026FaucetSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (수전금구류).xlsx");

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
    void 이미지_매칭키가_실제_시트명을_가리킨다() {
        requireSample(SAMPLE);
        List<VendorProductSet> sets = parser.parseSets(SAMPLE);
        Map<String, Map<Integer, ExcelImageExtractor.ExtractedImage>> images =
                new ExcelImageExtractor().extract(SAMPLE);

        assertTrue(sets.stream().allMatch(s -> "수전금구류".equals(s.sheetName())),
                "'수전금구'로 고정하면 이미지 맵의 키와 어긋나 285장이 통째로 끊긴다");

        Map<Integer, ExcelImageExtractor.ExtractedImage> bySheet = images.get("수전금구류");
        assertNotNull(bySheet, "이미지 맵은 실제 시트명을 키로 쓴다");

        long matched = sets.stream()
                .filter(s -> s.imageKey() != null)
                .filter(s -> bySheet.containsKey(Integer.parseInt(s.imageKey())))
                .count();
        assertTrue(matched > 200, "실제로 붙는 이미지가 200장을 넘어야 한다 (실측 " + matched + ")");
    }

    @Test
    void 전산코드를_보조품번으로_보존한다() {
        // T7이 품번표(분계품목코드)와 조인하는 키다. 구본은 이 컬럼을 읽지 않는다.
        VendorProductSet s = byCode(parse(), "G-0110");

        assertEquals("46dsg0110", s.main().subItemCode());
        assertEquals(new BigDecimal("<PRICE>"), s.setPrice());
        assertEquals("G-01", s.categorySmall(), "소분류 = 시리즈(병합셀 이어쓰기)");
        assertEquals("수전금구", s.categoryLarge());
    }

    @Test
    void 겹치는_품번은_전산코드로_가른다() {
        // K-0310B는 제조사가 바뀌며 같은 품번으로 두 벌이 실렸다(46drk / 46kfk). 단가도 다르다.
        List<VendorProductSet> sets = parse();

        assertEquals(new BigDecimal("<PRICE>"), byCode(sets, "K-0310B-46drk0310b").setPrice());
        assertEquals(new BigDecimal("<PRICE>"), byCode(sets, "K-0310B-46kfk0310b").setPrice());
        assertEquals("소진 후 단종(제조사 변경)", byCode(sets, "K-0310B-46drk0310b").main().remark());
    }

    @Test
    void 전산코드가_없는_신상품도_품번으로_적재된다() {
        // '26 신상품 12건은 전산코드도 단가도 비어 있다. 스킵하지 않고 D8 표기로 남긴다.
        VendorProductSet s = byCode(parse(), "BSS-1000");

        assertNull(s.main().subItemCode());
        assertTrue(s.main().productName().endsWith("(가격없음)"));
        assertTrue(s.main().description().contains("'26 신상품"));
    }

    @Test
    void 박스기준과_비고가_내용별로_갈라진다() {
        List<VendorProductSet> sets = parse();

        // 박스 기준은 여러 줄이어도 한 줄로 이어 description으로 간다.
        assertEquals("샤워 헤드 포함 1Box=8ea / 고압호스: <CODE>(<PRICE>원)x2",
                byCode(sets, "G-0814").main().description());
        // 비고 한 셀에 설명과 단종이 섞이면 줄 단위로 갈라진다.
        assertEquals("소진 후 단종", byCode(sets, "G-0530").main().remark());
        assertEquals("단종", byCode(sets, "IBF-0146").main().remark());
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(264, sets.size(), "제품 수");
        assertTrue(sets.stream().allMatch(s -> s.parts().isEmpty()), "부속 없는 단일 제품 목록");
        assertTrue(sets.stream().allMatch(s -> "수전금구".equals(s.categoryLarge())));
        assertEquals(23, sets.stream()
                .filter(s -> s.main().productName().endsWith("(가격없음)")).count(), "단가 빈 항목");
        assertEquals(sets.size(), sets.stream().map(s -> s.main().productCode()).distinct().count(),
                "품번이 겹치면 전산코드를 붙여 유일해야 한다");
    }
}
