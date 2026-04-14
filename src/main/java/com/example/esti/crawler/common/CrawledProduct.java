package com.example.esti.crawler.common;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrawledProduct {
    private String maker;          // ASTD, INUS
    private String vendorCode;     // A, B
    private Long siteProductId;    // 사이트 내부 상품 ID
    private String productCode;    // 실제 품번 / 모델코드
    private String productName;    // 제품명
    private String productUrl;     // 상세 URL
    private String imageUrl;       // 화면 표시용 이미지 URL
    private String downloadUrl;    // 원본 다운로드 URL
}
