package com.example.esti.excel;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B사(이누스) 단가표 파서 — 멀티 시트. 시트명으로 4개 양식 패밀리를 판별해 전용 파서로 분기한다(P3).
 *
 * <ul>
 *   <li><b>슬롯 2행형</b>(양변기 / 소변기,수채 / 세면기): 제품코드행 + 대리점가행 한 쌍.
 *       헤더의 슬롯 라벨(G~M)을 동적 인식해 도기=MAIN, 나머지=슬롯 라벨 relation(D9).
 *       計 컬럼이 있으면 세트가, 세면기는 計 없음 → 선택형 세트(D10).</li>
 *   <li><b>갈라시아 4행형</b>: 제품코드행 + 대리점가행 + 합계행 + 소비자단가행. 슬롯 E=도기, F=부속.</li>
 *   <li><b>소계 세트형</b>(악세사리 / 수전금구(국산 부속 기준) / 수전 부속(세트)):
 *       대표행 + 부속행들 + (소계행). 대표품목 + 부속 + 합계.</li>
 *   <li><b>단일행형</b>(비데,기타 / 수전금구): 1행 = 독립 제품. 부속 없음. 대리점가(G)만 채택(D7).
 *       시트 내 서브테이블이 여러 개면 헤더를 반복 탐지.</li>
 * </ul>
 *
 * 가격 없는 행은 스킵하지 않고 단가 0 + 제품명 뒤 "(가격없음)" 표기(D8).
 */
@Component
public class VendorBExcelParser implements VendorExcelParser {

    private static final Logger logger = LoggerFactory.getLogger(VendorBExcelParser.class);

    private static final int CODE_MAX_LEN = 50;
    private static final int SLOT_FIRST_COL = 6; // G열부터 슬롯 후보

    @Override
    public String getVendorCode() { return "B"; }

    @Override
    public List<VendorProductSet> parseSets(Path path) {
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter();

            List<VendorProductSet> result = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                String name = sheet.getSheetName();
                Ctx ctx = new Ctx(sheet, fmt, ev, name);

                switch (family(name)) {
                    case SLOT       -> parseSlotSheet(ctx, result);
                    case GALAXIA    -> parseGalaxiaSheet(ctx, result);
                    case SET_TOTAL  -> parseHeaderTotalSetSheet(ctx, result);
                    case SET_SUBTOTAL -> parseSubtotalSetSheet(ctx, result);
                    case SINGLE     -> parseSingleRowSheet(ctx, result);
                }
            }
            return result;
        } catch (Exception e) {
            throw wrap("B사 엑셀 파싱 중 오류", e);
        }
    }

    // ============================================================
    // 패밀리 판별
    // ============================================================

    private enum Family { SLOT, GALAXIA, SET_TOTAL, SET_SUBTOTAL, SINGLE }

    private Family family(String sheetName) {
        String n = sheetName.replaceAll("\\s", "");
        if (n.equals("양변기") || n.contains("소변기") || n.equals("세면기")) return Family.SLOT;
        if (n.contains("갈라시아")) return Family.GALAXIA;
        if (n.contains("악세사리") || n.contains("악세서리")) return Family.SET_TOTAL;
        if (n.contains("국산부속") || n.contains("수전부속")) return Family.SET_SUBTOTAL;
        return Family.SINGLE; // 비데,기타 / 수전금구 등
    }

    // ============================================================
    // (A) 슬롯 2행형 — 양변기 / 소변기,수채 / 세면기
    // ============================================================

    private void parseSlotSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "구분".equals(str(c, r, 0))
                && "품종".equals(str(c, r, 1)) && "품번".equals(str(c, r, 2)));
        if (headerRow < 0) {
            logger.warn("[B][{}] 슬롯 헤더(구분/품종/품번) 미발견 → 스킵", c.sheetName);
            return;
        }

        // 헤더에서 슬롯/計 컬럼 동적 인식
        Map<Integer, String> slots = new LinkedHashMap<>();
        int totalCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = stripSpace(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (isSkipSlotLabel(label)) continue;
            slots.put(col, label);
        }
        boolean selectable = (totalCol < 0); // 計 없음 → 세면기 선택형(D10)

        int last = c.sheet.getLastRowNum();
        for (int r = headerRow + 1; r <= last; r++) {
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작

            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
                if ("대리점가".equals(str(c, k, 5))) { priceRow = k; break; }
                if ("제품코드".equals(str(c, k, 5))) break; // 다음 제품행 만나면 중단
            }

            String repCode = normalizeCode(str(c, r, 2)); // C=품번(대표)
            if (repCode == null) continue;
            String kind = stripSpace(str(c, r, 1));        // B=품종
            String ksCode = normalizeCode(str(c, r, 4));   // E=KS품번

            List<VendorParsedItem> parts = new ArrayList<>();
            BigDecimal partSum = BigDecimal.ZERO;
            for (Map.Entry<Integer, String> slot : slots.entrySet()) {
                int col = slot.getKey();
                String slotLabel = slot.getValue();
                String partCode = normalizeCode(str(c, r, col));
                if (partCode == null) continue;
                BigDecimal partPrice = priceRow < 0 ? null : dec(c, priceRow, col);
                if (partPrice == null) partPrice = BigDecimal.ZERO;
                partSum = partSum.add(partPrice);

                String relation = slotLabel.startsWith("도기")
                        ? VendorParsedItem.RELATION_MAIN : slotLabel;
                parts.add(new VendorParsedItem(partCode, slotLabel, null, null,
                        relation, partPrice, null));
            }

            BigDecimal setPrice = null;
            if (!selectable && priceRow >= 0 && totalCol >= 0) {
                setPrice = dec(c, priceRow, totalCol);
                if (setPrice != null && partSum.compareTo(setPrice) != 0) {
                    logger.warn("[B][{}] 計≠부속합 (rep={}, 計={}, 합={})",
                            c.sheetName, repCode, setPrice, partSum);
                }
            }

            String repName = join(kind, repCode);
            if (selectable) repName = repName + " (부속 선택형)";        // D10
            else if (setPrice == null) repName = repName + " (가격없음)"; // D8

            VendorParsedItem main = new VendorParsedItem(repCode, repName, null, ksCode,
                    VendorParsedItem.RELATION_MAIN, setPrice != null ? setPrice : BigDecimal.ZERO, null);

            out.add(new VendorProductSet("B", c.sheetName, kind, main, parts,
                    setPrice, selectable, imageKeyOf(r), false));
        }
    }

    // ============================================================
    // (B) 갈라시아 4행형
    // ============================================================

    private void parseGalaxiaSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "구분".equals(str(c, r, 0)) && "품번".equals(str(c, r, 1)));
        if (headerRow < 0) {
            logger.warn("[B][{}] 갈라시아 헤더(구분/품번) 미발견 → 스킵", c.sheetName);
            return;
        }
        // 슬롯: E(4)=도기, F(5)=부속 (헤더 라벨 사용)
        String slotEName = orDefault(stripSpace(str(c, headerRow, 4)), "도기");
        String slotFName = orDefault(stripSpace(str(c, headerRow, 5)), "부속");

        int last = c.sheet.getLastRowNum();
        for (int r = headerRow + 1; r <= last; r++) {
            if (!"제품코드".equals(str(c, r, 3))) continue; // D=제품코드행

            String repCode = normalizeCode(str(c, r, 1)); // B=품번(대표)
            if (repCode == null) continue;
            String dogiCode = normalizeCode(str(c, r, 4));
            String partCode = normalizeCode(str(c, r, 5));

            // 다음 행 = 대리점가(D=대리점가), E/F=단가
            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 2, last); k++) {
                if ("대리점가".equals(str(c, k, 3))) { priceRow = k; break; }
            }
            BigDecimal dogiPrice = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, 4));
            BigDecimal partPrice = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, 5));

            List<VendorParsedItem> parts = new ArrayList<>();
            if (dogiCode != null) parts.add(new VendorParsedItem(dogiCode, slotEName, null, null,
                    VendorParsedItem.RELATION_MAIN, dogiPrice, null));
            if (partCode != null) parts.add(new VendorParsedItem(partCode, slotFName, null, null,
                    slotFName, partPrice, null));

            BigDecimal setPrice = dogiPrice.add(partPrice);
            VendorParsedItem main = new VendorParsedItem(repCode, repCode,
                    null, null, VendorParsedItem.RELATION_MAIN, setPrice, null);
            out.add(new VendorProductSet("B", c.sheetName, null, main, parts,
                    setPrice, false, imageKeyOf(r), false));
        }
    }

    // ============================================================
    // (C-1) 소계 세트형 — 헤더행이 곧 세트 합계 (악세사리)
    //   대표행(A=품목 있음, D=품번, H=대리점가) + 부속행들(A 없음, F=품명, H=가)
    // ============================================================

    private void parseHeaderTotalSetSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "품번".equals(noSpace(str(c, r, 3)))
                && containsPrice(str(c, r, 7)));
        if (headerRow < 0) {
            logger.warn("[B][{}] 악세사리 헤더 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();
        VendorParsedItem mainItem = null;
        List<VendorParsedItem> parts = null;
        String catSmall = null;
        BigDecimal setPrice = null;
        int mainRow = -1;

        for (int r = headerRow + 1; r <= last; r++) {
            String a = stripSpace(str(c, r, 0)); // 품목(세트 경계)
            String code = normalizeCode(str(c, r, 3)); // D=품번
            if (code == null) continue;

            boolean isSetStart = a != null; // A=품목 있으면 새 세트
            if (isSetStart) {
                flushSet(out, mainItem, parts, catSmall, setPrice, c.sheetName, mainRow);
                catSmall = stripSpace(str(c, r, 1)); // B=세부분류
                setPrice = nz(dec(c, r, 7));          // H=대리점가(세트 합계)
                String name = orDefault(stripSpace(str(c, r, 5)), code); // F=품명
                mainItem = new VendorParsedItem(code, join(catSmall, name), null, null,
                        VendorParsedItem.RELATION_MAIN, setPrice, null);
                parts = new ArrayList<>();
                mainRow = r;
            } else if (parts != null) {
                String name = orDefault(stripSpace(str(c, r, 5)), code);
                BigDecimal price = nz(dec(c, r, 7));
                parts.add(new VendorParsedItem(code, name, null, null, name, price, null));
            }
        }
        flushSet(out, mainItem, parts, catSmall, setPrice, c.sheetName, mainRow);
    }

    // ============================================================
    // (C-2) 소계 세트형 — 소계행으로 세트 종료 (국산 부속 기준 / 수전 부속(세트))
    //   대표행(A 있음) + 부속행(A 없음) + 소계행(C/B="소계")
    //   국산부속: code=C(2), name=B(1), price=G(6) | 수전부속: code=B(1), name=A/B, price=F(5)
    // ============================================================

    private void parseSubtotalSetSheet(Ctx c, List<VendorProductSet> out) {
        boolean korParts = c.sheetName.replaceAll("\\s", "").contains("국산부속");
        int codeCol  = korParts ? 2 : 1; // 국산:C품번 / 수전부속:B품번
        int nameCol  = korParts ? 1 : 0; // 국산:B품명 / 수전부속:A품명
        int priceCol = korParts ? 6 : 5; // 국산:G단가 / 수전부속:F단가
        int subtotalLabelCol = korParts ? 2 : 2; // "소계"는 C열

        int headerRow = findRow(c, r -> {
            String a = noSpace(str(c, r, 0));
            return a != null && (a.contains("품목") || a.contains("품명"));
        });
        if (headerRow < 0) {
            logger.warn("[B][{}] 소계세트 헤더 미발견 → 스킵", c.sheetName);
            return;
        }

        int last = c.sheet.getLastRowNum();
        VendorParsedItem mainItem = null;
        List<VendorParsedItem> parts = null;
        String catSmall = null;
        BigDecimal setPrice = null;
        int mainRow = -1;

        for (int r = headerRow + 1; r <= last; r++) {
            String subtotalCell = stripSpace(str(c, r, subtotalLabelCol));
            if (subtotalCell != null && subtotalCell.replace(" ", "").contains("소계")) {
                setPrice = nz(dec(c, r, priceCol));
                flushSet(out, mainItem, parts, catSmall, setPrice, c.sheetName, mainRow);
                mainItem = null; parts = null; catSmall = null; setPrice = null; mainRow = -1;
                continue;
            }

            String code = normalizeCode(str(c, r, codeCol));
            if (code == null) continue;
            String name = orDefault(stripSpace(str(c, r, nameCol)), code);
            BigDecimal price = nz(dec(c, r, priceCol));
            String a = stripSpace(str(c, r, 0)); // 세트 경계

            if (a != null) { // 대표행
                flushSet(out, mainItem, parts, catSmall, setPrice, c.sheetName, mainRow);
                catSmall = name;
                mainItem = new VendorParsedItem(code, name, null, null,
                        VendorParsedItem.RELATION_MAIN, price, null);
                parts = new ArrayList<>();
                setPrice = null;
                mainRow = r;
            } else if (parts != null) { // 부속행
                parts.add(new VendorParsedItem(code, name, null, null,
                        VendorParsedItem.RELATION_ACCESSORY, price, null));
            }
        }
        flushSet(out, mainItem, parts, catSmall, setPrice, c.sheetName, mainRow);
    }

    /** 누적된 세트 1건을 결과에 추가. setPrice 없으면 본품 단가 사용. */
    private void flushSet(List<VendorProductSet> out, VendorParsedItem mainItem,
                          List<VendorParsedItem> parts, String catSmall,
                          BigDecimal setPrice, String sheetName, int mainRow) {
        if (mainItem == null) return;
        BigDecimal price = setPrice != null ? setPrice : mainItem.unitPrice();
        VendorParsedItem main = new VendorParsedItem(mainItem.productCode(), mainItem.productName(),
                mainItem.oldItemCode(), mainItem.subItemCode(), VendorParsedItem.RELATION_MAIN,
                price, mainItem.remark());
        out.add(new VendorProductSet("B", sheetName, catSmall, main,
                parts != null ? parts : new ArrayList<>(), price, false, imageKeyOf(mainRow), false));
    }

    // ============================================================
    // (D) 단일행형 — 비데,기타 / 수전금구 (서브테이블 다수 가능)
    // ============================================================

    private void parseSingleRowSheet(Ctx c, List<VendorProductSet> out) {
        int last = c.sheet.getLastRowNum();
        ColMap cm = null;

        for (int r = 0; r <= last; r++) {
            ColMap detected = detectSingleHeader(c, r);
            if (detected != null) { cm = detected; continue; } // 헤더행이면 갱신
            if (cm == null) continue;

            String code = normalizeCode(str(c, r, cm.codeCol));
            if (code == null) continue;
            if (isHeaderLikeCode(code)) continue;

            String name = cm.nameCol >= 0 ? stripSpace(str(c, r, cm.nameCol)) : null;
            BigDecimal price = cm.priceCol >= 0 ? dec(c, r, cm.priceCol) : null;
            String remark = cm.remarkCol >= 0 ? stripSpace(str(c, r, cm.remarkCol)) : null;

            String displayName = join(name, code);
            if (price == null) displayName = displayName + " (가격없음)"; // D8

            VendorParsedItem main = new VendorParsedItem(code, displayName, null, null,
                    VendorParsedItem.RELATION_MAIN, nz(price), remark);
            out.add(new VendorProductSet("B", c.sheetName, name, main,
                    new ArrayList<>(), nz(price), false, imageKeyOf(r), false));
        }
    }

    /** 임베디드 이미지 매칭 키 = 대표품목의 0-based 행 인덱스(없으면 null). 시트는 categoryLarge로 식별. */
    private String imageKeyOf(int row) {
        return row >= 0 ? String.valueOf(row) : null;
    }

    /**
     * 단일행 시트의 헤더행 탐지(품번 + 대리점가 동시 존재). 헤더면 ColMap, 아니면 null.
     * 수전금구처럼 헤더가 두 줄로 쪼개진 경우를 위해 현재 행 + 윗행을 컬럼별로 병합해서 읽는다.
     */
    private ColMap detectSingleHeader(Ctx c, int r) {
        short lastCell = c.sheet.getRow(r) == null ? 0 : c.sheet.getRow(r).getLastCellNum();
        int codeCol = -1, nameCol = -1, priceCol = -1, remarkCol = -1;
        boolean hasCode = false, hasPrice = false;

        for (int col = 0; col < lastCell; col++) {
            String h = noSpace(str(c, r, col));
            if (h == null && r > 0) h = noSpace(str(c, r - 1, col)); // 윗행 병합
            if (h == null) continue;
            if (h.equals("품번") && codeCol < 0) { codeCol = col; hasCode = true; }
            else if ((h.equals("품종") || h.equals("품목")) && nameCol < 0) nameCol = col;
            else if (h.contains("대리점가") && priceCol < 0) { priceCol = col; hasPrice = true; }
            else if (h.contains("비고") && remarkCol < 0) remarkCol = col;
        }
        if (!(hasCode && hasPrice)) return null;
        return new ColMap(codeCol, nameCol, priceCol, remarkCol);
    }

    private record ColMap(int codeCol, int nameCol, int priceCol, int remarkCol) {}

    // ============================================================
    // 라벨/셀 유틸
    // ============================================================

    private boolean isTotalLabel(String label) {
        String s = label.replace(" ", "");
        return s.equals("計") || s.equals("계") || s.equals("합계") || s.equals("총계");
    }

    private boolean isSkipSlotLabel(String label) {
        String s = label.replace(" ", "");
        return s.contains("수량") || s.contains("비고") || s.equals("하부") || s.equals("상부")
                || s.contains("PLT");
    }

    private boolean containsPrice(String s) {
        String n = noSpace(s);
        return n != null && n.contains("대리점가");
    }

    private boolean isHeaderLikeCode(String code) {
        String s = code.replace(" ", "");
        return s.equals("품번") || s.equals("품종") || s.equals("품목")
                || s.equals("제품코드") || s.equals("구분") || s.equals("코드");
    }

    private interface RowMatcher { boolean matches(int rowIdx); }

    private int findRow(Ctx c, RowMatcher m) {
        int max = Math.min(c.sheet.getLastRowNum(), 80);
        for (int r = 0; r <= max; r++) {
            if (c.sheet.getRow(r) == null) continue;
            if (m.matches(r)) return r;
        }
        return -1;
    }

    private String str(Ctx c, int row, int col) {
        Row r = c.sheet.getRow(row);
        if (r == null) return null;
        Cell cell = r.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String v = c.fmt.formatCellValue(cell, c.ev);
        if (v == null) return null;
        v = v.replace(' ', ' ').trim();
        return v.isEmpty() ? null : v;
    }

    private BigDecimal dec(Ctx c, int row, int col) {
        String txt = str(c, row, col);
        if (txt == null) return null;
        String cleaned = txt.replace(",", "").replace("₩", "").replace("원", "").trim()
                .replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try { return new BigDecimal(cleaned); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String stripSpace(String s) {
        if (s == null) return null;
        String x = s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return x.isEmpty() ? null : x;
    }

    /** 헤더 비교용: 모든 공백 제거("품 번"→"품번"). */
    private String noSpace(String s) {
        if (s == null) return null;
        String x = s.replaceAll("[\\s ]+", "");
        return x.isEmpty() ? null : x;
    }

    private String normalizeCode(String s) {
        String x = stripSpace(s);
        if (x == null) return null;
        int p = x.indexOf('(');
        if (p > 0) x = x.substring(0, p).trim();
        if (x.length() > CODE_MAX_LEN) x = x.substring(0, CODE_MAX_LEN);
        return x.isEmpty() ? null : x;
    }

    private String join(String a, String b) {
        if (isBlank(a)) return b;
        if (isBlank(b)) return a;
        return a + " " + b;
    }

    private String orDefault(String v, String def) { return isBlank(v) ? def : v; }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private RuntimeException wrap(String msg, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return new RuntimeException(msg + ": " + root.getClass().getName() + " - " + root.getMessage(), e);
    }

    /** 시트 1개 파싱 컨텍스트(POI 객체 묶음). */
    private record Ctx(Sheet sheet, DataFormatter fmt, FormulaEvaluator ev, String sheetName) {}
}
