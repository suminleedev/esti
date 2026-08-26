package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T9 통합 검증 — 최신본 원본(14시트, 44MB)을 통째로 파싱한 결과를 고정한다.
 *
 * <p>시트별 테스트는 단일 시트 픽스처로 돌지만(R6), 여기서는 <b>시트가 서로 간섭하지 않는지</b>와
 * 합본에서만 드러나는 것(대분류 분포·시트 간 코드 충돌·이미지 매칭률)을 본다.
 */
class VendorB2026FullBookTest {

    private static final Path BOOK = Path.of("docs/samples/B사 단가표_2026최신.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(BOOK);
        return parser.parseSets(BOOK);
    }

    private BigDecimal sumOf(VendorProductSet s) {
        return s.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void 전체_적재량_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(767, sets.size(), "전체 세트/제품 수");
        assertEquals(449, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 수");
        assertEquals(28, sets.stream()
                .filter(s -> s.main().productName().contains("(가격없음)")).count(), "D8 표기");
    }

    @Test
    void 대분류_분포() {
        Map<String, Long> byCat = parse().stream().collect(Collectors.groupingBy(
                VendorProductSet::categoryLarge, TreeMap::new, Collectors.counting()));

        assertEquals(new TreeMap<>(Map.of(
                "양변기", 39L, "세면기", 56L, "소변기", 8L, "수채", 3L,
                "비데", 6L, "기타", 22L,
                "수전금구", 264L, "수전부속", 124L, "악세사리", 175L, "바스", 70L
        )), byCat);
    }

    @Test
    void 숨김과_삭제표기_시트는_어디에도_나타나지_않는다() {
        List<VendorProductSet> sets = parse();

        assertTrue(sets.stream().noneMatch(s -> s.sheetName().startsWith("(삭제)")));
        assertTrue(sets.stream().noneMatch(s -> s.sheetName().contains("품번 및 품목코드")),
                "품번 매핑표는 제품 시트가 아니다 (T7 보류)");
    }

    @Test
    void 計와_구성합_무결성은_액세사리를_빼면_전건_성립한다() {
        List<VendorProductSet> sets = parse();

        // 세면기는 택일 항목이 섞여 구조상 어긋난다(T2). 액세사리는 원본 세트가가 부속합과 다르다(T4·후속 ③).
        List<VendorProductSet> strict = sets.stream()
                .filter(s -> !s.parts().isEmpty())
                .filter(s -> !"세면기".equals(s.sheetName()))
                .filter(s -> !"액세사리류".equals(s.sheetName()))
                .toList();

        assertEquals(50, strict.size(), "양변기 39 + 소변기·수채 11");
        assertTrue(strict.stream().allMatch(s -> s.setPrice() != null
                        && sumOf(s).compareTo(s.setPrice()) == 0),
                "도기 3시트는 計 = 구성합이 정확히 성립해야 한다");
    }

    @Test
    void 액세사리_세트는_원본부터_세트가와_부속합이_다르다() {
        // 14건 전부 어긋난다. 파서 문제가 아니라 원본 값이고, 후속 ③ 재측정 대상이다(T10).
        List<VendorProductSet> acc = parse().stream()
                .filter(s -> "액세사리류".equals(s.sheetName()) && !s.parts().isEmpty())
                .toList();

        assertEquals(14, acc.size());
        assertEquals(14, acc.stream()
                .filter(s -> s.setPrice() == null || sumOf(s).compareTo(s.setPrice()) != 0)
                .count(), "세트가 vs 부속합 차이 건수");
    }

    @Test
    void 시트를_넘나드는_코드_충돌은_알려진_것뿐이다() {
        Map<String, List<String>> byCode = parse().stream().collect(Collectors.groupingBy(
                s -> s.main().productCode(),
                TreeMap::new,
                Collectors.mapping(VendorProductSet::sheetName, Collectors.toList())));

        Map<String, List<String>> dup = byCode.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        // 같은 시트 안의 완전 중복행(단가 동일)과, 두 시트에 함께 실린 핸드드라이어 2건.
        // 전부 단가가 같아 upsert가 흡수하지만, 대분류는 나중에 적재되는 쪽이 이긴다(계획서 §8 잔여 ⑦).
        assertEquals(List.of("EBA5600-6iba6002c", "EBA5600-6iba6002f", "EBA5600-6iba6002g",
                        "HD101G", "HD101P", "P0742G"),
                List.copyOf(dup.keySet()));
        assertEquals(List.of("액세사리류", "비데, 기타"), dup.get("HD101G"));
    }

    @Test
    void 이미지가_실제로_붙는_비율() {
        requireSample(BOOK);
        List<VendorProductSet> sets = parser.parseSets(BOOK);
        Map<String, Map<Integer, ExcelImageExtractor.ExtractedImage>> images =
                new ExcelImageExtractor().extract(BOOK);

        long hit = sets.stream()
                .filter(s -> s.imageKey() != null)
                .filter(s -> images.getOrDefault(s.sheetName(), Map.of())
                        .containsKey(Integer.parseInt(s.imageKey())))
                .count();

        // 개편 전에는 이 수치가 28(비데·기타)이었다 — 나머지 시트가 파싱되지 않거나 시트명이 어긋났다.
        assertTrue(hit >= 570, "이미지가 붙는 세트 수 (실측 " + hit + ")");
    }
}
