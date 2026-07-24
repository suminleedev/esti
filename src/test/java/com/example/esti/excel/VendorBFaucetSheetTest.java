package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 수전금구 3-시트 전용 검증(parseFaucetGeneralSheet / parseFaucetPartsSheet).
 *
 * <p>같은 본품(품번)이 일반·국산·OEM 시트에 등장 → 대분류는 `수전금구`로 통합하고 가격만 price_basis(시트명)로 분리(§10).
 * 국산/OEM은 부속 구성만 다르고 본품가는 동일. 부속 출처는 부속 categorySmall(국산/OEM).</p>
 * <p>커밋 샘플에는 일반·국산만 있고 OEM은 없다(파서 경로는 국산과 동일). 샘플 없으면 스킵.</p>
 */
class VendorBFaucetSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");
    private static final String KOR = "수전금구(국산 부속 기준)";
    // OEM 원본(로컬 전용). 커밋 샘플엔 OEM 시트가 없어 별도 파일로 실측 검증(§15).
    private static final Path OEM_SAMPLE = Path.of("docs/samples/B사 test (수전금구OEM).xlsx");
    private static final String OEM = "수전금구(OEM 부속 기준)";

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> faucetByBasis(String priceBasis) {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "수전금구".equals(s.categoryLarge()) && priceBasis.equals(s.priceBasis()))
                .toList();
    }

    private List<VendorProductSet> oemSets() {
        requireSample(OEM_SAMPLE);
        return parser.parseSets(OEM_SAMPLE).stream()
                .filter(s -> "수전금구".equals(s.categoryLarge()) && OEM.equals(s.priceBasis()))
                .toList();
    }

    private VendorProductSet find(List<VendorProductSet> sets, String code) {
        return sets.stream().filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst().orElse(null);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream().filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " 미발견"));
    }

    private VendorParsedItem part(VendorProductSet s, String name) {
        return s.parts().stream().filter(p -> name.equals(p.productName()))
                .findFirst().orElseThrow(() -> new AssertionError(name + " 부속 미발견"));
    }

    @Test
    void 대분류는_수전금구로_통합되고_가격은_시트명_basis로_분리() {
        // 일반·국산 모두 categoryLarge="수전금구", price_basis만 시트명으로 다름 (시트명 통째 대분류 없음)
        VendorProductSet gen = byCode(faucetByBasis("수전금구"), "G-0110");
        VendorProductSet kor = byCode(faucetByBasis(KOR), "G-0110");

        assertEquals("수전금구", gen.categoryLarge());
        assertEquals("수전금구", kor.categoryLarge());
        assertEquals("수전금구", gen.priceBasis(), "일반 basis=수전금구");
        assertEquals(KOR, kor.priceBasis(), "국산 basis=시트명");
        assertEquals("G-01", gen.categorySmall(), "본품 소분류=시리즈(안정)");
        assertEquals("G-01", kor.categorySmall());
    }

    @Test
    void 일반시트_본품은_부속없이_대리점가만() {
        VendorProductSet gen = byCode(faucetByBasis("수전금구"), "G-0110");
        assertEquals(0, gen.parts().size(), "일반=본품 1건(부속 없음)");
        assertEquals(0, new BigDecimal("31000").compareTo(gen.setPrice()), "본품 대리점가 31000");
    }

    @Test
    void 국산세트_소계가_본품_부속합이고_부속출처는_국산() {
        VendorProductSet kor = byCode(faucetByBasis(KOR), "G-0110");
        assertEquals(0, new BigDecimal("54400").compareTo(kor.setPrice()), "국산 소계=세트가");

        // 본품 31000 + 부속 합(8800+8000+3300+3300=23400) = 54400
        BigDecimal partSum = kor.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("31000").add(partSum).compareTo(kor.setPrice()), "본품+부속=소계");

        assertEquals(4, kor.parts().size());
        assertTrue(kor.parts().stream().allMatch(p -> "국산".equals(p.categorySmall())), "부속 출처=국산");
        assertEquals(0, new BigDecimal("8800").compareTo(part(kor, "폽업").unitPrice()));
    }

    @Test
    void 앵글밸브_냉온_동일코드는_c_h로_분리() {
        VendorProductSet kor = byCode(faucetByBasis(KOR), "G-0110");
        // 냉수용/온수용 모두 부속코드 U9420 → 품번 충돌 방지로 c/h 접미
        assertEquals("G-0110_U9420c", part(kor, "앵글밸브(냉수용)_일체형").productCode());
        assertEquals("G-0110_U9420h", part(kor, "앵글밸브(온수용)_일체형").productCode());
    }

    @Test
    void 품번없이_제품코드만_있는_부속도_유실없이_적재() {
        // G-0113 국산: 후렉시블호스(냉/온)은 품번(C) 없이 제품코드(D=43sw154hl)만 → D를 코드로, 냉/온 c/h 분리
        VendorProductSet kor = byCode(faucetByBasis(KOR), "G-0113");
        assertEquals("G-0113_43sw154hlc", part(kor, "후렉시블호스(냉)").productCode());
        assertEquals("G-0113_43sw154hlh", part(kor, "후렉시블호스(온)").productCode());
    }

    @Test
    void C2_소계_오른쪽_구성부기는_본품_description() {
        // 소계행 H "(메탈호스, 일반헤드 포함)" → 본품 description(C-2 결정 12). 부기 없는 블록은 null 유지.
        assertEquals("(메탈호스, 일반헤드 포함)", byCode(faucetByBasis(KOR), "G-0514").main().description());
        assertNull(byCode(faucetByBasis(KOR), "G-0110").main().description());
    }

    // ── §15 OEM 원본 실측: 시리즈 병합·소계 상태·소계 누락·매입처 ──────────────

    @Test
    void OEM_시리즈_병합으로_A가_빈_본품도_유실없이_적재() {
        // E계열은 시리즈 "E"가 A628:A655 하나로 여러 블록을 덮어 대부분 본품 A가 빔.
        // A(시리즈) 존재로 본품을 판정하면 유실 → 소계 경계(위치)로 판정해 5건 전부 적재.
        List<VendorProductSet> oem = oemSets();
        for (String code : List.of("E 0610", "E 0810", "E0910", "E 0128", "E 1010b")) {
            assertNotNull(find(oem, code), "OEM 본품 유실: " + code);
        }
        // G-0510은 시리즈 병합(A147:A151)이 한 행 늦게 시작해 본품 A가 빔 → 정상 적재
        VendorProductSet g0510 = byCode(oem, "G-0510");
        assertEquals("원홀 세면기 수전", g0510.main().productName());
        assertEquals(0, new BigDecimal("57700").compareTo(g0510.setPrice()), "소계=세트가");
    }

    @Test
    void OEM_부속코드는_본품세트로_승격되지_않음() {
        // 종전 버그: G-0510 본품이 드롭되고 부속 폽업(U9110150a)이 가짜 세트로 승격됨 → 이제 부속으로만 존재.
        List<VendorProductSet> oem = oemSets();
        assertNull(find(oem, "U9110150a"), "부속 U9110150a가 본품 세트로 승격되면 안 됨");
        VendorParsedItem popup = part(byCode(oem, "G-0510"), "폽업");
        assertEquals("G-0510_U9110150a", popup.productCode());
        assertEquals("OEM", popup.categorySmall(), "부속 출처=OEM");
    }

    @Test
    void OEM_소계행_생애주기_상태는_본품_remark() {
        // 단종/신제품 추가 등은 J(매입처)가 아니라 소계행 B열 → 본품 remark(C-2 상태=remark).
        List<VendorProductSet> oem = oemSets();
        assertEquals("단종", byCode(oem, "G-0210").main().remark());
        assertEquals("신제품 추가", byCode(oem, "E 0128").main().remark());
    }

    @Test
    void OEM_소계가_빈_S0146은_본품단가로_폴백() {
        // S 0146(단종)은 소계 G가 비어 세트가를 못 만들던 유일 케이스 → 본품 단가(윗 셀 165,000)로 폴백(D2).
        VendorProductSet s0146 = byCode(oemSets(), "S 0146");
        assertEquals(0, new BigDecimal("165000").compareTo(s0146.setPrice()), "소계 누락 → 본품 단가 폴백");
        assertEquals("단종", s0146.main().remark());
    }

    @Test
    void OEM_J열_매입처는_미저장() {
        // J=비고는 매입처(한양/대신…) → 저장 안 함(C-2 매입처 정책). G-0110 소계 B는 비어 remark=null.
        assertNull(byCode(oemSets(), "G-0110").main().remark(), "매입처는 remark로 새면 안 됨");
    }

    // ── 공용 파서 수정의 국산 시트 무회귀 + 국산도 동일 케이스 구제(재검증) ─────────

    @Test
    void 국산도_시리즈병합_A빈_본품_유실없이_적재() {
        // 국산 시트에도 같은 시리즈 병합 케이스가 있었음(GF 0130/GF 0230/G-0510/E계열) → 위치 기반 탐지로 구제.
        List<VendorProductSet> kor = faucetByBasis(KOR);
        for (String code : List.of("GF 0130", "GF 0230", "G-0510", "E 0810", "E 1010b")) {
            assertNotNull(find(kor, code), "국산 본품 유실: " + code);
        }
        assertEquals("단종", byCode(kor, "G-0210").main().remark(), "국산 소계 상태도 remark로 구제");
    }
}
