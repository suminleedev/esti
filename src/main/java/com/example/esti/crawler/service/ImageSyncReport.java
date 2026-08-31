package com.example.esti.crawler.service;

import java.util.List;

/**
 * 제조사 이미지 동기화 1회의 결과.
 *
 * <p>이전에는 {@code syncByMaker}가 {@code void}였고 컨트롤러가 무조건 "동기화 완료"를 돌려줬다.
 * 핸들러가 실패를 로그로 삼키므로 <b>한 건도 저장되지 않아도 성공으로 보였다.</b>
 * 무엇을 몇 건 했는지를 돌려주는 것이 이 기록의 목적이다.
 *
 * @param match 핸들러가 집계를 내놓지 않으면 {@code null} — 0으로 채우지 않는다
 */
public record ImageSyncReport(
        String maker,
        boolean dryRun,
        int sourcesTotal,
        int sourcesSucceeded,
        List<String> failedSources,
        int collected,
        int processed,
        int failed,
        MatchDetail match,
        String message
) {

    /** 매칭 상세. 핸들러가 {@link SyncMatchCounters}를 구현할 때만 채워진다. */
    public record MatchDetail(
            int indexedCodes,
            int exactMatched,
            int relaxedOnly,
            int notInDb,
            int skippedNoCode,
            int skippedNoImage,
            int downloadFailed,
            int rowsAffected,
            int rowsFilled,
            int rowsReplaced,
            List<String> relaxedCandidates
    ) {
        static MatchDetail from(SyncMatchCounters c) {
            return new MatchDetail(
                    c.indexedCodes(), c.exactMatched(), c.relaxedOnly(), c.notInDb(),
                    c.skippedNoCode(), c.skippedNoImage(), c.downloadFailed(),
                    c.rowsAffected(), c.rowsFilled(), c.rowsReplaced(),
                    c.relaxedCandidates());
        }
    }

    public boolean partialCrawl() {
        return sourcesSucceeded < sourcesTotal;
    }
}
