package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 수전부속 3-시트 전용 검증(§11): parseBreakdownSheet / parseFittingSetSheet / parseFittingPriceSheet.
 *
 * <p>분계표 본품은 §10 수전금구와 동일 제품 → 품번 정규화(G 0130→G-0130) 후 대분류=수전금구 병합,
 * 대리점가는 priceBasis=분계표(P1). 세트·단가표는 대분류=수전부속 신규, priceBasis=시트명(P5) —
 * 공통 43종 품번은 DB upsert에서 1행+가격 2행으로 병합(적재 검증은 {@code VendorBFittingDbTest}).</p>
 * <p>시트별 픽스처(로컬 전용, git 미추적)가 없으면 스킵.</p>
 */
class VendorBFittingSheetTest {

    private static final Path FIXTURE = Path.of("docs/samples/B사 test (수전부속).xlsx");
    private static final Path OEM_FIXTURE = Path.of("docs/samples/B사 test (신규 OEM 부속).xlsx");
    private static final String SET_BASIS = "수전 부속(세트)";
    private static final String PRICE_BASIS = "부속 단가표";
    private static final String OEM_BASIS = "신규 OEM 부속 단가표";

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parseFixture() {
        requireSample(FIXTURE);
        return parser.parseSets(FIXTURE);
    }

    private List<VendorProductSet> parseOemFixture() {
        requireSample(OEM_FIXTURE);
        return parser.parseSets(OEM_FIXTURE);
    }

    private List<VendorProductSet> byCode(List<VendorProductSet> sets, String code) {
        return sets.stream().filter(s -> s.main() != null && code.equals(s.main().productCode())).toList();
    }

    private VendorProductSet one(List<VendorProductSet> sets, String code, String priceBasis) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .filter(s -> priceBasis == null || priceBasis.equals(s.priceBasis()))
                .findFirst().orElseThrow(() -> new AssertionError(code + "@" + priceBasis + " 미발견"));
    }

    private VendorParsedItem part(VendorProductSet s, String code) {
        return s.parts().stream().filter(p -> code.equals(p.productCode()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " 부속 미발견: "
                        + s.parts().stream().map(VendorParsedItem::productCode).toList()));
    }

    // ===== 분계표 (P1~P4) =====

    @Test
    void 분계표_품번정규화_수전금구병합_basis분리() {
        List<VendorProductSet> sets = parseFixture();

        // "G 0130"(공백형) → G-0130: 대분류=수전금구(§10 병합), 가격은 분계표 basis, 소분류=시리즈 유도
        VendorProductSet g0130 = one(sets, "G-0130", "분계표");
        assertEquals("수전금구", g0130.categoryLarge());
        assertEquals("G-01", g0130.categorySmall());
        assertEquals(0, new BigDecimal("49800").compareTo(g0130.setPrice()), "대리점가=49800");

        // 부속 = {품번}_{전산코드} 프리픽스(P2), 출처 categorySmall=분계
        assertEquals(4, g0130.parts().size(), "몸체+편심+메탈호스+샤워헤드");
        VendorParsedItem body = part(g0130, "G-0130_46dsg0130n");
        assertEquals("몸체", body.productName());
        assertEquals(0, new BigDecimal("27800").compareTo(body.unitPrice()));
        assertTrue(g0130.parts().stream().allMatch(p -> "분계".equals(p.categorySmall())), "부속 출처=분계");

        // "T0130"(무구분형) → T-0130 정규화, "G-0121"(하이픈형) 유지
        assertEquals(0, new BigDecimal("135000").compareTo(one(sets, "T-0130", "분계표").setPrice()));
        assertEquals(1, byCode(sets, "G-0121").stream().filter(s -> "분계표".equals(s.priceBasis())).count());
    }

    @Test
    void 분계표_중복품번_S0346은_첫블록만_검수필요() {
        List<VendorProductSet> sets = parseFixture();
        // 슬림/일반/건설납품 3중 등장(품번·세트코드 동일) → 첫 블록만 + needsReview(P4)
        List<VendorProductSet> s0346 = byCode(sets, "S-0346");
        assertEquals(1, s0346.size(), "첫 블록만 적재");
        assertTrue(s0346.get(0).needsReview(), "중복 품번 → 검수필요");
        assertEquals(0, new BigDecimal("185000").compareTo(s0346.get(0).setPrice()));
    }

    @Test
    void 분계표_가격없는블록은_구성만_가격0() {
        List<VendorProductSet> sets = parseFixture();
        // 싱크수전 블록(G-0121)은 대리점가 없음 → 구성(몸체+싱크헤드)만, 가격 0(P3)
        VendorProductSet g0121 = one(sets, "G-0121", "분계표");
        assertEquals(0, BigDecimal.ZERO.compareTo(g0121.setPrice()));
        assertEquals(2, g0121.parts().size());
        assertNotNull(part(g0121, "G-0121_43u0301gc"));
        assertFalse(g0121.main().productName().contains("가격없음"), "공유 본품 이름 오염 금지(P3)");
    }

    // ===== 수전 부속(세트) (P6~P8) =====

    @Test
    void 세트시트_냉온소계는_합성세트_품번생성() {
        List<VendorProductSet> sets = parseFixture();
        // U9013c/h + 소계 10000 → 합성 세트 U9013(main) + 부속 U9013_c/h(P6)
        VendorProductSet u9013 = one(sets, "U9013", SET_BASIS);
        assertEquals("수전부속", u9013.categoryLarge());
        assertEquals(0, new BigDecimal("10000").compareTo(u9013.setPrice()), "세트가=소계");
        assertEquals(2, u9013.parts().size());
        assertEquals(0, new BigDecimal("5000").compareTo(part(u9013, "U9013_c").unitPrice()));
        assertEquals(0, new BigDecimal("5000").compareTo(part(u9013, "U9013_h").unitPrice()));

        // 제품코드 없는 신형(U9015MC/MH)도 품번만으로 합성 세트 생성
        VendorProductSet u9015m = one(sets, "U9015M", SET_BASIS);
        assertEquals(0, new BigDecimal("24000").compareTo(u9015m.setPrice()));
    }

    @Test
    void 세트시트_조합행은_구성부속과_함께_세트화() {
        List<VendorProductSet> sets = parseFixture();
        // U9510 = 43u9023c + 43ds1500 + 43u9310n + 43u0630 (P7)
        VendorProductSet u9510 = one(sets, "U9510", SET_BASIS);
        assertEquals(0, new BigDecimal("20000").compareTo(u9510.setPrice()));
        assertEquals(4, u9510.parts().size());
        assertNotNull(part(u9510, "U9510_43u9023c"));

        // U9310(건+행거)도 조합 → main=U9310(5000) + 부속 2건. 단가표의 건 단품(4500)과 basis로 분리 보존
        VendorProductSet u9310 = one(sets, "U9310", SET_BASIS);
        assertEquals(0, new BigDecimal("5000").compareTo(u9310.setPrice()));
        assertEquals(2, u9310.parts().size());
    }

    @Test
    void 세트시트_단품_품번정규화와_제품코드폴백() {
        List<VendorProductSet> sets = parseFixture();
        // OEM 하이픈·소문자 품번 정규화(P9): U-9110150a → U9110150A
        assertEquals(0, new BigDecimal("8500").compareTo(one(sets, "U9110150A", SET_BASIS).setPrice()));
        // 품번패턴 아님("1.5m(OEM)") → 제품코드 폴백(P8). 단 품번 매핑이 있는 43u04110은 U04110으로(P14 병합)
        assertEquals(0, new BigDecimal("3000").compareTo(one(sets, "U04110", SET_BASIS).setPrice()));
        // 가격 없는 행은 0 + "(가격없음)" 표기(D8)
        VendorProductSet pipe = one(sets, "43u91p300", SET_BASIS);
        assertEquals(0, BigDecimal.ZERO.compareTo(pipe.setPrice()));
        assertTrue(pipe.main().productName().endsWith("(가격없음)"));
    }

    // ===== 부속 단가표 (P5·P8·P9) =====

    @Test
    void 단가표_단품과_품번재사용_전산코드폴백() {
        List<VendorProductSet> sets = parseFixture();
        VendorProductSet u9111 = one(sets, "U9111", PRICE_BASIS);
        assertEquals("수전부속", u9111.categoryLarge());
        assertEquals("수동폽업", u9111.categorySmall());
        assertEquals(0, new BigDecimal("9500").compareTo(u9111.setPrice()));

        // U9310이 건(43u9310n)·행거(43u0630) 두 행에 재사용 → 행거는 전산코드 폴백(P8)
        assertEquals(0, new BigDecimal("4500").compareTo(one(sets, "U9310", PRICE_BASIS).setPrice()));
        assertEquals(0, new BigDecimal("500").compareTo(one(sets, "43u0630", PRICE_BASIS).setPrice()));

        // 니쁠 꼬리 블록(품번 없음 → 전산코드, P8)
        assertEquals(0, new BigDecimal("1500").compareTo(one(sets, "43u94p65", PRICE_BASIS).setPrice()));
    }

    // ===== C-2 비고 내용별 분류 (규격=specs / 상태=remark / 속성=description / 매입처=미저장) =====

    @Test
    void C2_비고분류_규격은specs_상태는remark_속성은description() {
        List<VendorProductSet> sets = parseFixture();

        // 규격 → specs: 세트 시트 OEM 단품 "45mm", 합성 세트 부속 "15파이"
        VendorProductSet valve = one(sets, "U942245", SET_BASIS);
        assertEquals("45mm", valve.main().specs());
        assertNull(valve.main().remark());
        assertEquals("15파이", part(one(sets, "U9013", SET_BASIS), "U9013_c").specs());

        // 상태 → remark: U9014M 냉/온 부속 "단종 예정"
        VendorParsedItem u9014c = part(one(sets, "U9014M", SET_BASIS), "U9014M_c");
        assertNotNull(u9014c.remark());
        assertTrue(u9014c.remark().contains("단종"));
        assertNull(u9014c.specs());

        // 속성 → description: 조합 세트 U9310 "3기능"
        VendorProductSet u9310 = one(sets, "U9310", SET_BASIS);
        assertEquals("3기능", u9310.main().description());
        assertNull(u9310.main().remark());

        // 단가표 비고(용도 설명)는 description: U9013C "손빨래 수전"
        VendorProductSet u9013c = one(sets, "U9013C", PRICE_BASIS);
        assertEquals("손빨래 수전", u9013c.main().description());
        assertNull(u9013c.main().remark());
    }

    @Test
    void C2_매입처비고는_저장하지_않는다() {
        List<VendorProductSet> sets = parseFixture();

        // 세트 시트 욕조왕 블록 비고 "한양"/"E.L" → 미저장 (B 잔여 description은 유지)
        VendorProductSet wang = one(sets, "43u0520cr", SET_BASIS);
        assertNull(wang.main().remark());
        assertNull(wang.main().specs());
        assertNull(one(sets, "43udscr65", SET_BASIS).main().remark());

        // 분계표 A열 매입처(한양/킴스코) → remark 미저장
        assertNull(one(sets, "G-0130", "분계표").main().remark());
        assertNull(one(sets, "T-0130", "분계표").main().remark());
    }

    // ===== 신규 OEM 부속 단가표 (§11-1 P12~P16) =====

    @Test
    void OEM단가표_품번정규화_소분류유도_비고보존() {
        List<VendorProductSet> sets = parseOemFixture();
        // U-942245 → U942245(P9) — 세트 시트 OEM 항목과 같은 코드로 나와 upsert 병합(P13)
        VendorProductSet valve = one(sets, "U942245", OEM_BASIS);
        assertEquals("수전부속", valve.categoryLarge());
        assertEquals("일체형 앵글밸브", valve.categorySmall(), "소분류=품명 괄호 앞(P15)");
        assertEquals(0, new BigDecimal("2300").compareTo(valve.setPrice()));
        assertEquals("1차 입고분", valve.main().remark(), "비고 보존(R7 잠정)");
        assertFalse(valve.needsReview());

        // 단종 10종도 적재 + 비고 '단종' 보존(P16)
        VendorProductSet trap = one(sets, "U9240D", OEM_BASIS);
        assertEquals(0, new BigDecimal("4400").compareTo(trap.setPrice()));
        assertEquals("단종", trap.main().remark());
    }

    @Test
    void OEM단가표_21종적재_조합예시스킵_검수플래그없음() {
        List<VendorProductSet> sets = parseOemFixture().stream()
                .filter(s -> OEM_BASIS.equals(s.priceBasis())).toList();
        assertEquals(21, sets.size(), "항목 21종(하단 조합 예시 6행 스킵, P16)");
        assertTrue(sets.stream().noneMatch(s -> s.setPrice().compareTo(new BigDecimal("19200")) == 0),
                "조합 예시(19,200 등) 미적재");
        // 코드 엇갈림 2종은 세트 시트 폴백을 품번으로 매핑해 병합(P14 병합 결정) → 검수플래그 없음
        assertTrue(sets.stream().noneMatch(VendorProductSet::needsReview));
    }

    @Test
    void P14병합_세트시트_폴백행도_품번코드로_적재된다() {
        List<VendorProductSet> sets = parseFixture();
        // 종전 43u04110/43u944265(전산코드 폴백) → U04110/U944265(품번) — OEM 시트 행과 upsert 자연 병합
        assertEquals(0, new BigDecimal("3000").compareTo(one(sets, "U04110", SET_BASIS).setPrice()));
        assertNotNull(one(sets, "U944265", SET_BASIS));
        assertTrue(byCode(sets, "43u04110").isEmpty(), "구 전산코드 폴백행 잔존 없음");
        assertTrue(byCode(sets, "43u944265").isEmpty());
    }

    @Test
    void 공통품번은_두시트에서_같은코드로_나와_basis로_분리된다() {
        List<VendorProductSet> sets = parseFixture();
        // U9111이 세트 시트(9500)·단가표(9500) 모두 등장 — 같은 코드 → DB upsert 1행 + 가격 2행(P5)
        List<VendorProductSet> u9111 = byCode(sets, "U9111");
        assertEquals(2, u9111.size());
        assertEquals(2, u9111.stream().map(VendorProductSet::priceBasis).distinct().count());
        assertTrue(u9111.stream().allMatch(s -> "수전부속".equals(s.categoryLarge())));
    }
}
