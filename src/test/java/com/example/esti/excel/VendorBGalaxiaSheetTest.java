package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 갈라시아 시트 전용 검증(parseGalaxiaSheet).
 *
 * <p>갈라시아는 실제 세면기 제품이라 표시를 보강한다(단순 표시 목적).</p>
 * <ul>
 *   <li>req1 — 소분류=갈라시아. (대분류는 시트명 "갈라시아" 유지 — categoryLarge가 이미지 매칭 키 겸용이라
 *       바꾸면 갈라시아 임베디드 이미지가 끊기므로 표시는 productName/소분류로만 보강)</li>
 *   <li>req2 — 메인 productName에 품번 앞 "갈라시아 세면기" 부여(이전엔 품번만 저장).</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBGalaxiaSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> galaxiaSets() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "갈라시아".equals(s.categoryLarge()))
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
    void req1_소분류는_갈라시아_대분류는_시트명유지() {
        VendorProductSet art = byCode(galaxiaSets(), "art6103");
        assertEquals("갈라시아", art.categorySmall(), "소분류=갈라시아");
        assertEquals("갈라시아", art.categoryLarge(), "대분류는 시트명 유지(이미지 매칭 보존)");
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
