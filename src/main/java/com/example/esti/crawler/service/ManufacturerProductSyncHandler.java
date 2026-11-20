package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;

public interface ManufacturerProductSyncHandler {

    /**
     * 이 핸들러가 맡는 제조사인가.
     *
     * <p><b>모든 제조사를 받는 기본 핸들러는 두지 않는다.</b> 예전 기본 핸들러는 가격행을 찾아
     * 제품이 없으면 만들고 제품명을 사이트 이름으로 덮어, «카탈로그의 정본은 단가표» 원칙을 깼다
     * (2026-11-20 삭제). 전용 핸들러가 없는 제조사는 동기화 요청이 400으로 거절된다.
     */
    boolean supports(String maker);

    /** {@link #supports}가 참인 핸들러가 여럿이면 값이 작은 것을 쓴다. 지금은 제조사마다 하나뿐이다. */
    int order();

    void save(CrawledProduct crawled);

    /** 제조사 동기화 시작 전 1회 호출. 매칭 인덱스 등 준비물을 반환한다(없으면 null). */
    default Object prepare(String vendorCode) { return null; }

    /** prepare()가 만든 컨텍스트를 활용하는 저장. 기본 구현은 컨텍스트를 무시한다. */
    default void save(CrawledProduct crawled, Object context) { save(crawled); }

    /**
     * 반영 없이 매칭만 해 본다(dry-run). 내려받지도, 저장하지도 않는다.
     * 기본 구현은 아무것도 하지 않는다 — dry-run을 지원하지 않는 핸들러는 빈 리포트를 낸다.
     */
    default void inspect(CrawledProduct crawled, Object context) { }
}
