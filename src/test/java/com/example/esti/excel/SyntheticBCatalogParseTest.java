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
 * <b>합성(런타임 생성) fixture</b> 기반 파서 검증(§15).
 *
 * <p>실데이터 샘플({@code docs/samples/...})은 gitignore라 CI에선 부재 → 스킵(거짓 초록) 위험이 있다.
 * 이 테스트는 코드로 통제된 값의 양변기 시트를 POI로 생성해 <b>항상 실행</b>되며,
 * 슬롯 2행형 파싱 골격(§13 리팩토링의 {@code findPriceRow}·{@code readSlotHeader(…,skip)}·
 * {@code isSkipBasicSlotLabel}, 품종 carry-forward, 計=부속합, 비고→description)을 회귀 검증한다.</p>
 */
class SyntheticBCatalogParseTest {

    private static Path fixture;

    @BeforeAll
    static void buildFixture() throws Exception {
        // 항상 존재하도록 target/에 런타임 생성(커밋 바이너리 불필요, CI 클린 체크아웃에서도 재현).
        fixture = Path.of("target/test-fixtures/synthetic-b-catalog.xlsx");
        Files.createDirectories(fixture.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(fixture)) {
            // 시트명 "양변기" → family()가 TOILET(parseToiletSheet)로 라우팅.
            Sheet s = wb.createSheet("양변기");
            // 컬럼: A0=구분 B1=품종 C2=품번 E4=KS품번 F5=제품코드/대리점가 마커 G6=도기 H7=시트 I8=計 J9=비고
            header(s, 0);
            // 세트1: TB100 (도기 50000 + 시트 20000 = 計 70000), 비고=탱크뚜껑 포함
            product(s, 1, "상품", "양변기", "TB100", "KS-A", "dog100", "seat100", "탱크뚜껑 포함");
            price(s, 2, 50000, 20000, 70000);
            // 세트2: TB200 — 품종(B) 병합 빈칸 → carry-forward("양변기"). 도기 60000 + 시트 25000 = 計 85000
            product(s, 3, "제품", null, "TB200", "KS-B", "dog200", "seat200", null);
            price(s, 4, 60000, 25000, 85000);
            wb.write(os);
        }
    }

    private static void header(Sheet s, int r) {
        Row row = s.createRow(r);
        set(row, 0, "구분"); set(row, 1, "품종"); set(row, 2, "품번");
        set(row, 4, "KS품번"); set(row, 6, "도기"); set(row, 7, "시트");
        set(row, 8, "計"); set(row, 9, "비고");
    }

    private static void product(Sheet s, int r, String gubun, String kind, String code,
                                String ks, String dogi, String seat, String note) {
        Row row = s.createRow(r);
        set(row, 0, gubun);
        if (kind != null) set(row, 1, kind);
        set(row, 2, code);
        set(row, 4, ks);
        set(row, 5, "제품코드");
        set(row, 6, dogi);
        set(row, 7, seat);
        if (note != null) set(row, 9, note);
    }

    private static void price(Sheet s, int r, int dogi, int seat, int total) {
        Row row = s.createRow(r);
        set(row, 5, "대리점가");
        setNum(row, 6, dogi);
        setNum(row, 7, seat);
        setNum(row, 8, total);
    }

    private static void set(Row row, int col, String v) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
    }

    private static void setNum(Row row, int col, int v) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
    }

    private List<VendorProductSet> parse() {
        return new VendorBExcelParser().parseSets(fixture);
    }

    private VendorProductSet byCode(List<VendorProductSet> sets, String code) {
        return sets.stream().filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " 미발견: "
                        + sets.stream().map(x -> x.main() == null ? "null" : x.main().productCode()).toList()));
    }

    @Test
    void 양변기_슬롯골격_2세트_계는_부속합() {
        List<VendorProductSet> sets = parse();
        assertEquals(2, sets.size(), "세트 2건");

        VendorProductSet tb100 = byCode(sets, "TB100");
        assertEquals("양변기", tb100.categoryLarge());
        assertEquals("양변기", tb100.sheetName(), "이미지 매칭 키=시트명");
        assertEquals(0, new BigDecimal("70000").compareTo(tb100.setPrice()), "計=도기+시트");
        assertEquals(2, tb100.parts().size(), "도기+시트");
        assertTrue(tb100.parts().stream()
                .anyMatch(p -> VendorParsedItem.RELATION_MAIN.equals(p.relationType())), "도기=본품");
    }

    @Test
    void 품종_병합빈칸은_carry_forward() {
        VendorProductSet tb200 = byCode(parse(), "TB200");
        assertEquals("양변기", tb200.categorySmall(), "품종 carry-forward");
        assertEquals(0, new BigDecimal("85000").compareTo(tb200.setPrice()));
    }

    @Test
    void 비고컬럼은_description으로_수집() {
        VendorProductSet tb100 = byCode(parse(), "TB100");
        assertNotNull(tb100.main().description());
        assertTrue(tb100.main().description().contains("탱크뚜껑"),
                "비고(J) → description: " + tb100.main().description());
    }
}
