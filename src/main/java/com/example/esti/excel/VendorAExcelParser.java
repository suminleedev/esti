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

            Sheet sheet = workbook.getSheetAt(0); // A사 시트 0번이라고 가정

            String currentCategory = null;   // B열: 대분류(카테고리)
            String currentSetName  = null;   // C열: 세트명/시리즈명

            // 세트 안에서 대표 품목/코드를 기억하기 위한 변수
            String primaryProductName = null;  // 첫 구성품의 품명(D열)
            String primaryOldCode     = null;  // 첫 구성품의 구품번(E열)
            String primaryNewCode     = null;  // 첫 구성품의 신품번(F열)

            // 0행은 비어있거나 상단 타이틀, 1행(엑셀 기준 2행)이 헤더라고 보고 2행부터 데이터
            int firstDataRow = 1;

            for (int rowIdx = firstDataRow; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String colB = getStringCell(row, 1); // category
                String colC = getStringCell(row, 2); // setName
                String colD = getStringCell(row, 3); // itemName
                String colE = getStringCell(row, 4); // oldCode
                String colF = getStringCell(row, 5); // newCode
                BigDecimal colG = getNumericCell(row, 6); // price

                // 완전 빈 줄이면 스킵
                if (isBlank(colB) && isBlank(colC) && isBlank(colD)
                        && isBlank(colE) && isBlank(colF) && colG == null) {
                    continue;
                }

                // 1) 헤더 줄 스킵 (B: "제품명", E: "구품번", F: "신품번" 등)
                if ("제품명".equals(colB) || "구품번".equals(colE) || "신품번".equals(colF)) {
                    continue;
                }

                // 2) 카테고리 행 판별 (B만 값 있고 나머지는 비어있으면 카테고리)
                boolean isCategoryRow =
                        !isBlank(colB) &&
                                isBlank(colC) &&
                                isBlank(colD) &&
                                isBlank(colE) &&
                                isBlank(colF) &&
                                colG == null;

                if (isCategoryRow) {
                    currentCategory = colB.trim();
                    currentSetName = null;

                    // 새 카테고리 시작 → 세트 대표 정보 초기화
                    primaryProductName = null;
                    primaryOldCode     = null;
                    primaryNewCode     = null;
                    continue;
                }

                // 3) 세트명(C열) 갱신
                if (!isBlank(colC)) {
                    currentSetName = colC.trim();

                    // 새 세트 시작 → 대표 정보 초기화
                    primaryProductName = null;
                    primaryOldCode     = null;
                    primaryNewCode     = null;
                }

                // 세트명이 아직 없다면 세트 데이터로 간주하지 않음
                if (isBlank(currentSetName)) {
                    continue;
                }

                // 4) 세트 합계 행 판별 (D/E/F 비어 있고 G에만 값이 있는 행)
                boolean isSetTotalRow =
                        isBlank(colD) &&
                                isBlank(colE) &&
                                isBlank(colF) &&
                                colG != null;

                if (isSetTotalRow) {
                    BigDecimal totalPrice = colG;

                    // 대표 품목명: 세트 내 첫 구성품 이름이 있으면 그걸 쓰고, 없으면 세트명 사용
                    String vendorItemName =
                            !isBlank(primaryProductName) ? primaryProductName : currentSetName;

                    String oldCode = primaryOldCode;
                    String newCode = primaryNewCode;

                    // 필요하다면 여기서 newCode가 비어있으면 스킵할지 결정
                    // if (isBlank(newCode)) { continue; }

                    VendorExcelRow dto = new VendorExcelRow(
                            "A",                     // vendorCode
                            currentCategory,         // categoryLarge (대분류)
                            currentSetName,          // categorySmall (세트/시리즈명)
                            currentSetName,          // productName (세트명)
                            newCode,                 // masterCodeHint
                            newCode,                 // proposalItemCode
                            newCode,                 // mainItemCode
                            null,                    // subItemCode
                            oldCode,                 // oldItemCode
                            vendorItemName,          // vendorItemName (대표 품목명)
                            null,                    // vendorSpec
                            null,                    // remark
                            totalPrice,              // unitPrice (세트 합계금액)
                            "SET"                    // priceType
                    );
                    result.add(dto);

                    // 세트 종료 → 다음 세트를 위해 대표 정보 초기화
                    primaryProductName = null;
                    primaryOldCode     = null;
                    primaryNewCode     = null;

                    continue;
                }

                // 5) 일반 구성품 행 (세트 내부 품목)
                boolean isComponentRow =
                        !isBlank(colD) ||
                                !isBlank(colE) ||
                                !isBlank(colF) ||
                                colG != null;

                if (isComponentRow) {
                    // 세트의 "대표 품목"을 아직 안 정했다면 이 구성품을 대표로 사용
                    if (isBlank(primaryProductName)) {
                        primaryProductName = colD;
                    }
                    if (isBlank(primaryOldCode)) {
                        primaryOldCode = colE;
                    }
                    if (isBlank(primaryNewCode)) {
                        primaryNewCode = colF;
                    }

                    // 지금은 구성품 자체는 VendorExcelRow로 생성하지 않고,
                    // 세트 합계 행에서 대표 정보 + 합계금액만 SET 타입으로 생성.
                    // 필요하면 여기서 itemType="COMP" 등으로 추가 행 만들 수 있음.
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("A사 엑셀 파싱 중 오류", e);
        }

        return result;
    }

    // ====== 공통 유틸 메서드들 ======

    private String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.rint(val)) { // 정수 형태면 소수점 제거
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
            String txt = cell.getStringCellValue()
                    .replace(",", "")
                    .replace("₩", "")
                    .replace("원", "")
                    .trim();
            if (txt.isEmpty()) return null;
            try {
                return new BigDecimal(txt);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
