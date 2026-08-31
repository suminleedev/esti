package com.example.esti.crawler.service;

import java.util.List;

/**
 * 핸들러가 한 번의 동기화에서 모은 매칭 집계.
 *
 * <p>{@link ManufacturerProductSyncHandler#prepare(String)}이 돌려준 컨텍스트가 이걸 구현하면
 * 동기화 리포트에 상세 집계가 실린다. 구현하지 않는 핸들러(ASTD 등)는 수집·저장 건수만 실린다 —
 * <b>있지도 않은 0을 채워 넣는 것보다 비어 있다고 말하는 편이 낫다.</b>
 */
public interface SyncMatchCounters {

    /** 인덱스에 담긴 서로 다른 품번 수 — 매칭 모수. */
    int indexedCodes();

    /** 사이트에서 받아 처리한 제품 수. */
    int collected();

    /** 정확 매칭된 제품 수. */
    int exactMatched();

    /** 정확 매칭은 없고 완화 후보만 있는 제품 수. 반영하지 않는다. */
    int relaxedOnly();

    /** DB에 없는 제품 수. */
    int notInDb();

    int skippedNoCode();

    int skippedNoImage();

    int downloadFailed();

    /** 갱신된(또는 dry-run이면 갱신될) 행 수. */
    int rowsAffected();

    /** 그중 이미지가 없던 행 — 결손 충전. */
    int rowsFilled();

    /** 그중 이미 이미지가 있던 행 — 교체. */
    int rowsReplaced();

    /** 완화 후보 짝 목록. 눈으로 보고 승인하기 위한 것이다. */
    List<String> relaxedCandidates();
}
