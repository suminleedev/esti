package com.example.esti.crawler.common;

import java.util.List;

public interface ProductImageCrawler {
    // 코드 내부 식별용
    String maker();       // ASTD, INUS

    // DB 매칭용
    String vendorCode();  // A, B

    /**
     * 재실행 최소 간격(분). {@code 0} 이하면 제한하지 않는다 (C-2).
     *
     * <p><b>제조사별로 다르다.</b> 한쪽은 요청 44회를 30초 간격으로 도는 약 22분짜리이고,
     * 다른 쪽은 6회를 2초 간격으로 도는 약 12초짜리다. 같은 값을 주면 한쪽에는 과하고
     * 다른 쪽에는 모자란다. 값은 각 크롤러가 자기 네임스페이스에서 읽는다.
     */
    default int cooldownMinutes() {
        return 0;
    }

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
