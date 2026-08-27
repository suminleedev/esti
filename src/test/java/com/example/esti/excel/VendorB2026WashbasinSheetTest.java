package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.ExpectedPrices.price;

/**
 * T2 검증 — 최신본(2026) 세면기 시트.
 *
 * <p>양변기·소변기와 컬럼 규약은 같지만 <b>한 세트에 택일 항목이 함께 실린다</b>
 * (도기 변형 여러 개, 반다리/긴다리). 그래서 {@code 計}는 구성 전체의 합이 아니라
 * <b>기본 조합가</b>이고, 세트 중간 행에도 다른 조합의 {@code 計}가 등장한다.
 */
class VendorB2026WashbasinSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (세면기).xlsx");

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
    void 세트가는_구성합이_아니라_기본_조합가다() {
        VendorProductSet s = byCode(parse(), "IL610");

        // 계(101100) = 도기 64100 + 반다리 32600 + 하프고리 2400 + 앙카볼트 2000.
        // 대체 도기(64100)와 긴다리(47300)는 빠진다 — 구성합 212500과 다른 게 정상이다.
        assertEquals(price("VendorB2026WashbasinSheetTest.세트가는_구성합이_아니라_기본_조합가다"), s.setPrice());
        assertEquals(price("VendorB2026WashbasinSheetTest.세트가는_구성합이_아니라_기본_조합가다.2"), sumOf(s));
        assertEquals(6, s.parts().size(), "택일 항목도 전부 구성으로 보존한다");
        assertEquals("도기", s.parts().get(0).productName());
        assertEquals(VendorParsedItem.RELATION_MAIN, s.parts().get(0).relationType());
    }

    @Test
    void 세트_중간의_計는_다른_조합가라_세트를_끊지_않는다() {
        // IL610은 긴다리 행(44행)에 다른 조합의 計 115800이 또 있다. 그걸 경계로 쓰면
        // 세트가 둘로 쪼개져 하프고리·앙카볼트가 떨어져 나간다.
        VendorProductSet s = byCode(parse(), "IL610");

        assertEquals(List.of("도기", "도기(비누대+독립폽업)", "반다리", "긴다리", "하프고리", "앙카볼트"),
                s.parts().stream().map(VendorParsedItem::productName).toList());
        assertEquals(price("VendorB2026WashbasinSheetTest.세트_중간의_計는_다른_조합가라_세트를_끊지_않는다"), s.setPrice(), "세트가는 시작 행의 計만 쓴다");
    }

    @Test
    void 도기_변형이_여러_개면_전부_구성으로_남는다() {
        VendorProductSet s = byCode(parse(), "L553");

        List<String> names = s.parts().stream().map(VendorParsedItem::productName).toList();
        assertEquals(List.of("도기(독립폽업)", "도기(103비누대)", "도기(103/102비누대)",
                "비누대(SP103)", "비누대"), names);
        assertEquals(price("VendorB2026WashbasinSheetTest.도기_변형이_여러_개면_전부_구성으로_남는다"), s.setPrice(), "기본 조합 = 도기 단독");
        // 대체 도기의 G열 코드는 그 구성행의 description으로 보존한다.
        assertEquals("대체코드: 33553anbnwt", s.parts().get(1).description());
    }

    @Test
    void 담수는_규격에_함께_들어간다() {
        VendorProductSet s = byCode(parse(), "L553");
        assertEquals("500(W) 455(D) / 담수 7.5ℓ", s.main().specs());
    }

    @Test
    void 셀_안의_줄바꿈은_한_문장으로_잇는다() {
        // P열 원본은 "NB 모델은⏎43hy582만 ⏎가능" — 좁은 컬럼에서 접힌 한 문장이다.
        VendorProductSet s = byCode(parse(), "L554");
        assertEquals("NB 모델은 43hy582만 가능", s.main().description());
        assertEquals("소진 후 단종", s.main().remark(), "단종은 같은 셀에 있어도 remark로 갈라진다");
    }

    @Test
    void 품목_괄호_뒤에_남은_설명도_보존한다() {
        // 93행 C열 원본은 "IL672⏎(롱하우)⏎비누대, 폽업" — 괄호 뒤 꼬리가 붙는다.
        List<VendorProductSet> sets = parse();
        VendorProductSet s = sets.stream()
                .filter(x -> x.main().productCode().startsWith("IL672")
                        && x.main().description() != null
                        && x.main().description().contains("롱하우"))
                .findFirst().orElseThrow();

        assertTrue(s.main().description().contains("비누대, 폽업"),
                "괄호 뒤 텍스트가 사라지면 안 된다: " + s.main().description());
    }

    @Test
    void 시트_하단_부속_부록표는_적재되지_않는다() {
        // 142행부터 '품명/제품코드/단가' 부록표(반다리 ABS·브라켓·후렌지)가 붙는다.
        // C(품목)가 없어 세트로 시작되지 않아야 하고, 직전 세트에 부속으로 흡수돼도 안 된다.
        List<VendorProductSet> sets = parse();

        assertTrue(sets.stream().noneMatch(s -> s.main().productCode().startsWith("반다리")));
        VendorProductSet last = byCode(sets, "L953-2");
        assertTrue(last.parts().stream().noneMatch(p -> "브라켓".equals(p.productName())),
                "부록표 항목이 직전 세트의 부속으로 붙으면 안 된다");
        assertEquals(price("VendorB2026WashbasinSheetTest.시트_하단_부속_부록표는_적재되지_않는다"), last.setPrice());
    }

    @Test
    void 동일_품번_변형은_갈라진다() {
        List<VendorProductSet> sets = parse();

        // IL672는 클레이탄·롱하우·독립폽업 3종으로 세 번 나온다.
        assertEquals(price("VendorB2026WashbasinSheetTest.IL672"), byCode(sets, "IL672").setPrice());
        assertEquals(price("VendorB2026WashbasinSheetTest.IL672-2"), byCode(sets, "IL672-2").setPrice());
        assertEquals(price("VendorB2026WashbasinSheetTest.IL672-3"), byCode(sets, "IL672-3").setPrice());
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(56, sets.size(), "세트 수");
        assertEquals(136, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 수");
        assertTrue(sets.stream().allMatch(s -> "세면기".equals(s.categoryLarge())));
        assertTrue(sets.stream().allMatch(s -> s.setPrice() != null), "計 없는 세트는 없다");

        // 택일 항목이 있는 세트만 구성합과 어긋난다. 나머지는 양변기처럼 정확히 일치해야 한다.
        long eq = sets.stream().filter(s -> sumOf(s).compareTo(s.setPrice()) == 0).count();
        assertEquals(48, eq, "구성이 확정된 세트는 計 = 구성합");
    }
}
