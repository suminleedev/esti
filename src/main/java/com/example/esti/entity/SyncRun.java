package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 카탈로그 적재·이미지 크롤링의 <b>마지막 실행 시각</b> (C-2·C-3).
 *
 * <p>종류·키마다 <b>한 행만</b> 두고 갱신한다. 이력 전체가 아니라 «마지막이 언제였나»만 필요하다.
 *
 * <p><b>왜 기록해야 하나</b> — 유추가 안 된다. {@code VendorProduct.updatedAt}이 유일한 단서인데
 * <b>크롤링도 {@code image_url}을 갱신하며 같은 필드를 움직여</b> 업로드와 크롤링이 구분되지 않는다.
 *
 * <p><b>왜 DB인가</b> — 인메모리면 재기동마다 «크롤링 안 함»으로 보인다. 그 표시를 믿을 수 없으면
 * 표시하지 않는 것과 같다.
 */
@Getter
@Entity
@Table(
        name = "sync_run",
        schema = "APP",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sync_run_type_key",
                columnNames = {"run_type", "run_key"})
)
public class SyncRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 30)
    private SyncRunType runType;

    /** 뜻은 {@link SyncRunType}에 달렸다 — 업로드면 공급사 코드, 크롤링이면 제조사 식별자. */
    @Column(name = "run_key", nullable = false, length = 50)
    private String runKey;

    /**
     * 마지막 실행 시각 — <b>dry-run도 포함한다.</b>
     *
     * <p>쿨다운이 지키려는 것은 «대상 사이트로 나가는 요청»인데,
     * {@code crawlAll()}이 dry-run 분기보다 앞에 있어 점검만 해도 요청은 그대로 나간다.
     * 그래서 여기서는 둘을 가르지 않는다.
     */
    @Setter
    @Column(name = "last_run_at", nullable = false)
    private LocalDateTime lastRunAt;

    /**
     * 마지막 <b>실반영</b> 시각 — dry-run은 세지 않는다. 아직 없으면 {@code null}.
     *
     * <p>«마지막 업로드 이후 크롤링 했나»는 이 값으로 답해야 한다.
     * dry-run은 이미지를 바꾸지 않으므로 «했다»로 세면 거짓이 된다.
     */
    @Setter
    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    protected SyncRun() {
    }

    public SyncRun(SyncRunType runType, String runKey, LocalDateTime lastRunAt) {
        this.runType = runType;
        this.runKey = runKey;
        this.lastRunAt = lastRunAt;
    }
}
