package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_template_line", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class ProposalTemplateLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 템플릿 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ProposalTemplate template;

    // 카탈로그 상품 id (VendorProduct 기준) — ProposalLine과 동일한 역정규화 스냅샷 방식.
    // 폐기 예정 ProductCatalog FK를 제거하고 평범한 Long 컬럼으로 보관한다.
    @Column(name = "product_id")
    private Long productId;

    // ===== 상품 스냅샷 =====
    @Column(length = 500)
    private String specs;

    @Lob
    private String description;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 100)
    private String vendorCode;

    @Column(length = 255)
    private String vendorName;

    @Column(length = 255)
    private String vendorItemName;

    @Column(length = 100)
    private String mainItemCode;

    @Column(length = 100)
    private String oldItemCode;

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 500)
    private String remark;

    // ===== 템플릿 설정값 =====
    @Column(length = 50)
    private String area;

    @Column(length = 100)
    private String category;

    private Integer defaultQty;

    @Column(length = 200)
    private String note;

    /**
     * 단위(SET/EA 등). 템플릿도 스냅샷이라 여기 없으면 템플릿으로 만든 제안서 라인의 단위가 빈다.
     * 평형·건물구분은 현장별 값이라 템플릿에 두지 않는다.
     */
    @Column(length = 20)
    private String unit;

    /** 소분류 — 제안서 카드의 `사양` 행. 없으면 템플릿으로 만든 카드의 사양이 빈다. */
    @Column(name = "category_small", length = 100)
    private String categorySmall;

    /**
     * 선택사항(유상옵션) 여부. 없으면 템플릿으로 만든 라인이 옵션 열(3열)로 가지 못한다.
     *
     * <p>컬럼은 <b>nullable</b>이다 — Derby가 기존 행이 있는 테이블에 {@code NOT NULL} 컬럼을
     * DEFAULT 없이 추가하지 못해 {@code ddl-auto=update}가 조용히 건너뛴다.
     * 구 데이터는 null이며 읽는 쪽에서 false로 본다({@code sortOrder}와 같은 방침).
     */
    @Column(name = "is_optional")
    private Boolean optional = false;
}
