package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AstdProductSyncHandlerTest {

    @Mock private ImageDownloadService imageDownloadService;
    @Mock private VendorProductRepository vendorProductRepository;

    /** 새 축은 가격행을 보지 않는다. 그 사실 자체를 테스트가 지킨다. */
    @Mock private VendorItemPriceRepository vendorItemPriceRepository;

    @InjectMocks private AstdProductSyncHandler handler;

    private VendorProduct set(Long id, String productCode, String masterCode, String imageUrl) {
        return VendorProduct.builder()
                .id(id)
                .productCode(productCode)
                .masterCode(masterCode)
                .itemType("SET")
                .productName("단가표가 채운 이름")
                .collectionName("단가표가 채운 컬렉션")
                .imageUrl(imageUrl)
                .build();
    }

    private CrawledProduct crawled(String productCode) {
        return CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode(productCode)
                .productName("사이트 이름").collectionName("사이트 컬렉션")
                .productUrl("http://example.com/view/" + productCode)
                .imageUrl("http://example.com/img/" + productCode)
                .build();
    }

    private void indexHas(VendorProduct... products) {
        when(vendorProductRepository.findAllByVendor_VendorCodeAndItemType("A", "SET"))
                .thenReturn(List.of(products));
    }

    private void downloadReturns(String relativePath) throws Exception {
        when(imageDownloadService.download(anyString(), anyString()))
                .thenReturn(new ImageDownloadService.DownloadResult("/abs" + relativePath, relativePath));
    }

    private SyncMatchCounters counters(Object ctx) {
        return (SyncMatchCounters) ctx;
    }

    @Test
    @DisplayName("하이픈이 있는 사이트 품번도 대표품번으로 매칭된다 — 예전엔 DB만 잘라 만날 수 없었다")
    void matchesByMasterCodeWhenSiteCodeHasHyphen() {
        // 대표품번은 같고 하이픈 뒤만 다른 변형이다. 예전에는 DB만 잘라
        // "B002000" 대 "B002000-5RAKI1570"을 비교하느라 절대 만나지 못했다.
        indexHas(set(1L, "B002000-5RAKI1712", "B002000", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("B002000-5RAKI1570"), ctx);

        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(1);
    }

    @Test
    @DisplayName("원형 품번이 그대로 같아도 매칭된다")
    void matchesByFullCode() {
        indexHas(set(1L, "FH1013-0GAK400AZ", "FH1013", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("FH1013-0GAK400AZ"), ctx);

        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(1);
    }

    @Test
    @DisplayName("대표품번이 같은 제품이 여럿이면 함께 붙는다 — 1:N은 의도된 동작이다")
    void oneSiteProductFillsEveryVariantRow() {
        indexHas(set(1L, "AC8100-A", "AC8100", null),
                 set(2L, "AC8100-B", "AC8100", null),
                 set(3L, "IL672-A", "IL672", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AC8100-C"), ctx);

        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(2);
    }

    @Test
    @DisplayName("제품 행을 만들지 않는다 — 사이트에만 있는 제품은 건너뛴다")
    void neverCreatesProductRows() {
        indexHas(set(1L, "AC8100-A", "AC8100", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("ZZ9999-X"), ctx);

        assertThat(counters(ctx).notInDb()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isZero();
        verify(vendorProductRepository, never()).save(any());
    }

    @Test
    @DisplayName("단가표가 채운 제품명·컬렉션명을 덮지 않는다 — 사진과 출처만 쓴다")
    void keepsCatalogNamesAndWritesOnlyImageAndSource() throws Exception {
        VendorProduct target = set(1L, "AC8100-A", "AC8100", null);
        indexHas(target);
        when(vendorProductRepository.findAllById(any())).thenReturn(List.of(target));
        downloadReturns("/uploads/product-images/A_AC8100-C.png");

        Object ctx = handler.prepare("A");
        handler.save(crawled("AC8100-C"), ctx);

        assertThat(target.getProductName()).isEqualTo("단가표가 채운 이름");
        assertThat(target.getCollectionName()).isEqualTo("단가표가 채운 컬렉션");
        assertThat(target.getImageUrl()).isEqualTo("/uploads/product-images/A_AC8100-C.png");
        assertThat(target.getDetailUrl()).isEqualTo("http://example.com/view/AC8100-C");
    }

    @Test
    @DisplayName("가격행을 한 줄도 건드리지 않는다")
    void neverTouchesPriceRows() throws Exception {
        VendorProduct target = set(1L, "AC8100-A", "AC8100", null);
        indexHas(target);
        when(vendorProductRepository.findAllById(any())).thenReturn(List.of(target));
        downloadReturns("/uploads/product-images/A_AC8100-C.png");

        Object ctx = handler.prepare("A");
        handler.save(crawled("AC8100-C"), ctx);

        verifyNoInteractions(vendorItemPriceRepository);
    }

    @Test
    @DisplayName("파일명에 확장자를 붙이지 않아 Content-Type 판정을 타게 한다")
    void passesFileNameWithoutExtension() throws Exception {
        VendorProduct target = set(1L, "AC8100-A", "AC8100", null);
        indexHas(target);
        when(vendorProductRepository.findAllById(any())).thenReturn(List.of(target));
        downloadReturns("/uploads/product-images/A_AC8100-C.png");

        Object ctx = handler.prepare("A");
        handler.save(crawled("AC8100-C"), ctx);

        verify(imageDownloadService).download(anyString(), eq("A_AC8100-C"));
    }

    @Test
    @DisplayName("이미 사진이 있던 행은 교체로, 없던 행은 충전으로 센다")
    void countsFilledAndReplacedSeparately() {
        indexHas(set(1L, "AC8100-A", "AC8100", "/uploads/product-images/A_old.jpg"),
                 set(2L, "IL672-A", "IL672", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AC8100-C"), ctx);
        handler.inspect(crawled("IL672-B"), ctx);

        assertThat(counters(ctx).rowsAffected()).isEqualTo(2);
        assertThat(counters(ctx).rowsReplaced()).isEqualTo(1);
        assertThat(counters(ctx).rowsFilled()).isEqualTo(1);
    }

    @Test
    @DisplayName("사이트 제품 둘이 같은 행에 걸리면 반영은 한 행이다 — 뒤에 온 사진이 이긴다")
    void countsContestedRowOnlyOnce() {
        indexHas(set(1L, "AC8100-A", "AC8100", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AC8100-B"), ctx);
        handler.inspect(crawled("AC8100-C"), ctx);

        assertThat(counters(ctx).exactMatched()).isEqualTo(2);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(1);
    }

    @Test
    @DisplayName("품번 없음·이미지 없음·DB 부재를 갈라 센다")
    void countsSkipReasonsSeparately() {
        indexHas(set(1L, "AC8100-A", "AC8100", null));

        Object ctx = handler.prepare("A");

        handler.inspect(CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode(null)
                .imageUrl("http://example.com/x").build(), ctx);
        handler.inspect(CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode("AC8100-C")
                .imageUrl(null).build(), ctx);
        handler.inspect(crawled("ZZ9999-X"), ctx);

        assertThat(counters(ctx).collected()).isEqualTo(3);
        assertThat(counters(ctx).skippedNoCode()).isEqualTo(1);
        assertThat(counters(ctx).skippedNoImage()).isEqualTo(1);
        assertThat(counters(ctx).notInDb()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isZero();
    }

    @Test
    @DisplayName("dry-run은 내려받지도 저장하지도 않는다")
    void dryRunTouchesNothing() {
        indexHas(set(1L, "AC8100-A", "AC8100", null));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AC8100-C"), ctx);

        verifyNoInteractions(imageDownloadService);
        verify(vendorProductRepository, never()).findAllById(any());
        verify(vendorProductRepository, never()).save(any());
    }

    @Test
    @DisplayName("dry-run과 실반영이 같은 숫자를 낸다 — 판정 경로가 하나이기 때문이다")
    void dryRunAndSaveAgreeOnCounts() throws Exception {
        VendorProduct target = set(1L, "AC8100-A", "AC8100", null);
        indexHas(target);

        Object inspectCtx = handler.prepare("A");
        handler.inspect(crawled("AC8100-C"), inspectCtx);

        when(vendorProductRepository.findAllById(any())).thenReturn(List.of(target));
        downloadReturns("/uploads/product-images/A_AC8100-C.png");

        Object saveCtx = handler.prepare("A");
        handler.save(crawled("AC8100-C"), saveCtx);

        assertThat(counters(saveCtx).exactMatched()).isEqualTo(counters(inspectCtx).exactMatched());
        assertThat(counters(saveCtx).rowsAffected()).isEqualTo(counters(inspectCtx).rowsAffected());
        assertThat(counters(saveCtx).rowsFilled()).isEqualTo(counters(inspectCtx).rowsFilled());
    }

    @Test
    @DisplayName("SET 로드는 prepare에서 한 번뿐 — 제품마다 풀스캔하지 않는다")
    void loadsIndexOnlyOnce() throws Exception {
        VendorProduct target = set(1L, "AC8100-A", "AC8100", null);
        indexHas(target);
        when(vendorProductRepository.findAllById(any())).thenReturn(List.of(target));
        downloadReturns("/uploads/product-images/A_x.png");

        Object ctx = handler.prepare("A");
        handler.save(crawled("AC8100-B"), ctx);
        handler.save(crawled("AC8100-C"), ctx);

        verify(vendorProductRepository, times(1)).findAllByVendor_VendorCodeAndItemType("A", "SET");
    }

    @Test
    @DisplayName("컨텍스트 없이 저장할 수 없다 — 레거시 풀스캔 경로는 사라졌다")
    void refusesToSaveWithoutContext() {
        assertThatThrownBy(() -> handler.save(crawled("AC8100-C")))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> handler.save(crawled("AC8100-C"), "인덱스가 아닌 것"))
                .isInstanceOf(IllegalStateException.class);
    }
}
