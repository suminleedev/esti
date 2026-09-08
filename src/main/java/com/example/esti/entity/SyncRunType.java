package com.example.esti.entity;

/**
 * {@link SyncRun}이 기록하는 실행의 종류.
 *
 * <p>둘의 <b>식별 축이 다르다</b> — 업로드는 공급사 코드(A/B)로, 크롤링은 제조사 식별자로 돈다.
 * 실제로는 대응되지만 그 대응은 크롤러({@code ProductImageCrawler.vendorCode()})만 알고 있다.
 * 그래서 {@code runKey}의 뜻이 이 값에 따라 달라진다. 억지로 한 축으로 합치지 않는다 —
 * 합치면 새 제조사가 들어올 때 결합이 늘어난다.
 */
public enum SyncRunType {

    /** 카탈로그 엑셀 적재. {@code runKey}는 공급사 코드(A·B). */
    CATALOG_UPLOAD,

    /** 제품 이미지 크롤링. {@code runKey}는 제조사 식별자. */
    IMAGE_CRAWL
}
