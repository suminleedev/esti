package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 화면 드롭다운의 출처가 되는 마스터 값(건물구분·적용부위·적용카테고리).
 *
 * <p><b>값 참조다 — FK가 아니다(M-5).</b> {@code ProposalLine.area}·{@code category}·
 * {@code buildingType}은 지금처럼 문자열 스냅샷을 들고, 이 테이블은 "새로 고를 때 뭐가 보이나"만
 * 정한다. 견적서는 발송 시점 값이 보존되는 편이 맞고, 현장마다 값을 직접 추가하는 M-3의 자유 입력과
 * FK가 충돌하기 때문이다.
 *
 * <p>그래서 삭제도 하드 삭제가 아니라 {@code active=false}다(M-6) — 이미 그 값을 쓰는 제안서는
 * 그대로 두고 드롭다운에서만 감춘다.
 */
@Entity
@Table(
        name = "master_code",
        schema = "APP",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_master_code_type_label",
                columnNames = {"code_type", "label"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class MasterCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 컬럼명을 {@code type}이 아니라 {@code code_type}으로 둔다 — DB 예약어와 부딪히지 않게.
     *
     * <p><b>{@code @Enumerated}가 아니라 컨버터를 쓴다.</b> {@code @Enumerated(STRING)}이면 Hibernate가
     * {@code check (code_type in ('APARTMENT_TYPE', ...))}를 함께 만드는데, {@code ddl-auto=update}는
     * 이미 있는 check 제약을 갱신하지 못한다. 그래서 종류를 하나 추가하는 순간 기존 DB에서는
     * 그 값을 <b>넣을 수 없다</b> — 기동은 멀쩡하고 insert에서만 터진다.
     * "종류가 늘어도 row만 추가하면 된다"(M-7)를 지키려면 이 제약이 없어야 한다.
     * 실제로 {@code APARTMENT_TYPE}을 추가할 때 이 경로로 한 번 깨졌다.
     */
    @Convert(converter = MasterCodeTypeConverter.class)
    @Column(name = "code_type", nullable = false, length = 30)
    private MasterCodeType type;

    /** 화면 표시값이자 {@code ProposalLine}에 그대로 저장되는 문자열. */
    @Column(nullable = false, length = 100)
    private String label;

    /** 드롭다운 표시 순서. 상수 배열 순서를 대체한다(M-9). */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** false = soft delete. 드롭다운에서만 숨고 기존 제안서의 값은 남는다(M-6). */
    @Column(nullable = false)
    private Boolean active = true;

    public MasterCode(MasterCodeType type, String label, int sortOrder) {
        this.type = type;
        this.label = label;
        this.sortOrder = sortOrder;
        this.active = true;
    }
}
