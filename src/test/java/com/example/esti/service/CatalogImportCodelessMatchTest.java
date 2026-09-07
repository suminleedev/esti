package com.example.esti.service;

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
 * 품번 없는 제품의 <b>재적재 멱등 매칭</b>.
 *
 * <p>품번이 있는 제품은 품번으로 식별하지만, 품번 없는 항목(A사 «신품번 없음» 등)은 이름으로 찾는다.
 * 예전에는 {@code 이름 + 대분류 + 소분류}로만 찾아서 <b>분류를 재편하는 재적재에서 매칭이 통째로
 * 빗나가 중복이 생겼다</b> — 2026-09-02에 실제로 6건. 소분류가 없는 구간에서는 조회 자체를 건너뛰어
 * 매번 새로 만들기까지 했다.
 *
 * <p>반대로 이름만으로 묶으면 서로 다른 제품이 한 행으로 병합된다. 이 테스트는 <b>양쪽 다</b> 지킨다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:codelessMatchTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class CatalogImportCodelessMatchTest {

    @Autowired private VendorCatalogImporter importer;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private ProposalLineRepository proposalLineRepository;

    /** 세트 하나. {@code code}가 null이면 품번 없는 항목이 된다(파서가 이름 뒤에 표기를 붙인다). */
    private record SetSpec(String code, String name, int mainPrice) {}

    @BeforeEach
    void reset() {
        proposalLineRepository.deleteAll();
        proposalRepository.deleteAll();
        priceRepository.deleteAll();
        relationRepository.deleteAll();
        productRepository.deleteAll();
        vendorRepository.deleteAll();
    }

    // ====== 되찾아야 하는 경우 ======

    @Test
    void 분류가_바뀌어도_같은_제품으로_알아본다() {
        // 1차: 소분류 '세면수전' 구간
        importSection("세면수전", List.of(new SetSpec(null, "양변기탱크", 100)));
        assertThat(codeless()).hasSize(1);
        Long firstId = codeless().get(0).getId();

        // 2차: 같은 항목이 '투피스양변기' 구간으로 옮겨졌다 (A-6 같은 분류 재편)
        importSection("투피스양변기", List.of(new SetSpec(null, "양변기탱크", 100)));

        assertThat(codeless())
                .as("분류가 바뀌었다고 새 행을 만들면 안 된다")
                .hasSize(1);
        assertThat(codeless().get(0).getId())
                .as("같은 행을 이어 써야 한다").isEqualTo(firstId);
        assertThat(codeless().get(0).getCategorySmall()).isEqualTo("투피스양변기");
    }

    @Test
    void 분류가_비어_있어도_멱등이다() {
        // C 라벨 전용행이 없는 구간 — 분류가 비운 채로 적재된다(A사 액세서리 구간이 실제로 그렇다).
        // 예전에는 대·소분류 중 하나라도 null이면 조회를 건너뛰어 재적재마다 새로 만들었다.
        importNoCategory(List.of(new SetSpec(null, "컵및컵대", 30)));
        assertThat(codeless()).hasSize(1);
        assertThat(codeless().get(0).getCategorySmall())
                .as("이 픽스처가 실제로 분류가 빈 행을 만드는지 확인").isNull();

        importNoCategory(List.of(new SetSpec(null, "컵및컵대", 30)));
        assertThat(codeless()).as("두 번 올려도 한 행").hasSize(1);
    }

    @Test
    void 재적재는_그대로_멱등이다() {
        importSection("투피스양변기", List.of(
                new SetSpec(null, "양변기탱크", 100),
                new SetSpec("T-1", "일체형양변기", 200)));

        importSection("투피스양변기", List.of(
                new SetSpec(null, "양변기탱크", 100),
                new SetSpec("T-1", "일체형양변기", 200)));

        assertThat(codeless()).hasSize(1);
        assertThat(productRepository.findAllByProductCode("T-1")).hasSize(1);
    }

    // ====== 합치면 안 되는 경우 ======

    @Test
    void 이름이_같은_다른_제품_둘은_한_행으로_접히지_않는다() {
        // 한 파일 안에 이름이 같고 분류가 다른 품번 없는 항목이 둘.
        // 이름만으로 묶으면 두 번째가 첫 번째를 물어 하나가 된다.
        importTwoSections(
                "투피스양변기", List.of(new SetSpec(null, "패킹", 10)),
                "세면수전", List.of(new SetSpec(null, "패킹", 20)));

        assertThat(codeless())
                .as("분류가 다른 두 항목은 각각 남아야 한다")
                .hasSize(2);
        assertThat(codeless().stream().map(VendorProduct::getCategorySmall))
                .containsExactlyInAnyOrder("투피스양변기", "세면수전");
    }

    @Test
    void 이름이_같은_둘이_있으면_모호하므로_새로_만든다() {
        // 위 상태에서 어느 쪽도 아닌 제3의 분류로 같은 이름이 들어오면,
        // 어느 행을 이어야 할지 가릴 수 없다. 잘못 합치느니 늘어나는 편이 낫다.
        importTwoSections(
                "투피스양변기", List.of(new SetSpec(null, "패킹", 10)),
                "세면수전", List.of(new SetSpec(null, "패킹", 20)));
        assertThat(codeless()).hasSize(2);

        importSection("반다리세면기", List.of(new SetSpec(null, "패킹", 30)));

        assertThat(codeless())
                .as("모호하면 합치지 않는다 — 종전과 같은 결과")
                .hasSize(3);
    }

    @Test
    void 품번_있는_제품을_물지_않는다() {
        // 이름·분류가 같아도 품번이 있는 제품은 품번으로 식별되는 별개다.
        // 둘을 같은 파일에 담는다 — 한쪽만 담으면 «사라진 행 정리»가 걷어가 매칭 여부를 못 본다.
        importSection("투피스양변기", List.of(
                new SetSpec("T-9", "양변기탱크", 100),
                new SetSpec(null, "양변기탱크", 100)));

        assertThat(productRepository.findAllByProductCode("T-9"))
                .as("품번 있는 제품은 그대로 남는다").hasSize(1);
        assertThat(codeless())
                .as("품번 없는 항목이 품번 있는 행을 덮어쓰지 않고 별도 행으로 들어간다").hasSize(1);

        // 다시 올려도 각각 하나씩 — 서로를 물지 않는다
        importSection("투피스양변기", List.of(
                new SetSpec("T-9", "양변기탱크", 100),
                new SetSpec(null, "양변기탱크", 100)));

        assertThat(productRepository.findAllByProductCode("T-9")).hasSize(1);
        assertThat(codeless()).hasSize(1);
    }

    // ====== 도우미 ======

    /** 품번이 없는 제품들. 파서가 이름 뒤에 «(신품번 없음)»을 붙이므로 그 행들이다. */
    private List<VendorProduct> codeless() {
        return productRepository.findAll().stream()
                .filter(p -> p.getProductCode() == null)
                .toList();
    }

    private void importSection(String smallLabel, List<SetSpec> specs) {
        importer.importVendorCatalog("A", fixture(sheet -> {
            header(sheet, 0);
            section(sheet, 1, smallLabel, specs);
        }), null);
    }

    private void importTwoSections(String label1, List<SetSpec> specs1,
                                   String label2, List<SetSpec> specs2) {
        importer.importVendorCatalog("A", fixture(sheet -> {
            header(sheet, 0);
            int r = section(sheet, 1, label1, specs1);
            section(sheet, r, label2, specs2);
        }), null);
    }

    /** C 라벨 전용행이 아예 없는 구간 — 대·소분류가 빈 채로 적재된다. */
    private void importNoCategory(List<SetSpec> specs) {
        importer.importVendorCatalog("A", fixture(sheet -> {
            header(sheet, 0);
            int r = 1;
            for (SetSpec spec : specs) {
                text(sheet, r, 2, spec.name());   // C=세트명(라벨 전용행이 아니라 분류를 바꾸지 않는다)
                item(sheet, r++, spec.name(), spec.code(), spec.mainPrice());
                total(sheet, r++, spec.mainPrice());
            }
        }), null);
    }

    private int section(Sheet s, int startRow, String smallLabel, List<SetSpec> specs) {
        int r = startRow;
        text(s, r++, 2, smallLabel);
        for (SetSpec spec : specs) {
            text(s, r, 2, spec.name());                 // C=세트명 → 세트 시작 행
            item(s, r++, spec.name(), spec.code(), spec.mainPrice());
            total(s, r++, spec.mainPrice());
        }
        return r;
    }

    private interface SheetWriter { void write(Sheet sheet); }

    private Path fixture(SheetWriter writer) {
        try {
            Path out = Files.createTempFile("codeless-match-", ".xlsx");
            try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(out)) {
                writer.write(wb.createSheet("ASK"));
                wb.write(os);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("fixture 생성 실패", e);
        }
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

    /** {@code code}가 null이면 F(신품번)를 비워 «품번 없는 행»을 만든다. */
    private static void item(Sheet s, int r, String name, String code, double price) {
        text(s, r, 3, name);
        if (code != null) text(s, r, 5, code);
        row(s, r).createCell(6).setCellValue(price);
    }

    private static void total(Sheet s, int r, double sum) {
        row(s, r).createCell(6).setCellValue(sum);
    }
}
