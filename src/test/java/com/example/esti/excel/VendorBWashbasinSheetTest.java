package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 세면기 시트 전용 검증(parseWashbasinSheet).
 *
 * <ul>
 *   <li>req1 — 원본 품목 유실 없음: 도자 종류만 다른 동일 품번(IL672E/IL674E)은 도기 코드 구분 글자로
 *       분기되어 업서트 충돌 없이 모두 보존된다(대표품번 전부 유일).</li>
 *   <li>req2 — 세트가: 도기(원홀)>도기(4"), 반다리>긴다리 기본형만 세트가에 반영. 둘 중 하나만 있으면 그 단가.
 *       하프고리/앙카볼트 등은 필수라 모두 반영. 비기본 도기·다리는 부속 보존(대체옵션)·세트가 미포함.</li>
 *   <li>req3 — 품번/슬롯코드의 "코드(설명)"에서 설명을 description으로 분리.</li>
 * </ul>
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBWashbasinSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> washbasinSets() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        return parser.parseSets(SAMPLE).stream()
                .filter(s -> "세면기".equals(s.categoryLarge()))
                .toList();
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 대표품목 미발견"));
    }

    /** 부속을 슬롯 라벨(productName)로 조회. */
    private VendorParsedItem part(VendorProductSet s, String label) {
        return s.parts().stream()
                .filter(p -> label.equals(p.productName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " 부속 미발견: "
                        + s.parts().stream().map(VendorParsedItem::productName).toList()));
    }

    @Test
    void req1_도자중복은_분기되어_모든_대표품번이_유일하고_원본품목_유실없음() {
        List<VendorProductSet> sets = washbasinSets();

        assertEquals(75, sets.size(), "세면기 제품코드행 75건 그대로");

        // 대표품번 전부 유일 → DB 업서트 충돌(유실) 없음
        Map<String, Long> byCount = sets.stream().collect(
                Collectors.groupingBy(s -> s.main().productCode(), Collectors.counting()));
        List<String> dups = byCount.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        assertTrue(dups.isEmpty(), "중복 대표품번 없어야 함: " + dups);

        // 도자 base 품번은 단독으로 남지 않고 구분 글자가 접미되어 분기
        assertTrue(sets.stream().noneMatch(s -> "IL672E".equals(s.main().productCode())));
        assertTrue(sets.stream().noneMatch(s -> "IL674E".equals(s.main().productCode())));
    }

    @Test
    void req2_도기원홀_4둘다있으면_원홀단가반영_4는대체옵션() {
        List<VendorProductSet> sets = washbasinSets();
        VendorProductSet il451 = byCode(sets, "IL451B");

        // 도기(원홀)65000 + 반다리4000 = 69000 (도기4"는 제외)
        assertEquals(0, new BigDecimal("69000").compareTo(il451.setPrice()));

        VendorParsedItem wonhol = part(il451, "도기(원홀)");
        assertEquals(VendorParsedItem.RELATION_MAIN, wonhol.relationType(), "원홀이 본품(세트가 포함)");
        assertNull(wonhol.remark());

        VendorParsedItem four = part(il451, "도기(4\")");
        assertEquals("대체옵션", four.remark(), "4\"는 대체옵션(세트가 미포함)");
        assertNotEquals(VendorParsedItem.RELATION_MAIN, four.relationType());

        assertNotNull(part(il451, "반다리"));
    }

    @Test
    void req2_도기_하나만있으면_그_단가반영() {
        List<VendorProductSet> sets = washbasinSets();
        // L551: 도기(4")만 존재(50000), 원홀 없음 → 50000
        VendorProductSet l551 = byCode(sets, "L551");
        assertEquals(0, new BigDecimal("50000").compareTo(l551.setPrice()));
        VendorParsedItem four = part(l551, "도기(4\")");
        assertEquals(VendorParsedItem.RELATION_MAIN, four.relationType(), "유일 도기는 본품");
        assertNull(four.remark());
    }

    @Test
    void req2_반다리_긴다리둘다있으면_반다리반영_긴다리는대체옵션_나머지는모두포함() {
        List<VendorProductSet> sets = washbasinSets();
        // L966: 도기원홀61000 + 반다리22500 + 하프고리2200 + 앙카볼트1500 = 87200 (긴다리24000 제외)
        VendorProductSet l966 = byCode(sets, "L966");
        assertEquals(0, new BigDecimal("87200").compareTo(l966.setPrice()));

        assertNull(part(l966, "반다리").remark(), "반다리 채택");
        assertEquals("대체옵션", part(l966, "긴다리").remark(), "긴다리 대체옵션");
        assertNull(part(l966, "하프고리").remark(), "하프고리 필수");
        assertNull(part(l966, "앙카볼트").remark(), "앙카볼트 필수");
    }

    @Test
    void req3_슬롯코드의_괄호설명이_description으로_분리됨() {
        List<VendorProductSet> sets = washbasinSets();
        // IL453: I=46ele1010b(수전/배터리식), L=AE4002(물비누통)
        VendorProductSet il453 = byCode(sets, "IL453");
        assertEquals(0, new BigDecimal("160000").compareTo(il453.setPrice()),
                "도기원홀50000+반다리90000+앙카볼트20000");

        VendorParsedItem bandari = part(il453, "반다리");
        assertEquals("수전/배터리식", bandari.description(), "괄호 설명 분리");
        assertTrue(bandari.productCode().contains("46ele1010b"), "코드는 괄호 제거: " + bandari.productCode());

        VendorParsedItem anka = part(il453, "앙카볼트");
        assertEquals("물비누통", anka.description());
        assertTrue(anka.productCode().contains("AE4002"));
    }

    @Test
    void req1_3_도자종류만_다른_동일품번은_도기코드_구분글자로_분기되고_도자명은description() {
        List<VendorProductSet> sets = washbasinSets();

        // IL672E(화려) G=4hl672awt / IL672E(클레이탄) G=4cl672awt → IL672Eh / IL672Ec
        VendorProductSet hwa = byCode(sets, "IL672Eh");
        VendorProductSet cle = byCode(sets, "IL672Ec");
        assertEquals("화려", hwa.main().description());
        assertEquals("클레이탄", cle.main().description());
        // 도기원홀59000 + 앙카볼트2000 = 61000
        assertEquals(0, new BigDecimal("61000").compareTo(hwa.setPrice()));
        assertEquals(0, new BigDecimal("61000").compareTo(cle.setPrice()));

        // IL674E(모노피) 4ml674awt / IL674E(길마위욕) 4jl674awt → IL674Em / IL674Ej
        VendorProductSet mono = byCode(sets, "IL674Em");
        VendorProductSet gil = byCode(sets, "IL674Ej");
        assertEquals("모노피", mono.main().description());
        assertEquals("길마위욕", gil.main().description());
        assertEquals(0, new BigDecimal("62000").compareTo(mono.setPrice()));
        assertEquals(0, new BigDecimal("62000").compareTo(gil.setPrice()));
    }
}
