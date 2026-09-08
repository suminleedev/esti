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
        return set.parts().stream()
                // 선택 옵션은 計에 안 들어간다 — 원본이 기본 구성만 합산한다(RELATION_OPTION).
                .filter(p -> !VendorParsedItem.RELATION_OPTION.equals(p.relationType()))
                .map(VendorParsedItem::unitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void 세로_나열형_세트는_計와_구성합이_일치하고_첫행이_MAIN이다() {
        VendorProductSet s = byCode(parse(), "IC552EF");

        assertEquals(price("VendorB2026ToiletSheetTest.세로_나열형_세트는_計와_구성합이_일치하고_첫행이_MAIN이다"), s.setPrice());
        assertEquals(0, sumOf(s).compareTo(s.setPrice()), "計 = 도기 + 부속 4건");
        assertEquals(6, s.parts().size(),
                "도기/F/V/스퍼드/시트/후렌지 + F/V 1등급 옵션 — 기본 F/V와 같은 부품의 변형이라 이 세트에 붙는다");

        VendorParsedItem dogi = s.parts().get(0);
        assertEquals("도기", dogi.productName());
        assertEquals(VendorParsedItem.RELATION_MAIN, dogi.relationType(), "세트 첫 행이 대표품목");
        assertEquals("IC552EF_4gc552wt-w3-g", dogi.productCode());
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
        assertEquals("L352E_46yj352", part(first, "자폐수전").orElseThrow().productCode());
        assertEquals("L352E-2_46yjk0352ren", part(second, "자폐수전").orElseThrow().productCode());
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

        // N~P 선택 옵션 도입(§8 잔여 ①) — 옵션 23건이 세트 부속으로 붙고,
        // 대응이 없는 항목은 독립 옵션 제품으로 나간다(양변기 6 · 소변기·수채 10).
        assertEquals(45, sets.size(), "세트 수 — 기본 39 + 독립 옵션 6");
        assertEquals(232, sets.stream().mapToInt(s -> s.parts().size()).sum(), "구성행 수 — 209 + 옵션 23");

        // 이 줄은 원래 «전부 양변기»였다. 그 전제가 곧 결함이었다 —
        // 유아용 구간(품종 축에 섞인 «용도» 라벨)에 세면기·소변기가 함께 들어 있다.
        assertEquals(
                java.util.Map.of("양변기", 41L, "세면기", 2L, "소변기", 2L),
                sets.stream().collect(java.util.stream.Collectors.groupingBy(
                        VendorProductSet::categoryLarge, java.util.stream.Collectors.counting())),
                "대분류 분포 — 유아용 4건만 시트명 밖으로 나간다");
        // 독립 옵션 제품은 좌측 앵커가 없어 이미지 키가 없다 — 세트에 대해서만 본다.
        assertTrue(sets.stream().filter(s -> !s.parts().isEmpty()).allMatch(s -> s.imageKey() != null), "이미지 매칭 키(행 인덱스)");

        long mismatch = sets.stream()
                // 독립 옵션 제품은 부속이 없다 — 대조할 «구성»이 없으므로 이 검사의 대상이 아니다.
                .filter(s -> !s.parts().isEmpty())
                .filter(s -> s.setPrice() == null || sumOf(s).compareTo(s.setPrice()) != 0)
                .count();
        assertEquals(0, mismatch, "計 = 구성합 무결성 (세트 전건 · 옵션 제외)");
    }

    /* ===================== 유아용 구간 — 품종 축에 «용도»가 섞여 있다 ===================== */

    /**
     * B열(품종)은 {@code 원피스·비데일체형·오토플러싱·화변기·BOX}처럼 전부 양변기의 하위 품종인데
     * <b>{@code 유아용}만 «용도»다.</b> 그 구간에는 유아용 양변기·세면기·소변기가 함께 들어 있어,
     * 시트명을 그대로 대분류로 주면 <b>세면기·소변기가 양변기로 분류된다.</b>
     *
     * <p>실제로 그랬다 — 실DB에서 유아용 세트 5건 중 4건(L·U 접두)이 «양변기»로 서 있었다.
     */
    @Test
    void 유아용_구간의_세면기는_세면기로_분류된다() {
        List<VendorProductSet> sets = parse();

        assertEquals("세면기", byCode(sets, "L352E").categoryLarge());
        assertEquals("세면기", byCode(sets, "L352E-2").categoryLarge());
    }

    @Test
    void 유아용_구간의_소변기는_소변기로_분류된다() {
        List<VendorProductSet> sets = parse();

        assertEquals("소변기", byCode(sets, "U352E").categoryLarge());
        assertEquals("소변기", byCode(sets, "U352E-2").categoryLarge());
    }

    /** 같은 구간의 양변기는 그대로 양변기다 — 보정이 과하게 돌지 않는지 본다. */
    @Test
    void 유아용_구간의_양변기는_그대로다() {
        assertEquals("양변기", byCode(parse(), "C352E").categoryLarge());
    }

    /** 품종은 그대로 «유아용»이다. 대분류만 바로잡고 소분류는 원본을 지킨다. */
    @Test
    void 유아용은_소분류로_남는다() {
        List<VendorProductSet> sets = parse();

        for (String code : List.of("C352E", "L352E", "U352E")) {
            assertEquals("유아용", byCode(sets, code).categorySmall(),
                    code + "의 소분류가 유아용이 아니다");
        }
    }

    /**
     * 보정은 도기 3시트의 {@code C·L·U} 접두에만 건다. 가장 많은 {@code I} 접두는
     * 여러 대분류에 걸쳐 있어 판별에 쓰지 않는다 — 건드리면 멀쩡한 것이 흔들린다.
     */
    @Test
    void I접두_세트는_시트_대분류를_그대로_쓴다() {
        long moved = parse().stream()
                .filter(s -> s.main() != null && s.main().productCode() != null)
                .filter(s -> s.main().productCode().toUpperCase().startsWith("I"))
                .filter(s -> !"양변기".equals(s.categoryLarge()))
                .count();

        assertEquals(0, moved, "I 접두가 양변기 밖으로 새어 나갔다");
    }

    /* ===================== N~P 선택 옵션 (§8 잔여 ①) ===================== */

    private List<VendorParsedItem> optionsOf(VendorProductSet set) {
        return set.parts().stream()
                .filter(p -> VendorParsedItem.RELATION_OPTION.equals(p.relationType()))
                .toList();
    }

    /**
     * 원본은 기본 구성을 왼쪽에, <b>고를 수 있는 것</b>을 오른쪽(N~P)에 나눠 적는다.
     * 종전에는 오른쪽을 통째로 버렸다 — 탱크뚜껑처럼 실제로 선택·교체가 일어나는 품목이 사라졌다.
     */
    @Test
    void 선택_옵션이_해당_세트에_붙는다() {
        List<VendorProductSet> sets = parse();

        long total = sets.stream().mapToLong(s -> optionsOf(s).size()).sum();
        assertEquals(23, total, "옵션 부속 수");
    }

    /**
     * <b>소속은 «행 정렬 + 코드 계열»이 함께 정한다.</b> 행만 보면 시트 첫머리의 F/V가
     * IC552EF 구간에 놓였다는 이유로 그 세트에 붙는데, 코드 계열이 전혀 다르다.
     */
    @Test
    void 코드_계열이_맞는_옵션만_세트에_붙는다() {
        VendorProductSet s = byCode(parse(), "IC702E");

        assertTrue(optionsOf(s).stream().allMatch(p -> p.productCode().contains("702")),
                "IC702E에는 702 계열 옵션만 붙어야 한다");
    }

    /** 같은 부품의 변형은 계열로 인정한다 — 기본 F/V와 그 1등급 변형은 앞자리를 길게 공유한다. */
    @Test
    void 같은_부품의_변형은_그_세트의_옵션이_된다() {
        VendorProductSet s = byCode(parse(), "IC552EF");

        assertEquals(1, optionsOf(s).size(), "F/V 1등급 변형 1건");
    }

    /** 어느 세트의 계열도 아니면 독립 옵션 제품으로 나간다. 억지로 붙이지 않는다. */
    @Test
    void 계열이_없는_옵션은_독립_제품이_된다() {
        List<VendorProductSet> standalone = parse().stream()
                .filter(s -> s.parts().isEmpty())
                .toList();

        assertEquals(6, standalone.size(), "F/V 5 + 어느 세트에도 안 맞는 탱크뚜껑 1");
        assertTrue(standalone.stream().allMatch(s -> s.setPrice() != null), "단가는 갖는다");
    }

    /** 옵션은 세트 정체성이 아니다 — 고를 수 있는 것이 달라졌다고 다른 세트가 되지 않는다. */
    @Test
    void 옵션은_세트_해시에_들어가지_않는다() {
        VendorProductSet s = byCode(parse(), "IC702E");
        assertFalse(optionsOf(s).isEmpty(), "이 세트는 옵션을 갖는다(전제)");

        List<VendorParsedItem> withoutOptions = s.parts().stream()
                .filter(p -> !VendorParsedItem.RELATION_OPTION.equals(p.relationType()))
                .toList();
        VendorProductSet bare = new VendorProductSet("B", s.categoryLarge(), s.categorySmall(),
                s.main(), withoutOptions, s.setPrice(), false, s.imageKey(), false);

        assertEquals(bare.setHash(), s.setHash(), "옵션이 있고 없고가 해시를 바꾸면 안 된다");
    }
}
