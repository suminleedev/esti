package com.example.esti.crawler.service;

import java.time.LocalDateTime;

/**
 * 크롤러 실행 상태 (C-3) — <b>막는 것이 아니라 알려 주는 쪽</b>이다.
 *
 * <p>크롤러가 의미 있는 시점은 카탈로그가 바뀐 직후다. 그렇다고 «업로드해야만 돌 수 있다»로
 * 강제하면 이미지만 다시 받고 싶은 때나 실패 후 재시도가 막힌다. 그래서 판단 재료만 내놓고
 * 결정은 사람이 한다.
 *
 * @param appliedSinceLastUpload 마지막 카탈로그 업로드 뒤에 <b>실반영</b> 크롤링을 했는가.
 *                               {@code null}이면 <b>판단할 수 없다</b>는 뜻이다 —
 *                               기록을 시작하기 전의 업로드는 남아 있지 않다.
 *                               모르는 것을 «안 했음»으로 답하면 그 표시를 믿을 수 없게 된다
 * @param cooldownRemainingSeconds 지금 요청하면 거절될 남은 시간. 0이면 바로 돌 수 있다
 */
public record CrawlerRunStatus(
        String maker,
        String vendorCode,
        boolean running,
        LocalDateTime lastRunAt,
        LocalDateTime lastAppliedAt,
        LocalDateTime lastCatalogUploadAt,
        Boolean appliedSinceLastUpload,
        int cooldownMinutes,
        long cooldownRemainingSeconds,
        String message
) {}
