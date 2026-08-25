package com.example.esti.output;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.util.KoreanCurrency;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 견적서(내부 검토용) 엑셀 렌더러 — 표 + 4단 집계 (Phase 6 P3).
 *
 * <p>제안서와 달리 <b>사입가·마진이 들어간다.</b> 수신자가 다르므로 파일도 엔드포인트도 분리한다(O-6).
 *
 * <p>양식은 `docs/samples/견적서_검토_sample.xlsx`를 실측해 재현했다.
 * <pre>
 *   R2       見 積 書
 *   R5~R12   西紀 / 제출처 / 見積NO / 工事名 / 合計金 / 下記와 如히    (F5:H12 = 대표이사 서명란)
 *   R13      헤더 — 품명·규격·단위·수량·단가·금액·제조사·비고 | 사입가·사입가대비(3열 병합)
 *   R14      섹션 헤더 (`아파트 59형(523)`) + J열 `마진`
 *   ...      항목 → 소분류 소계 → (소분류가 둘 이상이면) 섹션 소계
 *   말미      합 계 → 조건 문구
 * </pre>
 *
 * <p><b>집계 4단</b>: 항목 → 소분류 소계 → 섹션 소계 → 합계.
 *
 * <p>수식으로 쓴다(값 박아넣기 아님). 검토 중 사입가를 고쳐 보면 마진이 바로 따라오는 편이
 * 내부 검토 문서로서 쓸모 있기 때문이다.
 */
public final class QuoteExcelWriter {

    /* 열 인덱스 */
    private static final int C_NAME = 0;      // A 품 명
    private static final int C_SPEC = 1;      // B 규 격
    private static final int C_UNIT = 2;      // C 단위
    private static final int C_QTY = 3;       // D 수 량
    private static final int C_PRICE = 4;     // E 단 가
    private static final int C_AMOUNT = 5;    // F 금 액
    private static final int C_MAKER = 6;     // G 제조사
    private static final int C_NOTE = 7;      // H 비 고
    private static final int C_COST = 8;      // I 사입가
    private static final int C_DIFF = 9;      // J 단가차 (= E-I)
    private static final int C_GAIN = 10;     // K 총차익 (= D*J)
    private static final int C_MARGIN = 11;   // L 마진율

    /** 컬럼 폭(문자). 샘플 실측값. */
    private static final double[] COLUMN_WIDTHS =
            {16.7, 15.0, 5.3, 7.7, 10.7, 14.5, 11.8, 13.2, 10.5, 8.7, 11.8, 7.7};

    /** 본문이 시작하는 행(0-based) = R13 헤더. */
    private static final int HEADER_ROW = 12;

    /** 반복 값을 생략하는 표시. 샘플이 제조사·비고에 쓴다. */
    private static final String DITTO = "\"";

    /** 조건 문구 기본값 (O-9 — 제안서마다 수정 가능). */
    public static final List<String> DEFAULT_TERMS = List.of(
            " 현장 지정위치 하차도 / 아파트 비데설치시 양변기시트 별도(후렌지캡은 납품)",
            " 아파트 샤워기헤드는 고급형(안마헤드)",
            " 현장 샘플세대 무상교체 / 수전간격대 지급",
            " 앵글밸브,호스류등 부속 선납(현장여건에따라 앵글밸브,닛플의 길이가 길어질수 있음)"
    );

    private QuoteExcelWriter() {
    }

    /**
     * @param quoteNo 견적번호(`syt-YYYYMMDDNN`). 부여는 호출부(서비스) 책임이다
     * @param ceoName 서명란 대표이사명
     */
    public static byte[] write(Proposal proposal, List<ProposalLine> lines,
                               QuoteTarget target, String quoteNo, String ceoName) {

        List<ProposalLine> targetLines = lines.stream().filter(target::matches).toList();

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName(proposal));
            Styles st = new Styles(wb);

            for (int c = 0; c < COLUMN_WIDTHS.length; c++) {
                sheet.setColumnWidth(c, (int) (COLUMN_WIDTHS[c] * 256));
            }

            List<Section> sections = buildSections(targetLines, target, proposal);
            int lastRow = writeBody(sheet, st, sections);
            BigDecimal total = sections.stream()
                    .map(Section::total).reduce(BigDecimal.ZERO, BigDecimal::add);

            writeHeadings(sheet, st, proposal, target, quoteNo, ceoName, total);
            writeTail(sheet, st, proposal, sections, lastRow);

            sheet.setPrintGridlines(false);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("견적서 엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    /* ===================== 섹션 구성 ===================== */

    /**
     * 대상 라인을 섹션 → 소분류 묶음으로 정리한다.
     *
     * <p>본세대는 섹션 하나(평형)다. 부속동 합본은 건물 구분값마다 섹션이 하나씩 생긴다.
     */
    private static List<Section> buildSections(List<ProposalLine> lines, QuoteTarget target, Proposal proposal) {
        Map<String, List<ProposalLine>> byBuilding = new LinkedHashMap<>();
        for (ProposalLine line : lines) {
            String key = target.kind() == QuoteTarget.Kind.MAIN
                    ? mainSectionTitle(target, proposal)
                    : nvl(line.getBuildingType());
            byBuilding.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, List<ProposalLine>> e : byBuilding.entrySet()) {
            Map<QuoteItemGroup, List<ProposalLine>> groups = new LinkedHashMap<>();
            // enum 선언 순서(도기 → 수전 → 악세사리 → 기타)를 그대로 출력 순서로 쓴다
            for (QuoteItemGroup g : QuoteItemGroup.values()) {
                List<ProposalLine> items = e.getValue().stream().filter(l -> QuoteItemGroup.of(l) == g).toList();
                if (!items.isEmpty()) groups.put(g, items);
            }
            sections.add(new Section(e.getKey(), groups));
        }
        return sections;
    }

    /** 본세대 섹션 제목 — 샘플의 {@code 아파트 59형(523)} 꼴. */
    private static String mainSectionTitle(QuoteTarget target, Proposal proposal) {
        String type = target.apartmentType() != null && !target.apartmentType().isBlank()
                ? target.apartmentType()
                : nvl(proposal.getApartmentType());
        String size = type.replace("㎡", "").trim();
        Integer households = proposal.getHouseholds();

        if (size.isEmpty()) return "아파트";
        return households != null && households > 0
                ? "아파트 %s형(%d)".formatted(size, households)
                : "아파트 %s형".formatted(size);
    }

    /* ===================== 본문 ===================== */

    private static int writeBody(Sheet sheet, Styles st, List<Section> sections) {
        // 헤더 R13
        Row header = row(sheet, HEADER_ROW, 18f);
        String[] labels = {"품  명", "규 격", "단위", "수 량", "단 가", "금 액", "제조사", "비  고", "사입가"};
        for (int c = 0; c < labels.length; c++) cell(header, c, labels[c], st.header);
        cell(header, C_DIFF, "사입가대비", st.header);
        cell(header, C_GAIN, "", st.header);
        cell(header, C_MARGIN, "", st.header);
        sheet.addMergedRegion(new CellRangeAddress(HEADER_ROW, HEADER_ROW, C_DIFF, C_MARGIN));

        int r = HEADER_ROW + 1;
        boolean firstSection = true;

        for (Section section : sections) {
            Row sectionRow = row(sheet, r, 18f);
            cell(sectionRow, C_NAME, section.title(), st.sectionHeader);
            // 샘플은 섹션 헤더 행의 J열에 `마진`을 적어 사입가대비 3열(J~L)의 소제목을 단다
            if (firstSection) cell(sectionRow, C_DIFF, "마진", st.header);
            firstSection = false;
            r++;

            List<Integer> groupSubtotalRows = new ArrayList<>();
            String lastMaker = null;
            String lastNote = null;

            for (Map.Entry<QuoteItemGroup, List<ProposalLine>> g : section.groups().entrySet()) {
                int firstItemRow = r;
                for (ProposalLine line : g.getValue()) {
                    String maker = nvl(line.getVendorName());
                    String note = nvl(line.getNote());
                    writeItem(sheet, st, r, line, section, maker.equals(lastMaker), note.equals(lastNote));
                    if (!maker.isEmpty()) lastMaker = maker;
                    if (!note.isEmpty()) lastNote = note;
                    r++;
                }
                writeSubtotal(sheet, st, r, firstItemRow, r - 1);
                groupSubtotalRows.add(r);
                r++;
            }

            // 소분류가 둘 이상일 때만 섹션 소계를 낸다 — 하나뿐이면 바로 위 줄과 같은 값이다
            if (groupSubtotalRows.size() > 1) {
                writeSectionTotal(sheet, st, r, groupSubtotalRows);
                section.setTotalRow(r);
                r++;
            } else {
                section.setTotalRow(groupSubtotalRows.get(0));
            }
            r++;   // 섹션 사이 빈 줄
        }
        return r;
    }

    private static void writeItem(Sheet sheet, Styles st, int r, ProposalLine line,
                                  Section section, boolean sameMaker, boolean sameNote) {
        Row row = row(sheet, r, 18f);
        int n = r + 1;   // 수식용 1-based 행번호

        cell(row, C_NAME, section.displayName(line), st.text);
        cell(row, C_SPEC, nvl(line.getMainItemCode()), st.text);
        cell(row, C_UNIT, nvl(line.getUnit()), st.center);
        cell(row, C_QTY, line.getQty() != null ? BigDecimal.valueOf(line.getQty()) : BigDecimal.ZERO, st.number);
        cell(row, C_PRICE, defaultZero(line.getUnitPrice()), st.money);
        formula(row, C_AMOUNT, "E%d*D%d".formatted(n, n), st.money);

        String maker = nvl(line.getVendorName());
        String note = nvl(line.getNote());
        cell(row, C_MAKER, sameMaker && !maker.isEmpty() ? DITTO : maker, st.center);
        cell(row, C_NOTE, sameNote && !note.isEmpty() ? DITTO : note, st.text);

        cell(row, C_COST, defaultZero(line.getCatalogUnitPrice()), st.money);
        formula(row, C_DIFF, "E%d-I%d".formatted(n, n), st.money);
        formula(row, C_GAIN, "D%d*J%d".formatted(n, n), st.money);
        // O-4 ⓐ — 항목행·소계행 모두 사입가 대비로 통일한다. 사입가 0이면 나눗셈이 깨지므로 비운다
        formula(row, C_MARGIN, "IFERROR((E%d-I%d)/I%d,\"\")".formatted(n, n, n), st.percent);
    }

    /** 소분류 소계 — 수량·단가·금액·사입가·단가차·총차익은 SUM, 마진율은 합계끼리 다시 계산한다. */
    private static void writeSubtotal(Sheet sheet, Styles st, int r, int firstItemRow, int lastItemRow) {
        Row row = row(sheet, r, 18f);
        int n = r + 1;
        int from = firstItemRow + 1;
        int to = lastItemRow + 1;

        cell(row, C_NAME, "소 계", st.subtotalLabel);
        for (int c : new int[]{C_SPEC, C_UNIT, C_MAKER, C_NOTE}) cell(row, c, "", st.subtotal);
        for (int c : new int[]{C_QTY, C_PRICE, C_AMOUNT, C_COST, C_DIFF, C_GAIN}) {
            char col = (char) ('A' + c);
            formula(row, c, "SUM(%c%d:%c%d)".formatted(col, from, col, to),
                    c == C_QTY ? st.subtotalNumber : st.subtotalMoney);
        }
        formula(row, C_MARGIN, "IFERROR((E%d-I%d)/I%d,\"\")".formatted(n, n, n), st.subtotalPercent);
    }

    /** 섹션 소계 — 소분류 소계 행들을 더한다(범위 SUM이 아니라 지정 셀 합, 샘플과 동일). */
    private static void writeSectionTotal(Sheet sheet, Styles st, int r, List<Integer> subtotalRows) {
        Row row = row(sheet, r, 18f);
        int n = r + 1;

        cell(row, C_NAME, "소 계", st.subtotalLabel);
        for (int c : new int[]{C_SPEC, C_UNIT, C_MAKER, C_NOTE}) cell(row, c, "", st.subtotal);
        for (int c : new int[]{C_QTY, C_PRICE, C_AMOUNT, C_COST, C_DIFF, C_GAIN}) {
            char col = (char) ('A' + c);
            StringBuilder f = new StringBuilder();
            for (int sr : subtotalRows) {
                if (f.length() > 0) f.append('+');
                f.append(col).append(sr + 1);
            }
            formula(row, c, f.toString(), c == C_QTY ? st.subtotalNumber : st.subtotalMoney);
        }
        formula(row, C_MARGIN, "IFERROR((E%d-I%d)/I%d,\"\")".formatted(n, n, n), st.subtotalPercent);
    }

    /* ===================== 머리글 / 꼬리말 ===================== */

    private static void writeHeadings(Sheet sheet, Styles st, Proposal proposal, QuoteTarget target,
                                      String quoteNo, String ceoName, BigDecimal total) {
        row(sheet, 0, 13f);

        Row r2 = row(sheet, 1, 29f);
        cell(r2, 0, "見  積  書", st.title);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

        LocalDate date = parseDate(proposal.getDate());
        Row r5 = row(sheet, 4, 17f);
        cell(r5, 0, "西   紀 : %d年  %02d月  %02d日".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth()),
                st.text);
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 4));
        // 서명란은 F5:H12 한 칸에 줄바꿈으로 아래쪽에 이름을 앉힌다(샘플과 동일)
        cell(r5, 5, "\n\n\n\n\n\n\n대표이사 : " + nvl(ceoName), st.signature);
        sheet.addMergedRegion(new CellRangeAddress(4, 11, 5, 7));

        heading(sheet, st, 5, "%s 貴下".formatted(nvl(proposal.getClientName())).trim());
        row(sheet, 6, 4f);
        heading(sheet, st, 7, "見積NO  : " + nvl(quoteNo));
        heading(sheet, st, 8, "工事名  : %s 위생기구류 납품".formatted(nvl(proposal.getProjectName())).trim());
        row(sheet, 9, 17f);
        heading(sheet, st, 10, "合計金  :   %s(₩%,d)".formatted(
                KoreanCurrency.toKoreanAmount(total), total.longValue()));
        heading(sheet, st, 11, "下記와   如히   내역 하나이다.");

        // 부속동 합본은 평형이 없으므로 工事名 옆에 무엇을 담은 파일인지 남긴다
        if (target.kind() == QuoteTarget.Kind.ANNEX) {
            Row r4 = row(sheet, 3, 15f);
            cell(r4, 0, "(부속동·상가)", st.text);
        }
    }

    private static void heading(Sheet sheet, Styles st, int rowIdx, String text) {
        Row row = row(sheet, rowIdx, rowIdx == 5 ? 30f : 17f);
        cell(row, 0, text, st.text);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 4));
    }

    private static void writeTail(Sheet sheet, Styles st, Proposal proposal, List<Section> sections, int lastRow) {
        int r = lastRow;
        Row totalRow = row(sheet, r, 18f);
        cell(totalRow, C_NAME, "합 계", st.subtotalLabel);
        for (int c = C_SPEC; c <= C_MARGIN; c++) cell(totalRow, c, "", st.subtotal);

        StringBuilder f = new StringBuilder();
        for (Section s : sections) {
            if (f.length() > 0) f.append('+');
            f.append('F').append(s.totalRow() + 1);
        }
        if (f.length() > 0) formula(totalRow, C_AMOUNT, f.toString(), st.subtotalMoney);
        else cell(totalRow, C_AMOUNT, BigDecimal.ZERO, st.subtotalMoney);

        r += 2;
        for (String term : terms(proposal)) {
            cell(row(sheet, r++, 18f), C_NAME, term, st.text);
        }
    }

    /** 제안서에 조건 문구가 있으면 그것을, 없으면 기본 4줄을 쓴다(O-9). */
    private static List<String> terms(Proposal proposal) {
        String raw = proposal.getQuoteTerms();
        if (raw == null || raw.isBlank()) return DEFAULT_TERMS;
        return List.of(raw.split("\\R"));
    }

    /* ===================== 공통 ===================== */

    private static String sheetName(Proposal proposal) {
        return "견적서_검토_" + parseDate(proposal.getDate())
                .format(DateTimeFormatter.ofPattern("yyMMdd", Locale.KOREA));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private static Row row(Sheet sheet, int index, float height) {
        Row row = sheet.getRow(index);
        if (row == null) row = sheet.createRow(index);
        row.setHeightInPoints(height);
        return row;
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell c = row.getCell(index) != null ? row.getCell(index) : row.createCell(index);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void cell(Row row, int index, BigDecimal value, CellStyle style) {
        Cell c = row.getCell(index) != null ? row.getCell(index) : row.createCell(index);
        c.setCellValue(defaultZero(value).doubleValue());
        c.setCellStyle(style);
    }

    private static void formula(Row row, int index, String formula, CellStyle style) {
        Cell c = row.getCell(index) != null ? row.getCell(index) : row.createCell(index);
        c.setCellFormula(formula);
        c.setCellStyle(style);
    }

    private static BigDecimal defaultZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nvl(String v) {
        return v != null ? v : "";
    }

    /* ===================== 섹션 모델 ===================== */

    /** 견적서 한 섹션(본세대 평형 하나 또는 부속동·상가 중 하나). */
    private static final class Section {
        private final String title;
        private final Map<QuoteItemGroup, List<ProposalLine>> groups;
        private final Map<String, Long> nameCounts = new LinkedHashMap<>();
        private int totalRow;

        Section(String title, Map<QuoteItemGroup, List<ProposalLine>> groups) {
            this.title = title;
            this.groups = groups;
            for (List<ProposalLine> items : groups.values()) {
                for (ProposalLine l : items) {
                    nameCounts.merge(baseName(l), 1L, Long::sum);
                }
            }
        }

        String title() { return title; }
        Map<QuoteItemGroup, List<ProposalLine>> groups() { return groups; }
        int totalRow() { return totalRow; }
        void setTotalRow(int row) { this.totalRow = row; }

        BigDecimal total() {
            BigDecimal sum = BigDecimal.ZERO;
            for (List<ProposalLine> items : groups.values()) {
                for (ProposalLine l : items) {
                    BigDecimal amount = l.getAmount() != null ? l.getAmount()
                            : defaultZero(l.getUnitPrice()).multiply(
                                    BigDecimal.valueOf(l.getQty() != null ? l.getQty() : 0));
                    sum = sum.add(amount);
                }
            }
            return sum;
        }

        /**
         * 품명 — 같은 이름이 섹션 안에 둘 이상이면 부위를 괄호로 덧붙여 구분한다.
         *
         * <p>샘플이 그렇게 돼 있다: 유일한 `투피스양변기`는 그냥 두고, 둘인 비데는 `비데(공용)`·`비데(부부)`로 적었다.
         */
        String displayName(ProposalLine line) {
            String base = baseName(line);
            String area = nvl(line.getArea());
            if (area.isEmpty() || nameCounts.getOrDefault(base, 0L) <= 1) return base;
            return "%s(%s)".formatted(base, area);
        }

        private static String baseName(ProposalLine line) {
            if (line.getCategorySmall() != null && !line.getCategorySmall().isBlank()) return line.getCategorySmall();
            if (line.getVendorItemName() != null && !line.getVendorItemName().isBlank()) return line.getVendorItemName();
            return nvl(line.getProductName());
        }
    }

    /* ===================== 스타일 ===================== */

    private static final class Styles {
        final CellStyle title;
        final CellStyle text;
        final CellStyle center;
        final CellStyle signature;
        final CellStyle header;
        final CellStyle sectionHeader;
        final CellStyle number;
        final CellStyle money;
        final CellStyle percent;
        final CellStyle subtotal;
        final CellStyle subtotalLabel;
        final CellStyle subtotalNumber;
        final CellStyle subtotalMoney;
        final CellStyle subtotalPercent;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFmt = fmt.getFormat("#,##0");
            short numberFmt = fmt.getFormat("#,##0");
            short percentFmt = fmt.getFormat("0.0%");

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 20);

            Font bold = wb.createFont();
            bold.setBold(true);

            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            text = base(wb, HorizontalAlignment.LEFT, false);
            center = bordered(base(wb, HorizontalAlignment.CENTER, false));

            signature = wb.createCellStyle();
            signature.setAlignment(HorizontalAlignment.CENTER);
            signature.setVerticalAlignment(VerticalAlignment.BOTTOM);
            signature.setWrapText(true);

            header = bordered(base(wb, HorizontalAlignment.CENTER, true));
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            sectionHeader = bordered(base(wb, HorizontalAlignment.LEFT, true));

            number = bordered(base(wb, HorizontalAlignment.RIGHT, false));
            number.setDataFormat(numberFmt);
            money = bordered(base(wb, HorizontalAlignment.RIGHT, false));
            money.setDataFormat(moneyFmt);
            percent = bordered(base(wb, HorizontalAlignment.RIGHT, false));
            percent.setDataFormat(percentFmt);

            subtotal = bordered(base(wb, HorizontalAlignment.CENTER, true));
            subtotalLabel = bordered(base(wb, HorizontalAlignment.CENTER, true));
            subtotalNumber = bordered(base(wb, HorizontalAlignment.RIGHT, true));
            subtotalNumber.setDataFormat(numberFmt);
            subtotalMoney = bordered(base(wb, HorizontalAlignment.RIGHT, true));
            subtotalMoney.setDataFormat(moneyFmt);
            subtotalPercent = bordered(base(wb, HorizontalAlignment.RIGHT, true));
            subtotalPercent.setDataFormat(percentFmt);
        }

        private static CellStyle base(Workbook wb, HorizontalAlignment align, boolean bold) {
            CellStyle s = wb.createCellStyle();
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            if (bold) {
                Font f = wb.createFont();
                f.setBold(true);
                s.setFont(f);
            }
            return s;
        }

        private static CellStyle bordered(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
