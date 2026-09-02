package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AstdProductSyncHandlerTest {

    @Mock private ImageDownloadService imageDownloadService;
    @Mock private VendorItemPriceRepository vendorItemPriceRepository;
    @Mock private VendorProductRepository vendorProductRepository;
    @InjectMocks private AstdProductSyncHandler handler;

    private VendorItemPrice vip(Long id, String mainItemCode) {
        VendorItemPrice v = new VendorItemPrice();
        v.setId(id);
        v.setMainItemCode(mainItemCode);
        v.setVendor(new Vendor(1L, "A", "아메리칸스탠다드"));
        return v;
    }

    private VendorProduct product(String productCode, String imageUrl) {
        return VendorProduct.builder()
                .productCode(productCode)
                .imageUrl(imageUrl)
                .build();
    }

    private CrawledProduct crawled(String productCode) {
        return CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode(productCode)
                .imageUrl("http://example.com/" + productCode + ".jpg")
                .build();
    }

    private SyncMatchCounters counters(Object ctx) {
        return (SyncMatchCounters) ctx;
    }

    @Test
    void 전체_가격행_로드는_prepare에서_한_번만_수행된다() throws Exception {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "ABC123-XYZ"), vip(2L, "DEF999")));
        when(vendorItemPriceRepository.findAllById(any())).thenReturn(List.of(vip(1L, "ABC123-XYZ")));
        when(imageDownloadService.download(anyString(), anyString()))
                .thenReturn(new ImageDownloadService.DownloadResult("/abs/a.jpg", "/uploads/product-images/a.jpg"));
        when(vendorProductRepository.findByVendorAndProductCode(any(), anyString()))
                .thenReturn(Optional.empty());

        Object ctx = handler.prepare("A");

        CrawledProduct p1 = CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode("ABC123")
                .imageUrl("http://example.com/1.jpg").build();
        CrawledProduct p2 = CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode("ABC123")
                .imageUrl("http://example.com/2.jpg").build();

        handler.save(p1, ctx);
        handler.save(p2, ctx);

        // 성능 계약: 전체 로드는 prepare의 1회뿐, save에서는 호출되지 않는다
        verify(vendorItemPriceRepository, times(1)).findAllByVendor_VendorCode("A");
        // 매칭 계약: 하이픈 앞 대표품번(ABC123)으로 매칭되어 id 조회가 일어난다
        verify(vendorItemPriceRepository, times(2)).findAllById(List.of(1L));
    }

    @Test
    void 컨텍스트가_집계를_내놓아_리포트에_매칭_상세가_실린다() {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "ABC123-XYZ"), vip(2L, "DEF999")));
        when(vendorProductRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(product("ABC123-XYZ", null)));

        Object ctx = handler.prepare("A");

        // 예전에는 맨 HashMap이라 ProductImageSyncService가 매칭 상세를 실을 수 없었다
        assertThat(ctx).isInstanceOf(SyncMatchCounters.class);
        assertThat(counters(ctx).indexedCodes()).isEqualTo(2);
    }

    @Test
    void dry_run은_내려받지도_저장하지도_않고_같은_숫자를_낸다() throws Exception {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "ABC123-XYZ")));
        when(vendorProductRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(product("ABC123-XYZ", null)));

        Object inspectCtx = handler.prepare("A");
        handler.inspect(crawled("ABC123"), inspectCtx);

        // dry-run은 네트워크도 DB도 건드리지 않는다
        verifyNoInteractions(imageDownloadService);
        verify(vendorItemPriceRepository, never()).findAllById(any());
        verify(vendorProductRepository, never()).save(any());

        // 그리고 실반영과 같은 규칙으로 같은 숫자를 내야 한다 — 판정 경로가 하나이기 때문이다
        when(vendorItemPriceRepository.findAllById(any())).thenReturn(List.of(vip(1L, "ABC123-XYZ")));
        when(imageDownloadService.download(anyString(), anyString()))
                .thenReturn(new ImageDownloadService.DownloadResult("/abs/a.jpg", "/uploads/product-images/a.jpg"));
        when(vendorProductRepository.findByVendorAndProductCode(any(), anyString()))
                .thenReturn(Optional.of(product("ABC123-XYZ", null)));

        Object saveCtx = handler.prepare("A");
        handler.save(crawled("ABC123"), saveCtx);

        assertThat(counters(saveCtx).exactMatched()).isEqualTo(counters(inspectCtx).exactMatched());
        assertThat(counters(saveCtx).rowsAffected()).isEqualTo(counters(inspectCtx).rowsAffected());
        assertThat(counters(saveCtx).rowsFilled()).isEqualTo(counters(inspectCtx).rowsFilled());
    }

    @Test
    void 이미_이미지가_있던_행은_교체로_없던_행은_충전으로_센다() {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "AAA111-X"), vip(2L, "BBB222-Y")));
        when(vendorProductRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(
                        product("AAA111-X", "/uploads/product-images/A_AAA111-X.jpg"),
                        product("BBB222-Y", null)));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AAA111"), ctx);
        handler.inspect(crawled("BBB222"), ctx);

        assertThat(counters(ctx).rowsAffected()).isEqualTo(2);
        assertThat(counters(ctx).rowsReplaced()).isEqualTo(1);
        assertThat(counters(ctx).rowsFilled()).isEqualTo(1);
    }

    @Test
    void 여러_가격행이_같은_품번을_가리키면_제품_행은_한_번만_센다() {
        // 가격행 수로 세면 반영 규모가 부풀려진다. 사진이 붙는 곳은 제품 행 하나다.
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "AAA111-X"), vip(2L, "AAA111-X"), vip(3L, "AAA111-X")));
        when(vendorProductRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(product("AAA111-X", null)));

        Object ctx = handler.prepare("A");
        handler.inspect(crawled("AAA111"), ctx);

        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(1);
    }

    @Test
    void 품번이_없거나_이미지가_없으면_건너뛴_것으로_센다() {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "AAA111-X")));
        when(vendorProductRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(product("AAA111-X", null)));

        Object ctx = handler.prepare("A");

        handler.inspect(CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode(null)
                .imageUrl("http://example.com/x.jpg").build(), ctx);
        handler.inspect(CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode("AAA111")
                .imageUrl(null).build(), ctx);
        handler.inspect(crawled("ZZZ999"), ctx);

        assertThat(counters(ctx).collected()).isEqualTo(3);
        assertThat(counters(ctx).skippedNoCode()).isEqualTo(1);
        assertThat(counters(ctx).skippedNoImage()).isEqualTo(1);
        assertThat(counters(ctx).notInDb()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isZero();
    }

    @Test
    void 매칭되는_품번이_없으면_아무것도_저장하지_않는다() throws Exception {
        when(vendorItemPriceRepository.findAllByVendor_VendorCode("A"))
                .thenReturn(List.of(vip(1L, "DEF999")));

        Object ctx = handler.prepare("A");
        CrawledProduct p = CrawledProduct.builder()
                .maker("ASTD").vendorCode("A").productCode("ZZZ000")
                .imageUrl("http://example.com/x.jpg").build();
        handler.save(p, ctx);

        verify(vendorItemPriceRepository, never()).findAllById(any());
        verifyNoInteractions(imageDownloadService);
    }
}
