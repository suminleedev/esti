package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T0 검증 — 시트 판별 개편(`docs/plan-b-format-2026.md` §5 T0).
 *
 * <p>최신본(2026, 14시트)이 신양식 패밀리로 가고, 숨김·{@code (삭제)} 시트가 스킵되며,
 * <b>구본(2020, 9시트) 판별은 하나도 바뀌지 않는지</b>(D-B1 구·신 공존)를 고정한다.
 */
class VendorB2026SheetFamilyTest {

    private static final Path NEW_BOOK = Path.of("docs/samples/B사 단가표_2026최신.xlsx");
    private static final Path OLD_BOOK = Path.of("docs/samples/B사 단가표_sample.xlsx");
    private static final Path OLD_FITTING = Path.of("docs/samples/B사 test (수전부속).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    @Test
    void 최신본_14시트가_신양식_패밀리로_판별되고_숨김시트는_스킵된다() {
        requireSample(NEW_BOOK);
        Map<String, String> f = parser.diagnoseSheetFamilies(NEW_BOOK);

        assertEquals(14, f.size(), "최신본 시트 수");

        // 도기 3시트 — 시트명은 구본과 같고 레이아웃만 다르다. 레이아웃 판별이 살아 있어야 한다.
        assertEquals("TOILET_V2", f.get("양변기"));
        assertEquals("WASHBASIN_V2", f.get("세면기"));
        assertEquals("URINAL_SINK_V2", f.get("소변기, 수채"));

        // 시트명이 바뀐 3시트 — 구본 분기에 걸리면 안 된다.
        assertEquals("FAUCET_V2", f.get("수전금구류"));
        assertEquals("ACCESSORY_V2", f.get("액세사리류"),
                "'액'세사리는 구본 '악'세사리 분기(SET_TOTAL)에 걸리지 않아야 한다");
        assertEquals("FITTING_CATALOG_V2", f.get("부속류"));

        // 매핑표 — contains(\"수전금구\") 분기보다 먼저 걸려야 한다(FAUCET_GENERAL로 새면 오적재).
        assertEquals("FAUCET_CODEMAP_V2", f.get("수전금구 품번 및 품목코드"));

        // 신규 카테고리 4시트
        assertEquals("BATH_V2", f.get("바스 선반(직영)"));
        assertEquals("BATH_V2", f.get("바스 파티션,욕조(직영)"));
        assertEquals("BATH_V2", f.get("바스 천정재(직영)"));
        assertEquals("BATH_V2", f.get("바스 욕실장,거울(직영)"));

        // 숨김 + '(삭제)' 표기 → 스킵 (D-B8)
        assertEquals("SKIPPED", f.get("(삭제)바스 공통품목(의왕)"));
        assertEquals("SKIPPED", f.get("(삭제)바스 액세서리(의왕)"));

        // 최신본에서도 양식이 그대로인 시트는 구본 경로를 계속 탄다.
        assertEquals("BIDET_ETC", f.get("비데, 기타"));
    }

    @Test
    void 구본_9시트_판별은_하나도_바뀌지_않는다() {
        requireSample(OLD_BOOK);
        Map<String, String> f = parser.diagnoseSheetFamilies(OLD_BOOK);

        assertEquals(Map.of(
                "양변기", "TOILET",
                "세면기", "WASHBASIN",
                "소변기, 수채", "URINAL_SINK",
                "갈라시아", "GALAXIA",
                "비데, 기타", "BIDET_ETC",
                "수전금구", "FAUCET_GENERAL",
                "수전금구(국산 부속 기준)", "FAUCET_PARTS",
                "수전 부속(세트)", "FITTING_SET",
                "악세사리 단가표", "SET_TOTAL"
        ), f);
    }

    @Test
    void 구본_수전부속_픽스처_판별도_유지된다() {
        requireSample(OLD_FITTING);
        Map<String, String> f = parser.diagnoseSheetFamilies(OLD_FITTING);

        assertEquals("BREAKDOWN", f.get("분계표"));
        assertEquals("FITTING_SET", f.get("수전 부속(세트)"));
        assertEquals("FITTING_PRICE", f.get("부속 단가표"));
    }

    /**
     * 최신본 적재 진척도 — 시트별 세트 수. Task가 하나 끝날 때마다 이 표에 한 줄이 채워진다.
     * 미구현 시트가 0이 아니게 되면 구본 파서로 샜다는 뜻이다
     * (개편 전에는 '액세사리류' 232건 + '수전금구류' 264건이 잘못된 구조로 들어왔다).
     */
    @Test
    void 최신본_적재는_구현된_시트에서만_나온다() {
        requireSample(NEW_BOOK);
        List<VendorProductSet> sets = parser.parseSets(NEW_BOOK);

        Map<String, Long> bySheet = sets.stream()
                .collect(Collectors.groupingBy(VendorProductSet::sheetName, Collectors.counting()));

        assertEquals(Map.of(
                "비데, 기타", 28L,   // 양식 그대로 → 구본 경로(BIDET_ETC)
                "양변기", 39L,       // T1
                "세면기", 56L,       // T2
                "소변기, 수채", 11L,  // T3
                "액세사리류", 175L,   // T4
                "부속류", 124L,       // T5
                "수전금구류", 264L    // T6
        ), bySheet, "구현되지 않은 시트는 결과에 나타나지 않아야 한다");

        // 숨김·(삭제) 시트는 어떤 형태로도 결과에 나타나지 않는다.
        assertTrue(sets.stream().noneMatch(s -> s.sheetName() != null && s.sheetName().startsWith("(삭제)")));
    }

    @Test
    void 구본_적재결과는_기존과_동일하다() {
        requireSample(OLD_BOOK);
        List<VendorProductSet> sets = parser.parseSets(OLD_BOOK);

        // 구본 대표 케이스가 살아 있으면 도기 3시트가 V2로 오판되지 않은 것이다.
        assertTrue(sets.stream().anyMatch(s -> s.main() != null && "MC921".equals(s.main().productCode())),
                "구본 양변기(가로 슬롯형) MC921이 파싱돼야 한다");
        assertTrue(sets.stream().anyMatch(s -> "갈라시아".equals(s.sheetName())),
                "구본 갈라시아 시트가 계속 적재돼야 한다");
    }
}
