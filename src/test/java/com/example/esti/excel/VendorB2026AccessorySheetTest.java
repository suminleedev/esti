package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.ExpectedPrices.price;

/**
 * T4 검증 — 최신본(2026) 액세사리류 시트.
 *
 * <p>세트 12~14건 + 일반품이 한 시트에 섞여 있다. 구본 '악세사리 단가표' 대비
 * 컬럼이 2칸 왼쪽으로 밀렸고, 시트명도 '악'→'액'으로 바뀌었다.
 */
class VendorB2026AccessorySheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (액세사리류).xlsx");

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

    @Test
    void 세트는_규격SET으로_판정한다() {
        List<VendorProductSet> sets = parse();
        VendorProductSet s = byCode(sets, "AC8100");

        assertEquals(price("VendorB2026AccessorySheetTest.세트는_규격SET으로_판정한다"), s.setPrice());
        assertEquals(List.of("수건걸이", "휴지걸이", "컵대", "비누대"),
                s.parts().stream().map(VendorParsedItem::productName).toList());
        assertEquals("AC8100_AC8101", s.parts().get(0).productCode());
        assertEquals("악세사리", s.categoryLarge(), "대분류는 시트명이 아니라 '악세사리' 고정(C-1)");
        assertEquals("액세사리류", s.sheetName(), "이미지 매칭 키는 실제 시트명");
    }

    @Test
    void 시리즈_라벨이_세트로_오인되지_않는다() {
        // 100행 이후 A열은 세트명이 아니라 시리즈(DT 20A, DS 1A…)다.
        // "A열에 값이 있으면 세트"로 판정하면 여기서 전부 세트가 된다.
        List<VendorProductSet> sets = parse();

        VendorProductSet s = byCode(sets, "AT0111S");
        assertTrue(s.parts().isEmpty(), "시리즈 라벨 행은 단일품이다");
        assertEquals("DT 20A", s.categorySmall(), "시리즈는 소분류로만 쓴다");
        assertEquals(14, sets.stream().filter(x -> !x.parts().isEmpty()).count(), "세트는 14건뿐");
    }

    @Test
    void 세트_구성은_선언된_품수만큼만_묶는다() {
        List<VendorProductSet> sets = parse();

        // AC8300G는 '4품'인데 뒤에 옷걸이(AC8305G)가 한 줄 더 붙는다.
        // 세트가 108,400은 4품 합(108,500)에 대응하고 옷걸이(12,500)를 포함하지 않는다.
        VendorProductSet set = byCode(sets, "AC8300G");
        assertEquals(4, set.parts().size());
        assertTrue(set.parts().stream().noneMatch(p -> "옷걸이".equals(p.productName())));

        // 빠진 옷걸이는 유실되지 않고 단일품으로 남는다.
        VendorProductSet coat = byCode(sets, "AC8305G");
        assertTrue(coat.parts().isEmpty());
        assertEquals(price("VendorB2026AccessorySheetTest.세트_구성은_선언된_품수만큼만_묶는다"), coat.setPrice());
    }

    @Test
    void 품수는_5품_세트도_읽는다() {
        VendorProductSet s = byCode(parse(), "AC1100");
        assertEquals(5, s.parts().size());
        assertEquals(price("VendorB2026AccessorySheetTest.품수는_5품_세트도_읽는다"), s.setPrice());
    }

    @Test
    void 겹치는_품번은_전산코드로_가른다() {
        // AC9320은 비누대·휴지걸이·수건걸이·컵대 4행이 같은 품번을 쓴다.
        // 품번만 쓰면 4개 제품이 한 행으로 병합된다.
        List<VendorProductSet> sets = parse();

        assertEquals("AC9320 비누대", byCode(sets, "AC9320-6ibac9323").main().productName());
        assertEquals("AC9320 휴지걸이", byCode(sets, "AC9320-6ibac9322").main().productName());
        assertEquals(4, sets.stream()
                .filter(s -> s.main().productCode().startsWith("AC9320-")).count());

        // U로 시작하는 품번은 수전부속 체계와 겹치므로 유일해도 전산코드를 붙인다(구본 A1 정책).
        assertEquals("WATERPUE 씽크헤드 필터형", byCode(sets, "U0320-43u0320").main().productName());
    }

    @Test
    void 병합된_품명은_이어_쓰고_규격은_설명으로_간다() {
        // 165~186행은 D(품명)가 병합이라 아래 행이 비어 있다. E(규격)만 분리/고급으로 다르다.
        VendorProductSet s = byCode(parse(), "AG0112");

        assertEquals("세면기 손잡이", s.main().productName());
        assertEquals("분리", s.main().description());
        assertEquals("단종", s.main().remark());
    }

    @Test
    void 공급처와_참고_컬럼은_저장하지_않는다() {
        // I=공급처(대신건기·TS자바), J~L=참고(대림비앤코 대응 코드) — 우리 데이터가 아니다.
        List<VendorProductSet> sets = parse();

        assertTrue(sets.stream().allMatch(s ->
                        s.main().description() == null || !s.main().description().contains("TS자바")),
                "공급처가 설명으로 새면 안 된다");
        assertTrue(sets.stream().allMatch(s ->
                        s.main().description() == null || !s.main().description().startsWith("DL-")),
                "대림비앤코 참고 코드가 설명으로 새면 안 된다");
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(175, sets.size(), "세트 14 + 단일품");
        assertEquals(57, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 (4품 13 + 5품 1)");
        assertTrue(sets.stream().allMatch(s -> "악세사리".equals(s.categoryLarge())));
        assertTrue(sets.stream().allMatch(s -> "액세사리류".equals(s.sheetName())));

        // 세트가가 회계서식 0이라 비어 있는 단종 세트 1건(AC5300)은 D8 표기를 단다.
        assertTrue(byCode(sets, "AC5300").main().productName().endsWith("(가격없음)"));
    }
}
