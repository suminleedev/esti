package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.crawler.common.ProductImageCrawler;
import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.repository.ProductCatalogRepository;
import com.example.esti.repository.VendorItemPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageSyncService {

    private final List<ProductImageCrawler> crawlers;
    private final ImageDownloadService imageDownloadService;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final ProductCatalogRepository productCatalogRepository;

    @Transactional
    public void syncByMaker(String maker) throws Exception {
        ProductImageCrawler crawler = crawlers.stream()
                .filter(c -> c.maker().equalsIgnoreCase(maker))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 제조사 식별자: " + maker));

        List<String> productUrls = crawler.collectProductUrls();
        log.info("[{}] collected {} product urls", maker, productUrls.size());

        for (String productUrl : productUrls) {
            try {
                crawler.crawlProduct(productUrl).ifPresent(this::saveProductImage);
            } catch (Exception e) {
                log.error("[{}] crawl failed. url={}", maker, productUrl, e);
            }
        }
    }

    private void saveProductImage(CrawledProduct crawled) {
        try {
            if (crawled.getProductCode() == null || crawled.getProductCode().isBlank()) {
                log.info("[{}] skip. no productCode. url={}", crawled.getMaker(), crawled.getProductUrl());
                return;
            }

            String sourceUrl = crawled.getDownloadUrl() != null && !crawled.getDownloadUrl().isBlank()
                    ? crawled.getDownloadUrl()
                    : crawled.getImageUrl();

            if (sourceUrl == null || sourceUrl.isBlank()) {
                log.info("[{}] skip. no image url. productCode={}", crawled.getMaker(), crawled.getProductCode());
                return;
            }

            VendorItemPrice vendorItemPrice = vendorItemPriceRepository
                    .findByVendor_VendorCodeAndProposalItemCode(
                            crawled.getVendorCode(),
                            crawled.getProductCode()
                    )
                    .orElse(null);

            if (vendorItemPrice == null) {
                log.info("[{}] vendorItemPrice not found. vendorCode={}, proposalItemCode={}",
                        crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode());
                return;
            }

            ProductCatalog catalog = vendorItemPrice.getCatalog();

            if (catalog == null) {
                log.info("[{}] catalog is null. vendorCode={}, productCode={}",
                        crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode());
                return;
            }

            String fileName = crawled.getVendorCode() + "_" + crawled.getProductCode() + ".jpg";

            ImageDownloadService.DownloadResult result =
                    imageDownloadService.download(sourceUrl, fileName);

            catalog.setImageUrl(result.relativePath());
            productCatalogRepository.save(catalog);

            log.info("[{}] saved image. vendorCode={}, productCode={}, catalogId={}, path={}",
                    crawled.getMaker(),
                    crawled.getVendorCode(),
                    crawled.getProductCode(),
                    catalog.getId(),
                    result.relativePath());

        } catch (Exception e) {
            log.error("[{}] save failed. vendorCode={}, productCode={}",
                    crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode(), e);
        }
    }
}