package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawlResult;
import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageSyncService {

    private final List<ProductImageCrawler> crawlers;
    private final List<ManufacturerProductSyncHandler> syncHandlers;

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
        ProductImageCrawler crawler = crawlers.stream()
                .filter(c -> c.maker().equalsIgnoreCase(maker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제조사 식별자: " + maker));

        ManufacturerProductSyncHandler handler = syncHandlers.stream()
                .filter(h -> h.supports(maker))
                .min(Comparator.comparingInt(ManufacturerProductSyncHandler::order))
                .orElseThrow(() -> new IllegalArgumentException("저장 핸들러가 없는 제조사 식별자: " + maker));

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
