package com.example.esti.crawler.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface ProductImageCrawler {
    // 코드 내부 식별용
    String maker();       // ASTD, INUS

    // DB 매칭용
    String vendorCode();  // A, B

    List<String> collectProductUrls() throws Exception;

    Optional<CrawledProduct> crawlProduct(String productUrl) throws Exception;

    // 목록 기반 크롤링 : ASTD
    default List<CrawledProduct> crawlAllProducts() throws Exception {
        List<CrawledProduct> results = new ArrayList<>();

        for (String productUrl : collectProductUrls()) {
            crawlProduct(productUrl).ifPresent(results::add);
        }

        return results;
    }
}
