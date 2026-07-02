package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.excel.VendorBExcelParser;
import com.example.esti.excel.VendorProductSet;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * B사 시트별 <b>DB 레벨</b> 검증 기반 클래스.
 *
 * <p>기존엔 시트마다 {@code ij}로 데이터를 지우고 앱을 띄워 업로드한 뒤 DBeaver로 눈으로 확인했다.
 * 이 기반 클래스는 그 왕복을 자동화한다: 커밋된 샘플을 <b>인메모리 Derby(create-drop)</b>에 1회 적재하고,
 * 시트별 하위 클래스가 "유실 0 / 업서트 충돌 0 / 세트가 보존 / 재업로드 멱등"을 코드로 단언한다.
 * 파싱 정확성은 {@code VendorB*SheetTest}(파서 단위)가, 적재/업서트 정확성은 이 계층이 담당한다.</p>
 *
 * <p>새 시트 추가 시: 이 클래스를 상속하는 {@code VendorB<시트>DbTest}를 만들고
 * {@link #assertNoLossNoCollision(String)} + 스폿 단언 + {@link #assertReimportIdempotent()}만 채우면 된다.
 * (R6: 더는 시트별 단일 엑셀/ij 삭제가 필수 아님 — 합본 샘플에서 categoryLarge로 분리 검증)</p>
 *
 * <p>샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:sheetdbtest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // 테스트는 임시 디렉터리에 이미지 저장(실제 ./uploads 오염 방지)
        "app.crawler.image-dir=target/test-product-images"
})
abstract class AbstractVendorBSheetDbVerification {

    /**
     * 검증에 쓸 엑셀. 기본은 커밋된 합본 샘플이지만, {@code -Db.sample=<경로>}로 덮어쓸 수 있다.
     * (예: 데스크톱의 시트별 단일 엑셀로 실제 최신 데이터 적재 검증)
     */
    protected static final Path SAMPLE =
            Path.of(System.getProperty("b.sample", "docs/samples/B사 단가표_sample.xlsx"));
    private static final String SET = "SET";

    @Autowired protected CatalogImportAsyncService service;
    @Autowired protected VendorRepository vendorRepository;
    @Autowired protected VendorProductRepository productRepository;
    @Autowired protected VendorItemPriceRepository priceRepository;
    @Autowired protected VendorProductRelationRepository relationRepository;

    private final VendorBExcelParser parser = new VendorBExcelParser();
    private List<VendorProductSet> parsedCache;

    /**
     * 시트별 테스트 진입 전, 샘플을 인메모리 DB에 1회 적재한다.
     * 스프링 테스트 컨텍스트 캐시로 같은 설정의 모든 하위 클래스가 DB를 공유하므로,
     * 실제 적재는 전체에서 1회만 일어난다(이미 적재됐으면 생략).
     */
    @BeforeEach
    void ensureLoaded() {
        assumeTrue(Files.exists(SAMPLE), "샘플 엑셀이 없어 스킵: " + SAMPLE);
        if (productRepository.count() == 0) {
            service.importVendorCatalog("B", SAMPLE);
        }
    }

    // ===== 파서 기대치(원본) =====

    /** 해당 시트의 파서 결과(적재 전 원본 기대치). */
    protected List<VendorProductSet> parsedSetsOf(String categoryLarge) {
        if (parsedCache == null) parsedCache = parser.parseSets(SAMPLE);
        return parsedCache.stream()
                .filter(s -> categoryLarge.equals(s.categoryLarge()))
                .toList();
    }

    // ===== DB 실적재 조회 =====

    protected Vendor vendorB() {
        return vendorRepository.findByVendorCode("B").orElseThrow();
    }

    /** 해당 시트의 DB 대표품목(SET) 목록. */
    protected List<VendorProduct> dbSetProductsOf(String categoryLarge) {
        Vendor b = vendorB();
        return productRepository.findAll().stream()
                .filter(p -> p.getVendor() != null && b.getId().equals(p.getVendor().getId()))
                .filter(p -> SET.equals(p.getItemType()))
                .filter(p -> categoryLarge.equals(p.getCategoryLarge()))
                .toList();
    }

    protected VendorProduct dbSetProduct(String categoryLarge, String code) {
        return dbSetProductsOf(categoryLarge).stream()
                .filter(p -> code.equals(p.getProductCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(categoryLarge + "/" + code + " 대표품목 DB 미적재"));
    }

    /** 대표품목 세트가 — 시트(categoryLarge=priceBasis) 기준 분리 저장값. */
    protected BigDecimal dbSetPrice(String categoryLarge, VendorProduct main) {
        return priceRepository
                .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(
                        vendorB(), main, main.getProductCode(), categoryLarge)
                .map(VendorItemPrice::getUnitPrice)
                .orElseThrow(() -> new AssertionError(main.getProductCode() + " 세트가 미적재"));
    }

    /**
     * 대표품목 가격 — priceBasis를 명시 조회(수전금구처럼 대분류≠가격기준일 때).
     * ({@link #dbSetPrice(String, VendorProduct)}는 basis==categoryLarge를 가정하므로 그 경우엔 이 오버로드 사용, §10 S9)
     */
    protected BigDecimal dbSetPriceByBasis(VendorProduct main, String priceBasis) {
        return priceRepository
                .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(
                        vendorB(), main, main.getProductCode(), priceBasis)
                .map(VendorItemPrice::getUnitPrice)
                .orElseThrow(() -> new AssertionError(main.getProductCode() + " @basis=" + priceBasis + " 가격 미적재"));
    }

    /**
     * 대표품목 구성 부속(관계 대상 품목).
     * 관계의 targetProduct는 lazy 프록시이므로 id만 뽑아(세션 없이 안전) 재조회해 초기화된 엔티티로 반환한다.
     */
    protected List<VendorProduct> dbPartsOf(VendorProduct main) {
        List<Long> ids = relationRepository.findAllBySourceProduct(main).stream()
                .map(r -> r.getTargetProduct().getId())
                .toList();
        return productRepository.findAllById(ids);
    }

    protected VendorProduct dbPart(VendorProduct main, String partName) {
        return dbPartsOf(main).stream()
                .filter(p -> partName.equals(p.getProductName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(partName + " 부속 미적재: "
                        + dbPartsOf(main).stream().map(VendorProduct::getProductName).toList()));
    }

    /** 부속 단가(코드당 1건, priceBasis=null). */
    protected BigDecimal dbPartPrice(VendorProduct part) {
        return priceRepository.findFirstByVendorAndVendorProduct(vendorB(), part)
                .map(VendorItemPrice::getUnitPrice).orElse(BigDecimal.ZERO);
    }

    /** 부속 비고(대체옵션 등). */
    protected String dbPartRemark(VendorProduct part) {
        return priceRepository.findFirstByVendorAndVendorProduct(vendorB(), part)
                .map(VendorItemPrice::getRemark).orElse(null);
    }

    // ===== 시트 불문 공통 검증 =====

    /**
     * 유실 0 / 업서트 충돌 0: 파서가 만든 대표품번이 DB에 빠짐없이, 합쳐짐 없이 1:1로 적재됐는가.
     * 같은 코드가 둘 이상이면 업서트가 한 행으로 병합 → 유실이므로, 파서 단계 중복도 함께 막는다.
     */
    protected void assertNoLossNoCollision(String categoryLarge) {
        List<String> parsedCodes = parsedSetsOf(categoryLarge).stream()
                .map(s -> s.main().productCode()).toList();

        Map<String, Long> dup = parsedCodes.stream()
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        assertThat(dup.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList())
                .as(categoryLarge + " 파서 대표품번 중복(업서트 시 유실 위험)").isEmpty();

        List<VendorProduct> dbSets = dbSetProductsOf(categoryLarge);
        Set<String> dbCodes = dbSets.stream().map(VendorProduct::getProductCode).collect(Collectors.toSet());

        assertThat(dbSets).as(categoryLarge + " DB 대표품목 수 = 파서 세트 수(유실 0)")
                .hasSize(parsedCodes.size());
        assertThat(dbCodes).as(categoryLarge + " 모든 파서 대표품번이 DB에 존재(충돌 0)")
                .containsAll(parsedCodes);
    }

    /** 재업로드 멱등: 전체 행 수 불변(중복 적재/유실 없음). */
    protected void assertReimportIdempotent() {
        long products = productRepository.count();
        long prices = priceRepository.count();
        long relations = relationRepository.count();

        service.importVendorCatalog("B", SAMPLE);

        assertThat(productRepository.count()).as("재업로드 후 제품 수 불변").isEqualTo(products);
        assertThat(priceRepository.count()).as("재업로드 후 가격 수 불변").isEqualTo(prices);
        assertThat(relationRepository.count()).as("재업로드 후 관계 수 불변").isEqualTo(relations);
    }
}
