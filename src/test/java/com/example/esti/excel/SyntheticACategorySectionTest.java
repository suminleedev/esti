package com.example.esti.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <b>합성(런타임 생성) fixture</b> 기반 A사 분류 구간 검증(A-1~A-4).
 *
 * <p>실샘플({@code docs/samples/...})은 gitignore라 CI에서 스킵되므로, 대분류 구간 산출의
 * 뼈대는 코드로 만든 시트로 <b>항상</b> 검증한다. 검증 대상:
 *
 * <ul>
 *   <li>A-1 오프셋 학습 — 라벨이 빈 행에서 일정 거리(여기선 4) 아래 얹혀 있어도 구간 시작을 되찾는다</li>
 *   <li>A-2 대분류 어휘 정규화 — 원본 B열 라벨을 저장 어휘로 옮긴다</li>
 *   <li>A-3 C 라벨 전용행은 어휘 추론이 실패해도 소분류로 확정된다</li>
 *   <li>A-4 C 라벨이 없는 구간에 직전 소분류가 넘어오지 않는다</li>
 *   <li>구간이 바뀔 때 버퍼에 남은 품목은 <b>직전</b> 구간의 분류로 방출된다</li>
 * </ul>
 */
class SyntheticACategorySectionTest {

    private static Path fixture;

    /**
     * 원본 레이아웃을 축소해 재현한다 — 빈 행이 구간 구분선이고, B열 대분류 라벨은
     * 그 구분선에서 <b>4행 아래</b>에 얹혀 있다(실파일은 6행).
     *
     * <pre>
     *  r1  헤더
     *  r2  (빈 행)
     *  r3  C=원피스양변기 (라벨 전용)        ← 구간① 시작
     *  r4  본품 100 / r5 부속 50
     *  r6  B=양변기, 합계 150               ← 라벨 (r2 + 4)
     *  r7  (빈 행)
     *  r8  C=미니말 + 데이터 (세트명, 소분류 아님)  ← 구간② 시작
     *  r9~r11 액세서리 품목
     *  r12 B=액세서리                       ← 라벨 (r7 + 4)
     *  r13 (빈 행)
     *  r14 C=기타부속 (라벨 전용, 어휘 추론 불가) ← 구간③ 시작
     *  r15~r17 부속 품목, r17에 B=부속        ← 라벨 (r13 + 4)
     *  r18 B=부속 (같은 라벨 재등장)           ← 구간 시작 역행 → 보정 경로
     * </pre>
     */
    @BeforeAll
    static void buildFixture() throws Exception {
        fixture = Path.of("target/test-fixtures/synthetic-a-category.xlsx");
        Files.createDirectories(fixture.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(fixture)) {
            Sheet s = wb.createSheet("ASK");

            header(s, 0);
            // r1 = 빈 행(생성하지 않는다)
            label(s, 2, 2, "원피스양변기");            // 구간① 시작
            item(s, 3, "양변기 본품", "AC-1", 100);
            item(s, 4, "양변기 부속", "AC-2", 50);
            total(s, 5, "양변기", 150);                 // B라벨 = r2 + 4

            // r6 = 빈 행
            item(s, 7, "비누대", "AX-1", 30);
            set(s, 7, "미니말");                        // C=세트명 + 데이터 → 소분류 아님
            item(s, 8, "휴지걸이", "AX-2", 20);
            item(s, 9, "수건걸이", "AX-3", 10);
            item(s, 10, "컵대", "AX-4", 40);
            large(s, 11, "액세서리");                   // B라벨 = r6 + 4 (행 자체는 비어 있음)

            // r12 = 빈 행
            label(s, 13, 2, "기타부속");                // 어휘 추론이 실패하는 소분류
            item(s, 14, "폽업", "AB-1", 5);
            item(s, 15, "패킹", "AB-2", 5);
            item(s, 16, "호스", "AB-3", 7);
            large(s, 16, "부속");                       // B라벨 = r12 + 4
            item(s, 17, "거름망", "AB-4", 3);
            large(s, 17, "부속");                       // 같은 라벨 재등장 → 구간 시작 역행 보정

            wb.write(os);
        }
    }

    private static List<VendorProductSet> parse() {
        return new VendorAExcelParser().parseSets(fixture);
    }

    @Test
    void 대분류는_B열_라벨_구간에서_오고_어휘로_정규화된다() {
        Set<String> larges = parse().stream()
                .map(VendorProductSet::categoryLarge)
                .collect(Collectors.toSet());

        assertEquals(Set.of("양변기", "액세서리", "부속"), larges,
                "B열 라벨 3종이 각각 제 구간의 대분류가 돼야 함");
    }

    @Test
    void 라벨이_구분선보다_아래_있어도_구간_앞부분이_직전_대분류로_새지_않는다() {
        VendorProductSet toilet = parse().stream()
                .filter(x -> "원피스양변기".equals(x.categorySmall()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("원피스양변기 세트 미발견"));

        // B=양변기 라벨은 세트가 시작되고 4행 뒤에 찍혀 있다. 오프셋을 되돌리지 못하면
        // 본품·부속이 라벨 앞에 있어 대분류가 비게 된다.
        assertEquals("양변기", toilet.categoryLarge());
        assertEquals(1, toilet.parts().size(), "합계행으로 본품+부속이 묶여야 함");
    }

    @Test
    void 어휘_추론이_안_되는_C라벨도_소분류로_확정된다() {
        // "기타부속"은 inferLargeCategoryFromSmallCategory가 못 맞히는 값이다.
        // 예전에는 세트명으로 흘려보내 직전 소분류가 그대로 이어졌다.
        List<VendorProductSet> parts = parse().stream()
                .filter(x -> "부속".equals(x.categoryLarge()))
                .toList();

        assertFalse(parts.isEmpty(), "부속 구간이 있어야 함");
        assertTrue(parts.stream().allMatch(x -> "기타부속".equals(x.categorySmall())),
                "부속 구간의 소분류는 전부 '기타부속'이어야 함");
    }

    @Test
    void C라벨이_없는_구간은_직전_소분류를_물려받지_않는다() {
        List<VendorProductSet> accessories = parse().stream()
                .filter(x -> "액세서리".equals(x.categoryLarge()))
                .toList();

        assertEquals(4, accessories.size(), "액세서리 품목 4건");
        assertTrue(accessories.stream().allMatch(x -> x.categorySmall() == null),
                "C 라벨 전용행이 없는 구간의 소분류는 비어 있어야 함(직전 '원피스양변기'가 아님)");
    }

    @Test
    void 구간_경계에_걸친_잔여_품목은_직전_구간의_분류로_방출된다() {
        // 액세서리 4건은 합계행 없이 다음 구간(부속) 시작에서 정리된다.
        // 분류를 먼저 바꿔버리면 이 4건이 '부속'으로 넘어간다.
        long leaked = parse().stream()
                .filter(x -> "부속".equals(x.categoryLarge()))
                .filter(x -> x.main() != null && x.main().productName() != null
                        && x.main().productName().startsWith("컵대"))
                .count();

        assertEquals(0, leaked, "액세서리 잔여 품목이 다음 구간으로 새면 안 됨");
    }

    // ====== fixture 작성 유틸 ======

    private static Row row(Sheet s, int r) {
        Row row = s.getRow(r);
        return row != null ? row : s.createRow(r);
    }

    private static void text(Sheet s, int r, int c, String v) {
        Cell cell = row(s, r).createCell(c);
        cell.setCellValue(v);
    }

    private static void number(Sheet s, int r, int c, double v) {
        row(s, r).createCell(c).setCellValue(v);
    }

    /** 헤더 줄 — E/F에 '구품번'·'신품번'이 있어야 파서가 헤더로 걸러낸다. */
    private static void header(Sheet s, int r) {
        text(s, r, 1, "제품명");
        text(s, r, 4, "구품번");
        text(s, r, 5, "신품번");
    }

    /** C 라벨 전용행(소분류). */
    private static void label(Sheet s, int r, int c, String v) {
        text(s, r, c, v);
    }

    /** 세트명(C) — 데이터 행에 얹는다. */
    private static void set(Sheet s, int r, String name) {
        text(s, r, 2, name);
    }

    /** B열 대분류 라벨. */
    private static void large(Sheet s, int r, String name) {
        text(s, r, 1, name);
    }

    private static void item(Sheet s, int r, String name, String code, double price) {
        text(s, r, 3, name);
        text(s, r, 5, code);
        number(s, r, 6, price);
    }

    /** 합계행(G열만) + 같은 행에 B열 대분류 라벨. */
    private static void total(Sheet s, int r, String largeLabel, double sum) {
        text(s, r, 1, largeLabel);
        number(s, r, 6, sum);
    }
}
