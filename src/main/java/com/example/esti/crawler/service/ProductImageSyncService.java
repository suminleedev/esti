package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageSyncService {

    private final List<ProductImageCrawler> crawlers;
    private final List<ManufacturerProductSyncHandler> syncHandlers;

    @Transactional
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

        for (CrawledProduct crawled : products) {
            try {
                handler.save(crawled);
            } catch (Exception e) {
                log.error("[{}] save failed. productUrl={}", maker, crawled.getProductUrl(), e);
            }
        }
    }
}