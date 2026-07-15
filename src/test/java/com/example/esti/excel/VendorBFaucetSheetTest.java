package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> faucetByBasis(String priceBasis) {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "수전금구".equals(s.categoryLarge()) && priceBasis.equals(s.priceBasis()))
                .toList();
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
}
