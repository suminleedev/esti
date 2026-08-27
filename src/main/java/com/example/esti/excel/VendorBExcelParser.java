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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.esti.excel.ExcelParseUtils.*;

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
 *
 * <h2>최신본(2026) 양식 — {@code *_V2} 패밀리</h2>
 * B사가 단가표를 9시트 → 14시트로 개편하면서 도기 3시트(양변기/세면기/소변기,수채)의 양식이
 * <b>가로 슬롯형 → 세로 나열형</b>으로 뒤집혔다(부속이 열이 아니라 행으로 내려온다).
 * 구본 픽스처를 쓰는 기존 테스트를 지키기 위해 <b>구·신 파서를 공존</b>시킨다(D-B1) —
 * 구본 메서드는 수정하지 않고 신양식은 별도 메서드로 만든다.
 * 시트명이 겹치는 도기 3시트만 {@link #isV2DogiSheet}로 레이아웃을 보고 가른다.
 * 상세는 {@code docs/analysis-b-format-2026.md} / {@code docs/plan-b-format-2026.md}.
 */
@Component
public class VendorBExcelParser implements VendorExcelParser {

    private static final Logger logger = LoggerFactory.getLogger(VendorBExcelParser.class);

    private static final int SLOT_FIRST_COL = 6; // G열부터 슬롯 후보

    @Override
    public String getVendorCode() { return "B"; }

    @Override
    public List<VendorProductSet> parseSets(Path path) {
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter();

            // 조합행(P7) 부속 단가 해석용 전산코드 인덱스. 조합행에는 세트가만 있고 구성 부속의 단가는
            // 다른 행(부속 단가표 D/E열, 수전 부속(세트) C/F열)에 있어, 시트 순회 전에 미리 모아둔다.
            Map<String, BigDecimal> fittingPrices = buildFittingCodePriceIndex(wb, fmt, ev);

            List<VendorProductSet> result = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                if (isSkippedSheet(wb, i)) {
                    logger.info("[B][{}] 적재 대상 아님(숨김 또는 '(삭제)' 표기) → 스킵", sheet.getSheetName());
                    continue;
                }
                String name = sheet.getSheetName();
                Ctx ctx = new Ctx(sheet, fmt, ev, name, fittingPrices);

                switch (family(ctx)) {
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
                    case GALAXIA    -> parseGalaxiaSheet(ctx, result);
                    case SET_TOTAL  -> parseHeaderTotalSetSheet(ctx, result);
                    case SET_SUBTOTAL -> parseSubtotalSetSheet(ctx, result);
                    case SINGLE     -> parseSingleRowSheet(ctx, result);
                    case TOILET_V2    -> parseToiletSheetV2(ctx, result);
                    case WASHBASIN_V2 -> parseWashbasinSheetV2(ctx, result);
                    case URINAL_SINK_V2 -> parseUrinalSinkSheetV2(ctx, result);
                    case ACCESSORY_V2   -> parseAccessorySheetV2(ctx, result);
                    case FITTING_CATALOG_V2 -> parseFittingCatalogSheetV2(ctx, result);
                    case FAUCET_V2      -> parseFaucetSheetV2(ctx, result);
                    case BATH_V2        -> parseBathSheetV2(ctx, result);
                    // 품번↔전산코드 매핑표라 제품 시트가 아니다. 부속 구성(분계)을 담고 있지만
                    // 구성의 단가가 이 파일 어디에도 없어(380건 중 344건) 관계 생성은 보류했다(T7, 계획서 §5).
                    case FAUCET_CODEMAP_V2 ->
                            logger.info("[B][{}] 품번 매핑표 — 제품 시트가 아니라 적재하지 않는다", name);
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

    private enum Family {
        // 구본(2020) 9시트 + 시트별 test 픽스처 4종
        TOILET, WASHBASIN, URINAL_SINK, BIDET_ETC, FAUCET_GENERAL, FAUCET_PARTS,
        BREAKDOWN, FITTING_SET, FITTING_PRICE, FITTING_OEM, GALAXIA, SET_TOTAL, SET_SUBTOTAL, SINGLE,
        // 최신본(2026) 14시트 — 구본과 공존한다(D-B1). 시트명이 겹치는 도기 3시트는 레이아웃으로 갈린다.
        TOILET_V2, WASHBASIN_V2, URINAL_SINK_V2, ACCESSORY_V2,
        FITTING_CATALOG_V2, FAUCET_V2, FAUCET_CODEMAP_V2, BATH_V2
    }

    /**
     * 시트별 판별 결과를 진단용으로 노출한다(시트명 → {@code Family} 이름, 스킵된 시트는 {@code SKIPPED}).
     *
     * <p>파싱 결과만으로는 "구본 파서가 헤더를 못 찾아 0건"과 "신양식으로 판별돼 스킵돼서 0건"이 구분되지 않는다.
     * 구·신 분기(D-B1)가 조용히 뒤집히는 회귀를 잡으려면 판별 자체를 관찰할 수 있어야 한다.
     */
    Map<String, String> diagnoseSheetFamilies(Path path) {
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter();
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                String name = sheet.getSheetName();
                out.put(name, isSkippedSheet(wb, i)
                        ? "SKIPPED"
                        : family(new Ctx(sheet, fmt, ev, name, Map.of())).name());
            }
            return out;
        } catch (Exception e) {
            throw wrap("B사 엑셀 시트 판별 중 오류", e);
        }
    }

    /**
     * 시트명 + 레이아웃 판별. 구·신 양식은 시트명만으로 갈리지만, 도기 3시트(양변기/세면기/소변기,수채)만은
     * 시트명이 같고 양식이 완전히 다르다(가로 슬롯형 ↔ 세로 나열형) → 헤더 라벨로 판별한다.
     */
    private Family family(Ctx c) {
        Family byName = family(c.sheetName);
        return switch (byName) {
            case TOILET      -> isV2DogiSheet(c) ? Family.TOILET_V2 : byName;
            case WASHBASIN   -> isV2DogiSheet(c) ? Family.WASHBASIN_V2 : byName;
            case URINAL_SINK -> isV2DogiSheet(c) ? Family.URINAL_SINK_V2 : byName;
            default -> byName;
        };
    }

    /**
     * 최신본 도기 3시트 판별 — 구본에 없는 헤더 라벨 {@code 제품정보}가 상단 6행 안에 있으면 신양식이다.
     * (신양식은 F열이 '제품정보 / 제품코드' 2단 헤더. 구본 헤더는 '구분/품종/품번/이미지/KS품번'뿐이다.)
     */
    private boolean isV2DogiSheet(Ctx c) {
        int last = Math.min(c.sheet.getLastRowNum(), 5);
        for (int r = 0; r <= last; r++) {
            Row row = c.sheet.getRow(r);
            if (row == null) continue;
            for (int col = 0; col < row.getLastCellNum(); col++) {
                if ("제품정보".equals(noSpace(str(c, r, col)))) return true;
            }
        }
        return false;
    }

    /** 적재 대상이 아닌 시트 — 숨김 처리됐거나 시트명이 {@code (삭제)}로 시작한다(D-B8). */
    private boolean isSkippedSheet(Workbook wb, int idx) {
        if (wb.isSheetHidden(idx) || wb.isSheetVeryHidden(idx)) return true;
        return wb.getSheetAt(idx).getSheetName().replaceAll("\\s", "").startsWith("(삭제)");
    }

    private Family family(String sheetName) {
        String n = sheetName.replaceAll("\\s", "");
        // ── 최신본(2026) 전용 시트명. 구본 분기보다 먼저 판별한다.
        //    특히 '수전금구 품번 및 품목코드'는 아래 contains("수전금구")에 먼저 걸리므로 반드시 여기 있어야 한다.
        if (n.contains("품번") && n.contains("품목코드")) return Family.FAUCET_CODEMAP_V2; // 수전금구 품번 및 품목코드(매핑표)
        if (n.equals("수전금구류")) return Family.FAUCET_V2;
        if (n.contains("액세사리") || n.contains("액세서리")) return Family.ACCESSORY_V2;   // '악'세사리(구본)와 다른 글자
        if (n.equals("부속류")) return Family.FITTING_CATALOG_V2;
        if (n.startsWith("바스")) return Family.BATH_V2;                                  // 바스 선반/파티션·욕조/천정재/욕실장·거울 (직영)
        // ── 구본(2020) 시트명
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
        SlotHeaderCols hc = readSlotHeader(c, firstHeader, slots, this::isSkipBasicSlotLabel);
        String lastKind = null;    // 품종(B) 병합셀 → 직전 품종 유지(req1)
        String prevRepCode = null; // 직전 제품의 base 품번 — 도기수로/사출수로 충돌 판별용

        int last = c.sheet.getLastRowNum();
        // 도자 종류만 다른 동일 품번 중복(IC703E(성오도자)/IC703E(구륙도자) 등)을 도기 코드 구분 글자로 분기(row→최종품번)
        Map<Integer, String> dojaOverrides = computeDojaCodeOverrides(c, firstHeader, last);
        for (int r = firstHeader + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) {                 // 새 서브테이블 헤더 → 슬롯 라벨 갱신(req2)
                hc = readSlotHeader(c, r, slots, this::isSkipBasicSlotLabel);
                continue;
            }
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작

            int priceRow = findPriceRow(c, r, last);

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
     * 슬롯 2행형 헤더행에서 슬롯(col→라벨)을 채우고 計·비고 컬럼 인덱스를 반환(없으면 -1). 슬롯 라벨은 내부공백 제거 정규화.
     * 살아있는 3개 슬롯 파서(양변기/세면기/소변기·수채) 공용 — 슬롯 스킵 규칙만 {@code skip}으로 주입한다
     * (양변기/세면기는 하부/상부를 실제 도기 슬롯으로 유지, 소변기·수채는 스킵). 計 없는 시트(세면기)는 totalCol=-1.
     */
    private SlotHeaderCols readSlotHeader(Ctx c, int headerRow, Map<Integer, String> slots, Predicate<String> skip) {
        slots.clear();
        int totalCol = -1, noteCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = SLOT_FIRST_COL; col < lastCell; col++) {
            String label = normLabel(str(c, headerRow, col));
            if (label == null) continue;
            if (isTotalLabel(label)) { totalCol = col; continue; }
            if (label.replace(" ", "").contains("비고")) { noteCol = col; continue; } // C-2: 비고 → description 수집
            if (skip.test(label)) continue;
            slots.put(col, label);
        }
        return new SlotHeaderCols(totalCol, noteCol);
    }

    private record SlotHeaderCols(int totalCol, int noteCol) {}

    /** 슬롯 스킵(양변기·세면기): 수량/비고/PLT만 제외(하부/상부는 실제 도기 슬롯이라 유지). */
    private boolean isSkipBasicSlotLabel(String label) {
        String s = label.replace(" ", "");
        return s.contains("수량") || s.contains("비고") || s.contains("PLT");
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

            int priceRow = findPriceRow(c, r, last);
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

        // 세면기엔 計 없음 → totalCol 미사용(세트가는 기본구성 합산). 비고(P) → description 수집(C-2 결정 10)
        Map<Integer, String> slots = new LinkedHashMap<>();
        int noteCol = readSlotHeader(c, headerRow, slots, this::isSkipBasicSlotLabel).noteCol();

        int last = c.sheet.getLastRowNum();
        Map<Integer, String> dojaOverrides = computeDojaCodeOverrides(c, headerRow, last);
        String lastKind = null; // 품종(B) 병합셀 대비 carry-forward
        for (int r = headerRow + 1; r <= last; r++) {
            if (isSlotHeader(c, r)) continue;
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작

            int priceRow = findPriceRow(c, r, last);

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
                hc = readSlotHeader(c, r, slots, this::isSkipSlotLabel);
                catIdx++;
                currentCat = catIdx < categories.size() ? categories.get(catIdx) : c.sheetName;
                carryKind = null;
                continue;
            }
            if (!"제품코드".equals(str(c, r, 5))) continue; // 제품코드행만 세트 시작
            if (slots.isEmpty()) continue;                 // 헤더 전 잡행 방어

            int priceRow = findPriceRow(c, r, last);

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


    /** 소변기·수채 세트 1건 적재: categoryLarge를 서브테이블별 대분류로 받고, 비코드 슬롯 설명을 description에 보존. */
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
                        new ArrayList<>(), nz(price), false, imageKeyOf(r), false,
                        currentCat, c.sheetName)); // 대분류·priceBasis=소분류(비데/기타), 이미지는 sheetName("비데, 기타")로 매칭
            } else {
                String name = join("비데", baseCode);                   // 제품명 앞에 '비데' 부기
                if (price == null) name = name + " (가격없음)";
                VendorParsedItem main = new VendorParsedItem(baseCode, name, null, null,
                        VendorParsedItem.RELATION_MAIN, nz(price), null, remark);   // 비데 비고→description
                out.add(new VendorProductSet("B", currentCat, currentCat, main,     // 소분류=비데(req2)
                        new ArrayList<>(), nz(price), false, imageKeyOf(r), false,
                        currentCat, c.sheetName)); // 대분류·priceBasis=소분류(비데), 이미지는 sheetName("비데, 기타")로 매칭
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
        String lastSeries = null;   // A=시리즈는 병합셀 → 값이 상단행에만 있음. 병합/직전 시리즈를 carry(본품 A가 비어도 사용)
        boolean expectMain = true;  // 소계(또는 헤더) 직후 첫 데이터행 = 본품. A 존재 여부로 판정하면 시리즈 병합이 여러 블록을
                                    // 덮거나 한 행 늦게 시작할 때 본품이 유실/오염됨 → 위치(소계 경계)로 본품을 판정

        for (int r = headerRow + 1; r <= last; r++) {
            String cCell = noSpace(str(c, r, 2)); // C
            if (cCell != null && cCell.contains("소계")) {           // 소계행 → 세트 확정(세트가=G)
                BigDecimal setPrice = dec(c, r, 6);                 // 소계 G=세트가
                if (setPrice == null) setPrice = repUnit;           // 소계 미기재(단종품 등) → 본품 단가 폴백(D2)
                // H=구성 부기 "(메탈호스, 일반헤드 포함)"→본품 description(C-2 결정 12), B=생애주기 상태(단종/신제품 추가 등)→본품 remark
                flushFaucetPartsSet(c, out, series, repCode, repName, nz(setPrice), parts, repRow,
                        stripSpace(str(c, r, 7)), stripSpace(str(c, r, 1)));
                repCode = null; repName = null; parts = null; series = null; repUnit = null; repRow = -1;
                expectMain = true;
                continue;
            }
            BigDecimal price = nz(dec(c, r, 6)); // G=단가
            String bName = stripSpace(str(c, r, 1)); // B=품명/부속명
            String a = stripSpace(str(c, r, 0));     // A=시리즈(병합 상단행에만 값)
            if (a != null) lastSeries = a;

            if (expectMain) {                                       // 소계 직후 첫 행 = 본품(시리즈 병합으로 A가 비어도 인식)
                String code = normalizeCode(str(c, r, 2));          // C=본품 품번
                if (code == null) continue;                         // 빈 행 방어 — 다음 행을 본품 후보로
                series = a != null ? a : lastSeries;                // 병합/직전 시리즈 carry
                repCode = code;
                repName = orDefault(bName, repCode);
                repUnit = price;                                    // 본품 단가(소계 미기재 시 세트가 폴백)
                parts = new ArrayList<>();
                repRow = r;
                expectMain = false;
            } else if (repCode != null) {                           // 부속행
                String pcode = normalizeCode(str(c, r, 2));         // C=부속 품번
                if (pcode == null) pcode = normalizeCode(str(c, r, 3)); // 없으면 D=제품코드(S6 유실 방지)
                if (pcode == null) continue;
                String label = orDefault(bName, pcode);
                parts.add(new VendorParsedItem(partCode(repCode, faucetDetail(pcode, label)),
                        label, null, null, VendorParsedItem.RELATION_ACCESSORY, price, null, null, partOrigin));
            }
        }
        // 소계 없이 파일이 끝난 마지막 세트 방어(본품 단가를 세트가로)
        flushFaucetPartsSet(c, out, series, repCode, repName, nz(repUnit), parts, repRow, null, null);
    }

    private void flushFaucetPartsSet(Ctx c, List<VendorProductSet> out, String series, String repCode,
                                     String repName, BigDecimal setPrice, List<VendorParsedItem> parts, int repRow,
                                     String subtotalNote, String status) {
        if (repCode == null) return;
        BigDecimal price = setPrice != null ? setPrice : BigDecimal.ZERO;
        // 소계 오른쪽 구성 부기(H)는 본품 description(C-2 결정 12). 소계행 상태(B: 단종/신제품 추가 등)는 본품 remark(C-2 상태=remark).
        // J=비고(매입처: 한양/대신…)는 계속 미저장(C-2 매입처 정책).
        VendorParsedItem main = new VendorParsedItem(repCode, repName, null, null,
                VendorParsedItem.RELATION_MAIN, price, status, subtotalNote);
        // 대분류="수전금구"(통합), 소분류=시리즈(본품 안정), priceBasis=시트명(가격 분리), 이미지=시트명 실은 키
        out.add(new VendorProductSet("B", "수전금구", series, main,
                parts != null ? parts : new ArrayList<>(), price, false,
                imageKeyOf(repRow), false, c.sheetName));
    }

    // ============================================================
    // (A-5) 분계표 — 완성수전 부속분계(§11 P1~P4). 본품은 §10 수전금구와 동일 제품(표기만 다름) →
    //   품번 정규화(G 0130/T0130 → G-0130/T-0130) 후 대분류=수전금구로 병합, 대리점가는 priceBasis=시트명으로 분리(P1).
    //   부속(몸체/편심/…)은 같은 전산코드가 세트별 단가 상이 → 코드={품번}_{전산코드} 프리픽스, categorySmall=분계(P2).
    //   대리점가 없는 블록(싱크수전 등)은 구성만 의미(가격 0, P3). 같은 품번 재등장(변형 3종)은 첫 블록만 + 검수필요(P4).
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
                imageKeyOf(repRow), needsReview, c.sheetName));
    }

    // ============================================================
    // 슬롯 공용 헬퍼 — 살아있는 3개 슬롯 파서(양변기/세면기/소변기·수채)가 공유.
    // ============================================================

    /**
     * 제품코드행(r) 다음의 "대리점가" 행 인덱스를 찾는다(없으면 -1). 최대 3행 내에서 탐색하되
     * 다음 제품코드행을 만나면 중단. 슬롯 2행형(양변기/세면기/소변기·수채) 공통 골격.
     */
    private int findPriceRow(Ctx c, int r, int last) {
        for (int k = r + 1; k <= Math.min(r + 3, last); k++) {
            if ("대리점가".equals(str(c, k, 5))) return k;
            if ("제품코드".equals(str(c, k, 5))) break;
        }
        return -1;
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
            // 갈라시아는 실제 세면기 제품 → 대분류=세면기(갈라시아)(D46), 소분류=갈라시아, 품번 앞 "갈라시아 세면기" 접두.
            // sheetName("갈라시아")을 별도 보존해 이미지 매칭은 categoryLarge와 독립(§13 sheetName 분리로 D46 해소).
            String repName = "갈라시아 세면기 " + repCode;
            VendorParsedItem main = new VendorParsedItem(repCode, repName,
                    null, null, VendorParsedItem.RELATION_MAIN, setPrice, null);
            out.add(new VendorProductSet("B", "세면기(갈라시아)", "갈라시아", main, parts,
                    setPrice, false, imageKeyOf(r), false, c.sheetName)); // priceBasis=sheetName="갈라시아"(가격 종전과 동일)
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
                    new ArrayList<>(), price, false, imageKeyOf(r), false, c.sheetName));
        }
        if (inSet) flushAccSet(out, c, mainItem, parts, setCat, setPrice, mainRow);
    }

    private void flushAccSet(List<VendorProductSet> out, Ctx c, VendorParsedItem mainItem,
                             List<VendorParsedItem> parts, String setCat, BigDecimal setPrice, int mainRow) {
        if (mainItem == null) return;
        // 대분류=악세사리(C-1)·priceBasis=시트명·이미지=시트명 실은 키(D52)
        out.add(new VendorProductSet("B", "악세사리", setCat, mainItem,
                parts != null ? parts : new ArrayList<>(), setPrice, false,
                imageKeyOf(mainRow), false, c.sheetName));
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

    /**
     * 수전부속 시트에서 (전산코드 → 단가)를 모은다 — 조합행(P7) 부속 단가 해석용.
     *
     * <p>조합행은 C열에 구성 전산코드만 "+"로 나열하고 F열에는 세트가만 적는다. 구성 부속의 단가는
     * 같은 시트의 단품 행이나 `부속 단가표` 시트에 따로 있어, 그 둘을 미리 훑어 인덱스로 만든다.
     *
     * <p>`부속 단가표`를 나중에 넣어 충돌 시 그쪽이 이긴다 — 부속 카탈로그의 단일 출처이기 때문이다(D13).
     * 예: 스프레이건 단품은 부속 단가표에만 단품가로 있고, 같은 품번(`U9310`)은
     * 수전 부속(세트)에서는 행거를 포함한 세트가라 세트 시트 값을 쓰면 안 된다.
     */
    private Map<String, BigDecimal> buildFittingCodePriceIndex(Workbook wb, DataFormatter fmt, FormulaEvaluator ev) {
        Map<String, BigDecimal> idx = new HashMap<>();
        // 수전 부속(세트) C=제품코드 F=단가 → 부속 단가표 D=전산코드 E=단가 순으로 넣어 뒤가 앞을 덮게 한다.
        collectFittingPrices(wb, fmt, ev, idx, Family.FITTING_SET, 2, 5);
        collectFittingPrices(wb, fmt, ev, idx, Family.FITTING_PRICE, 3, 4);
        return idx;
    }

    private void collectFittingPrices(Workbook wb, DataFormatter fmt, FormulaEvaluator ev,
                                      Map<String, BigDecimal> idx, Family target, int codeCol, int priceCol) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            if (sheet == null || isSkippedSheet(wb, i)) continue;
            if (family(sheet.getSheetName()) != target) continue;
            Ctx c = new Ctx(sheet, fmt, ev, sheet.getSheetName(), Map.of());
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                String raw = str(c, r, codeCol);
                if (raw == null || raw.contains("+")) continue;   // 조합행 자신은 인덱스에 넣지 않는다
                String code = normalizeCode(raw);
                if (code == null || !FITTING_CODE.matcher(code).matches()) continue; // '소계' 등 비코드 셀 제외
                BigDecimal price = dec(c, r, priceCol);
                if (price != null) idx.put(code.toLowerCase(), price);
            }
        }
    }

    /** 전산코드 형태 — 영숫자로 시작하고 영숫자·점·하이픈만. 한글 라벨('소계','니쁠')을 걸러낸다. */
    private static final Pattern FITTING_CODE = Pattern.compile("^[0-9A-Za-z][0-9A-Za-z.\\-]*$");

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
                imageKeyOf(first.row()), false, c.sheetName));
    }

    /**
     * 세트 시트 전산코드 폴백 중 품번이 알려진 항목(신규 OEM 시트의 품번↔전산코드 매핑) → 품번으로 적재해
     * OEM 시트 행과 upsert 자연 병합(P14 병합 결정, 2026-07-15). 종전엔 2행 공존 + 검수필요 플래그였다.
     */
    private static final Map<String, String> FITTING_CODE_TO_PARTNO =
            Map.of("<CODE>", "U04110", "<CODE>", "U944265");

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

    /**
     * 제품코드 '+' 조합행(U9310 건+행거, U9510~U9550) → main=품번, 부속={품번}_{전산코드}(P7).
     *
     * <p>부속 단가는 조합행에 없어 전산코드 인덱스에서 찾는다(P5F-5 후속 ②). 종전에는 0으로 두었는데,
     * 그러면 부속 합계가 0이 되어 화면 대조가 성립하지 않았다. 인덱스에 없는 구성(한글 '니쁠' 등)은
     * 여전히 0이다 — 전산코드가 없어 매칭할 근거가 없다.
     */
    private void emitFittingComboSet(List<VendorProductSet> out, Ctx c, String group, FtMember m) {
        String token = normalizeCode(firstToken(m.bRaw()));
        String pn = (token != null && token.matches("^[A-Za-z].*[A-Za-z0-9]$")) ? fittingPartNo(token) : null;
        if (pn == null) return; // 조합행은 모두 U 품번 보유(방어)

        List<VendorParsedItem> parts = new ArrayList<>();
        for (String piece : m.cRaw().split("\\+")) {
            String[] pc = splitParen(piece);               // "<전산코드>(건)" → [전산코드, 건]
            String pcode = pc[0] != null ? pc[0].toLowerCase() : pc[1]; // '니쁠' 같은 비코드 구성도 보존
            if (pcode == null) continue;
            parts.add(new VendorParsedItem(partCode(pn, pcode), orDefault(pc[1], pcode), null, null,
                    VendorParsedItem.RELATION_ACCESSORY, nz(c.fittingPrices().get(pcode)), null));
        }
        String bClean = stripSpace(m.bRaw());
        String name = orDefault(join(group, bClean), pn);
        if (m.price() == null) name = name + " (가격없음)";
        NoteSplit ns = splitFittingNote(m.remark()); // 3기능/단기능/행거포함 → description(C-2)
        VendorParsedItem main = new VendorParsedItem(pn, name, null, null,
                VendorParsedItem.RELATION_MAIN, nz(m.price()), ns.remark(), ns.description(), null, ns.specs());
        out.add(new VendorProductSet("B", "수전부속", group, main, parts, nz(m.price()), false,
                imageKeyOf(m.row()), false, c.sheetName));
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
                new ArrayList<>(), nz(price), false, imageKeyOf(row), false, c.sheetName);
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
                    nz(price), false, imageKeyOf(r), false, c.sheetName));
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

    // ============================================================
    // (V2-도기) 최신본(2026) 도기 3시트 공통 리더 — 양변기/세면기/소변기,수채.
    //
    //   구본은 부속이 열로 펼쳐지는 가로 슬롯형이었으나, 최신본은 부속이 행으로 내려오는 세로 나열형이다.
    //   세트 1건 = 여러 행이며 컬럼 규약은 세 시트가 같다(헤더에서 위치를 읽으므로 하드코딩하지 않는다).
    //
    //     A=구분  B=품종  C=품목  D=KS품번  E=품명  F=제품코드  G=제품코드(대체)  H=단가  I=計  …  규격 … 비고
    //
    //   · 세트 시작 = C(품목)와 E(품명)가 모두 있는 행. 그 행이 대표품목(MAIN)이고 I=計가 세트가.
    //   · 이후 C가 비고 E만 있는 행 = 부속. 빈 행이나 다음 품목에서 세트가 끝난다.
    //   · N~P의 부속 서브테이블은 좌측 세트와 행이 정렬되지 않는 독립 옵션 목록이라 여기서 읽지 않는다
    //     (계획서 §8 잔여 ①). 실제로 計는 E열 '양부속(대소구분)' 값을 쓰고
    //     N열 '양부속(기본)'은 쓰지 않는다 — 서브테이블이 세트 구성이 아님을 보여준다.
    // ============================================================

    private void parseToiletSheetV2(Ctx c, List<VendorProductSet> out) {
        parseDogiSheetV2(c, out, DogiV2Rules.DETERMINATE);
    }

    private void parseWashbasinSheetV2(Ctx c, List<VendorProductSet> out) {
        parseDogiSheetV2(c, out, DogiV2Rules.SELECTABLE);
    }

    private void parseUrinalSinkSheetV2(Ctx c, List<VendorProductSet> out) {
        // 한 시트에 소변기 표와 소제싱크 표가 세로로 쌓여 있고 '■'로 갈린다.
        // 대분류는 구본과 같이 시트명("소변기, 수채")을 콤마로 쪼개 순서대로 부여한다.
        parseDogiSheetV2(c, out, DogiV2Rules.DETERMINATE.withCategories(splitSheetCategories(c.sheetName)));
    }

    /**
     * 시트별 차이.
     *
     * <p>{@code selectable} — 구성에 <b>택일 항목</b>이 섞이는가. 양변기·소변기는 구성이 확정이라
     * {@code 計 = 구성합}이 성립하고 어긋나면 원본 오류다. 세면기는 도기 변형·반다리/긴다리가
     * 한 세트에 함께 실려 구성합이 언제나 {@code 計}보다 크다 — 경고로 올리면 전건이 시끄러워진다.
     *
     * <p>{@code categories} — 시트 안에 대분류가 여러 개인 경우('■'로 갈린 서브테이블) 쓸 이름들.
     * 비면 시트명을 그대로 대분류로 쓴다.
     */
    private record DogiV2Rules(boolean selectable, List<String> categories) {
        static final DogiV2Rules DETERMINATE = new DogiV2Rules(false, List.of());
        static final DogiV2Rules SELECTABLE = new DogiV2Rules(true, List.of());

        DogiV2Rules withCategories(List<String> cats) { return new DogiV2Rules(selectable, cats); }
    }

    private void parseDogiSheetV2(Ctx c, List<VendorProductSet> out, DogiV2Rules rules) {
        int headerRow = findRow(c, r -> "구분".equals(noSpace(str(c, r, 0)))
                && "품목".equals(noSpace(str(c, r, 2))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 최신본 도기 헤더(구분/품목) 미발견 → 스킵", c.sheetName);
            return;
        }
        DogiV2Cols cols = readDogiV2Header(c, headerRow);
        if (cols == null) {
            logger.warn("[B][{}] 최신본 도기 2단 헤더(단가/計) 미발견 → 스킵", c.sheetName);
            return;
        }

        int last = c.sheet.getLastRowNum();
        Map<Integer, String> codeOverrides = computeDogiV2DuplicateCodes(c, cols, headerRow, last);

        DogiV2Set cur = null;
        String lastKind = null;
        int catIdx = 0;
        String category = rules.categories().isEmpty() ? c.sheetName : rules.categories().get(0);
        for (int r = headerRow + 2; r <= last; r++) {   // 헤더는 2행짜리
            // 본표 아래에 성격이 다른 부록표가 붙는다(양변기 218행~: 구분/BOX/PLT/소프트개폐시트).
            // 그 행들은 C에 값이 있어 세트 시작으로 오인되므로, 두 번째 '구분' 헤더에서 명시적으로 끝낸다.
            if (r > headerRow && "구분".equals(noSpace(str(c, r, 0)))) break;

            // '■ 소제싱크' 같은 구역 제목 → 다음 대분류로 넘어간다.
            String head = stripSpace(str(c, r, 0));
            if (head != null && head.startsWith("■")) {
                cur = flushDogiV2(c, out, cur, rules);
                if (!rules.categories().isEmpty()) {
                    catIdx = Math.min(catIdx + 1, rules.categories().size() - 1);
                    category = rules.categories().get(catIdx);
                }
                lastKind = null;
                continue;
            }

            String item = blankOrDash(str(c, r, cols.itemCol()));      // C=품목
            String name = blankOrDash(str(c, r, cols.nameCol()));      // E=품명
            if (name == null) { cur = flushDogiV2(c, out, cur, rules); continue; }  // 빈 행 → 세트 종료

            if (item != null) {                                        // 세트 시작
                cur = flushDogiV2(c, out, cur, rules);
                String kind = blankOrDash(str(c, r, cols.kindCol()));   // B=품종(병합셀)
                if (kind != null) lastKind = kind;
                cur = startDogiV2Set(c, cols, r, item, lastKind, codeOverrides.get(r), category);
            }
            // 세트 밖의 잔여 행 — 세면기 142행 아래 '품명/제품코드/단가' 부록표가 여기 걸린다.
            // C(품목)가 없어 세트로 시작되지 않고, 직전 세트는 빈 행에서 이미 닫혔다.
            if (cur == null) continue;
            addDogiV2Row(c, cols, r, name, cur);
        }
        flushDogiV2(c, out, cur, rules);
    }

    /** 세트 시작 행에서 대표품목의 뼈대를 만든다(부속 행은 {@link #addDogiV2Row}가 채운다). */
    private DogiV2Set startDogiV2Set(Ctx c, DogiV2Cols cols, int r, String item, String kind,
                                     String codeOverride, String categoryLarge) {
        String[] rep = splitParen(item);                       // "IC552EF⏎(구륙)" → 코드 + 설명
        String repCode = rep[0];
        if (repCode == null) return null;
        String parenLabel = rep[1];                            // 길마·모노피·클레이탄… — 변형의 실제 구분자
        String desc = joinNotes(parenLabel, trailingAfterParen(item)); // "IL672(롱하우) 비누대, 폽업"의 꼬리까지
        if (codeOverride != null) {
            // 같은 품목이 구성만 다르게 두 번 나온다(L352E 자폐수전 2종, U352E 감지기 색상 2종).
            // 그대로 두면 upsert가 한 행으로 병합해 한쪽 구성이 사라지므로 접미로 갈라 둔다(§8 잔여 ⑤).
            desc = joinNotes(desc, "동일 품번 변형 " + codeOverride.substring(codeOverride.lastIndexOf('-') + 1));
            repCode = codeOverride;
        }
        String div = stripSpace(str(c, r, cols.divCol()));      // A=구분(상품/제품, 병합셀)
        if (div != null) desc = joinNotes(desc, "구분: " + div);

        DogiV2Set set = new DogiV2Set(r, repCode, kind, normalizeCode(str(c, r, cols.ksCol())), categoryLarge);
        set.parenLabel = parenLabel;
        set.description = desc;
        set.specs = cols.specCol() >= 0 ? stripSpace(str(c, r, cols.specCol())) : null;
        if (cols.waterCol() >= 0) {                             // 세면기 담수(6ℓ 등)도 규격의 일부다
            String water = stripSpace(str(c, r, cols.waterCol()));
            if (water != null && !water.equals("-")) set.specs = joinNotes(set.specs, "담수 " + water);
        }
        set.setPrice = cols.totalCol() >= 0 ? dec(c, r, cols.totalCol()) : null;
        return set;
    }

    /**
     * 품목 셀에서 첫 괄호 그룹 <b>뒤에</b> 남은 설명. {@code splitParen}은 첫 괄호까지만 보기 때문에
     * "IL672 (롱하우) 비누대, 폽업"의 꼬리("비누대, 폽업")를 놓친다.
     */
    private String trailingAfterParen(String raw) {
        String x = stripSpace(raw);
        if (x == null) return null;
        int close = x.indexOf(')');
        if (close < 0 || close + 1 >= x.length()) return null;
        return stripSpace(x.substring(close + 1));
    }

    /** 세트에 속한 행 1개(대표품목 본품 또는 부속)를 담는다. */
    private void addDogiV2Row(Ctx c, DogiV2Cols cols, int r, String name, DogiV2Set set) {
        String code = normalizeCode(str(c, r, cols.codeCol()));         // F=제품코드
        String altCode = cols.altCodeCol() >= 0 ? normalizeCode(str(c, r, cols.altCodeCol())) : null;
        BigDecimal price = cols.priceCol() >= 0 ? dec(c, r, cols.priceCol()) : null;
        DogiV2Note note = splitDogiV2Note(cols.noteCol() >= 0 ? str(c, r, cols.noteCol()) : null,
                hasSubTableEntry(c, cols, r));

        String label = normLabel(name);
        boolean isMain = set.rows.isEmpty();                            // 세트 첫 행이 대표품목(도기/하부)
        String desc = null;
        if (altCode != null && !altCode.equalsIgnoreCase(code)) desc = "대체코드: " + altCode;
        desc = joinNotes(desc, note.description());

        if (isMain) {
            set.description = joinNotes(set.description, note.description());
            set.remark = joinNotes(set.remark, note.remark());
            if (altCode != null && !altCode.equalsIgnoreCase(code)) {
                set.description = joinNotes(set.description, "대체코드: " + altCode);
            }
            desc = null;
        }
        set.partSum = set.partSum.add(nz(price));
        set.rows.add(new VendorParsedItem(partCode(set.repCode, code), label, null, null,
                isMain ? VendorParsedItem.RELATION_MAIN : label,
                nz(price), isMain ? null : note.remark(), desc));
    }

    private DogiV2Set flushDogiV2(Ctx c, List<VendorProductSet> out, DogiV2Set set, DogiV2Rules rules) {
        if (set == null || set.rows.isEmpty()) return null;

        if (!rules.selectable() && set.setPrice != null && set.partSum.compareTo(set.setPrice) != 0) {
            logger.warn("[B][{}] 計≠구성합 (품목={}, 計={}, 합={})",
                    c.sheetName, set.repCode, set.setPrice, set.partSum);
        }
        // 같은 부속이 한 세트에 2개 들어가는 경우(S132E 수채가량 ×2, L352E 앵글밸브 ×2).
        // 원본이 행을 두 번 적고 計도 두 번 더한다. 적재 단계가 반복 행을 세어 관계 수량으로 담는다(§8 잔여 ② 해소).
        long distinct = set.rows.stream().map(VendorParsedItem::productCode).distinct().count();
        if (distinct < set.rows.size()) {
            logger.info("[B][{}] 세트에 같은 부속이 여러 개 — 관계 수량으로 저장된다 (품목={}, 구성행={}, 고유={})",
                    c.sheetName, set.repCode, set.rows.size(), distinct);
        }
        // 동일 품번 변형의 구분자는 원본 괄호 라벨(길마·모노피·클레이탄…)이다. 코드의 -2 접미만으로는
        // 화면에서 구분이 안 되므로 이름에 붙인다. 코드는 그대로 둬 제품 identity를 건드리지 않는다(§8 잔여 ⑤).
        String repName = join(set.kind, set.repCode);
        if (set.parenLabel != null) repName = repName + " (" + set.parenLabel + ")";
        if (set.setPrice == null) repName = repName + " (가격없음)"; // D8

        VendorParsedItem main = new VendorParsedItem(set.repCode, repName, null, set.ksCode,
                VendorParsedItem.RELATION_MAIN, nz(set.setPrice), set.remark, set.description, null, set.specs);
        // 대분류가 시트명과 다를 수 있다(소변기,수채 → 소변기 / 수채). 이미지 매칭 키는 시트명이라
        // 10-인자 생성자로 시트명을 따로 넘긴다(§13 sheetName 분리) — 안 그러면 수채 3건의 이미지가 끊긴다.
        // 이로써 V2 도기 3시트는 priceBasis도 시트명이 된다(가격 분리 기준 = 시트, 일관).
        out.add(new VendorProductSet("B", set.categoryLarge, set.kind, main, set.rows,
                set.setPrice, false, imageKeyOf(set.startRow), false, c.sheetName));
        return null;
    }

    /**
     * 값이 없거나 대시 플레이스홀더면 null. 소변기 42~47행은 좌측 컬럼이 전부 {@code -}이고
     * 우측 부속 서브테이블만 채워져 있다 — 대시를 값으로 읽으면 품번이 {@code -}인 세트가 생긴다.
     */
    private String blankOrDash(String raw) {
        String x = stripSpace(raw);
        if (x == null) return null;
        return x.matches("^[-\u2010-\u2015\uFF0D]+$") ? null : x;
    }

    /**
     * 같은 품목 코드가 시트 안에서 두 번 이상 세트로 나오면 2번째부터 {@code -2}, {@code -3}… 접미를 붙인다.
     * (행 → 최종 코드) 맵을 돌려주며, 중복이 없으면 빈 맵이다.
     */
    private Map<Integer, String> computeDogiV2DuplicateCodes(Ctx c, DogiV2Cols cols, int headerRow, int last) {
        Map<String, List<Integer>> byCode = new LinkedHashMap<>();
        for (int r = headerRow + 2; r <= last; r++) {
            if ("구분".equals(noSpace(str(c, r, 0)))) break;
            String item = stripSpace(str(c, r, cols.itemCol()));
            if (item == null || stripSpace(str(c, r, cols.nameCol())) == null) continue;
            String code = splitParen(item)[0];
            if (code != null) byCode.computeIfAbsent(code, k -> new ArrayList<>()).add(r);
        }
        Map<Integer, String> out = new HashMap<>();
        byCode.forEach((code, rows) -> {
            if (rows.size() < 2) return;
            logger.warn("[B][{}] 동일 품번이 {}회 — 구성이 다른 별개 세트로 보고 접미를 붙인다 (품목={})",
                    c.sheetName, rows.size(), code);
            for (int i = 1; i < rows.size(); i++) out.put(rows.get(i), code + "-" + (i + 1));
        });
        return out;
    }

    /** 최신본 도기 2단 헤더에서 컬럼 위치를 읽는다. 단가·計를 못 찾으면 null. */
    private DogiV2Cols readDogiV2Header(Ctx c, int headerRow) {
        int kindCol = -1, itemCol = -1, ksCol = -1, nameCol = -1, specCol = -1, noteCol = -1;
        short lastCell = c.sheet.getRow(headerRow).getLastCellNum();
        for (int col = 0; col < lastCell; col++) {
            String h = noSpace(str(c, headerRow, col));
            if (h == null) continue;
            if (h.equals("품종") && kindCol < 0) kindCol = col;
            else if (h.equals("품목") && itemCol < 0) itemCol = col;
            else if (h.contains("KS품번") && ksCol < 0) ksCol = col;
            else if (h.equals("품명") && nameCol < 0) nameCol = col;
            else if (h.equals("규격") && specCol < 0) specCol = col;
            else if (h.contains("비고") && noteCol < 0) noteCol = col;
        }
        // 2단 헤더 아랫줄: 제품코드 / 제품코드(대체) / 단가 / 計 … 그리고 부속 서브테이블의 제품코드.
        // '제품코드'가 세 번 나오면 세 번째가 N~P 부속 서브테이블의 것이다(세면기는 서브테이블이 없어 두 번).
        int codeCol = -1, altCodeCol = -1, subCodeCol = -1, priceCol = -1, totalCol = -1, waterCol = -1;
        Row sub = c.sheet.getRow(headerRow + 1);
        short subLast = sub == null ? 0 : sub.getLastCellNum();
        for (int col = 0; col < subLast; col++) {
            String h = noSpace(str(c, headerRow + 1, col));
            if (h == null) continue;
            if (h.equals("제품코드")) {
                if (codeCol < 0) codeCol = col;
                else if (altCodeCol < 0) altCodeCol = col;
                else if (subCodeCol < 0) subCodeCol = col;
            }
            else if (h.equals("단가") && priceCol < 0) priceCol = col;
            else if ((h.equals("計") || h.equals("계")) && totalCol < 0) totalCol = col;
            else if (h.equals("담수") && waterCol < 0) waterCol = col;  // 세면기 전용(규격 병합의 둘째 칸)
        }
        if (codeCol < 0 || priceCol < 0 || totalCol < 0 || itemCol < 0 || nameCol < 0) return null;
        return new DogiV2Cols(0, kindCol, itemCol, ksCol, nameCol, codeCol, altCodeCol, subCodeCol,
                priceCol, totalCol, specCol, waterCol, noteCol);
    }

    /** 이 행이 N~P 부속 서브테이블에 항목을 갖고 있는가(= 비고가 그쪽 설명일 수 있는가). */
    private boolean hasSubTableEntry(Ctx c, DogiV2Cols cols, int r) {
        return cols.subCodeCol() >= 0 && str(c, r, cols.subCodeCol()) != null;
    }

    /**
     * 최신본 도기 비고 분류(R7 / C-2 원칙) — 줄 단위로 나눠 상태 변동(단종)은 {@code remark},
     * 나머지 설명은 {@code description}으로 보낸다. 한 셀에 "소진 후 단종"과 기능 설명이
     * 줄바꿈으로 함께 들어오기 때문에 셀 전체를 한 덩어리로 판정하면 안 된다.
     *
     * <p><b>{@code hasSubTableEntry}가 참이면 설명은 버린다.</b> 비고(Q)는 구조상 행 전체 컬럼이지만
     * (헤더 병합이 {@code N3:P3=부속} / {@code Q3:Q4=비고}로 갈려 있다), 실제 내용은 그 행에
     * 부속 서브테이블 항목이 있으면 <b>그쪽</b>을 설명한다 — IC552EF 구간의 "막대형 일반 세척밸브, 3등급"은
     * 좌측 스퍼드가 아니라 우측 F/V 옵션의 설명이다. 서브테이블은 저장하지 않으므로(§8 잔여 ①)
     * 이런 설명은 버리는 편이 좌측 부속에 잘못 붙이는 것보다 낫다.
     * 단, {@code 단종}은 옵션이 아니라 제품 상태라 서브테이블 유무와 무관하게 남긴다.
     */
    private DogiV2Note splitDogiV2Note(String raw, boolean hasSubTableEntry) {
        if (raw == null || raw.isBlank()) return DogiV2Note.EMPTY;
        // 같은 셀 안의 줄바꿈은 대부분 좁은 컬럼에서 문장이 접힌 것이라 공백으로 잇는다
        // ("NB 모델은⏎<전산코드>만⏎가능"). ' / '로 이으면 한 문장이 조각나 보인다.
        StringBuilder remark = new StringBuilder(), desc = new StringBuilder();
        for (String line : raw.split("\\R")) {
            String s = stripSpace(line);
            if (s == null) continue;
            StringBuilder target = s.replaceAll("\\s", "").contains("단종") ? remark
                    : (hasSubTableEntry ? null : desc);
            if (target == null) continue;
            if (target.length() > 0) target.append(' ');
            target.append(s);
        }
        return new DogiV2Note(remark.length() == 0 ? null : remark.toString(),
                desc.length() == 0 ? null : desc.toString());
    }

    private record DogiV2Note(String remark, String description) {
        static final DogiV2Note EMPTY = new DogiV2Note(null, null);
    }

    /** 최신본 도기 시트의 컬럼 위치(헤더에서 읽는다). 없는 컬럼은 -1. */
    private record DogiV2Cols(int divCol, int kindCol, int itemCol, int ksCol, int nameCol,
                              int codeCol, int altCodeCol, int subCodeCol, int priceCol, int totalCol,
                              int specCol, int waterCol, int noteCol) {}

    /** 조립 중인 세트 1건(대표품목 + 부속 행들). */
    private static final class DogiV2Set {
        final int startRow;
        final String repCode;
        final String kind;
        final String ksCode;
        final String categoryLarge;
        final List<VendorParsedItem> rows = new ArrayList<>();
        BigDecimal partSum = BigDecimal.ZERO;
        BigDecimal setPrice;
        String description;
        String remark;
        String specs;
        /** 품목 셀의 괄호 라벨(길마·모노피·클레이탄…). 동일 품번 변형의 실제 구분자라 세트명에 붙인다(§8 잔여 ⑤). */
        String parenLabel;

        DogiV2Set(int startRow, String repCode, String kind, String ksCode, String categoryLarge) {
            this.startRow = startRow;
            this.repCode = repCode;
            this.kind = kind;
            this.ksCode = ksCode;
            this.categoryLarge = categoryLarge;
        }
    }

    // ============================================================
    // (V2-액세사리) 최신본(2026) 액세사리류 — 헤더총가 세트형 + 일반품.
    //
    //   컬럼: A=제품(세트명) B=품번 C=전산코드 D=품명 E=규격 F=대리점가 G=수량/BOX H=비고
    //         I=공급처 J~L=참고(대림비앤코)  ← I·J~L은 우리 데이터가 아니라 저장하지 않는다(R7 ④)
    //   구본 '악세사리 단가표' 대비 컬럼이 2칸 왼쪽으로 밀렸다(구 A=품목·B=세부분류가 삭제).
    //
    //   · 세트 시작 = 규격(E)이 'SET'인 행. 세트가는 F.
    //     "A열에 값이 있으면 세트"로 판정하면 100행 이후 시리즈 라벨(DT 20A…)이 전부 세트가 된다.
    //   · 세트 구성 = 품명(D)의 'N품'이 말하는 만큼만. 그 뒤 옷걸이처럼 덤으로 붙는 행은
    //     세트가에 안 들어가므로(4품 합과 옷걸이 포함 합이 다르다) 단일품으로 뺀다.
    // ============================================================

    private static final Pattern ACC_SET_COUNT = Pattern.compile("(\\d+)\\s*품");

    private void parseAccessorySheetV2(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "품번".equals(noSpace(str(c, r, 1)))
                && "전산코드".equals(noSpace(str(c, r, 2))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 액세사리 헤더(품번/전산코드) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();
        Set<String> ambiguous = duplicateAccPartNos(c, headerRow, last);

        VendorParsedItem setMain = null;
        List<VendorParsedItem> parts = null;
        String setCat = null;
        BigDecimal setPrice = null;
        int setRow = -1, remaining = 0;
        String lastName = null;                    // 품명(D) 병합셀 carry-forward(165~186행 손잡이 구간)

        for (int r = headerRow + 1; r <= last; r++) {
            String pn = normalizeCode(str(c, r, 1));            // B=품번
            String ecode = normalizeCode(str(c, r, 2));         // C=전산코드
            if (pn == null && ecode == null) continue;

            String name = stripSpace(str(c, r, 3));             // D=품명(병합)
            if (name != null) lastName = name; else name = lastName;
            String spec = stripSpace(str(c, r, 4));             // E=규격 또는 'SET'
            BigDecimal price = dec(c, r, 5);                    // F=대리점가
            DogiV2Note note = splitDogiV2Note(str(c, r, 7), false); // H=비고
            String code = accCode(pn, ecode, ambiguous);
            if (code == null) continue;

            if (spec != null && spec.replace(" ", "").equalsIgnoreCase("SET")) {   // ── 세트 대표행
                flushAccSetV2(c, out, setMain, parts, setCat, setPrice, setRow);
                setCat = stripSpace(str(c, r, 0));              // A=세트명("AC8100 4품 세트")
                setPrice = price;
                setRow = r;
                remaining = accSetCount(name, setCat);
                // 세트가가 회계서식 0이면 DataFormatter가 '-'로 내주고 단가는 비게 된다(AC5300, 단종품).
                String setName = orDefault(setCat, code) + (price == null ? " (가격없음)" : ""); // D8
                setMain = new VendorParsedItem(code, setName, null, null,
                        VendorParsedItem.RELATION_MAIN, nz(price), note.remark(), note.description());
                parts = new ArrayList<>();
                continue;
            }

            if (setMain != null && remaining > 0 && stripSpace(str(c, r, 0)) == null) {  // ── 세트 구성행
                String pName = orDefault(name, code);
                parts.add(new VendorParsedItem(partCode(setMain.productCode(), code), pName, null, null,
                        pName, nz(price), note.remark(), note.description()));
                remaining--;
                continue;
            }

            // ── 단일품(세트 종료 포함). 세트 뒤에 덤으로 붙는 옵션행도 여기로 온다.
            flushAccSetV2(c, out, setMain, parts, setCat, setPrice, setRow);
            setMain = null; parts = null; setCat = null; setPrice = null; remaining = 0;

            String descr = (spec != null && !spec.equals(name)) ? spec : null; // 규격이 품명과 같으면 중복이라 생략
            String single = stripSpace(str(c, r, 0));           // A=시리즈/구분(DT 20A, 750mm…)
            VendorParsedItem item = new VendorParsedItem(code, orDefault(name, code), null, null,
                    VendorParsedItem.RELATION_MAIN, nz(price), note.remark(),
                    joinNotes(descr, note.description()));
            out.add(new VendorProductSet("B", "악세사리", single, item,
                    new ArrayList<>(), nz(price), false, imageKeyOf(r), false, c.sheetName));
        }
        flushAccSetV2(c, out, setMain, parts, setCat, setPrice, setRow);
    }

    private void flushAccSetV2(Ctx c, List<VendorProductSet> out, VendorParsedItem main,
                               List<VendorParsedItem> parts, String setCat, BigDecimal setPrice, int setRow) {
        if (main == null) return;
        // 대분류=악세사리 고정(C-1) — 시트명('액세사리류')을 대분류로 쓰지 않는다. 이미지 키는 시트명(D52).
        out.add(new VendorProductSet("B", "악세사리", setCat, main,
                parts != null ? parts : new ArrayList<>(), setPrice, false,
                imageKeyOf(setRow), false, c.sheetName));
    }

    /** 세트 구성 품수 — 품명 "AC8100(4품)" 우선, 없으면 세트명 "AC8100 4품 세트". 못 읽으면 4로 본다. */
    private int accSetCount(String name, String setLabel) {
        for (String s : new String[]{name, setLabel}) {
            if (s == null) continue;
            Matcher m = ACC_SET_COUNT.matcher(s);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        logger.warn("[B] 액세사리 세트 품수를 못 읽었다 (품명={}, 세트명={}) → 4품으로 본다", name, setLabel);
        return 4;
    }

    /**
     * 품번이 시트 안에서 유일하지 않으면 전산코드를 붙여 가른다.
     * {@code AC9320}은 비누대·휴지걸이·수건걸이·컵대 4행이 같은 품번을 쓴다 — 그대로면 한 제품으로 병합된다.
     * {@code U}로 시작하는 품번은 수전부속 품번 체계와 겹치므로(U9120) 시트 안에서 유일해도 갈라 둔다(구본 A1 정책).
     */
    private String accCode(String pn, String ecode, Set<String> ambiguous) {
        if (pn == null) return ecode;
        if (ecode != null && (ambiguous.contains(pn) || pn.startsWith("U"))) return pn + "-" + ecode;
        return pn;
    }

    private Set<String> duplicateAccPartNos(Ctx c, int headerRow, int last) {
        Map<String, String> firstCode = new HashMap<>();
        Set<String> dup = new HashSet<>();
        for (int r = headerRow + 1; r <= last; r++) {
            String pn = normalizeCode(str(c, r, 1));
            String ecode = normalizeCode(str(c, r, 2));
            if (pn == null || ecode == null) continue;
            String prev = firstCode.putIfAbsent(pn, ecode);
            if (prev != null && !prev.equalsIgnoreCase(ecode)) dup.add(pn); // 완전 중복행은 upsert가 흡수한다
        }
        if (!dup.isEmpty()) {
            logger.info("[B][{}] 품번이 겹치는 항목 {}건 → 전산코드를 붙여 구분 {}", c.sheetName, dup.size(), dup);
        }
        return dup;
    }

    // ============================================================
    // (V2-부속류) 최신본(2026) 부속류 — 순수 부속 카탈로그.
    //
    //   컬럼: A=품명(그룹) B=품번 C=제품코드 D=단위 E=수량 F=단가 G=이미지 H=비고
    //   구본 '수전 부속(세트)'와 레이아웃은 같지만 소계행이 하나도 없다 → 세트를 만들지 않는다(D-B5).
    //   141행 아래에 니쁠 부표(B=품목 C=제품코드 D=단가 E=규격)가 다른 레이아웃으로 붙는다.
    //
    //   식별자는 품번(B)이 아니라 <b>전산코드(C)</b>다. 구·신 코드가 병존해
    //   같은 품번이 서로 다른 전산코드를 갖는 쌍이 14개 있다(예: 냉수용 한 품번에 구버전·신규 코드가 따로).
    //   품번을 코드로 쓰면 이 쌍이 한 제품으로 병합된다. T7의 품번표 조인 키도 전산코드다.
    // ============================================================

    /**
     * 부속류 D열 단위를 정규화한다. 알려진 토큰만 받고 나머지는 null(→ 기본값 SET).
     *
     * <p>화이트리스트인 이유는 D열이 깨끗하지 않기 때문이다 — 실측하면 {@code ea} 118건·{@code SET} 13건·
     * {@code 조} 4건 외에 단가 숫자와 {@code '단가'} 머리글이 섞여 있다(부표 헤더가 밀려 들어온 행).
     * 이 값들을 그대로 저장하면 단위 자리에 금액이 들어간다.
     */
    private static String normalizeUnit(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.equalsIgnoreCase("ea")) return "EA";
        if (v.equalsIgnoreCase("set")) return "SET";
        if (v.equals("조")) return "조";
        return null;
    }

    private void parseFittingCatalogSheetV2(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "품명".equals(noSpace(str(c, r, 0)))
                && "품번".equals(noSpace(str(c, r, 1))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 부속류 헤더(품명/품번) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();

        boolean nipple = false;
        String group = null;
        Map<String, BigDecimal> emitted = new LinkedHashMap<>(); // 전산코드 → 처음 본 단가

        for (int r = headerRow + 1; r <= last; r++) {
            // 니쁠 부표 서브헤더 → 이 아래는 컬럼 배치가 다르다
            if ("품목".equals(noSpace(str(c, r, 1))) && "제품코드".equals(noSpace(str(c, r, 2)))) {
                nipple = true;
                group = null;
                continue;
            }

            String code = normalizeCode(str(c, r, 2));   // C=제품코드(전산코드)
            if (code == null) continue;
            code = code.toLowerCase();                    // 대소문자 오타 흡수(43U9113, 구본 P9)

            String label = stripSpace(str(c, r, 1));      // 니쁠 부표는 B=품목, 본표는 B=품번
            BigDecimal price;
            String spec = null, remark = null, unit = null;
            if (nipple) {
                if (label != null) group = label;         // '니쁠' (병합셀)
                price = dec(c, r, 3);                     // D=단가
                spec = stripSpace(str(c, r, 4));          // E=규격
                label = null;                             // 니쁠 부표엔 품번이 없다
            } else {
                String aRaw = stripSpace(str(c, r, 0));   // A=품명(그룹, 병합셀)
                if (aRaw != null) group = aRaw;
                price = dec(c, r, 5);                     // F=단가
                remark = stripSpace(str(c, r, 7));        // H=비고
                unit = normalizeUnit(str(c, r, 3));       // D=단위 (니쁠 부표는 D가 단가라 본표에서만 읽는다)
            }

            // 같은 전산코드가 여러 그룹에 다시 등장한다(메탈호스는 6번). 단가는 전부 같으므로
            // 처음 본 그룹의 이름을 canonical로 삼고 이후는 건너뛴다 — 안 그러면 upsert 순서에 따라
            // '가로꼭지(2구)'가 '발코니수전 U9510'으로 덮인다.
            BigDecimal prev = emitted.putIfAbsent(code, nz(price));
            if (prev != null) {
                if (prev.compareTo(nz(price)) != 0) {
                    logger.warn("[B][{}] 같은 전산코드에 다른 단가 (코드={}, 처음={}, {}행={})",
                            c.sheetName, code, prev, r + 1, price);
                }
                continue;
            }

            String name = orDefault(join(group, label), join(group, code));
            if (spec != null) name = join(name, "(" + spec + ")");
            out.add(fittingSingleV2(c, group, code, name, price, remark, spec, unit, r));
        }
    }

    /**
     * 부속 카탈로그 1건 방출. 구본 {@link #fittingSingle}과 같은 모양이되 <b>규격을 받는다</b> —
     * 니쁠 부표의 {@code 65mm}는 R7 ③에 따라 {@code specs}로 가야 하는데 구본 헬퍼는 비고에서만 규격을 뽑는다.
     * (구본 헬퍼에 인자를 더하면 구본 호출부를 건드리게 되어 R2′에 걸린다.)
     */
    private VendorProductSet fittingSingleV2(Ctx c, String catSmall, String code, String name,
                                             BigDecimal price, String remark, String spec, String unit, int row) {
        if (price == null) name = name + " (가격없음)"; // D8
        NoteSplit ns = splitFittingNote(remark);        // 단종→remark / 규격→specs / 매입처→미저장
        VendorParsedItem main = new VendorParsedItem(code, name, null, null,
                VendorParsedItem.RELATION_MAIN, nz(price), ns.remark(), ns.description(),
                null, orDefault(spec, ns.specs()), unit);
        return new VendorProductSet("B", "수전부속", catSmall, main,
                new ArrayList<>(), nz(price), false, imageKeyOf(row), false, c.sheetName);
    }

    // ============================================================
    // (V2-수전금구) 최신본(2026) 수전금구류 — 단일 제품 목록.
    //
    //   컬럼(2단 헤더): B=시리즈 C=품목 D=이미지 E=품번 F=전산코드 | G=대리점가 H=박스 기준 I=비고
    //
    //   구본 `parseFaucetGeneralSheet`가 형태상 읽기는 하나 두 군데가 어긋난다.
    //     · 시트명을 "수전금구"로 고정해 이미지 맵(키=실제 시트명 '수전금구류')과 안 맞는다 → 285장 유실
    //     · 전산코드(F)를 버린다 → T7의 품번표 조인 키가 사라진다
    //   구본 메서드는 그대로 두고(R2′) 여기서 새로 읽는다.
    // ============================================================

    private void parseFaucetSheetV2(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> "시리즈".equals(noSpace(str(c, r, 1)))
                && "품번".equals(noSpace(str(c, r, 4)))
                && "전산코드".equals(noSpace(str(c, r, 5))));
        if (headerRow < 0) {
            logger.warn("[B][{}] 수전금구 헤더(시리즈/품번/전산코드) 미발견 → 스킵", c.sheetName);
            return;
        }
        int last = c.sheet.getLastRowNum();
        Set<String> ambiguous = duplicateFaucetPartNos(c, headerRow, last);

        String series = null;
        for (int r = headerRow + 1; r <= last; r++) {
            String pn = normalizeCode(str(c, r, 4));            // E=품번
            String ecode = normalizeCode(str(c, r, 5));         // F=전산코드
            if (pn == null && ecode == null) continue;

            String seriesRaw = stripSpace(str(c, r, 1));        // B=시리즈(병합셀)
            if (seriesRaw != null) series = seriesRaw;
            String kind = stripSpace(str(c, r, 2));             // C=품목
            BigDecimal price = dec(c, r, 6);                    // G=대리점가
            String box = stripSpace(str(c, r, 7));              // H=박스 기준
            DogiV2Note note = splitDogiV2Note(str(c, r, 8), false); // I=비고(줄 단위로 단종/설명 분리)

            // 품번이 겹치면(제조사 변경으로 같은 품번이 두 벌) 전산코드를 붙여 가른다.
            String code = pn == null ? ecode
                    : (ecode != null && ambiguous.contains(pn) ? pn + "-" + ecode : pn);
            if (code == null) continue;

            String name = orDefault(join(kind, code), code);
            if (price == null) name = name + " (가격없음)";     // D8 — '26 신상품 12건은 단가가 비어 있다
            String desc = joinNotes(box == null ? null : stripSpace(box.replaceAll("\\R", " ")),
                    note.description());

            // 전산코드는 subItemCode로 보존한다 — T7이 품번표와 조인하는 키이고, 화면에서도 보조 코드다.
            VendorParsedItem main = new VendorParsedItem(code, name, null, ecode,
                    VendorParsedItem.RELATION_MAIN, nz(price), note.remark(), desc);
            out.add(new VendorProductSet("B", "수전금구", series, main,
                    new ArrayList<>(), nz(price), false, imageKeyOf(r), false, c.sheetName));
        }
    }

    /** 시트 안에서 서로 다른 전산코드를 갖는 같은 품번(제조사 변경 병존 6쌍). */
    private Set<String> duplicateFaucetPartNos(Ctx c, int headerRow, int last) {
        Map<String, String> first = new HashMap<>();
        Set<String> dup = new HashSet<>();
        for (int r = headerRow + 1; r <= last; r++) {
            String pn = normalizeCode(str(c, r, 4));
            String ecode = normalizeCode(str(c, r, 5));
            if (pn == null || ecode == null) continue;
            String prev = first.putIfAbsent(pn, ecode);
            if (prev != null && !prev.equalsIgnoreCase(ecode)) dup.add(pn);
        }
        if (!dup.isEmpty()) {
            logger.info("[B][{}] 품번이 겹치는 항목 {}건 → 전산코드를 붙여 구분 {}", c.sheetName, dup.size(), dup);
        }
        return dup;
    }

    // ============================================================
    // (V2-바스) 최신본(2026) 바스 4시트(직영) — 선반 / 파티션·욕조 / 천정재 / 욕실장·거울.
    //
    //   2단 헤더이고 컬럼 규약이 같다. 다만 천정재만 이미지 컬럼이 없어 한 칸씩 왼쪽이라
    //   위치를 하드코딩하지 않고 헤더에서 읽는다.
    //     A=구분  [B=이미지]  전산코드  명  규격  단위|세트명  수량  판매점단가  인테리어가  소비자가  비고 …
    //
    //   가격은 3단이지만 <b>판매점 단가만</b> 저장한다(D-B3). 인테리어가·소비자가를 쓰려면
    //   VendorItemPrice에 축이 필요해 모델 변경이 따른다.
    // ============================================================

    /**
     * 바스 시트에서 건너뛸 전산코드 — 같은 제품이 {@code 액세사리류} 시트에도 실려 있다(§8 잔여 ⑦).
     *
     * <p>두 시트가 서로 다른 코드 축을 쓴다 — 액세사리류는 품번, 바스는 전산코드다.
     * 그래서 upsert가 병합하지 못하고 <b>같은 제품이 대분류만 다르게 2건</b> 생긴다(단가는 4건 모두 동일).
     * 수건선반·유리 코너선반은 성격상 액세사리이고, 액세사리류가 228코드짜리 종합 카탈로그라 그쪽을 정본으로 삼는다
     * (2026-08-27 결정).
     *
     * <p>코드 목록을 박아 두는 이유는 시트 간 조회로 풀 수 없기 때문이다 — 파서는 합본뿐 아니라
     * 시트별 단일 파일로도 돌아서, 바스 파일만 읽을 때는 액세사리류 시트가 아예 없다.
     */
    private static final Set<String> ACCESSORY_OWNED_BATH_CODES =
            Set.of("<CODE>", "<CODE>", "<CODE>", "<CODE>");

    private void parseBathSheetV2(Ctx c, List<VendorProductSet> out) {
        int headerRow = findRow(c, r -> findColByHeader(c, r, h -> h.contains("판매점")) >= 0);
        if (headerRow < 0) {
            logger.warn("[B][{}] 바스 헤더(판매점 단가) 미발견 → 스킵", c.sheetName);
            return;
        }
        int priceCol = findColByHeader(c, headerRow, h -> h.contains("판매점"));
        int imageCol = findColByHeader(c, headerRow, h -> h.equals("이미지"));
        int noteCol = findColByHeader(c, headerRow, h -> h.contains("비고"));

        int sub = headerRow + 1;
        int codeCol = findColByHeader(c, sub, h -> h.equals("전산코드"));
        int nameCol = findColByHeader(c, sub, h -> h.equals("명"));
        int specCol = findColByHeader(c, sub, h -> h.equals("규격"));
        int setNameCol = findColByHeader(c, sub, h -> h.equals("세트명"));  // 욕실장·거울만
        if (codeCol < 0 || priceCol < 0) {
            logger.warn("[B][{}] 바스 컬럼(전산코드/판매점 단가) 미발견 → 스킵", c.sheetName);
            return;
        }

        String sheetGroup = bathCategorySmall(c.sheetName);
        String group = sheetGroup;
        Map<String, BigDecimal> emitted = new LinkedHashMap<>();

        for (int r = sub + 1; r <= c.sheet.getLastRowNum(); r++) {
            // 이미지 컬럼에 품목군 라벨이 섞여 온다(파티션·욕조의 '샤워파티션'/'민자형'). 그림은 글자가 없다.
            if (imageCol >= 0) {
                String label = stripSpace(str(c, r, imageCol));
                if (label != null) group = label;
            }
            String code = normalizeCode(str(c, r, codeCol));
            if (code == null) continue;
            code = code.toLowerCase();                       // 대소문자 표기가 섞여 있다(45T1322S)
            if (ACCESSORY_OWNED_BATH_CODES.contains(code)) { // §8 잔여 ⑦ — 액세사리류가 정본
                logger.info("[B][{}] 액세사리류와 중복이라 건너뛴다 (전산코드={}, {}행)", c.sheetName, code, r + 1);
                continue;
            }

            BigDecimal price = dec(c, r, priceCol);
            BigDecimal prev = emitted.putIfAbsent(code, nz(price));
            if (prev != null) {
                if (prev.compareTo(nz(price)) != 0) {
                    logger.warn("[B][{}] 같은 전산코드에 다른 단가 (코드={}, 처음={}, {}행={})",
                            c.sheetName, code, prev, r + 1, price);
                }
                continue;
            }

            String name = nameCol >= 0 ? stripSpace(str(c, r, nameCol)) : null;
            String specs = specCol >= 0 ? stripSpace(str(c, r, specCol)) : null;
            DogiV2Note note = splitDogiV2Note(collectTrailingNotes(c, r, noteCol), false);

            String descr = note.description();
            if (setNameCol >= 0) {                            // '노블리젠시'처럼 세트명이 오지만 'ea'도 섞인다
                String setName = stripSpace(str(c, r, setNameCol));
                if (setName != null && !setName.equalsIgnoreCase("ea")) descr = joinNotes(setName, descr);
            }

            String display = orDefault(name, code);
            if (price == null) display = display + " (가격없음)"; // D8
            VendorParsedItem main = new VendorParsedItem(code, display, null, null,
                    VendorParsedItem.RELATION_MAIN, nz(price), note.remark(), descr, null, specs);
            out.add(new VendorProductSet("B", "바스", group, main,
                    new ArrayList<>(), nz(price), false, imageKeyOf(r), false, c.sheetName));
        }
    }

    /** 비고 컬럼부터 행 끝까지 모은다 — '단종'이 비고 오른쪽의 라벨 없는 컬럼에 따로 들어온다. */
    private String collectTrailingNotes(Ctx c, int r, int noteCol) {
        if (noteCol < 0) return null;
        Row row = c.sheet.getRow(r);
        if (row == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int col = noteCol; col < row.getLastCellNum(); col++) {
            String v = stripSpace(str(c, r, col));
            if (v == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(v);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** "바스 욕실장,거울(직영)" → "욕실장,거울". 대분류는 '바스'로 통일하고 시트별 품목군만 남긴다. */
    private String bathCategorySmall(String sheetName) {
        String s = sheetName.replaceAll("\\(.*?\\)", "").trim();
        if (s.startsWith("바스")) s = s.substring(2).trim();
        return s.isEmpty() ? sheetName : s;
    }

    /** 헤더 행에서 조건에 맞는 첫 컬럼. 없으면 -1. */
    private int findColByHeader(Ctx c, int r, Predicate<String> match) {
        Row row = c.sheet.getRow(r);
        if (row == null) return -1;
        for (int col = 0; col < row.getLastCellNum(); col++) {
            String h = noSpace(str(c, r, col));
            if (h != null && match.test(h)) return col;
        }
        return -1;
    }

    /**
     * 임베디드 이미지 매칭 키 = 대표품목의 0-based 행 인덱스(없으면 null).
     * 시트 식별은 {@code VendorProductSet.sheetName}이 담당한다(§13 sheetName 분리).
     */
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

    private RuntimeException wrap(String msg, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return new RuntimeException(msg + ": " + root.getClass().getName() + " - " + root.getMessage(), e);
    }

    /** 시트 1개 파싱 컨텍스트(POI 객체 묶음). */
    /**
     * @param fittingPrices 수전부속 전산코드(소문자) → 단가. 조합행(P7)에서만 쓴다. 다른 패밀리는 참조하지 않는다.
     */
    private record Ctx(Sheet sheet, DataFormatter fmt, FormulaEvaluator ev, String sheetName,
                       Map<String, BigDecimal> fittingPrices) {}
}
