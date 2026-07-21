package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 양변기 시트 전용 검증(parseToiletSheet).
 *
 * <ul>
 *   <li>req1 — 품종(B) 병합셀: 구간 첫 행 외에는 빈칸이므로 직전 품종을 이어써 categorySmall이 채워져야 한다
 *       (F/V 구간은 행마다 반복이라 원래 정상, 투피스/원피스 구간이 회귀 지점).</li>
 *   <li>req2 — 서브테이블마다 헤더가 달라 슬롯 라벨이 바뀐다: F/V 구간은 F/V·스퍼드, 투피스 구간은 탱크·양부속.</li>
 *   <li>req3 — 품번/부속코드의 "코드(설명)"에서 설명을 description으로 분리.</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBToiletSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> toiletSets() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "양변기".equals(s.categoryLarge()))
                .toList();
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 대표품목 미발견"));
    }

    private List<String> partNames(VendorProductSet s) {
        return s.parts().stream().map(VendorParsedItem::productName).toList();
    }

    @Test
    void FV구간_대변기_MC921은_기존대로_도기_FV_스퍼드_세트가77200() {
        List<VendorProductSet> sets = toiletSets();
        VendorProductSet mc921 = byCode(sets, "MC921");

        assertEquals("대변기", mc921.categorySmall(), "F/V 구간 품종");
        assertEquals(0, new BigDecimal("77200").compareTo(mc921.setPrice()));
        assertEquals(3, mc921.parts().size());
        assertTrue(partNames(mc921).contains("F/V"), "F/V 부속 유지");
        assertTrue(mc921.parts().stream().anyMatch(p ->
                p.productName().startsWith("도기") && VendorParsedItem.RELATION_MAIN.equals(p.relationType())));
    }

    @Test
    void req1_투피스_품종_병합셀이라도_carryforward로_categorySmall_채워짐() {
        List<VendorProductSet> sets = toiletSets();

        // C733(r19)에만 B=투피스, C752(r21)·C753(r23)은 B 빈칸(병합) → 직전 품종 이어써야 함
        assertEquals("투피스", byCode(sets, "C733").categorySmall());
        assertEquals("투피스", byCode(sets, "C752").categorySmall(), "병합셀 carry-forward");
        assertEquals("투피스", byCode(sets, "C753").categorySmall(), "병합셀 carry-forward");
        // 원피스 구간도 동일 (C801에만 B=원피스, C836은 빈칸)
        assertEquals("원피스", byCode(sets, "C836").categorySmall(), "원피스 구간 carry-forward");
    }

    @Test
    void req2_투피스_부속은_헤더기준으로_탱크_양부속이고_FV스퍼드가_아님() {
        List<VendorProductSet> sets = toiletSets();
        VendorProductSet c752 = byCode(sets, "C752");
        List<String> names = partNames(c752);

        assertTrue(names.contains("탱크"), "투피스(비사출수로) H슬롯=탱크: " + names);
        assertFalse(names.contains("탱크(사출수로)"), "괄호 표기는 저장 안 함: " + names);
        assertTrue(names.contains("양부속"), "투피스 I슬롯=양부속: " + names);
        assertFalse(names.contains("F/V"), "투피스엔 F/V 없음: " + names);
        assertFalse(names.contains("스퍼드"), "투피스엔 스퍼드 없음: " + names);

        assertEquals(0, new BigDecimal("126500").compareTo(c752.setPrice()));
        BigDecimal sum = c752.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, c752.setPrice().compareTo(sum), "計=부속합");
    }

    @Test
    void 도기수로_사출수로_변형은_둘다_보존되고_사출수로에만_p가_붙는다() {
        List<VendorProductSet> sets = toiletSets();

        // C853: 도기수로(C853) / 사출수로(C853p) 둘 다 존재, 둘 다 計=249500
        VendorProductSet dogi = byCode(sets, "C853");
        VendorProductSet sachul = byCode(sets, "C853p");
        assertTrue(dogi.main().description() != null && dogi.main().description().contains("도기수로"),
                "C853 description=도기수로: " + dogi.main().description());
        assertTrue(sachul.main().description() != null && sachul.main().description().contains("사출수로"),
                "C853p description=사출수로: " + sachul.main().description());
        assertEquals(0, new BigDecimal("249500").compareTo(dogi.setPrice()));
        assertEquals(0, new BigDecimal("249500").compareTo(sachul.setPrice()));

        // 다른 충돌 쌍도 사출수로에 p 부여
        assertEquals(0, new BigDecimal("223700").compareTo(byCode(sets, "C959p").setPrice()));
        assertNotNull(byCode(sets, "C959"));

        // 단독 사출수로(짝 없음)는 p를 붙이지 않고 원래 품번 유지
        VendorProductSet ic858 = byCode(sets, "IC858P");
        assertTrue(ic858.main().description() != null && ic858.main().description().contains("사출수로"));
        assertTrue(sets.stream().noneMatch(s -> "IC858Pp".equals(s.main().productCode())),
                "단독 사출수로는 p 미부여");
    }

    @Test
    void 탱크슬롯은_사출수로품목이면_사출수로_그외엔_탱크로_저장() {
        List<VendorProductSet> sets = toiletSets();

        // C752: 품번에 (사출수로) 표시 없음 → H부속=탱크
        List<String> c752 = partNames(byCode(sets, "C752"));
        assertTrue(c752.contains("탱크"), c752.toString());
        assertFalse(c752.contains("사출수로"), c752.toString());

        // C853p: 품번에 (사출수로) 표시 → H부속=사출수로 (탱크 아님)
        List<String> c853p = partNames(byCode(sets, "C853p"));
        assertTrue(c853p.contains("사출수로"), "사출수로 품목 H=사출수로: " + c853p);
        assertFalse(c853p.contains("탱크"), "사출수로 품목엔 탱크 없음: " + c853p);
    }

    @Test
    void 도자종류만_다른_동일품번_중복은_도기코드_구분글자로_분기되고_둘다_보존() {
        List<VendorProductSet> sets = toiletSets();

        // IC703E(성오도자) 4oc703wt / IC703E(구륙도자) 4gc703wt → IC703Eo / IC703Eg
        // (C-2로 Q열 비고가 " / "로 병합될 수 있어 도자명은 앞부분 일치로 확인)
        VendorProductSet o = byCode(sets, "IC703Eo");
        VendorProductSet g = byCode(sets, "IC703Eg");
        assertTrue(o.main().description().startsWith("성오도자"), o.main().description());
        assertTrue(g.main().description().startsWith("구륙도자"), g.main().description());
        assertTrue(sets.stream().noneMatch(s -> "IC703E".equals(s.main().productCode())), "base 품번 단독 잔존 없음");

        // IC855E는 두 모델 가격이 달라(173200/171300) 둘 다 보존되어야 함
        VendorProductSet m = byCode(sets, "IC855Em"); // 모노피 4mc855wt
        VendorProductSet e = byCode(sets, "IC855Ee"); // 헝얼자도 4ec855wt
        assertEquals(0, new BigDecimal("173200").compareTo(m.setPrice()));
        assertEquals(0, new BigDecimal("171300").compareTo(e.setPrice()));
    }

    @Test
    void C2_비고는_제품코드행_대리점가행을_병합해_description() {
        List<VendorProductSet> sets = toiletSets();

        // C733: 제품코드행 Q=탱크뚜껑 코드, 대리점가행 Q=인치 구분 → " / " 병합해 description(C-2 결정 9)
        String d = byCode(sets, "C733").main().description();
        assertNotNull(d);
        assertTrue(d.contains("32cap733"), d);
        assertTrue(d.contains("인치 구분"), d);

        // 비고 없는 제품은 description 오염 없음
        assertNull(byCode(sets, "MC921").main().description());
    }

    @Test
    void req3_품번에_괄호설명이_있으면_코드와_분리해_description에_저장() {
        List<VendorProductSet> sets = toiletSets();

        // r11: C="IC552EF(길마위욕)" → 코드=IC552EF, description=길마위욕
        VendorProductSet ic552 = byCode(sets, "IC552EF");
        assertEquals("길마위욕", ic552.main().description(), "괄호 설명 분리");
        assertTrue(partNames(ic552).contains("F/V"), "F/V 구간이므로 F/V 부속 포함");
    }
}
