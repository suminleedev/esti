package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 소변기·수채 시트 전용 검증(parseUrinalSinkSheet).
 *
 * <p>한 시트("소변기, 수채")에 "3. 소변기"·"4. 소제싱크(수채)" 두 서브테이블이 세로로 쌓여 있고
 * 슬롯 구성/計 컬럼 위치가 서로 다르다.</p>
 * <ul>
 *   <li>req1 — 대분류 분리: categoryLarge가 시트명 통째("소변기, 수채")가 아니라 서브테이블별로 소변기 / 수채로 나뉜다.</li>
 *   <li>req2 — 부속/단가 정확화: 수채 서브테이블 부속명(수채가랑/수채트랩)·단가·計(J)가 소변기 헤더(스퍼드…/計 M)에
 *       오염되지 않고 정확히 들어간다.</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBUrinalSinkSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> setsOf(String categoryLarge) {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> categoryLarge.equals(s.categoryLarge()))
                .toList();
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 대표품목 미발견"));
    }

    private VendorParsedItem part(VendorProductSet s, String label) {
        return s.parts().stream()
                .filter(p -> label.equals(p.productName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " 부속 미발견: "
                        + s.parts().stream().map(VendorParsedItem::productName).toList()));
    }

    @Test
    void req1_대분류가_소변기_수채로_분리되고_시트명통째는_없음() {
        List<VendorProductSet> all = parser.parseSets(SAMPLE).stream()
                .filter(s -> s.categoryLarge() != null && s.categoryLarge().contains("소변기"))
                .toList();
        assumeTrue(!all.isEmpty(), "샘플 없음 스킵");

        // 시트명 통째("소변기, 수채")로 저장된 세트가 없어야 한다
        assertTrue(all.stream().noneMatch(s -> s.categoryLarge().contains(",")),
                "대분류에 시트명 통째 잔존: "
                        + all.stream().map(VendorProductSet::categoryLarge).distinct().toList());

        assertEquals(13, setsOf("소변기").size(), "소변기 13세트");
        assertEquals(2, setsOf("수채").size(), "수채 2세트");
    }

    @Test
    void 소변기_U135는_스퍼드_후렌지_부속과_세트가95500() {
        List<VendorProductSet> urinals = setsOf("소변기");
        VendorProductSet u135 = byCode(urinals, "U135");

        assertEquals(0, new BigDecimal("95500").compareTo(u135.setPrice()), "計=95500");
        // 도기 80000 + 스퍼드 3500 + 후렌지 12000 = 95500
        assertEquals(0, new BigDecimal("3500").compareTo(part(u135, "스퍼드").unitPrice()));
        assertEquals(0, new BigDecimal("12000").compareTo(part(u135, "후렌지").unitPrice()));

        // 도기는 본품(세트가 주축)
        assertTrue(u135.parts().stream().anyMatch(p ->
                p.productName().startsWith("도기")
                        && VendorParsedItem.RELATION_MAIN.equals(p.relationType())));
    }

    @Test
    void req2_수채_SS131은_수채가랑_수채트랩부속_계122000이고_소변기슬롯_오염없음() {
        List<VendorProductSet> sinks = setsOf("수채");
        VendorProductSet ss131 = byCode(sinks, "SS131");

        // 計(J)=122000 정확 반영 (이전엔 計 컬럼이 M으로 어긋나 (가격없음) 처리되던 버그)
        assertEquals(0, new BigDecimal("122000").compareTo(ss131.setPrice()), "計=122000");

        // 수채 전용 부속명/단가 (소변기 헤더의 스퍼드/후렌지가 아니어야 함)
        assertEquals(0, new BigDecimal("12000").compareTo(part(ss131, "수채가랑").unitPrice()));
        assertEquals(0, new BigDecimal("20000").compareTo(part(ss131, "수채트랩").unitPrice()));
        List<String> names = ss131.parts().stream().map(VendorParsedItem::productName).toList();
        assertFalse(names.contains("스퍼드"), "소변기 슬롯 오염: " + names);
        assertFalse(names.contains("후렌지"), "소변기 슬롯 오염: " + names);

        // 計 = 부속 단가 합
        BigDecimal sum = ss131.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, ss131.setPrice().compareTo(sum), "計=부속합");
    }

    @Test
    void 소변기_IU302E_슬롯의_설명텍스트는_부속이아니라_description에_저장() {
        List<VendorProductSet> urinals = setsOf("소변기");
        VendorProductSet iu302 = byCode(urinals, "IU302E");

        // H칸 "후렌지/스프레다 포함"은 코드가 아니므로 부속(스퍼드)으로 만들지 않고 description에 보존
        // (C-2로 P열 비고가 " / "로 병합될 수 있어 앞부분 일치로 확인)
        assertTrue(iu302.main().description().startsWith("후렌지/스프레다 포함"), iu302.main().description());
        List<String> names = iu302.parts().stream().map(VendorParsedItem::productName).toList();
        assertFalse(names.contains("스퍼드"), "설명 텍스트가 부속으로 잘못 적재됨: " + names);
        // 도기(4su302wt 95000)만 부속, 計=95000
        assertEquals(0, new BigDecimal("95000").compareTo(iu302.setPrice()));
    }

    @Test
    void C2_비고는_description으로_병합_수집() {
        // U135: P열 비고(노출/매립 감지기 코드 안내) → description(C-2 결정 11)
        VendorProductSet u135 = byCode(setsOf("소변기"), "U135");
        assertNotNull(u135.main().description());
        assertTrue(u135.main().description().contains("노출 감지기"), u135.main().description());

        // 수채 서브테이블 비고도 재인식된 컬럼에서 수집(SS131)
        String ss131 = byCode(setsOf("수채"), "SS131").main().description();
        assertNotNull(ss131);
        assertTrue(ss131.contains("세트로 무조건 포함"), ss131);

        // 비고 없는 제품은 description 오염 없음
        assertNull(byCode(setsOf("소변기"), "IU374S").main().description());
    }

    @Test
    void 수채_SS132_세트가134000_품종_수채_carryforward() {
        List<VendorProductSet> sinks = setsOf("수채");
        VendorProductSet ss132 = byCode(sinks, "SS132");
        assertEquals(0, new BigDecimal("134000").compareTo(ss132.setPrice()));
        // B열 품종(수채)이 SS132행엔 비어 있어 carry-forward로 채워져야 함
        assertEquals("수채", ss132.categorySmall());
    }
}
