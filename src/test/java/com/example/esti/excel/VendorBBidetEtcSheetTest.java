package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 비데·기타 시트 전용 검증(parseBidetEtcSheet).
 *
 * <p>한 시트("비데, 기타")에 "5. 비데"·"6. 기타" 두 서브테이블이 세로로 쌓여 있고 컬럼 배치가 다르다
 * (비데: 품번=B·스펙 없음 / 기타: 품번=D·스펙=E). 부속 없는 단일행 제품이다.</p>
 * <ul>
 *   <li>req1 — 대분류 분리: categoryLarge가 시트명 통째("비데, 기타")가 아니라 비데 / 기타로 나뉜다.</li>
 *   <li>req2 — 비데 소분류=비데 고정.</li>
 *   <li>req3 — 기타: 품번(D) 같고 제품코드(G)만 다른 전기/배터리 변형은 품번 뒤 구분글자(e/b)로 둘 다 보존.</li>
 *   <li>req4 — 기타: 스펙(E)을 제품명 뒤 괄호로, 비고(I)는 description으로.</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBBidetEtcSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> setsOf(String categoryLarge) {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> categoryLarge.equals(s.categoryLarge()))
                .toList();
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 대표품목 미발견: "
                        + sets.stream().map(s -> s.main().productCode()).toList()));
    }

    @Test
    void req1_대분류가_비데_기타로_분리되고_시트명통째는_없음() {
        List<VendorProductSet> all = parser.parseSets(SAMPLE).stream()
                .filter(s -> s.categoryLarge() != null
                        && (s.categoryLarge().contains("비데") || s.categoryLarge().equals("기타")))
                .toList();
        assumeTrue(!all.isEmpty(), "샘플 없음 스킵");

        // 시트명 통째("비데, 기타")로 저장된 세트가 없어야 한다
        assertTrue(all.stream().noneMatch(s -> s.categoryLarge().contains(",")),
                "대분류에 시트명 통째 잔존: "
                        + all.stream().map(VendorProductSet::categoryLarge).distinct().toList());

        assertEquals(6, setsOf("비데").size(), "비데 6세트");
        assertEquals(14, setsOf("기타").size(), "기타 14세트");
    }

    @Test
    void req2_비데는_소분류도_비데() {
        List<VendorProductSet> bidets = setsOf("비데");
        assertTrue(bidets.stream().allMatch(s -> "비데".equals(s.categorySmall())),
                "비데 소분류가 모두 '비데'가 아님: "
                        + bidets.stream().map(VendorProductSet::categorySmall).distinct().toList());

        VendorProductSet dsb = byCode(bidets, "DSB-5420");
        assertEquals("비데", dsb.categoryLarge());
        assertEquals("비데", dsb.categorySmall());
        assertEquals("비데 DSB-5420", dsb.main().productName(), "제품명 앞 '비데' 부기");
        assertEquals(0, new BigDecimal("120000").compareTo(dsb.setPrice()));
    }

    @Test
    void 비데_비고는_description에_저장하고_remark는_비움() {
        List<VendorProductSet> bidets = setsOf("비데");
        VendorProductSet is24 = byCode(bidets, "IS-24");
        assertEquals("방수 비데", is24.main().description(), "비데 비고→description");
        assertNull(is24.main().remark(), "비데 비고는 remark가 아닌 description으로");

        VendorProductSet dsb6035 = byCode(bidets, "DSB-6035R");
        assertEquals("리모컨 타입", dsb6035.main().description());
    }

    @Test
    void req3_기타_전기배터리는_품번뒤_구분글자로_둘다보존() {
        List<VendorProductSet> etc = setsOf("기타");

        // 품번 E102가 전기(46ts201e)/배터리(46ts201b) 두 행으로 나뉘어 e/b 접미 → 둘 다 존재(유실 0)
        VendorProductSet electric = byCode(etc, "E102e");
        VendorProductSet battery = byCode(etc, "E102b");
        assertEquals(0, new BigDecimal("77000").compareTo(electric.setPrice()), "전기 77000");
        assertEquals(0, new BigDecimal("76000").compareTo(battery.setPrice()), "배터리 76000");

        // 원본 품번(E102)이 그대로 코드로 남아 충돌하지 않아야 함
        assertTrue(etc.stream().noneMatch(s -> "E102".equals(s.main().productCode())),
                "원본 품번 E102가 접미 없이 잔존(충돌 위험)");
    }

    @Test
    void req4_기타_스펙은_제품명괄호_비고는_description() {
        List<VendorProductSet> etc = setsOf("기타");
        VendorProductSet hd = byCode(etc, "HD101G");

        assertEquals("핸드 드라이어 (일반) HD101G", hd.main().productName(), "소분류 (스펙) 품목코드");
        assertEquals("보급형", hd.main().description(), "비고→description");
        assertNull(hd.main().remark(), "기타 비고는 remark가 아닌 description으로");
        assertEquals("핸드 드라이어", hd.categorySmall(), "소분류=품종");
    }

    @Test
    void req3req4_배터리행은_품종_품번_carryforward하고_타입은_description으로_구분() {
        List<VendorProductSet> etc = setsOf("기타");
        VendorProductSet electric = byCode(etc, "E102e");
        VendorProductSet battery = byCode(etc, "E102b");

        // 전기: 소분류 (스펙) 품목코드, 타입은 비고→description
        assertEquals("소변기 매립감지기 (소형) E102", electric.main().productName());
        assertEquals("전기 타입: 120x120", electric.main().description());

        // 배터리행은 품종(A)·품번(D) 병합 빈칸이라 carry-forward, 타입은 description으로 구분
        assertEquals("소변기 매립감지기", battery.categorySmall());
        assertEquals("배터리 타입: 120x120", battery.main().description());
    }
}
