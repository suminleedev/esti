package com.example.esti.crawler.common;

import java.util.List;

public interface ProductImageCrawler {
    // 코드 내부 식별용
    String maker();       // ASTD, INUS

    // DB 매칭용
    String vendorCode();  // A, B

    /**
     * 수집 결과를 부분 실패 정보와 함께 돌려준다.
     * 소스를 나누지 않는 크롤러는 기본 구현으로 충분하다.
     */
    default CrawlResult crawlAll() throws Exception {
        return CrawlResult.singleSource(crawlAllProducts());
    }

    /**
     * 목록을 돌며 전량을 수집한다.
     *
     * <p>예전에는 {@code collectProductUrls()}로 상세 URL을 모으고 {@code crawlProduct()}로
     * 한 건씩 여는 기본 구현이 있었다. <b>두 사이트 모두 리스트 HTML에 필요한 것이 다 들어 있어
     * 상세 페이지를 열 이유가 없었고, 구현체 둘 다 그 메서드를 빈 스텁으로 두고 이쪽만 구현했다.</b>
     * 아무도 호출하지 않는 경로라 지웠다.
     */
    List<CrawledProduct> crawlAllProducts() throws Exception;
}
