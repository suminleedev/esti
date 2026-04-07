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
}

