package com.example.esti.crawler.service;

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

    /**
     * 트랜잭션 없이 실행한다 — 크롤링(네트워크 I/O)이 수 분간 커넥션을 점유하지 않도록.
     * DB 쓰기는 각 핸들러의 {@code save(@Transactional, 제품 단위)}가 자체 트랜잭션으로 수행한다.
     */
    public void syncByMaker(String maker) throws Exception {
        ProductImageCrawler crawler = crawlers.stream()
                .filter(c -> c.maker().equalsIgnoreCase(maker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제조사 식별자: " + maker));

        ManufacturerProductSyncHandler handler = syncHandlers.stream()
                .filter(h -> h.supports(maker))
                .min(Comparator.comparingInt(ManufacturerProductSyncHandler::order))
                .orElseThrow(() -> new IllegalArgumentException("저장 핸들러가 없는 제조사 식별자: " + maker));

        List<CrawledProduct> products = crawler.crawlAllProducts();
        log.info("[{}] collected {} products", maker, products.size());

        Object context = handler.prepare(crawler.vendorCode());

        for (CrawledProduct crawled : products) {
            try {
                handler.save(crawled, context);
            } catch (Exception e) {
                log.error("[{}] save failed. productUrl={}", maker, crawled.getProductUrl(), e);
            }
        }
    }
}