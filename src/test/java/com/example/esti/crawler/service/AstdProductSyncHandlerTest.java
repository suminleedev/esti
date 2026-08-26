package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
