package com.example.esti.service;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.ProposalRepository;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재적재가 <b>최신본에서 사라진 행</b>을 걷어내는지 검증한다
 * (계획서 `docs/plan-import-stale-sweep.md`).
 *
 * <p>임포트는 upsert라 갱신만 하고 삭제를 하지 않았다. 파일에서 빠진 행은 DB에 그대로 남아
 * 카탈로그에 현재 데이터인 얼굴로 섰다. 2026-09-07에 실물이 나와 SQL로 직접 지워야 했다.
 *
 * <p>합성 fixture를 쓴다 — 실샘플은 gitignore라 CI에서 스킵되는데, 이건 <b>항상</b> 돌아야 한다.
 * B열 대분류 라벨을 넣지 않아 파서의 C열 추론 폴백을 타게 했다("투피스양변기"→양변기,
 * "반다리세면기"→세면기). 덕분에 구간 길이를 자유롭게 바꿀 수 있다 — 세트를 빼도 레이아웃이 안 흔들린다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:staleSweepTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class CatalogImportStaleSweepTest {

    @Autowired private VendorCatalogImporter importer;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private ProposalLineRepository proposalLineRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    /** 세트 하나 = 대표품목 + 부속 1건. 코드로 구분한다. */
    private record SetSpec(String code, String name, int mainPrice, int partPrice) {}

    private static final List<SetSpec> TOILETS = List.of(
            new SetSpec("T1", "원피스양변기", 100, 20),
            new SetSpec("T2", "투피스양변기", 200, 30),
            new SetSpec("T3", "일체형양변기", 300, 40));
    private static final List<SetSpec> BASINS = List.of(
            new SetSpec("W1", "반다리세면기", 400, 50),
            new SetSpec("W2", "긴다리세면기", 500, 60),
            new SetSpec("W3", "탑볼세면기", 600, 70));

    @BeforeEach
    void reset() {
        proposalLineRepository.deleteAll();
        proposalRepository.deleteAll();
        priceRepository.deleteAll();
        relationRepository.deleteAll();   // VENDOR_PRODUCT를 참조하는 외래키가 있어 먼저 지운다
        productRepository.deleteAll();
        vendorRepository.deleteAll();
    }

    // ====== 검증 ======

    @Test
    void 최신본에서_사라진_세트는_재적재_때_정리된다() {
        importAll(TOILETS, BASINS);
        assertThat(setPriceCodes("양변기")).containsExactlyInAnyOrder("T1", "T2", "T3");

        // T3가 빠진 최신본으로 재적재
        var result = importAll(TOILETS.subList(0, 2), BASINS);

        assertThat(result.removed()).as("사라진 대표품목 가격행 1건").isEqualTo(1);
        assertThat(setPriceCodes("양변기")).containsExactlyInAnyOrder("T1", "T2");
        assertThat(productRepository.findAllByProductCode("T3"))
                .as("가격행이 없어진 제품도 정리된다").isEmpty();
    }

    @Test
    void 부분_업로드는_올리지_않은_basis를_건드리지_않는다() {
        importAll(TOILETS, BASINS);

        // 세면기만 담은 파일 — 양변기 basis는 이번 업로드의 범위가 아니다
        var result = importAll(List.of(), BASINS);

        assertThat(result.removed()).isZero();
        assertThat(setPriceCodes("양변기"))
                .as("올리지 않은 basis의 세트가 통째로 날아가면 안 됨")
                .containsExactlyInAnyOrder("T1", "T2", "T3");
    }

    @Test
    void 절반_넘게_사라지면_지우지_않고_넘어간다() {
        importAll(TOILETS, BASINS);

        // 양변기가 3 → 1. 2/3가 사라진 것으로 보이는데, 정상 재적재에서 그럴 리 없다.
        // 파일을 잘못 골랐거나 basis가 여러 파일에 걸쳐 있다는 뜻이라 손대지 않는다.
        var result = importAll(TOILETS.subList(0, 1), BASINS);

        assertThat(result.removed()).isZero();
        assertThat(setPriceCodes("양변기"))
                .as("차단기가 걸리면 원래대로 남는다")
                .containsExactlyInAnyOrder("T1", "T2", "T3");
    }

    @Test
    void 제안서가_참조하는_제품은_가격행만_지우고_남긴다() {
        importAll(TOILETS, BASINS);
        VendorProduct t3 = productRepository.findAllByProductCode("T3").get(0);
        referenceFromProposal(t3);

        var result = importAll(TOILETS.subList(0, 2), BASINS);

        assertThat(result.removed()).as("가격행은 사라진 대로 정리된다").isEqualTo(1);
        assertThat(productRepository.findAllByProductCode("T3"))
                .as("제안서가 참조 중이면 제품은 남긴다 — productId에 외래키가 없어 끊어진 id가 조용히 생긴다")
                .isNotEmpty();
    }

    @Test
    void 파싱_결과가_비면_아무것도_지우지_않는다() {
        importAll(TOILETS, BASINS);

        var result = importAll(List.of(), List.of());

        assertThat(result.removed()).as("빈 파일로 카탈로그를 비우는 사고를 막는다").isZero();
        assertThat(priceRepository.count()).isPositive();
    }

    @Test
    void 부속_가격행은_정리_대상이_아니다() {
        importAll(TOILETS, BASINS);
        long partsBefore = partPriceCount();

        importAll(TOILETS.subList(0, 2), BASINS);

        // 부속은 공유 자원이라 코드당 1건을 유지한다(D13). 세트가 빠져도 부속 단가는 그대로 둔다.
        assertThat(partPriceCount()).isEqualTo(partsBefore);
    }

    // ====== 도우미 ======

    private VendorCatalogImporter.ImportResult importAll(List<SetSpec> toilets, List<SetSpec> basins) {
        return importer.importVendorCatalog("A", buildFixture(toilets, basins), null);
    }

    /** 그 basis의 대표품목 가격행에 남아 있는 품번들. */
    private List<String> setPriceCodes(String basis) {
        Vendor a = vendorRepository.findByVendorCode("A").orElseThrow();
        return priceRepository.findAllByVendorAndPriceTypeAndPriceBasis(a, "SET", basis).stream()
                .map(VendorItemPrice::getMainItemCode)
                .toList();
    }

    private long partPriceCount() {
        Vendor a = vendorRepository.findByVendorCode("A").orElseThrow();
        return priceRepository.findByVendor_VendorCode(a.getVendorCode()).stream()
                .filter(p -> "PART".equals(p.getPriceType()))
                .count();
    }

    private void referenceFromProposal(VendorProduct product) {
        Proposal p = new Proposal();
        p.setProjectName("정리 보호 검증");
        p.setStatus(Proposal.Status.DRAFT);
        proposalRepository.save(p);

        ProposalLine line = new ProposalLine();
        line.setProposal(p);
        line.setProductId(product.getId());
        line.setProductName(product.getProductName());
        line.setManualMargin(false);
        proposalLineRepository.save(line);
    }

    // ====== fixture 작성 ======

    /**
     * A사 시트를 만든다 — <b>B열 대분류 라벨을 넣지 않는다.</b>
     * 라벨이 하나도 없으면 파서가 C열 텍스트 추론 폴백으로 대분류를 정한다.
     *
     * <pre>
     *  r1  헤더
     *  r2  C=투피스양변기 (라벨 전용) → 대분류 '양변기'
     *  r3  세트 대표 (C=세트명) / r4 부속 / r5 합계행     ← 세트 하나가 3행
     *  ...
     *  rN  C=반다리세면기 (라벨 전용) → 대분류 '세면기'
     * </pre>
     */
    private Path buildFixture(List<SetSpec> toilets, List<SetSpec> basins) {
        try {
            Path out = Files.createTempFile("stale-sweep-", ".xlsx");
            try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(out)) {
                Sheet s = wb.createSheet("ASK");
                int r = 0;
                header(s, r++);
                r = section(s, r, "투피스양변기", toilets);
                section(s, r, "반다리세면기", basins);
                wb.write(os);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("fixture 생성 실패", e);
        }
    }

    /** C 라벨 전용행 + 세트들. 비어 있으면 구간 자체를 만들지 않는다(= 그 basis를 안 올린 파일). */
    private int section(Sheet s, int startRow, String smallLabel, List<SetSpec> specs) {
        if (specs.isEmpty()) return startRow;
        int r = startRow;
        text(s, r++, 2, smallLabel);
        for (SetSpec spec : specs) {
            text(s, r, 2, spec.name());                     // C=세트명 → 세트 시작 행
            item(s, r++, spec.name(), spec.code(), spec.mainPrice());
            item(s, r++, spec.name() + " 부속", spec.code() + "P", spec.partPrice());
            total(s, r++, spec.mainPrice() + spec.partPrice());
        }
        return r;
    }

    private static Row row(Sheet s, int r) {
        Row row = s.getRow(r);
        return row != null ? row : s.createRow(r);
    }

    private static void text(Sheet s, int r, int c, String v) {
        Cell cell = row(s, r).createCell(c);
        cell.setCellValue(v);
    }

    private static void header(Sheet s, int r) {
        text(s, r, 3, "제품명");
        text(s, r, 4, "구품번");
        text(s, r, 5, "신품번");
    }

    private static void item(Sheet s, int r, String name, String code, double price) {
        text(s, r, 3, name);
        text(s, r, 5, code);
        row(s, r).createCell(6).setCellValue(price);
    }

    /** 합계행 — C/D/E/F 비고 G만. */
    private static void total(Sheet s, int r, double sum) {
        row(s, r).createCell(6).setCellValue(sum);
    }
}
