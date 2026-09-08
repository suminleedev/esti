package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawlResult;
import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import com.example.esti.entity.SyncRunType;
import com.example.esti.exception.InvalidStateException;
import com.example.esti.exception.RateLimitedException;
import com.example.esti.service.SyncRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageSyncService {

    private final List<ProductImageCrawler> crawlers;
    private final List<ManufacturerProductSyncHandler> syncHandlers;
    private final SyncRunService syncRunService;

    /**
     * 제조사별 «실행 중» 표시 (C-1).
     *
     * <p>같은 제조사를 두 번 발사하면 <b>배치가 겹쳐서 나간다.</b> A사는 요청 44회를 30초 간격으로
     * 도는 약 22분짜리라, 겹치면 대상 사이트에는 Crawl-delay를 지킨다고 해놓고 두 배로 때리는 셈이 된다.
     * 지연 설정({@code request-delay-ms})은 그 약속을 «한 번 돌 때» 지키게 할 뿐,
     * <b>두 번 도는 것을 막아 주지는 않는다.</b>
     *
     * <p><b>제조사별로 나눈다.</b> 서로 다른 사이트라 A와 B가 함께 도는 것까지 막을 이유가 없다.
     *
     * <p><b>dry-run도 같은 표시를 쓴다.</b> {@code crawlAll()}이 dry-run 분기보다 앞에 있어
     * 점검만 해도 사이트 요청은 그대로 나간다 — 점검과 실행이 함께 돌면 트래픽은 역시 두 배다.
     *
     * <p>단일 서버 전제라 인메모리로 충분하다. 분산 잠금을 두지 않는다.
     */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /** 실반영. */
    public ImageSyncReport syncByMaker(String maker) throws Exception {
        return syncByMaker(maker, false);
    }

    /**
     * 트랜잭션 없이 실행한다 — 크롤링(네트워크 I/O)이 수 분간 커넥션을 점유하지 않도록.
     * DB 쓰기는 각 핸들러의 {@code save(@Transactional, 제품 단위)}가 자체 트랜잭션으로 수행한다.
     *
     * @param dryRun 참이면 <b>내려받지도 저장하지도 않고</b> 매칭 결과만 집계한다.
     *               덮어쓰기라 첫 실행이 기존 이미지를 갈아치우므로, 무엇이 바뀔지 먼저 보기 위한 것이다
     */
    public ImageSyncReport syncByMaker(String maker, boolean dryRun) throws Exception {
        return syncByMaker(maker, dryRun, false);
    }

    /**
     * @param force 참이면 쿨다운을 건너뛴다. <b>동시 실행 잠금은 건너뛰지 않는다</b> —
     *              그건 «지금 안 해도 되는 일»이 아니라 «겹치면 안 되는 일»이라 강제할 대상이 아니다
     */
    public ImageSyncReport syncByMaker(String maker, boolean dryRun, boolean force) throws Exception {
        ProductImageCrawler crawler = crawlers.stream()
                .filter(c -> c.maker().equalsIgnoreCase(maker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제조사 식별자: " + maker));

        ManufacturerProductSyncHandler handler = syncHandlers.stream()
                .filter(h -> h.supports(maker))
                .min(Comparator.comparingInt(ManufacturerProductSyncHandler::order))
                .orElseThrow(() -> new IllegalArgumentException("저장 핸들러가 없는 제조사 식별자: " + maker));

        // 잠금은 조회를 마친 뒤에 잡는다 — 없는 제조사는 «이미 실행 중»이 아니라 «그런 제조사 없음»이다.
        String key = maker.toUpperCase(Locale.ROOT);
        if (!running.add(key)) {
            log.warn("[{}] 이미 실행 중이라 요청을 거절했다 (dryRun={})", maker, dryRun);
            throw new InvalidStateException(
                    maker + " 이미지 동기화가 이미 실행 중입니다. 끝난 뒤에 다시 시도해 주세요.");
        }

        try {
            // 쿨다운은 잠금을 잡은 뒤에 본다 — 판정이 겹치지 않도록.
            // «이미 실행 중»이 «너무 이름»보다 먼저 나오는 것도 맞다: 앞엣것은 기다릴 일이고 뒤엣것은 아니다.
            if (!force) requireCooldownPassed(maker, crawler.cooldownMinutes());

            ImageSyncReport report = runSync(maker, dryRun, crawler, handler);

            // 기록은 성공한 실행만 남긴다. 실패한 배치 때문에 다음 시도가 막히면 안 된다.
            syncRunService.record(SyncRunType.IMAGE_CRAWL, maker, !dryRun);

            return report;
        } finally {
            // 예외로 죽어도 반드시 푼다. 안 풀면 재기동 전까지 그 제조사가 잠긴 채로 남는다.
            running.remove(key);
        }
    }

    /** 마지막 실행에서 충분히 지났는지 본다. 아니면 429. */
    private void requireCooldownPassed(String maker, int cooldownMinutes) {
        if (cooldownMinutes <= 0) return;

        Optional<LocalDateTime> last = syncRunService.lastRunAt(SyncRunType.IMAGE_CRAWL, maker);
        if (last.isEmpty()) return;

        Duration elapsed = Duration.between(last.get(), LocalDateTime.now());
        Duration cooldown = Duration.ofMinutes(cooldownMinutes);
        if (elapsed.compareTo(cooldown) >= 0) return;

        Duration remaining = cooldown.minus(elapsed);
        log.warn("[{}] 쿨다운에 걸려 요청을 거절했다 (남은 {}분)", maker, remaining.toMinutes());
        throw new RateLimitedException(
                maker + " 이미지 동기화는 " + cooldownMinutes + "분에 한 번만 돕니다. "
                        + "약 " + (remaining.toMinutes() + 1) + "분 뒤에 다시 시도하거나, "
                        + "지금 꼭 돌려야 하면 force=true로 요청해 주세요.");
    }

    /** 지금 도는 중인가. 상태 조회용. */
    public boolean isRunning(String maker) {
        return running.contains(maker.toUpperCase(Locale.ROOT));
    }

    /** 등록된 제조사의 크롤러를 찾는다. 없으면 400. */
    public ProductImageCrawler crawlerOf(String maker) {
        return crawlers.stream()
                .filter(c -> c.maker().equalsIgnoreCase(maker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제조사 식별자: " + maker));
    }

    /**
     * 실행 상태 (C-3). 지금 돌아도 되는지, 돌 필요가 있는지를 판단할 재료를 낸다.
     *
     * <p><b>모르는 것은 모른다고 답한다</b> — 기록을 시작하기 전의 업로드는 남아 있지 않아
     * 그때는 {@code appliedSinceLastUpload}가 {@code null}이다. 「안 했음」으로 답하면
     * 매번 «크롤링 필요»로 보여 표시 자체가 쓸모없어진다.
     */
    public CrawlerRunStatus statusOf(String maker) {
        ProductImageCrawler crawler = crawlerOf(maker);
        String name = crawler.maker();

        LocalDateTime lastRun = syncRunService.lastRunAt(SyncRunType.IMAGE_CRAWL, name).orElse(null);
        LocalDateTime lastApplied = syncRunService.lastAppliedAt(SyncRunType.IMAGE_CRAWL, name).orElse(null);
        LocalDateTime lastUpload = syncRunService
                .lastAppliedAt(SyncRunType.CATALOG_UPLOAD, crawler.vendorCode()).orElse(null);

        Boolean appliedSinceUpload = (lastUpload == null)
                ? null
                : lastApplied != null && lastApplied.isAfter(lastUpload);

        int cooldownMinutes = crawler.cooldownMinutes();
        long remaining = 0;
        if (cooldownMinutes > 0 && lastRun != null) {
            Duration left = Duration.ofMinutes(cooldownMinutes)
                    .minus(Duration.between(lastRun, LocalDateTime.now()));
            remaining = Math.max(0, left.toSeconds());
        }

        boolean isRunning = isRunning(name);

        return new CrawlerRunStatus(
                name, crawler.vendorCode(), isRunning,
                lastRun, lastApplied, lastUpload, appliedSinceUpload,
                cooldownMinutes, remaining,
                describeStatus(isRunning, lastRun, appliedSinceUpload, remaining));
    }

    private static String describeStatus(
            boolean isRunning, LocalDateTime lastRun, Boolean appliedSinceUpload, long remainingSeconds) {

        if (isRunning) return "지금 돌고 있다.";

        StringBuilder sb = new StringBuilder();
        if (lastRun == null) {
            sb.append("아직 돌린 적이 없다");
        } else if (remainingSeconds > 0) {
            sb.append("쿨다운 중 — 약 ").append(remainingSeconds / 60 + 1).append("분 남음");
        } else {
            sb.append("지금 돌릴 수 있다");
        }

        // «업로드 이후 했나»는 어느 경우에도 붙인다. 한 번도 안 돌린 상태에서 조기 반환하면
        // 정작 가장 쓸모 있는 신호(«업로드했는데 아직 안 돌렸다»)가 사라진다.

        if (appliedSinceUpload == null) {
            sb.append(" | 마지막 업로드 기록이 없어 «업로드 이후 크롤링 여부»는 알 수 없다");
        } else if (appliedSinceUpload) {
            sb.append(" | 마지막 업로드 이후 크롤링했다 — 다시 돌릴 이유가 없을 수 있다");
        } else {
            sb.append(" | 마지막 업로드 이후 크롤링하지 않았다 — 돌릴 만하다");
        }
        return sb.toString();
    }

    private ImageSyncReport runSync(
            String maker,
            boolean dryRun,
            ProductImageCrawler crawler,
            ManufacturerProductSyncHandler handler
    ) throws Exception {
        CrawlResult crawl = crawler.crawlAll();
        log.info("[{}] collected {} products ({}/{} sources)",
                maker, crawl.products().size(), crawl.sourcesSucceeded(), crawl.sourcesTotal());

        Object context = handler.prepare(crawler.vendorCode());

        int processed = 0;
        int failed = 0;

        for (CrawledProduct crawled : crawl.products()) {
            try {
                if (dryRun) {
                    handler.inspect(crawled, context);
                } else {
                    handler.save(crawled, context);
                }
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("[{}] {} failed. productUrl={}",
                        maker, dryRun ? "inspect" : "save", crawled.getProductUrl(), e);
            }
        }

        return buildReport(maker, dryRun, crawl, context, processed, failed);
    }

    private ImageSyncReport buildReport(
            String maker,
            boolean dryRun,
            CrawlResult crawl,
            Object context,
            int processed,
            int failed
    ) {
        ImageSyncReport.MatchDetail match = (context instanceof SyncMatchCounters counters)
                ? ImageSyncReport.MatchDetail.from(counters)
                : null;

        ImageSyncReport report = new ImageSyncReport(
                maker,
                dryRun,
                crawl.sourcesTotal(),
                crawl.sourcesSucceeded(),
                crawl.failedSources(),
                crawl.products().size(),
                processed,
                failed,
                match,
                describe(maker, dryRun, crawl, match, processed, failed));

        log.info("[{}] {}", maker, report.message());

        return report;
    }

    private String describe(
            String maker,
            boolean dryRun,
            CrawlResult crawl,
            ImageSyncReport.MatchDetail match,
            int processed,
            int failed
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(maker).append(dryRun ? " 매칭 점검(dry-run)" : " 이미지 동기화");
        sb.append(" — 소스 ").append(crawl.sourcesTotal()).append("개 중 ")
          .append(crawl.sourcesSucceeded()).append("개 성공");

        // 부분 수집을 감추지 않는다. 조용히 적게 받아 온 것이 가장 나쁘다.
        if (crawl.partial()) {
            sb.append(" ⚠️ 실패: ").append(crawl.failedSources());
        }

        sb.append(", 수집 ").append(crawl.products().size()).append("건");
        sb.append(", 처리 ").append(processed).append("건");

        if (failed > 0) {
            sb.append(", 실패 ").append(failed).append("건");
        }

        if (match != null) {
            sb.append(" | 정확 매칭 ").append(match.exactMatched()).append("건")
              .append(", 완화 후보 ").append(match.relaxedOnly()).append("건")
              .append(", DB 부재 ").append(match.notInDb()).append("건")
              .append(dryRun ? " | 반영 예정 " : " | 반영 ")
              .append(match.rowsAffected()).append("행")
              .append("(충전 ").append(match.rowsFilled())
              .append(" · 교체 ").append(match.rowsReplaced()).append(")");
        }

        return sb.toString();
    }
}
