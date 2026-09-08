package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import com.example.esti.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 크롤러 동시 실행 잠금 (C-1).
 *
 * <p>막으려는 것은 «배치가 겹쳐 나가는 것»이다. A사는 요청 44회를 30초 간격으로 도는
 * 약 22분짜리라, 두 번 발사되면 대상 사이트에는 Crawl-delay를 지킨다고 해놓고 두 배로 나간다.
 * {@code request-delay-ms}는 «한 번 돌 때»의 약속이라 이걸 막아 주지 못한다.
 *
 * <p><b>실제 크롤링을 돌리지 않는다.</b> 크롤러를 스텁으로 두고 잠금 동작만 본다 —
 * 검증하자고 사이트에 22분짜리를 또 보낼 수는 없다.
 */
class ProductImageSyncLockTest {

    /** 첫 호출을 임계구역 안에 붙잡아 두는 크롤러. 그 사이 두 번째 호출을 던져 본다. */
    static class BlockingCrawler implements ProductImageCrawler {
        private final String maker;
        private final String vendorCode;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        private final boolean blocking;
        private final RuntimeException toThrow;

        BlockingCrawler(String maker, String vendorCode, boolean blocking, RuntimeException toThrow) {
            this.maker = maker;
            this.vendorCode = vendorCode;
            this.blocking = blocking;
            this.toThrow = toThrow;
        }

        static BlockingCrawler blocking(String maker, String vendorCode) {
            return new BlockingCrawler(maker, vendorCode, true, null);
        }

        static BlockingCrawler instant(String maker, String vendorCode) {
            return new BlockingCrawler(maker, vendorCode, false, null);
        }

        static BlockingCrawler failing(String maker, String vendorCode, RuntimeException e) {
            return new BlockingCrawler(maker, vendorCode, false, e);
        }

        @Override public String maker() { return maker; }
        @Override public String vendorCode() { return vendorCode; }

        @Override
        public List<CrawledProduct> crawlAllProducts() throws Exception {
            calls.incrementAndGet();
            entered.countDown();
            if (toThrow != null) throw toThrow;
            if (blocking) release.await(5, TimeUnit.SECONDS);
            return List.of();
        }
    }

    /** 저장은 하지 않는다. 이 테스트가 보는 건 잠금뿐이다. */
    static class NoopHandler implements ManufacturerProductSyncHandler {
        private final String maker;

        NoopHandler(String maker) { this.maker = maker; }

        @Override public boolean supports(String m) { return maker.equalsIgnoreCase(m); }
        @Override public int order() { return 0; }
        @Override public void save(CrawledProduct crawled) { }
    }

    private static ProductImageSyncService service(BlockingCrawler... crawlers) {
        List<ProductImageCrawler> cs = List.of((ProductImageCrawler[]) crawlers);
        List<ManufacturerProductSyncHandler> hs = cs.stream()
                .map(c -> (ManufacturerProductSyncHandler) new NoopHandler(c.maker()))
                .toList();
        return new ProductImageSyncService(cs, hs);
    }

    /** 첫 호출을 임계구역에 붙잡아 둔 채로 검사할 일을 시킨다. */
    private static void whileRunning(ProductImageSyncService svc, BlockingCrawler crawler,
                                     String maker, ThrowingRunnable body) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> svc.syncByMaker(maker));
            assertThat(crawler.entered.await(5, TimeUnit.SECONDS))
                    .as("첫 호출이 크롤링 단계까지 들어와야 한다").isTrue();

            body.run();

            crawler.release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            crawler.release.countDown();
            pool.shutdownNow();
        }
    }

    interface ThrowingRunnable { void run() throws Exception; }

    @Test
    void 같은_제조사를_겹쳐_발사하면_두_번째는_거절된다() throws Exception {
        BlockingCrawler crawler = BlockingCrawler.blocking("ASTD", "A");
        ProductImageSyncService svc = service(crawler);

        whileRunning(svc, crawler, "ASTD", () ->
                assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                        .isInstanceOf(InvalidStateException.class)
                        .hasMessageContaining("이미 실행 중"));

        // 거절된 요청은 크롤링에 들어가지 않았다 — 사이트로 나간 배치는 한 번뿐이다.
        assertThat(crawler.calls.get()).isEqualTo(1);
    }

    @Test
    void dry_run도_같은_잠금을_쓴다() throws Exception {
        // crawlAll()이 dry-run 분기보다 앞에 있어 점검만 해도 사이트 요청은 그대로 나간다.
        BlockingCrawler crawler = BlockingCrawler.blocking("ASTD", "A");
        ProductImageSyncService svc = service(crawler);

        whileRunning(svc, crawler, "ASTD", () ->
                assertThatThrownBy(() -> svc.syncByMaker("ASTD", true))
                        .isInstanceOf(InvalidStateException.class));

        assertThat(crawler.calls.get()).isEqualTo(1);
    }

    @Test
    void 대소문자가_달라도_같은_제조사로_본다() throws Exception {
        BlockingCrawler crawler = BlockingCrawler.blocking("ASTD", "A");
        ProductImageSyncService svc = service(crawler);

        whileRunning(svc, crawler, "ASTD", () ->
                assertThatThrownBy(() -> svc.syncByMaker("astd"))
                        .isInstanceOf(InvalidStateException.class));
    }

    @Test
    void 다른_제조사는_서로_막지_않는다() throws Exception {
        // 서로 다른 사이트라 함께 도는 것까지 막을 이유가 없다.
        BlockingCrawler astd = BlockingCrawler.blocking("ASTD", "A");
        BlockingCrawler inus = BlockingCrawler.instant("INUS", "B");
        ProductImageSyncService svc = service(astd, inus);

        whileRunning(svc, astd, "ASTD", () ->
                assertThat(svc.syncByMaker("INUS")).isNotNull());

        assertThat(inus.calls.get()).isEqualTo(1);
    }

    @Test
    void 끝나면_잠금이_풀린다() throws Exception {
        BlockingCrawler crawler = BlockingCrawler.instant("ASTD", "A");
        ProductImageSyncService svc = service(crawler);

        assertThat(svc.syncByMaker("ASTD")).isNotNull();
        assertThat(svc.syncByMaker("ASTD")).isNotNull();

        assertThat(crawler.calls.get()).isEqualTo(2);
    }

    @Test
    void 예외로_죽어도_잠금이_풀린다() throws Exception {
        // 안 풀면 재기동 전까지 그 제조사가 잠긴 채로 남는다 — 크롤링은 실패하기 쉬운 작업이라
        // 여기가 새면 «한 번 실패하면 영영 못 돈다»가 된다.
        BlockingCrawler crawler = BlockingCrawler.failing("ASTD", "A", new RuntimeException("네트워크 끊김"));
        ProductImageSyncService svc = service(crawler);

        assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("네트워크 끊김");

        // 두 번째 호출이 «이미 실행 중»으로 막히면 안 된다. 같은 예외까지 도달해야 한다.
        assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(InvalidStateException.class)
                .hasMessage("네트워크 끊김");

        assertThat(crawler.calls.get()).isEqualTo(2);
    }

    @Test
    void 없는_제조사는_잠금을_잡지_않는다() throws Exception {
        // «그런 제조사 없음»(400)이지 «이미 실행 중»(409)이 아니다.
        BlockingCrawler crawler = BlockingCrawler.instant("ASTD", "A");
        ProductImageSyncService svc = service(crawler);

        assertThatThrownBy(() -> svc.syncByMaker("없는제조사"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(InvalidStateException.class);

        // 잠긴 것이 없으니 다시 불러도 같은 답이 나온다.
        assertThatThrownBy(() -> svc.syncByMaker("없는제조사"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
