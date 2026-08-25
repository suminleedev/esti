package com.example.esti.output;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 견적서(검토) 출력 검증 (P3).
 *
 * <p>기대값은 `docs/samples/견적서_검토_sample.xlsx` 실측에서 왔다 — 머리글 좌표, R13 헤더,
 * R14 섹션 헤더와 `마진` 위치, 항목·소계 수식이 그것이다.
 *
 * <p>제안서와 정반대로 <b>사입가·마진이 반드시 들어 있어야</b> 한다. 내부 검토 문서이기 때문이다.
 */
class QuoteExcelWriterTest {

    private static final String CEO = "홍 길 동";

    @Test
    @DisplayName("머리글 — 見積書·西紀·제출처·見積NO·工事名·合計金(한글+숫자)")
    void 머리글() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        assertThat(text(sheet, 1, 0)).isEqualTo("見  積  書");
        assertThat(merged(sheet, 1, 1, 0, 7)).isTrue();

        assertThat(text(sheet, 4, 0)).isEqualTo("西   紀 : 2026年  08月  25日");
        assertThat(text(sheet, 4, 5)).endsWith("대표이사 : " + CEO);
        assertThat(merged(sheet, 4, 11, 5, 7)).isTrue();          // F5:H12 서명란

        assertThat(text(sheet, 5, 0)).isEqualTo("[대우건설] 貴下");
        assertThat(text(sheet, 7, 0)).isEqualTo("見積NO  : syt-2026082501");
        assertThat(text(sheet, 8, 0)).isEqualTo("工事名  : 햇살아파트 위생기구류 납품");
        assertThat(text(sheet, 11, 0)).isEqualTo("下記와   如히   내역 하나이다.");

        // 도기 152,000 + 수전 69,000×2 + 악세 14,000 = 304,000
        assertThat(text(sheet, 10, 0)).isEqualTo("合計金  :   삼십만사천원정(₩304,000)");
    }

    @Test
    @DisplayName("R13 헤더와 R14 섹션 헤더 — `마진`은 J14 (샘플 위치)")
    void 헤더_구조() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        assertThat(text(sheet, 12, 0)).isEqualTo("품  명");
        assertThat(text(sheet, 12, 2)).isEqualTo("단위");
        assertThat(text(sheet, 12, 5)).isEqualTo("금 액");
        assertThat(text(sheet, 12, 8)).isEqualTo("사입가");
        assertThat(text(sheet, 12, 9)).isEqualTo("사입가대비");
        assertThat(merged(sheet, 12, 12, 9, 11)).isTrue();        // J13:L13

        assertThat(text(sheet, 13, 0)).isEqualTo("아파트 59형(523)");
        assertThat(text(sheet, 13, 9)).isEqualTo("마진");
    }

    @Test
    @DisplayName("항목행 수식 — F=E*D, J=E-I, K=D*J, 마진율은 사입가 대비(O-4 ⓐ)")
    void 항목행_수식() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));
        int r = 14;   // R15 첫 항목

        assertThat(text(sheet, r, 0)).isEqualTo("투피스양변기");
        assertThat(text(sheet, r, 1)).isEqualTo("IC702");
        assertThat(text(sheet, r, 2)).isEqualTo("SET");
        assertThat(numeric(sheet, r, 3)).isEqualByComparingTo("1");
        assertThat(numeric(sheet, r, 4)).isEqualByComparingTo("152000");
        assertThat(formula(sheet, r, 5)).isEqualTo("E15*D15");
        assertThat(numeric(sheet, r, 8)).isEqualByComparingTo("120000");   // 사입가
        assertThat(formula(sheet, r, 9)).isEqualTo("E15-I15");
        assertThat(formula(sheet, r, 10)).isEqualTo("D15*J15");
        // 소계행도 같은 정의를 쓴다 — 샘플의 K/F(매출 대비)와는 의도적으로 다르다
        assertThat(formula(sheet, r, 11)).isEqualTo("IFERROR((E15-I15)/I15,\"\")");
    }

    @Test
    @DisplayName("4단 집계 — 항목 → 소분류 소계 → 섹션 소계 → 합계")
    void 사단_집계() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        // 도기류 1건(R15) → 소계 R16 / 수전류 2건(R17~18) → 소계 R19 / 악세 1건(R20) → 소계 R21
        assertThat(text(sheet, 15, 0)).isEqualTo("소 계");
        assertThat(formula(sheet, 15, 5)).isEqualTo("SUM(F15:F15)");
        assertThat(text(sheet, 18, 0)).isEqualTo("소 계");
        assertThat(formula(sheet, 18, 5)).isEqualTo("SUM(F17:F18)");
        assertThat(text(sheet, 20, 0)).isEqualTo("소 계");

        // 섹션 소계 — 소분류 소계 행들을 더한다
        assertThat(text(sheet, 21, 0)).isEqualTo("소 계");
        assertThat(formula(sheet, 21, 5)).isEqualTo("F16+F19+F21");

        // 합 계 — 섹션 소계의 합. 샘플과 같이 금액(F)만 채운다
        assertThat(text(sheet, 23, 0)).isEqualTo("합 계");
        assertThat(formula(sheet, 23, 5)).isEqualTo("F22");
    }

    @Test
    @DisplayName("사입가·마진이 반드시 실린다 (제안서와 정반대)")
    void 사입가_노출() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        DataFormatter fmt = new DataFormatter();
        List<String> all = new ArrayList<>();
        for (Row row : sheet) for (Cell c : row) all.add(fmt.formatCellValue(c));

        assertThat(all).contains("사입가", "사입가대비", "마진");
        assertThat(numeric(sheet, 14, 8)).isEqualByComparingTo("120000");
    }

    @Test
    @DisplayName("부속동 합본은 건물 구분마다 섹션이 갈리고 합계가 이를 더한다")
    void 부속동_합본() throws Exception {
        Sheet sheet = render(QuoteTarget.annex());

        assertThat(text(sheet, 3, 0)).isEqualTo("(부속동·상가)");
        assertThat(text(sheet, 13, 0)).isEqualTo("부속동");

        // 부속동: 헤더(R14) → 항목(R15) → 소계(R16) → 빈 줄 → 상가 섹션(R18)
        assertThat(text(sheet, 17, 0)).isEqualTo("상가");
        assertThat(text(sheet, 21, 0)).isEqualTo("합 계");
        assertThat(formula(sheet, 21, 5)).isEqualTo("F16+F20");
    }

    @Test
    @DisplayName("품명은 섹션 안에서 겹칠 때만 부위를 덧붙인다 (샘플의 `비데(공용)` 방식)")
    void 품명_중복_구분() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        assertThat(text(sheet, 14, 0)).isEqualTo("투피스양변기");            // 유일 → 그대로
        assertThat(text(sheet, 16, 0)).isEqualTo("세면기수전(공용욕실)");    // 둘 → 부위 덧붙임
        assertThat(text(sheet, 17, 0)).isEqualTo("세면기수전(부부욕실)");
    }

    @Test
    @DisplayName("제조사·비고는 바로 위와 같으면 ditto(\") 로 줄인다 (샘플과 동일)")
    void ditto_표기() throws Exception {
        Sheet sheet = render(QuoteTarget.main("59㎡"));

        assertThat(text(sheet, 16, 6)).isEqualTo("\"");   // 제조사 동서 반복
        assertThat(text(sheet, 19, 6)).isEqualTo("범한"); // 바뀌면 실제 값
    }

    @Test
    @DisplayName("조건 문구 — 없으면 기본 4줄, 있으면 줄바꿈으로 나눠 그대로 쓴다")
    void 조건_문구() throws Exception {
        Sheet basic = render(QuoteTarget.main("59㎡"));
        assertThat(text(basic, 25, 0)).isEqualTo(QuoteExcelWriter.DEFAULT_TERMS.get(0));
        assertThat(text(basic, 28, 0)).isEqualTo(QuoteExcelWriter.DEFAULT_TERMS.get(3));

        Proposal custom = proposal();
        custom.setQuoteTerms("첫 줄\n둘째 줄");
        Sheet sheet = render(custom, lines(), QuoteTarget.main("59㎡"));
        assertThat(text(sheet, 25, 0)).isEqualTo("첫 줄");
        assertThat(text(sheet, 26, 0)).isEqualTo("둘째 줄");
    }

    @Test
    @DisplayName("대상에 해당하는 라인이 없어도 머리글만 있는 파일이 나온다")
    void 빈_대상() throws Exception {
        Sheet sheet = render(proposal(), lines(), QuoteTarget.main("84㎡"));   // 해당 평형 없음

        assertThat(text(sheet, 10, 0)).isEqualTo("合計金  :   영원정(₩0)");
        assertThat(text(sheet, 13, 0)).isEqualTo("합 계");
    }

    /* ===================== 픽스처 ===================== */

    private Sheet render(QuoteTarget target) throws Exception {
        return render(proposal(), lines(), target);
    }

    private Sheet render(Proposal p, List<ProposalLine> lines, QuoteTarget target) throws Exception {
        byte[] bytes = QuoteExcelWriter.write(p, lines, target, "syt-2026082501", CEO);
        return WorkbookFactory.create(new ByteArrayInputStream(bytes)).getSheetAt(0);
    }

    private Proposal proposal() {
        Proposal p = new Proposal();
        p.setProjectName("햇살아파트");
        p.setClientName("[대우건설]");
        p.setApartmentType("59㎡");
        p.setHouseholds(523);
        p.setDate("2026-08-25");
        return p;
    }

    private List<ProposalLine> lines() {
        List<ProposalLine> lines = new ArrayList<>();
        lines.add(line("본세대", "공용욕실", "양변기", "투피스양변기", "IC702", 152_000, 120_000, 1, "동서", "표준부속품일체"));
        lines.add(line("본세대", "공용욕실", "세면기 수전", "세면기수전", "N0310", 69_000, 50_000, 1, "동서", ""));
        lines.add(line("본세대", "부부욕실", "세면기 수전", "세면기수전", "N0410", 69_000, 50_000, 1, "동서", ""));
        lines.add(line("본세대", "공용욕실", "악세사리", "수건걸이", "KH-529", 14_000, 9_000, 1, "범한", ""));
        lines.add(line("부속동", "공용욕실", "양변기", "투피스양변기", "C733", 145_000, 115_000, 4, "동서", ""));
        lines.add(line("상가", "공용욕실", "양변기", "투피스양변기", "C733", 145_000, 115_000, 6, "동서", ""));
        return lines;
    }

    private ProposalLine line(String buildingType, String area, String category, String categorySmall,
                              String code, int price, int cost, int qty, String vendor, String note) {
        ProposalLine l = new ProposalLine();
        l.setBuildingType(buildingType);
        l.setArea(area);
        l.setCategory(category);
        l.setCategorySmall(categorySmall);
        l.setMainItemCode(code);
        l.setApartmentType("59㎡");
        l.setUnit("SET");
        l.setUnitPrice(BigDecimal.valueOf(price));
        l.setCatalogUnitPrice(BigDecimal.valueOf(cost));
        l.setQty(qty);
        l.setAmount(BigDecimal.valueOf((long) price * qty));
        l.setVendorName(vendor);
        l.setNote(note);
        return l;
    }

    /* ===================== 셀 읽기 ===================== */

    private String text(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) return "";
        Cell c = r.getCell(col);
        return c == null || c.getCellType() != CellType.STRING ? "" : c.getStringCellValue();
    }

    private String formula(Sheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getCellFormula();
    }

    private BigDecimal numeric(Sheet sheet, int row, int col) {
        return BigDecimal.valueOf(sheet.getRow(row).getCell(col).getNumericCellValue());
    }

    private boolean merged(Sheet sheet, int r1, int r2, int c1, int c2) {
        String want = new CellRangeAddress(r1, r2, c1, c2).formatAsString();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).formatAsString().equals(want)) return true;
        }
        return false;
    }
}
