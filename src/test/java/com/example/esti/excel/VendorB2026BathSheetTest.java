package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T8 검증 — 최신본(2026) 바스 4시트(직영). 최신본에서 새로 생긴 카테고리다.
 *
 * <p>네 시트가 같은 규약이되 <b>천정재만 이미지 컬럼이 없어 한 칸씩 왼쪽</b>이다.
 * 가격은 3단(판매점/인테리어/소비자)이지만 판매점 단가만 저장한다(D-B3).
 */
class VendorB2026BathSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (바스).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("품목 미발견: " + code));
    }

    @Test
    void 네_시트가_모두_대분류_바스로_들어간다() {
        List<VendorProductSet> sets = parse();

        assertTrue(sets.stream().allMatch(s -> "바스".equals(s.categoryLarge())));
        // 선반은 원본 18행이지만 4건이 액세사리류와 중복이라 여기서 빠진다(§8 잔여 ⑦, 액세사리류가 정본)
        assertEquals(Map.of(
                "바스 선반(직영)", 14L,
                "바스 파티션,욕조(직영)", 10L,
                "바스 천정재(직영)", 7L,
                "바스 욕실장,거울(직영)", 35L
        ), sets.stream().collect(Collectors.groupingBy(VendorProductSet::sheetName, Collectors.counting())));
    }

    @Test
    void 판매점_단가만_저장한다() {
        // 선반 5행: 판매점 <PRICE> / 인테리어 <PRICE> / 소비자 <PRICE>
        VendorProductSet s = byCode(parse(), "<CODE>");
        assertEquals(new BigDecimal("<PRICE>"), s.setPrice());
    }

    @Test
    void 이미지_컬럼이_없는_천정재도_한_칸_밀려_읽힌다() {
        // 천정재는 B가 전산코드다(다른 시트는 B=이미지, C=전산코드). 위치를 하드코딩하면 전부 깨진다.
        VendorProductSet s = byCode(parse(), "<CODE>");

        assertEquals("천정재 메인판(평판/1300*1750)-거광이앤지", s.main().productName());
        assertEquals(new BigDecimal("<PRICE>"), s.setPrice());
        assertEquals("평판/1300*1750", s.main().specs());
        assertEquals("천정재", s.categorySmall());
    }

    @Test
    void 이미지_컬럼의_품목군_라벨을_소분류로_쓴다() {
        // 파티션·욕조 시트는 이미지 컬럼에 '샤워파티션'/'민자형' 라벨이 섞여 온다(그림은 글자가 없다).
        List<VendorProductSet> sets = parse();

        assertEquals("샤워파티션", byCode(sets, "<CODE>").categorySmall());
        assertEquals("민자형", byCode(sets, "<CODE>").categorySmall());
        assertEquals("선반", byCode(sets, "<CODE>").categorySmall(), "라벨이 없으면 시트명에서 뽑는다");
    }

    @Test
    void 비고_오른쪽의_라벨_없는_단종_컬럼도_읽는다() {
        // K=비고와 별개로 L열에 '단종'이 따로 들어온다. 헤더가 없어 놓치기 쉽다.
        List<VendorProductSet> sets = parse();

        assertEquals("단종", byCode(sets, "<CODE>").main().remark());
        VendorProductSet s = byCode(sets, "<CODE>");
        assertEquals("단종", s.main().remark());
        assertTrue(s.main().description().contains("노블리젠시"), "세트명은 설명으로 남는다");
    }

    @Test
    void 세트명_컬럼의_ea는_설명으로_새지_않는다() {
        // 욕실장 F열은 세트명이지만 'ea'(단위)도 섞여 들어온다.
        VendorProductSet s = byCode(parse(), "<CODE>");
        assertEquals("온라인 스퀘어스케치", s.main().description());
    }

    @Test
    void 액세사리류와_중복인_4건은_바스에서_빠진다() {
        // 같은 제품이 두 시트에 실려 있는데 코드 축이 달라(액세사리류=품번 AT1322S, 바스=전산코드 <CODE>)
        // upsert가 병합하지 못하고 대분류만 다른 2건이 생긴다. 액세사리류를 정본으로 삼는다(§8 잔여 ⑦).
        List<VendorProductSet> sets = parse();

        for (String code : List.of("<CODE>", "<CODE>", "<CODE>", "<CODE>")) {
            assertTrue(sets.stream().noneMatch(s -> s.main() != null && code.equals(s.main().productCode())),
                    "액세사리류 정본과 중복 → 바스에서 제외되어야 한다: " + code);
        }
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        // 4시트 71행 - 시트 내 중복 전산코드 1건 - 액세사리류 중복 4건(§8 잔여 ⑦)
        assertEquals(66, sets.size(), "4시트 71행 - 중복 1건 - 액세사리류 중복 4건");
        assertTrue(sets.stream().allMatch(s -> s.parts().isEmpty()), "부속 없는 단일 제품");
        assertEquals(sets.size(), sets.stream().map(s -> s.main().productCode()).distinct().count());
    }
}
