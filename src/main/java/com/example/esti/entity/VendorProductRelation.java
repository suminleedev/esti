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
     * (`소변기수채` 시트에서 연속 두 행이 같은 부속·같은 전산코드·같은 단가).
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

    /**
     * 세트 정체성 — 이 관계가 속한 세트의 부속 구성 다이제스트 (G-1).
     *
     * <p>관계가 (제품 → 제품)이라 <b>가격행의 역할도 세트도 구분하지 못했다.</b> 같은 품번이
     * 여러 세트의 대표품목이면 그 세트들의 부속이 한 제품에 전부 누적돼, 화면에서 택1 부속이
     * 동시에 나왔다 — A사 22종에서 원본 48세트가 22행으로 접혔다.
     *
     * <p>유일키가 {@code (source, target, type, setHash)}가 되어 세트별로 갈린다.
     * {@link VendorItemPrice#getSetHash()}와 같은 값으로 이어 붙여 조회한다.
     *
     * <p>nullable인 이유는 {@code VendorItemPrice.setHash}와 같다 — Derby ALTER TABLE 제약.
     */
    @Column(name = "set_hash", length = 64)
    private String setHash;

    /**
     * <b>이 세트에 적힌 그대로의 부속 이름</b> (G-4).
     *
     * <p>{@code VendorProduct.productName}은 품번당 하나라 파일에서 마지막에 나온 이름으로 통일된다.
     * A사 33종이 여러 이름으로 등장하는데 <b>27종에서 최빈값이 아닌 이름</b>이 이겼고,
     * 오타가 이기는 경우까지 있었다. 세트마다 다른 이름으로 적힌 것을 살리려면 관계에 둬야 한다.
     *
     * <p>null이면 조회가 {@code VendorProduct.productName}으로 되돌아간다(하위호환·B사 불변).
     */
    @Column(name = "part_name", length = 200)
    private String partName;
}