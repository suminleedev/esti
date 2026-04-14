package com.example.esti.crawler.common;

import java.util.List;
import java.util.Optional;

public interface ProductImageCrawler {
    // 코드 내부 식별용
    String maker();       // ASTD, INUS

    // DB 매칭용
    String vendorCode();  // A, B

    List<String> collectProductUrls() throws Exception;

    Optional<CrawledProduct> crawlProduct(String productUrl) throws Exception;
}
