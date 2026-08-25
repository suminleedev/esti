package com.example.esti.crawler.inus;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * INUS 크롤러 — 파서 미구현 상태라 빈 등록을 보류한다(호출 시 항상 no-op이었음).
 * 구현 완료 후 {@code @Component}를 복원할 것. 등록 해제 상태에서는
 * {@code POST /api/admin/crawler/INUS/images} 가 "지원하지 않는 제조사" 오류로 응답한다.
 */
public class InusCrawler implements ProductImageCrawler {

    @Value("${app.crawler.inus.maker}")
    private String maker;

    @Value("${app.crawler.inus.vendor-code}")
    private String vendorCode;

    @Value("${app.crawler.inus.category-url}")
    private String categoryUrl;

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

    @Override
    public List<String> collectProductUrls() throws Exception {
        Document doc = Jsoup.connect(categoryUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        return Collections.emptyList();
    }

    @Override
    public Optional<CrawledProduct> crawlProduct(String productUrl) throws Exception {
        Document doc = Jsoup.connect(productUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        return parser.parse(productUrl, doc, maker, vendorCode);
    }
}