package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A사(아메리칸스탠다드) 단가표 파서 — 단일 시트.
 *
 * <p>A·B열 제외(D11): C(2)=소분류/세트명, D(3)=구성품명, E(4)=구품번, F(5)=신품번, G(6)=단가.
 * 합계행(G만 있는 행)이 세트 경계이자 대표품목 가격이다.
 * 그룹핑(D16): 직전 연속 부속 합이 합계와 "일치"하면 부속으로 연결, "불일치"면 대표품목(첫 행)만
 * 합계가로 저장하고 {@code needsReview=true}, 나머지 행은 개별 제품으로 저장한다.
 * 신품번(F) 없는 행(D8): 저장하되 제품명 뒤 "(신품번 없음)" 표기, 단가 0.
 */
@Component
@RequiredArgsConstructor
public class VendorAExcelParser implements VendorExcelParser {

    private static final Logger logger = LoggerFactory.getLogger(VendorAExcelParser.class);

    @Override
    public String getVendorCode() { return "A"; }

    @Override
    public List<VendorProductSet> parseSets(Path path) {
        try (InputStream is = java.nio.file.Files.newInputStream(path)) {
            return parseSets(is);
        } catch (Exception e) {
            throw wrap("A사 엑셀 파싱 중 오류", e);
        }
    }

    public List<VendorProductSet> parseSets(InputStream is) {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            return parseSetsWorkbook(workbook);
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

    private List<VendorProductSet> parseSetsWorkbook(Workbook workbook) {
        List<VendorProductSet> result = new ArrayList<>();
        Sheet sheet = workbook.getSheetAt(0);

        String currentLargeCategory = null;
        String currentSmallCategory = null;

        List<VendorParsedItem> buffer = new ArrayList<>(); // 합계행 전까지 누적된 품목

        for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            // A(0)·B(1)열은 제외(D11)
            String colC = getStringCell(row, 2);
            String colD = getStringCell(row, 3);
            String colE = getStringCell(row, 4);
            String colF = getStringCell(row, 5);
            BigDecimal colG = getNumericCell(row, 6);

            boolean cP = !isBlank(colC);
            boolean dP = !isBlank(colD);
            boolean eP = !isBlank(colE);
            boolean fP = !isBlank(colF);
            boolean gP = colG != null;
            boolean dataP = dP || eP || fP || gP;

            // 0) 완전 빈 줄
            if (!cP && !dataP) continue;

            // 1) 헤더 줄
            if (isHeaderRowNoAB(colD, colE, colF)) continue;

            // 2) 합계행: C/D/E/F 비고 G만 있음 → 세트 종료 + 가격 확정
            if (!cP && !dP && !eP && !fP && gP) {
                closeSetWithTotal(buffer, colG, currentLargeCategory, currentSmallCategory, result);
                continue;
            }

            // 3) C 라벨 전용 행(C만 있고 데이터 없음): 소분류 또는 세트명
            if (cP && !dataP) {
                flushOrphans(buffer, currentLargeCategory, currentSmallCategory, result);
                String cNorm = normalizeNoSpace(colC.trim());
                String inferred = inferLargeCategoryFromSmallCategory(cNorm);
                if (!isBlank(inferred)) {           // 소분류 행
                    currentSmallCategory = cNorm;
                    currentLargeCategory = inferred;
                }
                // 세트명 전용 행(추론 불가)은 분류를 바꾸지 않음(현재는 별도 보관 안 함)
                continue;
            }

            // 4) 세트 시작 행(C=세트명 + 데이터): 이전 잔여 정리 후 첫 품목으로 버퍼에 추가
            if (cP && dataP) {
                flushOrphans(buffer, currentLargeCategory, currentSmallCategory, result);
                buffer.add(buildItem(colD, colE, colF, colG));
                continue;
            }

            // 5) 일반 품목 행(C 없음 + 데이터): 버퍼에 추가
            if (!cP && dataP) {
                buffer.add(buildItem(colD, colE, colF, colG));
            }
        }

        // EOF: 남은 잔여는 개별 제품으로
        flushOrphans(buffer, currentLargeCategory, currentSmallCategory, result);
        return result;
    }

    /** 합계행 도달 시 세트 확정 (D16). */
    private void closeSetWithTotal(List<VendorParsedItem> buffer, BigDecimal total,
                                   String large, String small, List<VendorProductSet> out) {
        if (buffer.isEmpty()) {
            logger.warn("[VendorA] 합계행이지만 직전 품목 버퍼가 비어있음. total={}", total);
            return;
        }

        int k = findTrailingRunStart(buffer, total);
        if (k >= 0) {
            // 일치: buffer[k..]가 세트, 그 앞(orphan)은 개별 제품
            for (int i = 0; i < k; i++) emitStandalone(buffer.get(i), large, small, out);

            VendorParsedItem main = withRelation(buffer.get(k), VendorParsedItem.RELATION_MAIN);
            List<VendorParsedItem> parts = new ArrayList<>();
            for (int i = k + 1; i < buffer.size(); i++) {
                parts.add(withRelation(buffer.get(i), VendorParsedItem.RELATION_ACCESSORY));
            }
            out.add(new VendorProductSet("A", large, small, main, parts, total, false, null, false));
        } else {
            // 불일치: 대표품목(첫 행)만 합계가로 저장 + 검수 플래그, 나머지는 개별
            VendorParsedItem main = withRelation(buffer.get(0), VendorParsedItem.RELATION_MAIN);
            out.add(new VendorProductSet("A", large, small, main, new ArrayList<>(), total, false, null, true));
            for (int i = 1; i < buffer.size(); i++) emitStandalone(buffer.get(i), large, small, out);
            logger.warn("[VendorA] 합계≠부속합산 → 검수필요. total={}, bufferSize={}, main={}",
                    total, buffer.size(), main.productName());
        }
        buffer.clear();
    }

    /** 끝에서부터 누적해 합계와 정확히 일치하는 "가장 짧은" 연속 구간의 시작 인덱스. 없으면 -1. */
    private int findTrailingRunStart(List<VendorParsedItem> buffer, BigDecimal total) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = buffer.size() - 1; j >= 0; j--) {
            BigDecimal p = buffer.get(j).unitPrice();
            sum = sum.add(p != null ? p : BigDecimal.ZERO);
            int cmp = sum.compareTo(total);
            if (cmp == 0) return j;
            if (cmp > 0) return -1; // 초과하면 더 늘려도 일치 불가(가격 음수 없음)
        }
        return -1;
    }

    /** 버퍼의 모든 품목을 개별(독립) 제품으로 방출하고 버퍼 비움. */
    private void flushOrphans(List<VendorParsedItem> buffer, String large, String small,
                              List<VendorProductSet> out) {
        for (VendorParsedItem it : buffer) emitStandalone(it, large, small, out);
        buffer.clear();
    }

    private void emitStandalone(VendorParsedItem it, String large, String small,
                                List<VendorProductSet> out) {
        VendorParsedItem main = withRelation(it, VendorParsedItem.RELATION_MAIN);
        out.add(new VendorProductSet("A", large, small, main, new ArrayList<>(),
                it.unitPrice(), false, null, false));
    }

    private VendorParsedItem buildItem(String colD, String colE, String colF, BigDecimal colG) {
        String code = isBlank(colF) ? null : colF.trim();
        String oldCode = isBlank(colE) ? null : colE.trim();
        BigDecimal price = (colG != null) ? colG : BigDecimal.ZERO;

        String name;
        if (!isBlank(colD)) name = colD.trim();
        else if (code != null) name = code;
        else if (oldCode != null) name = oldCode;
        else name = "미상";

        if (code == null) name = name + " (신품번 없음)"; // D8

        return new VendorParsedItem(code, name, oldCode, null,
                VendorParsedItem.RELATION_MAIN, price, null);
    }

    private VendorParsedItem withRelation(VendorParsedItem it, String relationType) {
        return new VendorParsedItem(it.productCode(), it.productName(), it.oldItemCode(),
                it.subItemCode(), relationType, it.unitPrice(), it.remark());
    }

    private boolean isHeaderRowNoAB(String colD, String colE, String colF) {
        if ("제품명".equals(colD)) return true;
        if (colE != null && colE.contains("구품번")) return true;
        if (colF != null && colF.contains("신품번")) return true;
        return false;
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
