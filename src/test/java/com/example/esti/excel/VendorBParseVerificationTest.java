package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P6 통합 검증(파서/추출기 레벨 불변식): 합계 무결성 + 이미지 매칭 커버리지.
 * (DB 적재/재업로드 멱등/imageUrl 연결은 {@code CatalogImportBIntegrationTest}가 담당)
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class VendorBParseVerificationTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    /** 計 = 부속 단가 합이 성립해야 하는 시트(슬롯/갈라시아). 악세사리·소계세트는 구조상 제외. */
    private static final Set<String> EXACT_SUM_SHEETS = Set.of("양변기", "소변기, 수채", "갈라시아");

    private final VendorBExcelParser parser = new VendorBExcelParser();
    private final ExcelImageExtractor extractor = new ExcelImageExtractor();

    @Test
    void 슬롯_갈라시아_세트는_세트가가_부속단가합과_정확히_일치() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        List<VendorProductSet> sets = parser.parseSets(SAMPLE);

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (VendorProductSet s : sets) {
            if (!EXACT_SUM_SHEETS.contains(s.categoryLarge())) continue;
            if (s.selectable() || s.parts().isEmpty() || s.setPrice() == null) continue;
            checked++;
            BigDecimal sum = s.parts().stream().map(VendorParsedItem::unitPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(s.setPrice()) != 0) {
                violations.add(s.categoryLarge() + "/" + s.main().productCode()
                        + " 計=" + s.setPrice() + " 부속합=" + sum);
            }
        }
        assertTrue(checked > 50, "검증 대상 세트가 충분히 있어야 함(=" + checked + ")");
        assertTrue(violations.isEmpty(), "計≠부속합 세트: " + violations);
    }

    @Test
    void 세면기는_기본구성으로_세트가_산정되고_대체옵션은_세트가_제외() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        List<VendorProductSet> sets = parser.parseSets(SAMPLE);

        List<VendorProductSet> sink = sets.stream().filter(s -> "세면기".equals(s.categoryLarge())).toList();
        assertFalse(sink.isEmpty());
        assertTrue(sink.stream().noneMatch(VendorProductSet::selectable), "세면기도 기본구성으로 산정(선택형 아님)");
        assertTrue(sink.stream().allMatch(s -> s.setPrice() != null), "세트가 산정됨(null 아님)");

        // 세트가 = 기본구성(대체옵션 제외) 부속 단가 합과 일치해야 함
        for (VendorProductSet s : sink) {
            BigDecimal included = s.parts().stream()
                    .filter(p -> !"대체옵션".equals(p.remark()))
                    .map(VendorParsedItem::unitPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, included.compareTo(s.setPrice()),
                    "세트가=기본구성 합 (" + s.main().productCode() + ")");
        }
    }

    /** 세트/단일품이 혼재해 단일품 행에 이미지가 희소한 시트(이미지 커버리지 측정 제외). */
    private static final Set<String> IMAGE_SPARSE_SHEETS = Set.of("악세사리 단가표", "수전 부속(세트)");

    @Test
    void 임베디드_이미지_정확매칭_커버리지가_85퍼센트_이상() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        List<VendorProductSet> sets = parser.parseSets(SAMPLE);
        Map<String, Map<Integer, ExcelImageExtractor.ExtractedImage>> images = extractor.extract(SAMPLE);

        // 악세사리/수전부속은 단일품 구간이 많아 행별 이미지가 희소 → 측정에서 제외(무회귀 검증 목적)
        int total = 0, matched = 0;
        for (VendorProductSet s : sets) {
            if (s.imageKey() == null || IMAGE_SPARSE_SHEETS.contains(s.categoryLarge())) continue;
            total++;
            // 대분류를 시트명에서 분리한 시트(비데,기타 등)는 imageKey에 시트명을 실어두므로 그걸로 조회
            String sheetKey = s.categoryLarge();
            String rowPart = s.imageKey();
            int sep = s.imageKey().indexOf(VendorProductSet.IMAGE_KEY_SHEET_SEP);
            if (sep >= 0) {
                sheetKey = s.imageKey().substring(0, sep);
                rowPart = s.imageKey().substring(sep + VendorProductSet.IMAGE_KEY_SHEET_SEP.length());
            }
            Map<Integer, ExcelImageExtractor.ExtractedImage> byRow = images.get(sheetKey);
            if (byRow != null && byRow.get(Integer.parseInt(rowPart)) != null) matched++;
        }
        assertTrue(total > 0);
        double coverage = (double) matched / total;
        assertTrue(coverage >= 0.85, "이미지 정확매칭 커버리지=" + coverage + " (" + matched + "/" + total + ")");
    }
}
