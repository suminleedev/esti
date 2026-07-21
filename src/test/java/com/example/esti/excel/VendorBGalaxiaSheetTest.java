package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 갈라시아 시트 전용 검증(parseGalaxiaSheet).
 *
 * <p>갈라시아는 실제 세면기 제품이라 표시를 보강한다(단순 표시 목적).</p>
 * <ul>
 *   <li>req1 — 소분류=갈라시아, 대분류=세면기(갈라시아)(D46). §13 sheetName 분리로 이미지 매칭이
 *       categoryLarge와 독립(sheetName="갈라시아")이 되어 대분류를 정제값으로 바꿔도 이미지가 유지된다.</li>
 *   <li>req2 — 메인 productName에 품번 앞 "갈라시아 세면기" 부여(이전엔 품번만 저장).</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBGalaxiaSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> galaxiaSets() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "갈라시아".equals(s.sheetName()))
                .toList();
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 대표품목 미발견"));
    }

    @Test
    void req2_메인_productName은_갈라시아세면기_접두_품번코드는_유지() {
        VendorProductSet art = byCode(galaxiaSets(), "art6103");

        assertEquals("art6103", art.main().productCode(), "품번 코드는 그대로");
        assertEquals("갈라시아 세면기 art6103", art.main().productName(), "표시명에 접두 부여");
    }

    @Test
    void req1_소분류는_갈라시아_대분류는_세면기갈라시아_이미지키는_시트명() {
        VendorProductSet art = byCode(galaxiaSets(), "art6103");
        assertEquals("갈라시아", art.categorySmall(), "소분류=갈라시아");
        assertEquals("세면기(갈라시아)", art.categoryLarge(), "대분류=세면기(갈라시아)(D46)");
        assertEquals("갈라시아", art.sheetName(), "이미지 매칭 키=원본 시트명");
    }

    @Test
    void 세트가는_도기_부속_합이고_부속2개_도기는_본품() {
        VendorProductSet art = byCode(galaxiaSets(), "art6103");
        // 도기 367000 + 부속 63000 = 430000
        assertEquals(0, new BigDecimal("430000").compareTo(art.setPrice()));
        assertEquals(2, art.parts().size());
        assertTrue(art.parts().stream()
                .anyMatch(p -> VendorParsedItem.RELATION_MAIN.equals(p.relationType())), "도기=본품");
    }
}
