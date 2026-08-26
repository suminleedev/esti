package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;

public interface ManufacturerProductSyncHandler {

    boolean supports(String maker);

    int order();

    void save(CrawledProduct crawled);

    /** 제조사 동기화 시작 전 1회 호출. 매칭 인덱스 등 준비물을 반환한다(없으면 null). */
    default Object prepare(String vendorCode) { return null; }

    /** prepare()가 만든 컨텍스트를 활용하는 저장. 기본 구현은 컨텍스트를 무시한다. */
    default void save(CrawledProduct crawled, Object context) { save(crawled); }
}
