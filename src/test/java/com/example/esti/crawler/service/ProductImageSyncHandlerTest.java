package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import com.example.esti.repository.SyncRunRepository;
import com.example.esti.service.SyncRunService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 저장 핸들러 고르기.
 *
 * <p><b>모든 제조사를 받는 기본 핸들러는 없다</b>(2026-11-20 삭제). 예전에는 크롤러만 붙이고 전용 핸들러를
 * 빠뜨려도 기본 핸들러가 받아서 제품을 만들고 제품명을 사이트 이름으로 덮었다 — «카탈로그의 정본은 단가표»
 * 원칙이 조용히 깨지는 길이었다. 이제는 전용 핸들러가 없으면 사이트에 요청을 보내기 전에 거절한다.
 */
class ProductImageSyncHandlerTest {

    /** 사이트에 나간 요청 수만 센다. */
    static class CountingCrawler implements ProductImageCrawler {
        private final String maker;
        final AtomicInteger calls = new AtomicInteger();

        CountingCrawler(String maker) { this.maker = maker; }

        @Override public String maker() { return maker; }
        @Override public String vendorCode() { return "Z"; }

        @Override
        public List<CrawledProduct> crawlAllProducts() {
            calls.incrementAndGet();
            return List.of();
        }
    }

    static class NoopHandler implements ManufacturerProductSyncHandler {
        private final String maker;

        NoopHandler(String maker) { this.maker = maker; }

        @Override public boolean supports(String m) { return maker.equalsIgnoreCase(m); }
        @Override public int order() { return 10; }
        @Override public void save(CrawledProduct crawled) { }
    }

    private static ProductImageSyncService service(List<ProductImageCrawler> crawlers,
                                                   List<ManufacturerProductSyncHandler> handlers) {
        SyncRunRepository repo = mock(SyncRunRepository.class);
        when(repo.findByRunTypeAndRunKey(any(), any())).thenReturn(Optional.empty());
        return new ProductImageSyncService(crawlers, handlers, new SyncRunService(repo));
    }

    @Test
    void 전용_핸들러가_없는_제조사는_크롤링_전에_거절된다() {
        // 크롤러는 있고, 핸들러는 다른 제조사 것뿐이다 (제조사 이름은 지어낸 것)
        CountingCrawler crawler = new CountingCrawler("NEWCO");
        ProductImageSyncService svc = service(List.of(crawler), List.of(new NoopHandler("OTHERCO")));

        // IllegalArgumentException → 400 (GlobalExceptionHandler)
        assertThatThrownBy(() -> svc.syncByMaker("NEWCO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("저장 핸들러가 없는");

        assertThat(crawler.calls.get()).as("거절은 사이트 요청보다 먼저다").isZero();
    }

    @Test
    void 전용_핸들러가_있으면_그_핸들러로_돈다() throws Exception {
        CountingCrawler crawler = new CountingCrawler("NEWCO");
        ProductImageSyncService svc = service(List.of(crawler), List.of(new NoopHandler("NEWCO")));

        assertThat(svc.syncByMaker("NEWCO")).isNotNull();
        assertThat(crawler.calls.get()).isEqualTo(1);
    }
}
