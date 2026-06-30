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
                    case TOILET     -> parseToiletSheet(ctx, result);
                    case WASHBASIN  -> parseWashbasinSheet(ctx, result);
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

    private enum Family { TOILET, WASHBASIN, SLOT, GALAXIA, SET_TOTAL, SET_SUBTOTAL, SINGLE }

    private Family family(String sheetName) {
        String n = sheetName.replaceAll("\\s", "");
        if (n.equals("양변기")) return Family.TOILET;            // 양변기 전용 경로(서브테이블별 헤더/품종 병합 처리)
        if (n.equals("세면기")) return Family.WASHBASIN;          // 세면기 전용 경로(선택형 기본구성·도자 분기·괄호 설명 분리)
        if (n.contains("소변기")) return Family.SLOT;
        if (n.contains("갈라시아")) return Family.GALAXIA;
        if (n.contains("악세사리") || n.contains("악세서리")) return Family.SET_TOTAL;
        if (n.contains("국산부속") || n.contains("수전부속")) return Family.SET_SUBTOTAL;
        return Family.SINGLE; // 비데,기타 / 수전금구 등
    }

    // ============================================================
    // (A-0) 양변기 전용 — 슬롯 2행형이지만 시트 안에 서브테이블이 여러 개다.
    //   · 서브테이블마다 헤더(구분/품종/품번)가 따로 있고 슬롯 라벨(G~M)이 다르다
    //     (F/V 구간: 도기/F/V/스퍼드…  vs  투피스 구간: 하부/탱크(사출수로)/양부속…). → 헤더를 만날 때마다 슬롯 갱신.
    //   · 품종(B)이 병합셀이라 구간 첫 행에만 값이 있고 이후 행은 비어 있다 → 직전 품종을 이어쓴다.
    //   · 품번(C)/부속 제품코드(G~M)/대리점가행의 변형마커가 "코드(설명)" 형태면 설명을 description으로 분리.
    // ============================================================

    private void parseToiletSheet(Ctx c, List<VendorProductSet> out) {
        int firstHeader = findRow(c, r -> isSlotHeader(c, r));
        if (firstHeader < 0) {
            logger.warn("[B][{}] 양변기 헤더(구분/품종/품번) 미발견 → 스킵", c.sheetName);
            return;
        }

        Map<Integer, String> slots = new LinkedHashMap<>();
        int totalCol = readSlotHeader(c, firstHeader, slots);
        String lastKind = null;    // 품종(B) 병합셀 → 직전 품종 유지(req1)
        String prevRepCode = null; // 직전 제품의 base 품번 — 도기수로/사출수로 충돌 판별용

        int last = c.sheet.getLastRowNum();
        // 도자 종류만 다른 동일 품번 중복(IC703E(성오도자)/IC703E(구륙도자) 등)을 도기 코드 구분 글자로 분기(row→최종품번)
        Map<Integer, String> dojaOverrides = computeDojaCodeOverrides(c, firstHeader, last);
        for (int r = firstHeader + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) {                 // 새 서브테이블 헤더 → 슬롯 라벨 갱신(req2)
                totalCol = readSlotHeader(c, r, slots);
                continue;
            }
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작

            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
                if ("대리점가".equals(str(c, k, 5))) { priceRow = k; break; }
                if ("제품코드".equals(str(c, k, 5))) break;
            }

            String[] rep = splitParen(str(c, r, 2)); // C=품번(대표) — 괄호 설명은 description으로 분리(req3)
            String baseCode = rep[0];
            if (baseCode == null) continue;
            String repDesc = rep[1];
            String repCode = baseCode;

            boolean isSachul = false; // 품번에 "(사출수로)" 표시가 있는 사출수로 품목인지 → 탱크 슬롯 이름 결정
            String override = dojaOverrides.get(r);
            if (override != null) {
                // 도자 종류만 다른 동일 품번 → 도기 코드 구분 글자를 양쪽 모두 접미(예: IC703Eo/IC703Eg). 도자명은 description 유지.
                repCode = override;
            } else if (priceRow >= 0) {
                // 대리점가 행 C에 변형 마커("(도기수로)"/"(사출수로)")만 있으면 description에 병합(req3).
                // 도기수로/사출수로는 같은 품번(C)을 공유해 충돌한다(예: C853 r45=도기수로 / r47=사출수로).
                // 하부도기 품번이 사출수로일 때 '…pwt'로 다른 점을 반영해, 직전 형제(같은 base)가 있는
                // 사출수로 변형에만 품번 뒤에 'p'를 붙여 둘 다 보존한다(단독 사출수로 IC858P 등은 그대로).
                String[] variant = splitParen(str(c, priceRow, 2));
                if (variant[0] == null && variant[1] != null) {
                    repDesc = join(repDesc, variant[1]);
                    if (variant[1].replaceAll("\\s", "").contains("사출수로")) {
                        isSachul = true;
                        if (baseCode.equals(prevRepCode)) repCode = baseCode + "p";
                    }
                }
            }
            prevRepCode = baseCode;

            String kindRaw = stripSpace(str(c, r, 1)); // B=품종
            if (kindRaw != null) lastKind = kindRaw;   // 병합셀 빈칸 → 직전 품종 유지(req1)
            String kind = lastKind;
            String ksCode = normalizeCode(str(c, r, 4)); // E=KS품번

            List<VendorParsedItem> parts = new ArrayList<>();
            BigDecimal partSum = BigDecimal.ZERO;
            for (Map.Entry<Integer, String> slot : slots.entrySet()) {
                int col = slot.getKey();
                String label = slot.getValue();         // 가장 가까운 위 헤더의 슬롯 라벨(req2)
                String[] sc = splitParen(str(c, r, col)); // 부속 제품코드의 괄호 설명 분리(req3)
                if (sc[0] == null) continue;
                BigDecimal price = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, col));
                partSum = partSum.add(price);
                String partName = resolveTankSlotName(label, isSachul); // "탱크(사출수로)" → 사출수로 품목이면 "사출수로", 아니면 "탱크"
                String relation = label.startsWith("도기") ? VendorParsedItem.RELATION_MAIN : partName;
                parts.add(new VendorParsedItem(partCode(repCode, coldHot(sc[0], label)), partName, null, null,
                        relation, price, null, sc[1]));
            }

            BigDecimal setPrice = (priceRow >= 0 && totalCol >= 0) ? dec(c, priceRow, totalCol) : null;
            if (setPrice != null && partSum.compareTo(setPrice) != 0) {
                logger.warn("[B][{}] 計≠부속합 (rep={}, 計={}, 합={})", c.sheetName, repCode, setPrice, partSum);
            }

            String repName = join(kind, repCode);
            if (setPrice == null) repName = repName + " (가격없음)"; // D8
            VendorParsedItem main = new VendorParsedItem(repCode, repName, null, ksCode,
                    VendorParsedItem.RELATION_MAIN, setPrice != null ? setPrice : BigDecimal.ZERO, null, repDesc);
            out.add(new VendorProductSet("B", c.sheetName, kind, main, parts,
                    setPrice, false, imageKeyOf(r), false));
        }
    }

    private boolean isSlotHeader(Ctx c, int r) {
        return "구분".equals(str(c, r, 0)) && "품종".equals(str(c, r, 1)) && "품번".equals(str(c, r, 2));
    }

    /**
     * 양변기 헤더행에서 슬롯(col→라벨)을 채우고 計 컬럼 인덱스를 반환(없으면 -1). 슬롯 라벨은 내부공백 제거 정규화.
     * 투피스 구간은 G슬롯 라벨이 "하부"(도기 하부)라 일반 슬롯과 달리 스킵하면 안 된다
     * (공용 {@code isSkipSlotLabel}은 수량 서브헤더용 "하부/상부"를 스킵하므로 여기선 쓰지 않는다).
     */
    private int readSlotHeader(Ctx c, int headerRow, Map<Integer, String> slots) {
        slots.clear();
        int totalCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (isSkipToiletSlotLabel(label)) continue;
            slots.put(col, label);
        }
        return totalCol;
    }

    /** 양변기 슬롯 스킵: 수량/비고/PLT만 제외(하부/상부는 실제 도기 슬롯이라 유지). */
    private boolean isSkipToiletSlotLabel(String label) {
        String s = label.replace(" ", "");
        return s.contains("수량") || s.contains("비고") || s.contains("PLT");
    }

    /**
     * "코드(설명)" 분리. 반환[0]=정규화 코드, [1]=괄호 안 설명.
     * 순수 "(설명)"이면 [null, 설명], 괄호 없으면 [코드, null], 빈 셀이면 [null, null].
     */
    private String[] splitParen(String raw) {
        String x = stripSpace(raw);
        if (x == null) return new String[]{null, null};
        int p = x.indexOf('(');
        if (p < 0) return new String[]{normalizeCode(x), null};
        String codePart = x.substring(0, p).trim();
        int q = x.indexOf(')', p + 1);
        String desc = (q > p ? x.substring(p + 1, q) : x.substring(p + 1)).trim();
        return new String[]{
                codePart.isEmpty() ? null : normalizeCode(codePart),
                desc.isEmpty() ? null : desc
        };
    }

    /**
     * "탱크(사출수로)" 슬롯의 표시명 결정: 품번에 "(사출수로)" 표시가 있는 사출수로 품목이면 "사출수로",
     * 그 외(탱크 부속)이면 "탱크". 괄호가 없는 일반 슬롯 라벨은 그대로 둔다.
     */
    private String resolveTankSlotName(String label, boolean isSachul) {
        if (label == null) return null;
        if (label.replace(" ", "").contains("(사출수로)")) {
            String[] bp = splitParen(label); // [0]="탱크", [1]="사출수로"
            return isSachul ? bp[1] : bp[0];
        }
        return label;
    }

    /**
     * 도자 종류만 다른 동일 품번 중복(예: IC703E(성오도자) r85 / IC703E(구륙도자) r87)을
     * 도기(G) 코드의 첫 구분 글자로 분기한다(4<b>o</b>c703wt/4<b>g</b>c703wt → IC703Eo/IC703Eg).
     * 도기수로/사출수로 그룹(대리점가 행에 "수로" 마커)은 'p' 로직이 처리하므로 제외한다.
     * 반환: 분기 대상 제품코드행의 0-based row → 최종 품번(base+구분글자).
     */
    private Map<Integer, String> computeDojaCodeOverrides(Ctx c, int firstHeader, int last) {
        record Member(int row, String dogi) {}
        Map<String, List<Member>> groups = new LinkedHashMap<>();
        Map<String, Boolean> suroGroup = new LinkedHashMap<>();
        for (int r = firstHeader + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) continue;
            if (!"제품코드".equals(str(c, r, 5))) continue;
            String base = splitParen(str(c, r, 2))[0];
            if (base == null) continue;

            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
                if ("대리점가".equals(str(c, k, 5))) { priceRow = k; break; }
                if ("제품코드".equals(str(c, k, 5))) break;
            }
            boolean suro = false;
            if (priceRow >= 0) {
                String[] v = splitParen(str(c, priceRow, 2));
                if (v[0] == null && v[1] != null && v[1].replaceAll("\\s", "").contains("수로")) suro = true;
            }
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(new Member(r, normalizeCode(str(c, r, 6)))); // G=도기
            suroGroup.merge(base, suro, (a, b) -> a || b);
        }

        Map<Integer, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, List<Member>> e : groups.entrySet()) {
            List<Member> ms = e.getValue();
            if (ms.size() < 2 || Boolean.TRUE.equals(suroGroup.get(e.getKey()))) continue;
            int k = firstDiffIndex(ms.stream().map(Member::dogi).toList());
            for (Member m : ms) {
                String suffix = (m.dogi() != null && m.dogi().length() > k) ? String.valueOf(m.dogi().charAt(k)) : "";
                overrides.put(m.row(), e.getKey() + suffix);
            }
        }
        return overrides;
    }

    /** 문자열 리스트에서 모두 같지 않은 첫 인덱스. null이 있으면 0, 공통 접두뿐이면 최소 길이. */
    private int firstDiffIndex(List<String> codes) {
        int min = Integer.MAX_VALUE;
        for (String s : codes) {
            if (s == null) return 0;
            min = Math.min(min, s.length());
        }
        for (int i = 0; i < min; i++) {
            char ch = codes.get(0).charAt(i);
            for (String s : codes) if (s.charAt(i) != ch) return i;
        }
        return min;
    }

    // ============================================================
    // (A-1) 세면기 전용 — 計 없는 선택형 슬롯(기본구성으로 세트가 산정).
    //   · 슬롯(G~M): 도기(원홀)/도기(4")/반다리/긴다리/하프고리/앙카볼트. 수량/PLT는 스킵.
    //   · 세트가 = 기본 도기(원홀>4") + 기본 다리(반다리>긴다리) + 그 외 필수부속(하프고리·앙카볼트…).
    //     비기본 도기·다리는 부속으로 보존하되 remark="대체옵션"·세트가 미포함(품목 유실 방지·req1/req2).
    //   · 품번(C)/슬롯 코드(G~M)의 "코드(설명)" → 코드/description 분리(req3).
    //   · 도자 종류만 다른 동일 품번 중복(IL672E(화려)/IL672E(클레이탄) 등)은 도기 코드 구분 글자로 분기.
    // ============================================================

    private void parseWashbasinSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> isSlotHeader(c, r));
        if (headerRow < 0) {
            logger.warn("[B][{}] 세면기 헤더(구분/품종/품번) 미발견 → 스킵", c.sheetName);
            return;
        }

        Map<Integer, String> slots = new LinkedHashMap<>();
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) continue;            // 세면기엔 計 없음
            if (isSkipWashbasinSlotLabel(label)) continue; // 수량/비고/PLT만 스킵
            slots.put(col, label);
        }

        int last = c.sheet.getLastRowNum();
        Map<Integer, String> dojaOverrides = computeDojaCodeOverrides(c, headerRow, last);
        String lastKind = null; // 품종(B) 병합셀 대비 carry-forward
        for (int r = headerRow + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) continue;
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작

            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
                if ("대리점가".equals(str(c, k, 5))) { priceRow = k; break; }
                if ("제품코드".equals(str(c, k, 5))) break;
            }

            String[] rep = splitParen(str(c, r, 2)); // C=품번(대표) — 괄호 도자명은 description으로 분리(req3)
            String repCode = rep[0];
            if (repCode == null) continue;
            String repDesc = rep[1];
            String override = dojaOverrides.get(r);
            if (override != null) repCode = override; // 도자 종류만 다른 동일 품번 → 도기 코드 구분 글자 접미

            String kindRaw = stripSpace(str(c, r, 1)); // B=품종
            if (kindRaw != null) lastKind = kindRaw;
            String kind = lastKind;
            String ksCode = normalizeCode(str(c, r, 4)); // E=KS품번

            // 현재 행에 코드가 있는 슬롯만 수집(코드 괄호 설명 분리 req3)
            List<WbSlot> present = new ArrayList<>();
            for (Map.Entry<Integer, String> slot : slots.entrySet()) {
                int col = slot.getKey();
                String[] sc = splitParen(str(c, r, col));
                if (sc[0] == null) continue;
                BigDecimal price = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, col));
                present.add(new WbSlot(slot.getValue(), sc[0], price, sc[1]));
            }

            // 기본 구성: 도기 원홀>4", 다리 반다리>긴다리 — 기본형만 세트가에 포함
            WbSlot defDogi = pickWbDefault(present, "도기", "원홀");
            WbSlot defDari = pickWbDefault(present, "다리", "반다리");

            List<VendorParsedItem> parts = new ArrayList<>();
            BigDecimal setPrice = BigDecimal.ZERO;
            for (WbSlot s : present) {
                boolean isDogi = s.label().contains("도기");
                boolean isDari = s.label().contains("다리");
                boolean included;
                String relation;
                String remark = null;
                if (isDogi) {
                    included = (s == defDogi);
                    relation = included ? VendorParsedItem.RELATION_MAIN : s.label();
                    if (!included) remark = "대체옵션";
                } else if (isDari) {
                    included = (s == defDari);
                    relation = s.label();
                    if (!included) remark = "대체옵션";
                } else {
                    included = true; // 하프고리/앙카볼트 등 필수 부속
                    relation = s.label();
                }
                if (included) setPrice = setPrice.add(s.price());
                parts.add(new VendorParsedItem(partCode(repCode, coldHot(s.code(), s.label())),
                        s.label(), null, null, relation, s.price(), remark, s.desc()));
            }

            VendorParsedItem main = new VendorParsedItem(repCode, join(kind, repCode), null, ksCode,
                    VendorParsedItem.RELATION_MAIN, setPrice, null, repDesc);
            out.add(new VendorProductSet("B", c.sheetName, kind, main, parts,
                    setPrice, false, imageKeyOf(r), false));
        }
    }

    /** 세면기 슬롯 스킵: 수량/비고/PLT만 제외(도기/다리/하프고리/앙카볼트는 실제 슬롯이라 유지). */
    private boolean isSkipWashbasinSlotLabel(String label) {
        String s = label.replace(" ", "");
        return s.contains("수량") || s.contains("비고") || s.contains("PLT");
    }

    /** 세면기 슬롯 후보 중 keyword(도기/다리)에 해당하는 것에서 prefer(원홀/반다리) 우선, 없으면 첫 항목. */
    private WbSlot pickWbDefault(List<WbSlot> present, String keyword, String prefer) {
        WbSlot first = null;
        for (WbSlot s : present) {
            if (!s.label().contains(keyword)) continue;
            if (s.label().contains(prefer)) return s;
            if (first == null) first = s;
        }
        return first;
    }

    /** 세면기 슬롯 1개(라벨/부속코드/단가/괄호설명). */
    private record WbSlot(String label, String code, BigDecimal price, String desc) {}

    // ============================================================
    // (A) 슬롯 2행형 — 소변기,수채
    // ============================================================

    private void parseSlotSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "구분".equals(str(c, r, 0))
                && "품종".equals(str(c, r, 1)) && "품번".equals(str(c, r, 2)));
        if (headerRow < 0) {
            logger.warn("[B][{}] 슬롯 헤더(구분/품종/품번) 미발견 → 스킵", c.sheetName);
            return;
        }

        // 헤더에서 슬롯/計 컬럼 동적 인식 (슬롯 라벨은 내부공백 제거하여 정규화)
        Map<Integer, String> slots = new LinkedHashMap<>();
        int totalCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (isSkipSlotLabel(label)) continue;
            slots.put(col, label);
        }
        boolean selectable = (totalCol < 0); // 計 없음 → 세면기(선택형 슬롯, 기본구성으로 세트가 산정)

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

            // 현재 행에 실제로 코드가 있는 슬롯만 수집
            List<Slot> present = new ArrayList<>();
            for (Map.Entry<Integer, String> slot : slots.entrySet()) {
                int col = slot.getKey();
                String slotLabel = slot.getValue();
                String code = normalizeCode(str(c, r, col));
                if (code == null) continue;
                BigDecimal price = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, col));
                present.add(new Slot(slotLabel, code, price));
            }

            if (selectable) {
                buildSelectableSet(c, r, repCode, kind, ksCode, present, out);
            } else {
                buildSlotTotalSet(c, r, priceRow, totalCol, repCode, kind, ksCode, present, out);
            }
        }
    }

    /** 양변기/소변기: 計(합계) 컬럼으로 세트가 확정. 부속 품번 = 대표품번_부속코드. */
    private void buildSlotTotalSet(Ctx c, int r, int priceRow, int totalCol,
                                   String repCode, String kind, String ksCode,
                                   List<Slot> present, List<VendorProductSet> out) {
        List<VendorParsedItem> parts = new ArrayList<>();
        BigDecimal partSum = BigDecimal.ZERO;
        for (Slot s : present) {
            partSum = partSum.add(s.price());
            String relation = s.label().startsWith("도기") ? VendorParsedItem.RELATION_MAIN : s.label();
            parts.add(part(repCode, s, relation, null));
        }

        BigDecimal setPrice = (priceRow >= 0 && totalCol >= 0) ? dec(c, priceRow, totalCol) : null;
        if (setPrice != null && partSum.compareTo(setPrice) != 0) {
            logger.warn("[B][{}] 計≠부속합 (rep={}, 計={}, 합={})", c.sheetName, repCode, setPrice, partSum);
        }

        String repName = join(kind, repCode);
        if (setPrice == null) repName = repName + " (가격없음)"; // D8
        VendorParsedItem main = new VendorParsedItem(repCode, repName, null, ksCode,
                VendorParsedItem.RELATION_MAIN, setPrice != null ? setPrice : BigDecimal.ZERO, null);
        out.add(new VendorProductSet("B", c.sheetName, kind, main, parts,
                setPrice, false, imageKeyOf(r), false));
    }

    /**
     * 세면기: 計 컬럼이 없는 선택형 슬롯이지만, "기본 구성"을 정해 세트가를 산정한다.
     * - 도기: 원홀 우선(없으면 존재하는 도기) / 다리: 반다리 우선(없으면 존재하는 다리) → 기본형만 세트가 포함
     * - 그 외 슬롯(하프고리/앙카볼트 등): 필수 부속 → 세트가 포함
     * - 비기본 도기·다리(4"/긴다리): 부속으로 저장하되 remark="대체옵션", 세트가 미포함
     */
    private void buildSelectableSet(Ctx c, int r, String repCode, String kind, String ksCode,
                                    List<Slot> present, List<VendorProductSet> out) {
        Slot defDogi = pickDefault(present, "도기", "원홀");
        Slot defDari = pickDefault(present, "다리", "반다리");

        List<VendorParsedItem> parts = new ArrayList<>();
        BigDecimal setPrice = BigDecimal.ZERO;
        for (Slot s : present) {
            boolean isDogi = s.label().contains("도기");
            boolean isDari = s.label().contains("다리");
            boolean included;
            String relation;
            String remark = null;
            if (isDogi) {
                included = (s == defDogi);
                relation = included ? VendorParsedItem.RELATION_MAIN : s.label();
                if (!included) remark = "대체옵션";
            } else if (isDari) {
                included = (s == defDari);
                relation = s.label();
                if (!included) remark = "대체옵션";
            } else {
                included = true; // 필수 부속
                relation = s.label();
            }
            if (included) setPrice = setPrice.add(s.price());
            parts.add(part(repCode, s, relation, remark));
        }

        VendorParsedItem main = new VendorParsedItem(repCode, join(kind, repCode), null, ksCode,
                VendorParsedItem.RELATION_MAIN, setPrice, null);
        out.add(new VendorProductSet("B", c.sheetName, kind, main, parts,
                setPrice, false, imageKeyOf(r), false));
    }

    /** present 슬롯 중 keyword(도기/다리)에 해당하는 것에서 prefer(원홀/반다리) 우선, 없으면 첫 항목. */
    private Slot pickDefault(List<Slot> present, String keyword, String prefer) {
        Slot first = null;
        for (Slot s : present) {
            if (!s.label().contains(keyword)) continue;
            if (s.label().contains(prefer)) return s;
            if (first == null) first = s;
        }
        return first;
    }

    /** 슬롯 1개 → 부속 품목. 품번 = 대표품번_부속코드(냉수c/온수h 구분 반영). */
    private VendorParsedItem part(String repCode, Slot s, String relation, String remark) {
        String detail = coldHot(s.code(), s.label());
        return new VendorParsedItem(partCode(repCode, detail), s.label(), null, null,
                relation, s.price(), remark);
    }

    /** 슬롯 1개(라벨/부속코드/단가). */
    private record Slot(String label, String code, BigDecimal price) {}

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
            String accCode = normalizeCode(str(c, r, 5));

            // 다음 행 = 대리점가(D=대리점가), E/F=단가
            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 2, last); k++) {
                if ("대리점가".equals(str(c, k, 3))) { priceRow = k; break; }
            }
            BigDecimal dogiPrice = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, 4));
            BigDecimal partPrice = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, 5));

            List<VendorParsedItem> parts = new ArrayList<>();
            if (dogiCode != null) parts.add(new VendorParsedItem(
                    partCode(repCode, coldHot(dogiCode, slotEName)), slotEName, null, null,
                    VendorParsedItem.RELATION_MAIN, dogiPrice, null));
            if (accCode != null) parts.add(new VendorParsedItem(
                    partCode(repCode, coldHot(accCode, slotFName)), slotFName, null, null,
                    slotFName, partPrice, null));

            BigDecimal setPrice = dogiPrice.add(partPrice);
            VendorParsedItem main = new VendorParsedItem(repCode, repCode,
                    null, null, VendorParsedItem.RELATION_MAIN, setPrice, null);
            out.add(new VendorProductSet("B", c.sheetName, null, main, parts,
                    setPrice, false, imageKeyOf(r), false));
        }
    }

    // ============================================================
    // (C-1) 악세사리 — 세트 구간 + 단일품 구간 혼재
    //   세트: G열="SET" 대표행 + 부속행들(A·B·C 빈칸, H=가). 세트가 = 대표행 H.
    //   단일품: 그 외 모든 행 = 독립 제품(부속 없음). A/B=분류, F=품명, G=규격, H=가.
    // ============================================================

    private void parseHeaderTotalSetSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "품번".equals(noSpace(str(c, r, 3)))
                && containsPrice(str(c, r, 7)));
        if (headerRow < 0) {
            logger.warn("[B][{}] 악세사리 헤더 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();
        VendorParsedItem mainItem = null;          // 열린 세트의 대표
        List<VendorParsedItem> parts = null;
        String setCat = null;
        BigDecimal setPrice = null;
        int mainRow = -1;
        boolean inSet = false;
        String catA = null, catB = null;           // 단일품 구간 분류 carry-forward
        String lastName = null;                     // 따옴표(ditto) 품명 → 직전 실품명

        for (int r = headerRow + 1; r <= last; r++) {
            String code = normalizeCode(str(c, r, 3)); // D=품번
            if (code == null) continue;

            String aRaw = stripSpace(str(c, r, 0));
            String bRaw = stripSpace(str(c, r, 1));
            String cRaw = stripSpace(str(c, r, 2));
            String spec = stripSpace(str(c, r, 6));     // G=규격(또는 "SET")
            BigDecimal price = nz(dec(c, r, 7));        // H=대리점가
            String name = resolveDitto(stripSpace(str(c, r, 5)), lastName); // F=품명
            if (name != null) lastName = name;

            boolean isSetRep = spec != null && spec.replace(" ", "").equalsIgnoreCase("SET");
            if (isSetRep) {                              // ── 세트 대표행(G=SET) ──
                if (inSet) flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow);
                setCat = orDefault(bRaw, aRaw);
                setPrice = price;
                mainItem = new VendorParsedItem(code, join(setCat, orDefault(name, code)), null, null,
                        VendorParsedItem.RELATION_MAIN, setPrice, null);
                parts = new ArrayList<>();
                mainRow = r;
                inSet = true;
                if (aRaw != null) { catA = aRaw; catB = null; }
                if (bRaw != null) catB = bRaw;
                continue;
            }

            boolean isSetPart = inSet && aRaw == null && bRaw == null && cRaw == null;
            if (isSetPart) {                             // ── 세트 부속행 ──
                String pName = orDefault(name, code);
                parts.add(new VendorParsedItem(partCode(mainItem.productCode(), coldHot(code, pName)),
                        pName, null, null, pName, price, null));
                continue;
            }

            // ── 단일품 구간(세트 종료 포함) ──
            if (inSet) { flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow); inSet = false; }
            if (aRaw != null) { catA = aRaw; catB = null; }
            if (bRaw != null) catB = bRaw;
            String catSmall = orDefault(catB, catA);
            VendorParsedItem single = new VendorParsedItem(code, orDefault(name, code), null, null,
                    VendorParsedItem.RELATION_MAIN, price, spec);
            out.add(new VendorProductSet("B", c.sheetName, catSmall, single,
                    new ArrayList<>(), price, false, imageKeyOf(r), false));
        }
        if (inSet) flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow);
    }

    private void flushAccSet(List<VendorProductSet> out, Ctx c, VendorParsedItem mainItem,
                             List<VendorParsedItem> parts, String setCat, BigDecimal setPrice, int mainRow) {
        if (mainItem == null) return;
        out.add(new VendorProductSet("B", c.sheetName, setCat, mainItem,
                parts != null ? parts : new ArrayList<>(), setPrice, false, imageKeyOf(mainRow), false));
    }

    // ============================================================
    // (C-2) 소계 세트형 — 소계행으로 세트 종료 (국산 부속 기준 / 수전 부속(세트))
    //   대표행(A 있음) + 부속행(A 없음) + 소계행(C/B="소계")
    //   국산부속: code=C(2), name=B(1), price=G(6) | 수전부속: code=B(1), name=A/B, price=F(5)
    // ============================================================

    private void parseSubtotalSetSheet(Ctx c, List<VendorProductSet> out) {
        boolean korParts = c.sheetName.replaceAll("\\s", "").contains("국산부속");
        if (!korParts) { parseSujeonPartsSheet(c, out); return; } // 수전부속은 전용 처리(세트/플랫 분리)
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
                parts.add(new VendorParsedItem(partCode(mainItem.productCode(), coldHot(code, name)),
                        name, null, null, VendorParsedItem.RELATION_ACCESSORY, price, null));
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
    // (C-3) 수전 부속(세트) — 세트 구간 + 플랫 단일 구간 혼재
    //   컬럼: A=품명 B=품번 C=제품코드 F=단가. 소계행(C="소계", F=세트단가)으로 세트 종료.
    //   - 소계로 닫히는 블록 = 세트(대표=첫 멤버, 나머지=부속). 소계 없는 블록 = 각 행이 독립 부속.
    //   - 품번(B) 첫 토큰이 품번패턴([영문]시작·숫자끝)이면 코드로, 아니면 제품코드(C)로 대체.
    //     원본 B열은 description에 보존.
    //   - 하단 "니쁠" 서브테이블(C=제품코드, D=단가, E=규격)은 별도 인식.
    // ============================================================

    private void parseSujeonPartsSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> {
            String a = noSpace(str(c, r, 0));
            return a != null && a.contains("품명");
        });
        if (headerRow < 0) {
            logger.warn("[B][{}] 수전부속 헤더(품명) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        List<SjMember> buf = new ArrayList<>();
        String blockCat = null;
        boolean nipple = false;

        for (int r = headerRow + 1; r <= last; r++) {
            String cText = noSpace(str(c, r, 2));

            // 니쁠 서브헤더(제품코드/단가/규격) 등장 → 이후 별도 레이아웃
            if ("제품코드".equals(cText)) {
                flushSjFlat(out, c, buf, blockCat); buf.clear(); blockCat = null;
                nipple = true;
                continue;
            }
            if (nipple) {
                String code = normalizeCode(str(c, r, 2)); // C=제품코드
                if (code == null) continue;
                BigDecimal price = nz(dec(c, r, 3));        // D=단가
                String nm = join(orDefault(stripSpace(str(c, r, 1)), "니쁠"), stripSpace(str(c, r, 4)));
                out.add(sjSingle(c, "니쁠", code, orDefault(nm, code), price, stripSpace(str(c, r, 1)), r));
                continue;
            }

            // 소계행 → 현재 블록을 세트로 확정
            if (cText != null && cText.contains("소계")) {
                flushSjSet(out, c, buf, blockCat, nz(dec(c, r, 5)));
                buf.clear(); blockCat = null;
                continue;
            }

            String aRaw = stripSpace(str(c, r, 0));
            if (aRaw != null && aRaw.startsWith("*")) continue; // 각주

            String bRaw = str(c, r, 1);
            String[] cd = resolveSujeonCode(bRaw, str(c, r, 2));
            String code = cd[0];
            if (code == null) continue;
            BigDecimal price = nz(dec(c, r, 5));

            if (aRaw != null) { // 새 블록 시작 → 이전(소계 없던) 블록은 플랫 단일로 방출
                flushSjFlat(out, c, buf, blockCat); buf.clear();
                blockCat = aRaw;
            }
            String name = orDefault(join(blockCat, stripSpace(bRaw)), code);
            buf.add(new SjMember(code, name, price, cd[1], r));
        }
        flushSjFlat(out, c, buf, blockCat); // 잔여(소계 없음) → 플랫
    }

    /** 소계로 닫힌 블록 = 세트(대표=첫 멤버, 나머지=부속, 세트가=소계). */
    private void flushSjSet(List<VendorProductSet> out, Ctx c, List<SjMember> buf,
                            String blockCat, BigDecimal subtotal) {
        if (buf.isEmpty()) return;
        SjMember rep = buf.get(0);
        List<VendorParsedItem> parts = new ArrayList<>();
        for (int i = 1; i < buf.size(); i++) {
            SjMember m = buf.get(i);
            parts.add(new VendorParsedItem(partCode(rep.code(), m.code()), m.name(), null, null,
                    VendorParsedItem.RELATION_ACCESSORY, m.price(), null, m.descr()));
        }
        VendorParsedItem main = new VendorParsedItem(rep.code(), rep.name(), null, null,
                VendorParsedItem.RELATION_MAIN, subtotal, null, rep.descr());
        out.add(new VendorProductSet("B", c.sheetName, blockCat, main, parts,
                subtotal, false, imageKeyOf(rep.row()), false));
    }

    /** 소계 없이 끝난 블록 = 각 멤버가 독립 단일 제품. */
    private void flushSjFlat(List<VendorProductSet> out, Ctx c, List<SjMember> buf, String blockCat) {
        for (SjMember m : buf) {
            out.add(sjSingle(c, blockCat, m.code(), m.name(), m.price(), m.descr(), m.row()));
        }
    }

    private VendorProductSet sjSingle(Ctx c, String catSmall, String code, String name,
                                      BigDecimal price, String descr, int row) {
        VendorParsedItem main = new VendorParsedItem(code, name, null, null,
                VendorParsedItem.RELATION_MAIN, price, null, descr);
        return new VendorProductSet("B", c.sheetName, catSmall, main,
                new ArrayList<>(), price, false, imageKeyOf(row), false);
    }

    /**
     * 수전부속 코드 결정:
     * - 품번(B) 첫 토큰이 품번패턴(영문 시작, 끝은 숫자 또는 영문)이면 그 토큰을 코드로, B의 <b>나머지</b> 문자열을 description으로.
     * - 맞지 않으면(1.5m, 65MM, 한글 등) 제품코드(C)를 코드로, B <b>전체</b>를 description으로.
     * 반환[0]=code, [1]=description.
     */
    private String[] resolveSujeonCode(String bRaw, String cRaw) {
        String bClean = stripSpace(bRaw);
        String token = normalizeCode(firstToken(bRaw));
        if (token != null && token.matches("^[A-Za-z].*[A-Za-z0-9]$")) {
            String remainder = null;
            if (bClean != null && bClean.length() > token.length() && bClean.startsWith(token)) {
                remainder = bClean.substring(token.length()).trim();
                if (remainder.isEmpty()) remainder = null;
            }
            return new String[]{ token, remainder };       // 첫 토큰=코드, 나머지 B=description
        }
        return new String[]{ normalizeCode(cRaw), bClean }; // 제품코드(C), B 전체=description
    }

    private String firstToken(String s) {
        if (s == null) return null;
        String t = s.replace(' ', ' ').trim();
        if (t.isEmpty()) return null;
        int i = 0;
        while (i < t.length() && !Character.isWhitespace(t.charAt(i))) i++;
        return t.substring(0, i);
    }

    /** 수전부속 버퍼 멤버(코드/표시명/단가/원본B/행). */
    private record SjMember(String code, String name, BigDecimal price, String descr, int row) {}

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

    /**
     * 부속 품번 = 대표품번 + '_' + 부속코드 (A사 master-detail 방식 차용).
     * 같은 부속이 세트마다 고유 품번을 가지며, 서비스가 '_' 기준 masterCode/detailCode로 분리한다.
     */
    private String partCode(String masterCode, String detailCode) {
        if (isBlank(masterCode)) return detailCode;
        if (isBlank(detailCode)) return masterCode;
        return masterCode + "_" + detailCode;
    }

    /**
     * 동일 코드가 냉수용/온수용으로 중복될 때 코드에 c/h를 부여해 구분(관계 유실 방지).
     * 품명에 "냉수"면 c, "온수"면 h. (이미 해당 접미사면 그대로)
     */
    private String coldHot(String code, String name) {
        if (code == null || name == null) return code;
        String n = name.replaceAll("\\s", "");
        String cc = code.replaceAll("\\s", "");
        // 코드에 이미 냉/온수 표식이 있으면(접미사 c/h 또는 한글) 중복 부여하지 않음
        if (n.contains("냉수") && !cc.endsWith("c") && !cc.contains("냉수")) return code + "c";
        if (n.contains("온수") && !cc.endsWith("h") && !cc.contains("온수")) return code + "h";
        return code;
    }

    /** 슬롯/부속 라벨 정규화: 글자 사이 공백까지 제거("긴 다 리"→"긴다리", "매립 감지기"→"매립감지기"). */
    private String normLabel(String s) {
        return noSpace(s);
    }

    /** 따옴표(ditto, 〃) 셀이면 직전 실품명으로 대체. lastName이 없으면 원본 유지. */
    private String resolveDitto(String name, String lastName) {
        if (isDitto(name) && lastName != null) return lastName;
        return name;
    }

    private boolean isDitto(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.equals("\"") || t.equals("“") || t.equals("”") || t.equals("″")
                || t.equals("〃") || t.equals("''") || t.equals("\"\"");
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
