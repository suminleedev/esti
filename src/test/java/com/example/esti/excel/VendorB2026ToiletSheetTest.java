package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.ExpectedPrices.price;

/**
 * T1 검증 — 최신본(2026) 양변기 시트(세로 나열형).
 *
 * <p>구본은 세트 1건이 2행(제품코드행 + 대리점가행)이고 부속이 열로 펼쳐졌으나,
 * 최신본은 세트 1건이 N행이고 부속이 행으로 내려온다. 경계는 C(품목)이며 I(計)가 세트가다.
 */
class VendorB2026ToiletSheetTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 2026 (양변기).xlsx");

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parse() {
        requireSample(SAMPLE);
        return parser.parseSets(SAMPLE);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream()
                .filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("대표품목 미발견: " + code));
    }

    private Optional<VendorParsedItem> part(VendorProductSet set, String name) {
        return set.parts().stream().filter(p -> name.equals(p.productName())).findFirst();
    }

    private BigDecimal sumOf(VendorProductSet set) {
        return set.parts().stream().map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void 세로_나열형_세트는_計와_구성합이_일치하고_첫행이_MAIN이다() {
        VendorProductSet s = byCode(parse(), "IC552EF");

        assertEquals(price("VendorB2026ToiletSheetTest.세로_나열형_세트는_計와_구성합이_일치하고_첫행이_MAIN이다"), s.setPrice());
        assertEquals(0, sumOf(s).compareTo(s.setPrice()), "計 = 도기 + 부속 4건");
        assertEquals(5, s.parts().size(), "도기/F/V/스퍼드/시트/후렌지");

        VendorParsedItem dogi = s.parts().get(0);
        assertEquals("도기", dogi.productName());
        assertEquals(VendorParsedItem.RELATION_MAIN, dogi.relationType(), "세트 첫 행이 대표품목");
        assertEquals("IC552EF_<CODE>", dogi.productCode());
        assertEquals(price("VendorB2026ToiletSheetTest.세로_나열형_세트는_計와_구성합이_일치하고_첫행이_MAIN이다.2"), dogi.unitPrice());

        assertEquals("F/V", s.categorySmall(), "소분류 = B열 품종");
        assertEquals("C910CR", s.main().subItemCode(), "KS 품번");
        assertEquals("400(W) 680(D) 435(H)", s.main().specs(), "규격은 specs로 (R7 ③)");
        assertTrue(s.main().description().contains("구륙"), "품목 괄호 설명은 description으로");
        assertEquals("양변기", s.sheetName());
    }

    @Test
    void 부속_서브테이블_설명이_좌측_부속에_잘못_붙지_않는다() {
        VendorProductSet s = byCode(parse(), "IC552EF");

        // Q열 비고는 구조상 행 전체 컬럼이지만, 그 행에 N~P 부속 서브테이블 항목이 있으면
        // 내용은 그쪽(F/V 옵션 목록) 설명이다. 좌측 부속에 붙이면 스퍼드가 '대소구분 세척밸브'가 된다.
        assertNull(part(s, "스퍼드").orElseThrow().description());
        assertNull(part(s, "시트").orElseThrow().description());
        assertNull(part(s, "후렌지").orElseThrow().description());
        assertFalse(s.main().description().contains("세척밸브"),
                "우측 옵션 설명이 대표품목 description으로 새면 안 된다");
    }

    @Test
    void 단종_표기는_서브테이블_유무와_무관하게_remark로_남는다() {
        List<VendorProductSet> sets = parse();

        // 서브테이블 항목이 없는 행 — 설명·상태 둘 다 좌측 것이다.
        assertEquals("단종", byCode(sets, "C853").main().remark());
        // 서브테이블 항목이 있는 행(N=탱크뚜껑 O=4jv352) — 설명은 버리되 단종은 제품 상태라 남긴다.
        assertEquals("소진 후 단종(블루)", byCode(sets, "C352E").main().remark());
        // 부속 행의 단종도 그 부속에 붙는다.
        assertEquals("단종", part(byCode(sets, "C853"), "양부속").orElseThrow().remark());
    }

    @Test
    void 투피스는_하부가_대표품목이_된다() {
        VendorProductSet s = byCode(parse(), "C853");

        assertEquals("하부", s.parts().get(0).productName());
        assertEquals(VendorParsedItem.RELATION_MAIN, s.parts().get(0).relationType(),
                "'도기'라는 이름이 아니라 세트 첫 행이 대표품목이다");
        assertEquals(price("VendorB2026ToiletSheetTest.투피스는_하부가_대표품목이_된다"), s.setPrice());
        assertEquals(0, sumOf(s).compareTo(s.setPrice()));
    }

    @Test
    void 동일_품번이_두_번_나오면_별개_세트로_갈린다() {
        List<VendorProductSet> sets = parse();

        // L352E는 자폐수전만 다른 두 구성으로 두 번 나온다. 접미가 없으면 upsert가 한 행으로 병합해
        // 한쪽 구성이 사라진다.
        VendorProductSet first = byCode(sets, "L352E");
        VendorProductSet second = byCode(sets, "L352E-2");

        assertEquals(price("VendorB2026ToiletSheetTest.동일_품번이_두_번_나오면_별개_세트로_갈린다"), first.setPrice());
        assertEquals(price("VendorB2026ToiletSheetTest.동일_품번이_두_번_나오면_별개_세트로_갈린다.2"), second.setPrice());
        assertEquals("L352E_<CODE>", part(first, "자폐수전").orElseThrow().productCode());
        assertEquals("L352E-2_<CODE>", part(second, "자폐수전").orElseThrow().productCode());
        assertTrue(second.main().description().contains("동일 품번 변형 2"));

        // 같은 부속이 한 세트에 2번 들어가는 경우(앵글밸브 ×2)도 計에 두 번 반영된다.
        assertEquals(2, first.parts().stream().filter(p -> "앵글밸브".equals(p.productName())).count());
        assertEquals(0, sumOf(first).compareTo(first.setPrice()));
    }

    @Test
    void 본표_아래_부록표는_적재되지_않는다() {
        List<VendorProductSet> sets = parse();

        // 218행부터 '구분/BOX/PLT/소프트개폐시트' 부록표가 붙는다. C열에 값(20·10·50·100)이 있어
        // 세트 시작으로 오인되기 쉽다.
        assertTrue(sets.stream().noneMatch(s -> s.main().productCode().matches("^[0-9]+$")),
                "부록표의 BOX 수량이 품번으로 들어오면 안 된다");
        assertTrue(sets.stream().noneMatch(s -> "소프트개폐시트".equals(s.categorySmall())));
    }

    @Test
    void 세트_사이_빈_행은_세트만_끊고_시트를_끝내지_않는다() {
        // 오토플러싱 구간은 IC600DE(182행) — 빈 행 — IC599DE(184행) 순이다.
        List<VendorProductSet> sets = parse();
        assertEquals(price("VendorB2026ToiletSheetTest.IC600DE"), byCode(sets, "IC600DE").setPrice());
        assertEquals(price("VendorB2026ToiletSheetTest.IC599DE"), byCode(sets, "IC599DE").setPrice(),
                "빈 행 뒤의 세트도 계속 읽어야 한다");
        assertEquals("화변기", byCode(sets, "C922").categorySmall(), "시트 마지막 세트까지 도달");
    }

    @Test
    void 전체_회귀_기준값() {
        List<VendorProductSet> sets = parse();

        assertEquals(39, sets.size(), "세트 수");
        assertEquals(209, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 수");
        assertTrue(sets.stream().allMatch(s -> "양변기".equals(s.categoryLarge())));
        assertTrue(sets.stream().allMatch(s -> s.imageKey() != null), "이미지 매칭 키(행 인덱스)");

        long mismatch = sets.stream()
                .filter(s -> s.setPrice() == null || sumOf(s).compareTo(s.setPrice()) != 0)
                .count();
        assertEquals(0, mismatch, "計 = 구성합 무결성 (양변기 39세트 전건)");
    }
}
