package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T3 검증 — 최신본(2026) 소변기·수채 시트.
 *
 * <p>컬럼 규약은 양변기와 같다. 다른 점은 두 가지 —
 * 한 시트에 {@code ■}로 갈린 서브테이블이 두 개(소변기 / 소제싱크)이고,
 * 좌측이 전부 {@code -}인 채 우측 부속 서브테이블만 채워진 행이 있다.
 */
class VendorB2026UrinalSinkSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (소변기수채).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("대표품목 미발견: " + code));
    }

    private BigDecimal sumOf(VendorProductSet set) {
        return set.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void 시트_안의_두_구역이_각각_다른_대분류로_갈린다() {
        List<VendorProductSet> sets = parse();

        assertEquals("소변기", byCode(sets, "U136").categoryLarge());
        assertEquals("수채", byCode(sets, "S131E").categoryLarge(), "'■ 소제싱크' 아래는 수채");
        assertEquals(8, sets.stream().filter(s -> "소변기".equals(s.categoryLarge())).count());
        assertEquals(3, sets.stream().filter(s -> "수채".equals(s.categoryLarge())).count());
    }

    @Test
    void 대분류가_갈려도_이미지_매칭키는_시트명을_유지한다() {
        // 이미지 추출 맵은 시트명이 키다. 대분류를 시트명 대신 쓰면 수채 3건의 이미지가 끊긴다.
        assertTrue(parse().stream().allMatch(s -> "소변기, 수채".equals(s.sheetName())));
    }

    @Test
    void 좌측이_대시뿐인_행은_세트로_읽지_않는다() {
        // 42~47행은 C·E가 전부 '-'이고 우측 부속 서브테이블(감지기 옵션 목록)만 채워져 있다.
        List<VendorProductSet> sets = parse();

        assertTrue(sets.stream().noneMatch(s -> "-".equals(s.main().productCode())),
                "대시를 품번으로 읽으면 안 된다");
        assertTrue(sets.stream().flatMap(s -> s.parts().stream())
                        .noneMatch(p -> "-".equals(p.productName())),
                "대시 행이 직전 세트의 부속으로 붙으면 안 된다");
        assertEquals(4, byCode(sets, "IU306E").parts().size(), "도기/스퍼드/후렌지/매립감지기");
    }

    @Test
    void 같은_부속이_두_번_들어간_세트도_計가_맞는다() {
        VendorProductSet s = byCode(parse(), "S132E");

        // 계(173100) = 도기 115000 + 수채가량 18000 × 2 + 수채트랩 22100
        assertEquals(new BigDecimal("173100"), s.setPrice());
        assertEquals(0, sumOf(s).compareTo(s.setPrice()));
        assertEquals(2, s.parts().stream().filter(p -> "수채가량".equals(p.productName())).count(),
                "중복 부속을 합치면 計가 어긋난다 — 원본 그대로 둔다");
        // 관계는 (source,target,type) 유일이라 저장 시 1건으로 접힌다(수량 축 부재, 계획서 §8 잔여 ②).
        assertEquals(1, s.parts().stream().filter(p -> "수채가량".equals(p.productName()))
                .map(VendorParsedItem::productCode).distinct().count());
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(11, sets.size(), "소변기 8 + 수채 3");
        assertEquals(47, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 수");
        assertTrue(sets.stream().allMatch(s -> s.imageKey() != null));

        long mismatch = sets.stream()
                .filter(s -> s.setPrice() == null || sumOf(s).compareTo(s.setPrice()) != 0)
                .count();
        assertEquals(0, mismatch, "구성이 확정된 시트라 計 = 구성합이 전건 성립");
    }
}
