package com.example.esti.service;

import com.example.esti.excel.ExcelImageExtractor;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorParsedItem;
import com.example.esti.excel.VendorProductSet;
import com.example.esti.progress.ImportProgress;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * B-1 검증: 카탈로그 임포트가 중간에 실패하면 <b>그 실행분이 전부 롤백</b>되고,
 * 이전에 적재된 데이터는 그대로 남는지 확인한다.
 *
 * <p>실패 상황은 파서를 스텁으로 갈아끼워 만든다 — 엑셀 파서 소스는 건드리지 않고,
 * 샘플 엑셀도 손상시키지 않는다({@code parseSets}가 돌려주는 리스트가 순회 도중 예외를 던진다).
 * 정상 세트 2건이 먼저 저장된 뒤 세 번째에서 터지므로 "부분 적재"가 실제로 발생하는 조건이다.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:rollbacktest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class CatalogImportRollbackTest {

    /** 파서/이미지추출기는 스텁으로 대체하므로 실제 파일 내용은 쓰이지 않는다. */
    private static final Path DUMMY = Path.of("target", "rollback-test-dummy.xlsx");

    @MockitoBean private VendorExcelParserFactory parserFactory;
    @MockitoBean private ExcelImageExtractor imageExtractor;

    @Autowired private CatalogImportAsyncService asyncService;
    @Autowired private ImportProgressStore progressStore;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private VendorProductRepository productRepository;
    @Autowired private VendorItemPriceRepository priceRepository;
    @Autowired private VendorProductRelationRepository relationRepository;

    private long products;
    private long prices;
    private long relations;

    /** 선행 적재(성공 케이스) — 롤백이 "이번 실행분만" 되돌리는지 보려면 기존 데이터가 있어야 한다. */
    @BeforeEach
    void seedSuccessfulImport() {
        given(imageExtractor.extract(any())).willReturn(Map.of());
        given(parserFactory.getParser("B")).willReturn(parserReturning(goodSets("SEED", 2)));

        if (productRepository.count() == 0) {
            asyncService.importVendorCatalog("B", DUMMY);
        }
        products = productRepository.count();
        prices = priceRepository.count();
        relations = relationRepository.count();
        assertThat(products).as("선행 적재 데이터").isGreaterThan(0);
    }

    @Test
    void 동기_임포트가_중간에_실패하면_그_실행분이_전부_롤백된다() {
        given(parserFactory.getParser("B")).willReturn(parserReturning(poisonedSets("SYNC", 2)));

        assertThatThrownBy(() -> asyncService.importVendorCatalog("B", DUMMY))
                .isInstanceOf(IllegalStateException.class);

        assertUnchangedAndNotPartiallyStored("SYNC");
    }

    @Test
    void 비동기_임포트가_실패하면_진행률은_실패이고_DB는_롤백된다() throws Exception {
        given(parserFactory.getParser("B")).willReturn(parserReturning(poisonedSets("ASYNC", 2)));

        Path upload = Files.createFile(Files.createTempDirectory("rollback").resolve("upload.xlsx"));
        String jobId = progressStore.createJob();

        asyncService.importVendorCatalogAsync(jobId, "B", upload);

        ImportProgress progress = awaitDone(jobId);
        assertThat(progress.isError()).as("사용자에게 실패로 보고").isTrue();
        assertThat(progress.getMessage()).contains("실패");
        assertThat(Files.exists(upload)).as("임시 업로드 파일 정리").isFalse();

        assertUnchangedAndNotPartiallyStored("ASYNC");
    }

    // ===== 헬퍼 =====

    /** 실패한 실행분이 하나도 남지 않았고, 기존 데이터는 그대로인지. */
    private void assertUnchangedAndNotPartiallyStored(String prefix) {
        assertThat(productRepository.count()).as("실패 후 제품 수 불변(부분 적재 없음)").isEqualTo(products);
        assertThat(priceRepository.count()).as("실패 후 가격 수 불변").isEqualTo(prices);
        assertThat(relationRepository.count()).as("실패 후 관계 수 불변").isEqualTo(relations);

        var vendor = vendorRepository.findByVendorCode("B").orElseThrow();
        assertThat(productRepository.findByVendorAndProductCode(vendor, prefix + "-0"))
                .as("예외 이전에 저장되던 세트도 남지 않아야 한다").isEmpty();
    }

    private ImportProgress awaitDone(String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ImportProgress p = progressStore.get(jobId);
            if (p.isDone()) return p;
            Thread.sleep(50);
        }
        throw new AssertionError("비동기 임포트가 10초 안에 끝나지 않았다: " + jobId);
    }

    private VendorExcelParser parserReturning(List<VendorProductSet> sets) {
        return new VendorExcelParser() {
            @Override public String getVendorCode() { return "B"; }
            @Override public List<VendorProductSet> parseSets(Path path) { return sets; }
        };
    }

    private List<VendorProductSet> goodSets(String prefix, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> set(prefix, i)).toList();
    }

    /**
     * 앞 {@code goodCount}건은 정상, 그 다음 순회에서 예외.
     * for-each가 {@link AbstractList} 반복자의 {@code get(i)}를 타므로 "N건 저장 후 중단"이 재현된다.
     */
    private List<VendorProductSet> poisonedSets(String prefix, int goodCount) {
        List<VendorProductSet> good = goodSets(prefix, goodCount);
        return new AbstractList<>() {
            @Override public VendorProductSet get(int index) {
                if (index < goodCount) return good.get(index);
                throw new IllegalStateException("적재 중단(테스트 유도 예외)");
            }
            @Override public int size() { return goodCount + 1; }
        };
    }

    private VendorProductSet set(String prefix, int i) {
        VendorParsedItem main = new VendorParsedItem(
                prefix + "-" + i, "롤백테스트 세트 " + i, null, null,
                VendorParsedItem.RELATION_MAIN, new BigDecimal("10000"), null);
        VendorParsedItem part = new VendorParsedItem(
                prefix + "-" + i + "_P1", "롤백테스트 부속", null, null,
                "도기", new BigDecimal("3000"), null);
        return new VendorProductSet("B", "양변기", "테스트", main, List.of(part),
                new BigDecimal("13000"), false, null, false);
    }
}
