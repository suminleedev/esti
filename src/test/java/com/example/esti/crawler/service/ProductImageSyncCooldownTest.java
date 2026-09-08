package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import com.example.esti.entity.SyncRun;
import com.example.esti.entity.SyncRunType;
import com.example.esti.exception.RateLimitedException;
import com.example.esti.repository.SyncRunRepository;
import com.example.esti.service.SyncRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 쿨다운(C-2)과 실행 상태(C-3).
 *
 * <p>C-1이 «겹치는 것»을 막았다면 여기는 «너무 자주 도는 것»을 거른다. 둘은 다른 거절이다 —
 * 409는 끝나기를 기다릴 일이고, 429는 지금 안 해도 되는 일이다.
 *
 * <p>C-3은 <b>막지 않는다.</b> 판단 재료만 내고 결정은 사람이 한다.
 * 그래서 «모르는 것을 모른다고 답하는지»가 여기서 가장 중요한 검증이다.
 */
class ProductImageSyncCooldownTest {

    /** 즉시 끝나는 크롤러. 쿨다운 값만 바꿔 가며 쓴다. */
    static class StubCrawler implements ProductImageCrawler {
        private final String maker;
        private final String vendorCode;
        private final int cooldownMinutes;
        final AtomicInteger calls = new AtomicInteger();

        StubCrawler(String maker, String vendorCode, int cooldownMinutes) {
            this.maker = maker;
            this.vendorCode = vendorCode;
            this.cooldownMinutes = cooldownMinutes;
        }

        @Override public String maker() { return maker; }
        @Override public String vendorCode() { return vendorCode; }
        @Override public int cooldownMinutes() { return cooldownMinutes; }

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
        @Override public int order() { return 0; }
        @Override public void save(CrawledProduct crawled) { }
    }

    /** 인메모리 기록. 실제 저장소 대신 쓰되 서비스 로직은 진짜를 태운다. */
    private Map<String, SyncRun> store;
    private SyncRunService syncRunService;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        SyncRunRepository repo = mock(SyncRunRepository.class);
        when(repo.findByRunTypeAndRunKey(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(key(inv.getArgument(0), inv.getArgument(1)))));
        when(repo.save(any(SyncRun.class))).thenAnswer(inv -> {
            SyncRun run = inv.getArgument(0);
            store.put(key(run.getRunType(), run.getRunKey()), run);
            return run;
        });
        syncRunService = new SyncRunService(repo);
    }

    private static String key(SyncRunType type, String runKey) {
        return type + " " + runKey;
    }

    private ProductImageSyncService service(StubCrawler... crawlers) {
        List<ProductImageCrawler> cs = List.of((ProductImageCrawler[]) crawlers);
        List<ManufacturerProductSyncHandler> hs = cs.stream()
                .map(c -> (ManufacturerProductSyncHandler) new NoopHandler(c.maker()))
                .toList();
        return new ProductImageSyncService(cs, hs, syncRunService);
    }

    /* C-2 쿨다운 */

    @Test
    void 첫_실행은_쿨다운에_걸리지_않는다() throws Exception {
        StubCrawler crawler = new StubCrawler("ASTD", "A", 360);
        ProductImageSyncService svc = service(crawler);

        assertThat(svc.syncByMaker("ASTD")).isNotNull();
        assertThat(crawler.calls.get()).isEqualTo(1);
    }

    @Test
    void 쿨다운_안에_다시_부르면_429다() throws Exception {
        StubCrawler crawler = new StubCrawler("ASTD", "A", 360);
        ProductImageSyncService svc = service(crawler);

        svc.syncByMaker("ASTD");

        assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("분 뒤에 다시 시도");

        // 거절된 요청은 사이트로 나가지 않았다.
        assertThat(crawler.calls.get()).isEqualTo(1);
    }

    @Test
    void 쿨다운이_지나면_다시_돈다() throws Exception {
        StubCrawler crawler = new StubCrawler("ASTD", "A", 60);
        ProductImageSyncService svc = service(crawler);

        // 61분 전에 돈 것으로 기록해 둔다.
        syncRunService.record(SyncRunType.IMAGE_CRAWL, "ASTD", true, LocalDateTime.now().minusMinutes(61));

        assertThat(svc.syncByMaker("ASTD")).isNotNull();
        assertThat(crawler.calls.get()).isEqualTo(1);
    }

    @Test
    void force면_쿨다운을_건너뛴다() throws Exception {
        // 이미지가 안 받아졌을 때처럼 «지금 꼭 돌려야 하는» 경우가 있다. 막는 게 아니라 되묻는 장치다.
        StubCrawler crawler = new StubCrawler("ASTD", "A", 360);
        ProductImageSyncService svc = service(crawler);

        svc.syncByMaker("ASTD");
        assertThat(svc.syncByMaker("ASTD", false, true)).isNotNull();

        assertThat(crawler.calls.get()).isEqualTo(2);
    }

    @Test
    void 쿨다운이_0이면_제한하지_않는다() throws Exception {
        StubCrawler crawler = new StubCrawler("ASTD", "A", 0);
        ProductImageSyncService svc = service(crawler);

        svc.syncByMaker("ASTD");
        svc.syncByMaker("ASTD");

        assertThat(crawler.calls.get()).isEqualTo(2);
    }

    @Test
    void 제조사마다_쿨다운이_따로_돈다() throws Exception {
        // 22분짜리와 12초짜리에 같은 값을 주지 않는다는 것이 이 설계의 요지다.
        StubCrawler astd = new StubCrawler("ASTD", "A", 360);
        StubCrawler inus = new StubCrawler("INUS", "B", 0);
        ProductImageSyncService svc = service(astd, inus);

        svc.syncByMaker("ASTD");
        svc.syncByMaker("INUS");
        svc.syncByMaker("INUS");

        assertThatThrownBy(() -> svc.syncByMaker("ASTD")).isInstanceOf(RateLimitedException.class);
        assertThat(inus.calls.get()).isEqualTo(2);
    }

    @Test
    void dry_run도_쿨다운을_소모한다() throws Exception {
        // crawlAll()이 dry-run 분기보다 앞이라 점검만 해도 사이트 요청은 그대로 나간다.
        // 쿨다운이 지키는 것은 그 요청이므로 둘을 가르지 않는다.
        StubCrawler crawler = new StubCrawler("ASTD", "A", 360);
        ProductImageSyncService svc = service(crawler);

        svc.syncByMaker("ASTD", true);

        assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void 실패한_실행은_쿨다운을_잡지_않는다() {
        // 실패한 배치 때문에 다음 시도가 6시간 막히면 안 된다.
        StubCrawler crawler = new StubCrawler("ASTD", "A", 360) {
            @Override
            public List<CrawledProduct> crawlAllProducts() {
                calls.incrementAndGet();
                throw new RuntimeException("네트워크 끊김");
            }
        };
        ProductImageSyncService svc = service(crawler);

        assertThatThrownBy(() -> svc.syncByMaker("ASTD")).hasMessage("네트워크 끊김");
        assertThatThrownBy(() -> svc.syncByMaker("ASTD"))
                .hasMessage("네트워크 끊김")
                .isNotInstanceOf(RateLimitedException.class);

        assertThat(crawler.calls.get()).isEqualTo(2);
    }

    /* C-3 실행 상태 */

    @Test
    void 아직_돌린_적이_없으면_그렇게_답한다() {
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 360));

        CrawlerRunStatus status = svc.statusOf("ASTD");

        assertThat(status.lastRunAt()).isNull();
        assertThat(status.running()).isFalse();
        assertThat(status.cooldownRemainingSeconds()).isZero();
        assertThat(status.message()).contains("아직 돌린 적이 없다");
    }

    @Test
    void 업로드_기록이_없으면_모른다고_답한다() throws Exception {
        // 기록을 시작하기 전의 업로드는 남아 있지 않다.
        // 모르는 것을 «안 했음»으로 답하면 매번 «크롤링 필요»로 보여 표시가 쓸모없어진다.
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 0));
        svc.syncByMaker("ASTD");

        CrawlerRunStatus status = svc.statusOf("ASTD");

        assertThat(status.appliedSinceLastUpload()).isNull();
        assertThat(status.message()).contains("알 수 없다");
    }

    @Test
    void 업로드_뒤_크롤링을_안_했으면_돌릴_만하다고_답한다() {
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 360));
        syncRunService.record(SyncRunType.CATALOG_UPLOAD, "A", true);

        CrawlerRunStatus status = svc.statusOf("ASTD");

        assertThat(status.appliedSinceLastUpload()).isFalse();
        assertThat(status.message()).contains("돌릴 만하다");
    }

    @Test
    void 업로드_뒤_크롤링을_했으면_그렇게_답한다() {
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 360));
        syncRunService.record(SyncRunType.CATALOG_UPLOAD, "A", true, LocalDateTime.now().minusHours(2));
        syncRunService.record(SyncRunType.IMAGE_CRAWL, "ASTD", true, LocalDateTime.now().minusHours(1));

        CrawlerRunStatus status = svc.statusOf("ASTD");

        assertThat(status.appliedSinceLastUpload()).isTrue();
        assertThat(status.message()).contains("다시 돌릴 이유가 없을 수 있다");
    }

    @Test
    void dry_run은_크롤링_했음으로_세지_않는다() throws Exception {
        // dry-run은 이미지를 바꾸지 않는다. «했다»로 세면 거짓이다.
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 0));
        syncRunService.record(SyncRunType.CATALOG_UPLOAD, "A", true, LocalDateTime.now().minusHours(2));

        svc.syncByMaker("ASTD", true);

        CrawlerRunStatus status = svc.statusOf("ASTD");
        assertThat(status.lastRunAt()).isNotNull();           // 돌긴 돌았고
        assertThat(status.lastAppliedAt()).isNull();          // 반영은 안 했다
        assertThat(status.appliedSinceLastUpload()).isFalse();
    }

    @Test
    void 쿨다운_남은_시간을_알려준다() throws Exception {
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 60));
        svc.syncByMaker("ASTD");

        CrawlerRunStatus status = svc.statusOf("ASTD");

        assertThat(status.cooldownMinutes()).isEqualTo(60);
        assertThat(status.cooldownRemainingSeconds()).isBetween(3500L, 3600L);
        assertThat(status.message()).contains("쿨다운 중");
    }

    @Test
    void 없는_제조사의_상태를_물으면_400이다() {
        ProductImageSyncService svc = service(new StubCrawler("ASTD", "A", 360));

        assertThatThrownBy(() -> svc.statusOf("없는제조사"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
