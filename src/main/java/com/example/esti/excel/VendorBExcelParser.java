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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B사(이누스) 단가표 파서 — 멀티 시트. 시트명으로 4개 양식 패밀리를 판별해 전용 파서로 분기한다(P3).
 *
 * <ul>
 *   <li><b>슬롯 2행형</b>(양변기 / 소변기,수채 / 세면기): 제품코드행 + 대리점가행 한 쌍.
 *       헤더의 슬롯 라벨(G~M)을 동적 인식해 도기=MAIN, 나머지=슬롯 라벨 relation(D9).
 *       計 컬럼이 있으면 세트가, 세면기는 計 없음 → 선택형 세트(D10).</li>
 *   <li><b>갈라시아 4행형</b>: 제품코드행 + 대리점가행 + 합계행 + 소비자단가행. 슬롯 E=도기, F=부속.</li>
 *   <li><b>소계 세트형</b>(악세사리 / 수전금구(국산·OEM 부속 기준)):
 *       대표행 + 부속행들 + (소계행). 대표품목 + 부속 + 합계.</li>
 *   <li><b>수전부속 3-시트</b>(§11): 분계표=수전금구 병합 뷰(A-5) /
 *       수전 부속(세트)(C-3)·부속 단가표(C-4)=부속 카탈로그(대분류 수전부속, priceBasis=시트명).</li>
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
                    case URINAL_SINK -> parseUrinalSinkSheet(ctx, result);
                    case BIDET_ETC  -> parseBidetEtcSheet(ctx, result);
                    case FAUCET_GENERAL -> parseFaucetGeneralSheet(ctx, result);
                    case FAUCET_PARTS   -> parseFaucetPartsSheet(ctx, result);
                    case BREAKDOWN      -> parseBreakdownSheet(ctx, result);
                    case FITTING_SET    -> parseFittingSetSheet(ctx, result);
                    case FITTING_PRICE  -> parseFittingPriceSheet(ctx, result);
                    case FITTING_OEM    -> parseOemFittingSheet(ctx, result);
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

    private enum Family { TOILET, WASHBASIN, URINAL_SINK, BIDET_ETC, FAUCET_GENERAL, FAUCET_PARTS,
        BREAKDOWN, FITTING_SET, FITTING_PRICE, FITTING_OEM, SLOT, GALAXIA, SET_TOTAL, SET_SUBTOTAL, SINGLE }

    private Family family(String sheetName) {
        String n = sheetName.replaceAll("\\s", "");
        if (n.equals("양변기")) return Family.TOILET;            // 양변기 전용 경로(서브테이블별 헤더/품종 병합 처리)
        if (n.equals("세면기")) return Family.WASHBASIN;          // 세면기 전용 경로(선택형 기본구성·도자 분기·괄호 설명 분리)
        if (n.contains("소변기")) return Family.URINAL_SINK;       // 소변기·수채 전용 경로(서브테이블별 헤더/대분류 분리)
        if (n.contains("비데")) return Family.BIDET_ETC;           // 비데·기타 전용 경로(서브테이블별 헤더/대분류 분리)
        // 수전금구 3-시트: 대분류 통합("수전금구")·가격은 price_basis(시트명)로 분리(§10).
        //   "수전금구" → 일반(단일 제품) / "수전금구(국산 부속 기준)"·"수전금구(OEM 부속 기준)" → 소계 세트형.
        //   반드시 아래 국산부속 일반분기보다 먼저 판별(수전금구 부속기준이 SET_SUBTOTAL로 새지 않게).
        if (n.contains("수전금구")) return n.contains("부속") ? Family.FAUCET_PARTS : Family.FAUCET_GENERAL;
        // 수전부속 3-시트(§11): 분계표=수전금구 병합 뷰 / 수전 부속(세트)·부속 단가표=부속 카탈로그(대분류 수전부속).
        //   "부속단가"는 "악세사리단가표"와 겹치지 않게 악세사리 분기보다 앞이어도 무방하나, 명시적으로 여기 배치.
        if (n.contains("분계표")) return Family.BREAKDOWN;
        if (n.contains("신규") && n.contains("부속단가")) return Family.FITTING_OEM; // 신규 OEM 부속 단가표(§11-1) — 일반 부속단가보다 먼저
        if (n.contains("부속단가")) return Family.FITTING_PRICE;
        if (n.contains("수전부속")) return Family.FITTING_SET;
        if (n.contains("갈라시아")) return Family.GALAXIA;
        if (n.contains("악세사리") || n.contains("악세서리")) return Family.SET_TOTAL;
        if (n.contains("국산부속")) return Family.SET_SUBTOTAL;
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
        SlotHeaderCols hc = readSlotHeader(c, firstHeader, slots);
        String lastKind = null;    // 품종(B) 병합셀 → 직전 품종 유지(req1)
        String prevRepCode = null; // 직전 제품의 base 품번 — 도기수로/사출수로 충돌 판별용

        int last = c.sheet.getLastRowNum();
        // 도자 종류만 다른 동일 품번 중복(IC703E(성오도자)/IC703E(구륙도자) 등)을 도기 코드 구분 글자로 분기(row→최종품번)
        Map<Integer, String> dojaOverrides = computeDojaCodeOverrides(c, firstHeader, last);
        for (int r = firstHeader + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) {                 // 새 서브테이블 헤더 → 슬롯 라벨 갱신(req2)
                hc = readSlotHeader(c, r, slots);
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

            BigDecimal setPrice = (priceRow >= 0 && hc.totalCol() >= 0) ? dec(c, priceRow, hc.totalCol()) : null;
            if (setPrice != null && partSum.compareTo(setPrice) != 0) {
                logger.warn("[B][{}] 計≠부속합 (rep={}, 計={}, 합={})", c.sheetName, repCode, setPrice, partSum);
            }

            // 비고(탱크뚜껑 코드/인치 구분 등 구성 정보)는 제품코드행·대리점가행 양쪽에 나뉨 → 병합해 description(C-2 결정 9)
            if (hc.noteCol() >= 0) {
                String note = joinNotes(stripSpace(str(c, r, hc.noteCol())),
                        priceRow >= 0 ? stripSpace(str(c, priceRow, hc.noteCol())) : null);
                repDesc = joinNotes(repDesc, note);
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
     * 양변기 헤더행에서 슬롯(col→라벨)을 채우고 計·비고 컬럼 인덱스를 반환(없으면 -1). 슬롯 라벨은 내부공백 제거 정규화.
     * 투피스 구간은 G슬롯 라벨이 "하부"(도기 하부)라 일반 슬롯과 달리 스킵하면 안 된다
     * (공용 {@code isSkipSlotLabel}은 수량 서브헤더용 "하부/상부"를 스킵하므로 여기선 쓰지 않는다).
     */
    private SlotHeaderCols readSlotHeader(Ctx c, int headerRow, Map<Integer, String> slots) {
        slots.clear();
        int totalCol = -1, noteCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (label.replace(" ", "").contains("비고")) { noteCol = col; continue; } // C-2: 비고 → description 수집
            if (isSkipToiletSlotLabel(label)) continue;
            slots.put(col, label);
        }
        return new SlotHeaderCols(totalCol, noteCol);
    }

    private record SlotHeaderCols(int totalCol, int noteCol) {}

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
        int noteCol = -1; // 비고(P) → description 수집(C-2 결정 10)
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) continue;            // 세면기엔 計 없음
            if (label.replace(" ", "").contains("비고")) { noteCol = col; continue; }
            if (isSkipWashbasinSlotLabel(label)) continue; // 수량/PLT 스킵
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

            // 비고(언더카운터/세트 판매 기준 등)는 제품코드행·대리점가행 양쪽 → 병합해 description(C-2 결정 10)
            if (noteCol >= 0) {
                String note = joinNotes(stripSpace(str(c, r, noteCol)),
                        priceRow >= 0 ? stripSpace(str(c, priceRow, noteCol)) : null);
                repDesc = joinNotes(repDesc, note);
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
    // (A-2) 소변기·수채 전용 — 서브테이블별 헤더/대분류 분리
    // ============================================================

    /**
     * 소변기·수채 시트 전용. 한 시트에 "3. 소변기"·"4. 소제싱크(수채)" 두 서브테이블이 세로로 쌓여 있고,
     * 서브테이블마다 슬롯 구성과 計 컬럼 위치가 다르다(소변기 計=M / 수채 計=J, 슬롯도 스퍼드·후렌지… vs 수채가랑·수채트랩).
     *
     * <ul>
     *   <li>req1 — 대분류 분리: 헤더(구분/품종/품번)를 만날 때마다 새 서브테이블로 보고, 대분류를 시트명 콤마 분리값으로
     *       순서대로 부여(소변기 / 수채). 더는 categoryLarge에 시트명("소변기, 수채")을 통째로 저장하지 않는다.</li>
     *   <li>req2 — 부속/단가 정확화: 헤더를 만날 때마다 슬롯 라벨/計 컬럼을 재인식(가장 가까운 위 헤더 기준)하여,
     *       수채 서브테이블 부속(수채가랑/수채트랩)·단가·計(J)가 소변기 헤더(스퍼드…/計 M)에 오염되지 않게 한다.</li>
     * </ul>
     */
    private void parseUrinalSinkSheet(Ctx c, List<VendorProductSet> out) {
        List<String> categories = splitSheetCategories(c.sheetName);

        Map<Integer, String> slots = new LinkedHashMap<>();
        SlotHeaderCols hc = new SlotHeaderCols(-1, -1);
        int catIdx = -1;
        String currentCat = c.sheetName; // 첫 헤더 전 안전 기본값
        String carryKind = null;         // 서브테이블 내 품종(B) 병합셀 carry-forward

        int last = c.sheet.getLastRowNum();
        for (int r = 0; r <= last; r++) {
            if (isSlotHeader(c, r)) {                       // 새 서브테이블 시작 → 슬롯/計/대분류 갱신
                hc = readUrinalSlotHeader(c, r, slots);
                catIdx++;
                currentCat = catIdx < categories.size() ? categories.get(catIdx) : c.sheetName;
                carryKind = null;
                continue;
            }
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작
            if (slots.isEmpty()) continue;                 // 헤더 전 잡행 방어

            int priceRow = -1;
            for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
                if ("대리점가".equals(str(c, k, 5))) { priceRow = k; break; }
                if ("제품코드".equals(str(c, k, 5))) break; // 다음 제품행 만나면 중단
            }

            String repCode = normalizeCode(str(c, r, 2)); // C=품번(대표)
            if (repCode == null) continue;
            String kind = stripSpace(str(c, r, 1));        // B=품종(병합 하위는 빈칸 → carry-forward)
            if (kind != null) carryKind = kind; else kind = carryKind;
            String ksCode = normalizeCode(str(c, r, 4));   // E=KS품번

            List<Slot> present = new ArrayList<>();
            List<String> slotNotes = new ArrayList<>(); // 코드 아닌 설명 텍스트(예: "후렌지/스프레다 포함")
            for (Map.Entry<Integer, String> slot : slots.entrySet()) {
                int col = slot.getKey();
                String raw = str(c, r, col);
                String code = normalizeCode(raw);
                if (code == null) continue;
                if (!isCodeLike(code)) {            // 코드패턴 아님(한글 설명) → 부속 아님, description으로
                    String note = stripSpace(raw);
                    if (note != null) slotNotes.add(note);
                    continue;
                }
                BigDecimal price = priceRow < 0 ? BigDecimal.ZERO : nz(dec(c, priceRow, col));
                present.add(new Slot(slot.getValue(), code, price));
            }
            String desc = slotNotes.isEmpty() ? null : String.join(" / ", slotNotes);

            // 비고(감지기 코드/세트 판매 안내 등)는 제품코드행·대리점가행 양쪽 → 병합해 description(C-2 결정 11)
            if (hc.noteCol() >= 0) {
                String note = joinNotes(stripSpace(str(c, r, hc.noteCol())),
                        priceRow >= 0 ? stripSpace(str(c, priceRow, hc.noteCol())) : null);
                desc = joinNotes(desc, note);
            }

            buildUrinalSinkSet(c, r, priceRow, hc.totalCol(), currentCat, repCode, kind, ksCode, present, desc, out);
        }
    }

    /** 소변기·수채 헤더행에서 슬롯(col→라벨)을 채우고 計·비고 컬럼 인덱스를 반환. 수량/PLT는 스킵. */
    private SlotHeaderCols readUrinalSlotHeader(Ctx c, int headerRow, Map<Integer, String> slots) {
        slots.clear();
        int totalCol = -1, noteCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (label.replace(" ", "").contains("비고")) { noteCol = col; continue; } // C-2: 비고 → description 수집
            if (isSkipSlotLabel(label)) continue;
            slots.put(col, label);
        }
        return new SlotHeaderCols(totalCol, noteCol);
    }

    /** {@code buildSlotTotalSet}와 동일하나 categoryLarge를 서브테이블별 대분류로 받고, 비코드 슬롯 설명을 description에 보존. */
    private void buildUrinalSinkSet(Ctx c, int r, int priceRow, int totalCol, String categoryLarge,
                                    String repCode, String kind, String ksCode,
                                    List<Slot> present, String description, List<VendorProductSet> out) {
        List<VendorParsedItem> parts = new ArrayList<>();
        BigDecimal partSum = BigDecimal.ZERO;
        for (Slot s : present) {
            partSum = partSum.add(s.price());
            String relation = s.label().startsWith("도기") ? VendorParsedItem.RELATION_MAIN : s.label();
            parts.add(part(repCode, s, relation, null));
        }

        BigDecimal setPrice = (priceRow >= 0 && totalCol >= 0) ? dec(c, priceRow, totalCol) : null;
        if (setPrice != null && partSum.compareTo(setPrice) != 0) {
            logger.warn("[B][{}] 計≠부속합 (cat={}, rep={}, 計={}, 합={})",
                    c.sheetName, categoryLarge, repCode, setPrice, partSum);
        }

        String repName = join(kind, repCode);
        if (setPrice == null) repName = repName + " (가격없음)";
        VendorParsedItem main = new VendorParsedItem(repCode, repName, null, ksCode,
                VendorParsedItem.RELATION_MAIN, setPrice != null ? setPrice : BigDecimal.ZERO, null, description);
        out.add(new VendorProductSet("B", categoryLarge, kind, main, parts,
                setPrice, false, imageKeyOf(r), false));
    }

    /** 부속 슬롯 값이 코드 패턴인지(한글 설명 텍스트가 아닌지). B사 부속코드는 영숫자뿐이라 한글이 있으면 코드가 아니다. */
    private boolean isCodeLike(String s) {
        if (s == null) return false;
        return s.chars().noneMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3); // 한글 음절 없음
    }

    /** 시트명을 콤마/슬래시류로 분리해 대분류 후보 목록 반환("소변기, 수채" → [소변기, 수채]). */
    private List<String> splitSheetCategories(String sheetName) {
        List<String> out = new ArrayList<>();
        if (sheetName != null) {
            for (String p : sheetName.split("[,，、/]")) {
                String t = stripSpace(p);
                if (t != null) out.add(t);
            }
        }
        return out;
    }

    // ============================================================
    // (A-3) 비데·기타 전용 — 서브테이블별 헤더/대분류 분리
    //   한 시트에 "5. 비데"·"6. 기타" 두 서브테이블이 세로로 쌓여 있고 컬럼 배치가 서로 다르다
    //   (비데: 품번=B·스펙 없음 / 기타: 품번=D·스펙=E). 부속 없는 단일행 제품이다.
    //
    //   <ul>
    //     <li>req1 — 대분류 분리: 헤더(품번+대리점가)를 만날 때마다 새 서브테이블로 보고 컬럼을 재인식,
    //         대분류를 시트명 콤마 분리값으로 순서대로 부여(비데 / 기타).</li>
    //     <li>req2 — 비데 소분류=비데 고정(비데 표는 품종 컬럼이 비어 있음).</li>
    //     <li>req3 — 기타: 전기/배터리처럼 품번(D)이 같고 제품코드(G)만 다른 변형(품번 병합으로 아래 행 D가 빈칸)은
    //         품번 뒤에 제품코드의 구분글자(e/b 등)를 붙여 두 행 모두 유실 없이 보존.</li>
    //     <li>req4 — 기타: 스펙(E)을 제품명 뒤 괄호로 덧붙이고, 비고(I)는 description 컬럼에 저장.</li>
    //   </ul>
    // ============================================================

    private void parseBidetEtcSheet(Ctx c, List<VendorProductSet> out) {
        List<String> categories = splitSheetCategories(c.sheetName);
        Map<Integer, String> codeOverrides = computeBidetCodeOverrides(c); // 기타 변형행 품번 접미(req3)

        BidetCols cols = null;
        int catIdx = -1;
        String currentCat = c.sheetName; // 첫 헤더 전 안전 기본값
        String carryKind = null;         // 기타 품종(A) 병합셀 carry-forward
        String carryBase = null;         // 기타 품번(D) 병합셀 carry-forward(변형행)

        int last = c.sheet.getLastRowNum();
        for (int r = 0; r <= last; r++) {
            BidetCols detected = detectBidetHeader(c, r);
            if (detected != null) {                          // 새 서브테이블 시작 → 컬럼/대분류 갱신
                cols = detected;
                catIdx++;
                currentCat = catIdx < categories.size() ? categories.get(catIdx) : c.sheetName;
                carryKind = null;
                carryBase = null;
                continue;
            }
            if (cols == null) continue;

            boolean etc = cols.specCol >= 0;                 // 스펙 컬럼 존재 → 기타 서브테이블
            String ownCode = normalizeCode(str(c, r, cols.codeCol));
            BigDecimal price = cols.priceCol >= 0 ? dec(c, r, cols.priceCol) : null;
            String prod = cols.productCodeCol >= 0 ? normalizeCode(str(c, r, cols.productCodeCol)) : null;
            if (ownCode == null && prod == null && price == null) continue; // 병합 잔여/꼬리 빈 행 방어

            if (ownCode != null) carryBase = ownCode;
            String baseCode = ownCode != null ? ownCode : (etc ? carryBase : null); // 기타 변형행은 직전 품번 이어쓰기
            if (baseCode == null || isHeaderLikeCode(baseCode)) continue;

            String remark = cols.remarkCol >= 0 ? stripSpace(str(c, r, cols.remarkCol)) : null;
            String kindRaw = cols.kindCol >= 0 ? stripSpace(str(c, r, cols.kindCol)) : null;
            if (kindRaw != null) carryKind = kindRaw;

            if (etc) {
                String code = codeOverrides.getOrDefault(r, baseCode); // 변형이면 e/b 접미(req3)
                String spec = cols.specCol >= 0 ? stripSpace(str(c, r, cols.specCol)) : null;
                // 제품명 = 소분류(품종) + (스펙) + 품목코드
                String name = carryKind;
                if (spec != null) name = join(name, "(" + spec + ")"); // 스펙 괄호 부기(req4)
                name = join(name, baseCode);
                if (price == null) name = name + " (가격없음)";
                VendorParsedItem main = new VendorParsedItem(code, name, null, null,
                        VendorParsedItem.RELATION_MAIN, nz(price), null, remark); // 비고→description(req4)
                out.add(new VendorProductSet("B", currentCat, carryKind, main,
                        new ArrayList<>(), nz(price), false, imageKeyOf(c.sheetName, r), false)); // 대분류≠시트명 → 시트명 키
            } else {
                String name = join("비데", baseCode);                   // 제품명 앞에 '비데' 부기
                if (price == null) name = name + " (가격없음)";
                VendorParsedItem main = new VendorParsedItem(baseCode, name, null, null,
                        VendorParsedItem.RELATION_MAIN, nz(price), null, remark);   // 비데 비고→description
                out.add(new VendorProductSet("B", currentCat, currentCat, main,     // 소분류=비데(req2)
                        new ArrayList<>(), nz(price), false, imageKeyOf(c.sheetName, r), false)); // 대분류≠시트명 → 시트명 키
            }
        }
    }

    /**
     * 기타 서브테이블에서 같은 품번(D)을 공유하는 변형(전기/배터리 등)을 찾아 제품코드(G)의 구분글자를 접미한다(req3).
     * 품번(D)이 채워진 행이 그룹 시작, 아래의 D 빈칸 행은 같은 품번의 변형으로 본다. 그룹 크기 ≥2면
     * 각 멤버의 제품코드 첫 상이 인덱스 글자를 품번에 붙여 충돌 없이 둘 다 보존한다.
     * 반환: 대상 행(0-based) → 최종 품번(base+구분글자). 단일 품번(변형 없음)은 포함하지 않는다.
     */
    private Map<Integer, String> computeBidetCodeOverrides(Ctx c) {
        record Member(int row, String prodCode) {}
        Map<String, List<Member>> groups = new LinkedHashMap<>();
        BidetCols cols = null;
        String carryBase = null;
        int last = c.sheet.getLastRowNum();
        for (int r = 0; r <= last; r++) {
            BidetCols detected = detectBidetHeader(c, r);
            if (detected != null) { cols = detected; carryBase = null; continue; }
            if (cols == null || cols.specCol < 0) continue; // 기타(스펙 존재) 구간만
            String ownCode = normalizeCode(str(c, r, cols.codeCol));
            BigDecimal price = cols.priceCol >= 0 ? dec(c, r, cols.priceCol) : null;
            String prod = cols.productCodeCol >= 0 ? normalizeCode(str(c, r, cols.productCodeCol)) : null;
            if (ownCode == null && prod == null && price == null) continue; // 꼬리 빈 행 방어(메인 루프와 동일)
            if (ownCode != null) carryBase = ownCode;
            String base = ownCode != null ? ownCode : carryBase;
            if (base == null || isHeaderLikeCode(base)) continue;
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(new Member(r, prod));
        }

        Map<Integer, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, List<Member>> e : groups.entrySet()) {
            List<Member> ms = e.getValue();
            if (ms.size() < 2) continue;                    // 변형 없는 단일 품번은 그대로
            int k = firstDiffIndex(ms.stream().map(Member::prodCode).toList());
            for (Member m : ms) {
                String suffix = (m.prodCode() != null && m.prodCode().length() > k)
                        ? String.valueOf(m.prodCode().charAt(k)) : "";
                overrides.put(m.row(), e.getKey() + suffix);
            }
        }
        return overrides;
    }

    /**
     * 비데·기타 서브테이블 헤더행 탐지(품번 + 대리점가 동시 존재). 헤더면 컬럼맵, 아니면 null.
     * 두 서브테이블의 컬럼 위치가 다르므로(비데 품번=B / 기타 품번=D+스펙 E) 헤더마다 재탐지한다.
     */
    private BidetCols detectBidetHeader(Ctx c, int r) {
        Row row = c.sheet.getRow(r);
        if (row == null) return null;
        short lastCell = row.getLastCellNum();
        int kindCol = -1, codeCol = -1, specCol = -1, prodCol = -1, priceCol = -1, remarkCol = -1;
        boolean hasCode = false, hasPrice = false;
        for (int col = 0; col < lastCell; col++) {
            String h = noSpace(str(c, r, col));
            if (h == null) continue;
            if (h.equals("품번") && codeCol < 0) { codeCol = col; hasCode = true; }
            else if ((h.equals("품종") || h.equals("품목")) && kindCol < 0) kindCol = col;
            else if (h.equals("스펙") && specCol < 0) specCol = col;
            else if (h.equals("제품코드") && prodCol < 0) prodCol = col;
            else if (h.contains("대리점가") && priceCol < 0) { priceCol = col; hasPrice = true; }
            else if (h.contains("비고") && remarkCol < 0) remarkCol = col;
        }
        if (!(hasCode && hasPrice)) return null;
        return new BidetCols(kindCol, codeCol, specCol, prodCol, priceCol, remarkCol);
    }

    private record BidetCols(int kindCol, int codeCol, int specCol,
                             int productCodeCol, int priceCol, int remarkCol) {}

    // ============================================================
    // (A-4) 수전금구 3-시트 — 대분류 통합("수전금구") + price_basis(시트명) 분리 (§10)
    //   · 일반: 단일 제품(부속 없음), 본품 대리점가. 대분류=시트명 그대로라 이미지 매칭 종전대로.
    //   · 국산/OEM 부속 기준: 소계 세트형(대표=본품 품번, 부속행들, 소계=세트가). 레이아웃 동일 → 공용 파서.
    //     같은 본품이 3시트에 등장 → upsert로 본품 1행(대분류=수전금구 고정), 가격만 price_basis(시트명)로 3분리(S1·S2).
    //     대분류≠시트명이라 이미지는 시트명 실은 imageKey로 매칭(S: 비데와 동일 트릭). 부속 출처는 부속 categorySmall(국산/OEM, S4).
    // ============================================================

    /** 수전금구(일반) — 단일 제품(부속 없음). 시리즈(구분) carry-forward, 대분류=소분류 기준 시리즈. */
    private void parseFaucetGeneralSheet(Ctx c, List<VendorProductSet> out) {
        FaucetGenCols cols = null;
        String carrySeries = null;
        int last = c.sheet.getLastRowNum();
        for (int r = 0; r <= last; r++) {
            FaucetGenCols d = detectFaucetGenHeader(c, r);
            if (d != null) { cols = d; carrySeries = null; continue; }
            if (cols == null) continue;

            String code = normalizeCode(str(c, r, cols.codeCol)); // E=품번
            if (code == null || isHeaderLikeCode(code)) continue;

            String series = cols.seriesCol >= 0 ? stripSpace(str(c, r, cols.seriesCol)) : null; // B=구분/시리즈(병합)
            if (series != null) carrySeries = series;
            String name = cols.nameCol >= 0 ? stripSpace(str(c, r, cols.nameCol)) : null;        // C=품목
            BigDecimal price = cols.priceCol >= 0 ? dec(c, r, cols.priceCol) : null;             // G=대리점가
            String remark = cols.remarkCol >= 0 ? stripSpace(str(c, r, cols.remarkCol)) : null;  // J=비고(단종 등, R7 잠정)

            String displayName = name != null ? name : code;
            if (price == null) displayName = displayName + " (가격없음)";
            // 대분류="수전금구"(=시트명 → 이미지 매칭 종전대로), 소분류=시리즈(본품 안정), priceBasis=categoryLarge(9-arg 기본)
            VendorParsedItem main = new VendorParsedItem(code, displayName, null, null,
                    VendorParsedItem.RELATION_MAIN, nz(price), remark);
            out.add(new VendorProductSet("B", "수전금구", carrySeries, main,
                    new ArrayList<>(), nz(price), false, imageKeyOf(r), false));
        }
    }

    /** 수전금구(일반) 2행 헤더 탐지(품번 + 대리점가). 헤더가 두 줄로 쪼개져 윗행 병합해 읽는다. */
    private FaucetGenCols detectFaucetGenHeader(Ctx c, int r) {
        Row row = c.sheet.getRow(r);
        if (row == null) return null;
        short lastCell = row.getLastCellNum();
        int seriesCol = -1, nameCol = -1, codeCol = -1, priceCol = -1, remarkCol = -1;
        boolean hasCode = false, hasPrice = false;
        for (int col = 0; col < lastCell; col++) {
            String h = noSpace(str(c, r, col));
            if (h == null && r > 0) h = noSpace(str(c, r - 1, col)); // 윗행 병합
            if (h == null) continue;
            if (h.equals("품번") && codeCol < 0) { codeCol = col; hasCode = true; }
            else if ((h.equals("구분") || h.equals("시리즈")) && seriesCol < 0) seriesCol = col;
            else if (h.equals("품목") && nameCol < 0) nameCol = col;
            else if (h.contains("대리점가") && priceCol < 0) { priceCol = col; hasPrice = true; }
            else if (h.contains("비고") && remarkCol < 0) remarkCol = col;
        }
        if (!(hasCode && hasPrice)) return null;
        return new FaucetGenCols(seriesCol, nameCol, codeCol, priceCol, remarkCol);
    }

    private record FaucetGenCols(int seriesCol, int nameCol, int codeCol, int priceCol, int remarkCol) {}

    /**
     * 수전금구(국산 부속 기준)·(OEM 부속 기준) 공용 — 소계 세트형.
     * 대표행(A=시리즈 있음, C=본품 품번, G=본품 단가) + 부속행(A공백, B=부속명, C품번/없으면 D제품코드, G=부속단가) + 소계행(C="소계", G=세트가).
     * 대분류="수전금구"(통합), 소분류=시리즈, priceBasis=시트명(가격 3분리), 부속 categorySmall=국산/OEM(출처).
     */
    private void parseFaucetPartsSheet(Ctx c, List<VendorProductSet> out) {
        String ns = c.sheetName.replaceAll("\\s", "");
        String partOrigin = ns.contains("OEM") ? "OEM" : (ns.contains("국산") ? "국산" : null); // 부속 출처(S4)

        int headerRow = findRow(c, r -> {
            String a = noSpace(str(c, r, 0));
            return a != null && (a.contains("품목") || a.contains("품명"));
        });
        if (headerRow < 0) {
            logger.warn("[B][{}] 수전금구 부속 헤더(품목) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        String series = null, repCode = null, repName = null;
        BigDecimal repUnit = null;
        List<VendorParsedItem> parts = null;
        int repRow = -1;

        for (int r = headerRow + 1; r <= last; r++) {
            String cCell = noSpace(str(c, r, 2)); // C
            if (cCell != null && cCell.contains("소계")) {           // 소계행 → 세트 확정(세트가=G)
                // 소계 값 오른쪽(H) 구성 부기 "(메탈호스, 일반헤드 포함)" → 본품 description(C-2 결정 12)
                flushFaucetPartsSet(c, out, series, repCode, repName, nz(dec(c, r, 6)), parts, repRow,
                        stripSpace(str(c, r, 7)));
                repCode = null; parts = null; series = null; repUnit = null; repRow = -1;
                continue;
            }
            BigDecimal price = nz(dec(c, r, 6)); // G=단가
            String bName = stripSpace(str(c, r, 1)); // B=품명/부속명
            String a = stripSpace(str(c, r, 0));     // A=품목(시리즈) → 대표행 경계

            if (a != null) {                                        // 대표행(본품)
                flushFaucetPartsSet(c, out, series, repCode, repName, repUnit, parts, repRow, null); // 소계 없이 닫힌 이전 세트 방어
                series = a;
                repCode = normalizeCode(str(c, r, 2));              // C=본품 품번
                repName = orDefault(bName, repCode);
                repUnit = price;                                    // 본품 단가(소계 없을 때 폴백)
                parts = new ArrayList<>();
                repRow = r;
            } else if (parts != null && repCode != null) {          // 부속행
                String pcode = normalizeCode(str(c, r, 2));         // C=부속 품번
                if (pcode == null) pcode = normalizeCode(str(c, r, 3)); // 없으면 D=제품코드(S6 유실 방지)
                if (pcode == null) continue;
                String label = orDefault(bName, pcode);
                parts.add(new VendorParsedItem(partCode(repCode, faucetDetail(pcode, label)),
                        label, null, null, VendorParsedItem.RELATION_ACCESSORY, price, null, null, partOrigin));
            }
        }
        flushFaucetPartsSet(c, out, series, repCode, repName, repUnit, parts, repRow, null);
    }

    private void flushFaucetPartsSet(Ctx c, List<VendorProductSet> out, String series, String repCode,
                                     String repName, BigDecimal setPrice, List<VendorParsedItem> parts, int repRow,
                                     String subtotalNote) {
        if (repCode == null) return;
        BigDecimal price = setPrice != null ? setPrice : BigDecimal.ZERO;
        // 소계 오른쪽 구성 부기는 본품 description(C-2 결정 12). OEM J=비고(매입처)는 계속 미저장.
        VendorParsedItem main = new VendorParsedItem(repCode, repName, null, null,
                VendorParsedItem.RELATION_MAIN, price, null, subtotalNote);
        // 대분류="수전금구"(통합), 소분류=시리즈(본품 안정), priceBasis=시트명(가격 분리), 이미지=시트명 실은 키
        out.add(new VendorProductSet("B", "수전금구", series, main,
                parts != null ? parts : new ArrayList<>(), price, false,
                imageKeyOf(c.sheetName, repRow), false, c.sheetName));
    }

    /** 수전금구 부속 냉/온 구분: 라벨에 냉/온이 있으면 코드에 c/h 접미(같은 제품코드 냉·온수 공유 시 충돌 방지). */
    private String faucetDetail(String code, String label) {
        if (code == null || label == null) return code;
        String n = label.replaceAll("\\s", "");
        if (n.contains("냉") && !code.endsWith("c")) return code + "c";
        if (n.contains("온") && !code.endsWith("h")) return code + "h";
        return code;
    }

    // ============================================================
    // (A-5) 분계표 — 완성수전 부속분계(§11 P1~P4). 본품은 §10 수전금구와 동일 제품(표기만 다름) →
    //   품번 정규화(G 0130/T0130 → G-0130/T-0130) 후 대분류=수전금구로 병합, 대리점가는 priceBasis=시트명으로 분리(P1).
    //   부속(몸체/편심/…)은 같은 전산코드가 세트별 단가 상이 → 코드={품번}_{전산코드} 프리픽스, categorySmall=분계(P2).
    //   대리점가 없는 블록(싱크수전 등)은 구성만 의미(가격 0, P3). 같은 품번 재등장(S 0346 변형 3종)은 첫 블록만 + 검수필요(P4).
    //   컬럼: A=매입처 B=품번 C=품명 D=세트 전산코드(미저장, S7) F=부속 전산코드 G=부속명 H=기준단가 I=대리점가.
    // ============================================================

    /** 분계표 품번(공백/하이픈/무구분 혼용): "G 0130"/"G-0121"/"T0130" → 수전금구 표기 "G-0130". */
    private static final Pattern BREAKDOWN_PN = Pattern.compile("^([A-Z]{1,2})[ -]?(\\d{4})$");

    private void parseBreakdownSheet(Ctx c, List<VendorProductSet> out) {
        int last = c.sheet.getLastRowNum();

        // P4: 같은 품번이 여러 블록(S 0346 슬림/일반/건설)이면 첫 블록만 적재 + 검수필요 → 중복 품번 사전 집계
        Map<String, Integer> codeCount = new LinkedHashMap<>();
        for (int r = 0; r <= last; r++) {
            Matcher m = breakdownPn(str(c, r, 1));
            if (m != null) codeCount.merge(m.group(1) + "-" + m.group(2), 1, Integer::sum);
        }

        Set<String> flushed = new HashSet<>();
        String code = null, kindName = null;
        List<VendorParsedItem> parts = null;
        Set<String> partSeen = null;
        BigDecimal setPrice = null;
        int repRow = -1;
        boolean needsReview = false, skipBlock = false;

        for (int r = 0; r <= last; r++) {
            Matcher m = breakdownPn(str(c, r, 1)); // B=품번 → 새 블록 경계
            if (m != null) {
                flushBreakdownSet(c, out, code, kindName, setPrice, parts, repRow, needsReview);
                String next = m.group(1) + "-" + m.group(2);
                skipBlock = !flushed.add(next);            // 재등장 블록(S 0346 2·3번째)은 통째로 스킵(P4)
                code = skipBlock ? null : next;
                kindName = stripSpace(str(c, r, 2));       // C=품명(욕조샤워 등)
                parts = new ArrayList<>();
                partSeen = new HashSet<>();
                setPrice = null;
                repRow = r;
                needsReview = codeCount.getOrDefault(next, 1) > 1; // 중복 품번의 첫 블록 → 검수필요
            }
            if (code == null || skipBlock) continue;

            // 부속행: F=전산코드(영숫자·숫자 시작), G=부속명, H=기준단가. 헤더("전산코드")·안내문은 한글 포함이라 걸러짐.
            String pf = normalizeCode(str(c, r, 5));
            if (pf != null && pf.matches("^\\d[0-9a-zA-Z]*$")) {
                String finalCode = partCode(code, pf);
                if (partSeen.add(finalCode)) {
                    parts.add(new VendorParsedItem(finalCode, orDefault(stripSpace(str(c, r, 6)), pf), null, null,
                            VendorParsedItem.RELATION_ACCESSORY, nz(dec(c, r, 7)), null, null, "분계"));
                } else {
                    needsReview = true; // 변형 서브블록(S 0646 HRS/HR/HS 등) 공통 부속 중복 → 첫 건만 유지·검수 표기
                }
            }

            // A=매입처(한양/킴스코…)는 저장하지 않는다(C-2 정책: 매입처 비고 미저장)

            BigDecimal i = dec(c, r, 8);                   // I=대리점가(문자 "파이프추가 예정" 등은 null)
            if (i != null) setPrice = i;
        }
        flushBreakdownSet(c, out, code, kindName, setPrice, parts, repRow, needsReview);
    }

    private Matcher breakdownPn(String raw) {
        String x = stripSpace(raw);
        if (x == null) return null;
        Matcher m = BREAKDOWN_PN.matcher(x);
        return m.matches() ? m : null;
    }

    private void flushBreakdownSet(Ctx c, List<VendorProductSet> out, String code, String kindName,
                                   BigDecimal setPrice, List<VendorParsedItem> parts,
                                   int repRow, boolean needsReview) {
        if (code == null) return;
        // 이름은 수전금구 본품과 병합(last-wins)돼도 형식이 어긋나지 않게 "품명 품번". 가격없음 접미는 붙이지 않는다(공유 본품 이름 오염 방지, P3).
        VendorParsedItem main = new VendorParsedItem(code, join(kindName, code), null, null,
                VendorParsedItem.RELATION_MAIN, nz(setPrice), null);
        // 대분류=수전금구(병합, P1)·소분류=품번 유도 시리즈("G-0130"→"G-01", §10 적재값과 동일 형식)·priceBasis=시트명
        String series = code.substring(0, code.indexOf('-') + 3);
        out.add(new VendorProductSet("B", "수전금구", series, main,
                parts != null ? parts : new ArrayList<>(), nz(setPrice), false,
                imageKeyOf(c.sheetName, repRow), needsReview, c.sheetName));
    }

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
            // 갈라시아는 실제 세면기 제품 → 표시용으로 품번 앞에 "갈라시아 세면기" 부여, 소분류=갈라시아.
            // (대분류는 시트명 "갈라시아" 유지 — categoryLarge는 이미지 매칭 키 겸용이라 바꾸면 이미지가 끊김)
            String repName = "갈라시아 세면기 " + repCode;
            VendorParsedItem main = new VendorParsedItem(repCode, repName,
                    null, null, VendorParsedItem.RELATION_MAIN, setPrice, null);
            out.add(new VendorProductSet("B", c.sheetName, "갈라시아", main, parts,
                    setPrice, false, imageKeyOf(r), false));
        }
    }

    // ============================================================
    // (C-1) 악세사리 단가표 — 세트 구간 + 단일품 구간 혼재 (§12 A1~A7)
    //   세트: G열="SET" 대표행 + 부속행들(A·B·C 빈칸, H=가). 세트가 = 대표행 H.
    //   단일품: 그 외 모든 행 = 독립 제품(부속 없음). A/B=분류(carry), F=품명, G=규격, H=가, J=비고.
    //   대분류=악세사리(C-1 '단가표' 제거) → 이미지는 시트명 실은 imageKey로 매칭(D52), priceBasis=시트명.
    //   품명 정비: ditto(")·빈칸·숫자 오염(5000)은 세부분류+규격 또는 직전 실품명으로 폴백(A5·A6).
    //   규격(G)은 description 보존(동명 이규격 구분, A2), 비고(J)는 remark 잠정 보존(R7, A3).
    //   U접두 품번(핸드스프레이 필터 3종)은 수전부속 U9120(자동폽업)과 충돌 → {품번}-{전산코드} 결합(A1).
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
        String catA = null, catB = null;           // 분류 carry-forward
        String lastName = null;                     // 따옴표(ditto)·빈칸 품명 → 직전 실품명

        for (int r = headerRow + 1; r <= last; r++) {
            String code = normalizeCode(str(c, r, 3)); // D=품번
            if (code == null) continue;
            if (code.startsWith("U")) {                // 수전부속 품번 체계와 충돌(U9120) → {품번}-{전산코드}(A1)
                String ecode = normalizeCode(str(c, r, 4)); // E=전산코드
                if (ecode != null) code = code + "-" + ecode;
            }

            String aRaw = stripSpace(str(c, r, 0));
            String bRaw = stripSpace(str(c, r, 1));
            String cRaw = stripSpace(str(c, r, 2));
            String spec = stripSpace(str(c, r, 6));     // G=규격(또는 "SET")
            BigDecimal price = nz(dec(c, r, 7));        // H=대리점가
            String remark = stripSpace(str(c, r, 9));   // J=비고(단종/옵션 등, R7 잠정, A3)

            String rawName = stripSpace(str(c, r, 5));  // F=품명
            if (rawName != null && rawName.matches("\\d+(\\.\\d+)?")) rawName = null; // 숫자 오염(5000) 무효(A5)
            String name = resolveDitto(rawName, lastName);
            if (name != null) lastName = name;
            else name = (bRaw != null) ? join(bRaw, spec) : lastName; // 빈칸·오염 폴백: 세부분류+규격 / 직전 실품명(A5·A6)

            boolean isSetRep = spec != null && spec.replace(" ", "").equalsIgnoreCase("SET");
            if (isSetRep) {                              // ── 세트 대표행(G=SET) ──
                if (inSet) flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow);
                if (aRaw != null) { catA = aRaw; catB = null; }
                if (bRaw != null) catB = bRaw;
                setCat = orDefault(catB, catA);          // 대표행 A/B 빈칸(AC8300G)도 carry로 보완(A7)
                setPrice = price;
                mainItem = new VendorParsedItem(code, join(setCat, orDefault(rawName, code)), null, null,
                        VendorParsedItem.RELATION_MAIN, setPrice, remark);
                parts = new ArrayList<>();
                mainRow = r;
                inSet = true;
                continue;
            }

            boolean isSetPart = inSet && aRaw == null && bRaw == null && cRaw == null;
            if (isSetPart) {                             // ── 세트 부속행 ──
                String pName = orDefault(name, code);
                parts.add(new VendorParsedItem(partCode(mainItem.productCode(), coldHot(code, pName)),
                        pName, null, null, pName, price, remark)); // 부속 비고(단종 예정/옵션) 보존
                continue;
            }

            // ── 단일품 구간(세트 종료 포함) ──
            if (inSet) { flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow); inSet = false; }
            if (aRaw != null) { catA = aRaw; catB = null; }
            if (bRaw != null) catB = bRaw;
            String catSmall = orDefault(catB, catA);
            String descr = (spec != null && !spec.equals(name)) ? spec : null; // 규격=description(품명과 같으면 생략, A2)
            VendorParsedItem single = new VendorParsedItem(code, orDefault(name, code), null, null,
                    VendorParsedItem.RELATION_MAIN, price, remark, descr);
            out.add(new VendorProductSet("B", "악세사리", catSmall, single,
                    new ArrayList<>(), price, false, imageKeyOf(c.sheetName, r), false, c.sheetName));
        }
        if (inSet) flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow);
    }

    private void flushAccSet(List<VendorProductSet> out, Ctx c, VendorParsedItem mainItem,
                             List<VendorParsedItem> parts, String setCat, BigDecimal setPrice, int mainRow) {
        if (mainItem == null) return;
        // 대분류=악세사리(C-1)·priceBasis=시트명·이미지=시트명 실은 키(D52)
        out.add(new VendorProductSet("B", "악세사리", setCat, mainItem,
                parts != null ? parts : new ArrayList<>(), setPrice, false,
                imageKeyOf(c.sheetName, mainRow), false, c.sheetName));
    }

    // ============================================================
    // (C-2) 소계 세트형 — 소계행으로 세트 종료 (국산 부속 기준 전용)
    //   대표행(A 있음) + 부속행(A 없음) + 소계행(C/B="소계")
    // ============================================================

    private void parseSubtotalSetSheet(Ctx c, List<VendorProductSet> out) {
        boolean korParts = c.sheetName.replaceAll("\\s", "").contains("국산부속"); // 수전 부속(세트)는 §11 전용 파서로 분리됨
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
    // (C-3) 수전 부속(세트) — 부속 카탈로그 세트 뷰(§11 P5~P8). 대분류=수전부속(신규), priceBasis=시트명.
    //   컬럼: A=품명그룹 B=품번 C=제품코드 E=수량 F=단가 G=이미지/부기 H=비고.
    //   - 냉/온+소계 블록 → 합성 세트 품번(U9013c/h→U9013) 생성: main=세트(세트가=소계), 부속={base}_c/h(P6).
    //   - 제품코드(C)에 '+'가 있는 행(U9310 건+행거, U9510~U9550 조합) → main=품번, 부속={품번}_{전산코드}(P7).
    //   - 그 외 행 = 단품. 품번패턴이면 품번(대문자·하이픈 제거, P9), 아니면 제품코드 폴백(P8). 원본 B 잔여=description.
    //   - 하단 "니쁠" 서브테이블(C=제품코드, D=단가, E=규격)은 품번이 없어 제품코드를 코드로(P8).
    // ============================================================

    private void parseFittingSetSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> {
            String a = noSpace(str(c, r, 0));
            return a != null && a.contains("품명");
        });
        if (headerRow < 0) {
            logger.warn("[B][{}] 수전부속 헤더(품명) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        List<FtMember> buf = new ArrayList<>();
        String group = null;
        boolean nipple = false;

        for (int r = headerRow + 1; r <= last; r++) {
            String cText = noSpace(str(c, r, 2));

            if ("제품코드".equals(cText)) {              // 니쁠 서브헤더(제품코드/단가/규격) → 별도 레이아웃
                flushFittingFlat(out, c, buf, group); buf.clear(); group = null;
                nipple = true;
                continue;
            }
            if (nipple) {
                String code = normalizeCode(str(c, r, 2)); // C=제품코드
                if (code == null) continue;
                code = FITTING_CODE_TO_PARTNO.getOrDefault(code.toLowerCase(), code.toLowerCase()); // P14 병합
                String bLabel = stripSpace(str(c, r, 1));
                if (bLabel != null) group = bLabel;        // '니쁠'
                String spec = stripSpace(str(c, r, 4));    // E=규격
                String name = group;
                if (spec != null) name = join(name, "(" + spec + ")");
                name = join(name, code);
                out.add(fittingSingle(c, group, code, name, dec(c, r, 3), null, null, r)); // D=단가
                continue;
            }

            if (cText != null && cText.contains("소계")) { // 냉/온 블록 종료 → 합성 세트(P6)
                flushFittingComposite(out, c, buf, group, dec(c, r, 5),
                        join(stripSpace(str(c, r, 6)), stripSpace(str(c, r, 7)))); // "(세트단가임)" 등
                buf.clear();
                continue;
            }

            String aRaw = stripSpace(str(c, r, 0));
            if (aRaw != null && aRaw.startsWith("*")) continue; // 각주
            if (str(c, r, 1) == null && str(c, r, 2) == null) continue;

            if (aRaw != null) {                            // 새 그룹 시작 → 이전(소계 없던) 블록은 단품으로 방출
                flushFittingFlat(out, c, buf, group); buf.clear();
                group = aRaw;
            }
            buf.add(new FtMember(r, str(c, r, 1), str(c, r, 2), dec(c, r, 5),
                    join(stripSpace(str(c, r, 6)), stripSpace(str(c, r, 7))))); // F=단가, G+H=부기/비고(R7 잠정)
        }
        flushFittingFlat(out, c, buf, group);
    }

    /**
     * 냉/온+소계 블록 → 합성 세트(P6): 접미 제거 품번(U9013c/h→U9013)이 세트 품번,
     * 부속 코드={base}_c/h(§10 coldHot 관례) — 단가표 단품 U9013C와 코드가 달라 공존.
     */
    private void flushFittingComposite(List<VendorProductSet> out, Ctx c, List<FtMember> buf,
                                       String group, BigDecimal subtotal, String subRemark) {
        if (buf.isEmpty()) return;
        FtMember first = buf.get(0);
        String firstPn = fittingPartNo(normalizeCode(firstToken(first.bRaw())));
        if (firstPn == null) { flushFittingFlat(out, c, buf, group); return; } // 품번 없는 블록 방어 → 단품 처리
        String base = firstPn.matches(".*[CH]$") ? firstPn.substring(0, firstPn.length() - 1) : firstPn;

        List<VendorParsedItem> parts = new ArrayList<>();
        int idx = 0;
        for (FtMember m : buf) {
            String label = stripSpace(m.bRaw());
            String n = label == null ? "" : label.replaceAll("\\s", "");
            String suffix = n.contains("냉") ? "c" : n.contains("온") ? "h" : String.valueOf(++idx);
            NoteSplit ns = splitFittingNote(m.remark()); // 부속 비고도 내용별 분류(C-2): U9013c 15파이 → specs
            parts.add(new VendorParsedItem(partCode(base, suffix), orDefault(label, base + suffix), null, null,
                    VendorParsedItem.RELATION_ACCESSORY, nz(m.price()), ns.remark(), ns.description(), null, ns.specs()));
        }
        VendorParsedItem main = new VendorParsedItem(base, join(join(group, "세트"), base), null, null,
                VendorParsedItem.RELATION_MAIN, nz(subtotal), subRemark);
        out.add(new VendorProductSet("B", "수전부속", group, main, parts, nz(subtotal), false,
                imageKeyOf(c.sheetName, first.row()), false, c.sheetName));
    }

    /**
     * 세트 시트 전산코드 폴백 중 품번이 알려진 항목(신규 OEM 시트의 품번↔전산코드 매핑) → 품번으로 적재해
     * OEM 시트 행과 upsert 자연 병합(P14 병합 결정, 2026-07-15). 종전엔 2행 공존 + 검수필요 플래그였다.
     */
    private static final Map<String, String> FITTING_CODE_TO_PARTNO =
            Map.of("43u04110", "U04110", "43u944265", "U944265");

    /** 소계 없이 끝난 블록 → 행별 단품(제품코드 '+' 조합행은 구성세트, P7). */
    private void flushFittingFlat(List<VendorProductSet> out, Ctx c, List<FtMember> buf, String group) {
        for (FtMember m : buf) {
            if (m.cRaw() != null && m.cRaw().contains("+")) { emitFittingComboSet(out, c, group, m); continue; }
            String token = normalizeCode(firstToken(m.bRaw()));
            String bClean = stripSpace(m.bRaw());
            String code, descr;
            if (token != null && token.matches("^[A-Za-z].*[A-Za-z0-9]$")) {
                code = fittingPartNo(token);               // 품번=코드(대문자·하이픈 제거, P9), B 잔여=description
                descr = (bClean != null && bClean.length() > token.length() && bClean.startsWith(token))
                        ? orDefault(bClean.substring(token.length()).trim(), null) : null;
            } else {
                code = normalizeCode(m.cRaw());            // 품번패턴 아님(1.5m/65MM/한글) → 제품코드 폴백(P8)
                if (code != null) {
                    code = code.toLowerCase();             // 대소문자 오타 흡수(43U9113 등, P9)
                    code = FITTING_CODE_TO_PARTNO.getOrDefault(code, code); // 품번 매핑이 있으면 품번으로(P14 병합)
                }
                descr = bClean;                            // B 전체=description
            }
            if (code == null) continue;
            out.add(fittingSingle(c, group, code, orDefault(join(group, bClean), code),
                    m.price(), m.remark(), descr, m.row()));
        }
    }

    /** 제품코드 '+' 조합행(U9310 건+행거, U9510~U9550) → main=품번, 부속={품번}_{전산코드}·단가 없음(P7). */
    private void emitFittingComboSet(List<VendorProductSet> out, Ctx c, String group, FtMember m) {
        String token = normalizeCode(firstToken(m.bRaw()));
        String pn = (token != null && token.matches("^[A-Za-z].*[A-Za-z0-9]$")) ? fittingPartNo(token) : null;
        if (pn == null) return; // 조합행은 모두 U 품번 보유(방어)

        List<VendorParsedItem> parts = new ArrayList<>();
        for (String piece : m.cRaw().split("\\+")) {
            String[] pc = splitParen(piece);               // "43u9360n(건)" → [43u9360n, 건]
            String pcode = pc[0] != null ? pc[0].toLowerCase() : pc[1]; // '니쁠' 같은 비코드 구성도 보존
            if (pcode == null) continue;
            parts.add(new VendorParsedItem(partCode(pn, pcode), orDefault(pc[1], pcode), null, null,
                    VendorParsedItem.RELATION_ACCESSORY, BigDecimal.ZERO, null));
        }
        String bClean = stripSpace(m.bRaw());
        String name = orDefault(join(group, bClean), pn);
        if (m.price() == null) name = name + " (가격없음)";
        NoteSplit ns = splitFittingNote(m.remark()); // 3기능/단기능/행거포함 → description(C-2)
        VendorParsedItem main = new VendorParsedItem(pn, name, null, null,
                VendorParsedItem.RELATION_MAIN, nz(m.price()), ns.remark(), ns.description(), null, ns.specs());
        out.add(new VendorProductSet("B", "수전부속", group, main, parts, nz(m.price()), false,
                imageKeyOf(c.sheetName, m.row()), false, c.sheetName));
    }

    /**
     * 수전부속 단품 1건(대분류=수전부속, priceBasis=시트명). 가격 없으면 0 + "(가격없음)" 표기(D8).
     * 비고는 내용별 분류(C-2): 규격→specs, 상태→remark, 기능/속성→description(원본 B 잔여와 병합), 매입처→미저장.
     */
    private VendorProductSet fittingSingle(Ctx c, String catSmall, String code, String name,
                                           BigDecimal price, String remark, String descr, int row) {
        if (price == null) name = name + " (가격없음)";
        NoteSplit ns = splitFittingNote(remark);
        VendorParsedItem main = new VendorParsedItem(code, name, null, null,
                VendorParsedItem.RELATION_MAIN, nz(price), ns.remark(),
                joinNotes(descr, ns.description()), null, ns.specs());
        return new VendorProductSet("B", "수전부속", catSmall, main,
                new ArrayList<>(), nz(price), false, imageKeyOf(c.sheetName, row), false, c.sheetName);
    }

    // ---- C-2 비고 내용별 분류 (수전 부속(세트)·부속 단가표 계열) ----------------------------------
    //   제품 기능/속성=description, 단종 등 상태 변동=remark, 규격(15파이/70mm)=specs, 매입처=미저장.

    /** 매입처 표기(분계표 A열과 동일 계열) — 비고에 섞여 나오면 저장하지 않는다. */
    private static final Set<String> FITTING_VENDOR_NOTES = Set.of("한양", "킴스코", "E.L");

    /** 규격성 비고: "15파이", "45mm", "70MM" 등 숫자+단위 단독 표기. */
    private static final Pattern FITTING_SPEC_NOTE = Pattern.compile("^\\d+(\\.\\d+)?(mm|파이)$", Pattern.CASE_INSENSITIVE);

    /** 비고 1건을 (remark, description, specs) 중 한 곳으로 분류. 매입처는 전부 버린다(EMPTY). */
    private NoteSplit splitFittingNote(String note) {
        if (note == null || note.isBlank()) return NoteSplit.EMPTY;
        String n = note.replaceAll("\\s", "");
        if (FITTING_VENDOR_NOTES.contains(n)) return NoteSplit.EMPTY;
        if (FITTING_SPEC_NOTE.matcher(n).matches()) return new NoteSplit(null, null, note);
        if (n.contains("단종") || n.contains("입고") || n.contains("중단")) return new NoteSplit(note, null, null);
        return new NoteSplit(null, note, null);
    }

    /** 설명 두 조각(원본 B 잔여 + 분류된 비고)을 " / "로 병합. */
    private String joinNotes(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " / " + b;
    }

    private record NoteSplit(String remark, String description, String specs) {
        static final NoteSplit EMPTY = new NoteSplit(null, null, null);
    }

    /** 수전부속 품번 정규화(P9): 대문자 + 하이픈 제거(U-9110150a→U9110150A, U9013c→U9013C). */
    private String fittingPartNo(String token) {
        if (token == null) return null;
        String x = token.replace("-", "").toUpperCase();
        return x.isEmpty() ? null : x;
    }

    private String firstToken(String s) {
        if (s == null) return null;
        String t = s.replace(' ', ' ').trim();
        if (t.isEmpty()) return null;
        int i = 0;
        while (i < t.length() && !Character.isWhitespace(t.charAt(i))) i++;
        return t.substring(0, i);
    }

    /** 수전부속 버퍼 멤버(행/원본B/원본C/단가(null=가격없음)/비고). */
    private record FtMember(int row, String bRaw, String cRaw, BigDecimal price, String remark) {}

    // ============================================================
    // (C-4) 부속 단가표 — 부속 단품 마스터(§11 P5·P8·P9). 대분류=수전부속, priceBasis=시트명.
    //   컬럼: A=품목그룹 B=품번 C=규격 D=전산코드 E=단가 F=비고 G=부기("세트시 기본").
    //   - 품번 있으면 품번=코드(대문자), 없거나 재사용(U9310 건/행거)이면 전산코드 폴백(P8).
    //   - 하단 니쁠 블록(C=전산코드, D=단가, E=규격)은 레이아웃이 달라 C 코드패턴으로 인식.
    //   - 세트 시트와 같은 품번은 upsert로 1행 병합, 가격은 시트명 basis 2행으로 분리 보존(P5).
    // ============================================================

    private void parseFittingPriceSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "품목".equals(noSpace(str(c, r, 0)))
                && "전산코드".equals(noSpace(str(c, r, 3))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 부속 단가표 헤더(품목/전산코드) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        String group = null, lastSpec = null;
        Set<String> seenPn = new HashSet<>(); // 품번 재사용(U9310 행거) 감지 → 전산코드 폴백(P8)

        for (int r = headerRow + 1; r <= last; r++) {
            String aRaw = stripSpace(str(c, r, 0));
            if (aRaw != null && (aRaw.startsWith("※") || aRaw.matches("^\\d+\\..*"))) continue; // 특기사항 각주
            if (aRaw != null) group = aRaw;

            // 니쁠 꼬리 블록: C=전산코드(43…), D=단가, E=규격 (B='니쁠')
            String cCode = normalizeCode(str(c, r, 2));
            if (cCode != null && cCode.matches("^43[0-9a-zA-Z]+$")) {
                String bLabel = stripSpace(str(c, r, 1));
                if (bLabel != null) group = bLabel;
                String spec = stripSpace(str(c, r, 4));
                String name = group;
                if (spec != null) name = join(name, "(" + spec + ")");
                name = join(name, cCode.toLowerCase());
                out.add(fittingSingle(c, group, cCode.toLowerCase(), name, dec(c, r, 3), null, null, r));
                continue;
            }

            String pnRaw = stripSpace(str(c, r, 1));                 // B=품번
            String ecodeRaw = normalizeCode(str(c, r, 3));           // D=전산코드
            String ecode = ecodeRaw != null ? ecodeRaw.toLowerCase() : null; // 대소문자 오타 흡수(43u0105GC, P9)
            if (pnRaw == null && ecode == null) continue;

            String pn = fittingPartNo(normalizeCode(firstToken(pnRaw)));
            String code = (pn != null && seenPn.add(pn)) ? pn : orDefault(ecode, pn);
            if (code == null) continue;

            String spec = resolveDitto(stripSpace(str(c, r, 2)), lastSpec); // C=규격(ditto " 처리)
            if (spec != null) lastSpec = spec;
            String name = group;
            if (spec != null) name = join(name, "(" + spec + ")");
            name = join(name, pn != null ? pn : code);
            String remark = join(stripSpace(str(c, r, 5)), stripSpace(str(c, r, 6))); // F비고+G부기(R7 잠정)
            out.add(fittingSingle(c, group, code, name, dec(c, r, 4), remark, null, r)); // E=단가
        }
    }

    // ============================================================
    // (C-5) 신규 OEM 부속 단가표 — OEM(영파) 부속 단품 21종 + 국산 대비 비교(§11-1 P12~P16).
    //   컬럼: B=순번 C=품번(하이픈형 U-942245) D=전산코드 E=품명(재질·규격 포함) F=대리점가
    //         G=차액·H=기존 국산가(파생·중복 정보 → 미저장, P16) I=비고(1차 입고분/단종, R7 잠정).
    //   대분류=수전부속·priceBasis=시트명. 품번 정규화(P9)로 세트 시트 OEM 9종과 upsert 자연 병합(P13).
    //   소분류=품명 앞부분(괄호 앞) 유도(P15). 하단 "세면기 수전" 조합 예시(품번 없음)는 스킵(P16).
    // ============================================================

    // (구 P14 검수 상수 제거) 코드 엇갈림 2종은 세트 시트 폴백을 품번으로 매핑해 병합한다 → FITTING_CODE_TO_PARTNO

    private void parseOemFittingSheet(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "구분".equals(noSpace(str(c, r, 1)))
                && "품번".equals(noSpace(str(c, r, 2)))
                && "전산코드".equals(noSpace(str(c, r, 3))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 신규 OEM 부속 헤더(구분/품번/전산코드) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        for (int r = headerRow + 1; r <= last; r++) {
            String pnRaw = stripSpace(str(c, r, 2));                 // C=품번 — 없으면 하단 조합 예시행 → 스킵(P16)
            if (pnRaw == null) continue;
            String pn = fittingPartNo(normalizeCode(firstToken(pnRaw)));
            if (pn == null || !pn.matches("^[A-Za-z].*")) continue;

            String itemName = stripSpace(str(c, r, 4));              // E=품명
            BigDecimal price = dec(c, r, 5);                         // F=대리점가
            String remark = stripSpace(str(c, r, 8));                // I=비고(1차 입고분/단종)

            String group = itemName;                                 // 소분류=품명 괄호 앞부분(P15)
            if (group != null) {
                int p = group.indexOf('(');
                if (p >= 0) group = stripSpace(group.substring(0, p));
            }

            String display = join(itemName, pn);
            if (price == null) display = display + " (가격없음)";
            VendorParsedItem main = new VendorParsedItem(pn, display, null, null,
                    VendorParsedItem.RELATION_MAIN, nz(price), remark);
            out.add(new VendorProductSet("B", "수전부속", group, main, new ArrayList<>(),
                    nz(price), false, imageKeyOf(c.sheetName, r), false, c.sheetName));
        }
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
     * 시트명 포함 이미지 키("시트명" + SEP + 행). 대분류를 시트명에서 분리 저장하는 시트(비데,기타 등)는
     * categoryLarge로 이미지를 못 찾으므로 원본 시트명을 함께 실어 시트명 기준으로 매칭하게 한다.
     */
    private String imageKeyOf(String sheetName, int row) {
        return row >= 0 ? sheetName + VendorProductSet.IMAGE_KEY_SHEET_SEP + row : null;
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
