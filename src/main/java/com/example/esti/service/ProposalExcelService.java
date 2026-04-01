package com.example.esti.service;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalExcelService {

    private final ProposalRepository proposalRepository;
    private final ProposalLineRepository proposalLineRepository;

    public byte[] exportProposal(Long proposalId) {
        Proposal proposal = proposalRepository.findByIdAndDeletedAtIsNull(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("제안서를 찾을 수 없습니다. id=" + proposalId));

        if (proposal.getStatus() != Proposal.Status.SENT) {
            throw new IllegalStateException("발송완료 상태의 제안서만 출력할 수 있습니다.");
        }

        List<ProposalLine> lines = proposalLineRepository.findByProposalId(proposalId);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Proposal");

            // 스타일
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            int rowIdx = 0;

            // ===== 상단 기본정보 =====
            rowIdx = writeProposalInfo(sheet, proposal, rowIdx, textStyle, numberStyle);

            rowIdx++; // 한 줄 띄움

            // ===== 헤더 =====
            Row headerRow = sheet.createRow(rowIdx++);
            String[] headers = {
                    "No", "공간", "카테고리", "제품명", "업체코드", "업체명", "업체품명",
                    "메인품목코드", "구품목코드", "카탈로그단가", "마진율", "최종단가",
                    "수량", "금액", "비고", "메모", "이미지URL"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ===== 데이터 =====
            int no = 1;
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (ProposalLine line : lines) {
                Row row = sheet.createRow(rowIdx++);

                createCell(row, 0, no++, textStyle);
                createCell(row, 1, nvl(line.getArea()), textStyle);
                createCell(row, 2, nvl(line.getCategory()), textStyle);
                createCell(row, 3, nvl(line.getProductName()), textStyle);
                createCell(row, 4, nvl(line.getVendorCode()), textStyle);
                createCell(row, 5, nvl(line.getVendorName()), textStyle);
                createCell(row, 6, nvl(line.getVendorItemName()), textStyle);
                createCell(row, 7, nvl(line.getMainItemCode()), textStyle);
                createCell(row, 8, nvl(line.getOldItemCode()), textStyle);

                createCell(row, 9, defaultBigDecimal(line.getCatalogUnitPrice()).doubleValue(), numberStyle);
                createCell(row, 10, defaultBigDecimal(line.getMarginRate()).doubleValue(), numberStyle);
                createCell(row, 11, defaultBigDecimal(line.getUnitPrice()).doubleValue(), numberStyle);

                createCell(row, 12, line.getQty() != null ? line.getQty() : 0, numberStyle);

                BigDecimal amount = line.getAmount() != null
                        ? line.getAmount()
                        : calculateAmount(line.getUnitPrice(), line.getQty());

                createCell(row, 13, amount.doubleValue(), numberStyle);
                createCell(row, 14, nvl(line.getRemark()), textStyle);
                createCell(row, 15, nvl(line.getNote()), textStyle);
                createCell(row, 16, nvl(line.getImageUrl()), textStyle);

                totalAmount = totalAmount.add(amount);
            }

            // ===== 합계 =====
            Row totalRow = sheet.createRow(rowIdx);
            createCell(totalRow, 12, "합계", headerStyle);
            createCell(totalRow, 13, totalAmount.doubleValue(), numberStyle);

            // 컬럼 너비
            int[] widths = {
                    3000, 5000, 5000, 8000, 5000, 6000, 7000,
                    5000, 5000, 4500, 4000, 4500,
                    3000, 5000, 7000, 7000, 10000
            };

            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i]);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    private int writeProposalInfo(
            Sheet sheet,
            Proposal proposal,
            int rowIdx,
            CellStyle textStyle,
            CellStyle numberStyle
    ) {
        Row row0 = sheet.createRow(rowIdx++);
        createCell(row0, 0, "제안서 ID", textStyle);
        createCell(row0, 1, proposal.getId() != null ? proposal.getId() : 0L, numberStyle);

        Row row1 = sheet.createRow(rowIdx++);
        createCell(row1, 0, "프로젝트명", textStyle);
        createCell(row1, 1, nvl(proposal.getProjectName()), textStyle);

        Row row2 = sheet.createRow(rowIdx++);
        createCell(row2, 0, "담당자", textStyle);
        createCell(row2, 1, nvl(proposal.getManager()), textStyle);

        Row row3 = sheet.createRow(rowIdx++);
        createCell(row3, 0, "작성일", textStyle);
        createCell(row3, 1, proposal.getDate() != null ? proposal.getDate().toString() : "", textStyle);

        Row row4 = sheet.createRow(rowIdx++);
        createCell(row4, 0, "아파트 타입", textStyle);
        createCell(row4, 1, nvl(proposal.getApartmentType()), textStyle);

        Row row5 = sheet.createRow(rowIdx++);
        createCell(row5, 0, "세대수", textStyle);
        createCell(row5, 1, proposal.getHouseholds() != null ? proposal.getHouseholds() : 0, numberStyle);

        Row row6 = sheet.createRow(rowIdx++);
        createCell(row6, 0, "비고", textStyle);
        createCell(row6, 1, nvl(proposal.getNote()), textStyle);

        return rowIdx;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return style;
    }

    private CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("#,##0.00"));

        return style;
    }

    private void createCell(Row row, int cellIndex, String value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int cellIndex, int value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int cellIndex, long value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int cellIndex, double value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private BigDecimal calculateAmount(BigDecimal unitPrice, Integer qty) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        int quantity = qty != null ? qty : 0;
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}