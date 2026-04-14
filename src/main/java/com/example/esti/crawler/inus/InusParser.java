package com.example.esti.crawler.inus;

import com.example.esti.crawler.common.CrawledProduct;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class InusParser {

    public Optional<CrawledProduct> parse(
            String productUrl,
            Document doc,
            String maker,
            String vendorCode
    ) {
        // TODO:
        // 1. 제품명 추출
        // 2. 품번(모델코드) 추출
        // 3. 이미지 URL 추출
        // 4. 필요하면 상세 페이지 링크 패턴에 맞춰 siteProductId 추출

        String productName = null;
        String productCode = null;
        String imageUrl = null;
        String downloadUrl = null;

        return Optional.of(CrawledProduct.builder()
                .maker(maker)
                .vendorCode(vendorCode)
                .siteProductId(null)
                .productCode(productCode)
                .productName(productName)
                .productUrl(productUrl)
                .imageUrl(imageUrl)
                .downloadUrl(downloadUrl)
                .build());
    }
}
