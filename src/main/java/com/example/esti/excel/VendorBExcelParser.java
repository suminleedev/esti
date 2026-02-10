package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * B사 단가표 엑셀 파서 (시트별 헤더 흔들림 + 중간 헤더 스킵 + 코드 50자 방어 + 단가 파싱 강화)
 *
 * ** 반영된 개선사항(로그 결과 기준)
 * 1) 시트마다 '단가/계/합계/금액/가격' 헤더명이 달라도 TOTAL_PRICE로 최대한 인식하도록 alias 확장
 * 2) '計/계' 단독 헤더도 TOTAL_PRICE로 인정 (기존 조건이 너무 빡세서 전부 0원 되는 문제 방지)
 * 3) 단가 셀 값이 "1,000원", "1234(VAT별도)", " - " 처럼 문자열이 섞여도 숫자만 추출해 파싱
 * 4) 중간에 반복 헤더/구분 행이 데이터로 들어오는 문제 스킵
 * 5) 품번(코드) 컬럼은 괄호 설명 제거 + 50자 컷으로 VARCHAR(50) truncation 방지
 * 6) 단가 없으면 0원 정책 유지
 */
@Component
@RequiredArgsConstructor
public class VendorBExcelParser implements VendorExcelParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(VendorBExcelParser.class);

    // 내부 표준 컬럼 키
    private static final String COL_CATEGORY_SMALL = "CATEGORY_SMALL"; // 품종/품목/소분류
    private static final String COL_PRODUCT_CODE   = "PRODUCT_CODE";   // 품번(코드)
    private static final String COL_SUB_CODE       = "SUB_CODE";       // KS 품번 등
    private static final String COL_TOTAL_PRICE    = "TOTAL_PRICE";    // 計/계/합계/총계/금액/단가/가격 등
    private static final String COL_REMARK         = "REMARK";         // 비고/규격/특징

    // 헤더 탐색 범위(상단 몇 행까지 헤더 후보로 볼지)
    private static final int HEADER_SCAN_MAX_ROWS = 40;

    // 코드(품번) 컬럼 DB 길이(@Column length=50)
    private static final int CODE_MAX_LEN = 50;

    @Override
    public String getVendorCode() {
        return "B";
    }

    /** 기존 parse 함수 -> InputStream 오버로드함수로 위임 */
    @Override
    public List<VendorExcelRow> parse(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return parse(is); // InputStream 버전으로 위임
        } catch (Exception e) {
            throw wrap("B사 엑셀 파싱 중 오류", e);
        }
    }

    /** 비동기/임시파일/테스트에서 재사용 가능한 InputStream 버전 */
    public List<VendorExcelRow> parse(InputStream is) {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            return parseWorkbook(workbook); // 실제 파싱 로직
        } catch (Exception e) {
            throw wrap("B사 엑셀 파싱 중 오류", e);
        }
    }

    /** (선택) Path로도 바로 받을 수 있게 */
    public List<VendorExcelRow> parse(java.nio.file.Path path) {
        try (InputStream is = java.nio.file.Files.newInputStream(path)) {
            return parse(is);
        } catch (Exception e) {
            throw wrap("B사 엑셀 파싱 중 오류", e);
        }
    }

    /** 예외처리 함수 */
    private RuntimeException wrap(String msg, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return new RuntimeException(msg + ": " + root.getClass().getName() + " - " + root.getMessage(), e);
    }

    /** 기존 parse() 안의 대부분 로직을 여기로 그대로 옮기면 됨 */
//    private List<VendorExcelRow> parseWorkbook(Workbook workbook) {
//        List<VendorExcelRow> result = new ArrayList<>();
//
//        DataFormatter formatter = new DataFormatter();
//        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
//
//        // 디버그용(원하면 false로)
//        boolean debug = true;
//
//        for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
//            Sheet sheet = workbook.getSheetAt(sheetIdx);
//            if (sheet == null) continue;
//
//            String categoryLarge = sheet.getSheetName();
//
//            // 1) 헤더 자동 탐지 + 컬럼맵 생성(상/하위 헤더 2줄을 합쳐서 인식)
//            HeaderInfo headerInfo = detectHeader(sheet, formatter, evaluator);
//            if (headerInfo == null) continue;
//
//            Map<String, Integer> colMap = headerInfo.colMap;
//            int headerRowIdx = headerInfo.headerRowIdx;
//
//            if (debug) {
//                System.out.println("[B][COLMAP] sheet=" + categoryLarge +
//                        " headerRow=" + headerRowIdx +
//                        " keys=" + colMap.keySet());
//            }
//
//            // 2) 데이터 시작 행: 기본은 헤더 다음 행
//            int dataStartRowIdx = headerRowIdx + 1;
//
//            // 2-1) 헤더 바로 아래가 하위 헤더(하부/상부...) 행이면 1줄 더 스킵
//            if (looksLikeSubHeaderRow(sheet.getRow(dataStartRowIdx), formatter, evaluator)) {
//                dataStartRowIdx += 1;
//            }
//
//            int zeroPriceRows = 0;
//            int parsedRows = 0;
//
//            // 3) 데이터 순회
//            int lastRowNum = sheet.getLastRowNum();
//            for (int rowIdx = dataStartRowIdx; rowIdx <= lastRowNum; rowIdx++) {
//                Row row = sheet.getRow(rowIdx);
//                if (row == null) continue;
//
//                // 3-1) 원문 추출
//                String categorySmall = normalizeSpace(getByKey(row, colMap, COL_CATEGORY_SMALL, formatter, evaluator));
//                String productCodeRaw = getByKey(row, colMap, COL_PRODUCT_CODE, formatter, evaluator);
//                String subCodeRaw     = getByKey(row, colMap, COL_SUB_CODE, formatter, evaluator);
//
//                // 3-2) 코드값 정리(공백정리 + 괄호설명 제거 + 50자 컷)
//                String productCode = normalizeCode(productCodeRaw, CODE_MAX_LEN);
//                String subCode     = normalizeCode(subCodeRaw, CODE_MAX_LEN);
//
//                // 3-3) 중간 반복 헤더/구분행 스킵
//                if (isHeaderLikeCode(productCodeRaw) || isHeaderLikeCode(productCode)) {
//                    continue;
//                }
//
//                // 3-4) 품번 없으면 스킵
//                if (isBlank(productCode)) continue;
//
//                // 3-5) 비고/단가
//                String remark = normalizeSpace(getByKey(row, colMap, COL_REMARK, formatter, evaluator));
//                BigDecimal totalPrice = getDecimalByKey(row, colMap, COL_TOTAL_PRICE, formatter, evaluator);
//
//                // 3-6) 단가 정책: 없으면 0원
//                if (totalPrice == null) {
//                    totalPrice = BigDecimal.ZERO;
//                    zeroPriceRows++;
//
//                    // (선택) 파싱 실패 흔적 남기고 싶으면 사용
//                    // remark = (remark == null ? "" : remark + " | ") + "단가누락(0원)";
//                }
//
//                parsedRows++;
//
//                // 3-7) 제품명 없으면 null 방지용으로 조합
//                String productName = safeProductName(categoryLarge, categorySmall, productCode);
//
//                VendorExcelRow dto = new VendorExcelRow(
//                        "B",
//                        categoryLarge,     // 대분류(시트명)
//                        categorySmall,     // 소분류
//                        productName,       // 제품명(없으면 조합)
//                        productCode,       // masterCodeHint
//                        productCode,       // proposalItemCode (VARCHAR(50) 안전)
//                        productCode,       // mainItemCode
//                        subCode,           // subItemCode
//                        null,              // oldItemCode
//                        productName,       // vendorItemName (없으면 제품명 대체)
//                        remark,            // vendorSpec (B사 파일에선 비고/규격이 섞이는 경우가 많아 remark 재사용)
//                        remark,            // remark
//                        totalPrice,        // unitPrice
//                        "SET"
//                );
//
//                result.add(dto);
//            }
//
//            if (debug) {
//                System.out.println("[B][SHEET] " + categoryLarge +
//                        " parsedRows=" + parsedRows +
//                        " zeroPriceRows=" + zeroPriceRows);
//            }
//        }
//
//        return result;
//    }

    private List<VendorExcelRow> parseWorkbook(Workbook workbook) {
        List<VendorExcelRow> result = new ArrayList<>();

        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
            Sheet sheet = workbook.getSheetAt(sheetIdx);
            if (sheet == null) continue;

            String sheetName = sheet.getSheetName();

            // ⭐️ 양변기 시트만 전용 파서로
            if ("양변기".equals(sheetName)) {
                result.addAll(parseToiletSheet(sheet, formatter, evaluator));
                continue;
            }

            // 기존 로직 그대로
            result.addAll(parseDefaultSheet(sheet, formatter, evaluator));
        }

        return result;
    }


    private List<VendorExcelRow> parseToiletSheet(
            Sheet sheet,
            DataFormatter f,
            FormulaEvaluator e
    ) {
        List<VendorExcelRow> out = new ArrayList<>();

        int headerRow = findHeaderRow(sheet, f, e);
        if (headerRow < 0) return out;

        int rowIdx = headerRow + 2;
        int lastRow = sheet.getLastRowNum();

        String lastCategorySmall = null;

        while (rowIdx <= lastRow) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) { rowIdx++; continue; }

            // 🔹 중간 헤더 스킵
            if (isHeaderRowLike(row, f, e)) {
                rowIdx++;
                continue;
            }

            String categoryLarge = sheet.getSheetName();
            String categorySmall = normalizeSpace(getCellString(row, 1, f, e));
            if (!isBlank(categorySmall)) lastCategorySmall = categorySmall;
            else categorySmall = lastCategorySmall;

            String productCodeRaw = getCellString(row, 2, f, e);
            String productCode = normalizeCode(productCodeRaw, 50);
            if (isBlank(productCode)) { rowIdx++; continue; }

            String ksCode = normalizeCode(getCellString(row, 4, f, e), 50);
            String remark = normalizeSpace(getCellString(row, 16, f, e));

            // 🔥 단가 찾기 (다음 1~3행)
            BigDecimal unitPrice = findDealerPrice(sheet, rowIdx, lastRow, f, e);
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;

            String productName = safeProductName(categoryLarge, categorySmall, productCode);

            out.add(new VendorExcelRow(
                    "B",
                    categoryLarge,
                    categorySmall,
                    productName,
                    productCode,
                    productCode,
                    productCode,
                    ksCode,
                    null,
                    productName,
                    remark,
                    remark,
                    unitPrice,
                    "SET"
            ));

            rowIdx += 2; // 제품행 + 가격행 소비
        }

        return out;
    }


    private BigDecimal findDealerPrice(
            Sheet sheet,
            int productRowIdx,
            int lastRow,
            DataFormatter f,
            FormulaEvaluator e
    ) {
        for (int i = 1; i <= 3; i++) {
            int idx = productRowIdx + i;
            if (idx > lastRow) break;

            Row r = sheet.getRow(idx);
            if (r == null) continue;

            if (isHeaderRowLike(r, f, e)) continue;

            String label = normalizeSpace(getCellString(r, 5, f, e));
            if (label != null && label.replace(" ", "").contains("대리점가")) {
                return getDecimalCell(r, 13, f, e); // N열 計
            }

            // 다음 제품행 나오면 중단
            String nextCode = normalizeCode(getCellString(r, 2, f, e), 50);
            if (!isBlank(nextCode)) break;
        }
        return null;
    }


    private List<VendorExcelRow> parseDefaultSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator
            //,boolean debug
    ) {
        List<VendorExcelRow> result = new ArrayList<>();

        String categoryLarge = sheet.getSheetName();

        HeaderInfo headerInfo = detectHeader(sheet, formatter, evaluator);
        if (headerInfo == null) return result;

        Map<String, Integer> colMap = headerInfo.colMap;
        int headerRowIdx = headerInfo.headerRowIdx;

//        if (debug) {

//        }
        LOGGER.debug("[B][COLMAP] sheet=" + categoryLarge +
                " headerRow=" + headerRowIdx +
                " keys=" + colMap.keySet());

        int dataStartRowIdx = headerRowIdx + 1;
        if (looksLikeSubHeaderRow(sheet.getRow(dataStartRowIdx), formatter, evaluator)) {
            dataStartRowIdx += 1;
        }

        int lastRowNum = sheet.getLastRowNum();
        for (int rowIdx = dataStartRowIdx; rowIdx <= lastRowNum; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            String categorySmall = normalizeSpace(getByKey(row, colMap, COL_CATEGORY_SMALL, formatter, evaluator));
            String productCodeRaw = getByKey(row, colMap, COL_PRODUCT_CODE, formatter, evaluator);
            String subCodeRaw     = getByKey(row, colMap, COL_SUB_CODE, formatter, evaluator);

            String productCode = normalizeCode(productCodeRaw, CODE_MAX_LEN);
            String subCode     = normalizeCode(subCodeRaw, CODE_MAX_LEN);

            if (isHeaderLikeCode(productCodeRaw) || isHeaderLikeCode(productCode)) continue;
            if (isBlank(productCode)) continue;

            String remark = normalizeSpace(getByKey(row, colMap, COL_REMARK, formatter, evaluator));
            BigDecimal totalPrice = getDecimalByKey(row, colMap, COL_TOTAL_PRICE, formatter, evaluator);
            if (totalPrice == null) totalPrice = BigDecimal.ZERO;

            String productName = safeProductName(categoryLarge, categorySmall, productCode);

            result.add(new VendorExcelRow(
                    "B",
                    categoryLarge,
                    categorySmall,
                    productName,
                    productCode,
                    productCode,
                    productCode,
                    subCode,
                    null,
                    productName,
                    remark,
                    remark,
                    totalPrice,
                    "SET"
            ));
        }

        return result;
    }


    private List<VendorExcelRow> parseToiletSheet(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            boolean debug
    ) {
        List<VendorExcelRow> result = new ArrayList<>();

        // 양변기 시트는 구조가 고정이라 “헤더 detectHeader” 대신,
        // "구분/품종/품번"이 있는 행을 찾아 시작점을 잡는 방식이 안전함
        int headerRowIdx = findHeaderRow(sheet, formatter, evaluator);
        if (headerRowIdx < 0) return result;

        int rowIdx = headerRowIdx + 2; // 헤더 + (하부/상부 같은 하위헤더) 1줄 스킵
        int lastRowNum = sheet.getLastRowNum();

        String categoryLarge = sheet.getSheetName();
        String lastCategorySmall = null;

        while (rowIdx <= lastRowNum) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) { rowIdx++; continue; }

            if (isHeaderRowLike(row, formatter, evaluator)) { // 중간 헤더 스킵
                rowIdx++;
                continue;
            }

            // 고정 컬럼 (0-based): B=품종(1), C=품번(2), E=KS품번(4), F=라벨(5), N=計(13), Q=비고(16)
            String categorySmall = normalizeSpace(cellText(row, 1, formatter, evaluator));
            if (!isBlank(categorySmall)) lastCategorySmall = categorySmall;
            else categorySmall = lastCategorySmall;

            String productCodeRaw = cellText(row, 2, formatter, evaluator);
            String productCode = normalizeCode(productCodeRaw, CODE_MAX_LEN);
            if (isBlank(productCode)) { rowIdx++; continue; }

            String ksCode = normalizeCode(cellText(row, 4, formatter, evaluator), CODE_MAX_LEN);
            String remark = normalizeSpace(cellText(row, 16, formatter, evaluator));

            // ✅ 단가: 다음 1~3행 중 F열이 "대리점가"인 행의 N열(計)을 읽기
            PricePick pick = findDealerPriceRow(sheet, rowIdx, lastRowNum, formatter, evaluator);
            BigDecimal totalPrice = null;
            if (pick.priceRow != null) {
                totalPrice = parseDecimal(cellText(pick.priceRow, 13, formatter, evaluator)); // N열 計
            }
            if (totalPrice == null) totalPrice = BigDecimal.ZERO;

            String productName = safeProductName(categoryLarge, categorySmall, productCode);

            result.add(new VendorExcelRow(
                    "B",
                    categoryLarge,
                    categorySmall,
                    productName,
                    productCode,
                    productCode,
                    productCode,
                    ksCode,     // 여기서는 KS 품번을 subItemCode에 넣었음(원하면 바꿔줘)
                    null,
                    productName,
                    remark,
                    remark,
                    totalPrice,
                    "SET"
            ));

            // 가격행까지 소비했으면 그 다음부터
            rowIdx = (pick.consumedUntilRowIdx != null) ? (pick.consumedUntilRowIdx + 1) : (rowIdx + 1);
        }

        if (debug) {
            System.out.println("[B][TOILET] sheet=" + categoryLarge + " rows=" + result.size());
        }

        return result;
    }



    private int findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int max = Math.min(sheet.getLastRowNum(), 80);

        for (int r = 0; r <= max; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String a = normalizeSpace(cellText(row, 0, formatter, evaluator)); // A:구분
            String b = normalizeSpace(cellText(row, 1, formatter, evaluator)); // B:품종
            String c = normalizeSpace(cellText(row, 2, formatter, evaluator)); // C:품번

            if ("구분".equals(a) && "품종".equals(b) && "품번".equals(c)) {
                return r;
            }
        }
        return -1;
    }

    private boolean isHeaderRowLike(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        String a = normalizeSpace(cellText(row, 0, formatter, evaluator));
        String b = normalizeSpace(cellText(row, 1, formatter, evaluator));
        String c = normalizeSpace(cellText(row, 2, formatter, evaluator));
        return "구분".equals(a) && "품종".equals(b) && "품번".equals(c);
    }

    private static class PricePick {
        Row priceRow;
        Integer consumedUntilRowIdx;
    }

    private PricePick findDealerPriceRow(
            Sheet sheet,
            int productRowIdx,
            int lastRowNum,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        PricePick pick = new PricePick();

        for (int i = 1; i <= 3; i++) {
            int idx = productRowIdx + i;
            if (idx > lastRowNum) break;

            Row r = sheet.getRow(idx);
            if (r == null) continue;

            if (isHeaderRowLike(r, formatter, evaluator)) continue;

            String label = normalizeSpace(cellText(r, 5, formatter, evaluator)); // F열
            if (label != null && label.replace(" ", "").contains("대리점가")) {
                pick.priceRow = r;
                pick.consumedUntilRowIdx = idx;
                return pick;
            }

            // 다음 제품행(품번)이 나오면 탐색 중단
            String maybeNextProduct = normalizeCode(cellText(r, 2, formatter, evaluator), CODE_MAX_LEN);
            if (!isBlank(maybeNextProduct)) break;
        }

        return pick;
    }

    private BigDecimal parseDecimal(String txt) {
        if (isBlank(txt)) return null;

        String cleaned = txt.replace("\u00A0", " ")
                .replace(",", "")
                .replace("₩", "")
                .replace("원", "")
                .trim();

        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getCellString(
            Row row,
            int colIdx,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        if (row == null) return null;

        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        // DataFormatter + FormulaEvaluator 조합이 핵심
        // → 수식 셀도 계산된 결과를 문자열로 반환
        String value = formatter.formatCellValue(cell, evaluator);

        if (value == null) return null;

        value = value.replace('\u00A0', ' ').trim(); // NBSP 제거
        return value.isEmpty() ? null : value;
    }

    private BigDecimal getDecimalCell(
            Row row,
            int colIdx,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        String txt = getCellString(row, colIdx, formatter, evaluator);
        if (txt == null) return null;

        // 통화/문자 제거
        String cleaned = txt
                .replace(",", "")
                .replace("₩", "")
                .replace("원", "")
                .trim();

        // 숫자 / 소수점 / 음수만 남김
        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || "-".equals(cleaned)) return null;

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }



    // ============================================================
    // 헤더 탐지 & 표준 컬럼맵 생성
    // ============================================================

    private static class HeaderInfo {
        final int headerRowIdx;
        final Map<String, Integer> colMap;

        HeaderInfo(int headerRowIdx, Map<String, Integer> colMap) {
            this.headerRowIdx = headerRowIdx;
            this.colMap = colMap;
        }
    }

    /**
     * 시트 상단을 훑어 "헤더 행"을 찾는다.
     * - r행(상위헤더) + r+1행(하위헤더)을 같은 컬럼 index 기준으로 합쳐서 헤더 텍스트 구성
     * - 품번 컬럼은 반드시 있어야 하고, 단가/비고 중 하나 이상 있으면 헤더로 인정
     */
    private HeaderInfo detectHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int max = Math.min(sheet.getLastRowNum(), HEADER_SCAN_MAX_ROWS);

        for (int r = 0; r <= max; r++) {
            Row h1 = sheet.getRow(r);
            if (h1 == null) continue;

            Row h2 = (r + 1 <= sheet.getLastRowNum()) ? sheet.getRow(r + 1) : null;

            Map<String, Integer> colMap = buildStandardColMap(h1, h2, formatter, evaluator);

            boolean hasProductCode = colMap.containsKey(COL_PRODUCT_CODE);
            boolean hasTotalOrRemark = colMap.containsKey(COL_TOTAL_PRICE) || colMap.containsKey(COL_REMARK);

            if (hasProductCode && hasTotalOrRemark) {
                return new HeaderInfo(r, colMap);
            }
        }

        return null;
    }

    /**
     * 상위/하위 헤더 2줄을 합쳐 표준 컬럼키 -> 컬럼 인덱스로 매핑한다.
     */
    private Map<String, Integer> buildStandardColMap(Row headerRow, Row subHeaderRow,
                                                     DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> map = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();

        for (int c = 0; c < lastCellNum; c++) {
            String t1 = cellText(headerRow, c, formatter, evaluator);
            String t2 = (subHeaderRow == null) ? null : cellText(subHeaderRow, c, formatter, evaluator);

            // 상/하위 헤더 합치기
            String combined = normalizeHeader((t1 == null ? "" : t1) + " " + (t2 == null ? "" : t2));
            if (isBlank(combined)) continue;

            String key = toStandardKey(combined);
            if (key != null) {
                map.putIfAbsent(key, c);
            }
        }

        return map;
    }

    private String cellText(Row row, int col, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String v = formatter.formatCellValue(cell, evaluator);
        v = (v == null) ? null : v.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    /**
     * 헤더 문자열 정규화:
     * - NBSP 제거 + 다중공백 제거
     * - 괄호 안 단위 제거: "계(원)" -> "계"
     */
    private String normalizeHeader(String s) {
        if (s == null) return null;

        String x = s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (x.isEmpty()) return null;

        x = x.replaceAll("\\(.*?\\)", "").trim();
        x = x.replaceAll("\\s+", " ").trim();

        return x.isEmpty() ? null : x;
    }

    /**
     * 헤더(정규화된 문자열)를 표준 컬럼키로 매핑한다.
     *
     * ** 단가 컬럼(TOTAL_PRICE) 매핑을 넓게 잡는 것이 핵심:
     * - "計" 단독/ "계" 단독도 TOTAL_PRICE로 인정
     * - 합계/총계/금액/단가/가격/원/₩/공급가/판매가 등 다양한 표현을 TOTAL_PRICE로 흡수
     */
    private String toStandardKey(String header) {
        if (header == null) return null;

        String h = header;
        String hs = header.replace(" ", "");
        String lower = header.toLowerCase(Locale.ROOT);

        // ===== 품번/제품코드 =====
        if (equalsAny(h, "품번", "제품품번", "제품 품번", "제안서 품번", "품번(제안서)", "품번(제안)", "품번(제품)") ||
                equalsAny(hs, "품번", "제품품번", "제안서품번")) {
            return COL_PRODUCT_CODE;
        }

        // ===== 품종/소분류 =====
        if (equalsAny(h, "품종", "품목", "소분류", "품목소분류", "품목 소분류") ||
                equalsAny(hs, "품종", "품목", "소분류", "품목소분류")) {
            return COL_CATEGORY_SMALL;
        }

        // ===== KS 품번 =====
        if (containsAny(h, "KS") && containsAny(h, "품번", "코드")) {
            return COL_SUB_CODE;
        }
        if (equalsAny(h, "KS 품번", "KS품번", "KS 코드", "KS코드")) {
            return COL_SUB_CODE;
        }

        // ===== 단가(합계) =====
        // 1) "計" / "계" 단독은 무조건 TOTAL_PRICE
        if (hs.equalsIgnoreCase("計") || hs.equalsIgnoreCase("계")) {
            return COL_TOTAL_PRICE;
        }

        // 2) 합계/총계/총액/금액/단가/가격/원/₩ 등 폭넓게 흡수
        boolean hasTotalLike = containsAny(h, "計", "계", "합계", "총계", "총액", "합산");
        boolean hasMoneyLike = containsAny(h, "단가", "금액", "가격", "원", "₩", "KRW", "PRICE", "공급가", "판매가");

        if ((hasTotalLike || hasMoneyLike) && !containsAny(h, "비고", "규격", "특징", "설명")) {
            return COL_TOTAL_PRICE;
        }

        // ===== 비고/규격 =====
        if (containsAny(lower, "비고", "규격", "특징", "설명", "remark")) {
            return COL_REMARK;
        }

        return null;
    }

    private boolean equalsAny(String target, String... candidates) {
        if (target == null) return false;
        for (String c : candidates) {
            if (c == null) continue;
            if (target.equalsIgnoreCase(c)) return true;
        }
        return false;
    }

    private boolean containsAny(String target, String... tokens) {
        if (target == null) return false;
        String t = target.replace(" ", "").toLowerCase(Locale.ROOT);
        for (String k : tokens) {
            if (k == null) continue;
            String kk = k.replace(" ", "").toLowerCase(Locale.ROOT);
            if (!kk.isEmpty() && t.contains(kk)) return true;
        }
        return false;
    }

    /**
     * 헤더 바로 아래가 하위 헤더인지 감지(옵션)
     */
    private boolean looksLikeSubHeaderRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) return false;

        Set<String> tokens = Set.of("하부", "상부", "상", "하", "세부", "구분");
        int hit = 0;

        short last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;

            String v = formatter.formatCellValue(cell, evaluator);
            v = (v == null) ? null : v.trim();
            if (isBlank(v)) continue;

            String vn = v.replace(" ", "");
            if (tokens.contains(v) || tokens.contains(vn)) hit++;
        }

        return hit >= 2;
    }

    // ============================================================
    // 데이터 추출
    // ============================================================

    private String getByKey(Row row, Map<String, Integer> colMap, String key,
                            DataFormatter formatter, FormulaEvaluator evaluator) {
        Integer idx = colMap.get(key);
        if (idx == null) return null;

        Cell cell = row.getCell(idx);
        if (cell == null) return null;

        String v = formatter.formatCellValue(cell, evaluator);
        v = (v == null) ? null : v.trim();
        return isBlank(v) ? null : v;
    }

    /**
     * ** 단가 파싱 강화 버전
     * - "1,234원", "1234(VAT별도)", "₩12,345" 등에서도 숫자만 추출해서 파싱
     * - '-', 빈값, "별도문의" 같은 케이스는 null 반환
     */
    private BigDecimal getDecimalByKey(Row row, Map<String, Integer> colMap, String key,
                                       DataFormatter formatter, FormulaEvaluator evaluator) {
        String txt = getByKey(row, colMap, key, formatter, evaluator);
        if (isBlank(txt)) return null;

        String cleaned = txt.replace("\u00A0", " ")
                .replace(",", "")
                .replace("₩", "")
                .replace("원", "")
                .trim();

        // 숫자/부호/소수점만 남기기
        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");

        if (cleaned.isEmpty() || cleaned.equals("-")) return null;

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============================================================
    // 문자열 정리/검증
    // ============================================================

    private String normalizeSpace(String s) {
        if (s == null) return null;
        String x = s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return x.isEmpty() ? null : x;
    }

    /**
     * 코드(품번) 정리:
     * - 공백 정리
     * - 괄호 설명 제거: "U9420B (1구...)" -> "U9420B"
     * - 50자 컷
     */
    private String normalizeCode(String s, int maxLen) {
        String x = normalizeSpace(s);
        if (x == null) return null;

        int p = x.indexOf('(');
        if (p > 0) x = x.substring(0, p).trim();

        if (x.length() > maxLen) x = x.substring(0, maxLen);

        return x.isEmpty() ? null : x;
    }

    /**
     * 중간 반복 헤더/구분행 판별:
     * - 품번 칸이 "품번/구분/코드/제품코드/품목/품종" 같은 단어면 헤더로 간주
     * - 지나치게 길면(설명/문장) 헤더/소제목으로 간주
     */
    private boolean isHeaderLikeCode(String raw) {
        if (raw == null) return false;

        String x = normalizeSpace(raw);
        if (x == null) return true;

        String xs = x.replace(" ", "");
        if (xs.equalsIgnoreCase("품번") ||
                xs.equalsIgnoreCase("구분") ||
                xs.equalsIgnoreCase("코드") ||
                xs.equalsIgnoreCase("제품코드") ||
                xs.equalsIgnoreCase("품목") ||
                xs.equalsIgnoreCase("품종")) {
            return true;
        }

        if (x.length() > CODE_MAX_LEN) return true;

        return false;
    }

    private String safeProductName(String categoryLarge, String categorySmall, String productCode) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(categoryLarge)) sb.append(categoryLarge);
        if (!isBlank(categorySmall)) sb.append(" ").append(categorySmall);
        if (!isBlank(productCode)) sb.append(" ").append(productCode);

        String s = sb.toString().trim();
        return s.isEmpty() ? productCode : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
