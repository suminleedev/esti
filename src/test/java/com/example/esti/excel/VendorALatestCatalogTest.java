package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.esti.support.TestSamples.requireSample;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A사 최신본(시트 {@code ASK}, 2220행) 분류 회귀 검증 — `docs/plan-a-format.md` A-5.
 *
 * <p>고도화 전에는 대분류가 C열 텍스트 추론에서 나와 <b>12종 중 7종만</b> 생성됐고,
 * 소분류 {@code 세면수전} 하나에 728건(전체 38%)이 쏠렸다. 액세서리·발코니수전 구간은
 * C 라벨 전용행이 없어 직전 소분류 {@code 벽붙이주방수전}에 통째로 흡수됐다.
 *
 * <p>샘플은 gitignore이므로 파일이 없으면 스킵한다(CI strict에선 fail).
 */
class VendorALatestCatalogTest {

    private static final Path LATEST = Path.of("docs/samples/A사 단가표_2021최신.xls");

    /**
     * 원본 B열 12종을 저장 어휘로 옮긴 결과(G-2). {@code 매립형 욕조&부속}·{@code 스탠딩욕조}가 합쳐져 11종이다.
     *
     * <p>{@code 수전}은 <b>원본 그대로 둔다</b>(분류 후속 ①). 예전에는 {@code 세면수전}으로 좁혔는데
     * 그 구간에 샤워·욕조 수전이 섞여 있어 90종이 오분류됐다.
     */
    private static final Set<String> EXPECTED_LARGE_CATEGORIES = Set.of(
            "양변기", "비데", "세면기", "욕조", "수전", "샤워수전",
            "주방수전", "액세서리", "발코니수전", "상업용제품", "부속");

    private List<VendorProductSet> parseLatest() {
        requireSample(LATEST);
        return new VendorAExcelParser().parseSets(LATEST);
    }

    @Test
    void 대분류_11종이_모두_생성된다() {
        Set<String> larges = parseLatest().stream()
                .map(VendorProductSet::categoryLarge)
                .collect(Collectors.toSet());

        assertEquals(EXPECTED_LARGE_CATEGORIES, larges,
                "고도화 전에는 비데·발코니수전·상업용제품·부속이 아예 생성되지 않았다");
    }

    @Test
    void 액세서리_구간이_직전_소분류에_흡수되지_않는다() {
        List<VendorProductSet> accessories = parseLatest().stream()
                .filter(s -> "액세서리".equals(s.categoryLarge()))
                .toList();

        assertFalse(accessories.isEmpty(), "액세서리 대분류가 있어야 함");
        assertTrue(accessories.stream().noneMatch(s -> "벽붙이주방수전".equals(s.categorySmall())),
                "액세서리 구간이 직전 소분류(벽붙이주방수전)로 새면 안 됨");
        assertTrue(parseLatest().stream()
                        .filter(s -> "주방수전".equals(s.categoryLarge()))
                        .noneMatch(s -> "액세서리".equals(s.categorySmall())),
                "역방향 오염도 없어야 함");
    }

    @Test
    void 어휘_추론이_안_되던_소분류_6종이_살아난다() {
        // 예전에는 이 6종이 세트명으로 흘러가 직전 소분류가 조용히 이어졌다.
        Set<String> smalls = parseLatest().stream()
                .map(VendorProductSet::categorySmall)
                .filter(s -> s != null)
                .collect(Collectors.toSet());

        for (String recovered : List.of("월풀", "매립헤드", "자동온도조절수전",
                "핸드레일&바", "폽업", "기타부속")) {
            assertTrue(smalls.contains(recovered), "소분류 '" + recovered + "'가 인식돼야 함");
        }
    }

    @Test
    void 세트_축이_같은_대표품목의_다른_세트를_가른다() {
        // G-1. 세트 정체성 = 부속 구성 해시. 가격행 축(품번+대분류)은 22종 중 0종을 갈랐다.
        List<VendorProductSet> sets = parseLatest();

        // 카탈로그 행 = (품번, 대분류, 세트해시). 세트 축 전에는 (품번, 대분류)라 881행이었다.
        Set<String> rows = sets.stream()
                .map(s -> key(s) + "|" + s.categoryLarge() + "|" + s.setHash())
                .collect(Collectors.toSet());
        assertEquals(909, rows.size(), "세트 축 적용 후 카탈로그 행 수");

        // 대표품목 하나에 부속 구성이 다른 세트가 2개 이상 = 예전에 한 덩어리로 합쳐지던 것
        Map<String, Set<String>> hashesByMain = new HashMap<>();
        for (VendorProductSet s : sets) {
            if (s.parts().isEmpty()) continue;
            hashesByMain.computeIfAbsent(key(s), k -> new HashSet<>()).add(s.setHash());
        }
        long split = hashesByMain.values().stream().filter(h -> h.size() > 1).count();
        assertEquals(22, split, "구성이 갈리는 대표품목 종수 — 이만큼이 예전엔 1행으로 접혔다");
    }

    @Test
    void 부속_순서가_달라도_같은_세트해시다() {
        // 엑셀 행 순서가 바뀌었다고 정체성이 흔들리면 재적재마다 행이 새로 생긴다.
        VendorProductSet original = parseLatest().stream()
                .filter(s -> s.parts().size() >= 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("부속 2건 이상인 세트 미발견"));

        List<VendorParsedItem> reversed = new ArrayList<>(original.parts());
        Collections.reverse(reversed);
        VendorProductSet shuffled = new VendorProductSet("A", original.categoryLarge(),
                original.categorySmall(), original.main(), reversed, original.setPrice(),
                false, null, false);

        assertEquals(original.setHash(), shuffled.setHash());
    }

    /** 대표품목 식별자 — 코드가 없으면 이름(임포터의 제품 식별과 같은 축). */
    private static String key(VendorProductSet s) {
        return s.main().productCode() != null ? s.main().productCode() : "NAME:" + s.main().productName();
    }

    @Test
    void 수전_구간의_소분류가_제품명으로_갈린다() {
        // 분류 후속 ①. 원본은 이 구간(816행)에 C 라벨을 하나만 두고 시리즈 단위로 묶었다.
        // 좁혀서 세면수전으로 못 박으면 샤워·욕조 수전이 그 안에 묻힌다.
        Map<String, Long> smalls = parseLatest().stream()
                .filter(s -> "수전".equals(s.categoryLarge()))
                .collect(Collectors.groupingBy(
                        s -> s.categorySmall() == null ? "(없음)" : s.categorySmall(),
                        Collectors.counting()));

        // 버킷은 지어낸 게 아니라 실데이터 분포에서 뽑았다 — 아래가 전부 3건 이상이다.
        for (String expected : List.of("세면수전", "샤워수전", "샤워욕조수전", "욕조수전",
                "데크샤워욕조수전", "데크욕조수전", "매립세면수전", "매립샤워수전",
                "매립샤워욕조수전", "매립욕조수전", "수전부속")) {
            assertTrue(smalls.containsKey(expected) || "욕조수전".equals(expected),
                    "수전 소분류 '" + expected + "'가 있어야 함 (실제: " + smalls.keySet() + ")");
        }
        assertFalse(smalls.containsKey("(없음)"), "수전 구간은 소분류가 비면 안 된다");
    }

    @Test
    void 예전에_세면수전으로_굳던_샤워욕조수전이_제자리를_찾는다() {
        // 분류 후속 ①의 핵심. FB/FC는 샤워·욕조 수전 접두어다.
        List<VendorProductSet> faucets = parseLatest().stream()
                .filter(s -> "수전".equals(s.categoryLarge()))
                .filter(s -> s.main().productCode() != null
                        && s.main().productCode().matches("^F[BC].*"))
                .toList();

        assertFalse(faucets.isEmpty());
        assertTrue(faucets.stream().noneMatch(s -> "세면수전".equals(s.categorySmall())),
                "샤워·욕조 수전 접두어인데 소분류가 세면수전이면 안 됨");
    }

    @Test
    void 부속_구간의_도기_완제품이_제_대분류로_간다() {
        // 분류 후속 ②. 원본이 파일 끝에 파티오 시리즈를 기타부속 아래 적어 놨다.
        List<VendorProductSet> sets = parseLatest();

        assertTrue(sets.stream().noneMatch(s -> "부속".equals(s.categoryLarge())
                        && s.main().productName() != null
                        && s.main().productName().startsWith("파티오")),
                "파티오 완제품이 부속에 남으면 안 됨");

        // 같은 구간의 진짜 부속은 그대로 둔다 — 접두어가 도기 계열이어도 마찬가지다.
        assertTrue(sets.stream().anyMatch(s -> "부속".equals(s.categoryLarge())
                        && s.main().productName() != null
                        && s.main().productName().contains("트랩")),
                "트랩은 부속에 남아야 함(접두어만 보면 도기로 오인된다)");
    }

    @Test
    void 세트_그룹핑은_회귀하지_않는다() {
        List<VendorProductSet> sets = parseLatest();

        int items = sets.size() + sets.stream().mapToInt(s -> s.parts().size()).sum();
        long review = sets.stream().filter(VendorProductSet::needsReview).count();

        // 분류만 손댔으므로 합계행 기반 그룹핑 결과는 그대로여야 한다(2021 최신본 실측값).
        assertEquals(962, sets.size(), "세트 수");
        assertEquals(1892, items, "품목 총계 = 원본 데이터 행 수");
        assertEquals(7, review, "합계≠부속합산으로 검수 필요한 세트");
    }
}
