package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VendorAExcelParser implements VendorExcelParser {

    @Override
    public String getVendorCode() {
        return "A";
    }

    @Override
    public List<VendorExcelRow> parse(MultipartFile file) {
        List<VendorExcelRow> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0); // 필요시 시트 선택 로직

            // 0행이 헤더라고 가정
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                // ===== TODO: 실제 인덱스에 맞게 수정 =====
                String categoryLarge  = getStringCell(row, 0); // 품목대분류
                String categorySmall  = getStringCell(row, 1); // 품목소분류
                String productName    = getStringCell(row, 2); // 제품명

                String mainPartName   = getStringCell(row, 3); // 메인부속품명
                // 하위부속품은 item_component로 별도 파싱 가능

                String oldCode        = getStringCell(row, 5); // 구품번
                String newCode        = getStringCell(row, 6); // 신품번
                BigDecimal totalPrice = getNumericCell(row, 7); // 합계 단가

                if (newCode == null || newCode.isBlank()) {
                    continue; // 품번 없는 행은 스킵
                }

                VendorExcelRow dto = new VendorExcelRow(
                        "A",
                        categoryLarge,
                        categorySmall,
                        productName,
                        newCode,         // masterCodeHint: 일단 A사 신품번
                        newCode,         // proposalItemCode: 제안서 품번 = 메인부속 신품번
                        newCode,         // mainItemCode
                        null,            // subItemCode
                        oldCode,         // oldItemCode
                        productName,     // vendorItemName
                        null,            // vendorSpec
                        null,            // remark
                        totalPrice,
                        "SET"
                );
                result.add(dto);
            }

        } catch (Exception e) {
            throw new RuntimeException("A사 엑셀 파싱 중 오류", e);
        }

        return result;
    }

    private String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.rint(val)) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        } else if (cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return cell.toString().trim();
    }

    private BigDecimal getNumericCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.STRING) {
            String txt = cell.getStringCellValue().replace(",", "").trim();
            if (txt.isEmpty()) return null;
            try {
                return new BigDecimal(txt);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

