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
public class VendorBExcelParser implements VendorExcelParser {

    @Override
    public String getVendorCode() {
        return "B";
    }

    @Override
    public List<VendorExcelRow> parse(MultipartFile file) {
        List<VendorExcelRow> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            // ★ B사는 시트별로 대분류가 나뉘어 있으므로,
            //   for each sheet 돌면서 sheetName을 categoryLarge로 쓰는 식으로 가능
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                String categoryLarge = sheet.getSheetName(); // 대분류로 활용

                // 시트마다 컬럼 인덱스가 다르면, sheetName으로 분기해서 인덱스 설정
                // 예: SheetConfig config = getConfigForSheet(sheet.getSheetName());

                for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) continue;

                    // ===== TODO: 시트별 컬럼 인덱스에 맞게 수정 =====
                    String categorySmall  = getStringCell(row, 0); // 품목소분류
                    String productCode    = getStringCell(row, 1); // 제품 품번(제안서 품번)
                    String subCode        = getStringCell(row, 2); // 보조품번
                    String mainPartName   = getStringCell(row, 3); // 메인부속품명
                    // 하위부속품은 item_component로 별도 처리
                    BigDecimal totalPrice = getNumericCell(row, 5); // 합계 단가
                    String remark         = getStringCell(row, 6);  // 비고(규격/특징)

                    if (productCode == null || productCode.isBlank()) {
                        continue;
                    }

                    VendorExcelRow dto = new VendorExcelRow(
                            "B",
                            categoryLarge,
                            categorySmall,
                            mainPartName,   // 또는 별도 제품명 컬럼이 있으면 그걸 사용
                            productCode,    // masterCodeHint: 일단 B사 제품품번
                            productCode,    // proposalItemCode: 제안서 품번
                            productCode,    // mainItemCode: 필요시
                            subCode,        // subItemCode: 보조품번
                            null,           // oldItemCode
                            mainPartName,   // vendorItemName
                            remark,         // vendorSpec or remark
                            remark,
                            totalPrice,
                            "SET"
                    );
                    result.add(dto);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("B사 엑셀 파싱 중 오류", e);
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

