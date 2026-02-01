package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VendorAExcelParser implements VendorExcelParser {

    private static final Logger logger = LoggerFactory.getLogger(VendorAExcelParser.class);

    @Override
    public String getVendorCode() { return "A"; }

    /** 기존 parse 함수 -> InputStream 오버로드함수로 위임 */
    @Override
    public List<VendorExcelRow> parse(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return parse(is); // InputStream 버전으로 위임
        } catch (Exception e) {
            throw wrap("A사 엑셀 파싱 중 오류", e);
        }
    }

    /** 비동기/임시파일/테스트에서 재사용 가능한 InputStream 버전 */
    public List<VendorExcelRow> parse(InputStream is) {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            return parseWorkbook(workbook); // 실제 파싱 로직
        } catch (Exception e) {
            throw wrap("A사 엑셀 파싱 중 오류", e);
        }
    }

    /** (선택) Path로도 바로 받을 수 있게 */
    public List<VendorExcelRow> parse(java.nio.file.Path path) {
        try (InputStream is = java.nio.file.Files.newInputStream(path)) {
            return parse(is);
        } catch (Exception e) {
            throw wrap("A사 엑셀 파싱 중 오류", e);
        }
    }

    /** 예외처리 함수 */
    private RuntimeException wrap(String msg, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return new RuntimeException(msg + ": " + root.getClass().getName() + " - " + root.getMessage(), e);
    }

//    public List<VendorExcelRow> parse(MultipartFile file) {
    private List<VendorExcelRow> parseWorkbook(Workbook workbook) {
        List<VendorExcelRow> result = new ArrayList<>();

        Sheet sheet = workbook.getSheetAt(0); // A사 시트 0번이라고 가정

        // ===== 현재 상태값 =====
        String currentLargeCategory = null; // B열: 대분류(섹션 타이틀, 없을 때도 있음)
        String currentSmallCategory = null; // C열: 소분류(예: 비데일체형양변기, 탱크리스양변기)
        String currentSetName = null;       // C열: 세트명(예: 유로젠(단종))

        // 세트(현재 소분류+세트명) 안에서 대표 코드(SET 합계 행 생성 시 코드 힌트로 사용)
        String primaryOldCode = null;  // 첫 ITEM의 구품번(E열)
        String primaryNewCode = null;  // 첫 ITEM의 모델명/신품번(F열)

        // 상단 타이틀/공백/헤더가 섞여있을 수 있으므로 0행부터 돌되, 헤더는 감지하여 스킵
        int firstDataRow = 0;

        for (int rowIdx = firstDataRow; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            // ===== 컬럼 매핑 (샘플 기준) =====
            // B: 대분류 타이틀(섹션명)
            // C: 소분류/세트명 (행 단위로 나뉘어 존재)
            // D: 제품명(ITEM)
            // E: 구품번
            // F: 모델명(신품번)
            // G: 단가/합계금액
            String colB = getStringCell(row, 1);
            String colC = getStringCell(row, 2);
            String colD = getStringCell(row, 3);
            String colE = getStringCell(row, 4);
            String colF = getStringCell(row, 5);
            BigDecimal colG = getNumericCell(row, 6);

            // 0) 완전 빈 줄이면 스킵
            if (isBlank(colB) && isBlank(colC) && isBlank(colD)
                    && isBlank(colE) && isBlank(colF) && colG == null) {
                continue;
            }

            // 1) 헤더 줄 스킵
            if (isHeaderRow(colB, colC, colD, colE, colF)) {
                continue;
            }

            // 2) "대분류 타이틀 행" 판별 (B에만 값 있고 나머지 비어있음)
            boolean isLargeCategoryRow =
                    !isBlank(colB) &&
                            isBlank(colC) &&
                            isBlank(colD) &&
                            isBlank(colE) &&
                            isBlank(colF) &&
                            colG == null;

            if (isLargeCategoryRow) {
                currentLargeCategory = colB.trim();

                // 새 대분류 시작 시 소분류/세트/대표코드 초기화
                currentSmallCategory = null;
                currentSetName = null;
                primaryOldCode = null;
                primaryNewCode = null;
                continue;
            }

            // 3) C열 처리: "소분류 행" vs "세트명 행" 구분
            //    - 소분류: 대분류 추론이 가능해야 함 (예: 비데일체형양변기 -> 비데)
            //    - 세트명: 추론 불가한 단순 이름 (예: 유로젠(단종))
            if (!isBlank(colC)) {
                String cRaw = colC.trim();
                String cNorm = normalizeNoSpace(cRaw);

                String inferredLarge = inferLargeCategoryFromSmallCategory(cNorm);

                if (!isBlank(inferredLarge)) {
                    // ✅ 소분류 행
                    currentSmallCategory = cNorm;       // 소분류는 공백 제거 형태로 저장(원하면 유지)
                    currentLargeCategory = inferredLarge; // 대분류 누락 구간 보정
                    currentSetName = null;                // 소분류가 바뀌면 보통 세트명이 새로 등장
                    primaryOldCode = null;
                    primaryNewCode = null;
                } else {
                    // ✅ 세트명 행
                    currentSetName = cRaw;  // 세트명은 원문 유지(괄호/띄어쓰기 포함)
                    primaryOldCode = null;
                    primaryNewCode = null;
                }

                // C열 행은 라벨/제목 성격이므로 다음 행으로
                continue;
            }

            // 4) ITEM 행 판별: 제품명(D) + 모델명(F)이 있으면 구성품(ITEM)으로 저장
            boolean isItemRow = !isBlank(colD) && !isBlank(colF);
            if (isItemRow) {
                // 분류/세트명이 잡혀있어야 정확히 매핑 가능
                if (isBlank(currentSmallCategory) || isBlank(currentSetName)) {
                    logger.warn("[VendorA] ITEM row but smallCategory/setName is null. row={}, D={}, F={}, small={}, set={}",
                            rowIdx, colD, colF, currentSmallCategory, currentSetName);
                    continue;
                }

                String safeLargeCategory = !isBlank(currentLargeCategory)
                        ? currentLargeCategory
                        : inferLargeCategoryFromSmallCategory(currentSmallCategory);

                if (isBlank(safeLargeCategory)) {
                    logger.warn("[VendorA] ITEM row but largeCategory is null. row={}, small={}", rowIdx, currentSmallCategory);
                    continue;
                }

                // ✅ ITEM DTO 생성
                // - 대분류: safeLargeCategory
                // - 소분류: currentSmallCategory
                // - 제품명: D열
                // - 모델명: F열
                VendorExcelRow itemDto = new VendorExcelRow(
                        "A",                   // vendorCode
                        safeLargeCategory,      // categoryLarge (대분류)
                        currentSmallCategory,   // categorySmall (소분류)
                        colD.trim(),            // ✅ productName (제품명)
                        colF.trim(),            // masterCodeHint (모델명)
                        colF.trim(),            // proposalItemCode
                        colF.trim(),            // mainItemCode
                        null,                   // subItemCode
                        isBlank(colE) ? null : colE.trim(), // oldItemCode
                        colD.trim(),            // vendorItemName
                        null,                   // vendorSpec
                        null,                   // remark
                        colG,                   // unitPrice (단가, 있을 수도/없을 수도)
                        "ITEM"                  // priceType
                );
                result.add(itemDto);

                // SET 대표코드(첫 ITEM 코드) 잡아두기
                if (isBlank(primaryOldCode) && !isBlank(colE)) primaryOldCode = colE.trim();
                if (isBlank(primaryNewCode)) primaryNewCode = colF.trim();

                continue;
            }

            // 5) 세트 합계 행 판별 (D/E/F 비어 있고 G에만 값이 있는 행)
            boolean isSetTotalRow =
                    isBlank(colD) &&
                            isBlank(colE) &&
                            isBlank(colF) &&
                            colG != null;

            if (isSetTotalRow) {
                // SET 생성에 필요한 값 체크
                if (isBlank(currentSmallCategory) || isBlank(currentSetName)) {
                    logger.warn("[VendorA] SET total row but smallCategory/setName is null. row={}, small={}, set={}",
                            rowIdx, currentSmallCategory, currentSetName);
                    continue;
                }

                String safeLargeCategory = !isBlank(currentLargeCategory)
                        ? currentLargeCategory
                        : inferLargeCategoryFromSmallCategory(currentSmallCategory);

                if (isBlank(safeLargeCategory)) {
                    logger.warn("[VendorA] skip SET row because largeCategory is null. row={}, smallCategory={}",
                            rowIdx, currentSmallCategory);
                    continue;
                }

                String newCode = isBlank(primaryNewCode) ? null : primaryNewCode;
                String oldCode = isBlank(primaryOldCode) ? null : primaryOldCode;

                // ✅ SET DTO 생성
                // - productName에는 "세트명" 저장 (요구사항)
                VendorExcelRow setDto = new VendorExcelRow(
                        "A",                   // vendorCode
                        safeLargeCategory,      // categoryLarge (대분류)
                        currentSmallCategory,   // categorySmall (소분류)
                        currentSetName,         // ✅ productName (세트명)
                        newCode,                // masterCodeHint (세트 대표 코드 힌트)
                        newCode,                // proposalItemCode
                        newCode,                // mainItemCode
                        null,                   // subItemCode
                        oldCode,                // oldItemCode
                        currentSetName,         // vendorItemName (세트명)
                        null,                   // vendorSpec
                        null,                   // remark
                        colG,                   // unitPrice (세트 합계금액)
                        "SET"                   // priceType
                );
                result.add(setDto);

                // 세트 종료 → 다음 세트를 위해 대표코드 초기화
                primaryOldCode = null;
                primaryNewCode = null;

                continue;
            }

            // 그 외 행은 현재 포맷에서 의미 없는 행일 가능성이 높아 스킵
        }

        return result;
    }

    /**
     * C열(소분류 후보) 텍스트를 기반으로 대분류를 추론한다.
     * - 반환값이 null이 아니면 "이 C열 값은 소분류"로 판정 가능
     * - 반환값이 null이면 "세트명(단순 이름)"으로 판정
     */
    private String inferLargeCategoryFromSmallCategory(String smallCategory) {
        if (isBlank(smallCategory)) return null;

        String t = smallCategory.replaceAll("\\s+", ""); // 공백 제거

        if (t.equals("비데일체형양변기")) return "양변기";
        if (t.contains("비데")) return "비데";
        if (t.contains("양변기") || t.contains("변기")) return "양변기";

        if (t.contains("세면기") || t.contains("세면대")) return "세면기";
        if (t.contains("욕조") || t.contains("배스")) return "욕조";

        if (t.contains("세탁")) return "세탁수전";
        if (t.contains("주방") || t.contains("싱크") || t.contains("씽크")) return "주방수전";
        if (t.contains("샤워") || t.contains("레인샤워") || t.contains("해바라기")) return "샤워수전";

        if (t.contains("세면") && t.contains("수전")) return "세면수전";

        if (t.contains("액세서리") || t.contains("휴지걸이") || t.contains("수건걸이")
                || t.contains("거울") || t.contains("선반")) return "액세서리";

        return null;
    }

    private String normalizeNoSpace(String s) {
        if (isBlank(s)) return null;
        return s.replaceAll("\\s+", "").trim();
    }

    private boolean isHeaderRow(String colB, String colC, String colD, String colE, String colF) {
        if ("제품명".equals(colB) || "제품명".equals(colD)) return true;
        if ("구품번".equals(colE) || (colE != null && colE.contains("구품번"))) return true;
        if ("신품번".equals(colF) || (colF != null && colF.contains("신품번"))) return true;
        return false;
    }

    // ====== 공통 유틸 메서드들 ======

    private String getStringCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            String v = cell.getStringCellValue();
            return v == null ? null : v.trim();
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
