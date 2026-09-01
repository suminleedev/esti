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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static com.example.esti.excel.ExcelParseUtils.isBlank;

/**
 * A사(아메리칸스탠다드) 단가표 파서 — 단일 시트.
 *
 * <p>A·B열 제외(D11): C(2)=소분류/세트명, D(3)=구성품명, E(4)=구품번, F(5)=신품번, G(6)=단가.
 * 합계행(G만 있는 행)이 세트 경계이자 대표품목 가격이다.
 * 그룹핑(D16): 직전 연속 부속 합이 합계와 "일치"하면 부속으로 연결, "불일치"면 대표품목(첫 행)만
 * 합계가로 저장하고 {@code needsReview=true}, 나머지 행은 개별 제품으로 저장한다.
 * 신품번(F) 없는 행(D8): 저장하되 제품명 뒤 "(신품번 없음)" 표기, 단가 0.
 *
 * <p><b>대분류는 B열 라벨 구간에서 온다(A-1·A-2).</b> B열 라벨은 구간 시작보다 일정 행 아래에
 * 얹혀 있어 위치를 그대로 못 쓰지만, 그 어긋남이 일정하다 — {@code 구간 시작 = B라벨 행 − 오프셋}이고
 * 오프셋은 파일에서 학습한다({@link #resolveLargeCategorySections}). C열 텍스트 추론은
 * B라벨이 하나도 없는 파일을 위한 폴백으로만 남는다.
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

        // A-1: B열 라벨로 대분류 구간 지도를 먼저 만든다. 비어 있으면 C열 추론 폴백으로 동작한다.
        NavigableMap<Integer, String> sections = resolveLargeCategorySections(sheet);

        String currentLargeCategory = null;
        String currentSmallCategory = null;

        List<VendorParsedItem> buffer = new ArrayList<>(); // 합계행 전까지 누적된 품목

        for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            // A-2: 대분류 구간이 바뀌면 대분류를 갱신하고 소분류는 리셋한다(A-4).
            // 액세서리·발코니수전처럼 C 라벨 전용행이 하나도 없는 구간에 직전 소분류가 넘어오는 것을 막는다.
            Map.Entry<Integer, String> section = sections.floorEntry(rowIdx);
            if (section != null && !section.getValue().equals(currentLargeCategory)) {
                // 버퍼에 남은 품목은 아직 직전 구간의 것이다. 분류를 바꾸기 전에 먼저 내보낸다.
                flushOrphans(buffer, currentLargeCategory, currentSmallCategory, result);
                currentLargeCategory = section.getValue();
                currentSmallCategory = null;
            }

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

            // 3) C 라벨 전용 행(C만 있고 데이터 없음) = 소분류
            if (cP && !dataP) {
                flushOrphans(buffer, currentLargeCategory, currentSmallCategory, result);
                String cNorm = normalizeNoSpace(colC.trim());
                if (!sections.isEmpty()) {
                    // A-3: 대분류가 B열 구간에서 오므로 추론 성공 여부와 무관하게 소분류로 확정한다.
                    // 예전에는 추론이 실패하면 세트명으로 흘려보내 직전 소분류가 그대로 이어졌다.
                    currentSmallCategory = cNorm;
                } else {
                    String inferred = inferLargeCategoryFromSmallCategory(cNorm);
                    if (!isBlank(inferred)) {       // 소분류 행
                        currentSmallCategory = cNorm;
                        currentLargeCategory = inferred;
                    }
                    // 세트명 전용 행(추론 불가)은 분류를 바꾸지 않음(현재는 별도 보관 안 함)
                }
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

    // ====== A-1·A-2: B열 라벨 → 대분류 구간 ======

    /** 오프셋을 학습하지 못했을 때 쓰는 기본값 — 실측한 2021 최신본(시트 {@code ASK})의 값. */
    private static final int DEFAULT_LABEL_OFFSET = 6;

    /** 오프셋 후보로 인정하는 최대 거리. 이보다 멀면 "직전 빈 행"이 그 구간의 것이 아니다. */
    private static final int MAX_LABEL_OFFSET = 12;

    /**
     * 원본 B열 대분류 라벨 → 저장 어휘(G-2). 원본은 12종, 저장 어휘는 11종이다
     * ({@code 매립형 욕조&부속}·{@code 스탠딩욕조}가 {@code 욕조}로 합쳐진다).
     * 키는 공백을 제거한 형태로 비교한다.
     */
    private static final Map<String, String> LARGE_CATEGORY_VOCABULARY = Map.ofEntries(
            Map.entry("양변기", "양변기"),
            Map.entry("전자비데", "비데"),
            Map.entry("세면기", "세면기"),
            Map.entry("매립형욕조&부속", "욕조"),
            Map.entry("스탠딩욕조", "욕조"),
            Map.entry("수전", "세면수전"),
            Map.entry("샤워", "샤워수전"),
            Map.entry("주방수전", "주방수전"),
            Map.entry("액세서리", "액세서리"),
            Map.entry("발코니수전", "발코니수전"),
            Map.entry("상업용제품", "상업용제품"),
            Map.entry("부속", "부속"));

    /**
     * B열 대분류 라벨의 위치에서 구간 경계를 뽑는다(A-1).
     *
     * <p>B열 라벨은 <b>구간이 시작된 뒤 일정 행 아래</b>에 시각적으로 얹혀 있다. 위치를 그대로
     * 구간 시작으로 쓰면 앞부분이 직전 대분류로 잘리지만, 그 어긋남이 일정해서 되돌릴 수 있다.
     *
     * <pre>
     *   구간 시작 = (B라벨 행) − 오프셋,  그 행이 완전 빈 행이면 다음 행
     * </pre>
     *
     * <p>오프셋은 상수로 박지 않고 <b>파일에서 학습한다</b> — 라벨마다 직전 빈 행까지의 거리를 재고
     * 그 최빈값을 쓴다. 다음 최신본의 레이아웃이 달라져도 따라간다.
     *
     * @return 구간 시작 행 → 대분류. B열 라벨이 하나도 없으면 빈 맵(= C열 추론 폴백)
     */
    private NavigableMap<Integer, String> resolveLargeCategorySections(Sheet sheet) {
        List<Integer> blankRows = new ArrayList<>();
        List<Integer> labelRows = new ArrayList<>();
        Map<Integer, String> labels = new HashMap<>();

        for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null || isBlankRow(row)) {
                blankRows.add(rowIdx);
                continue;
            }
            String colB = getStringCell(row, 1);
            if (isBlank(colB) || isHeaderRowNoAB(getStringCell(row, 3), getStringCell(row, 4),
                    getStringCell(row, 5))) {
                continue;
            }
            labelRows.add(rowIdx);
            labels.put(rowIdx, colB.trim());
        }

        NavigableMap<Integer, String> sections = new TreeMap<>();
        if (labelRows.isEmpty()) return sections;

        int offset = learnLabelOffset(labelRows, blankRows);

        int previousStart = Integer.MIN_VALUE;
        for (int labelRow : labelRows) {
            int start = Math.max(0, labelRow - offset);
            if (blankRows.contains(start)) start++;      // 빈 행은 구분선이므로 그 다음 행이 시작
            if (start <= previousStart) {
                logger.warn("[VendorA] 대분류 구간 시작이 역행 → 보정. label='{}' row={} start={} prev={}",
                        labels.get(labelRow), labelRow + 1, start + 1, previousStart + 1);
                start = previousStart + 1;
            }
            sections.put(start, normalizeLargeCategory(labels.get(labelRow)));
            previousStart = start;
        }
        logger.info("[VendorA] 대분류 구간 {}개(오프셋 {}): {}", sections.size(), offset,
                sections.entrySet().stream()
                        .map(e -> "r" + (e.getKey() + 1) + "~" + e.getValue())
                        .toList());
        return sections;
    }

    /** B라벨마다 직전 빈 행까지의 거리를 재고 최빈값을 오프셋으로 삼는다. 근거가 없으면 기본값. */
    private int learnLabelOffset(List<Integer> labelRows, List<Integer> blankRows) {
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int labelRow : labelRows) {
            int nearestBlank = -1;
            for (int blankRow : blankRows) {
                if (blankRow >= labelRow) break;
                nearestBlank = blankRow;
            }
            if (nearestBlank < 0) continue;
            int distance = labelRow - nearestBlank;
            if (distance <= MAX_LABEL_OFFSET) histogram.merge(distance, 1, Integer::sum);
        }
        if (histogram.isEmpty()) return DEFAULT_LABEL_OFFSET;

        int best = DEFAULT_LABEL_OFFSET;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : histogram.entrySet()) {
            // 동률이면 더 짧은 거리를 택한다 — 구간을 넘겨 잡는 쪽이 위험하다
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() < best)) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    /** 원본 B열 라벨을 저장 어휘로 옮긴다. 사전에 없으면 원문을 그대로 쓰고 경고를 남긴다. */
    private String normalizeLargeCategory(String rawLabel) {
        String key = normalizeNoSpace(rawLabel);
        String mapped = LARGE_CATEGORY_VOCABULARY.get(key);
        if (mapped != null) return mapped;
        logger.warn("[VendorA] 사전에 없는 대분류 라벨 '{}' — 원문 그대로 사용한다.", rawLabel);
        return key;
    }

    /** A~G 전부 비어 있는 행 = 구간 구분선. */
    private boolean isBlankRow(Row row) {
        for (int colIdx = 0; colIdx <= 6; colIdx++) {
            if (!isBlank(getStringCell(row, colIdx))) return false;
        }
        return true;
    }

    /**
     * C열(소분류 후보) 텍스트를 기반으로 대분류를 추론한다 — <b>B열 라벨이 없는 파일용 폴백</b>.
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

}
