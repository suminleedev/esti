package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_line", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class ProposalLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 제안서 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(length = 200)
    private String productName;

    @Column(length = 20)
    private String vendorCode;

    @Column(length = 100)
    private String vendorName;

    @Column(length = 200)
    private String vendorItemName;

    @Column(length = 100)
    private String mainItemCode;

    @Column(length = 100)
    private String oldItemCode;

    @Column(precision = 15, scale = 2)
    private BigDecimal catalogUnitPrice;   // 카탈로그 기준 단가

    @Column(name = "manual_margin", nullable = false)
    private Boolean manualMargin = false;  // 마진율 수동 설정 여부

    @Column(precision = 5, scale = 2)
    private BigDecimal marginRate;  // 적용 마진율(%)

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;   // 최종 제안 단가

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;      // 총금액

    @Column(length = 500)
    private String remark;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 50)
    private String area;

    @Column(length = 100)
    private String category;

    private Integer qty;

    @Column(length = 200)
    private String note;

    /** 제안서 내 표시 순서(0-based). 기존 행은 null이며 조회 시 id 순으로 폴백된다. */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 단위(SET/EA 등) — 견적서 C열. 담을 때 {@link VendorProduct}에서 스냅샷한다.
     * 마스터가 나중에 바뀌어도 이미 만든 제안서는 당시 값을 유지한다(다른 표시 필드와 동일한 방침).
     */
    @Column(length = 20)
    private String unit;

    /**
     * 평형 — 라인 단위다. 한 제안서에 59㎡·84㎡가 섞이고, 견적서는 그중 한 평형만 뽑는다(O-7).
     * 제안서 단위 값인 {@code Proposal.apartmentType}과 별개다.
     */
    @Column(name = "apartment_type", length = 50)
    private String apartmentType;

    /**
     * 건물 구분(본세대/부속동/상가 등) — 견적서의 본동·부속동 섹션 분리 기준(O-5).
     * 현장마다 값이 추가될 수 있어 enum이 아니라 문자열이다. 마스터 관리는 Phase 7.
     */
    @Column(name = "building_type", length = 50)
    private String buildingType;

    /**
     * 소분류 — 제안서 카드의 `사양` 행(투피스양변기·반다리세면기 등). 카탈로그 `categorySmall`을 스냅샷한다.
     * 라인의 {@code category}(유형: 양변기·세면기)보다 한 단계 구체적이다.
     */
    @Column(name = "category_small", length = 100)
    private String categorySmall;

    /**
     * 선택사항(유상옵션) 여부 — 제안서 카드 그리드의 <b>3열 배치 기준</b>이다.
     * 샘플은 비고에 `유상옵션`이라 적어 두었으나, 표시 문구가 레이아웃을 좌우하면 문구를 바꾸는 순간
     * 배치가 깨지므로 별도 필드로 받는다.
     */
    @Column(name = "is_optional", nullable = false)
    private Boolean optional = false;
}

