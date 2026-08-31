package com.example.esti.crawler.inus;

import com.example.esti.crawler.common.CrawlException;
import com.example.esti.crawler.common.CrawlResult;
import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * INUS(이누스) 이미지 크롤러.
 *
 * <p>ASTD와 달리 <b>상세 페이지를 돌지 않는다.</b> 이 사이트는 상세 페이지 자체가 없고
 * 리스트 HTML에 품번·이미지·품목명이 다 들어 있어, 리스트 6장을 받는 것으로 수집이 끝난다.
 * 그래서 {@link #collectProductUrls()}·{@link #crawlProduct(String)}는 쓰이지 않고
 * {@link #crawlAllProducts()}만 구현한다.
 */
@Slf4j
@Component
public class InusCrawler implements ProductImageCrawler {

    /** 페이지네이션을 건너뛰고 한 번에 전량을 받는다. */
    private static final String FULL_LIST_QUERY = "?count=9999";

    /**
     * jsoup 기본 응답 한도는 2MB이고, <b>넘으면 예외 없이 조용히 잘린다.</b>
     * 제품이 줄어든 것과 구분이 안 되므로 여유 있게 박아 둔다(가장 큰 리스트가 약 400KB).
     */
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    @Value("${app.crawler.inus.maker}")
    private String maker;

    @Value("${app.crawler.inus.vendor-code}")
    private String vendorCode;

    @Value("${app.crawler.inus.base-url}")
    private String baseUrl;

    @Value("${app.crawler.inus.list-paths}")
    private String listPaths;

    @Value("${app.crawler.inus.request-delay-ms}")
    private long requestDelayMs;

    @Value("${app.crawler.user-agent}")
    private String userAgent;

    @Value("${app.crawler.timeout-ms}")
    private int timeoutMs;

    private final InusParser parser = new InusParser();

    @Override
    public String maker() {
        return maker;
    }

    @Override
    public String vendorCode() {
        return vendorCode;
    }

    /** 리스트만으로 수집이 끝나 쓰이지 않는다. */
    @Override
    public List<String> collectProductUrls() {
        return Collections.emptyList();
    }

    /** 상세 페이지가 없어 쓰이지 않는다. */
    @Override
    public Optional<CrawledProduct> crawlProduct(String productUrl) {
        return Optional.empty();
    }

    @Override
    public List<CrawledProduct> crawlAllProducts() throws Exception {
        return crawlAll().products();
    }

    @Override
    public CrawlResult crawlAll() throws Exception {
        List<String> urls = buildListUrls();

        // 품번 하나가 여러 리스트에 걸릴 수 있다. ASTD와 달리 siteProductId가 늘 null이라
        // 그걸 섞은 키는 의미가 없어 품번 단독을 키로 쓴다.
        Map<String, CrawledProduct> unique = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();

        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) {
                Thread.sleep(requestDelayMs);
            }

            String url = urls.get(i);
            try {
                List<CrawledProduct> products = parser.parseList(fetch(url), url, maker, vendorCode);
                products.forEach(p -> unique.putIfAbsent(p.getProductCode(), p));
                log.info("INUS 리스트 수집: {} → {}건", url, products.size());
            } catch (Exception e) {
                // 한 리스트가 실패해도 나머지는 살린다. 다만 조용히 넘어가지는 않는다 —
                // 부분 반영을 성공으로 착각하는 게 가장 나쁘다.
                failed.add(url);
                log.warn("INUS 리스트 수집 실패(건너뜀): {} — {}", url, e.toString());
            }
        }

        if (failed.size() == urls.size()) {
            throw new CrawlException("INUS 리스트를 하나도 받지 못했습니다: " + failed);
        }
        if (!failed.isEmpty()) {
            log.warn("INUS 리스트 {}개 중 {}개 실패 — 부분 수집으로 진행합니다: {}",
                    urls.size(), failed.size(), failed);
        }

        // 몇 개 중 몇 개를 받았는지 함께 넘긴다. 리포트가 부분 수집을 감추지 않게 하려는 것이다.
        return new CrawlResult(
                new ArrayList<>(unique.values()),
                urls.size(),
                urls.size() - failed.size(),
                List.copyOf(failed));
    }

    private Document fetch(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .maxBodySize(MAX_BODY_SIZE)
                .get();
    }

    private List<String> buildListUrls() {
        List<String> urls = new ArrayList<>();

        for (String token : listPaths.split(",")) {
            String path = token.trim();
            if (path.isBlank()) {
                continue;
            }
            urls.add(trimTrailingSlash(baseUrl) + path + FULL_LIST_QUERY);
        }

        if (urls.isEmpty()) {
            throw new IllegalArgumentException("INUS list-paths가 비어 있습니다.");
        }

        return urls;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
