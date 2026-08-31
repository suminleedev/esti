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

    /**
     * 수집 결과를 부분 실패 정보와 함께 돌려준다.
     * 소스를 나누지 않는 크롤러는 기본 구현으로 충분하다.
     */
    default CrawlResult crawlAll() throws Exception {
        return CrawlResult.singleSource(crawlAllProducts());
    }

    // 목록 기반 크롤링 : ASTD
    default List<CrawledProduct> crawlAllProducts() throws Exception {
        List<CrawledProduct> results = new ArrayList<>();

        for (String productUrl : collectProductUrls()) {
            crawlProduct(productUrl).ifPresent(results::add);
        }

        return results;
    }
}
