package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 *  대표 품목 - 부속 품목 연결 엔티티
 */
@Entity
@Table(name = "vendor_product_relation", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProductRelation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기준 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_vendor_product_id", nullable = false)
    private VendorProduct sourceProduct;

    // 연결 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_vendor_product_id", nullable = false)
    private VendorProduct targetProduct;

    // 예: MAIN, ACCESSORY, SEAT_COVER, TANK
    @Column(name = "relation_type", nullable = false, length = 50)
    private String relationType;

    /**
     * 세트에 들어가는 개수. 기본 1.
     *
     * <p>원본은 같은 부속이 2개 들어갈 때 <b>행을 두 번 적는다</b>
     * (`소변기수채` 시트 54·55행이 둘 다 `수채가량 <CODE> 18000`).
     * 관계 유일키가 {@code (source, target, type)}이라 그대로 두면 한 건으로 접혀 부속 합계가 세트가에 못 미친다.
     * 중복 행을 세어 여기 담으면 {@code S132E}·{@code L352E}·{@code L352E-2} 3건이 세트가와 정확히 일치한다.
     * ({@code plan-b-format-2026.md} §8 잔여 ②)
     */
    // columnDefinition에 DEFAULT를 박아야 한다 — ddl-auto=update로 기존 테이블에 NOT NULL 컬럼을
    // 붙일 때 Derby가 기본값을 요구한다(없으면 ALTER TABLE이 통째로 실패하고 컬럼이 안 생긴다).
    // 인메모리 create-drop을 쓰는 테스트에서는 드러나지 않는 차이다.
    @Column(name = "quantity", nullable = false, columnDefinition = "integer default 1 not null")
    @Builder.Default
    private Integer quantity = 1;
}