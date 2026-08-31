package com.example.esti.crawler.common;

import java.util.List;

/**
 * 한 번의 수집 결과.
 *
 * <p>제품 목록만으로는 <b>부분 실패를 알 수 없다.</b> 이누스처럼 리스트 여러 장을 도는 크롤러는
 * 한 장이 실패해도 나머지를 살려 진행하는데, 그때 줄어든 건수와 원래 적은 건수가 구분되지 않는다.
 * 조용한 부분 반영이 가장 나쁘므로 몇 개 중 몇 개를 받았는지를 함께 들고 다닌다.
 *
 * @param sourcesTotal     받으려 한 소스(리스트·카테고리) 수
 * @param sourcesSucceeded 실제로 받은 수
 * @param failedSources    실패한 소스 URL
 */
public record CrawlResult(
        List<CrawledProduct> products,
        int sourcesTotal,
        int sourcesSucceeded,
        List<String> failedSources
) {

    /** 소스를 나누지 않는 크롤러용 — 한 덩어리를 받은 것으로 본다. */
    public static CrawlResult singleSource(List<CrawledProduct> products) {
        return new CrawlResult(products, 1, 1, List.of());
    }

    public boolean partial() {
        return sourcesSucceeded < sourcesTotal;
    }
}
