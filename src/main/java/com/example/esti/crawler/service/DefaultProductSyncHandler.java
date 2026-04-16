package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProductSyncHandler implements ManufacturerProductSyncHandler {

    private final ImageDownloadService imageDownloadService;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final VendorProductRepository vendorProductRepository;

    @Override
    public boolean supports(String maker) {
        return true;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    @Transactional
    public void save(CrawledProduct crawled) {
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

            VendorItemPrice vip = vendorItemPriceRepository
                    .findByVendor_VendorCodeAndProposalItemCode(
                            crawled.getVendorCode(),
                            crawled.getProductCode()
                    )
                    .orElse(null);

            if (vip == null) {
                log.info("[{}] vendorItemPrice not found. vendorCode={}, proposalItemCode={}",
                        crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode());
                return;
            }

            String fileName = crawled.getVendorCode() + "_" + crawled.getProductCode() + ".jpg";
            ImageDownloadService.DownloadResult result =
                    imageDownloadService.download(sourceUrl, fileName);

            VendorProduct vendorProduct = vendorProductRepository
                    .findByVendorAndProductCode(vip.getVendor(), vip.getProposalItemCode())
                    .orElseGet(() -> VendorProduct.builder()
                            .vendor(vip.getVendor())
                            .productCode(vip.getProposalItemCode())
                            .representativeCode(vip.getProposalItemCode())
                            .detailCode(null)
                            .build());

            vendorProduct.setProductName(crawled.getProductName());
            vendorProduct.setCollectionName(crawled.getCollectionName());
            vendorProduct.setImageUrl(result.relativePath());
            vendorProduct.setDetailUrl(crawled.getProductUrl());
            vendorProduct.setRawTagText(crawled.getRawTagText());

            vendorProduct = vendorProductRepository.save(vendorProduct);

            vip.setVendorProduct(vendorProduct);
            vendorItemPriceRepository.save(vip);

            log.info("[{}] saved vendorProduct. vendorCode={}, productCode={}, path={}",
                    crawled.getMaker(),
                    crawled.getVendorCode(),
                    crawled.getProductCode(),
                    result.relativePath());

        } catch (Exception e) {
            log.error("[{}] save failed. vendorCode={}, productCode={}",
                    crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode(), e);
        }
    }
}
