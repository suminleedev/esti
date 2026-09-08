package com.example.esti.service;

import com.example.esti.entity.SyncRun;
import com.example.esti.entity.SyncRunType;
import com.example.esti.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 마지막 실행 시각 기록·조회 (C-2·C-3).
 *
 * <p>키는 <b>대문자로 정규화</b>해 넣고 찾는다. 제조사 식별자는 경로변수로도 들어와
 * 대소문자가 섞이는데, 그걸 그대로 두면 같은 대상이 두 행으로 갈린다.
 */
@Service
@RequiredArgsConstructor
public class SyncRunService {

    private final SyncRunRepository syncRunRepository;

    /**
     * 실행을 기록한다. 같은 (종류, 키)가 있으면 시각만 갱신한다.
     *
     * <p><b>기록 실패가 본 작업을 되돌리면 안 된다.</b> 크롤링·적재는 이미 끝난 상태이고,
     * 이건 «언제 했나»를 남기는 부수 기록이다. 그래서 호출부에서 트랜잭션을 분리해 부른다.
     */
    /**
     * 실행을 기록한다. 같은 (종류, 키)가 있으면 시각만 갱신한다.
     *
     * @param applied 실제로 반영했는가. {@code false}(dry-run)면 {@code lastRunAt}만 올리고
     *                {@code lastAppliedAt}은 그대로 둔다 — 반영하지 않은 실행을 «했다»로 세면 거짓이 된다
     */
    @Transactional
    public void record(SyncRunType runType, String runKey, boolean applied, LocalDateTime at) {
        String key = normalize(runKey);
        SyncRun run = syncRunRepository.findByRunTypeAndRunKey(runType, key)
                .orElseGet(() -> new SyncRun(runType, key, at));
        run.setLastRunAt(at);
        if (applied) run.setLastAppliedAt(at);
        syncRunRepository.save(run);
    }

    /** 지금 시각으로 기록한다. */
    @Transactional
    public void record(SyncRunType runType, String runKey, boolean applied) {
        record(runType, runKey, applied, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<SyncRun> find(SyncRunType runType, String runKey) {
        return syncRunRepository.findByRunTypeAndRunKey(runType, normalize(runKey));
    }

    /** dry-run 포함한 마지막 실행 시각. 쿨다운 판정에 쓴다. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastRunAt(SyncRunType runType, String runKey) {
        return find(runType, runKey).map(SyncRun::getLastRunAt);
    }

    /** 실반영만 센 마지막 시각. «업로드 이후 했나» 판정에 쓴다. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastAppliedAt(SyncRunType runType, String runKey) {
        return find(runType, runKey).map(SyncRun::getLastAppliedAt);
    }

    private static String normalize(String key) {
        return key == null ? null : key.toUpperCase(Locale.ROOT);
    }
}
