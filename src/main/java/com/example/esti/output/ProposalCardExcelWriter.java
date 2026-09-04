package com.example.esti.output;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.util.ImagePathUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 제안서(고객 제출용) 엑셀 렌더러 — 8행 카드 × 4열 그리드 (Phase 6 P2).
 *
 * <p>사입가·마진율은 <b>싣지 않는다.</b> 그 정보는 견적서(검토)로 간다(O-6).
 *
 * <p>양식은 `docs/samples/제안서_sample.xlsx`를 실측해 재현했다.
 * <pre>
 *   R1        제목 (A1:L1 병합)
 *   R2        총액 (K2:L2)            = 세대당 × 세대수
 *   R3        "세대당"(J3) + 금액 (K3:L3)
 *   R4        열별 세대당 소계 — 라벨(B/E/H/K) + 값(C/F/I/L)
 *   R5~R12    카드 블록 1 (8행)   ─┐
 *   R13~R20   카드 블록 2          ├ 품목 수만큼 아래로 반복 (상한 없음, O-2 ⓑ)
 *   ...                            ─┘
 * </pre>
 *
 * <p>열 구성은 카드 1장이 3열이다 — 이미지(A) · 라벨(B) · 값(C). 이것이 4벌 반복된다.
 */
public final class ProposalCardExcelWriter {

    private static final Logger logger = LoggerFactory.getLogger(ProposalCardExcelWriter.class);

    /** 카드 1장이 차지하는 행 수. */
    private static final int BLOCK_ROWS = 8;
    /** 카드 블록이 시작하는 행(0-based) = R5. 위 4행은 제목·총액·세대당·소계다. */
    private static final int FIRST_BLOCK_ROW = 4;

    /**
     * 한 페이지에 넣을 블록 수 (F-023).
     *
     * <p>블록 하나가 카드 한 줄(8행 × 4열)이라 <b>페이지는 반드시 블록 경계에서 넘어가야 한다.</b>
     * 예전에는 페이지 나누기를 하나도 두지 않아 엑셀이 알아서 잘랐고, 그러면 카드가 반으로 갈렸다.
     *
     * <p>4로 둔 이유는 세로 축소율 때문이다. 블록 높이 합이 195pt이고 A4 가로 인쇄면이 약 487pt라
     * 4블록(780pt)을 담으려면 62%로 줄어야 하는데, 가로 폭을 한 장에 맞추는 축소율이 이미
     * 그와 비슷한 수준이라 <b>이 값 때문에 글씨가 더 작아지지는 않는다.</b>
     */
    private static final int BLOCKS_PER_PAGE = 4;

    /** 카드 열별 시작 컬럼(0-based). 각 카드는 여기서부터 이미지·라벨·값 3칸이다. */
    private static final int[] CARD_START_COL = {0, 3, 6, 9};

    /** 컬럼 폭(1/256 문자). 샘플 실측값 — 마지막 값열(L)만 넓다. */
    private static final int WIDTH_IMAGE = 9130;
    private static final int WIDTH_LABEL = 1962;
    private static final int WIDTH_VALUE = 4010;
    private static final int WIDTH_VALUE_LAST = 6272;

    /** 카드 8행의 높이(pt). 샘플 실측값 — 원산지 행만 낮다. */
    private static final float[] BLOCK_ROW_HEIGHTS = {25f, 25f, 25f, 30f, 25f, 25f, 15f, 25f};

    /** 카드 8행의 라벨. 3번째(모델명)는 라벨 없이 라벨·값 칸을 병합해 품번만 쓴다. */
    private static final String[] CARD_LABELS = {"평형", "부위", null, "사양", "금액", "업체", "원산지", "비고"};

    /** 카드 안에서 모델명(품번)이 놓이는 행 오프셋. */
    private static final int ROW_MODEL = 2;

    private ProposalCardExcelWriter() {
    }

    public static byte[] write(Proposal proposal, List<ProposalLine> lines) {
        List<List<ProposalLine>> columns = ProposalCardLayout.distribute(lines);
        int blocks = ProposalCardLayout.blockCount(columns);

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName(proposal));
            Styles styles = new Styles(wb);

            applyColumnWidths(sheet);
            writeHeader(sheet, styles, proposal, columns);

            for (int block = 0; block < blocks; block++) {
                for (int col = 0; col < ProposalCardLayout.COLUMNS; col++) {
                    List<ProposalLine> column = columns.get(col);
                    if (block >= column.size()) continue;
                    writeCard(sheet, wb, styles, column.get(block), block, col);
                }
            }

            // 카드가 한 장도 없어도 머리글만 있는 파일은 나온다 — 빈 제안서를 오류로 보지 않는다.
            sheet.setPrintGridlines(false);
            sheet.getPrintSetup().setLandscape(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            applyPageBreaks(sheet, blocks);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("제안서 엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    /* ===================== 머리글 ===================== */

    private static void writeHeader(Sheet sheet, Styles styles, Proposal proposal,
                                    List<List<ProposalLine>> columns) {
        // R1 제목
        Row r1 = row(sheet, 0, 50f);
        cell(r1, 0, title(proposal), styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

        BigDecimal perHousehold = BigDecimal.ZERO;
        BigDecimal[] columnSums = new BigDecimal[ProposalCardLayout.COLUMNS];
        for (int c = 0; c < ProposalCardLayout.COLUMNS; c++) {
            columnSums[c] = columns.get(c).stream()
                    .map(ProposalCardExcelWriter::lineAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 유상옵션 열은 계약금액이 아니라 별도 청구분이라 합계에서 뺀다(ProposalCardLayout.OPTION_COLUMN)
            if (c != ProposalCardLayout.OPTION_COLUMN) {
                perHousehold = perHousehold.add(columnSums[c]);
            }
        }

        // R2 총액 = 세대당 × 세대수. 세대수가 없으면 세대당 금액을 그대로 총액으로 본다.
        int households = proposal.getHouseholds() != null ? proposal.getHouseholds() : 0;
        BigDecimal total = households > 0
                ? perHousehold.multiply(BigDecimal.valueOf(households))
                : perHousehold;

        Row r2 = row(sheet, 1, 30f);
        cell(r2, 10, total, styles.totalAmount);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 10, 11));

        // R3 세대당
        Row r3 = row(sheet, 2, 30f);
        cell(r3, 9, "세대당", styles.label);
        cell(r3, 10, perHousehold, styles.perHousehold);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 10, 11));

        // R4 열별 세대당 소계 — 라벨 자리에는 제안서 기준 평형을 쓴다(샘플과 동일)
        Row r4 = row(sheet, 3, 35f);
        String baseType = nvl(proposal.getApartmentType());
        for (int c = 0; c < ProposalCardLayout.COLUMNS; c++) {
            int start = CARD_START_COL[c];
            cell(r4, start + 1, baseType, styles.label);
            if (c == ProposalCardLayout.OPTION_COLUMN) {
                // 옵션 열은 소계 칸을 비운다 — 샘플과 동일하게 라벨 자리만 남긴다
                cell(r4, start + 2, "", styles.subtotal);
            } else {
                cell(r4, start + 2, columnSums[c], styles.subtotal);
            }
        }
    }

    /**
     * 페이지가 블록 경계에서만 넘어가게 한다 (F-023).
     *
     * <p>블록 경계마다 <b>수동</b> 페이지 나누기를 넣고, 그렇게 생기는 페이지 수를
     * {@code fitHeight}로 함께 알려 준다. 나누기만 넣고 세로를 «무제한»으로 두면
     * 축소율에 따라 엑셀이 그 사이에 자동 나누기를 하나 더 끼워 카드가 갈릴 수 있다.
     * 몇 장인지 못 박아야 그 안에 들어가도록 축소율이 정해진다.
     */
    private static void applyPageBreaks(Sheet sheet, int blocks) {
        if (blocks <= 0) {
            sheet.getPrintSetup().setFitHeight((short) 1);
            return;
        }
        for (int block = BLOCKS_PER_PAGE; block < blocks; block += BLOCKS_PER_PAGE) {
            // setRowBreak은 «그 행 다음»에서 끊는다 — 블록의 첫 행 바로 앞을 지정한다.
            sheet.setRowBreak(FIRST_BLOCK_ROW + block * BLOCK_ROWS - 1);
        }
        int pages = (blocks + BLOCKS_PER_PAGE - 1) / BLOCKS_PER_PAGE;
        sheet.getPrintSetup().setFitHeight((short) pages);
    }

    private static String title(Proposal proposal) {
        String project = nvl(proposal.getProjectName());
        Integer households = proposal.getHouseholds();
        // 샘플 제목: `[현장명] "세대" 위생기구류 계약 내역`
        return households != null && households > 0
                ? "%s %d세대 위생기구류 계약 내역".formatted(project, households)
                : "%s 위생기구류 계약 내역".formatted(project);
    }

    private static String sheetName(Proposal proposal) {
        LocalDate date = parseDate(proposal.getDate());
        return "제안서_" + date.format(DateTimeFormatter.ofPattern("yyMMdd", Locale.KOREA));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /* ===================== 카드 1장 ===================== */

    private static void writeCard(Sheet sheet, Workbook wb, Styles styles,
                                  ProposalLine line, int block, int col) {
        int top = FIRST_BLOCK_ROW + block * BLOCK_ROWS;
        int startCol = CARD_START_COL[col];
        int imageCol = startCol;
        int labelCol = startCol + 1;
        int valueCol = startCol + 2;

        String[] values = {
                nvl(line.getApartmentType()),
                nvl(line.getArea()),
                nvl(line.getMainItemCode()),
                nvl(line.getCategorySmall()),
                null,                        // 금액은 숫자라 따로 쓴다
                nvl(line.getVendorName()),
                "",                          // 원산지 — 값은 비우고 자리만 지킨다(O-1)
                nvl(line.getNote())
        };

        for (int i = 0; i < BLOCK_ROWS; i++) {
            Row row = row(sheet, top + i, BLOCK_ROW_HEIGHTS[i]);

            if (i == ROW_MODEL) {
                // 모델명 행은 라벨 없이 라벨·값 칸을 병합해 품번만 쓴다
                cell(row, labelCol, values[i], styles.model);
                cell(row, valueCol, "", styles.model);
                sheet.addMergedRegion(new CellRangeAddress(top + i, top + i, labelCol, valueCol));
                continue;
            }

            cell(row, labelCol, CARD_LABELS[i], styles.label);
            if (values[i] == null) {
                cell(row, valueCol, defaultZero(line.getUnitPrice()), styles.amount);
            } else {
                cell(row, valueCol, values[i], styles.value);
            }
        }

        // 이미지 칸: 8행 세로 병합 + 그림 삽입
        sheet.addMergedRegion(new CellRangeAddress(top, top + BLOCK_ROWS - 1, imageCol, imageCol));
        for (int i = 0; i < BLOCK_ROWS; i++) {
            cell(sheet.getRow(top + i), imageCol, "", styles.image);
        }
        drawImage(sheet, wb, line.getImageUrl(), imageCol, top);
    }

    /* ===================== 이미지 ===================== */

    /**
     * 병합된 이미지 칸에 그림을 넣는다. 칸 안에서 <b>비율을 지키며 가운데</b>에 놓는다.
     *
     * <p>칸을 꽉 채우도록 늘이면(MOVE_AND_RESIZE) 세로로 긴 제품(수전·샤워기)이 눌려 보인다.
     * 파일이 없거나 읽을 수 없는 포맷이면 조용히 건너뛴다 — 이미지 한 장 때문에 출력이 막히면 안 된다.
     */
    private static void drawImage(Sheet sheet, Workbook wb, String imageUrl, int imageCol, int top) {
        if (imageUrl == null || imageUrl.isBlank()) return;

        Path path = ImagePathUtils.toFilePath(imageUrl);
        if (path == null || !Files.isRegularFile(path)) return;

        int pictureType = pictureTypeOf(imageUrl);
        if (pictureType < 0) return;   // EMF 등 POI가 못 넣는 포맷

        try {
            byte[] bytes = Files.readAllBytes(path);
            Dimension native_ = imageSize(bytes);
            if (native_ == null) return;

            long boxWidth = columnWidthEmu(sheet, imageCol);
            long boxHeight = 0;
            for (int i = 0; i < BLOCK_ROWS; i++) {
                boxHeight += (long) (BLOCK_ROW_HEIGHTS[i] * Units.EMU_PER_POINT);
            }
            // 테두리에 딱 붙지 않게 사방으로 조금 띄운다
            long padding = Units.EMU_PER_POINT * 2;
            boxWidth -= padding * 2;
            boxHeight -= padding * 2;
            if (boxWidth <= 0 || boxHeight <= 0) return;

            double scale = Math.min(boxWidth / (double) (native_.width * Units.EMU_PER_PIXEL),
                    boxHeight / (double) (native_.height * Units.EMU_PER_PIXEL));
            long drawWidth = (long) (native_.width * Units.EMU_PER_PIXEL * scale);
            long drawHeight = (long) (native_.height * Units.EMU_PER_PIXEL * scale);

            long offsetX = padding + (boxWidth - drawWidth) / 2;
            long offsetY = padding + (boxHeight - drawHeight) / 2;

            // 세로 끝점은 행 경계를 넘어가므로 어느 행의 몇 EMU 지점인지 되짚는다
            long remaining = offsetY + drawHeight;
            int endRow = top;
            for (int i = 0; i < BLOCK_ROWS; i++) {
                long rowEmu = (long) (BLOCK_ROW_HEIGHTS[i] * Units.EMU_PER_POINT);
                if (remaining <= rowEmu) { endRow = top + i; break; }
                remaining -= rowEmu;
                endRow = top + i;
            }

            long startRemaining = offsetY;
            int startRow = top;
            for (int i = 0; i < BLOCK_ROWS; i++) {
                long rowEmu = (long) (BLOCK_ROW_HEIGHTS[i] * Units.EMU_PER_POINT);
                if (startRemaining < rowEmu) { startRow = top + i; break; }
                startRemaining -= rowEmu;
                startRow = top + i;
            }

            int pictureIdx = wb.addPicture(bytes, pictureType);
            Drawing<?> drawing = sheet.getDrawingPatriarch() != null
                    ? sheet.getDrawingPatriarch()
                    : sheet.createDrawingPatriarch();

            XSSFClientAnchor anchor = new XSSFClientAnchor(
                    (int) offsetX, (int) startRemaining, (int) (offsetX + drawWidth), (int) remaining,
                    imageCol, startRow, imageCol, endRow);
            anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
            drawing.createPicture(anchor, pictureIdx);
        } catch (Exception e) {
            logger.warn("[제안서출력] 이미지 삽입 실패(건너뜀): {} — {}", imageUrl, e.toString());
        }
    }

    private static Dimension imageSize(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        return img == null ? null : new Dimension(img.getWidth(), img.getHeight());
    }

    private static int pictureTypeOf(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return Workbook.PICTURE_TYPE_JPEG;
        return -1;
    }

    /** 컬럼 폭(1/256 문자) → EMU. 기본 폰트 기준 1문자 ≈ 7px으로 환산한다. */
    private static long columnWidthEmu(Sheet sheet, int col) {
        return (long) (sheet.getColumnWidth(col) / 256.0 * 7.0 * Units.EMU_PER_PIXEL);
    }

    /* ===================== 공통 ===================== */

    private static void applyColumnWidths(Sheet sheet) {
        for (int c = 0; c < ProposalCardLayout.COLUMNS; c++) {
            int start = CARD_START_COL[c];
            sheet.setColumnWidth(start, WIDTH_IMAGE);
            sheet.setColumnWidth(start + 1, WIDTH_LABEL);
            sheet.setColumnWidth(start + 2, c == ProposalCardLayout.COLUMNS - 1 ? WIDTH_VALUE_LAST : WIDTH_VALUE);
        }
    }

    private static BigDecimal lineAmount(ProposalLine line) {
        if (line.getAmount() != null) return line.getAmount();
        BigDecimal unit = defaultZero(line.getUnitPrice());
        int qty = line.getQty() != null ? line.getQty() : 0;
        return unit.multiply(BigDecimal.valueOf(qty));
    }

    private static Row row(Sheet sheet, int index, float heightPoints) {
        Row row = sheet.getRow(index);
        if (row == null) row = sheet.createRow(index);
        row.setHeightInPoints(heightPoints);
        return row;
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.getCell(index);
        if (cell == null) cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void cell(Row row, int index, BigDecimal value, CellStyle style) {
        Cell cell = row.getCell(index);
        if (cell == null) cell = row.createCell(index);
        cell.setCellValue(defaultZero(value).doubleValue());
        cell.setCellStyle(style);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    /** 스타일 묶음. 워크북마다 한 번만 만든다(셀마다 만들면 엑셀 스타일 한도에 걸린다). */
    private static final class Styles {
        final CellStyle title;
        final CellStyle totalAmount;
        final CellStyle perHousehold;
        final CellStyle subtotal;
        final CellStyle label;
        final CellStyle value;
        final CellStyle model;
        final CellStyle amount;
        final CellStyle image;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();
            short moneyFormat = fmt.getFormat("#,##0");

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            totalAmount = wb.createCellStyle();
            totalAmount.setFont(titleFont);
            totalAmount.setAlignment(HorizontalAlignment.RIGHT);
            totalAmount.setVerticalAlignment(VerticalAlignment.CENTER);
            totalAmount.setDataFormat(moneyFormat);

            perHousehold = wb.createCellStyle();
            perHousehold.setFont(boldFont);
            perHousehold.setAlignment(HorizontalAlignment.RIGHT);
            perHousehold.setVerticalAlignment(VerticalAlignment.CENTER);
            perHousehold.setDataFormat(moneyFormat);

            subtotal = wb.createCellStyle();
            subtotal.setFont(boldFont);
            subtotal.setAlignment(HorizontalAlignment.RIGHT);
            subtotal.setVerticalAlignment(VerticalAlignment.CENTER);
            subtotal.setDataFormat(moneyFormat);
            border(subtotal);

            label = wb.createCellStyle();
            label.setFont(boldFont);
            label.setAlignment(HorizontalAlignment.CENTER);
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            label.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(label);

            value = wb.createCellStyle();
            value.setVerticalAlignment(VerticalAlignment.CENTER);
            value.setWrapText(true);
            border(value);

            model = wb.createCellStyle();
            model.setFont(boldFont);
            model.setAlignment(HorizontalAlignment.CENTER);
            model.setVerticalAlignment(VerticalAlignment.CENTER);
            border(model);

            amount = wb.createCellStyle();
            amount.setAlignment(HorizontalAlignment.RIGHT);
            amount.setVerticalAlignment(VerticalAlignment.CENTER);
            amount.setDataFormat(moneyFormat);
            border(amount);

            image = wb.createCellStyle();
            image.setAlignment(HorizontalAlignment.CENTER);
            image.setVerticalAlignment(VerticalAlignment.CENTER);
            border(image);
        }

        private static void border(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
