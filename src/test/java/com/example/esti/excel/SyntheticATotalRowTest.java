package com.example.esti.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <b>합성(런타임 생성) fixture</b> 기반 A사 합계행 판정 검증(D8-1).
 *
 * <p>합계행은 "C/D/E/F 비고 G만 있는 행"으로 가른다. 그 네 칸 중 하나에 <b>글자도 숫자도 아닌
 * 잔여 문자</b>가 남아 있으면 판정이 빗나가고, 세트가 닫히지 못한 채 다음 합계행까지 버퍼가 이어진다.
 * 그 다음 합계행이 자기 세트만 뒤에서부터 회수해 가므로 <b>앞 세트는 통째로 개별 제품으로 흩어지고,
 * 대표품목은 세트가 대신 단품가를 갖는다.</b> 합계는 이름이 없는 유령 품목에 붙는다.
 *
 * <p>합계가 맞아떨어지는 경로로 빠지기 때문에 {@code needsReview}도 서지 않는다 — 조용히 틀린다.
 * 실제로 A사 최신본 시트 {@code ASK} 1367행이 구품번 칸에 백틱 두 개를 달고 있어 이 경로를 탔다.
 */
class SyntheticATotalRowTest {

    private static Path fixture;

    /** 원본(시트 {@code ASK} 1361~1375행)을 축소해 재현한다 — 백틱 합계행 뒤에 정상 세트가 하나 더 온다.
     *
     * <pre>
     *  r1  헤더
     *  r2  C=세면수전 (라벨 전용)
     *  r3  C=시그니처 + 대표 100                ← 세트① 시작
     *  r4  부속 20 / r5 부속 30
     *  r6  E=`` , 합계 150                     ← 세트①의 합계행인데 구품번 칸에 백틱
     *  r7  C=슬릭 + 대표 200                    ← 세트② 시작
     *  r8  부속 40
     *  r9  합계 240                             ← 정상 합계행
     * </pre>
     */
    @BeforeAll
    static void buildFixture() throws Exception {
        fixture = Path.of("target/test-fixtures/synthetic-a-total-row.xlsx");
        Files.createDirectories(fixture.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(fixture)) {
            Sheet s = wb.createSheet("ASK");

            header(s, 0);
            label(s, 1, "세면수전");
            item(s, 2, "1H세면수전", "FA-1", 100);
            set(s, 2, "시그니처");
            item(s, 3, "폽업", "FJ-1", 20);
            item(s, 4, "P트랩", "FJ-2", 30);
            totalWithJunk(s, 5, "``", 150);          // ← 재현 대상

            item(s, 6, "2H세면수전", "FA-2", 200);
            set(s, 6, "슬릭");
            item(s, 7, "폽업", "FJ-3", 40);
            total(s, 8, 240);

            wb.write(os);
        }
    }

    private static List<VendorProductSet> parse() {
        return new VendorAExcelParser().parseSets(fixture);
    }

    private static VendorProductSet byMainCode(String code) {
        return parse().stream()
                .filter(x -> x.main() != null && code.equals(x.main().productCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("대표품목 " + code + " 세트 미발견"));
    }

    @Test
    void 구품번_칸에_잔여_문자가_있어도_합계행으로_인식한다() {
        VendorProductSet first = byMainCode("FA-1");

        assertEquals(2, first.parts().size(),
                "백틱 합계행이 세트를 닫아 부속 2건이 대표품목에 붙어야 함");
        assertEquals(0, new BigDecimal("150").compareTo(first.setPrice()),
                "대표품목 가격은 단품가(100)가 아니라 합계행의 세트가(150)여야 함");
        assertFalse(first.needsReview(),
                "정상적으로 닫힌 세트이므로 검수 플래그가 서면 안 됨");
    }

    @Test
    void 잔여_문자_행이_개별_제품으로_새지_않는다() {
        // 판정이 빗나가면 이 행은 구품번을 이름 폴백으로 집어 "`` (신품번 없음)" 제품이 되고,
        // 세트 합계가 그 유령 품목의 단가로 붙는다.
        boolean leaked = parse().stream()
                .anyMatch(x -> x.main() != null && x.main().productName() != null
                        && x.main().productName().contains("(신품번 없음)"));

        assertFalse(leaked, "합계행이 제품으로 저장되면 안 됨");
    }

    @Test
    void 뒤따르는_정상_세트는_그대로_묶인다() {
        VendorProductSet second = byMainCode("FA-2");

        // 앞 세트가 닫히지 못하면 이 세트의 합계행이 뒤에서부터 자기 몫만 회수하고
        // 앞 세트 품목들을 개별 제품으로 흘려보낸다. 고친 뒤에도 이쪽은 영향이 없어야 한다.
        assertEquals(1, second.parts().size());
        assertEquals(0, new BigDecimal("240").compareTo(second.setPrice()));
    }

    @Test
    void 세트는_둘뿐이고_흩어진_개별_제품이_없다() {
        assertEquals(2, parse().size(),
                "세트 2건만 나와야 함 — 흩어지면 개별 제품이 섞여 건수가 늘어난다");
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
        text(s, r, 3, "제품명");
        text(s, r, 4, "구품번");
        text(s, r, 5, "신품번");
    }

    /** C 라벨 전용행(소분류). */
    private static void label(Sheet s, int r, String v) {
        text(s, r, 2, v);
    }

    /** 세트명(C) — 데이터 행에 얹는다. */
    private static void set(Sheet s, int r, String name) {
        text(s, r, 2, name);
    }

    private static void item(Sheet s, int r, String name, String code, double price) {
        text(s, r, 3, name);
        text(s, r, 5, code);
        number(s, r, 6, price);
    }

    /** 합계행(G열만). */
    private static void total(Sheet s, int r, double sum) {
        number(s, r, 6, sum);
    }

    /** 합계행인데 구품번(E) 칸에 잔여 문자가 남아 있는 형태. */
    private static void totalWithJunk(Sheet s, int r, String junk, double sum) {
        text(s, r, 4, junk);
        number(s, r, 6, sum);
    }
}
